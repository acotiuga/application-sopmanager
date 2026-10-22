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
        for (PDStructureElement row : getRows(table)) {
            List<Block> cells = new ArrayList<>();
            PDPage rowPage = PdfStructure.getPage(row, page);
            for (PDStructureElement cell : PdfStructure.getElements(row)) {
                String type = this.structure.getType(cell);
                if (HEADER_CELL.equals(type) || "TD".equals(type)) {
                    cells.add(convertCell(cell, PdfStructure.getPage(cell, rowPage), HEADER_CELL.equals(type)));
                }
            }
            if (!cells.isEmpty()) {
                rows.add(new TableRowBlock(cells));
            }
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(new TableBlock(rows));
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
                setSpan(block, "colspan", table.getColSpan());
                setSpan(block, "rowspan", table.getRowSpan());
            }
        }
        return block;
    }

    private static void setSpan(TableCellBlock cell, String parameter, int span)
    {
        if (span > 1) {
            cell.setParameter(parameter, String.valueOf(span));
        }
    }
}
