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
package org.xwiki.contrib.sopmanager.test.ui;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.Network;
import org.xwiki.contrib.sopmanager.test.po.ImportDocumentsPage;
import org.xwiki.contrib.sopmanager.test.po.ImportDocumentsPage.ReportRow;
import org.xwiki.contrib.sopmanager.test.po.ImportDocumentsPage.Version;
import org.xwiki.contrib.sopmanager.test.po.ImportPreviewPage;
import org.xwiki.contrib.sopmanager.test.po.SOPDocumentPage;
import org.xwiki.contrib.sopmanager.test.po.SOPManagerHomePage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.integration.junit.LogCaptureConfiguration;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.CLEAN;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.CONVERTED;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.IN_CONTENT;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.NATIVE;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.PASSWORD;
import static org.xwiki.contrib.sopmanager.test.ui.SOPTestUtils.WORD_97;

/**
 * Imports documents as SOP documents, from a folder of the server holding the documents of each kind found in the SOP
 * documents to import (the page header and footer in the header and footer of the document, in its content, or in the
 * frames of a document converted from PDF, a clean document), their versions in other formats (the PDF exported from
 * a Word document, the original PDF of a document converted from PDF, a Word 97-2003 document) and files that can't be
 * imported. The folder is analysed, the versions are previewed and selected, then imported. The imported documents are
 * then reviewed and approved, which generates their PDF with the PDF templates of the SOP Manager.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportDocumentsIT
{
    private static final String EDITOR = "SopEditor";

    private static final String READER = "SopReader";

    private static final String WRITER = "SopWriter";

    private static final String DOCX_DOC_PDF = "docx, doc, pdf";

    private static final String WRONG_FORMAT = "Skipped: wrong format";

    private static final String PDF = "pdf";

    private static final String DAMAGED = "ANA_AA_9.104_Dummy-beschaedigt";

    private static final String SCAN = "ANA_AA_9.106_Dummy-Scan";

    private static final String LOCK_FILE = "~$A_AA_9.101_Dummy-Haendedesinfektion.docx";

    private static final String RECOMMENDED = "Recommended";

    private static final List<String> ORIGINAL_RECOMMENDED = List.of("Original", RECOMMENDED);

    private static final String ORIGINAL_WORD = "Original Word document";

    private static final String EXPORTED_TO_PDF = "PDF exported from a Word document";

    private static final String UNTAGGED_PDF = "The PDF has no structure (e.g. a scan): it can't be imported.";

    private static final String MUSTER = "xwiki:XWiki.MMuster";

    private static final String BEISPIEL = "xwiki:XWiki.KBeispiel";

    private static final String APPROVER = "approver";

    private static final String REVIEWER = "reviewer";

    private static final String IMPORTED = "Imported";

    private static final String CHROME_CONTAINER = "sop-test-chrome-" + Network.SHARED.getId().substring(0, 12);

    private static String folder;

    @BeforeAll
    static void setUp(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        setup.createUser(EDITOR, PASSWORD, null, "first_name", "Sop", "last_name", "Editor");
        setup.createUser(READER, PASSWORD, null, "first_name", "Sop", "last_name", "Reader");
        setup.createUser(WRITER, PASSWORD, null, "first_name", "Sop", "last_name", "Writer");
        // The users named in the documents: the approver, and two users who may be the reviewer, "K. Beispiel".
        setup.createUser("MMuster", PASSWORD, null, "first_name", "Max", "last_name", "Muster");
        setup.createUser("KBeispiel", PASSWORD, null, "first_name", "Karl", "last_name", "Beispiel");
        setup.createUser("BeispielKl", PASSWORD, null, "first_name", "Klaus", "last_name", "");
        // The editor imports the SOP documents, writes them, reviews and approves them.
        for (String group : List.of("SOPEditorsGroup", "SOPReviewersGroup", "SOPApproversGroup")) {
            setup.addObject("XWiki", group, "XWiki.XWikiGroups", "member", "XWiki." + EDITOR);
        }
        // The SOP editors can edit the pages of the SOP Manager.
        setup.addObject(new LocalDocumentReference("SOPManager", "WebPreferences"), "XWiki.XWikiGlobalRights",
            "groups", "XWiki.SOPEditorsGroup", "levels", "edit", "allow", "1");
        // The writer can edit the SOP documents, but can't import them: not being a SOP editor.
        setup.addObject(new LocalDocumentReference("SOPManager", "WebPreferences"), "XWiki.XWikiGlobalRights",
            "users", "XWiki." + WRITER, "levels", "edit", "allow", "1");
        folder = SOPTestUtils.copyDocumentsToServer(setup, getDocuments());
        // The approved SOP documents are exported to PDF on the server. XWiki runs in a container: the browser printing
        // the PDF documents is started in its network, and loads the pages from its address in this network. Its
        // container is named after the network, so that the container of a previous run, in another network, is not
        // reused.
        setup.addObject(new LocalDocumentReference(List.of("XWiki", "PDFExport"), "Configuration"),
            "XWiki.PDFExport.ConfigurationClass", "serverSide", "1", "dockerNetwork", Network.SHARED.getId(),
            "xwikiURI", "http://xwikiweb:8080", "chromeDockerContainerName", CHROME_CONTAINER);
        SOPTestUtils.waitForServerSidePrinting(setup);
        SOPTestUtils.startOfficeServer(setup);
    }

    @AfterAll
    static void tearDown()
    {
        SOPTestUtils.removeContainer(CHROME_CONTAINER);
    }

    /**
     * The job macro, built on XWiki 12.10, uses deprecated getters of the job status to display it.
     */
    private static void allowJobMacroWarnings(LogCaptureConfiguration logCaptureConfiguration)
    {
        logCaptureConfiguration.registerExcludes(
            "Deprecated usage of getter [org.xwiki.job.internal.script.safe.SafeCancelableJobStatus.getLog] in "
                + "xwiki:JobMacro.JobProgressMacro");
    }

    private static List<Path> getDocuments() throws Exception
    {
        URI documents = ImportDocumentsIT.class.getResource("/documents").toURI();
        try (Stream<Path> files = Files.list(Paths.get(documents))) {
            return files.filter(file -> !file.getFileName().toString().endsWith(".py")).sorted().toList();
        }
    }

    @Test
    @Order(1)
    void importNeedsAnAdministratorOrASOPEditor(TestUtils setup)
    {
        for (String user : List.of(READER, WRITER)) {
            setup.login(user, PASSWORD);
            ImportDocumentsPage importPage = ImportDocumentsPage.gotoPage();
            assertFalse(importPage.isFormDisplayed());
            assertEquals("Only the wiki administrators and the members of the SOP Editors group can import "
                + "documents.", importPage.getErrorMessage());
            assertFalse(SOPManagerHomePage.gotoPage().hasImportLink());
        }
    }

    @Test
    @Order(2)
    void analyseThenImportTheFirstDocuments(TestUtils setup, LogCaptureConfiguration logCaptureConfiguration)
        throws Exception
    {
        allowJobMacroWarnings(logCaptureConfiguration);
        setup.login(EDITOR, PASSWORD);
        SOPManagerHomePage home = SOPManagerHomePage.gotoPage();
        assertTrue(home.hasImportLink());
        // Analyse the folder, selecting the first 2 documents to try the import on them.
        ImportDocumentsPage analysis = ImportDocumentsPage.gotoPage().analyse(folder, DOCX_DOC_PDF, 2);

        assertEquals("", analysis.getReportError());
        assertEquals(List.of(NATIVE.page(), CONVERTED.page(), IN_CONTENT.page(), DAMAGED, WORD_97.page(), SCAN,
            CLEAN.page()), analysis.getDocuments());
        // The native Word document is the original, the PDF was exported from it.
        assertEquals(List.of(new Version(NATIVE.file(PDF), List.of(), EXPORTED_TO_PDF, false, true),
            new Version(NATIVE.file(), ORIGINAL_RECOMMENDED, ORIGINAL_WORD, true, true)), analysis.getVersions(NATIVE.page()));
        assertEquals(List.of("doc"), analysis.getMissingFormats(NATIVE.page()));
        assertEquals("Same text as the recommended version: 100%", analysis.getTextMatch(NATIVE.file(PDF)));
        Map<String, String> data = analysis.getVersionData(NATIVE.file());
        assertEquals("ANA_AA_9.101", data.get("revisionId"));
        assertEquals(NATIVE.title(), data.get("title"));
        assertEquals("05.11.2025", data.get("releaseDate"));
        assertEquals("5", data.get("revisionNumber"));
        assertEquals("04.11.2028", data.get("revisionPlannedDate"));
        assertEquals(data, analysis.getVersionData(NATIVE.file(PDF)));
        // The document converted from PDF: the original PDF is recommended.
        assertEquals(List.of(new Version(CONVERTED.file(PDF), ORIGINAL_RECOMMENDED, EXPORTED_TO_PDF, true, true),
            new Version(CONVERTED.file(), List.of(), "Word document converted from a PDF", false, true)),
            analysis.getVersions(CONVERTED.page()));
        assertEquals("", analysis.getDocumentNotes(CONVERTED.page()));
        assertEquals(List.of(new Version(IN_CONTENT.file(), List.of(RECOMMENDED),
            "Word document with the page header and footer in its content (rebuilt)", false, true)),
            analysis.getVersions(IN_CONTENT.page()));
        // The Word 97-2003 document is the original, the Word document converted from it keeps more of its
        // formatting.
        assertEquals(List.of(new Version(WORD_97.file(), List.of(RECOMMENDED),
            "Word document converted from a Word 97-2003 document", false, true),
            new Version(WORD_97.file("doc"), List.of("Original"), ORIGINAL_WORD, false, true)),
            analysis.getVersions(WORD_97.page()));
        // The files that can't be imported.
        Version damaged = analysis.getVersions(DAMAGED).get(0);
        assertTrue(damaged.origin().startsWith("The document can not be read: Not a Word document"),
            damaged.origin());
        assertFalse(damaged.enabled());
        assertEquals(List.of(new Version(SCAN + ".pdf", List.of(), UNTAGGED_PDF, false, false)),
            analysis.getVersions(SCAN));
        assertEquals(List.of(new ReportRow("Notizen.txt", "Type not accepted", WRONG_FORMAT,
            "The type [txt] is not one of the accepted types."), new ReportRow(LOCK_FILE, "Invalid file",
            WRONG_FORMAT, "[" + LOCK_FILE + "] is the lock file of a document open in Word, not a document.")),
            analysis.getOtherFiles());
        assertEquals(List.of(NATIVE.file(), CONVERTED.file(PDF)), analysis.getSelectedFiles());
        // The SOP data of the recommended version, and the users named in it: the most likely user of each role,
        // next to the names written in the document. The last name of the reviewer is also in the name of another
        // user: the one with this last name comes first.
        assertEquals("ANA_AA_9.101", analysis.getDocumentData(NATIVE.page()).get("revisionId"));
        assertEquals("Dr. M. Muster", analysis.getWrittenNames(NATIVE.page(), APPROVER));
        assertEquals("XWiki.MMuster", analysis.getUser(NATIVE.page(), APPROVER));
        assertEquals("K. Beispiel", analysis.getWrittenNames(NATIVE.page(), REVIEWER));
        assertEquals("XWiki.KBeispiel", analysis.getUser(NATIVE.page(), REVIEWER));
        // The owner: who last modified the document, in its version modified last (the PDF exported from the Word
        // document), not a user of the wiki.
        String lastModification = analysis.getDocumentData(NATIVE.page()).get("lastModifiedBy");
        assertTrue(lastModification.startsWith("Dummy, ") && lastModification.endsWith("(" + NATIVE.file(PDF) + ")"),
            lastModification);
        assertEquals("Dummy", analysis.getWrittenNames(NATIVE.page(), "owner"));
        assertEquals("", analysis.getUser(NATIVE.page(), "owner"));
        assertEquals("Selected documents: 2", analysis.getSelectedCount());
        List<String> log = analysis.getLog();
        assertTrue(log.contains("Analysing the 12 files of the folder [" + folder + "] (accepted types: docx, doc, "
            + "pdf)."), String.join("\n", log));
        assertTrue(log.contains("Documents that can be imported: 5 of 7, selected: 2."), String.join("\n", log));

        // Preview the versions: the pages they would be imported as, opened directly in the preview mode of the
        // editor, in a new window.
        assertFalse(analysis.hasPreview(SCAN + ".pdf"));
        for (String file : List.of(NATIVE.file(), NATIVE.file(PDF))) {
            ImportPreviewPage preview = analysis.preview(file);
            assertEquals(NATIVE.title(), preview.getDocumentTitle(), file);
            String content = preview.getPreviewContent();
            assertTrue(content.contains("Hygienische Händedesinfektion"), content);
            assertFalse(content.contains("Freigabe am"), content);
            assertEquals(2, preview.getTableCount(), file);
            assertEquals(1, preview.getImageCount(), file);
            // The version is imported from the preview, not saved as previewed.
            assertTrue(preview.canImport(), file);
            assertFalse(preview.hasSaveButtons(), file);
            analysis = preview.close();
        }
        ImportPreviewPage preview = analysis.preview(WORD_97.file("doc"));
        assertEquals(WORD_97.title(), preview.getDocumentTitle());
        assertTrue(preview.getPreviewContent().contains("Die Übergabe erfolgt am Bett des Patienten."),
            preview.getPreviewContent());
        analysis = preview.close();
        // Nothing was saved.
        assertFalse(setup.pageExists(List.of("SOPManager", NATIVE.page()), "WebHome"));

        // Change the reviewer of a document, then import the selected versions.
        analysis.setUser(CONVERTED.page(), REVIEWER, "Klaus", "XWiki.BeispielKl");
        assertEquals("XWiki.BeispielKl", analysis.getUser(CONVERTED.page(), REVIEWER));
        ImportDocumentsPage report = analysis.importSelected();
        assertEquals("", report.getReportError());
        assertEquals("Imported documents (2)", report.getImportedTitle());
        assertEquals(List.of(new ReportRow(NATIVE.file(), "Compatible", NATIVE.title(), ""),
            new ReportRow(CONVERTED.file(PDF), "PDF with structure: the layout and part of the formatting are "
                + "approximated", CONVERTED.title(), "")), report.getImported());
        assertEquals("Documents not imported (0)", report.getNotImportedTitle());
        log = report.getLog();
        assertTrue(log.contains("Importing the 2 selected files of the folder [" + folder + "]."),
            String.join("\n", log));
        assertTrue(log.contains(String.format("[%s] (Compatible): imported as [xwiki:SOPManager.%s.WebHome].",
            NATIVE.file(), NATIVE.page().replace(".", "\\."))), String.join("\n", log));
        assertTrue(log.contains(String.format("Imported documents (2): %s, %s", NATIVE.file(), CONVERTED.file(PDF))),
            String.join("\n", log));
    }

    @Test
    @Order(3)
    void analyseThenImportTheOtherDocuments(TestUtils setup, LogCaptureConfiguration logCaptureConfiguration)
    {
        allowJobMacroWarnings(logCaptureConfiguration);
        setup.login(EDITOR, PASSWORD);
        ImportDocumentsPage analysis = ImportDocumentsPage.gotoPage().analyse(folder, DOCX_DOC_PDF, null);

        // The documents already imported are skipped: collapsed, their details can be expanded.
        for (SOPTestUtils.Document document : List.of(NATIVE, CONVERTED)) {
            assertTrue(analysis.getDocumentNotes(document.page()).startsWith("The page exists, the document is not "
                + "imported."), analysis.getDocumentNotes(document.page()));
            assertTrue(analysis.isCollapsed(document.page()));
            analysis.expand(document.page());
            assertFalse(analysis.isCollapsed(document.page()));
            assertTrue(analysis.getVersions(document.page()).stream().noneMatch(Version::enabled));
        }
        assertEquals(List.of(IN_CONTENT.file(), WORD_97.file(), CLEAN.file()), analysis.getSelectedFiles());
        // Import the original Word 97-2003 document instead of the recommended Word document.
        analysis.select(WORD_97.file("doc"));
        assertEquals(List.of(IN_CONTENT.file(), WORD_97.file("doc"), CLEAN.file()), analysis.getSelectedFiles());
        assertEquals("Selected documents: 3", analysis.getSelectedCount());

        ImportDocumentsPage report = analysis.importSelected();
        assertEquals("Imported documents (3)", report.getImportedTitle());
        List<ReportRow> imported = report.getImported();
        assertEquals(List.of(IN_CONTENT.file(), WORD_97.file("doc"), CLEAN.file()),
            imported.stream().map(ReportRow::file).toList());
        assertEquals("Compatible: header and footer in the content, removed from it", imported.get(0).compatibility());
        assertEquals(IN_CONTENT.title(), imported.get(0).result());
        assertEquals(new ReportRow(WORD_97.file("doc"), "Compatible", WORD_97.title(), ""), imported.get(1));
        assertEquals(new ReportRow(CLEAN.file(), "Compatible", CLEAN.title(), ""), imported.get(2));
        assertEquals("Documents not imported (0)", report.getNotImportedTitle());
    }

    @Test
    @Order(4)
    void importedDocumentsHoldTheDetailsOfTheirHeaderAndFooter(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        // The details of the page header and footer are in the SOP object.
        Map<String, String> data = SOPTestUtils.getData(setup, NATIVE.page());
        assertEquals(NATIVE.title(), data.get("title"));
        assertEquals("imported", data.get("status"));
        assertEquals("0", data.get("isInReview"));
        assertEquals("ANA_AA_9.101", data.get("revisionId"));
        assertEquals("5", data.get("revisionNumber"));
        assertEquals("05.11.2025", data.get("releaseDate"));
        assertEquals("04.11.2028", data.get("revisionPlannedDate"));
        assertEquals("SOPManager.Code.GKHPDFTemplateVertical", data.get("pdfTemplate"));
        assertEquals("photo.png", data.get("attachments"));
        // The users selected for the persons named in the document.
        assertEquals(MUSTER, data.get("approverUser"));
        assertEquals(BEISPIEL, data.get("reviewerUser"));
        assertEquals("", data.get("revisionOwner"));
        // The page header and footer are not in the content.
        String content = SOPTestUtils.getContent(setup, NATIVE.page());
        assertTrue(content.contains("|Hygienische Händedesinfektion|3 ml"), content);
        assertTrue(content.contains("1. Die Hände werden vor und nach jedem Patientenkontakt desinfiziert."),
            content);
        // The numbers closed by a parenthesis, and the indentation of the paragraphs, as in the document.
        assertTrue(content.contains("(% style=\"list-style-type: gkh-decimal-parenthesis\" class=\"sop-list\" %)\n1. Die Hände"), content);
        assertTrue(content.contains("(% style=\"margin-left: 1.25cm\" %)\nDie Hände trocknen an der Luft."), content);
        assertTrue(content.contains("[[image:photo.png||width=\"167\"]]"), content);
        SOPTestUtils.assertNoHeaderOrFooter("ANA_AA_9.101", content);

        // The original PDF of a document converted from PDF, in landscape: its page header and footer are not in its
        // structure.
        data = SOPTestUtils.getData(setup, CONVERTED.page());
        assertEquals(CONVERTED.title(), data.get("title"));
        assertEquals(MUSTER, data.get("approverUser"));
        assertEquals("xwiki:XWiki.BeispielKl", data.get("reviewerUser"));
        assertEquals("ANA_AA_9.102", data.get("revisionId"));
        assertEquals("2", data.get("revisionNumber"));
        assertEquals("23.01.2024", data.get("releaseDate"));
        assertEquals("23.01.2027", data.get("revisionPlannedDate"));
        assertEquals("SOPManager.Code.GKHPDFTemplateHorizontal", data.get("pdfTemplate"));
        assertEquals("", data.get("attachments"));
        content = SOPTestUtils.getContent(setup, CONVERTED.page());
        assertTrue(content.contains("|(% colspan=\"3\" %)**Schublade 1**"), content);
        assertTrue(content.contains("|2 Amp. Adrenalin|2 Amp. Atropin|5 x Kanülen 20G"), content);
        SOPTestUtils.assertNoHeaderOrFooter("ANA_AA_9.102", content);

        // A document whose page header and footer are repeated in its content, on each page.
        data = SOPTestUtils.getData(setup, IN_CONTENT.page());
        assertEquals(IN_CONTENT.title(), data.get("title"));
        assertEquals("ANA_AA_9.103", data.get("revisionId"));
        assertEquals("0", data.get("revisionNumber"));
        assertEquals("12.05.2026", data.get("releaseDate"));
        assertEquals("12.05.2029", data.get("revisionPlannedDate"));
        content = SOPTestUtils.getContent(setup, IN_CONTENT.page());
        assertTrue(content.contains("**Durchzuführende Maßnahmen**"), content);
        assertTrue(content.contains("Die Kontrolle wird in der Checkliste dokumentiert."), content);
        SOPTestUtils.assertNoHeaderOrFooter("ANA_AA_9.103", content);

        // A Word 97-2003 document, converted by the office server.
        data = SOPTestUtils.getData(setup, WORD_97.page());
        assertEquals(WORD_97.title(), data.get("title"));
        assertEquals("ANA_AA_9.105", data.get("revisionId"));
        assertEquals("7", data.get("revisionNumber"));
        assertEquals("02.03.2015", data.get("releaseDate"));
        assertEquals("02.03.2030", data.get("revisionPlannedDate"));
        content = SOPTestUtils.getContent(setup, WORD_97.page());
        assertTrue(content.contains("Die Übergabe erfolgt am Bett des Patienten."), content);
        SOPTestUtils.assertNoHeaderOrFooter("ANA_AA_9.105", content);

        // A clean document: empty SOP data.
        data = SOPTestUtils.getData(setup, CLEAN.page());
        assertEquals(CLEAN.title(), data.get("title"));
        assertEquals("imported", data.get("status"));
        assertEquals("", data.get("revisionId"));
        assertEquals("", data.get("releaseDate"));
        assertEquals("", data.get("revisionPlannedDate"));
        assertTrue(SOPTestUtils.getContent(setup, CLEAN.page()).startsWith("= Dummy Hausordnung =\n\n"
            + "Diese Hausordnung gilt für alle Mitarbeiter."));

        // The imported documents are not in the review process.
        setup.login(EDITOR, PASSWORD);
        setup.gotoPage(SOPTestUtils.reference(NATIVE.page()));
        SOPDocumentPage document = new SOPDocumentPage();
        assertFalse(document.hasReviewSection());
        assertTrue(document.getContent().contains("Die Hände werden vor und nach jedem Patientenkontakt"));
    }

    @Test
    @Order(5)
    void startTheReviewFromTheTable(TestUtils setup) throws Exception
    {
        // A user who can't edit the documents can't start their review.
        setup.login(READER, PASSWORD);
        SOPManagerHomePage home = SOPManagerHomePage.gotoPage();
        assertEquals(IMPORTED, home.getStatus(NATIVE.title()));
        assertFalse(home.canStartReview(NATIVE.title()));

        setup.login(EDITOR, PASSWORD);
        home = SOPManagerHomePage.gotoPage();
        for (SOPTestUtils.Document document : List.of(NATIVE, CONVERTED, IN_CONTENT, WORD_97, CLEAN)) {
            assertEquals(IMPORTED, home.getStatus(document.title()), document.title());
            assertTrue(home.canStartReview(document.title()), document.title());
        }
        SOPDocumentPage document = home.startReview(NATIVE.title());
        assertTrue(document.hasReviewSection());

        home = SOPManagerHomePage.gotoPage();
        assertEquals("Draft", home.getStatus(NATIVE.title()));
        assertFalse(home.canStartReview(NATIVE.title()));
        assertEquals(IMPORTED, home.getStatus(CONVERTED.title()));
        assertTrue(home.canStartReview(CONVERTED.title()));

        // The review keeps the revision of the imported document, and its planned revision date still to come.
        setup.loginAsSuperAdmin();
        Map<String, String> data = SOPTestUtils.getData(setup, NATIVE.page());
        assertEquals("draft", data.get("status"));
        assertEquals("1", data.get("isInReview"));
        assertEquals("5", data.get("revisionNumber"));
        assertEquals("04.11.2028", data.get("revisionPlannedDate"));
        assertEquals("xwiki:XWiki." + EDITOR, data.get("revisionOwner"));
    }

    @Test
    @Order(6)
    void approveWithThePortraitPDFTemplate(TestUtils setup) throws Exception
    {
        SOPTestUtils.PDFPage pdf = approve(setup, NATIVE, "ANA_AA_9.101_Dummy Händedesinfektion_5.pdf");
        assertFalse(pdf.landscape());
        // The page header of the PDF template: the code and the title of the document.
        assertTrue(pdf.header().contains("ANA_AA_9.101"), pdf.header());
        assertTrue(pdf.header().contains(NATIVE.title()), pdf.header());
        // The page footer of the PDF template: the release details.
        assertTrue(pdf.footer().contains("Sop Editor"), pdf.footer());
        assertTrue(pdf.footer().contains("04.11.2028"), pdf.footer());
        assertTrue(pdf.footer().matches("(?s).*\\b5\\b.*"), pdf.footer());
        // The content, without the page header and footer of the Word document.
        assertTrue(pdf.body().contains("Hygienische Händedesinfektion"), pdf.body());
        assertFalse(pdf.text().contains("Freigabe am"), pdf.text());
        assertFalse(pdf.text().contains("Dr. M. Muster"), pdf.text());
    }

    @Test
    @Order(7)
    void approveWithTheLandscapePDFTemplate(TestUtils setup) throws Exception
    {
        setup.login(EDITOR, PASSWORD);
        SOPManagerHomePage.gotoPage().startReview(CONVERTED.title());
        SOPTestUtils.PDFPage pdf = approve(setup, CONVERTED, "ANA_AA_9.102_Dummy Bestückung Notfallwagen_2.pdf");
        assertTrue(pdf.landscape());
        assertTrue(pdf.header().contains("ANA_AA_9.102"), pdf.header());
        assertTrue(pdf.header().contains(CONVERTED.title()), pdf.header());
        assertTrue(pdf.footer().contains("Sop Editor"), pdf.footer());
        assertTrue(pdf.footer().contains("23.01.2027"), pdf.footer());
        assertTrue(pdf.body().contains("Schublade 1"), pdf.body());
        assertFalse(pdf.text().contains("Freigabe am"), pdf.text());
        assertFalse(pdf.text().contains("W:\\"), pdf.text());
    }

    /**
     * Submits a document in review for review, approves it, and reads its PDF.
     */
    private SOPTestUtils.PDFPage approve(TestUtils setup, SOPTestUtils.Document document, String pdfName)
        throws Exception
    {
        // The groups reviewing and approving the document.
        setup.loginAsSuperAdmin();
        setup.updateObject(SOPTestUtils.reference(document.page()), "SOPManager.Code.ControlledDocumentClass", 0,
            "reviewerGroups", "XWiki.SOPReviewersGroup", "approverGroups", "XWiki.SOPApproversGroup");

        setup.login(EDITOR, PASSWORD);
        setup.gotoPage(SOPTestUtils.reference(document.page()));
        SOPDocumentPage page = new SOPDocumentPage();
        String submitted = page.runAction("submitForReview");
        assertFalse(submitted.startsWith("ERROR"), submitted);
        setup.gotoPage(SOPTestUtils.reference(document.page()));
        page = new SOPDocumentPage();
        assertTrue(page.hasAction("approve"), page.getReviewSectionText());
        String approved = page.runAction("approve");
        assertFalse(approved.startsWith("ERROR"), approved);

        setup.loginAsSuperAdmin();
        Map<String, String> data = SOPTestUtils.getData(setup, document.page());
        assertEquals("approved", data.get("status"));
        assertEquals("xwiki:XWiki." + EDITOR, data.get("approverUser"));
        assertTrue(data.get("attachments").contains(pdfName), data.get("attachments"));
        assertFalse(data.get("pDFSecondaryTargetLocation").isEmpty());
        return SOPTestUtils.readPDF(setup, new DocumentReference(SOPTestUtils.reference(document.page()),
            new WikiReference("xwiki")), pdfName);
    }
}
