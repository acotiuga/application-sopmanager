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

import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.P;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TBL;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TC;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TR;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.attribute;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.child;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.children;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.is;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.lines;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion.Origin;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;

/**
 * Reads a Word document for its import as SOP document: converts its content, and finds the details of its page
 * header and footer, wherever they are:
 * <ul>
 * <li>in the page header and footer of the document, as in the SOP template;</li>
 * <li>in the content, repeated on each page: at the top of the pages a paragraph or a table starting with the code of
 * the document (with the logo, possibly in a text box), at the bottom of the pages a table whose cells are the labels
 * of the release details, and the path of the original document; these are removed from the content;</li>
 * <li>in the frames of a fixed layout produced by the conversion of a PDF document (see
 * {@link FixedLayoutConverter}).</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.0
 */
public class DocxReader
{
    private static final String SECTION = "sectPr";

    private final DocxPackage docx;

    /**
     * @param docx the Word document
     */
    public DocxReader(DocxPackage docx)
    {
        this.docx = docx;
    }

    /**
     * @return the content and the details of the document
     * @throws DocumentReadException if the document can't be read
     */
    public ImportedDocument read() throws DocumentReadException
    {
        Element body = getBody();
        Map<String, DocxPackage.Relationship> relationships = this.docx.getRelationships(DocxPackage.DOCUMENT);
        DocxStyles styles = new DocxStyles(getPart(relationships, "styles"), getPart(relationships, "numbering"));
        DocxInlineConverter inlineConverter = new DocxInlineConverter(this.docx, relationships);
        DocxBlockConverter blockConverter = new DocxBlockConverter(styles, inlineConverter);
        inlineConverter.setBlockConverter(blockConverter);

        SOPDocumentDetails details = new SOPDocumentDetails();
        Element section = getLastSection(body);
        boolean inParts = readHeaderFooterParts(section, relationships, details);

        List<Element> elements = DocxBlockConverter.flatten(children(body));
        List<Block> blocks;
        Compatibility compatibility;
        if (FixedLayoutConverter.isFixedLayout(elements)) {
            blocks = new FixedLayoutConverter(inlineConverter, blockConverter).convert(elements, details);
            compatibility = Compatibility.FIXED_LAYOUT;
        } else {
            List<Element> content = removeHeaderFooter(elements, details);
            blocks = blockConverter.convert(content);
            compatibility = getCompatibility(elements, content, inParts);
        }

        List<ImportWarning> warnings = new ArrayList<>(details.getWarnings());
        if (inlineConverter.getBackgroundImages() > 0) {
            warnings.add(new ImportWarning("sopManager.import.warning.backgroundImages",
                inlineConverter.getBackgroundImages()));
        }
        return new ImportedDocument(new XDOM(blocks), inlineConverter.getImages(), details, compatibility,
            isLandscape(section), warnings,
            DocxProperties.read(this.docx, details.getOriginalDocument()));
    }

    /**
     * Reads where the document comes from, as {@link #read()} tells it, without converting its content.
     *
     * @return where the document comes from
     * @throws DocumentReadException if the document can't be read
     */
    public Origin readOrigin() throws DocumentReadException
    {
        Element body = getBody();
        SOPDocumentDetails details = new SOPDocumentDetails();
        boolean inParts = readHeaderFooterParts(getLastSection(body),
            this.docx.getRelationships(DocxPackage.DOCUMENT), details);
        List<Element> elements = DocxBlockConverter.flatten(children(body));
        Compatibility compatibility = FixedLayoutConverter.isFixedLayout(elements) ? Compatibility.FIXED_LAYOUT
            : getCompatibility(elements, removeHeaderFooter(elements, details), inParts);
        return ImportVersions.getOrigin(DocumentReader.DOCX, compatibility,
            DocxProperties.read(this.docx, details.getOriginalDocument()));
    }

    private Element getBody() throws DocumentReadException
    {
        Element body = child(this.docx.getXml(DocxPackage.DOCUMENT), "body");
        if (body == null) {
            throw new DocumentReadException("Not a Word document: no body.");
        }
        return body;
    }

    /**
     * @return whether the content had the page header and footer, removed from it
     */
    private static Compatibility getCompatibility(List<Element> elements, List<Element> content, boolean inParts)
    {
        return content.size() < elements.size() && !inParts ? Compatibility.HEADER_FOOTER_IN_CONTENT
            : Compatibility.COMPATIBLE;
    }

    private Element getPart(Map<String, DocxPackage.Relationship> relationships, String type)
        throws DocumentReadException
    {
        for (DocxPackage.Relationship relationship : relationships.values()) {
            if (type.equals(relationship.type()) && !relationship.external()) {
                return this.docx.getXml(relationship.target());
            }
        }
        return null;
    }

    private static Element getLastSection(Element body)
    {
        Element section = child(body, SECTION);
        if (section == null) {
            // The properties of the last section can also be in the last paragraph.
            List<Element> paragraphs = children(body, P);
            section = paragraphs.isEmpty() ? null : DocxXml.path(paragraphs.get(paragraphs.size() - 1),
                DocxXml.PPR, SECTION);
        }
        return section;
    }

    private static boolean isLandscape(Element section)
    {
        Element size = child(section, "pgSz");
        if (size == null) {
            return false;
        }
        if ("landscape".equals(attribute(size, "orient"))) {
            return true;
        }
        try {
            return Integer.parseInt(attribute(size, "w")) > Integer.parseInt(attribute(size, "h"));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Reads the details of the page header and footer parts of the last section: the default ones, else the ones of
     * the first page.
     *
     * @return whether details were found in the page header or footer parts
     */
    private boolean readHeaderFooterParts(Element section, Map<String, DocxPackage.Relationship> relationships,
        SOPDocumentDetails details) throws DocumentReadException
    {
        boolean found = false;
        for (String type : List.of("default", "first", "even")) {
            for (Element reference : children(section)) {
                boolean header = is(reference, "headerReference");
                if ((header || is(reference, "footerReference")) && type.equals(attribute(reference, "type"))) {
                    DocxPackage.Relationship target =
                        relationships.get(reference.getAttributeNS(DocxPackage.R, "id"));
                    Element part = target == null ? null : this.docx.getXml(target.target());
                    if (part != null) {
                        found |= header ? details.readHeader(getGroups(part)) : readFooter(children(part), details);
                    }
                }
            }
        }
        return found;
    }

    /**
     * @return the lines of text of the paragraphs, cells and text boxes of a page header
     */
    private static List<List<String>> getGroups(Element part)
    {
        List<List<String>> groups = new ArrayList<>();
        for (Element element : DocxBlockConverter.flatten(children(part))) {
            if (is(element, TBL)) {
                for (Element row : children(element, TR)) {
                    for (Element cell : children(row, TC)) {
                        groups.add(lines(cell));
                    }
                }
            } else {
                groups.add(lines(element));
            }
        }
        return groups;
    }

    private static boolean readFooter(List<Element> elements, SOPDocumentDetails details)
    {
        boolean found = false;
        for (Element element : DocxBlockConverter.flatten(elements)) {
            if (is(element, TBL)) {
                found |= details.readReleaseTable(getRows(element));
            } else {
                String text = StringUtils.normalizeSpace(DocxXml.text(element));
                found |= details.readInline(text, 2);
                // The path of the original document, e.g. a FILENAME field.
                details.readOriginalDocument(text);
            }
        }
        return found;
    }

    private static List<List<String>> getRows(Element table)
    {
        List<List<String>> rows = new ArrayList<>();
        for (Element row : children(table, TR)) {
            List<String> cells = new ArrayList<>();
            for (Element cell : children(row, TC)) {
                cells.add(StringUtils.normalizeSpace(DocxXml.text(cell)));
            }
            rows.add(cells);
        }
        return rows;
    }

    /**
     * Removes the page header and footer repeated in the content, and reads their details: the first paragraph or
     * table, among the first ones of the content, starting with the code of the document, and the next ones with the
     * same text; the tables of the release details; the paths of the original document.
     *
     * @return the content without the page header and footer
     */
    private static List<Element> removeHeaderFooter(List<Element> elements, SOPDocumentDetails details)
    {
        List<Element> content = new ArrayList<>();
        String headerText = null;
        int textIndex = 0;
        for (Element element : elements) {
            String compactText = DocxXml.compactText(element);
            String text = StringUtils.normalizeSpace(compactText);
            if (headerText == null && textIndex < 3 && SOPDocumentDetails.CODE.matcher(text).find()) {
                headerText = text;
                details.readHeader(is(element, TBL) ? getGroups(wrap(element)) : List.of(lines(compactText)));
            } else if ((text.isEmpty() || !text.equals(headerText)) && !isFooter(element, text, details)) {
                content.add(element);
            }
            textIndex += text.isEmpty() ? 0 : 1;
        }
        return content;
    }

    /**
     * @return whether the element is a part of the page footer: the release details, read if so, or the path of the
     *     original document
     */
    private static boolean isFooter(Element element, String text, SOPDocumentDetails details)
    {
        if (is(element, TBL)) {
            return details.readReleaseTable(getRows(element));
        }
        return details.readInline(text, 2) || details.readOriginalDocument(text);
    }

    private static Element wrap(Element table)
    {
        // A part with this table only, to read it as a page header.
        Element part = table.getOwnerDocument().createElementNS(DocxPackage.W, "w:hdr");
        part.appendChild(table.cloneNode(true));
        return part;
    }
}
