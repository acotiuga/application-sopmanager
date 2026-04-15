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

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.FileManagerStorageManager;
import org.xwiki.contrib.sopmanager.PDFExportManager;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobRequestFactory;
import org.xwiki.export.pdf.job.PDFExportJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobExecutor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;

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
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private TemporaryResourceStore temporaryResourceStore;

    @Inject
    private PDFExportJobRequestFactory requestFactory;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private FileManagerStorageManager fileManagerStorageManager;

    @Override
    public String exportAndAttachPDF(DocumentReference documentReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWikiDocument previousDoc = context.getDoc();

        try {
            XWikiDocument attachmentDoc = context.getWiki().getDocument(documentReference, context);
            context.setDoc(attachmentDoc);

            String attachmentName = attachmentDoc.getTitle() + ".pdf";

            PDFExportJobRequest request = createExportRequest(documentReference, context);

            Job job = jobExecutor.execute("export/pdf", request);
            job.join();

            PDFExportJobStatus status = (PDFExportJobStatus) job.getStatus();
            TemporaryResourceReference pdfReference = status.getPDFFileReference();

            if (pdfReference == null) {
                throw new RuntimeException("The PDF export did not produce a temporary PDF file.");
            }

            File pdfFile = temporaryResourceStore.getTemporaryFile(pdfReference);
            if (pdfFile == null || !pdfFile.exists() || !pdfFile.isFile()) {
                throw new RuntimeException("The PDF export finished without creating the temporary PDF file at ["
                    + pdfFile + "].");
            }

            XWikiAttachment attachment = createAttachment(pdfFile, attachmentName, context);

            attachmentDoc.setAttachment(attachment);
            attachmentDoc.setAuthorReference(context.getUserReference());
            context.getWiki().saveDocument(attachmentDoc, "Attach generated PDF", context);

            fileManagerStorageManager.storeAttachment(documentReference, attachment, attachmentName);

            return String.format("The PDF was attached as [%s].", attachmentName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to attach generated PDF to the document [" + documentReference + "]", e);
        } finally {
            context.setDoc(previousDoc);
        }
    }

    private PDFExportJobRequest createExportRequest(DocumentReference documentReference, XWikiContext context)
        throws Exception
    {
        PDFExportJobRequest request = requestFactory.createRequest();
        request.setDocuments(List.of(documentReference));
        request.setTemplate(documentReferenceResolver.resolve("XWiki.PDFExport.Template"));

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
