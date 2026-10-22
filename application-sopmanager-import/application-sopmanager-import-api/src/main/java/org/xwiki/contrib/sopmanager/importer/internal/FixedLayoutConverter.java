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

import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.PPR;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.attribute;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.lines;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.w3c.dom.Element;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;

/**
 * Converts the content of a Word document with a fixed layout, as produced by the conversion of a PDF document: each
 * line of text is a paragraph positioned in a frame on the page, the page header and footer are repeated in the
 * content of each page, and the borders of the tables are images behind the text.
 * <p>
 * The consecutive paragraphs of the same frame are joined, the frames on the same line form the cells of a table row
 * (the frames touching each other being joined in the same cell), and the frames of the page header and footer are
 * removed: the frames of the code of the document at the top of the pages, and the frames at or below the labels of
 * the release details ("Freigabe am") at the bottom of the pages.
 *
 * @version $Id$
 * @since 1.0
 */
public class FixedLayoutConverter
{
    /**
     * The tolerance, in twentieths of a point, when comparing the vertical position of the frames.
     */
    private static final int FOOTER_TOLERANCE = 50;

    /**
     * The maximum gap, in twentieths of a point, between frames joined in the same cell.
     */
    private static final int JOIN_GAP = 60;

    private final DocxInlineConverter inlineConverter;

    private final DocxBlockConverter blockConverter;

    /**
     * The consecutive paragraphs of a frame.
     */
    private static final class Frame
    {
        private final int x;

        private final int y;

        private int width;

        private final List<Element> paragraphs = new ArrayList<>();

        Frame(int x, int y, int width)
        {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        List<String> lines()
        {
            List<String> lines = new ArrayList<>();
            this.paragraphs.forEach(paragraph -> lines.addAll(DocxXml.lines(paragraph)));
            return lines;
        }
    }

    /**
     * @param inlineConverter converts the content of the paragraphs
     * @param blockConverter converts the paragraphs that are not positioned in frames (e.g. the images)
     */
    public FixedLayoutConverter(DocxInlineConverter inlineConverter, DocxBlockConverter blockConverter)
    {
        this.inlineConverter = inlineConverter;
        this.blockConverter = blockConverter;
    }

    /**
     * @param elements the paragraphs of the content
     * @param details where to store the details of the page header and footer
     * @return the blocks of the content, without the page header and footer
     */
    public List<Block> convert(List<Element> elements, SOPDocumentDetails details)
    {
        List<Object> items = group(elements);
        HeaderFooter headerFooter = readHeaderFooter(items, details);
        List<Block> blocks = new ArrayList<>();
        List<Frame> line = new ArrayList<>();
        List<Block> rows = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Frame frame) {
                if (!headerFooter.contains(frame)) {
                    if (!line.isEmpty() && line.get(0).y != frame.y) {
                        addLine(line, rows, blocks);
                        line = new ArrayList<>();
                    }
                    line.add(frame);
                }
            } else {
                addLine(line, rows, blocks);
                line = new ArrayList<>();
                flushRows(rows, blocks);
                blocks.addAll(this.blockConverter.convert(List.of((Element) item)));
            }
        }
        addLine(line, rows, blocks);
        flushRows(rows, blocks);
        return blocks;
    }

    /**
     * The position of the page header and footer of a fixed layout.
     *
     * @param header the frame of the code of the document at the top of the pages, {@code null} if there's none
     * @param footerY the vertical position of the labels of the release details at the bottom of the pages
     */
    private record HeaderFooter(Frame header, int footerY)
    {
        boolean contains(Frame frame)
        {
            return (this.header != null && frame.x == this.header.x && frame.y == this.header.y)
                || frame.y >= this.footerY - FOOTER_TOLERANCE;
        }
    }

    /**
     * Reads the details of the page header and footer: the first frame starting with the code of the document, the
     * frames starting with a label of the release details.
     */
    private static HeaderFooter readHeaderFooter(List<Object> items, SOPDocumentDetails details)
    {
        Frame header = null;
        int footerY = Integer.MAX_VALUE;
        for (Object item : items) {
            List<String> lines = item instanceof Frame frame ? frame.lines() : List.of();
            if (lines.isEmpty()) {
                continue;
            }
            Frame frame = (Frame) item;
            details.readOriginalDocument(String.join(" ", lines));
            if (header == null && SOPDocumentDetails.CODE.matcher(lines.get(0)).find()) {
                header = frame;
                details.readHeader(List.of(lines));
            } else if (SOPDocumentDetails.getField(lines.get(0)) != null) {
                footerY = Math.min(footerY, frame.y);
                details.setValue(lines.get(0), String.join(" ", lines.subList(1, lines.size())));
            } else if (details.readInline(String.join(" ", lines), 1)) {
                footerY = Math.min(footerY, frame.y);
            }
        }
        return new HeaderFooter(header, footerY);
    }

    /**
     * Groups the consecutive paragraphs of the same frame.
     *
     * @return the frames, and the paragraphs that are not positioned in a frame
     */
    private static List<Object> group(List<Element> elements)
    {
        List<Object> items = new ArrayList<>();
        Frame current = null;
        for (Element element : DocxBlockConverter.flatten(elements)) {
            Element framePr = path(element, PPR, "framePr");
            if (framePr == null) {
                current = null;
                items.add(element);
                continue;
            }
            int x = parse(attribute(framePr, "x"));
            int y = parse(attribute(framePr, "y"));
            if (current == null || current.x != x || current.y != y) {
                current = new Frame(x, y, parse(attribute(framePr, "w")));
                items.add(current);
            }
            current.paragraphs.add(element);
        }
        return items;
    }

    /**
     * Adds a line of frames: a paragraph if there's a single cell, a table row otherwise.
     */
    private void addLine(List<Frame> line, List<Block> rows, List<Block> blocks)
    {
        if (line.isEmpty()) {
            return;
        }
        List<List<Frame>> cells = new ArrayList<>();
        Frame previous = null;
        for (Frame frame : line.stream().sorted(Comparator.comparingInt(frame -> frame.x)).toList()) {
            if (previous != null && previous.x + previous.width + JOIN_GAP >= frame.x) {
                cells.get(cells.size() - 1).add(frame);
            } else {
                cells.add(new ArrayList<>(List.of(frame)));
            }
            previous = frame;
        }
        if (cells.size() == 1) {
            flushRows(rows, blocks);
            List<Block> content = convertCell(cells.get(0));
            if (!content.isEmpty()) {
                blocks.add(new ParagraphBlock(content));
            }
        } else {
            List<Block> row = new ArrayList<>();
            cells.forEach(cell -> row.add(new TableCellBlock(convertCell(cell))));
            rows.add(new TableRowBlock(row));
        }
    }

    /**
     * @return the content of frames: the frames of a line separated by spaces, the paragraphs of a frame separated by
     *     new lines
     */
    private List<Block> convertCell(List<Frame> frames)
    {
        List<Block> content = new ArrayList<>();
        for (Frame frame : frames) {
            for (Element paragraph : frame.paragraphs) {
                List<Block> after = new ArrayList<>();
                List<Block> inline = this.inlineConverter.convert(paragraph, after);
                if (!inline.isEmpty()) {
                    if (!content.isEmpty()) {
                        content.add(frame.paragraphs.indexOf(paragraph) == 0 ? new SpaceBlock() : new NewLineBlock());
                    }
                    content.addAll(inline);
                }
            }
        }
        return content;
    }

    private static void flushRows(List<Block> rows, List<Block> blocks)
    {
        if (!rows.isEmpty()) {
            blocks.add(new TableBlock(new ArrayList<>(rows)));
            rows.clear();
        }
    }

    private static int parse(String value)
    {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @param paragraphs the paragraphs of a document
     * @return whether most of the paragraphs with text are positioned in frames
     */
    public static boolean isFixedLayout(List<Element> paragraphs)
    {
        int text = 0;
        int framed = 0;
        for (Element paragraph : paragraphs) {
            if (!lines(paragraph).isEmpty()) {
                text++;
                if (DocxStyles.isFramed(paragraph)) {
                    framed++;
                }
            }
        }
        return text > 0 && framed * 2 > text;
    }
}
