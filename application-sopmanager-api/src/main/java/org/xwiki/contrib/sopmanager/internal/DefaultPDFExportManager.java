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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.xpn.xwiki.objects.BaseObject;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.FileManagerStorageManager;
import org.xwiki.contrib.sopmanager.PDFExportJobManager;
import org.xwiki.contrib.sopmanager.PDFExportManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobRequestFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Default implementation of {@link PDFExportManager}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultPDFExportManager implements PDFExportManager
{
    private static final LocalDocumentReference CONTROLLED_DOC_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    private static final LocalDocumentReference TAG_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "TagClass");

    private static final String PDF_EXTENSION = ".pdf";

    private static final String UNDERLINE_SEPARATOR = "_";

    private static final String PDF_SECONDARY_TARGET_LOCATION = "pDFSecondaryTargetLocation";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private PDFExportJobManager pdfExportJobManager;

    @Inject
    private PDFExportJobRequestFactory requestFactory;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Inject
    private FileManagerStorageManager fileManagerStorageManager;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Override
    public String exportAndAttachPDF(XWikiDocument sopDoc, DocumentReference pdfTemplateReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWikiDocument previousDoc = context.getDoc();

        try {
            DocumentReference documentReference = sopDoc.getDocumentReference();
            context.setDoc(sopDoc);

            BaseObject sopObj = sopDoc.getXObject(CONTROLLED_DOC_CLASS);
            if (sopObj == null) {
                throw new IllegalStateException(String.format(
                    "Document [%s] is not a controlled SOP document.", documentReference));
            }

            PDFExportJobRequest request = createExportRequest(documentReference, context, pdfTemplateReference);

            File pdfFile = pdfExportJobManager.export(request);

            String revisionId = sopObj.getStringValue("revisionId").trim();
            int revisionNumber = sopObj.getIntValue("revisionNumber");

            String attachmentBaseName = sopDoc.getTitle() + UNDERLINE_SEPARATOR + revisionNumber + PDF_EXTENSION;
            String fileManagerAttachmentBaseName = sopDoc.getTitle() + PDF_EXTENSION;

            String attachmentName = revisionId.isEmpty()
                ? attachmentBaseName
                : revisionId + UNDERLINE_SEPARATOR + attachmentBaseName;
            String fileManagerAttachmentName = revisionId.isEmpty()
                ? fileManagerAttachmentBaseName
                : revisionId + UNDERLINE_SEPARATOR + fileManagerAttachmentBaseName;

            XWikiAttachment sourceAttachment = createAttachment(pdfFile, context);
            sourceAttachment.setFilename(attachmentName);

            XWikiAttachment fileManagerAttachment = sourceAttachment.clone();
            fileManagerAttachment.setFilename(fileManagerAttachmentName);

            BaseObject tags = sopDoc.getXObject(TAG_CLASS);

            DocumentReference fileManagerReference = fileManagerStorageManager.storeAttachment(documentReference,
                fileManagerAttachment, fileManagerAttachmentName, revisionNumber, tags);
            sopObj.set(PDF_SECONDARY_TARGET_LOCATION, localEntityReferenceSerializer.serialize(fileManagerReference),
                context);

            sopDoc.setAttachment(sourceAttachment);
            sopDoc.setAuthorReference(context.getUserReference());
            context.getWiki().saveDocument(sopDoc,
                localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.saveDocument"), context);

            return localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.success",
                attachmentName);
        } catch (Exception e) {
            throw new RuntimeException(localizationManager.getTranslationPlain(
                "sopManager.defaultPDFExportManager.error.attachFailed", sopDoc.getDocumentReference()), e);
        } finally {
            context.setDoc(previousDoc);
        }
    }

    private PDFExportJobRequest createExportRequest(DocumentReference documentReference, XWikiContext context,
        DocumentReference pdfTemplateReference) throws Exception
    {
        PDFExportJobRequest request = requestFactory.createRequest();
        request.setDocuments(List.of(documentReference));
        if (pdfTemplateReference != null) {
            request.setTemplate(pdfTemplateReference);
        }
        request.setWithCover(false);
        request.setWithToc(false);

        EntityReference spaceReferences =
            documentReference.getLastSpaceReference().removeParent(documentReference.getWikiReference());
        URL baseURL = context.getURLFactory().createExternalURL(
            localEntityReferenceSerializer.serialize(spaceReferences),
            documentReference.getName(),
            "view",
            "",
            "",
            documentReference.getWikiReference().getName(),
            context
        );
        request.setBaseURL(baseURL);
        request.setId("export", "pdf", documentReference.toString(), String.valueOf(System.currentTimeMillis()));
        return request;
    }

    XWikiAttachment createAttachment(File pdfFile, XWikiContext context) throws Exception
    {
        try (InputStream is = new FileInputStream(pdfFile)) {
            XWikiAttachment attachment = new XWikiAttachment();
            attachment.setContent(is);
            attachment.setAuthorReference(context.getUserReference());
            return attachment;
        }
    }
}
