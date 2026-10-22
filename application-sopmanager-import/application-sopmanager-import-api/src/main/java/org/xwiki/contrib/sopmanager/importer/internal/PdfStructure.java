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
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.text.TextPosition;

/**
 * The structure tree of a tagged PDF: the standard type of its elements, their page and their text.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfStructure
{
    /**
     * The types of the elements holding blocks: an element holding one of them is a container, not a paragraph.
     */
    private static final Set<String> BLOCK_TYPES = Set.of("Document", "Part", "Art", "Sect", "Div", "BlockQuote",
        "NonStruct", "TOC", "TOCI", "P", "H", "H1", "H2", "H3", "H4", "H5", "H6", "L", "LI", "Table", "Figure");

    private static final int MAX_ROLE_MAPPINGS = 10;

    private final PdfPageText text;

    private final Map<String, Object> roleMap;

    /**
     * @param text the text of the pages
     * @param roleMap the types of the elements specific to the application that wrote the PDF, mapped to standard
     *     types
     */
    public PdfStructure(PdfPageText text, Map<String, Object> roleMap)
    {
        this.text = text;
        this.roleMap = roleMap == null ? Map.of() : roleMap;
    }

    /**
     * @param element an element of the structure tree
     * @return the standard type of the element, e.g. "P" for a paragraph styled "Standard"
     */
    public String getType(PDStructureElement element)
    {
        String type = element.getStructureType();
        for (int i = 0; i < MAX_ROLE_MAPPINGS && this.roleMap.get(type) instanceof String standard
            && !standard.equals(type); i++) {
            type = standard;
        }
        return type;
    }

    /**
     * @param page a page
     * @return the start of the lines of the page that are not indented
     */
    public float getLeftMargin(PDPage page)
    {
        return page == null ? 0 : this.text.getLeftMargin(page);
    }

    /**
     * @param element an element of the structure tree
     * @param parentPage the page of its parent
     * @return the page of the element
     */
    public static PDPage getPage(PDStructureElement element, PDPage parentPage)
    {
        PDPage page = element.getPage();
        return page == null ? parentPage : page;
    }

    /**
     * @param node an element of the structure tree
     * @return its child elements
     */
    public static List<PDStructureElement> getElements(PDStructureNode node)
    {
        List<PDStructureElement> elements = new ArrayList<>();
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement element) {
                elements.add(element);
            }
        }
        return elements;
    }

    /**
     * @param element an element of the structure tree
     * @param type a standard type
     * @return the first child element of this type, {@code null} if there's none
     */
    public PDStructureElement getElement(PDStructureElement element, String type)
    {
        return getElements(element).stream().filter(child -> type.equals(getType(child))).findFirst().orElse(null);
    }

    /**
     * @param element an element of the structure tree
     * @return whether the element holds blocks (paragraphs, lists, tables...), i.e. it is not a paragraph
     */
    public boolean isContainer(PDStructureElement element)
    {
        return getElements(element).stream().anyMatch(child -> BLOCK_TYPES.contains(getType(child)));
    }

    /**
     * @param element an element of the structure tree
     * @return whether the element holds text itself, not only through its child elements
     */
    public static boolean hasOwnText(PDStructureElement element)
    {
        return element.getKids().stream()
            .anyMatch(kid -> kid instanceof Integer || kid instanceof PDMarkedContentReference);
    }

    /**
     * @param node an element of the structure tree
     * @param page the page of the element
     * @param skipped the descendant elements whose text is not collected
     * @return the characters of the element and of its descendants, in reading order
     */
    public List<TextPosition> getText(PDStructureNode node, PDPage page, Predicate<PDStructureElement> skipped)
    {
        List<TextPosition> positions = new ArrayList<>();
        collect(node, page, skipped, true, positions);
        return positions;
    }

    /**
     * @param element an element of the structure tree
     * @param page the page of the element
     * @return the characters of the element itself, not of its child elements
     */
    public List<TextPosition> getOwnText(PDStructureElement element, PDPage page)
    {
        List<TextPosition> positions = new ArrayList<>();
        collect(element, page, child -> true, false, positions);
        return positions;
    }

    private void collect(PDStructureNode node, PDPage parentPage, Predicate<PDStructureElement> skipped,
        boolean descendants, List<TextPosition> positions)
    {
        PDPage page = node instanceof PDStructureElement element ? getPage(element, parentPage) : parentPage;
        for (Object kid : node.getKids()) {
            if (kid instanceof Integer id) {
                positions.addAll(this.text.get(page, id));
            } else if (kid instanceof PDMarkedContentReference reference) {
                PDPage referencePage = reference.getPage() == null ? page : reference.getPage();
                positions.addAll(this.text.get(referencePage, reference.getMCID()));
            } else if (descendants && kid instanceof PDStructureElement child && !skipped.test(child)) {
                collect(child, page, skipped, true, positions);
            }
        }
    }
}
