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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion.Origin;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.XDOM;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ImportVersions}.
 *
 * @version $Id$
 */
class ImportVersionsTest
{
    private static final Instant OLD = Instant.parse("2014-01-24T10:00:00Z");

    private static final Instant NEW = Instant.parse("2026-07-17T10:00:00Z");

    private static ImportedDocument document(String text, Compatibility compatibility, DocumentSource source)
    {
        return new ImportedDocument(new XDOM(List.of(new ParagraphBlock(DocxInlineConverter.text(text)))), Map.of(),
            new SOPDocumentDetails(), compatibility, false, List.of(), source);
    }

    private static DocumentSource source(String application, Instant created, String originalDocument,
        Integer compatibilityMode)
    {
        return new DocumentSource(application, null, created, null, null, originalDocument, compatibilityMode);
    }

    @Test
    void getOrigin()
    {
        ImportedDocument word = document("", Compatibility.COMPATIBLE, DocumentSource.UNKNOWN);
        assertEquals(Origin.ORIGINAL, ImportVersions.getOrigin("doc", word));
        assertEquals(Origin.EXPORTED_TO_PDF, ImportVersions.getOrigin("pdf", word));
        assertEquals(Origin.ORIGINAL, ImportVersions.getOrigin("docx", word));
        assertEquals(Origin.CONVERTED_FROM_PDF, ImportVersions.getOrigin("docx",
            document("", Compatibility.FIXED_LAYOUT, DocumentSource.UNKNOWN)));
        // Converted by Aspose, then saved by Word.
        assertEquals(Origin.CONVERTED_FROM_PDF, ImportVersions.getOrigin("docx",
            document("", Compatibility.COMPATIBLE, source("Aspose", null, null, null))));
        assertEquals(Origin.REBUILT, ImportVersions.getOrigin("docx",
            document("", Compatibility.HEADER_FOOTER_IN_CONTENT, DocumentSource.UNKNOWN)));
        assertEquals(Origin.CONVERTED_FROM_DOC, ImportVersions.getOrigin("docx",
            document("", Compatibility.COMPATIBLE, source(null, null, "M:\\QM\\A.DOC", null))));
        assertEquals(Origin.CONVERTED_FROM_DOC, ImportVersions.getOrigin("docx",
            document("", Compatibility.COMPATIBLE, source(null, null, null, 12))));
        assertEquals(Origin.ORIGINAL, ImportVersions.getOrigin("docx",
            document("", Compatibility.COMPATIBLE, source(null, null, "M:\\QM\\A.pdf", 15))));
    }

    private static SOPImportVersion version(String fileName, ImportedDocument document,
        Map<SOPImportVersion, ImportedDocument> documents)
    {
        SOPImportVersion version = new SOPImportVersion(fileName, fileName.substring(fileName.indexOf('.') + 1));
        if (document != null) {
            ImportVersionDescription.describe(version, document);
            documents.put(version, document);
        }
        return version;
    }

    private static SOPImportCandidate candidate(SOPImportVersion... versions)
    {
        SOPImportCandidate candidate = new SOPImportCandidate("A", new DocumentReference("xwiki", "SOPManager", "A"),
            false);
        candidate.getVersions().addAll(List.of(versions));
        return candidate;
    }

    @Test
    void compareTheOriginalWordDocumentWithItsConversions()
    {
        Map<SOPImportVersion, ImportedDocument> documents = new HashMap<>();
        // The Word 97-2003 document can't be converted (no office server): the newer Word document is recommended.
        SOPImportVersion word97 = new SOPImportVersion("A.doc", "doc");
        word97.setCompatibility(Compatibility.NOT_SUPPORTED);
        SOPImportVersion pdf = version("A.pdf",
            document("Die Hände werden desinfiziert", Compatibility.TAGGED_PDF, DocumentSource.UNKNOWN), documents);
        SOPImportVersion word = version("A.docx", document("Die Hände werden gründlich desinfiziert",
            Compatibility.COMPATIBLE, source("Microsoft Office Word", NEW, null, 15)), documents);
        SOPImportCandidate candidate = candidate(word97, pdf, word);

        ImportVersions.compare(candidate, documents);

        assertEquals(List.of(word, word97, pdf), candidate.getVersions());
        assertSame(word, candidate.getRecommended());
        assertSame(word97, candidate.getOriginal());
        assertNull(word.getTextMatch());
        assertNull(word97.getTextMatch());
        // 4 words of 5 and 4.
        assertEquals(89, pdf.getTextMatch());
        assertNull(candidate.getMissingOriginal());
    }

    @Test
    void compareWordDocumentOlderThanTheWord97Document()
    {
        Map<SOPImportVersion, ImportedDocument> documents = new HashMap<>();
        SOPImportVersion word = version("A.docx", document("Text", Compatibility.COMPATIBLE,
            source(null, OLD, null, null)), documents);
        SOPImportVersion word97 = version("A.doc", document("Text", Compatibility.COMPATIBLE,
            source(null, NEW, null, null)), documents);
        SOPImportCandidate candidate = candidate(word, word97);

        ImportVersions.compare(candidate, documents);

        assertTrue(word.isOriginal());
        assertTrue(word.isRecommended());
        assertFalse(word97.isOriginal());
        assertEquals(100, word97.getTextMatch());
    }

    @Test
    void compareConversionsWithoutOriginal()
    {
        Map<SOPImportVersion, ImportedDocument> documents = new HashMap<>();
        // A Word document converted from a Word 97-2003 document that is not there, and the PDF exported from it.
        SOPImportVersion word = version("A.docx", document("Text", Compatibility.COMPATIBLE,
            source(null, null, "W:\\QM\\A.doc", null)), documents);
        SOPImportVersion pdf = version("A.pdf", document("Text", Compatibility.TAGGED_PDF,
            source(null, null, "W:\\QM\\A.doc", null)), documents);
        SOPImportCandidate candidate = candidate(pdf, word);

        ImportVersions.compare(candidate, documents);

        assertSame(word, candidate.getRecommended());
        assertNull(candidate.getOriginal());
        assertEquals("W:\\QM\\A.doc", candidate.getMissingOriginal());
    }

    @Test
    void compareWithoutImportableVersion()
    {
        SOPImportVersion scan = new SOPImportVersion("A.pdf", "pdf");
        scan.setCompatibility(Compatibility.NOT_SUPPORTED);
        SOPImportCandidate candidate = candidate(scan);

        ImportVersions.compare(candidate, Map.of());

        assertNull(candidate.getRecommended());
        assertNull(candidate.getOriginal());
        assertNull(scan.getTextMatch());
    }

    @Test
    void getTextMatch()
    {
        assertEquals(100, ImportVersions.getTextMatch(Map.of(), Map.of()));
        assertEquals(0, ImportVersions.getTextMatch(Map.of("a", 1), Map.of("b", 1)));
        assertEquals(50, ImportVersions.getTextMatch(Map.of("a", 1), Map.of("a", 1, "b", 2)));
    }
}
