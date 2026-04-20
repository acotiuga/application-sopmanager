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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            .thenReturn("FileManagerCode.FolderClass");
    }

    @Test
    void extractFolderNamesForSingleSpaceDocument()
    {
        DocumentReference reference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");

        assertEquals(List.of("Sandbox"), this.storageManager.extractFolderNames(reference));
    }

    @Test
    void extractFolderNamesForNestedSpacesDocument()
    {
        DocumentReference reference = new DocumentReference("xwiki", List.of("Sandbox", "S1", "S2"), "WebHome");

        assertEquals(List.of("Sandbox", "S1", "S2"), this.storageManager.extractFolderNames(reference));
    }

    @Test
    void findExistingFolderWhenNoResultsReturnsNull() throws Exception
    {
        DocumentReference result = this.storageManager.findExistingFolder("Sandbox", null, this.context);

        assertNull(result);
    }

    @Test
    void findExistingFolderWhenRootFolderMatchesReturnsReference() throws Exception
    {
        String fullName = "FileManager.Sandbox";
        DocumentReference candidateReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference fileManagerWebHome = new DocumentReference("xwiki", List.of("FileManager"), "WebHome");

        when(this.query.execute()).thenReturn(List.of(fullName));
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(candidateReference);

        XWikiDocument candidateDoc = mock(XWikiDocument.class);
        when(this.wiki.getDocument(eq(candidateReference), eq(this.context))).thenReturn(candidateDoc);
        when(candidateDoc.getParentReference()).thenReturn(fileManagerWebHome);

        DocumentReference result = this.storageManager.findExistingFolder("Sandbox", null, this.context);

        assertEquals(candidateReference, result);
    }

    @Test
    void findExistingFolderWhenNestedFolderParentMatchesReturnsReference() throws Exception
    {
        String fullName = "FileManager.Marmota";
        DocumentReference parentFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference candidateReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");

        when(this.query.execute()).thenReturn(List.of(fullName));
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(candidateReference);

        XWikiDocument candidateDoc = mock(XWikiDocument.class);
        when(this.wiki.getDocument(any(DocumentReference.class), eq(this.context))).thenReturn(candidateDoc);
        when(candidateDoc.getParentReference()).thenReturn(parentFolderReference);

        DocumentReference result =
            this.storageManager.findExistingFolder("Marmota", parentFolderReference, this.context);

        assertEquals(candidateReference, result);
    }

    @Test
    void findExistingFolderWhenNestedFolderParentDoesNotMatchReturnsNull() throws Exception
    {
        String fullName = "FileManager.Marmota";
        DocumentReference expectedParentFolderReference =
            new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference differentParentFolderReference =
            new DocumentReference("xwiki", List.of("FileManager"), "Sandbox1");
        DocumentReference candidateReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");

        when(this.query.execute()).thenReturn(List.of(fullName));
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(candidateReference);

        XWikiDocument candidateDoc = mock(XWikiDocument.class);
        when(this.wiki.getDocument(any(DocumentReference.class), eq(this.context))).thenReturn(candidateDoc);
        when(candidateDoc.getParentReference()).thenReturn(differentParentFolderReference);

        DocumentReference result =
            this.storageManager.findExistingFolder("Marmota", expectedParentFolderReference, this.context);

        assertNull(result);
    }

    @Test
    void getOrCreateFileManagerFolderReturnsExistingFolderWhenFound() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(new DefaultFileManagerStorageManager());

        QueryManager queryManager = mock(QueryManager.class);
        UniqueDocumentReferenceGenerator uniqueDocRefGenerator = mock(UniqueDocumentReferenceGenerator.class);
        EntityReferenceSerializer<String> serializer = mock(EntityReferenceSerializer.class);
        DocumentReferenceResolver<String> resolver = mock(DocumentReferenceResolver.class);

        setField(storageManager, "queryManager", queryManager);
        setField(storageManager, "uniqueDocRefGenerator", uniqueDocRefGenerator);
        setField(storageManager, "localEntityReferenceSerializer", serializer);
        setField(storageManager, "documentReferenceResolver", resolver);

        XWikiContext context = mock(XWikiContext.class);
        XWiki wiki = mock(XWiki.class);
        when(context.getWiki()).thenReturn(wiki);

        DocumentReference existingReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");

        doReturn(existingReference).when(storageManager)
            .findExistingFolder("Sandbox", null, context);

        DocumentReference result =
            storageManager.getOrCreateFileManagerFolder("Sandbox", null, "xwiki", context);

        assertEquals(existingReference, result);
        verify(uniqueDocRefGenerator, never()).generate(any(), any());
        verify(wiki, never()).saveDocument(any(), anyString(), eq(context));
    }

    @Test
    void getOrCreateFileManagerFolderCreatesRootFolderWhenMissing() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(new DefaultFileManagerStorageManager());

        UniqueDocumentReferenceGenerator uniqueDocRefGenerator = mock(UniqueDocumentReferenceGenerator.class);
        ContextualLocalizationManager localizationManager = mock(ContextualLocalizationManager.class);

        setField(storageManager, "uniqueDocRefGenerator", uniqueDocRefGenerator);
        setField(storageManager, "localizationManager", localizationManager);

        XWikiContext context = mock(XWikiContext.class);
        XWiki wiki = mock(XWiki.class);
        XWikiDocument folderDoc = mock(XWikiDocument.class);
        DocumentReference currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(context.getWiki()).thenReturn(wiki);
        when(context.getUserReference()).thenReturn(currentUser);
        when(localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveFolder"))
            .thenReturn("Create File Manager folder");

        DocumentReference generatedReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        when(uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(generatedReference);

        doReturn(null).when(storageManager).findExistingFolder("Sandbox", null, context);
        when(wiki.getDocument(generatedReference, context)).thenReturn(folderDoc);
        when(folderDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(null);

        DocumentReference result =
            storageManager.getOrCreateFileManagerFolder("Sandbox", null, "xwiki", context);

        assertEquals(generatedReference, result);
        verify(folderDoc).setTitle("Sandbox");
        verify(folderDoc).setParentReference(any(LocalDocumentReference.class));
        verify(folderDoc).newXObject(any(LocalDocumentReference.class), eq(context));
        verify(folderDoc).setCreatorReference(currentUser);
        verify(folderDoc).setAuthorReference(currentUser);
        verify(wiki).saveDocument(folderDoc, "Create File Manager folder", context);
    }

    @Test
    void getOrCreateFileManagerFolderCreatesNestedFolderWithParent() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(new DefaultFileManagerStorageManager());

        UniqueDocumentReferenceGenerator uniqueDocRefGenerator = mock(UniqueDocumentReferenceGenerator.class);
        ContextualLocalizationManager localizationManager = mock(ContextualLocalizationManager.class);

        setField(storageManager, "uniqueDocRefGenerator", uniqueDocRefGenerator);
        setField(storageManager, "localizationManager", localizationManager);

        XWikiContext context = mock(XWikiContext.class);
        XWiki wiki = mock(XWiki.class);
        XWikiDocument folderDoc = mock(XWikiDocument.class);
        DocumentReference currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(context.getWiki()).thenReturn(wiki);
        when(context.getUserReference()).thenReturn(currentUser);
        when(localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveFolder"))
            .thenReturn("Create File Manager folder");

        DocumentReference parentReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference generatedReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");

        when(uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(generatedReference);

        doReturn(null).when(storageManager).findExistingFolder("Marmota", parentReference, context);
        when(wiki.getDocument(generatedReference, context)).thenReturn(folderDoc);
        when(folderDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(null);

        DocumentReference result =
            storageManager.getOrCreateFileManagerFolder("Marmota", parentReference, "xwiki", context);

        assertEquals(generatedReference, result);
        verify(folderDoc).setTitle("Marmota");
        verify(folderDoc).setParentReference(parentReference.removeParent(parentReference.getWikiReference()));
        verify(folderDoc).newXObject(any(LocalDocumentReference.class), eq(context));
        verify(folderDoc).setCreatorReference(currentUser);
        verify(folderDoc).setAuthorReference(currentUser);
        verify(wiki).saveDocument(folderDoc, "Create File Manager folder", context);
    }

    @Test
    void storeAttachmentStoresFileInRootFolder() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(new DefaultFileManagerStorageManager());

        Provider<XWikiContext> xcontextProvider = mock(Provider.class);
        UniqueDocumentReferenceGenerator uniqueDocRefGenerator = mock(UniqueDocumentReferenceGenerator.class);
        @SuppressWarnings("unchecked")
        EntityReferenceSerializer<String> localSerializer = mock(EntityReferenceSerializer.class);
        ContextualLocalizationManager localizationManager = mock(ContextualLocalizationManager.class);

        setField(storageManager, "xcontextProvider", xcontextProvider);
        setField(storageManager, "uniqueDocRefGenerator", uniqueDocRefGenerator);
        setField(storageManager, "localEntityReferenceSerializer", localSerializer);
        setField(storageManager, "localizationManager", localizationManager);

        XWikiContext context = mock(XWikiContext.class);
        XWiki wiki = mock(XWiki.class);
        XWikiDocument fileDoc = mock(XWikiDocument.class);
        BaseObject fileObject = mock(BaseObject.class);
        BaseObject tagObject = mock(BaseObject.class);
        BaseObject backlinkObject = mock(BaseObject.class);

        when(xcontextProvider.get()).thenReturn(context);
        when(context.getWiki()).thenReturn(wiki);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference sandboxFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox.pdf");
        DocumentReference currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        XWikiAttachment attachment = new XWikiAttachment();
        attachment.setFilename("Sandbox.pdf");

        doReturn(sandboxFolderReference).when(storageManager)
            .getOrCreateFileManagerFolder("Sandbox", null, "xwiki", context);

        when(uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(fileReference);

        when(wiki.getDocument(fileReference, context)).thenReturn(fileDoc);
        when(context.getUserReference()).thenReturn(currentUser);
        when(localSerializer.serialize(sourceReference)).thenReturn("Sandbox.WebHome");
        when(localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveDocument"))
            .thenReturn("Store generated PDF in File Manager");

        when(fileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(null, null, null);
        when(fileDoc.newXObject(any(LocalDocumentReference.class), eq(context)))
            .thenReturn(fileObject, tagObject, backlinkObject);

        storageManager.storeAttachment(sourceReference, attachment, "Sandbox.pdf");

        verify(fileDoc).setTitle("Sandbox.pdf");
        verify(fileDoc, times(3)).newXObject(any(LocalDocumentReference.class), eq(context));
        verify(fileDoc).setParentReference(
            sandboxFolderReference.removeParent(sandboxFolderReference.getWikiReference()));
        verify(tagObject).setDBStringListValue("tags", List.of("Sandbox"));
        verify(backlinkObject).setStringValue("backlink", "Sandbox.WebHome");
        verify(fileDoc).setAttachment(attachment);
        verify(fileDoc).setCreatorReference(currentUser);
        verify(fileDoc).setAuthorReference(currentUser);
        verify(wiki).saveDocument(fileDoc, "Store generated PDF in File Manager", context);
    }

    @Test
    void storeAttachmentStoresFileInNestedFolder() throws Exception
    {
        DefaultFileManagerStorageManager storageManager = spy(new DefaultFileManagerStorageManager());

        Provider<XWikiContext> xcontextProvider = mock(Provider.class);
        UniqueDocumentReferenceGenerator uniqueDocRefGenerator = mock(UniqueDocumentReferenceGenerator.class);
        @SuppressWarnings("unchecked")
        EntityReferenceSerializer<String> localSerializer = mock(EntityReferenceSerializer.class);
        ContextualLocalizationManager localizationManager = mock(ContextualLocalizationManager.class);

        setField(storageManager, "xcontextProvider", xcontextProvider);
        setField(storageManager, "uniqueDocRefGenerator", uniqueDocRefGenerator);
        setField(storageManager, "localEntityReferenceSerializer", localSerializer);
        setField(storageManager, "localizationManager", localizationManager);

        XWikiContext context = mock(XWikiContext.class);
        XWiki wiki = mock(XWiki.class);
        XWikiDocument fileDoc = mock(XWikiDocument.class);
        BaseObject fileObject = mock(BaseObject.class);
        BaseObject tagObject = mock(BaseObject.class);
        BaseObject backlinkObject = mock(BaseObject.class);

        when(xcontextProvider.get()).thenReturn(context);
        when(context.getWiki()).thenReturn(wiki);

        DocumentReference sourceReference = new DocumentReference("xwiki", List.of("Sandbox", "Marmota"), "Zorro");
        DocumentReference sandboxFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference marmotaFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference fileReference = new DocumentReference("xwiki", List.of("FileManager"), "Zorro.pdf");
        DocumentReference currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        XWikiAttachment attachment = new XWikiAttachment();
        attachment.setFilename("Zorro.pdf");

        doReturn(sandboxFolderReference).when(storageManager)
            .getOrCreateFileManagerFolder("Sandbox", null, "xwiki", context);
        doReturn(marmotaFolderReference).when(storageManager)
            .getOrCreateFileManagerFolder("Marmota", sandboxFolderReference, "xwiki", context);

        when(uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(fileReference);

        when(wiki.getDocument(fileReference, context)).thenReturn(fileDoc);
        when(context.getUserReference()).thenReturn(currentUser);
        when(localSerializer.serialize(sourceReference)).thenReturn("Sandbox.Marmota.Zorro");
        when(localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveDocument"))
            .thenReturn("Store generated PDF in File Manager");

        when(fileDoc.getXObject(any(LocalDocumentReference.class))).thenReturn(null, null, null);
        when(fileDoc.newXObject(any(LocalDocumentReference.class), eq(context)))
            .thenReturn(fileObject, tagObject, backlinkObject);

        storageManager.storeAttachment(sourceReference, attachment, "Zorro.pdf");

        verify(fileDoc).setTitle("Zorro.pdf");
        verify(fileDoc).setParentReference(
            marmotaFolderReference.removeParent(marmotaFolderReference.getWikiReference()));
        verify(tagObject).setDBStringListValue("tags", List.of("Marmota"));
        verify(backlinkObject).setStringValue("backlink", "Sandbox.Marmota.Zorro");
        verify(fileDoc).setAttachment(attachment);
        verify(fileDoc).setCreatorReference(currentUser);
        verify(fileDoc).setAuthorReference(currentUser);
        verify(wiki).saveDocument(fileDoc, "Store generated PDF in File Manager", context);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception
    {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}