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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.text.TextPosition;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
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

    /**
     * How far below the top of a figure the top of the text of the row it starts next to can be, in points: the
     * padding of the cells.
     */
    private static final float ROW_TOLERANCE = 6;

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
        List<PDStructureElement> elements = PdfStructure.getElements(node);
        int index = 0;
        while (index < elements.size()) {
            PDStructureElement element = elements.get(index++);
            if (TABLE.equals(this.structure.getType(element))) {
                // The figures following the table, e.g. the icons drawn in its last column, next to its rows.
                List<PDStructureElement> figures = new ArrayList<>();
                while (index < elements.size() && FIGURE.equals(this.structure.getType(elements.get(index)))) {
                    figures.add(elements.get(index++));
                }
                convertTable(element, figures, page, blocks);
            } else {
                convert(element, page, blocks);
            }
        }
        return blocks;
    }

    /**
     * Converts a table and the figures following it: the figures are displayed in the last column of the table when
     * its cells are all empty (the figures were drawn there), the table being split in sections, each starting at the
     * row next to the top of its figure; the figures are displayed after the table otherwise.
     */
    private void convertTable(PDStructureElement table, List<PDStructureElement> figures, PDPage parentPage,
        List<Block> blocks)
    {
        PDPage page = PdfStructure.getPage(table, parentPage);
        Optional<TableBlock> tableBlock = this.tableConverter.convert(table, page);
        List<ImageBlock> figureImages = new ArrayList<>();
        List<Position> figureTops = new ArrayList<>();
        for (PDStructureElement figure : figures) {
            PDPage figurePage = PdfStructure.getPage(figure, page);
            ImageBlock image = this.images.extract(figure, figurePage);
            if (image != null) {
                figureImages.add(image);
                figureTops.add(new Position(this.images.getPageIndex(figurePage),
                    this.images.getTop(figure, figurePage)));
            }
        }
        List<TableBlock> sections = tableBlock.isEmpty() || figureImages.isEmpty() ? List.of()
            : getSections(table, page, tableBlock.get(), figureImages, figureTops);
        if (sections.isEmpty()) {
            tableBlock.ifPresent(blocks::add);
            figureImages.forEach(image -> blocks.add(new ParagraphBlock(List.of(image))));
        } else {
            blocks.addAll(sections);
        }
    }

    /**
     * @return the sections of the table with their figures, empty if the figures can't be displayed in the table: each
     *     figure starts a section at the last title row above it (a row whose first cell is a header or bold, e.g. "B
     *     BEATMUNG"), the PDF not telling where the cells of the rows start; the rows above the first section are a
     *     section without figure
     */
    private List<TableBlock> getSections(PDStructureElement table, PDPage page, TableBlock tableBlock,
        List<ImageBlock> figureImages, List<Position> figureTops)
    {
        List<Position> rowTops = getRowTops(table, page, figureTops.get(0).page());
        List<Block> rows = tableBlock.getChildren();
        List<Integer> sections = new ArrayList<>();
        List<List<Block>> sectionFigures = new ArrayList<>();
        for (int i = 0; i < figureImages.size(); i++) {
            Position figureTop = figureTops.get(i).below(ROW_TOLERANCE);
            int start = 0;
            for (int row = 0; row < rows.size() && rowTops.get(row).compareTo(figureTop) <= 0; row++) {
                if (isTitle(rows.get(row))) {
                    start = row;
                }
            }
            if (sections.isEmpty() && start > 0) {
                // The rows above the first figure, e.g. the end of a list continued from the previous page: a section
                // without figure.
                sections.add(0);
                sectionFigures.add(new ArrayList<>());
            }
            if (sections.isEmpty() || start > sections.get(sections.size() - 1)) {
                sections.add(start);
                sectionFigures.add(new ArrayList<>());
            }
            sectionFigures.get(sectionFigures.size() - 1).add(figureImages.get(i));
        }
        return PdfTableConverter.addFigureColumns(tableBlock, sections, sectionFigures);
    }

    private static boolean isTitle(Block row)
    {
        List<Block> cells = row.getChildren();
        if (cells.isEmpty()) {
            return false;
        }
        List<Block> content = cells.get(0).getChildren();
        return cells.get(0) instanceof TableHeadCellBlock || (!content.isEmpty() && content.stream().allMatch(
            block -> block instanceof FormatBlock format && format.getFormat() == Format.BOLD));
    }

    /**
     * @param firstPage the index of the page of the table when its rows don't tell it, e.g. the page of its figures
     * @return the position of the top of the text of each row of the table; a row without text is at the position of
     *     the previous row
     */
    private List<Position> getRowTops(PDStructureElement table, PDPage page, int firstPage)
    {
        List<Position> tops = new ArrayList<>();
        int tablePage = this.images.getPageIndex(page);
        Position previous = new Position(tablePage < 0 ? firstPage : tablePage, 0);
        for (PDStructureElement row : this.tableConverter.getCellRows(table)) {
            PDPage rowPage = PdfStructure.getPage(row, page);
            float top = Float.MAX_VALUE;
            for (TextPosition position : this.structure.getText(row, rowPage, child -> false)) {
                top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
            }
            int pageIndex = this.images.getPageIndex(rowPage);
            if (pageIndex < 0) {
                // The rows continued on the next page start higher on it.
                pageIndex = previous.page() + (top < previous.top() ? 1 : 0);
            }
            Position rowTop = top == Float.MAX_VALUE ? previous : new Position(pageIndex, top);
            tops.add(rowTop);
            previous = rowTop;
        }
        return tops;
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

    /**
     * A position in the PDF, from the top of its first page.
     *
     * @param page the index of the page
     * @param top the distance from the top of the page, in points
     */
    private record Position(int page, float top) implements Comparable<Position>
    {
        Position below(float distance)
        {
            return new Position(this.page, this.top + distance);
        }

        @Override
        public int compareTo(Position other)
        {
            return this.page == other.page ? Float.compare(this.top, other.top)
                : Integer.compare(this.page, other.page);
        }
    }
}
