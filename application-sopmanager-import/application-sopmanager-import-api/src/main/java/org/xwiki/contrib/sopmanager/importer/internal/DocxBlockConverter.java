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
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.PPR;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.SDT;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.SDT_CONTENT;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TBL;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TC;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.TR;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.VAL;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.attribute;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.child;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.children;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.is;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.path;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Element;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;

/**
 * Converts the paragraphs, lists and tables of a Word document to rendering blocks.
 *
 * @version $Id$
 * @since 1.0
 */
public class DocxBlockConverter
{
    private static final String GRID_SPAN = "gridSpan";

    private static final String V_MERGE = "vMerge";

    private static final String ROWSPAN = "rowspan";

    private static final int MAX_LEVELS = 9;

    private static final float TWIPS_PER_CENTIMETER = 567;

    /**
     * The smallest indentation kept, in centimeters: below, the paragraph is aligned with the others.
     */
    private static final float MIN_INDENTATION = 0.5F;

    private final DocxStyles styles;

    private final DocxInlineConverter inlineConverter;

    /**
     * The number of the last item of each list, by level: Word numbers the items of a list across the whole document,
     * e.g. in the rows of a table, or after paragraphs.
     */
    private final Map<String, int[]> numbers = new HashMap<>();

    /**
     * An item of a list being built: its level, the format of its list, and its block.
     */
    private record OpenItem(int level, ListFormat format, ListItemBlock item)
    {
    }

    /**
     * @param styles the styles and numbering definitions of the document
     * @param inlineConverter converts the content of the paragraphs
     */
    public DocxBlockConverter(DocxStyles styles, DocxInlineConverter inlineConverter)
    {
        this.styles = styles;
        this.inlineConverter = inlineConverter;
    }

    /**
     * @param elements paragraphs and tables
     * @return the blocks of the elements, the consecutive list paragraphs grouped in lists
     */
    public List<Block> convert(List<Element> elements)
    {
        List<Block> blocks = new ArrayList<>();
        ListBuilder list = new ListBuilder();
        for (Element element : flatten(elements)) {
            Element numbering = is(element, P) ? path(element, PPR, "numPr") : null;
            String numId = value(numbering, "numId");
            if (!numId.isEmpty() && !"0".equals(numId)) {
                int level = Math.min(parseLevel(value(numbering, "ilvl")), MAX_LEVELS - 1);
                List<Block> after = new ArrayList<>();
                list.add(level, this.styles.getListFormat(numId, level), nextNumber(numId, level),
                    this.inlineConverter.convert(element, after));
                if (!after.isEmpty()) {
                    blocks.addAll(list.build());
                    blocks.addAll(after);
                }
            } else {
                blocks.addAll(list.build());
                if (is(element, P)) {
                    convertParagraph(element, blocks);
                } else {
                    blocks.add(convertTable(element));
                }
            }
        }
        blocks.addAll(list.build());
        return blocks;
    }

    /**
     * @param elements paragraphs, tables and structured document tags
     * @return the paragraphs and tables, with the content of the structured document tags
     */
    public static List<Element> flatten(List<Element> elements)
    {
        List<Element> flat = new ArrayList<>();
        for (Element element : elements) {
            if (is(element, SDT)) {
                flat.addAll(flatten(children(child(element, SDT_CONTENT))));
            } else if (is(element, P) || is(element, TBL)) {
                flat.add(element);
            }
        }
        return flat;
    }

    /**
     * @return the number of the next item of a list at a level, the deeper levels starting again
     */
    private int nextNumber(String numId, int level)
    {
        int[] counters = this.numbers.computeIfAbsent(numId, key -> new int[MAX_LEVELS]);
        counters[level]++;
        for (int deeper = level + 1; deeper < MAX_LEVELS; deeper++) {
            counters[deeper] = 0;
        }
        return counters[level];
    }

    private void convertParagraph(Element paragraph, List<Block> blocks)
    {
        List<Block> after = new ArrayList<>();
        List<Block> content = this.inlineConverter.convert(paragraph, after);
        if (!content.isEmpty()) {
            int level = this.styles.getHeadingLevel(paragraph);
            if (level > 0) {
                blocks.add(new HeaderBlock(unbold(content), HeaderLevel.parseInt(level)));
            } else {
                ParagraphBlock block = new ParagraphBlock(content);
                setIndentation(block, getIndentation(paragraph) / TWIPS_PER_CENTIMETER);
                blocks.add(block);
            }
        }
        blocks.addAll(after);
    }

    /**
     * @return the left indentation of a paragraph, in twips
     */
    private static int getIndentation(Element paragraph)
    {
        Element indentation = path(paragraph, PPR, "ind");
        String left = attribute(indentation, "left");
        return parseLevel(left.isEmpty() ? attribute(indentation, "start") : left);
    }

    /**
     * Indents a paragraph as in the imported document.
     *
     * @param paragraph a paragraph
     * @param centimeters its left indentation, in centimeters
     */
    public static void setIndentation(Block paragraph, float centimeters)
    {
        if (centimeters >= MIN_INDENTATION) {
            paragraph.setParameter("style", String.format(Locale.ROOT, "margin-left: %.2fcm", centimeters));
        }
    }

    /**
     * @return the content of a heading, without the bold of the whole content: the headings are bold
     */
    private static List<Block> unbold(List<Block> content)
    {
        if (content.size() == 1 && content.get(0) instanceof FormatBlock format
            && format.getFormat() == Format.BOLD) {
            return format.getChildren();
        }
        return content;
    }

    /**
     * @param table a table
     * @return the table block, the merged cells spanning the rows and columns, the header cells being the cells of
     *     the rows repeated as header rows and the shaded cells
     */
    public TableBlock convertTable(Element table)
    {
        List<Block> rows = new ArrayList<>();
        Map<Integer, TableCellBlock> mergeOrigins = new HashMap<>();
        for (Element row : children(table, TR)) {
            boolean headerRow = path(row, "trPr", "tblHeader") != null;
            List<Block> cells = new ArrayList<>();
            int column = 0;
            for (Element cell : children(row, TC)) {
                Element properties = child(cell, "tcPr");
                int span = Math.max(1, parseLevel(value(properties, GRID_SPAN)));
                Element merge = child(properties, V_MERGE);
                if (merge != null && !"restart".equals(attribute(merge, VAL))) {
                    TableCellBlock origin = mergeOrigins.get(column);
                    if (origin != null) {
                        String rowspan = origin.getParameter(ROWSPAN);
                        origin.setParameter(ROWSPAN, String.valueOf(rowspan == null ? 2 : Integer.parseInt(rowspan)
                            + 1));
                    }
                } else {
                    TableCellBlock block = convertCell(cell, headerRow || isShaded(properties), span);
                    if (merge != null) {
                        mergeOrigins.put(column, block);
                    } else {
                        mergeOrigins.remove(column);
                    }
                    cells.add(block);
                }
                column += span;
            }
            rows.add(new TableRowBlock(cells));
        }
        return new TableBlock(rows);
    }

    /**
     * @param cellProperties the properties of a cell
     * @return whether the cell has a background color, as the header cells of the tables of the SOP documents (e.g.
     *     the labels of the scope of the document, or the titles of the drawers in grey)
     */
    private static boolean isShaded(Element cellProperties)
    {
        String fill = attribute(child(cellProperties, "shd"), "fill");
        return !fill.isEmpty() && !"auto".equalsIgnoreCase(fill) && !"FFFFFF".equalsIgnoreCase(fill);
    }

    private TableCellBlock convertCell(Element cell, boolean header, int span)
    {
        List<Block> content = convertCell(cell);
        TableCellBlock block = header ? new TableHeadCellBlock(content) : new TableCellBlock(content);
        if (span > 1) {
            block.setParameter("colspan", String.valueOf(span));
        }
        return block;
    }

    private List<Block> convertCell(Element cell)
    {
        return cellContent(convert(children(cell)));
    }

    /**
     * @param blocks the blocks of a cell
     * @return the content of a cell: the content of its paragraphs separated by new lines, or a group if the cell
     *     holds other blocks than paragraphs
     */
    public static List<Block> cellContent(List<Block> blocks)
    {
        if (blocks.stream().allMatch(ParagraphBlock.class::isInstance)) {
            List<Block> content = new ArrayList<>();
            for (Block paragraph : blocks) {
                if (!content.isEmpty()) {
                    content.add(new NewLineBlock());
                }
                content.addAll(paragraph.getChildren());
            }
            return content;
        }
        return List.of(new GroupBlock(blocks));
    }

    private static int parseLevel(String value)
    {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Builds nested lists from the items of consecutive list paragraphs.
     */
    private static final class ListBuilder
    {
        private final List<Block> roots = new ArrayList<>();

        private final List<OpenItem> open = new ArrayList<>();

        private final List<Block> openLists = new ArrayList<>();

        void add(int level, ListFormat format, int number, List<Block> content)
        {
            // Close the items and lists deeper than this level, and the list of this level if its format differs (e.g.
            // bullets after numbered items).
            while (!this.open.isEmpty() && (this.open.get(this.open.size() - 1).level() > level
                || this.open.get(this.open.size() - 1).level() == level
                && !this.open.get(this.open.size() - 1).format().equals(format))) {
                this.open.remove(this.open.size() - 1);
                this.openLists.remove(this.openLists.size() - 1);
            }
            ListItemBlock item = new ListItemBlock(content);
            if (!this.open.isEmpty() && this.open.get(this.open.size() - 1).level() == level) {
                this.openLists.get(this.openLists.size() - 1).addChild(item);
                this.open.set(this.open.size() - 1, new OpenItem(level, format, item));
            } else {
                // Keep the number of the document, the lists continuing in other cells or after paragraphs.
                Block list = format.newList(number);
                list.addChild(item);
                if (this.open.isEmpty()) {
                    this.roots.add(list);
                } else {
                    this.open.get(this.open.size() - 1).item().addChild(list);
                }
                this.open.add(new OpenItem(level, format, item));
                this.openLists.add(list);
            }
        }

        /**
         * @return the lists built since the last call, the next items starting new lists
         */
        List<Block> build()
        {
            List<Block> lists = new ArrayList<>(this.roots);
            this.roots.clear();
            this.open.clear();
            this.openLists.clear();
            return lists;
        }
    }
}
