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
package org.xwiki.contrib.sopmanager.importer.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Creates the SOP document of an imported document.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = SOPDocumentCreator.class)
@Singleton
public class SOPDocumentCreator
{
    /**
     * The PDF template of the documents in portrait orientation.
     */
    static final String PDF_TEMPLATE_VERTICAL = "SOPManager.Code.GKHPDFTemplateVertical";

    /**
     * The PDF template of the documents in landscape orientation.
     */
    static final String PDF_TEMPLATE_HORIZONTAL = "SOPManager.Code.GKHPDFTemplateHorizontal";

    private static final String WARNING_PREFIX = "sopManager.import.warning.";

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    /**
     * @param baseName the name of the imported file, without extension
     * @param wiki the wiki where to import the file
     * @return the SOP document of the imported file: a page of the SOP Manager space, named after the file
     */
    public DocumentReference getReference(String baseName, String wiki)
    {
        return new DocumentReference(wiki, List.of(SOPManager.SOP_MANAGER, baseName), "WebHome");
    }

    /**
     * Creates the SOP document of an imported document, with the status "imported", outside of the review
     * process.
     *
     * @param reference the SOP document to create
     * @param baseName the name of the imported file, without extension
     * @param docx the imported document
     * @param users the users selected for the document, by role ({@link SOPImportManager#OWNER},
     *     {@link SOPImportManager#APPROVER}, {@link SOPImportManager#REVIEWER}): the references of the users, possibly
     *     separated by commas as submitted by the user pickers; the first one is set as owner, all of them as approvers
     *     and reviewers
     * @param context the XWiki context
     * @return what was not imported as is
     * @throws XWikiException if the document can't be saved
     * @throws IOException if an image can't be attached
     */
    public List<ImportWarning> create(DocumentReference reference, String baseName, ImportedDocument docx,
        Map<String, List<String>> users, XWikiContext context) throws XWikiException, IOException
    {
        SOPDocumentDetails details = docx.details();
        XWikiDocument document = context.getWiki().getDocument(reference, context).clone();
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setTitle(getTitle(baseName, details));
        WikiPrinter printer = new DefaultWikiPrinter();
        this.renderer.render(docx.content(), printer);
        document.setContent(printer.toString());
        DocumentReference user = context.getUserReference();
        document.setCreatorReference(user);
        document.setAuthorReference(user);
        document.setContentAuthorReference(user);
        for (Map.Entry<String, byte[]> image : docx.images().entrySet()) {
            XWikiAttachment attachment =
                document.setAttachment(image.getKey(), new ByteArrayInputStream(image.getValue()), context);
            attachment.setAuthorReference(user);
        }

        BaseObject sop = document.newXObject(SOPManager.SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE, context);
        // Set the values through the class, which knows how they are stored (e.g. the page references).
        sop.set("status", SOPImportManager.IMPORTED, context);
        sop.set("isInReview", 0, context);
        sop.set("pdfTemplate", docx.landscape() ? PDF_TEMPLATE_HORIZONTAL : PDF_TEMPLATE_VERTICAL, context);
        sop.set("revisionId", StringUtils.defaultString(getRevisionId(baseName, details)), context);
        if (details.getRevisionNumber() != null) {
            sop.set("revisionNumber", details.getRevisionNumber(), context);
        }
        setDate(sop, "releaseDate", details.getReleaseDate(), context);
        setDate(sop, "revisionPlannedDate", details.getRevisionPlannedDate(), context);
        List<ImportWarning> warnings = new ArrayList<>(docx.warnings());
        setUsers(sop, "revisionOwner", users.get(SOPImportManager.OWNER), false, context);
        if (!setUsers(sop, "approverUser", users.get(SOPImportManager.APPROVER), true, context)
            && StringUtils.isNotBlank(details.getApprovedBy())) {
            warnings.add(new ImportWarning(WARNING_PREFIX + "approverNotSet", details.getApprovedBy()));
        }
        if (!setUsers(sop, "reviewerUser", users.get(SOPImportManager.REVIEWER), true, context)
            && StringUtils.isNotBlank(details.getRevisedBy())) {
            warnings.add(new ImportWarning(WARNING_PREFIX + "reviewerNotSet", details.getRevisedBy()));
        }

        context.getWiki().saveDocument(document,
            this.localizationManager.getTranslationPlain("sopManager.import.saveComment"), context);
        return warnings;
    }

    /**
     * Sets a user property to the selected users that exist: all of them, separated by commas, if the property holds
     * several users, the first one otherwise.
     *
     * @return whether a user was set
     */
    private boolean setUsers(BaseObject sop, String property, List<String> users, boolean multiple,
        XWikiContext context) throws XWikiException
    {
        List<String> existing = new ArrayList<>();
        for (String value : users == null ? List.<String>of() : users) {
            for (String user : StringUtils.split(StringUtils.defaultString(value), ',')) {
                DocumentReference reference = this.resolver.resolve(user.trim());
                String serialized = this.serializer.serialize(reference);
                if (StringUtils.isNotBlank(user) && !existing.contains(serialized)
                    && context.getWiki().exists(reference, context)) {
                    existing.add(serialized);
                }
            }
        }
        if (existing.isEmpty()) {
            return false;
        }
        sop.set(property, multiple ? String.join(",", existing) : existing.get(0), context);
        return true;
    }

    private static void setDate(BaseObject sop, String property, LocalDate date, XWikiContext context)
        throws XWikiException
    {
        if (date != null) {
            sop.set(property, Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()), context);
        }
    }

    /**
     * @return the code of the document: from the page header, else from the file name
     */
    static String getRevisionId(String baseName, SOPDocumentDetails details)
    {
        if (StringUtils.isNotEmpty(details.getRevisionId())) {
            return details.getRevisionId();
        }
        Matcher matcher = SOPDocumentDetails.CODE.matcher(baseName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * @return the title of the document: from the page header, else the file name without the code
     */
    static String getTitle(String baseName, SOPDocumentDetails details)
    {
        if (StringUtils.isNotBlank(details.getTitle())) {
            return details.getTitle();
        }
        Matcher matcher = SOPDocumentDetails.CODE.matcher(baseName);
        String title = matcher.find() ? StringUtils.strip(baseName.substring(matcher.end()), " ._-") : baseName;
        return title.isEmpty() ? baseName : title;
    }
}
