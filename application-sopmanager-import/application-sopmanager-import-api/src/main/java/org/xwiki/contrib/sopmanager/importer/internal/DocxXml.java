/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.sopmanager.importer.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Helpers to read the elements of a Word document.
 *
 * @version $Id$
 * @since 1.0
 */
public final class DocxXml
{
    /**
     * A paragraph.
     */
    public static final String P = "p";

    /**
     * A table.
     */
    public static final String TBL = "tbl";

    /**
     * A row of a table.
     */
    public static final String TR = "tr";

    /**
     * A cell of a table.
     */
    public static final String TC = "tc";

    /**
     * The properties of a paragraph.
     */
    public static final String PPR = "pPr";

    /**
     * A structured document tag, whose content is in {@link #SDT_CONTENT}.
     */
    public static final String SDT = "sdt";

    /**
     * The content of a structured document tag.
     */
    public static final String SDT_CONTENT = "sdtContent";

    /**
     * The content of a text box.
     */
    public static final String TXBX_CONTENT = "txbxContent";

    /**
     * The attribute holding the value of most properties.
     */
    public static final String VAL = "val";

    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    private static final String FALLBACK = "Fallback";

    private static final String BR = "br";

    private static final String TYPE = "type";

    private static final String PAGE = "page";

    private DocxXml()
    {
    }

    /**
     * @param parent an element
     * @param name the local name of the children, in the Word namespace
     * @return the children of the element with this name
     */
    public static List<Element> children(Element parent, String name)
    {
        List<Element> children = new ArrayList<>();
        if (parent != null) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element && is(element, name)) {
                    children.add(element);
                }
            }
        }
        return children;
    }

    /**
     * @param parent an element
     * @return the child elements of the element
     */
    public static List<Element> children(Element parent)
    {
        List<Element> children = new ArrayList<>();
        if (parent != null) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element) {
                    children.add(element);
                }
            }
        }
        return children;
    }

    /**
     * @param parent an element
     * @param name the local name of the child, in the Word namespace
     * @return the first child of the element with this name, {@code null} if there's none
     */
    public static Element child(Element parent, String name)
    {
        List<Element> children = children(parent, name);
        return children.isEmpty() ? null : children.get(0);
    }

    /**
     * @param parent an element
     * @param path the local names of the descendants, in the Word namespace
     * @return the first descendant at this path, {@code null} if there's none
     */
    public static Element path(Element parent, String... path)
    {
        Element element = parent;
        for (int i = 0; i < path.length && element != null; i++) {
            element = child(element, path[i]);
        }
        return element;
    }

    /**
     * @param element an element
     * @param name a local name in the Word namespace
     * @return whether the element has this name
     */
    public static boolean is(Element element, String name)
    {
        return name.equals(element.getLocalName()) && DocxPackage.W.equals(element.getNamespaceURI());
    }

    /**
     * @param element an element
     * @param name the local name of an attribute, in the Word namespace
     * @return the value of the attribute, an empty string if it's not set
     */
    public static String attribute(Element element, String name)
    {
        return element == null ? "" : element.getAttributeNS(DocxPackage.W, name);
    }

    /**
     * @param parent an element
     * @param name the local name of a child holding a value in its "val" attribute
     * @return the value, an empty string if there's no such child
     */
    public static String value(Element parent, String name)
    {
        return attribute(child(parent, name), VAL);
    }

    /**
     * @param properties the properties of a run or a paragraph
     * @param name the local name of a toggle property, e.g. "b"
     * @return whether the toggle property is on
     */
    public static boolean isOn(Element properties, String name)
    {
        Element toggle = child(properties, name);
        if (toggle == null) {
            return false;
        }
        String value = attribute(toggle, VAL);
        return !"0".equals(value) && !"false".equals(value) && !"none".equals(value);
    }

    /**
     * @param element an element
     * @return the descendant elements of the element, in document order, without the alternative content of the
     *     markup compatibility (e.g. the VML copy of a text box)
     */
    public static List<Element> descendants(Element element)
    {
        List<Element> descendants = new ArrayList<>();
        collect(element, descendants);
        return descendants;
    }

    private static void collect(Element element, List<Element> descendants)
    {
        for (Element child : children(element)) {
            if (!(FALLBACK.equals(child.getLocalName()) && MC.equals(child.getNamespaceURI()))) {
                descendants.add(child);
                collect(child, descendants);
            }
        }
    }

    /**
     * @param element an element
     * @return the text of the element, its paragraphs and line breaks separated by new lines, without the
     *     instructions of the fields, nor the deleted text, nor the alternative content of the markup compatibility
     */
    public static String text(Element element)
    {
        return text(element, true);
    }

    /**
     * @param element an element
     * @return the text of the element, as {@link #text(Element)}, but with the paragraphs of the text boxes joined:
     *     the text of a page header can be split in several text boxes (e.g. "ANA", "_", "AA", "_", "7.212")
     */
    public static String compactText(Element element)
    {
        return text(element, false);
    }

    private static String text(Element element, boolean textBoxParagraphs)
    {
        StringBuilder text = new StringBuilder();
        for (Element descendant : descendants(element)) {
            if (is(descendant, "t")) {
                text.append(descendant.getTextContent());
            } else if (is(descendant, "tab")) {
                text.append(' ');
            } else if (isLineBreak(descendant) || isNewParagraph(descendant, textBoxParagraphs)) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private static boolean isLineBreak(Element element)
    {
        return is(element, BR) && !PAGE.equals(attribute(element, TYPE));
    }

    private static boolean isNewParagraph(Element element, boolean textBoxParagraphs)
    {
        return is(element, P) && (textBoxParagraphs || !isInTextBox(element));
    }

    private static boolean isInTextBox(Element element)
    {
        for (Node parent = element.getParentNode(); parent instanceof Element ancestor;
            parent = ancestor.getParentNode()) {
            if (is(ancestor, TXBX_CONTENT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param element an element
     * @return the lines of text of the element that are not blank, with normalized spaces
     */
    public static List<String> lines(Element element)
    {
        return lines(text(element));
    }

    /**
     * @param text a text
     * @return the lines of the text that are not blank, with normalized spaces
     */
    public static List<String> lines(String text)
    {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (StringUtils.isNotBlank(line)) {
                lines.add(StringUtils.normalizeSpace(line));
            }
        }
        return lines;
    }

    /**
     * @param element an element
     * @return whether the element contains a page break
     */
    public static boolean hasPageBreak(Element element)
    {
        return descendants(element).stream()
            .anyMatch(descendant -> is(descendant, BR) && PAGE.equals(attribute(descendant, TYPE)));
    }

    /**
     * @param element an element
     * @return the text boxes of the element
     */
    public static List<Element> textBoxes(Element element)
    {
        return descendants(element).stream().filter(descendant -> is(descendant, TXBX_CONTENT)).toList();
    }
}
