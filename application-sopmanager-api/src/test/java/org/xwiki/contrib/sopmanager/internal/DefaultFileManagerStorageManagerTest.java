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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xwiki.environment.Environment;
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
import org.xwiki.query.QueryException;
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

/**
 * Unit tests for {@link DefaultFileManagerStorageManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultFileManagerStorageManagerTest
{
    private static final String PDF_CONTENT = "%PDF-1.4 test content";

    private static final String SAVE_FILE_COMMENT = "Store generated PDF in File Manager";

    private static final LocalDocumentReference CONTROLLED_DOCUMENT_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    private static final LocalDocumentReference ORIGINAL_DETAILS_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "OriginalDocumentDetailsClass");

    private static final LocalDocumentReference DRIVE_CLASS =
        new LocalDocumentReference(List.of("FileManagerCode"), "DriveClass");

    private static final LocalDocumentReference FOLDER_CLASS =
        new LocalDocumentReference(List.of("FileManagerCode"), "FolderClass");

    private static final LocalDocumentReference FILE_CLASS =
        new LocalDocumentReference(List.of("FileManagerCode"), "FileClass");

    private static final LocalDocumentReference TAG_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "TagClass");

    private static final DocumentReference DRIVE_REFERENCE =
        new DocumentReference("xwiki", List.of("FileManager"), "WebHome");

    private static final DocumentReference FILE_REFERENCE =
        new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF");

    private static final DocumentReference SOURCE_REFERENCE =
        new DocumentReference("xwiki", List.of("Sandbox", "Marmota"), "Zorro");

    private static final DocumentReference CURRENT_USER = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

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

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @MockComponent
    private Environment environment;

    @TempDir
    private Path permanentDirectory;

    private XWikiContext context;

    private XWiki wiki;

    /**
     * The full names of the File Manager documents returned by the lookup query, indexed by the queried title.
     */
    private final Map<String, List<String>> documentNamesByTitle = new HashMap<>();

    private XWikiDocument fileDocument;

    private BaseObject fileTagObject;

    private BaseObject fileDetailsObject;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(CURRENT_USER);
        when(this.environment.getPermanentDirectory()).thenReturn(this.permanentDirectory.toFile());

        when(this.queryManager.createQuery(anyString(), eq(Query.HQL))).thenAnswer(invocation -> mockLookupQuery());
        when(this.localEntityReferenceSerializer.serialize(any(EntityReference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0).toString());
        when(this.localEntityReferenceSerializer.serialize(SOURCE_REFERENCE)).thenReturn("Sandbox.Marmota.Zorro");
        when(this.localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveDocument"))
            .thenReturn(SAVE_FILE_COMMENT);
    }

    @Test
    void extractFolderNamesReturnsTheSpacesFromRootToLeaf()
    {
        DocumentReference reference = new DocumentReference("xwiki", List.of("Sandbox", "S1", "S2"), "WebHome");

        assertEquals(List.of("Sandbox", "S1", "S2"), this.storageManager.extractFolderNames(reference));
    }

    @Test
    void findExistingFileReturnsTheCandidateWithTheExpectedParent() throws Exception
    {
        DocumentReference parentReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference otherParentReference = new DocumentReference("xwiki", List.of("FileManager"), "Other");
        DocumentReference otherFileReference = new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF1");

        mockExistingFile("Zorro.pdf", otherFileReference, otherParentReference);
        mockExistingFile("Zorro.pdf", FILE_REFERENCE, parentReference);

        assertEquals(FILE_REFERENCE, this.storageManager.findExistingFile("Zorro.pdf", parentReference, this.context));
    }

    @Test
    void findExistingFileReturnsNullWhenNoCandidateHasTheExpectedParent() throws Exception
    {
        DocumentReference parentReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        DocumentReference otherParentReference = new DocumentReference("xwiki", List.of("FileManager"), "Other");
        DocumentReference orphanFileReference = new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF1");

        mockExistingFile("Zorro.pdf", FILE_REFERENCE, otherParentReference);
        mockExistingFile("Zorro.pdf", orphanFileReference, null);

        assertNull(this.storageManager.findExistingFile("Zorro.pdf", parentReference, this.context));
        // Without parent folder, the file has to be at the root of the drive.
        assertNull(this.storageManager.findExistingFile("Zorro.pdf", null, this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageGeneratesANewReferenceWhenNoFileExists() throws Exception
    {
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(FILE_REFERENCE);
        XWikiDocument newFileDocument = mock(XWikiDocument.class);
        when(this.wiki.getDocument(FILE_REFERENCE, this.context)).thenReturn(newFileDocument);

        XWikiDocument result = this.storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki",
            "Zorro.pdf", null, SOURCE_REFERENCE, 1);

        assertSame(newFileDocument, result);
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageReplacesTheLinkedFileForANewRevision() throws Exception
    {
        XWikiDocument existingFileDocument = mockExistingFile("Zorro.pdf", FILE_REFERENCE, DRIVE_REFERENCE);
        XWikiDocument newFileDocument = mock(XWikiDocument.class);
        when(this.wiki.getDocument(FILE_REFERENCE, this.context)).thenReturn(existingFileDocument,
            existingFileDocument, newFileDocument);
        mockBacklink(existingFileDocument, "Sandbox.Marmota.Zorro");

        XWikiDocument result = this.storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki",
            "Zorro.pdf", null, SOURCE_REFERENCE, 2);

        assertSame(newFileDocument, result);
        verify(this.wiki).deleteDocument(existingFileDocument, this.context);
        verifyNoInteractions(this.uniqueDocRefGenerator);
    }

    @Test
    void getFileManagerFileDocumentForStorageRefusesToReplaceAFileOfAnotherDocument() throws Exception
    {
        XWikiDocument existingFileDocument = mockExistingFile("Zorro.pdf", FILE_REFERENCE, DRIVE_REFERENCE);
        mockBacklink(existingFileDocument, "Other.Page");

        XWikiException exception = assertThrows(XWikiException.class,
            () -> this.storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki", "Zorro.pdf", null,
                SOURCE_REFERENCE, 2));

        assertThat(exception.getMessage(), containsString("not linked to the current SOP document"));
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageRefusesToReplaceAFileWithoutBacklink() throws Exception
    {
        mockExistingFile("Zorro.pdf", FILE_REFERENCE, DRIVE_REFERENCE);

        XWikiException exception = assertThrows(XWikiException.class,
            () -> this.storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki", "Zorro.pdf", null,
                SOURCE_REFERENCE, 2));

        assertThat(exception.getMessage(), containsString("not linked to the current SOP document"));
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void getFileManagerFileDocumentForStorageRefusesToReplaceAFileForTheFirstRevision() throws Exception
    {
        XWikiDocument existingFileDocument = mockExistingFile("Zorro.pdf", FILE_REFERENCE, DRIVE_REFERENCE);
        mockBacklink(existingFileDocument, "Sandbox.Marmota.Zorro");

        XWikiException exception = assertThrows(XWikiException.class,
            () -> this.storageManager.getFileManagerFileDocumentForStorage(this.context, "xwiki", "Zorro.pdf", null,
                SOURCE_REFERENCE, 1));

        assertThat(exception.getMessage(), containsString("first SOP revision"));
        verify(this.wiki, never()).deleteDocument(any(XWikiDocument.class), eq(this.context));
    }

    @Test
    void storeAttachmentMirrorsTheSourceSpacesAsFileManagerFolders() throws Exception
    {
        DocumentReference sandboxFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Sandbox");
        DocumentReference marmotaFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Marmota");
        BaseObject sopTagsObject = mock(BaseObject.class);
        XWikiAttachment attachment = mockAttachmentWithPDFContent();

        mockSourceDocument("");
        // The Sandbox folder already exists, the Marmota folder has to be created inside it.
        mockFolder(sandboxFolderReference, "Sandbox", DRIVE_REFERENCE);
        mockLookupResult("Sandbox", sandboxFolderReference);
        XWikiDocument marmotaFolderDocument = mockFolder(marmotaFolderReference, "Marmota", sandboxFolderReference);
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(marmotaFolderReference, FILE_REFERENCE);
        when(this.localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.saveFolder"))
            .thenReturn("Create folder");
        mockNewFileDocument();
        when(sopTagsObject.getListValue("tags")).thenReturn(List.of("test", "marmota"));

        DocumentReference result =
            this.storageManager.storeAttachment(SOURCE_REFERENCE, attachment, "Zorro.pdf", 2, sopTagsObject);

        assertEquals(FILE_REFERENCE, result);
        assertStoredPDF("Sandbox", "Marmota", "Zorro.pdf");

        verify(marmotaFolderDocument).setTitle("Marmota");
        verify(marmotaFolderDocument).setParentReference(sandboxFolderReference.getLocalDocumentReference());
        verify(this.wiki).saveDocument(marmotaFolderDocument, "Create folder", this.context);

        verify(this.fileDocument).setTitle("Zorro.pdf");
        verify(this.fileDocument).newXObject(FILE_CLASS, this.context);
        verify(this.fileDocument).setParentReference(marmotaFolderReference.getLocalDocumentReference());
        verify(this.fileTagObject).setDBStringListValue("tags", List.of("Marmota"));
        verify(this.fileDetailsObject).setStringValue("backlink", "Sandbox.Marmota.Zorro");
        verify(this.fileDetailsObject).setDBStringListValue("sopTags", List.of("test", "marmota"));
        verify(attachment).setDoc(this.fileDocument);
        verify(this.fileDocument).setAttachment(attachment);
        verify(this.fileDocument).setCreatorReference(CURRENT_USER);
        verify(this.fileDocument).setAuthorReference(CURRENT_USER);
        verify(this.wiki).saveDocument(this.fileDocument, SAVE_FILE_COMMENT, this.context);
    }

    @Test
    void storeAttachmentUsesTheConfiguredFileManagerFolder() throws Exception
    {
        DocumentReference selectedFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "SOPTest");

        mockSourceDocument("xwiki:FileManager.SOPTest", selectedFolderReference);
        mockFolder(selectedFolderReference, "SOPTest", DRIVE_REFERENCE);
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(FILE_REFERENCE);
        mockNewFileDocument();

        DocumentReference result = this.storageManager.storeAttachment(SOURCE_REFERENCE,
            mockAttachmentWithPDFContent(), "Zorro.pdf", 1, null);

        assertEquals(FILE_REFERENCE, result);
        assertStoredPDF("SOPTest", "Zorro.pdf");

        // The configured folder replaces the folders mirroring the source spaces.
        verify(this.uniqueDocRefGenerator).generate(any(SpaceReference.class), any(DocumentNameSequence.class));
        verify(this.fileDocument).setParentReference(selectedFolderReference.getLocalDocumentReference());
        verify(this.fileTagObject).setDBStringListValue("tags", List.of("SOPTest"));
        verify(this.fileDetailsObject, never()).setDBStringListValue(eq("sopTags"), any());
        verify(this.wiki).saveDocument(this.fileDocument, SAVE_FILE_COMMENT, this.context);
    }

    @Test
    void storeAttachmentUsesTheConfiguredFileManagerDrive() throws Exception
    {
        XWikiDocument driveDocument = mock(XWikiDocument.class);

        mockSourceDocument("xwiki:FileManager.WebHome", DRIVE_REFERENCE);
        when(this.wiki.getDocument(DRIVE_REFERENCE, this.context)).thenReturn(driveDocument);
        when(driveDocument.getXObject(DRIVE_CLASS)).thenReturn(mock(BaseObject.class));
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(FILE_REFERENCE);
        mockNewFileDocument();

        DocumentReference result = this.storageManager.storeAttachment(SOURCE_REFERENCE,
            mockAttachmentWithPDFContent(), "Zorro.pdf", 1, null);

        assertEquals(FILE_REFERENCE, result);
        assertStoredPDF("Zorro.pdf");

        verify(this.fileDocument).setParentReference(DRIVE_REFERENCE.getLocalDocumentReference());
        verify(this.fileTagObject).setDBStringListValue("tags", List.of());
    }

    @Test
    void storeAttachmentRejectsAConfiguredLocationThatIsNotADriveOrFolder() throws Exception
    {
        DocumentReference locationReference = new DocumentReference("xwiki", List.of("Main"), "WebHome");

        mockSourceDocument("xwiki:Main.WebHome", locationReference);
        when(this.wiki.getDocument(locationReference, this.context)).thenReturn(mock(XWikiDocument.class));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.storageManager
            .storeAttachment(SOURCE_REFERENCE, mock(XWikiAttachment.class), "Zorro.pdf", 1, null));

        assertThat(exception.getCause().getMessage(), containsString("is not a File Manager drive or folder"));
        verifyNoInteractions(this.uniqueDocRefGenerator);
        verify(this.wiki, never()).saveDocument(any(), anyString(), eq(this.context));
    }

    @Test
    void storeAttachmentRejectsAConfiguredLocationFromAnotherWiki() throws Exception
    {
        DocumentReference locationReference = new DocumentReference("subwiki", List.of("FileManager"), "SOPTest");

        mockSourceDocument("subwiki:FileManager.SOPTest", locationReference);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.storageManager
            .storeAttachment(SOURCE_REFERENCE, mock(XWikiAttachment.class), "Zorro.pdf", 1, null));

        assertThat(exception.getCause().getMessage(), containsString("must belong to the same wiki"));
        verifyNoInteractions(this.uniqueDocRefGenerator);
        verify(this.wiki, never()).saveDocument(any(), anyString(), eq(this.context));
    }

    @Test
    void storeAttachmentRejectsFolderTitlesEscapingTheStorageDirectory() throws Exception
    {
        DocumentReference selectedFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Up");

        mockSourceDocument("xwiki:FileManager.Up", selectedFolderReference);
        mockFolder(selectedFolderReference, "..", DRIVE_REFERENCE);
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(FILE_REFERENCE);
        when(this.localizationManager.getTranslationPlain(
            "sopManager.defaultFileManagerStorageManager.error.invalidArchivePathSegment", ".."))
            .thenReturn("Invalid path segment [..]");
        mockNewFileDocument();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.storageManager
            .storeAttachment(SOURCE_REFERENCE, mockAttachmentWithPDFContent(), "Zorro.pdf", 1, null));

        assertEquals("Invalid path segment [..]", exception.getCause().getMessage());
        assertFalse(Files.exists(this.permanentDirectory.resolve("Zorro.pdf")));
        verify(this.wiki, never()).saveDocument(any(), anyString(), eq(this.context));
    }

    @Test
    void archiveFileMovesThePDFAndTheFileManagerDocumentToTheArchiveFolder() throws Exception
    {
        DocumentReference folderReference = new DocumentReference("xwiki", List.of("FileManager"), "SOPTest");
        DocumentReference archiveFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Archiv");
        Path storedPDF = writeStoredPDF("SOPTest", "Zorro.pdf");

        XWikiDocument archivedDocument = mockLinkedFileDocument(folderReference);
        mockFolder(folderReference, "SOPTest", DRIVE_REFERENCE);
        XWikiDocument archiveFolderDocument = mockFolder(archiveFolderReference, "Archiv", DRIVE_REFERENCE);
        when(this.uniqueDocRefGenerator.generate(any(SpaceReference.class), any(DocumentNameSequence.class)))
            .thenReturn(archiveFolderReference);
        when(archivedDocument.getXObject(TAG_CLASS)).thenReturn(this.fileTagObject);
        when(this.localizationManager.getTranslationPlain("sopManager.defaultFileManagerStorageManager.archiveDocument"))
            .thenReturn("Archive document");

        this.storageManager.archiveFile(FILE_REFERENCE, SOURCE_REFERENCE);

        assertFalse(Files.exists(storedPDF));
        assertStoredPDF("Archiv", "Zorro.pdf");
        verify(this.wiki).saveDocument(eq(archiveFolderDocument), any(), eq(this.context));
        verify(archivedDocument).setParentReference(archiveFolderReference.getLocalDocumentReference());
        verify(this.fileTagObject).setDBStringListValue("tags", List.of("Archiv"));
        verify(archivedDocument).setAuthorReference(CURRENT_USER);
        verify(this.wiki).saveDocument(archivedDocument, "Archive document", this.context);
        verify(this.wiki, never()).deleteDocument(any(), eq(this.context));
    }

    @Test
    void archiveFileReplacesAPreviouslyArchivedFileWithTheSameName() throws Exception
    {
        DocumentReference archiveFolderReference = new DocumentReference("xwiki", List.of("FileManager"), "Archiv");
        DocumentReference previousArchiveReference =
            new DocumentReference("xwiki", List.of("FileManager"), "ZorroPDF1");
        writeStoredPDF("Zorro.pdf");
        writeStoredPDF("Archiv", "Zorro.pdf");

        XWikiDocument archivedDocument = mockLinkedFileDocument(DRIVE_REFERENCE);
        mockFolder(archiveFolderReference, "Archiv", DRIVE_REFERENCE);
        mockLookupResult("Archiv", archiveFolderReference);
        XWikiDocument previousArchiveDocument =
            mockExistingFile("Zorro.pdf", previousArchiveReference, archiveFolderReference);

        this.storageManager.archiveFile(FILE_REFERENCE, SOURCE_REFERENCE);

        assertStoredPDF("Archiv", "Zorro.pdf");
        verify(this.wiki).deleteDocument(previousArchiveDocument, this.context);
        verify(archivedDocument).setParentReference(archiveFolderReference.getLocalDocumentReference());
        verify(this.wiki).saveDocument(eq(archivedDocument), any(), eq(this.context));
        verifyNoInteractions(this.uniqueDocRefGenerator);
    }

    @Test
    void archiveFileRefusesAFileThatIsNotLinkedToTheSourceDocument() throws Exception
    {
        XWikiDocument otherFileDocument = mock(XWikiDocument.class);
        when(this.wiki.getDocument(FILE_REFERENCE, this.context)).thenReturn(otherFileDocument);
        mockBacklink(otherFileDocument, "Other.Page");

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> this.storageManager.archiveFile(FILE_REFERENCE, SOURCE_REFERENCE));

        assertThat(exception.getCause().getMessage(), containsString("is not linked to SOP document"));
        verify(this.wiki, never()).saveDocument(any(), anyString(), eq(this.context));
    }

    private Query mockLookupQuery() throws QueryException
    {
        Query query = mock(Query.class);
        Map<String, Object> boundValues = new HashMap<>();

        when(query.bindValue(anyString(), any())).thenAnswer(invocation -> {
            boundValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return query;
        });
        when(query.execute())
            .thenAnswer(invocation -> this.documentNamesByTitle.getOrDefault(boundValues.get("title"), List.of()));

        return query;
    }

    /**
     * Makes the lookup query find the File Manager document with the given title.
     */
    private void mockLookupResult(String title, DocumentReference reference)
    {
        String fullName = reference.getLastSpaceReference().getName() + '.' + reference.getName();
        this.documentNamesByTitle.computeIfAbsent(title, key -> new ArrayList<>()).add(fullName);
        when(this.documentReferenceResolver.resolve(fullName)).thenReturn(reference);
    }

    /**
     * Mocks an existing File Manager file that the lookup query finds by its title.
     */
    private XWikiDocument mockExistingFile(String title, DocumentReference reference,
        DocumentReference parentReference) throws XWikiException
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(this.wiki.getDocument(reference, this.context)).thenReturn(document);
        when(document.getParentReference()).thenReturn(parentReference);
        mockLookupResult(title, reference);

        return document;
    }

    private XWikiDocument mockFolder(DocumentReference folderReference, String title,
        DocumentReference parentReference) throws XWikiException
    {
        XWikiDocument folderDocument = mock(XWikiDocument.class);
        when(this.wiki.getDocument(folderReference, this.context)).thenReturn(folderDocument);
        when(folderDocument.getXObject(FOLDER_CLASS)).thenReturn(mock(BaseObject.class));
        when(folderDocument.getTitle()).thenReturn(title);
        when(folderDocument.getParentReference()).thenReturn(parentReference);

        return folderDocument;
    }

    private void mockSourceDocument(String fileManagerLocation) throws XWikiException
    {
        XWikiDocument sourceDocument = mock(XWikiDocument.class);
        BaseObject controlledDocumentObject = mock(BaseObject.class);

        when(this.wiki.getDocument(SOURCE_REFERENCE, this.context)).thenReturn(sourceDocument);
        when(sourceDocument.getXObject(CONTROLLED_DOCUMENT_CLASS)).thenReturn(controlledDocumentObject);
        when(controlledDocumentObject.getStringValue("fileManagerLocation")).thenReturn(fileManagerLocation);
    }

    private void mockSourceDocument(String fileManagerLocation, DocumentReference resolvedLocation)
        throws XWikiException
    {
        mockSourceDocument(fileManagerLocation);
        when(this.currentStringDocRefResolver.resolve(fileManagerLocation, SOURCE_REFERENCE))
            .thenReturn(resolvedLocation);
    }

    /**
     * Mocks the File Manager document that receives the PDF, as a new document without any object.
     */
    private void mockNewFileDocument() throws XWikiException
    {
        this.fileDocument = mock(XWikiDocument.class);
        this.fileTagObject = mock(BaseObject.class);
        this.fileDetailsObject = mock(BaseObject.class);

        when(this.wiki.getDocument(FILE_REFERENCE, this.context)).thenReturn(this.fileDocument);
        when(this.fileDocument.getDocumentReference()).thenReturn(FILE_REFERENCE);
        when(this.fileDocument.newXObject(FILE_CLASS, this.context)).thenReturn(mock(BaseObject.class));
        when(this.fileDocument.newXObject(ORIGINAL_DETAILS_CLASS, this.context)).thenReturn(this.fileDetailsObject);
        when(this.fileDocument.newXObject(TAG_CLASS, this.context)).thenReturn(this.fileTagObject);
    }

    /**
     * Mocks an existing File Manager document holding the PDF of the source document.
     */
    private XWikiDocument mockLinkedFileDocument(DocumentReference parentReference) throws XWikiException
    {
        XWikiDocument document = mock(XWikiDocument.class);
        this.fileTagObject = mock(BaseObject.class);

        when(this.wiki.getDocument(FILE_REFERENCE, this.context)).thenReturn(document);
        when(document.getDocumentReference()).thenReturn(FILE_REFERENCE);
        when(document.getTitle()).thenReturn("Zorro.pdf");
        when(document.getParentReference()).thenReturn(parentReference);
        when(document.getXObject(TAG_CLASS)).thenReturn(this.fileTagObject);
        mockBacklink(document, "Sandbox.Marmota.Zorro");

        return document;
    }

    private void mockBacklink(XWikiDocument fileDocument, String backlink)
    {
        BaseObject detailsObject = mock(BaseObject.class);
        when(fileDocument.getXObject(ORIGINAL_DETAILS_CLASS)).thenReturn(detailsObject);
        when(detailsObject.getStringValue("backlink")).thenReturn(backlink);
    }

    private XWikiAttachment mockAttachmentWithPDFContent() throws XWikiException
    {
        XWikiAttachment attachment = mock(XWikiAttachment.class);
        when(attachment.getContentInputStream(this.context))
            .thenReturn(new ByteArrayInputStream(PDF_CONTENT.getBytes(StandardCharsets.UTF_8)));

        return attachment;
    }

    private Path writeStoredPDF(String... pathSegments) throws Exception
    {
        Path storedPDF = this.permanentDirectory.resolve(Path.of("sops", pathSegments));
        Files.createDirectories(storedPDF.getParent());

        return Files.writeString(storedPDF, PDF_CONTENT);
    }

    private void assertStoredPDF(String... pathSegments) throws Exception
    {
        Path storedPDF = this.permanentDirectory.resolve(Path.of("sops", pathSegments));

        assertEquals(PDF_CONTENT, Files.readString(storedPDF, StandardCharsets.UTF_8));
    }
}
