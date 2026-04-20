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

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private PDFExportJobManager pdfExportJobManager;

    @Inject
    private PDFExportJobRequestFactory requestFactory;

    @Inject
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    @Inject
    private FileManagerStorageManager fileManagerStorageManager;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Override
    public String exportAndAttachPDF(DocumentReference documentReference, DocumentReference pdfTemplateReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWikiDocument previousDoc = context.getDoc();

        try {
            XWikiDocument attachmentDoc = context.getWiki().getDocument(documentReference, context);
            context.setDoc(attachmentDoc);

            PDFExportJobRequest request = createExportRequest(documentReference, context, pdfTemplateReference);

            File pdfFile = pdfExportJobManager.export(request);

            BaseObject controlledObj = attachmentDoc.getXObject(CONTROLLED_DOC_CLASS);
            String revisionId = controlledObj != null ? controlledObj.getStringValue("revisionId").trim() : "";
            String attachmentBaseName = attachmentDoc.getTitle() + ".pdf";

            String attachmentName = revisionId.isEmpty() ? attachmentBaseName : revisionId + "_" + attachmentBaseName;

            XWikiAttachment attachment = createAttachment(pdfFile, attachmentName, context);

            attachmentDoc.setAttachment(attachment);
            attachmentDoc.setAuthorReference(context.getUserReference());
            context.getWiki().saveDocument(attachmentDoc,
                localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.saveDocument"), context);

            fileManagerStorageManager.storeAttachment(documentReference, attachment, attachmentName);

            return localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.success",
                attachmentName);
        } catch (Exception e) {
            throw new RuntimeException(localizationManager.getTranslationPlain(
                "sopManager.defaultPDFExportManager.error.attachFailed", documentReference), e);
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
            defaultEntityReferenceSerializer.serialize(spaceReferences),
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

    private XWikiAttachment createAttachment(File pdfFile, String attachmentName, XWikiContext context) throws Exception
    {
        try (InputStream is = new FileInputStream(pdfFile)) {
            XWikiAttachment attachment = new XWikiAttachment();
            attachment.setFilename(attachmentName);
            attachment.setContent(is);
            attachment.setAuthorReference(context.getUserReference());
            return attachment;
        }
    }
}
