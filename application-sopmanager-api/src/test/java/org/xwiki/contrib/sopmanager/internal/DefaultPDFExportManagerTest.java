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
package org.xwiki.contrib.sopmanager.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xwiki.contrib.sopmanager.FileManagerStorageManager;
import org.xwiki.contrib.sopmanager.PDFExportJobManager;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobRequestFactory;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiURLFactory;

/**
 * Unit tests for {@link DefaultPDFExportManager}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
class DefaultPDFExportManagerTest
{
    private static final LocalDocumentReference CONTROLLED_DOC_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    @InjectMockComponents
    private DefaultPDFExportManager pdfExportManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private PDFExportJobManager pdfExportJobManager;

    @MockComponent
    private PDFExportJobRequestFactory requestFactory;

    @MockComponent
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    @MockComponent
    private FileManagerStorageManager fileManagerStorageManager;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @TempDir
    private Path temporaryDirectory;

    private XWikiContext context;

    private XWiki wiki;

    private XWikiURLFactory urlFactory;

    private XWikiDocument attachmentDoc;

    private XWikiDocument previousDoc;

    private PDFExportJobRequest request;

    private DocumentReference documentReference;

    private DocumentReference userReference;

    private DocumentReference pdfTemplateReference;

    private URL baseURL;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.urlFactory = mock(XWikiURLFactory.class);
        this.attachmentDoc = mock(XWikiDocument.class);
        this.previousDoc = mock(XWikiDocument.class);
        this.request = mock(PDFExportJobRequest.class);

        this.documentReference = new DocumentReference("xwiki", List.of("SOPs", "Quality"), "Procedure");
        this.userReference = new DocumentReference("xwiki", List.of("XWiki"), "Admin");
        this.pdfTemplateReference = new DocumentReference("xwiki", List.of("SOPManager", "Code"),
            "GKHPDFTemplateVertical");
        this.baseURL = new URL("https://example.org/xwiki/bin/view/SOPs/Quality/");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getDoc()).thenReturn(this.previousDoc);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getURLFactory()).thenReturn(this.urlFactory);
        when(this.context.getUserReference()).thenReturn(this.userReference);

        when(this.attachmentDoc.getDocumentReference()).thenReturn(this.documentReference);
        when(this.attachmentDoc.getTitle()).thenReturn("Procedure");

        when(this.requestFactory.createRequest()).thenReturn(this.request);
        when(this.defaultEntityReferenceSerializer.serialize(any(EntityReference.class))).thenReturn("SOPs/Quality");
        when(this.urlFactory.createExternalURL(eq("SOPs/Quality"), eq("Procedure"), eq("view"), eq(""), eq(""),
            eq("xwiki"), eq(this.context))).thenReturn(this.baseURL);

        when(this.localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.saveDocument"))
            .thenReturn("Attach generated PDF");
    }

    @Test
    void exportAndAttachPDFUsesTemplateAndRevisionIdInAttachmentName() throws Exception
    {
        DefaultPDFExportManager testedManager = spy(this.pdfExportManager);

        BaseObject controlledObj = mock(BaseObject.class);
        File pdfFile = createPDFFile();
        XWikiAttachment attachment = mock(XWikiAttachment.class);

        when(this.attachmentDoc.getXObject(CONTROLLED_DOC_CLASS)).thenReturn(controlledObj);
        when(controlledObj.getStringValue("revisionId")).thenReturn("SOP-001");

        when(this.pdfExportJobManager.export(this.request)).thenReturn(pdfFile);
        when(this.localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.success",
            "SOP-001_Procedure.pdf")).thenReturn("Generated SOP-001_Procedure.pdf");

        doReturn(attachment).when(testedManager).createAttachment(pdfFile, "SOP-001_Procedure.pdf", this.context);

        String result = testedManager.exportAndAttachPDF(this.attachmentDoc, this.pdfTemplateReference);

        assertEquals("Generated SOP-001_Procedure.pdf", result);

        verify(this.request).setDocuments(List.of(this.documentReference));
        verify(this.request).setTemplate(this.pdfTemplateReference);
        verify(this.request).setWithCover(false);
        verify(this.request).setWithToc(false);
        verify(this.request).setBaseURL(this.baseURL);
        verify(this.request).setId(eq("export"), eq("pdf"), eq(this.documentReference.toString()), anyString());

        verify(testedManager).createAttachment(pdfFile, "SOP-001_Procedure.pdf", this.context);
        verify(this.attachmentDoc).setAttachment(attachment);
        verify(this.attachmentDoc).setAuthorReference(this.userReference);

        verify(this.wiki).saveDocument(this.attachmentDoc, "Attach generated PDF", this.context);
        verify(this.fileManagerStorageManager).storeAttachment(this.documentReference, attachment,
            "SOP-001_Procedure.pdf");

        verify(this.context).setDoc(this.attachmentDoc);
        verify(this.context).setDoc(this.previousDoc);
    }

    @Test
    void exportAndAttachPDFWorksWithoutTemplateAndWithoutRevisionId() throws Exception
    {
        DefaultPDFExportManager testedManager = spy(this.pdfExportManager);

        File pdfFile = createPDFFile();
        XWikiAttachment attachment = mock(XWikiAttachment.class);

        when(this.attachmentDoc.getXObject(CONTROLLED_DOC_CLASS)).thenReturn(null);

        when(this.pdfExportJobManager.export(this.request)).thenReturn(pdfFile);
        when(this.localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.success",
            "Procedure.pdf")).thenReturn("Generated Procedure.pdf");

        doReturn(attachment).when(testedManager).createAttachment(pdfFile, "Procedure.pdf", this.context);

        String result = testedManager.exportAndAttachPDF(this.attachmentDoc, null);

        assertEquals("Generated Procedure.pdf", result);

        verify(this.request).setDocuments(List.of(this.documentReference));
        verify(this.request, never()).setTemplate(any(DocumentReference.class));
        verify(this.request).setWithCover(false);
        verify(this.request).setWithToc(false);
        verify(this.request).setBaseURL(this.baseURL);
        verify(this.request).setId(eq("export"), eq("pdf"), eq(this.documentReference.toString()), anyString());

        verify(testedManager).createAttachment(pdfFile, "Procedure.pdf", this.context);
        verify(this.attachmentDoc).setAttachment(attachment);
        verify(this.attachmentDoc).setAuthorReference(this.userReference);

        verify(this.fileManagerStorageManager).storeAttachment(this.documentReference, attachment, "Procedure.pdf");
        verify(this.wiki).saveDocument(this.attachmentDoc, "Attach generated PDF", this.context);

        verify(this.context).setDoc(this.attachmentDoc);
        verify(this.context).setDoc(this.previousDoc);
    }

    @Test
    void exportAndAttachPDFRestoresContextDocumentAndWrapsException() throws Exception
    {
        DefaultPDFExportManager testedManager = spy(this.pdfExportManager);

        RuntimeException attachmentException = new RuntimeException("Attachment creation failed");
        File pdfFile = createPDFFile();

        when(this.attachmentDoc.getXObject(CONTROLLED_DOC_CLASS)).thenReturn(null);

        when(this.pdfExportJobManager.export(this.request)).thenReturn(pdfFile);
        when(this.localizationManager.getTranslationPlain(
            "sopManager.defaultPDFExportManager.error.attachFailed", this.documentReference))
            .thenReturn("Failed to attach generated PDF.");

        doThrow(attachmentException).when(testedManager).createAttachment(pdfFile, "Procedure.pdf", this.context);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> testedManager.exportAndAttachPDF(this.attachmentDoc, this.pdfTemplateReference));

        assertEquals("Failed to attach generated PDF.", exception.getMessage());
        assertSame(attachmentException, exception.getCause());

        verify(this.request).setDocuments(List.of(this.documentReference));
        verify(this.request).setTemplate(this.pdfTemplateReference);
        verify(testedManager).createAttachment(pdfFile, "Procedure.pdf", this.context);

        verify(this.attachmentDoc, never()).setAttachment(any(XWikiAttachment.class));
        verify(this.wiki, never()).saveDocument(eq(this.attachmentDoc), anyString(), eq(this.context));
        verify(this.fileManagerStorageManager, never()).storeAttachment(any(), any(), anyString());

        verify(this.context).setDoc(this.attachmentDoc);
        verify(this.context).setDoc(this.previousDoc);
    }

    private File createPDFFile() throws Exception
    {
        Path pdfFile = this.temporaryDirectory.resolve("export.pdf");
        Files.writeString(pdfFile, "PDF content");

        return pdfFile.toFile();
    }
}