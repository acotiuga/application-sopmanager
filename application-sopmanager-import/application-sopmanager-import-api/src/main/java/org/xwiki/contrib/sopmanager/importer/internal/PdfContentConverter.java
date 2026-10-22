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
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.text.TextPosition;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;

/**
 * Converts the structure tree of a tagged PDF (as written by Word, or LibreOffice) to rendering blocks: its
 * paragraphs, headings, lists, tables and figures. The page header and footer, marked as artifacts, are not in the
 * structure tree.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfContentConverter
{
    private static final Pattern HEADING = Pattern.compile("^H([1-6]?)$");

    private static final String LIST = "L";

    private static final String TABLE = "Table";

    private static final String FIGURE = "Figure";

    private static final float POINTS_PER_CENTIMETER = 28.3465F;

    private final PdfStructure structure;

    private final PdfImages images;

    private final PdfListConverter listConverter;

    private final PdfTableConverter tableConverter;

    /**
     * The depth of the table cells being converted: the paragraphs of the cells are not indented.
     */
    private int cellDepth;

    /**
     * @param structure the structure tree of the PDF
     * @param images extracts the figures of the PDF
     */
    public PdfContentConverter(PdfStructure structure, PdfImages images)
    {
        this.structure = structure;
        this.images = images;
        this.listConverter = new PdfListConverter(structure);
        this.tableConverter = new PdfTableConverter(structure, this);
    }

    /**
     * @param node an element of the structure tree, e.g. its root
     * @param page the page of the element, {@code null} if it's not known
     * @return the blocks of the child elements of the element
     */
    public List<Block> convertChildren(PDStructureNode node, PDPage page)
    {
        List<Block> blocks = new ArrayList<>();
        for (PDStructureElement element : PdfStructure.getElements(node)) {
            convert(element, page, blocks);
        }
        return blocks;
    }

    /**
     * @param cell a cell of a table of the structure tree
     * @param page the page of the cell
     * @return the blocks of the cell, its paragraphs not indented
     */
    public List<Block> convertCell(PDStructureNode cell, PDPage page)
    {
        this.cellDepth++;
        try {
            return convertChildren(cell, page);
        } finally {
            this.cellDepth--;
        }
    }

    private void convert(PDStructureElement element, PDPage parentPage, List<Block> blocks)
    {
        PDPage page = PdfStructure.getPage(element, parentPage);
        String type = this.structure.getType(element);
        Matcher heading = HEADING.matcher(type);
        if (LIST.equals(type)) {
            blocks.addAll(this.listConverter.convert(element, page));
        } else if (TABLE.equals(type)) {
            this.tableConverter.convert(element, page).ifPresent(blocks::add);
        } else if (FIGURE.equals(type)) {
            ImageBlock image = this.images.extract(element, page);
            if (image != null) {
                blocks.add(new ParagraphBlock(List.of(image)));
            }
        } else if (heading.matches()) {
            addHeading(element, page, heading.group(1), blocks);
        } else if (this.structure.isContainer(element)) {
            // The text of the container itself, then its blocks.
            addParagraph(PdfInlineConverter.convert(PdfInlineConverter.glyphs(
                this.structure.getOwnText(element, page))), blocks);
            blocks.addAll(convertChildren(element, page));
        } else {
            List<TextPosition> positions = this.structure.getText(element, page, child -> false);
            ParagraphBlock paragraph = addParagraph(PdfInlineConverter.convert(PdfInlineConverter.glyphs(positions)),
                blocks);
            if (paragraph != null && this.cellDepth == 0) {
                // The start of the lines of the paragraph, relative to the lines that are not indented.
                float start = positions.stream().map(TextPosition::getXDirAdj).min(Float::compare).orElse(0F);
                DocxBlockConverter.setIndentation(paragraph,
                    (start - this.structure.getLeftMargin(page)) / POINTS_PER_CENTIMETER);
            }
        }
    }

    private void addHeading(PDStructureElement element, PDPage page, String level, List<Block> blocks)
    {
        // The headings are usually bold: their content is not.
        List<PdfInlineConverter.Glyph> glyphs = new ArrayList<>();
        for (PdfInlineConverter.Glyph glyph : PdfInlineConverter.glyphs(
            this.structure.getText(element, page, child -> false))) {
            EnumSet<Format> formats = EnumSet.noneOf(Format.class);
            formats.addAll(glyph.formats());
            formats.remove(Format.BOLD);
            glyphs.add(new PdfInlineConverter.Glyph(glyph.text(), glyph.spaceBefore(), formats));
        }
        List<Block> content = PdfInlineConverter.convert(glyphs);
        if (!content.isEmpty()) {
            blocks.add(new HeaderBlock(content, HeaderLevel.parseInt(level.isEmpty() ? 1 : Integer.parseInt(level))));
        }
    }

    private static ParagraphBlock addParagraph(List<Block> content, List<Block> blocks)
    {
        if (content.isEmpty()) {
            return null;
        }
        ParagraphBlock paragraph = new ParagraphBlock(content);
        blocks.add(paragraph);
        return paragraph;
    }
}
