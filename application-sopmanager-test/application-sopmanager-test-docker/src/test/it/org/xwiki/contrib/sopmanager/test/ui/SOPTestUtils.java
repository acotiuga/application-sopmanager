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

import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.testcontainers.DockerClientFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.test.ui.TestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helpers of the functional tests of the SOP Manager.
 *
 * @version $Id$
 */
public final class SOPTestUtils
{
    /**
     * The password of the users of the tests.
     */
    public static final String PASSWORD = "pass";

    /**
     * A document of the SOP template of Word, with its details in its page header and footer, and the PDF exported
     * from it.
     */
    public static final Document NATIVE = new Document("ANA_AA_9.101_Dummy-Haendedesinfektion",
        "Dummy Händedesinfektion");

    /**
     * A document converted from PDF, with a fixed layout, in landscape, and the original PDF.
     */
    public static final Document CONVERTED = new Document("ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen",
        "Dummy Bestückung Notfallwagen");

    /**
     * A document with its page header and footer repeated in its content.
     */
    public static final Document IN_CONTENT = new Document("ANA_AA_9.103_Dummy-Check-Arbeitsplatz",
        "Dummy Check Arbeitsplatz");

    /**
     * A Word 97-2003 document, the original, and the Word document converted from it.
     */
    public static final Document WORD_97 = new Document("ANA_AA_9.105_Dummy-Altformat", "Dummy Altformat");

    /**
     * A document without page header and footer.
     */
    public static final Document CLEAN = new Document("Dummy-Hausordnung", "Dummy-Hausordnung");

    private static final String SOP_MANAGER = "SOPManager";

    private static final LocalDocumentReference DOCUMENTS = new LocalDocumentReference("SOPImportTest", "Documents");

    /**
     * The fraction of the height of a PDF page holding its page header, and its page footer.
     */
    private static final float MARGIN = 0.14F;

    private static final long PRINTING_TIMEOUT = 180_000;

    private static final long PRINTING_RETRY_DELAY = 5_000;

    private static final String DATA_SCRIPT = String.join("\n",
        "{{velocity wiki=\"false\"}}",
        "#set ($document = $xwiki.getDocument($services.model.createDocumentReference('xwiki', "
            + "['SOPManager', $request.page], 'WebHome')))",
        "#set ($object = $document.getObject('SOPManager.Code.ControlledDocumentClass'))",
        "#set ($data = {'title': $document.plainTitle})",
        "#foreach ($property in ['status', 'isInReview', 'revisionId', 'revisionNumber', 'pdfTemplate', "
            + "'revisionOwner', 'approverUser', 'reviewerUser', 'pDFSecondaryTargetLocation'])",
        "  #set ($data[$property] = \"$!object.getValue($property)\")",
        "#end",
        "#foreach ($property in ['releaseDate', 'revisionPlannedDate'])",
        "  #set ($value = $object.getValue($property))",
        "  #set ($data[$property] = \"#if ($value)$datetool.format('dd.MM.yyyy', $value)#end\")",
        "#end",
        "#set ($names = [])",
        "#foreach ($attachment in $document.attachmentList)",
        "  #set ($discard = $names.add($attachment.filename))",
        "#end",
        "#set ($data.attachments = $stringtool.join($names, ','))",
        "$jsontool.serialize($data)",
        "{{/velocity}}");

    /**
     * A document to import.
     *
     * @param baseName the name of the file, without extension: the name of its SOP document
     * @param title the title of the SOP document
     */
    public record Document(String baseName, String title)
    {
        /**
         * @return the name of the Word document
         */
        public String file()
        {
            return file("docx");
        }

        /**
         * @param extension the extension of a version of the document
         * @return the name of the file of this version
         */
        public String file(String extension)
        {
            return this.baseName + '.' + extension;
        }

        /**
         * @return the name of the SOP document, in the SOP Manager space
         */
        public String page()
        {
            return this.baseName;
        }
    }

    /**
     * The first page of a PDF document.
     *
     * @param landscape whether the page is in landscape orientation
     * @param header the text of the page header
     * @param body the text between the page header and footer
     * @param footer the text of the page footer
     * @param text the text of the whole document
     */
    public record PDFPage(boolean landscape, String header, String body, String footer, String text)
    {
    }

    private SOPTestUtils()
    {
    }

    /**
     * Writes documents in a folder of the server, as superadmin.
     *
     * @param setup the test setup
     * @param documents the documents
     * @return the absolute path of the folder on the server
     * @throws Exception if the documents can't be written
     */
    public static String copyDocumentsToServer(TestUtils setup, List<Path> documents) throws Exception
    {
        setup.loginAsSuperAdmin();
        setup.rest().savePage(DOCUMENTS, "The documents imported by the tests.", "Documents");
        for (Path document : documents) {
            try (InputStream content = Files.newInputStream(document)) {
                setup.attachFile(DOCUMENTS, document.getFileName().toString(), content, false);
            }
        }
        String folder = setup.executeWikiPlain(String.join("\n",
            "{{groovy}}",
            "def folder = new File(System.getProperty('java.io.tmpdir'), 'sop-import')",
            "folder.deleteDir()",
            "folder.mkdirs()",
            "xwiki.getDocument('SOPImportTest.Documents').attachmentList.each {",
            "  new File(folder, it.filename).bytes = it.contentAsBytes",
            "}",
            "print(folder.listFiles().length + ':' + folder.absolutePath)",
            "{{/groovy}}"), Syntax.XWIKI_2_1).trim();
        // The number of files written, and the folder.
        assertEquals(String.valueOf(documents.size()), StringUtils.substringBefore(folder, ':'), folder);
        return StringUtils.substringAfter(folder, ":");
    }

    /**
     * Copies files of the folder written by {@link #copyDocumentsToServer(TestUtils, List)} into sub-folders of a new
     * folder of the server, e.g. AA/BB/AA and AA/BB/CC/AA, where the same document is a different document.
     *
     * @param setup the test setup, logged in as superadmin
     * @param folder the folder of the documents on the server
     * @param copies the paths of the copies in the new folder, e.g. "BB/AA/Document.docx" for a copy of the file
     *     "Document.docx" in the sub-folder "BB/AA"
     * @return the absolute path of the new folder on the server, named "AA"
     * @throws Exception if the files can't be copied
     */
    public static String copyToSubfolders(TestUtils setup, String folder, List<String> copies) throws Exception
    {
        String root = setup.executeWikiPlain(String.join("\n",
            "{{groovy}}",
            "def root = new File(System.getProperty('java.io.tmpdir'), 'sop-import-tree/AA')",
            "root.parentFile.deleteDir()",
            "root.mkdirs()",
            "'" + String.join("|", copies) + "'.split('\\\\|').each {",
            "  def target = new File(root, it)",
            "  target.parentFile.mkdirs()",
            "  target.bytes = new File('" + folder + "', target.name).bytes",
            "}",
            "print(root.absolutePath)",
            "{{/groovy}}"), Syntax.XWIKI_2_1).trim();
        assertTrue(root.endsWith("/AA"), root);
        return root;
    }

    /**
     * Waits for the browser printing the PDF documents on the server: its container is started on first use.
     *
     * @param setup the test setup, logged in as superadmin
     * @throws Exception if the browser is not available after 3 minutes
     */
    public static void waitForServerSidePrinting(TestUtils setup) throws Exception
    {
        String script = "{{velocity}}$services.export.pdf.isServerSidePrintingAvailable(){{/velocity}}";
        long end = System.currentTimeMillis() + PRINTING_TIMEOUT;
        String available = "";
        while (!"true".equals(available) && System.currentTimeMillis() < end) {
            available = setup.executeWikiPlain(script, Syntax.XWIKI_2_1).trim();
            if (!"true".equals(available)) {
                Thread.sleep(PRINTING_RETRY_DELAY);
            }
        }
        assertEquals("true", available, "The server-side PDF printing is not available.");
    }

    /**
     * Starts the office server converting the Word 97-2003 documents: LibreOffice is installed in the container of
     * XWiki, but XWiki doesn't start it.
     *
     * @param setup the test setup, logged in as superadmin
     * @throws Exception if the office server is not connected after 3 minutes
     */
    public static void startOfficeServer(TestUtils setup) throws Exception
    {
        String script = String.join("\n",
            "{{groovy}}",
            "def server = services.component.getInstance(org.xwiki.officeimporter.server.OfficeServer)",
            "if (server.state != org.xwiki.officeimporter.server.OfficeServer.ServerState.CONNECTED) {",
            "  try { server.start() } catch (e) { print e.message }",
            "}",
            "print 'state:' + server.state.name()",
            "{{/groovy}}");
        String connected = "state:CONNECTED";
        long end = System.currentTimeMillis() + PRINTING_TIMEOUT;
        String state = "";
        while (!state.contains(connected) && System.currentTimeMillis() < end) {
            state = setup.executeWikiPlain(script, Syntax.XWIKI_2_1).trim();
            if (!state.contains(connected)) {
                Thread.sleep(PRINTING_RETRY_DELAY);
            }
        }
        assertTrue(state.contains(connected), "The office server is not connected: " + state);
    }

    /**
     * Removes a Docker container started by the tested XWiki instance, if it exists.
     *
     * @param name the name of the container
     */
    public static void removeContainer(String name)
    {
        try {
            DockerClientFactory.instance().client().removeContainerCmd(name).withForce(true).exec();
        } catch (NotFoundException e) {
            // The container was not started.
        }
    }

    /**
     * @param name the name of a SOP document
     * @return the reference of the document, in the SOP Manager space
     */
    public static LocalDocumentReference reference(String name)
    {
        return new LocalDocumentReference(List.of(SOP_MANAGER, name), "WebHome");
    }

    /**
     * @param setup the test setup, logged in as superadmin
     * @param name the name of a SOP document
     * @return the title of the document, the values of its SOP object, its attachments
     * @throws Exception if the data can't be read
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getData(TestUtils setup, String name) throws Exception
    {
        String json = setup.executeWiki(DATA_SCRIPT, Syntax.XWIKI_2_1, Map.of("outputSyntax", "plain", "page", name));
        return new ObjectMapper().readValue(json.trim(), Map.class);
    }

    /**
     * @param setup the test setup, logged in as superadmin
     * @param name the name of a SOP document
     * @return the content of the document
     * @throws Exception if the document can't be read
     */
    public static String getContent(TestUtils setup, String name) throws Exception
    {
        Page page = setup.rest().get(reference(name));
        return page.getContent();
    }

    /**
     * Checks that the content of an imported document doesn't hold its page header and footer.
     *
     * @param code the code of the document, in its page header
     * @param content the content of the document
     */
    public static void assertNoHeaderOrFooter(String code, String content)
    {
        for (String text : List.of(code, "Freigabe", "Erstellt von", "Überarbeitet von", "Revision geplant", "W:\\",
            "Seite 1", "Seite 2")) {
            assertFalse(content.contains(text), String.format("[%s] in:%n%s", text, content));
        }
    }

    /**
     * Reads the first page of a PDF attached to a document, and keeps the PDF in the target folder of the tests.
     *
     * @param setup the test setup
     * @param document the document
     * @param name the name of the PDF attachment
     * @return the text of the first page, by area
     * @throws Exception if the PDF can't be read
     */
    public static PDFPage readPDF(TestUtils setup, DocumentReference document, String name) throws Exception
    {
        String baseURL = StringUtils.removeEnd(setup.rest().getBaseURL(), "rest");
        URI base = URI.create(baseURL);
        StringBuilder path = new StringBuilder(base.getPath()).append("bin/download");
        document.getSpaceReferences().forEach(space -> path.append('/').append(space.getName()));
        path.append('/').append(document.getName()).append('/').append(name);
        URI url = new URI(base.getScheme(), null, base.getHost(), base.getPort(), path.toString(), null, null);
        String authorization = Base64.getEncoder()
            .encodeToString((TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName() + ':'
                + TestUtils.SUPER_ADMIN_CREDENTIALS.getPassword()).getBytes(StandardCharsets.UTF_8));
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(url).header("Authorization", "Basic " + authorization).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), url.toString());
        Path target = Paths.get("target", "pdf", name);
        Files.createDirectories(target.getParent());
        Files.write(target, response.body());

        try (PDDocument pdf = Loader.loadPDF(response.body())) {
            PDPage page = pdf.getPage(0);
            PDRectangle box = page.getMediaBox();
            float margin = box.getHeight() * MARGIN;
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.addRegion("header", new Rectangle2D.Float(0, 0, box.getWidth(), margin));
            stripper.addRegion("body", new Rectangle2D.Float(0, margin, box.getWidth(), box.getHeight() - 2 * margin));
            stripper.addRegion("footer", new Rectangle2D.Float(0, box.getHeight() - margin, box.getWidth(), margin));
            stripper.extractRegions(page);
            return new PDFPage(box.getWidth() > box.getHeight(), stripper.getTextForRegion("header"),
                stripper.getTextForRegion("body"), stripper.getTextForRegion("footer"),
                new PDFTextStripper().getText(pdf));
        }
    }
}
