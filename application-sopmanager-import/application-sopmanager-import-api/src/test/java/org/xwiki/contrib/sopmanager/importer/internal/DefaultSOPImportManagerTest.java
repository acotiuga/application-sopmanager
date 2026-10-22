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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.contrib.sopmanager.importer.SOPImportAnalysis;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportPreview;
import org.xwiki.contrib.sopmanager.importer.SOPImportReport;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Outcome;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion.Origin;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.officeimporter.server.OfficeServer;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultSOPImportManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultSOPImportManagerTest
{
    private static final String NATIVE = "ANA_AA_9.101_Dummy-Haendedesinfektion";

    private static final String CONVERTED = "ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen";

    private static final String IN_CONTENT = "ANA_AA_9.103_Dummy-Check-Arbeitsplatz";

    private static final String DAMAGED = "ANA_AA_9.104_Dummy-beschaedigt";

    private static final String WORD_97 = "ANA_AA_9.105_Dummy-Altformat";

    private static final String SCAN = "ANA_AA_9.106_Dummy-Scan";

    private static final String CLEAN = "Dummy-Hausordnung";

    private static final String DOCX = ".docx";

    private static final String PDF = ".pdf";

    private static final String TYPES = " .DOCX; doc,pdf ";

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "Editor");

    private static final DocumentReference MUSTER = new DocumentReference("xwiki", "XWiki", "MMuster");

    private static final DocumentReference SOP_HOME = new DocumentReference("xwiki", "SOPManager", "WebHome");

    @InjectMockComponents
    private DefaultSOPImportManager importManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    private JobProgressManager progressManager;

    @MockComponent
    private SOPDocumentCreator documentCreator;

    @MockComponent
    private ImportDocumentAnalyser analyser;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.INFO);

    @TempDir
    private Path folder;

    private XWikiContext context;

    private XWiki wiki;

    @BeforeEach
    void beforeEach() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getWikiId()).thenReturn("xwiki");
        when(this.context.getUserReference()).thenReturn(USER);
        when(this.authorizationManager.hasAccess(eq(Right.EDIT), eq(USER), any())).thenReturn(true);
        // The translations: the key and its parameters.
        ContextualLocalizationManager localizationManager = mock(ContextualLocalizationManager.class);
        when(localizationManager.getTranslationPlain(anyString(), any(Object[].class))).thenAnswer(
            invocation -> invocation.getArgument(0) + " " + Arrays.toString(
                Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length)));
        when(this.documentCreator.getReference(anyString(), eq("xwiki"))).thenAnswer(
            invocation -> new DocumentReference("xwiki", List.of("SOPManager", invocation.getArgument(0)), "WebHome"));
        when(this.serializer.serialize(any())).thenAnswer(invocation -> invocation.getArgument(0).toString());
        // The documents are read and analysed by the real reader and analyser, without office server.
        DocumentReader reader = new DocumentReader();
        OfficeServer officeServer = mock(OfficeServer.class);
        when(officeServer.getState()).thenReturn(OfficeServer.ServerState.NOT_CONNECTED);
        ReflectionUtils.setFieldValue(reader, "officeServer", officeServer);
        ImportDocumentAnalyser realAnalyser = new ImportDocumentAnalyser();
        ReflectionUtils.setFieldValue(realAnalyser, "logger", LoggerFactory.getLogger(ImportDocumentAnalyser.class));
        ReflectionUtils.setFieldValue(realAnalyser, "localizationManager", localizationManager);
        ReflectionUtils.setFieldValue(realAnalyser, "documentCreator", this.documentCreator);
        ReflectionUtils.setFieldValue(realAnalyser, "documentReader", reader);
        ReflectionUtils.setFieldValue(realAnalyser, "serializer", this.serializer);
        ImportPreviewRenderer previewRenderer = mock(ImportPreviewRenderer.class);
        when(previewRenderer.render(any())).thenReturn("preview");
        ReflectionUtils.setFieldValue(realAnalyser, "previewRenderer", previewRenderer);
        ReflectionUtils.setFieldValue(realAnalyser, "userResolver", new ImportUserResolver());
        // The users of the wiki: the approver of the documents, and two users named Beispiel.
        when(this.analyser.getUsers()).thenReturn(List.of(new ImportUserResolver.User(MUSTER, "Max", "Muster"),
            new ImportUserResolver.User(new DocumentReference("xwiki", "XWiki", "KBeispiel"), "Karl", "Beispiel"),
            new ImportUserResolver.User(new DocumentReference("xwiki", "XWiki", "BeispielA"), "Anna", "")));
        when(this.analyser.preview(any())).thenAnswer(invocation -> realAnalyser.preview(invocation.getArgument(0)));
        when(this.analyser.analyse(anyString(), any(), any(), any())).thenAnswer(invocation -> realAnalyser.analyse(
            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
        when(this.analyser.read(any())).thenAnswer(invocation -> realAnalyser.read((Path) invocation.getArgument(0)));
        when(this.analyser.read(any(), any())).thenAnswer(
            invocation -> realAnalyser.read(invocation.getArgument(0), (SOPImportResult) invocation.getArgument(1)));
        when(this.analyser.translate(any())).thenAnswer(invocation -> realAnalyser.translate(invocation.getArgument(0)));

        Path documents = Paths.get(getClass().getResource("/documents").toURI());
        try (Stream<Path> files = Files.list(documents)) {
            for (Path file : files.toList()) {
                Files.copy(file, this.folder.resolve(file.getFileName()));
            }
        }
        Files.createDirectory(this.folder.resolve("Unterordner.docx"));
    }

    private DocumentReference reference(String name)
    {
        return new DocumentReference("xwiki", List.of("SOPManager", name), "WebHome");
    }

    @Test
    void analyseDocuments() throws Exception
    {
        when(this.wiki.exists(reference(CLEAN), this.context)).thenReturn(true);

        SOPImportAnalysis analysis = this.importManager.analyseDocuments(this.folder.toString(), TYPES, 0, "job");

        assertSame(analysis, this.importManager.getAnalysis("job"));
        assertNull(analysis.getError());
        List<SOPImportCandidate> documents = analysis.getDocuments();
        assertEquals(List.of(NATIVE, CONVERTED, IN_CONTENT, DAMAGED, WORD_97, SCAN, CLEAN),
            documents.stream().map(SOPImportCandidate::getName).toList());

        // The native Word document, the original, and the PDF exported from it.
        SOPImportCandidate nativeDocument = documents.get(0);
        assertVersions(nativeDocument, NATIVE + DOCX, NATIVE + PDF);
        SOPImportVersion word = nativeDocument.getVersions().get(0);
        assertVersion(word, Origin.ORIGINAL, Compatibility.COMPATIBLE, true, true);
        assertEquals("ANA_AA_9.101", word.getData().get("revisionId"));
        assertEquals("Dummy Händedesinfektion", word.getData().get("title"));
        assertEquals(5, word.getData().get("revisionNumber"));
        assertEquals(SOPDocumentCreator.PDF_TEMPLATE_VERTICAL, word.getData().get("pdfTemplate"));
        assertEquals("Microsoft Office Word", word.getSource().get("application"));
        assertNull(word.getTextMatch());
        SOPImportVersion pdf = nativeDocument.getVersions().get(1);
        assertVersion(pdf, Origin.EXPORTED_TO_PDF, Compatibility.TAGGED_PDF, false, false);
        assertEquals(word.getData().get("releaseDate"), pdf.getData().get("releaseDate"));
        assertEquals(100, pdf.getTextMatch());
        assertTrue(nativeDocument.isSelected());
        // The persons named in the document, and the users who may be them.
        assertEquals(List.of(MUSTER), nativeDocument.getUsers(SOPImportManager.APPROVER));
        assertEquals("Dr. M. Muster", nativeDocument.getPeople().get(SOPImportManager.APPROVER).get(0).getName());
        assertEquals(List.of(new DocumentReference("xwiki", "XWiki", "KBeispiel"),
            new DocumentReference("xwiki", "XWiki", "BeispielA")), nativeDocument.getUsers(SOPImportManager.REVIEWER));
        assertEquals(List.of(), nativeDocument.getUsers(SOPImportManager.OWNER));
        // The last modification of the versions: the PDF, exported after the Word document was written.
        assertEquals(NATIVE + PDF, nativeDocument.getLastModifiedFile());
        assertEquals("Dummy", nativeDocument.getLastModifiedBy());
        assertNull(nativeDocument.getMissingOriginal());

        // The document converted from PDF, and the original PDF: the PDF is recommended.
        SOPImportCandidate converted = documents.get(1);
        assertVersions(converted, CONVERTED + PDF, CONVERTED + DOCX);
        assertVersion(converted.getVersions().get(0), Origin.EXPORTED_TO_PDF, Compatibility.TAGGED_PDF, true, true);
        assertEquals(SOPDocumentCreator.PDF_TEMPLATE_HORIZONTAL,
            converted.getVersions().get(0).getData().get("pdfTemplate"));
        assertVersion(converted.getVersions().get(1), Origin.CONVERTED_FROM_PDF, Compatibility.FIXED_LAYOUT, false,
            false);
        assertTrue(converted.getVersions().get(1).getTextMatch() > 80);
        assertEquals("W:\\Gemeinsam\\QM\\Dokumentenlenkung\\Dokumente\\" + CONVERTED + ".doc",
            converted.getMissingOriginal());

        // The document whose page header and footer were rebuilt in the content.
        SOPImportCandidate rebuilt = documents.get(2);
        assertVersion(rebuilt.getVersions().get(0), Origin.REBUILT, Compatibility.HEADER_FOOTER_IN_CONTENT, false,
            true);
        assertNull(rebuilt.getOriginal());

        // The damaged document and the scan can't be imported.
        assertVersion(documents.get(3).getVersions().get(0), Origin.UNKNOWN, Compatibility.INVALID, false, false);
        assertTrue(documents.get(3).getVersions().get(0).getMessage()
            .startsWith("sopManager.import.message.unreadable [Not a Word document"));
        assertNull(documents.get(3).getRecommended());
        assertFalse(documents.get(3).isSelected());
        SOPImportVersion scan = documents.get(5).getVersions().get(0);
        assertVersion(scan, Origin.UNKNOWN, Compatibility.NOT_SUPPORTED, false, false);
        assertEquals("sopManager.import.message.untaggedPdf []", scan.getMessage());

        // The Word 97-2003 document, the original, can't be converted without office server: the Word document
        // converted from it is recommended.
        SOPImportCandidate word97 = documents.get(4);
        assertVersions(word97, WORD_97 + DOCX, WORD_97 + ".doc");
        assertVersion(word97.getVersions().get(0), Origin.CONVERTED_FROM_DOC, Compatibility.COMPATIBLE, false, true);
        assertVersion(word97.getVersions().get(1), Origin.UNKNOWN, Compatibility.NOT_SUPPORTED, true, false);
        assertEquals("sopManager.import.message.officeServer []", word97.getVersions().get(1).getMessage());

        // The page of the clean document exists.
        assertTrue(documents.get(6).isExists());
        assertFalse(documents.get(6).isSelected());

        assertEquals(List.of("Notizen.txt", "~$A_AA_9.101_Dummy-Haendedesinfektion.docx"),
            analysis.getOtherFiles().stream().map(SOPImportResult::getFileName).toList());
        assertEquals(Compatibility.NOT_ACCEPTED, analysis.getOtherFiles().get(0).getCompatibility());
        assertEquals(Compatibility.INVALID, analysis.getOtherFiles().get(1).getCompatibility());

        verify(this.progressManager).pushLevelProgress(7, this.importManager);
        verify(this.progressManager, times(7)).startStep(this.importManager);
        verify(this.documentCreator, never()).create(any(), anyString(), any(), any(), any());

        List<String> log = messages();
        assertEquals(11, log.size());
        assertEquals(String.format("Analysing the 12 files of the folder [%s] (accepted types: docx, doc, pdf).",
            this.folder), log.get(0));
        assertEquals("[Notizen.txt] (sopManager.import.compatibility.NOT_ACCEPTED []): not imported: "
            + "sopManager.import.message.notAccepted [txt]", log.get(1));
        assertEquals(String.format("[%1$s]: %1$s.docx (sopManager.import.origin.ORIGINAL []), %1$s.pdf "
            + "(sopManager.import.origin.EXPORTED_TO_PDF []): recommended version: [%1$s.docx].", NATIVE), log.get(3));
        assertEquals(String.format("[%1$s]: %1$s.docx (sopManager.import.message.unreadable [Not a Word document: "
            + "zip END header not found]): no file can be imported.", DAMAGED), log.get(6));
        assertEquals(String.format("[%1$s]: %1$s.docx (sopManager.import.origin.ORIGINAL []): the page [%2$s] "
            + "already exists, the document will not be imported.", CLEAN, reference(CLEAN)), log.get(9));
        assertEquals("Documents that can be imported: 4 of 7, selected: 4.", log.get(10));
    }

    private static void assertVersions(SOPImportCandidate candidate, String... fileNames)
    {
        assertEquals(List.of(fileNames), candidate.getVersions().stream().map(SOPImportVersion::getFileName).toList());
    }

    private static void assertVersion(SOPImportVersion version, Origin origin, Compatibility compatibility,
        boolean original, boolean recommended)
    {
        assertEquals(origin, version.getOrigin(), version.getFileName());
        assertEquals(compatibility, version.getCompatibility(), version.getFileName());
        assertEquals(original, version.isOriginal(), version.getFileName());
        assertEquals(recommended, version.isRecommended(), version.getFileName());
    }

    @Test
    void analyseSelectsTheFirstDocuments()
    {
        SOPImportAnalysis analysis = this.importManager.analyseDocuments(this.folder.toString(), null, 2, null);

        // Only the Word documents.
        assertEquals(List.of(NATIVE, CONVERTED, IN_CONTENT, DAMAGED, WORD_97, CLEAN),
            analysis.getDocuments().stream().map(SOPImportCandidate::getName).toList());
        assertEquals(List.of(true, true, false, false, false, false),
            analysis.getDocuments().stream().map(SOPImportCandidate::isSelected).toList());
        assertEquals("Documents that can be imported: 5 of 6, selected: 2.", messages().get(messages().size() - 1));
    }

    private List<String> messages()
    {
        return IntStream.range(0, this.logCapture.size()).mapToObj(this.logCapture::getMessage).toList();
    }

    @Test
    void importDocuments() throws Exception
    {
        // A page exists for the converted document, the clean document can't be edited, the creation of the document
        // with its page header and footer in its content fails.
        when(this.wiki.exists(reference(CONVERTED), this.context)).thenReturn(true);
        when(this.authorizationManager.hasAccess(Right.EDIT, USER, reference(CLEAN))).thenReturn(false);
        when(this.documentCreator.create(eq(reference(IN_CONTENT)), eq(IN_CONTENT), any(), any(), eq(this.context)))
            .thenThrow(new XWikiException(0, 0, "Saving failed"));
        Map<String, List<String>> nativeUsers = Map.of(SOPImportManager.APPROVER, List.of("xwiki:XWiki.MMuster"));
        when(this.documentCreator.create(eq(reference(NATIVE)), eq(NATIVE), any(), eq(nativeUsers), eq(this.context)))
            .thenReturn(List.of(new ImportWarning("sopManager.import.warning.reviewerNotSet", "K. Beispiel")));

        SOPImportReport report = this.importManager.importDocuments(this.folder.toString(), List.of(NATIVE + PDF,
            CONVERTED + PDF, IN_CONTENT + DOCX, DAMAGED + DOCX, WORD_97 + ".doc", SCAN + PDF, CLEAN + DOCX,
            "../Notizen.txt", "Fehlt.docx", "~$A_AA_9.101_Dummy-Haendedesinfektion.docx", NATIVE + PDF),
            Map.of(NATIVE, nativeUsers), "job");

        assertSame(report, this.importManager.getReport("job"));
        assertNull(report.getError());
        List<SOPImportResult> results = report.getResults();
        assertEquals(10, results.size());
        assertResult(results.get(0), NATIVE + PDF, Compatibility.TAGGED_PDF, Outcome.IMPORTED, null);
        assertEquals(List.of("sopManager.import.warning.reviewerNotSet [K. Beispiel]"),
            results.get(0).getWarnings());
        assertEquals(reference(NATIVE), results.get(0).getDocumentReference());
        assertResult(results.get(1), CONVERTED + PDF, Compatibility.TAGGED_PDF, Outcome.SKIPPED_EXISTING,
            "sopManager.import.message.exists [" + reference(CONVERTED) + "]");
        assertResult(results.get(2), IN_CONTENT + DOCX, Compatibility.HEADER_FOOTER_IN_CONTENT, Outcome.FAILED,
            "sopManager.import.message.failed [Error number 0 in 0: Saving failed]");
        assertEquals(Compatibility.INVALID, results.get(3).getCompatibility());
        assertEquals(Outcome.FAILED, results.get(3).getOutcome());
        assertResult(results.get(4), WORD_97 + ".doc", Compatibility.NOT_SUPPORTED, Outcome.SKIPPED_FORMAT,
            "sopManager.import.message.officeServer []");
        assertResult(results.get(5), SCAN + PDF, Compatibility.NOT_SUPPORTED, Outcome.SKIPPED_FORMAT,
            "sopManager.import.message.untaggedPdf []");
        assertResult(results.get(6), CLEAN + DOCX, Compatibility.COMPATIBLE, Outcome.FAILED,
            "sopManager.import.message.notAllowed [" + reference(CLEAN) + "]");
        assertResult(results.get(7), "../Notizen.txt", null, Outcome.FAILED,
            "sopManager.import.message.notInFolder [../Notizen.txt]");
        assertResult(results.get(8), "Fehlt.docx", null, Outcome.FAILED,
            "sopManager.import.message.notInFolder [Fehlt.docx]");
        assertResult(results.get(9), "~$A_AA_9.101_Dummy-Haendedesinfektion.docx", Compatibility.INVALID,
            Outcome.SKIPPED_FORMAT, "sopManager.import.message.lockFile [~$A_AA_9.101_Dummy-Haendedesinfektion.docx]");
        assertEquals(1, report.getImported().size());
        assertEquals(9, report.getNotImported().size());

        verify(this.progressManager).pushLevelProgress(10, this.importManager);
        verify(this.progressManager, times(10)).endStep(this.importManager);
        verify(this.progressManager).popLevelProgress(this.importManager);

        List<String> log = messages();
        assertEquals(14, log.size());
        assertEquals(String.format("Importing the 10 selected files of the folder [%s].", this.folder), log.get(0));
        assertEquals(String.format("[%s.pdf] (sopManager.import.compatibility.TAGGED_PDF []): imported as [%s].",
            NATIVE, reference(NATIVE)), log.get(1));
        assertEquals(String.format("[%s.pdf]: sopManager.import.warning.reviewerNotSet [K. Beispiel]", NATIVE),
            log.get(2));
        assertEquals(String.format("[%s.pdf] (sopManager.import.compatibility.TAGGED_PDF []): not imported: "
            + "sopManager.import.message.exists [%s]", CONVERTED, reference(CONVERTED)), log.get(3));
        assertEquals("[Fehlt.docx] (-): failed: sopManager.import.message.notInFolder [Fehlt.docx]", log.get(10));
        assertEquals("Imported documents (1): " + NATIVE + PDF, log.get(12));
        assertTrue(log.get(13).startsWith("Documents not imported (9): " + CONVERTED + PDF + ", "), log.get(13));
    }

    private static void assertResult(SOPImportResult result, String fileName, Compatibility compatibility,
        Outcome outcome, String message)
    {
        assertEquals(fileName, result.getFileName());
        assertEquals(compatibility, result.getCompatibility());
        assertEquals(outcome, result.getOutcome());
        assertEquals(message, result.getMessage());
    }

    @Test
    void importWithoutSelection()
    {
        SOPImportReport report = this.importManager.importDocuments(this.folder.toString(), null, null, null);

        assertTrue(report.getResults().isEmpty());
        assertEquals(String.format("Importing the 0 selected files of the folder [%s].", this.folder),
            messages().get(0));
    }

    @Test
    void previewDocument()
    {
        SOPImportPreview preview = this.importManager.previewDocument(this.folder.toString(), NATIVE + PDF);

        assertEquals("Dummy Händedesinfektion", preview.title());
        assertEquals("preview", preview.content());
        assertEquals(Origin.EXPORTED_TO_PDF, preview.version().getOrigin());
        assertEquals("ANA_AA_9.101", preview.version().getData().get("revisionId"));

        SOPImportPreview scan = this.importManager.previewDocument(this.folder.toString(), SCAN + PDF);
        assertEquals("Dummy-Scan", scan.title());
        assertEquals("", scan.content());
        assertEquals("sopManager.import.message.untaggedPdf []", scan.version().getMessage());

        assertNull(this.importManager.previewDocument(this.folder.toString(), "../" + NATIVE + PDF));
        assertNull(this.importManager.previewDocument(this.folder.toString(), "~$A_AA_9.101_Dummy-Haendedesinfektion"
            + DOCX));
        assertNull(this.importManager.previewDocument("relative", NATIVE + PDF));
    }

    @Test
    void importNeedsTheEditRightOnTheSOPManager() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.EDIT, USER, SOP_HOME)).thenReturn(false);

        SOPImportReport report = this.importManager.importDocuments(this.folder.toString(), List.of(NATIVE + DOCX),
            Map.of(),
            "job");
        SOPImportAnalysis analysis = this.importManager.analyseDocuments(this.folder.toString(), TYPES, 0, "job");

        assertEquals("sopManager.import.message.notAllowed [" + SOP_HOME + "]", report.getError());
        assertTrue(report.getResults().isEmpty());
        assertEquals("sopManager.import.message.notAllowed [" + SOP_HOME + "]", analysis.getError());
        assertTrue(analysis.getDocuments().isEmpty());
        // The other users can't read the reports, nor preview the documents.
        assertNull(this.importManager.getReport("job"));
        assertNull(this.importManager.getAnalysis("job"));
        assertNull(this.importManager.previewDocument(this.folder.toString(), NATIVE + DOCX));
        verify(this.documentCreator, never()).create(any(), anyString(), any(), any(), any());
        assertEquals(List.of("The import failed: [" + SOP_HOME + "].", "The import failed: [" + SOP_HOME + "]."),
            messages());
    }

    @Test
    void importNeedsAnExistingAbsoluteFolder() throws IOException
    {
        assertEquals("sopManager.import.message.noFolder []",
            this.importManager.analyseDocuments(" ", TYPES, 0, null).getError());
        assertEquals("sopManager.import.message.noSuchFolder [relative]",
            this.importManager.analyseDocuments("relative", TYPES, 0, null).getError());
        String missing = this.folder.resolve("missing").toString();
        assertEquals("sopManager.import.message.noSuchFolder [" + missing + "]",
            this.importManager.importDocuments(missing, List.of(), Map.of(), null).getError());
        String file = this.folder.resolve("Notizen.txt").toString();
        assertEquals("sopManager.import.message.noSuchFolder [" + file + "]",
            this.importManager.importDocuments(file, List.of(), Map.of(), null).getError());
        assertEquals("sopManager.import.message.noSuchFolder [/a\0b]",
            this.importManager.importDocuments("/a\0b", List.of(), Map.of(), null).getError());
        assertEquals(List.of("The import failed: [].", "The import failed: [relative].",
            "The import failed: [" + missing + "].", "The import failed: [" + file + "].",
            "The import failed: [/a\0b]."), messages());
    }

    @Test
    void getReportKeepsTheLastReports()
    {
        for (int i = 0; i < 21; i++) {
            this.importManager.importDocuments("", List.of(), Map.of(), "report" + i);
            this.importManager.analyseDocuments("", TYPES, 0, "analysis" + i);
        }
        assertNull(this.importManager.getReport("report0"));
        assertNull(this.importManager.getAnalysis("analysis0"));
        assertEquals("sopManager.import.message.noFolder []", this.importManager.getReport("report20").getError());
        assertEquals("sopManager.import.message.noFolder []",
            this.importManager.getAnalysis("analysis20").getError());
        assertNull(this.importManager.getReport(null));
        assertNull(this.importManager.getAnalysis(null));
        assertEquals(42, messages().size());
    }
}
