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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.filemanager.internal.reference.DocumentNameSequence;
import org.xwiki.filemanager.reference.UniqueDocumentReferenceGenerator;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

@ComponentTest
class DefaultFileManagerStorageManagerTest
{
    @InjectMockComponents
    private DefaultFileManagerStorageManager storageManager;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @MockComponent
    @Named("currentmixed")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private UniqueDocumentReferenceGenerator uniqueDocRefGenerator;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    private Query query;

    private XWikiContext context;

    private XWiki wiki;

    @BeforeEach
    void setUp() throws Exception
    {
        this.query = mock(Query.class);
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);

        when(this.queryManager.createQuery(anyString(), eq(Query.HQL))).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);

        when(this.localEntityReferenceSerializer.serialize(any(EntityReference.class)))
            .thenReturn("serializedReference");
    }

    @Test
    void extractFolderNamesForNestedSpacesDocument()
    {
        DocumentReference reference = new DocumentReference("xwiki", List.of("Sandbox", "S1", "S2"), "WebHome");

        assertEquals(List.of("Sandbox", "S1", "S2"), this.storageManager.extractFolderNames(reference));
    }

    @Test
    void findExistingFileReturnsReferenceOnlyWhenParentMatches() throws Exception
    {
        String fullName = "FileManager.ZorroPDF";
        DocumentReference parentReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference candidateReference = new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF");

        when(this.query.execute()).thenReturn(List.of(fullName));
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(candidateReference);

        XWikiDocument candidateDoc = mock(XWikiDocument.class);
        when(this.wiki.getDocument(candidateReference, this.context)).thenReturn(candidateDoc);
        when(candidateDoc.getParentReference()).thenReturn(parentReference);

        DocumentReference result =
            this.storageManager.findExistingFile("Zorro.pdf", parentReference, this.context);

        assertEquals(candidateReference, result);
    }

    @Test
    void findExistingFileReturnsNullWhenParentDoesNotMatch() throws Exception
    {
        String fullName = "FileManager.ZorroPDF";
        DocumentReference expectedParentReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference differentParentReference = new DocumentReference("xwiki", List.of("FileManager"), "Other");
        DocumentReference candidateReference = new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF");

        when(this.query.execute()).thenReturn(List.of(fullName));
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(candidateReference);

        XWikiDocument candidateDoc = mock(XWikiDocument.class);
        when(this.wiki.getDocument(candidateReference, this.context)).thenReturn(candidateDoc);
        when(candidateDoc.getParentReference()).thenReturn(differentParentReference);

        DocumentReference result =
            this.storageManager.findExistingFile("Zorro.pdf", expectedParentReference, this.context);

        assertNull(result);
    }

    @Test
    void getFileManagerFileDocumentForStorageCreatesNewFileWhenNoExistingFileFound() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(this.storageManager);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "SandboxPDF");
        XWikiDocument fileDoc = mock(XWikiDocument.class);

        doReturn(null).when(storageManager).findExistingFile("Sandbox.pdf", null, this.context);

        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(fileReference);
        when(this.wiki.getDocument(fileReference, this.context)).thenReturn(fileDoc);

        XWikiDocument result = storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki",
            "Sandbox.pdf", null, sourceReference, 1);

        assertSame(fileDoc, result);
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageDeletesExistingLinkedFileForNewRevision() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(this.storageManager);

        XWikiDocument existingFileDoc = mock(XWikiDocument.class);
        XWikiDocument newFileDoc = mock(XWikiDocument.class);
        BaseObject backlinkObject = mock(BaseObject.class);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "SandboxPDF");

        doReturn(fileReference).when(storageManager).findExistingFile("Sandbox.pdf", null, this.context);

        when(this.wiki.getDocument(fileReference, this.context)).thenReturn(existingFileDoc, newFileDoc);
        verify(this.uniqueDocRefGenerator, never()).generate(any(SpaceReference.class), any(DocumentNameSequence.class));
        when(existingFileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(backlinkObject);
        when(this.localEntityReferenceSerializer.serialize(sourceReference)).thenReturn("Sandbox.WebHome");
        when(backlinkObject.getStringValue("backlink")).thenReturn("Sandbox.WebHome");

        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(fileReference);

        XWikiDocument result = storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki",
            "Sandbox.pdf", null, sourceReference, 2);

        assertSame(newFileDoc, result);
        verify(this.wiki).deleteDocument(existingFileDoc, this.context);
    }

    @Test
    void getFileManagerFileDocumentForStorageRefusesToDeleteUnlinkedFile() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(this.storageManager);

        XWikiDocument existingFileDoc = mock(XWikiDocument.class);
        BaseObject backlinkObject = mock(BaseObject.class);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "SandboxPDF");

        doReturn(fileReference).when(storageManager).findExistingFile("Sandbox.pdf", null, this.context);

        when(this.wiki.getDocument(fileReference, this.context)).thenReturn(existingFileDoc);
        when(existingFileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(backlinkObject);
        when(this.localEntityReferenceSerializer.serialize(sourceReference)).thenReturn("Sandbox.WebHome");
        when(backlinkObject.getStringValue("backlink")).thenReturn("Other.Page");

        XWikiException exception = assertThrows(XWikiException.class,
            () -> storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki", "Sandbox.pdf",
                null, sourceReference, 2));

        assertTrue(exception.getMessage().contains("not linked to the current SOP document"));
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageRefusesToReplaceFileForFirstRevision() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(this.storageManager);

        XWikiDocument existingFileDoc = mock(XWikiDocument.class);
        BaseObject backlinkObject = mock(BaseObject.class);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "SandboxPDF");

        doReturn(fileReference).when(storageManager).findExistingFile("Sandbox.pdf", null, this.context);

        when(this.wiki.getDocument(fileReference, this.context)).thenReturn(existingFileDoc);
        when(existingFileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(backlinkObject);
        when(this.localEntityReferenceSerializer.serialize(sourceReference)).thenReturn("Sandbox.WebHome");
        when(backlinkObject.getStringValue("backlink")).thenReturn("Sandbox.WebHome");

        XWikiException exception = assertThrows(XWikiException.class,
            () -> storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki", "Sandbox.pdf",
                null, sourceReference, 1));

        assertTrue(exception.getMessage().contains("first SOP revision"));
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void storeAttachmentStoresGeneratedPDFInNestedFileManagerFolder() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(this.storageManager);

        XWikiDocument fileDoc = mock(XWikiDocument.class);
        BaseObject fileObject = mock(BaseObject.class);
        BaseObject tagObject = mock(BaseObject.class);
        BaseObject backlinkObject = mock(BaseObject.class);
        XWikiAttachment attachment = mock(XWikiAttachment.class);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox", "Marmota"), "Zorro");
        DocumentReference sandboxFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference marmotaFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF");

        doReturn(sandboxFolderReference).when(storageManager)
            .getOrCreateFileManagerFolder("Sandbox", null, "xwiki", this.context);
        doReturn(marmotaFolderReference).when(storageManager)
            .getOrCreateFileManagerFolder("Marmota", sandboxFolderReference, "xwiki", this.context);
        doReturn(null).when(storageManager).findExistingFile("Zorro.pdf", marmotaFolderReference, this.context);

        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(fileReference);
        when(this.wiki.getDocument(fileReference, this.context)).thenReturn(fileDoc);
        when(this.localEntityReferenceSerializer.serialize(sourceReference)).thenReturn("Sandbox.Marmota.Zorro");
        when(this.localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveDocument"))
            .thenReturn("Store generated PDF in File Manager");

        when(fileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(null, null, null);
        when(fileDoc.newXObject(any(LocalDocumentReference.class), eq(this.context)))
            .thenReturn(fileObject, tagObject, backlinkObject);

        storageManager.storeAttachment(sourceReference, attachment, "Zorro.pdf", 2);

        verify(fileDoc).setTitle("Zorro.pdf");
        verify(fileDoc).setParentReference(
            marmotaFolderReference.removeParent(marmotaFolderReference.getWikiReference()));
        verify(tagObject).setDBStringListValue("tags", List.of("Marmota"));
        verify(backlinkObject).setStringValue("backlink", "Sandbox.Marmota.Zorro");
        verify(fileDoc).setAttachment(attachment);
        verify(this.wiki).saveDocument(fileDoc, "Store generated PDF in File Manager", this.context);
    }
}