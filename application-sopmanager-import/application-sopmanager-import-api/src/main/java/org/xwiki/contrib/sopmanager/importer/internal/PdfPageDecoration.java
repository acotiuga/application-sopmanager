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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.TextPosition;

/**
 * Reads the details of the page header and footer of a tagged PDF, marked as artifacts: the code and the title in the
 * header, the release details in the footer, either a table (a row of labels above a row of values) or a line, and
 * the path of the original document.
 *
 * @version $Id$
 * @since 1.0
 */
public final class PdfPageDecoration
{
    /**
     * The part of the page height at its top and bottom where the page header and footer are.
     */
    private static final float MARGIN = 0.3F;

    /**
     * The gap between two cells of a line, relative to the font size.
     */
    private static final float CELL_GAP = 1.2F;

    private static final float WORD_GAP = 0.2F;

    private static final float LINE_GAP = 0.5F;

    private static final String SPACE = " ";

    /**
     * The end of a label followed by another text, e.g. "Revision: Revision geplant:".
     */
    private static final Pattern LABEL_END = Pattern.compile("(?<=:)\\s+(?=\\S)");

    /**
     * A piece of text of a line, separated from the next one by a large gap, e.g. a cell of a table.
     *
     * @param text the text
     * @param start the horizontal position of its start
     * @param end the horizontal position of its end
     */
    record Segment(String text, float start, float end)
    {
        float center()
        {
            return (this.start + this.end) / 2;
        }
    }

    private PdfPageDecoration()
    {
    }

    /**
     * @param document the PDF
     * @param text the text of its pages
     * @param details where to store the details
     */
    public static void read(PDDocument document, PdfPageText text, SOPDocumentDetails details)
    {
        for (PDPage page : document.getPages()) {
            float height = page.getCropBox().getHeight();
            List<TextPosition> header = new ArrayList<>();
            List<TextPosition> footer = new ArrayList<>();
            for (TextPosition position : text.getArtifacts(page)) {
                if (position.getYDirAdj() < height * MARGIN) {
                    header.add(position);
                } else if (position.getYDirAdj() > height * (1 - MARGIN)) {
                    footer.add(position);
                }
            }
            details.readHeader(getColumns(getLines(header)));
            readFooter(getLines(footer), details);
        }
    }

    private static void readFooter(List<List<Segment>> lines, SOPDocumentDetails details)
    {
        int i = 0;
        while (i < lines.size()) {
            List<Segment> labelSegments = splitLabels(lines.get(i));
            List<String> labels = labelSegments.stream().map(Segment::text).toList();
            if (SOPDocumentDetails.countLabels(labels) >= 2 && i + 1 < lines.size()) {
                // The labels, and the values below them.
                details.readReleaseTable(List.of(labels, align(labelSegments, lines.get(i + 1))));
                i += 2;
            } else {
                String line = lines.get(i).stream().map(Segment::text).collect(Collectors.joining(SPACE));
                details.readInline(line, 2);
                details.readOriginalDocument(line);
                i++;
            }
        }
    }

    /**
     * @return the segments, the ones holding several labels followed by a colon (narrow cells close to each other)
     *     split, their width shared according to the length of the labels
     */
    private static List<Segment> splitLabels(List<Segment> segments)
    {
        List<Segment> split = new ArrayList<>();
        for (Segment segment : segments) {
            String[] parts = LABEL_END.split(segment.text());
            float width = (segment.end() - segment.start()) / Math.max(1, segment.text().length());
            float start = segment.start();
            for (String part : parts) {
                float end = start + part.length() * width;
                split.add(new Segment(part, start, end));
                start = end + width;
            }
        }
        return split;
    }

    /**
     * @return the values below the labels: the text of the segments whose center is in the column of each label
     */
    private static List<String> align(List<Segment> labels, List<Segment> values)
    {
        List<StringBuilder> columns = new ArrayList<>();
        labels.forEach(label -> columns.add(new StringBuilder()));
        for (Segment value : values) {
            int column = 0;
            while (column + 1 < labels.size()
                && value.center() > (labels.get(column).center() + labels.get(column + 1).center()) / 2) {
                column++;
            }
            StringBuilder text = columns.get(column);
            text.append(text.length() > 0 ? SPACE : "").append(value.text());
        }
        return columns.stream().map(StringBuilder::toString).toList();
    }

    /**
     * @return the lines of text, from top to bottom, each split in segments
     */
    static List<List<Segment>> getLines(List<TextPosition> positions)
    {
        List<TextPosition> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparing(TextPosition::getYDirAdj));
        List<List<TextPosition>> lines = new ArrayList<>();
        List<TextPosition> line = new ArrayList<>();
        for (TextPosition position : sorted) {
            if (!line.isEmpty() && Math.abs(position.getYDirAdj() - line.get(0).getYDirAdj())
                > size(line.get(0)) * LINE_GAP) {
                lines.add(line);
                line = new ArrayList<>();
            }
            line.add(position);
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines.stream().map(PdfPageDecoration::getSegments).filter(segments -> !segments.isEmpty()).toList();
    }

    private static List<Segment> getSegments(List<TextPosition> line)
    {
        List<TextPosition> sorted = new ArrayList<>(line);
        sorted.sort(Comparator.comparing(TextPosition::getXDirAdj));
        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        float start = 0;
        float end = 0;
        for (TextPosition position : sorted) {
            float gap = position.getXDirAdj() - end;
            if (text.length() > 0 && gap > size(position) * CELL_GAP) {
                addSegment(text.toString(), start, end, segments);
                text.setLength(0);
            }
            if (text.length() == 0) {
                start = position.getXDirAdj();
            } else if (gap > size(position) * WORD_GAP) {
                text.append(' ');
            }
            text.append(position.getUnicode());
            end = position.getXDirAdj() + position.getWidthDirAdj();
        }
        addSegment(text.toString(), start, end, segments);
        return segments;
    }

    private static void addSegment(String text, float start, float end, List<Segment> segments)
    {
        String normalized = text.replaceAll("\\s+", SPACE).trim();
        if (!normalized.isEmpty()) {
            segments.add(new Segment(normalized, start, end));
        }
    }

    private static float size(TextPosition position)
    {
        float size = position.getFontSizeInPt() > 0 ? position.getFontSizeInPt() : position.getHeightDir() * 2;
        return Math.max(1, size);
    }

    /**
     * @return the lines of text grouped in columns: the segments of consecutive lines overlapping horizontally, e.g.
     *     the right aligned code and title of a page header, next to the logo
     */
    static List<List<String>> getColumns(List<List<Segment>> lines)
    {
        List<List<Segment>> columns = new ArrayList<>();
        for (List<Segment> line : lines) {
            for (Segment segment : line) {
                List<Segment> column = columns.stream().filter(candidate -> overlaps(candidate, segment)).findFirst()
                    .orElse(null);
                if (column == null) {
                    column = new ArrayList<>();
                    columns.add(column);
                }
                column.add(segment);
            }
        }
        columns.sort(Comparator.comparing(column -> column.get(0).start()));
        return columns.stream().map(column -> column.stream().map(Segment::text).toList()).toList();
    }

    private static boolean overlaps(List<Segment> column, Segment segment)
    {
        return column.stream().anyMatch(other -> other.start() < segment.end() && segment.start() < other.end());
    }
}
