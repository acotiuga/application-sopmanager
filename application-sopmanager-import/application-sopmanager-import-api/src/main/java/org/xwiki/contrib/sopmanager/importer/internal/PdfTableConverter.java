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
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.Revisions;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;

/**
 * Converts the tables of a tagged PDF: their rows (possibly grouped in a head, a body and a foot), their cells and
 * the cells spanning several rows or columns, when the PDF tells it.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfTableConverter
{
    private static final Set<String> ROW_GROUPS = Set.of("THead", "TBody", "TFoot");

    private static final String ROW = "TR";

    private static final String HEADER_CELL = "TH";

    private static final String COLSPAN = "colspan";

    private static final String ROWSPAN = "rowspan";

    private final PdfStructure structure;

    private final PdfContentConverter contentConverter;

    /**
     * @param structure the structure tree of the PDF
     * @param contentConverter converts the content of the cells
     */
    public PdfTableConverter(PdfStructure structure, PdfContentConverter contentConverter)
    {
        this.structure = structure;
        this.contentConverter = contentConverter;
    }

    /**
     * @param table a table of the structure tree
     * @param page the page of the table
     * @return the table block, empty if the table has no cell
     */
    public Optional<TableBlock> convert(PDStructureElement table, PDPage page)
    {
        List<Block> rows = new ArrayList<>();
        for (PDStructureElement row : getCellRows(table)) {
            List<Block> cells = new ArrayList<>();
            PDPage rowPage = PdfStructure.getPage(row, page);
            for (PDStructureElement cell : getCells(row)) {
                String type = this.structure.getType(cell);
                cells.add(convertCell(cell, PdfStructure.getPage(cell, rowPage), HEADER_CELL.equals(type)));
            }
            rows.add(new TableRowBlock(cells));
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(new TableBlock(rows));
    }

    /**
     * @param table a table of the structure tree
     * @return its rows that have cells: the rows of the table block it's converted to
     */
    public List<PDStructureElement> getCellRows(PDStructureElement table)
    {
        return getRows(table).stream().filter(row -> !getCells(row).isEmpty()).toList();
    }

    private List<PDStructureElement> getCells(PDStructureElement row)
    {
        return PdfStructure.getElements(row).stream().filter(cell -> {
            String type = this.structure.getType(cell);
            return HEADER_CELL.equals(type) || "TD".equals(type);
        }).toList();
    }

    private List<PDStructureElement> getRows(PDStructureElement element)
    {
        List<PDStructureElement> rows = new ArrayList<>();
        for (PDStructureElement child : PdfStructure.getElements(element)) {
            String type = this.structure.getType(child);
            if (ROW.equals(type)) {
                rows.add(child);
            } else if (ROW_GROUPS.contains(type)) {
                rows.addAll(getRows(child));
            }
        }
        return rows;
    }

    private TableCellBlock convertCell(PDStructureElement cell, PDPage page, boolean header)
    {
        List<Block> blocks = new ArrayList<>();
        List<Block> ownText = PdfInlineConverter.convert(PdfInlineConverter.glyphs(
            this.structure.getOwnText(cell, page)));
        if (!ownText.isEmpty()) {
            blocks.add(new ParagraphBlock(ownText));
        }
        blocks.addAll(this.contentConverter.convertCell(cell, page));
        List<Block> content = DocxBlockConverter.cellContent(blocks);
        TableCellBlock block = header ? new TableHeadCellBlock(content) : new TableCellBlock(content);
        Revisions<PDAttributeObject> attributes = cell.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.getObject(i) instanceof PDTableAttributeObject table) {
                setSpan(block, COLSPAN, table.getColSpan());
                setSpan(block, ROWSPAN, table.getRowSpan());
            }
        }
        return block;
    }

    /**
     * Displays figures in the last column of a table when all its cells are empty, e.g. the icons of a list of
     * equipment drawn next to the sections of the list: the table is split in one table per section, the cells of the
     * last column of each section merged into a single cell holding the figures of the section, spanning its rows.
     * The PDF doesn't tell which cells span several columns, so the rows with fewer cells (e.g. a title, or an item
     * spanning its size column) get their first cell widened to keep the columns aligned.
     *
     * @param table a table
     * @param sections the first row of each section of the table, the first one being 0, in increasing order
     * @param figures the figures of each section, none for a section without figure (its last column is kept, empty)
     * @return the tables of the sections with their figures, empty if the figures can't be displayed in the table
     */
    public static List<TableBlock> addFigureColumns(TableBlock table, List<Integer> sections,
        List<List<Block>> figures)
    {
        List<Block> rows = new ArrayList<>(table.getChildren());
        if (figures.stream().allMatch(List::isEmpty) || rows.stream().anyMatch(row -> !hasEmptyLastCell(row))) {
            return List.of();
        }
        int width = rows.stream().mapToInt(PdfTableConverter::getWidth).max().orElse(0);
        for (Block row : rows) {
            int rowWidth = getWidth(row);
            row.removeBlock(row.getChildren().get(row.getChildren().size() - 1));
            if (rowWidth < width && !row.getChildren().isEmpty()) {
                TableCellBlock first = (TableCellBlock) row.getChildren().get(0);
                setSpan(first, COLSPAN, getSpan(first, COLSPAN) + width - rowWidth);
            }
        }
        List<TableBlock> tables = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            int end = i + 1 < sections.size() ? sections.get(i + 1) : rows.size();
            List<Block> sectionRows = new ArrayList<>(rows.subList(sections.get(i), end));
            List<Block> content = new ArrayList<>();
            for (Block figure : figures.get(i)) {
                content.add(new ParagraphBlock(List.of(figure)));
            }
            TableCellBlock column = new TableCellBlock(DocxBlockConverter.cellContent(content));
            setSpan(column, ROWSPAN, sectionRows.size());
            column.setParameter("style", "vertical-align: top; text-align: center;");
            sectionRows.get(0).addChild(column);
            TableBlock sectionTable = new TableBlock(sectionRows);
            sectionTable.setParameters(table.getParameters());
            tables.add(sectionTable);
        }
        return tables;
    }

    /**
     * @return whether the last cell of the row is empty, and spans neither several rows nor several columns
     */
    private static boolean hasEmptyLastCell(Block row)
    {
        List<Block> cells = row.getChildren();
        if (cells.size() < 2 || cells.stream().anyMatch(cell -> cell.getParameter(ROWSPAN) != null)) {
            return false;
        }
        Block last = cells.get(cells.size() - 1);
        return last.getChildren().isEmpty() && last.getParameter(COLSPAN) == null;
    }

    /**
     * @return the number of columns of the row
     */
    private static int getWidth(Block row)
    {
        return row.getChildren().stream().mapToInt(cell -> getSpan(cell, COLSPAN)).sum();
    }

    private static int getSpan(Block cell, String parameter)
    {
        try {
            return Math.max(1, Integer.parseInt(StringUtils.defaultString(cell.getParameter(parameter), "1")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void setSpan(TableCellBlock cell, String parameter, int span)
    {
        if (span > 1) {
            cell.setParameter(parameter, String.valueOf(span));
        }
    }
}
