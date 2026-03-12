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
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rights.RightsWriter;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.security.authorization.ReadableSecurityRule;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.RuleState;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of the {@link SOPManager} role.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultSOPManager implements SOPManager
{
    private static final String STATUS = "status";

    private static final String APPROVED_BY = "approvedBy";

    private static final String REVISED_BY = "revisedBy";

    private static final LocalDocumentReference RIGHTS_CLASS_REF =
        new LocalDocumentReference(List.of("XWiki"), "XWikiRights");

    private static final String DRAFT = "draft";

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private RightsWriter rightsWriter;

    /**
     * Allows to set rules on page objects without saving them to the database yet.
     */
    @Inject
    @Named("recycling")
    private RulesObjectWriter rulesObjectWriter;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public void addDocumentToReviewProcess(DocumentReference documentReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xWiki = context.getWiki();
        try {
            XWikiDocument sopDoc = xWiki.getDocument(documentReference, context);
            if (sopDoc.getXObjects(SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE).isEmpty()) {
                BaseObject sopObj = sopDoc.newXObject(SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE, context);
                // Add the current user as default reviewer.
                sopObj.setLargeStringValue(REVISED_BY, serializer.serialize(context.getUserReference()));
                sopObj.setLargeStringValue("revisionOwner", serializer.serialize(context.getUserReference()));
                // Set today as the default revisionDate.
                sopObj.setDateValue("releaseDate", new Date());
                sopObj.setStringValue(STATUS, DRAFT);
                xWiki.saveDocument(sopDoc, localizationManager.getTranslationPlain("sopManager.addPage.added"),
                    context);
            }
        } catch (XWikiException e) {
            logger.error(String.format("An error appeared when adding document [%s] to review process",
                documentReference), e);
        }
    }

    @Override
    public void updateDocumentReviewState(String action, DocumentReference documentReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xWiki = context.getWiki();
        try {
            XWikiDocument sopDoc = xWiki.getDocument(documentReference, context);
            BaseObject sopObj = sopDoc.getXObject(SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE);
            if (sopObj == null) {
                logger.warn(String.format("Document [%s] doesn't have the [%s] object, cannot update review state",
                    documentReference, SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE));
                return;
            }
            String revisedByString = sopObj.getLargeStringValue(REVISED_BY);
            DocumentReference revisedByUser = currentStringDocRefResolver.resolve(revisedByString);

            String approvedByString = sopObj.getLargeStringValue(APPROVED_BY);
            DocumentReference approvedByUser = currentStringDocRefResolver.resolve(approvedByString);
            String status = "";
            List<ReadableSecurityRule> rules = new ArrayList<>();
            switch (action) {
                case "submitForReview":
                    status = "submittedForReview";
                    rules.add(
                        rightsWriter.createRule(null, List.of(revisedByUser), List.of(Right.EDIT), RuleState.ALLOW));
                    // TODO: Send notifications!
                    break;
                case "returnForChanges":
                    status = DRAFT;
                    break;
                case "submitForApproval":
                    status = "submittedForApproval";
                    rules.add(
                        rightsWriter.createRule(null, List.of(approvedByUser), List.of(Right.EDIT), RuleState.ALLOW));
                    break;
                case "approve":
                    status = "approved";
                    // TODO: Generate the PDF file, attach it to the current page and in the File manager and store a
                    //  pair of XWiki version and the revision number in the object.
                    break;
                default:
                    logger.warn(String.format("Unknown action [%s] when updating document [%s] review state",
                        action, documentReference));
            }
            String comment = String.format("sopManager.reviewPage.%s", action);
            sopObj.setStringValue(STATUS, status);
            rulesObjectWriter.persistRulesToObjects(rules, sopDoc, RIGHTS_CLASS_REF, context);
            xWiki.saveDocument(sopDoc, localizationManager.getTranslationPlain(comment), context);
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }
}
