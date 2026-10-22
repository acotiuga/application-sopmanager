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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;

/**
 * The text of the pages of a tagged PDF: the text of each marked content, referenced by the elements of the structure
 * tree, and the text of the artifacts, i.e. what is not part of the content, such as the page header and footer.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfPageText
{
    private static final String ARTIFACT = "Artifact";

    private final Map<PDPage, Map<Integer, List<TextPosition>>> markedContents = new HashMap<>();

    private final Map<PDPage, List<TextPosition>> artifacts = new HashMap<>();

    private final Map<PDPage, Float> leftMargins = new HashMap<>();

    /**
     * Extracts the text of the pages of a PDF.
     *
     * @param document the PDF
     * @throws IOException if a page can't be read
     */
    public PdfPageText(PDDocument document) throws IOException
    {
        for (PDPage page : document.getPages()) {
            PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
            extractor.processPage(page);
            Map<Integer, List<TextPosition>> texts = new HashMap<>();
            List<TextPosition> pageArtifacts = new ArrayList<>();
            for (PDMarkedContent content : extractor.getMarkedContents()) {
                collect(content, -1, false, texts, pageArtifacts);
            }
            this.markedContents.put(page, texts);
            this.artifacts.put(page, pageArtifacts);
            texts.values().stream().flatMap(List::stream).map(TextPosition::getXDirAdj).min(Float::compare)
                .ifPresent(margin -> this.leftMargins.put(page, margin));
        }
    }

    private static void collect(PDMarkedContent content, int parentId, boolean inArtifact,
        Map<Integer, List<TextPosition>> texts, List<TextPosition> artifacts)
    {
        boolean artifact = inArtifact || ARTIFACT.equals(content.getTag());
        // A marked content without identifier is a part of its parent.
        int id = content.getMCID() >= 0 ? content.getMCID() : parentId;
        for (Object item : content.getContents()) {
            if (item instanceof TextPosition position) {
                if (artifact) {
                    artifacts.add(position);
                } else if (id >= 0) {
                    texts.computeIfAbsent(id, key -> new ArrayList<>()).add(position);
                }
            } else if (item instanceof PDMarkedContent child) {
                collect(child, id, artifact, texts, artifacts);
            }
        }
    }

    /**
     * @param page a page
     * @param id the identifier of a marked content of the page
     * @return the text of the marked content, empty if there's none
     */
    public List<TextPosition> get(PDPage page, int id)
    {
        return page == null ? List.of() : this.markedContents.getOrDefault(page, Map.of()).getOrDefault(id, List.of());
    }

    /**
     * @param page a page
     * @return the horizontal position of the leftmost character of the content of the page (not of its artifacts),
     *     the start of its lines that are not indented
     */
    public float getLeftMargin(PDPage page)
    {
        return this.leftMargins.getOrDefault(page, 0F);
    }

    /**
     * @param page a page
     * @return the text of the artifacts of the page, such as its header and footer
     */
    public List<TextPosition> getArtifacts(PDPage page)
    {
        return this.artifacts.getOrDefault(page, List.of());
    }
}
