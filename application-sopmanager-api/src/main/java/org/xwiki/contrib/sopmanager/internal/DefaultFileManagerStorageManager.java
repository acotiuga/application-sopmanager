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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.FileManagerStorageManager;
import org.xwiki.filemanager.internal.reference.DocumentNameSequence;
import org.xwiki.filemanager.reference.UniqueDocumentReferenceGenerator;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link FileManagerStorageManager}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultFileManagerStorageManager implements FileManagerStorageManager
{
    private static final String FILE_MANAGER_SPACE = "FileManager";

    private static final LocalDocumentReference FILE_MANAGER_REFERENCE =
        new LocalDocumentReference(List.of(FILE_MANAGER_SPACE), "WebHome");

    private static final String FILE_MANAGER_CODE = "FileManagerCode";

    private static final LocalDocumentReference FOLDER_CLASS =
        new LocalDocumentReference(List.of(FILE_MANAGER_CODE), "FolderClass");

    private static final LocalDocumentReference FILE_CLASS =
        new LocalDocumentReference(List.of(FILE_MANAGER_CODE), "FileClass");

    private static final LocalDocumentReference TAG_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "TagClass");

    private static final LocalDocumentReference BACKLINK_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "OriginalDocumentBacklinkClass");

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private UniqueDocumentReferenceGenerator uniqueDocRefGenerator;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Override
    public void storeAttachment(DocumentReference sourceDocumentReference, XWikiAttachment attachment, String fileName)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xwiki = context.getWiki();

        try {
            String wikiName = sourceDocumentReference.getWikiReference().getName();
            List<String> folderNames = extractFolderNames(sourceDocumentReference);

            DocumentReference parentFolderReference = null;

            for (String folderName : folderNames) {
                parentFolderReference =
                    getOrCreateFileManagerFolder(folderName, parentFolderReference, wikiName, context);
            }

            DocumentReference fileReference = uniqueDocRefGenerator.generate(
                new SpaceReference(wikiName, FILE_MANAGER_SPACE),
                new DocumentNameSequence(fileName)
            );

            XWikiDocument fileDoc = xwiki.getDocument(fileReference, context);
            fileDoc.setTitle(fileName);

            if (fileDoc.getXObject(FILE_CLASS) == null) {
                fileDoc.newXObject(FILE_CLASS, context);
            }

            BaseObject tagObject = fileDoc.getXObject(TAG_CLASS);
            if (tagObject == null) {
                tagObject = fileDoc.newXObject(TAG_CLASS, context);
            }

            // Ensure backlink object exists and set backlink to the original source document.
            BaseObject backlinkObject = fileDoc.getXObject(BACKLINK_CLASS);
            if (backlinkObject == null) {
                backlinkObject = fileDoc.newXObject(BACKLINK_CLASS, context);
            }

            List<String> tags = new ArrayList<>();
            if (parentFolderReference != null) {
                tags.add(parentFolderReference.getName());
                fileDoc.setParentReference(
                    parentFolderReference.removeParent(parentFolderReference.getWikiReference()));
            } else {
                fileDoc.setParentReference(FILE_MANAGER_REFERENCE);
            }

            tagObject.setDBStringListValue("tags", tags);

            String serializedBacklink = localEntityReferenceSerializer.serialize(sourceDocumentReference);
            backlinkObject.setStringValue("backlink", serializedBacklink);

            fileDoc.setAttachment(attachment);
            fileDoc.setCreatorReference(context.getUserReference());
            fileDoc.setAuthorReference(context.getUserReference());
            xwiki.saveDocument(fileDoc, "Store generated PDF in File Manager", context);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to store generated PDF in File Manager for document [" + sourceDocumentReference + "]", e);
        }
    }

    DocumentReference getOrCreateFileManagerFolder(String folderName, DocumentReference parentFolderReference,
        String wikiName, XWikiContext context) throws Exception
    {
        XWiki xwiki = context.getWiki();

        DocumentReference existingReference = findExistingFolder(folderName, parentFolderReference, context);
        if (existingReference != null) {
            return existingReference;
        }

        DocumentReference folderReference = uniqueDocRefGenerator.generate(
            new SpaceReference(wikiName, FILE_MANAGER_SPACE),
            new DocumentNameSequence(folderName)
        );

        XWikiDocument folderDoc = xwiki.getDocument(folderReference, context);
        folderDoc.setTitle(folderReference.getName());

        if (parentFolderReference != null) {
            folderDoc.setParentReference(
                parentFolderReference.removeParent(parentFolderReference.getWikiReference()));
        } else {
            folderDoc.setParentReference(FILE_MANAGER_REFERENCE);
        }

        if (folderDoc.getXObject(FOLDER_CLASS) == null) {
            folderDoc.newXObject(FOLDER_CLASS, context);
        }

        folderDoc.setCreatorReference(context.getUserReference());
        folderDoc.setAuthorReference(context.getUserReference());
        xwiki.saveDocument(folderDoc, "Create File Manager folder", context);
        return folderReference;
    }

    DocumentReference findExistingFolder(String folderName, DocumentReference parentFolderReference,
        XWikiContext context) throws QueryException, XWikiException
    {
        String hql =
            "select distinct doc.fullName "
                + "from XWikiDocument doc, BaseObject obj "
                + "where doc.fullName = obj.name "
                + "and obj.className = :folderClass "
                + "and doc.space = :space "
                + "and doc.title = :title";

        Query query = queryManager.createQuery(hql, Query.HQL);
        query.bindValue("folderClass", localEntityReferenceSerializer.serialize(FOLDER_CLASS));
        query.bindValue("space", FILE_MANAGER_SPACE);
        query.bindValue("title", folderName);

        List<String> documentNames = query.execute();

        EntityReference expectedParent = parentFolderReference != null
            ? parentFolderReference.getLocalDocumentReference()
            : FILE_MANAGER_REFERENCE;

        for (String documentName : documentNames) {
            DocumentReference candidateReference = documentReferenceResolver.resolve(documentName);
            XWikiDocument candidateDoc = context.getWiki().getDocument(candidateReference, context);
            if (candidateDoc == null) {
                continue;
            }

            EntityReference actualParent = candidateDoc.getParentReference() != null
                ? candidateDoc.getParentReference().getLocalDocumentReference()
                : null;

            if (sameReference(actualParent, expectedParent)) {
                return candidateReference;
            }
        }

        return null;
    }

    List<String> extractFolderNames(DocumentReference sourceDocumentReference)
    {
        List<String> folderNames = new ArrayList<>();

        for (EntityReference reference : sourceDocumentReference.getReversedReferenceChain()) {
            if (reference.getType() == EntityType.SPACE) {
                folderNames.add(reference.getName());
            }
        }

        return folderNames;
    }

    private boolean sameReference(EntityReference left, EntityReference right)
    {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }
}
