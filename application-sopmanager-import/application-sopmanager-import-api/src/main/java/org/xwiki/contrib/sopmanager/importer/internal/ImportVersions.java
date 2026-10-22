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

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion.Origin;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

/**
 * Compares the versions of a document to import (e.g. A.doc, A.docx and A.pdf): where each comes from, which one is
 * the original, and which one to import, the one keeping the most of the content and of its formatting:
 * <ol>
 * <li>a Word document written with Word, or converted by Word from a Word 97-2003 document (same content);</li>
 * <li>a Word 97-2003 document, converted by the office server;</li>
 * <li>a PDF exported from Word, with its structure;</li>
 * <li>a Word document whose page header and footer are in the content (rebuilt);</li>
 * <li>a Word document converted from a PDF, with a fixed layout.</li>
 * </ol>
 *
 * @version $Id$
 * @since 1.0
 */
public final class ImportVersions
{
    /**
     * The compatibility mode of Word 2010: a Word document in an older mode was converted from a Word 97-2003
     * document.
     */
    private static final int WORD_2010 = 14;

    private static final Map<Origin, Integer> RANKS = Map.of(Origin.ORIGINAL, 5, Origin.CONVERTED_FROM_DOC, 5,
        Origin.EXPORTED_TO_PDF, 3, Origin.REBUILT, 2, Origin.CONVERTED_FROM_PDF, 1, Origin.UNKNOWN, 0);

    private static final int WORD_97_RANK = 4;

    private static final int PERCENT = 100;

    private ImportVersions()
    {
    }

    /**
     * @param format the format of the file: its extension
     * @param document the document read from the file
     * @return where the file comes from
     */
    public static Origin getOrigin(String format, ImportedDocument document)
    {
        if (DocumentReader.DOC.equals(format)) {
            return Origin.ORIGINAL;
        }
        if (DocumentReader.PDF.equals(format)) {
            return Origin.EXPORTED_TO_PDF;
        }
        DocumentSource source = document.source();
        String application = StringUtils.defaultString(source.application()).toLowerCase(Locale.ROOT);
        if (document.compatibility() == Compatibility.FIXED_LAYOUT || application.contains("aspose")) {
            return Origin.CONVERTED_FROM_PDF;
        }
        if (document.compatibility() == Compatibility.HEADER_FOOTER_IN_CONTENT) {
            return Origin.REBUILT;
        }
        boolean fromWord97 = Strings.CI.endsWith(source.originalDocument(), "." + DocumentReader.DOC)
            || source.compatibilityMode() != null && source.compatibilityMode() < WORD_2010;
        return fromWord97 ? Origin.CONVERTED_FROM_DOC : Origin.ORIGINAL;
    }

    /**
     * Marks the original document and the recommended version, puts the recommended version first, and compares the
     * text of the other versions with it.
     *
     * @param candidate a document to import, with its versions
     * @param documents the documents read from the versions that could be read
     */
    public static void compare(SOPImportCandidate candidate, Map<SOPImportVersion, ImportedDocument> documents)
    {
        List<SOPImportVersion> versions = candidate.getVersions();
        SOPImportVersion recommended = versions.stream().filter(SOPImportVersion::isImportable)
            .max(Comparator.comparingInt(ImportVersions::getRank)).orElse(null);
        SOPImportVersion original = getOriginal(versions, documents);
        if (original != null) {
            original.setOriginal(true);
        }
        if (recommended != null) {
            recommended.setRecommended(true);
            versions.remove(recommended);
            versions.add(0, recommended);
            Map<String, Integer> words = getWords(documents.get(recommended).content());
            for (SOPImportVersion version : versions.subList(1, versions.size())) {
                ImportedDocument document = documents.get(version);
                version.setTextMatch(document == null ? null : getTextMatch(words, getWords(document.content())));
            }
        }
        candidate.setMissingOriginal(getMissingOriginal(versions, documents));
    }

    private static int getRank(SOPImportVersion version)
    {
        return DocumentReader.DOC.equals(version.getFormat()) ? WORD_97_RANK : RANKS.get(version.getOrigin());
    }

    /**
     * @return the oldest Word document, else the PDF; {@code null} if there are only conversions of a PDF
     */
    private static SOPImportVersion getOriginal(List<SOPImportVersion> versions,
        Map<SOPImportVersion, ImportedDocument> documents)
    {
        SOPImportVersion word97 = find(versions, DocumentReader.DOC);
        SOPImportVersion word = versions.stream().filter(version -> version.getOrigin() == Origin.ORIGINAL
            && DocumentReader.DOCX.equals(version.getFormat())).findFirst().orElse(null);
        if (word97 != null || word != null) {
            return getOlder(word97, word, documents);
        }
        // A Word document converted from a Word 97-2003 document is not the original; a PDF that can't be read (e.g. a
        // scan) tells nothing about where it comes from.
        SOPImportVersion pdf = find(versions, DocumentReader.PDF);
        boolean converted = versions.stream().anyMatch(version -> version.getOrigin() == Origin.CONVERTED_FROM_DOC);
        return !converted && pdf != null && documents.containsKey(pdf) ? pdf : null;
    }

    /**
     * @return the older of the Word 97-2003 document and the Word document, the Word 97-2003 document by default
     */
    private static SOPImportVersion getOlder(SOPImportVersion word97, SOPImportVersion word,
        Map<SOPImportVersion, ImportedDocument> documents)
    {
        if (word97 == null || word == null) {
            return word97 == null ? word : word97;
        }
        Instant created = getCreated(word, documents);
        Instant word97Created = getCreated(word97, documents);
        return created != null && word97Created != null && created.isBefore(word97Created) ? word : word97;
    }

    private static SOPImportVersion find(List<SOPImportVersion> versions, String format)
    {
        return versions.stream().filter(version -> format.equals(version.getFormat())).findFirst().orElse(null);
    }

    private static Instant getCreated(SOPImportVersion version, Map<SOPImportVersion, ImportedDocument> documents)
    {
        ImportedDocument document = documents.get(version);
        return document == null ? null : document.source().created();
    }

    /**
     * @return the original document printed in a version, if it is not one of the versions
     */
    private static String getMissingOriginal(List<SOPImportVersion> versions,
        Map<SOPImportVersion, ImportedDocument> documents)
    {
        for (ImportedDocument document : documents.values()) {
            String path = document.source().originalDocument();
            if (path != null) {
                String fileName = path.substring(Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/')) + 1);
                if (versions.stream().noneMatch(version -> version.getFileName().equalsIgnoreCase(fileName))) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * @param content the content of a document
     * @return the number of occurrences of each word of the content, in lower case
     */
    static Map<String, Integer> getWords(Block content)
    {
        Map<String, Integer> words = new HashMap<>();
        for (Block block : content.getBlocks(new ClassBlockMatcher(WordBlock.class), Block.Axes.DESCENDANT)) {
            words.merge(((WordBlock) block).getWord().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        return words;
    }

    /**
     * @return the percentage of the words of a text found in another one: the words they share, relative to the
     *     average number of words of the two texts
     */
    static int getTextMatch(Map<String, Integer> words, Map<String, Integer> otherWords)
    {
        int total = 0;
        int shared = 0;
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            total += entry.getValue();
            shared += Math.min(entry.getValue(), otherWords.getOrDefault(entry.getKey(), 0));
        }
        total += otherWords.values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? PERCENT : Math.round(2F * PERCENT * shared / total);
    }
}
