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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rights.RightsWriter;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.contrib.sopmanager.internal.event.ApprovedEvent;
import org.xwiki.contrib.sopmanager.internal.event.ReturnedForChangesEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForApprovalEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForReviewEvent;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.ObservationManager;
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
    private static final String REVISION_OWNER = "revisionOwner";

    private static final String STATUS = "status";

    private static final String APPROVED_BY = "approvedBy";

    private static final String REVISED_BY = "revisedBy";

    private static final String XWIKI = "XWiki";

    private static final LocalDocumentReference RIGHTS_CLASS_REF =
        new LocalDocumentReference(List.of(XWIKI), "XWikiRights");

    private static final String DRAFT = "draft";

    private static final String SUBMIT_FOR_REVIEW = "submitForReview";

    private static final String RETURN_FOR_CHANGES = "returnForChanges";

    private static final String SUBMITTED_FOR_REVIEW = "submittedForReview";

    private static final String SUBMITTED_FOR_APPROVAL = "submittedForApproval";

    private static final String RETURNED_FOR_CHANGES = "returnedForChanges";

    private static final String APPROVED = "approved";

    private static final String SUBMIT_FOR_APPROVAL = "submitForApproval";

    private static final String APPROVE = "approve";

    private static final String START_NEW_REVISION = "startNewRevision";

    private static final String UNKNOWN_WORKFLOW_ACTION = "Unknown workflow action.";

    private static final String EVENT_SOURCE = "org.xwiki.contrib:application-sopmanager-api";

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private Logger logger;

    @Inject
    private ObservationManager observationManager;

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
                sopObj.setLargeStringValue(REVISION_OWNER, serializer.serialize(context.getUserReference()));
                // Set today as the default revisionDate.
                sopObj.setDateValue("releaseDate", new Date());
                sopObj.setStringValue(STATUS, DRAFT);

                List<ReadableSecurityRule> rules = new ArrayList<>();
                addEditRight(rules, context.getUserReference());

                rulesObjectWriter.persistRulesToObjects(rules, sopDoc, RIGHTS_CLASS_REF, context);
                xWiki.saveDocument(sopDoc, localizationManager.getTranslationPlain("sopManager.addPage.added"),
                    context);
            }
        } catch (XWikiException e) {
            logger.error(String.format("An error appeared when adding document [%s] to review process",
                documentReference), e);
        }
    }

    @Override
    public String updateDocumentReviewState(String action, DocumentReference documentReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xWiki = context.getWiki();
        try {
            XWikiDocument sopDoc = xWiki.getDocument(documentReference, context);
            BaseObject sopObj = getControlledDocumentObject(sopDoc, documentReference);
            if (sopObj == null) {
                return localizationManager.getTranslationPlain("sopManager.defaultSOPManager.error.notInWorkflow");
            }

            String currentStatus = sopObj.getStringValue(STATUS);
            validateTransition(action, currentStatus);

            String status;
            String successMessage;
            List<ReadableSecurityRule> rules = new ArrayList<>();

            switch (action) {
                case SUBMIT_FOR_REVIEW:
                    successMessage = handleSubmitForReview(sopDoc, sopObj, rules);
                    status = SUBMITTED_FOR_REVIEW;
                    break;
                case RETURN_FOR_CHANGES:
                    successMessage = handleReturnForChanges(sopDoc, sopObj, rules);
                    status = RETURNED_FOR_CHANGES;
                    break;
                case SUBMIT_FOR_APPROVAL:
                    successMessage = handleSubmitForApproval(sopDoc, sopObj, rules);
                    status = SUBMITTED_FOR_APPROVAL;
                    break;
                case APPROVE:
                    successMessage = handleApprove(sopDoc, sopObj, rules);
                    status = APPROVED;
                    break;
                case START_NEW_REVISION:
                    successMessage = handleStartNewRevision(sopObj, rules);
                    status = DRAFT;
                    break;
                default:
                    logger.warn(String.format("Unknown action [%s] when updating document [%s] review state",
                        action, documentReference));
                    throw new IllegalArgumentException(UNKNOWN_WORKFLOW_ACTION);
            }

            saveReviewState(sopDoc, sopObj, status, rules, action, context, xWiki);

            return successMessage;
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateTransition(String action, String currentStatus)
    {
        switch (action) {
            case SUBMIT_FOR_REVIEW:
                requireStatus(action, currentStatus, DRAFT, RETURNED_FOR_CHANGES);
                break;
            case RETURN_FOR_CHANGES:
                requireStatus(action, currentStatus, SUBMITTED_FOR_REVIEW, SUBMITTED_FOR_APPROVAL, APPROVED);
                break;
            case SUBMIT_FOR_APPROVAL:
                requireStatus(action, currentStatus, SUBMITTED_FOR_REVIEW);
                break;
            case APPROVE:
                requireStatus(action, currentStatus, SUBMITTED_FOR_APPROVAL);
                break;
            case START_NEW_REVISION:
                requireStatus(action, currentStatus, APPROVED);
                break;
            default:
                throw new IllegalArgumentException(UNKNOWN_WORKFLOW_ACTION);
        }
    }

    private void requireStatus(String action, String currentStatus, String... allowedStatuses)
    {
        for (String allowedStatus : allowedStatuses) {
            if (allowedStatus.equals(currentStatus)) {
                return;
            }
        }

        throw new IllegalArgumentException(localizationManager.getTranslationPlain(
            "sopManager.defaultSOPManager.error.invalidAction", action, currentStatus));
    }

    private BaseObject getControlledDocumentObject(XWikiDocument sopDoc, DocumentReference documentReference)
    {
        BaseObject sopObj = sopDoc.getXObject(SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE);
        if (sopObj == null) {
            logger.warn(String.format("Document [%s] doesn't have the [%s] object, cannot update review state",
                documentReference, SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE));
        }
        return sopObj;
    }

    private String handleSubmitForReview(XWikiDocument sopDoc, BaseObject sopObj, List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String revisedByString = sopObj.getLargeStringValue(REVISED_BY);
        if (StringUtils.isBlank(revisedByString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.submitForReview.error"));
        }

        DocumentReference revisedByUser = currentStringDocRefResolver.resolve(revisedByString);
        addEditRight(rules, revisedByUser);

        Set<String> target = new HashSet<>();
        target.add(serializer.serialize(revisedByUser));
        observationManager.notify(new SubmittedForReviewEvent(target), EVENT_SOURCE, sopDoc);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.submitForReview.success",
            getUserDisplayName(revisedByUser));
    }

    private String handleReturnForChanges(XWikiDocument sopDoc, BaseObject sopObj, List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String revisionOwnerString = sopObj.getLargeStringValue(REVISION_OWNER);
        if (StringUtils.isBlank(revisionOwnerString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.returnForChanges.error"));
        }

        DocumentReference revisionOwner = currentStringDocRefResolver.resolve(revisionOwnerString);
        addEditRight(rules, revisionOwner);

        Set<String> target = new HashSet<>();
        target.add(serializer.serialize(revisionOwner));
        observationManager.notify(new ReturnedForChangesEvent(target), EVENT_SOURCE, sopDoc);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.returnForChanges.success",
            getUserDisplayName(revisionOwner));
    }

    private String handleSubmitForApproval(XWikiDocument sopDoc, BaseObject sopObj, List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String approvedByString = sopObj.getLargeStringValue(APPROVED_BY);
        if (StringUtils.isBlank(approvedByString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.submitForApproval.error"));
        }

        DocumentReference approvedByUser = currentStringDocRefResolver.resolve(approvedByString);
        addEditRight(rules, approvedByUser);

        Set<String> target = new HashSet<>();
        target.add(serializer.serialize(approvedByUser));
        observationManager.notify(new SubmittedForApprovalEvent(target), EVENT_SOURCE, sopDoc);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.submitForApproval.success",
            getUserDisplayName(approvedByUser));
    }

    private String handleApprove(XWikiDocument sopDoc, BaseObject sopObj, List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String revisionOwnerString = sopObj.getLargeStringValue(REVISION_OWNER);
        String revisedByString = sopObj.getLargeStringValue(REVISED_BY);
        if (StringUtils.isBlank(revisionOwnerString) && StringUtils.isBlank(revisedByString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.approve.error"));
        }

        DocumentReference revisionOwner = currentStringDocRefResolver.resolve(revisionOwnerString);
        DocumentReference revisedBy = currentStringDocRefResolver.resolve(revisedByString);

        addEditRight(rules, revisionOwner);

        Set<String> target = new HashSet<>();
        target.add(serializer.serialize(revisionOwner));
        target.add(serializer.serialize(revisedBy));
        observationManager.notify(new ApprovedEvent(target), EVENT_SOURCE, sopDoc);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.approve.success",
            getUserDisplayName(revisionOwner), getUserDisplayName(revisedBy));
    }

    private String handleStartNewRevision(BaseObject sopObj, List<ReadableSecurityRule> rules)
    {
        DocumentReference revisionOwner = xcontextProvider.get().getUserReference();
        sopObj.setLargeStringValue(REVISION_OWNER, serializer.serialize(revisionOwner));
        addEditRight(rules, revisionOwner);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.startNewRevision.success");
    }

    private void saveReviewState(XWikiDocument sopDoc, BaseObject sopObj, String status,
        List<ReadableSecurityRule> rules, String action, XWikiContext context, XWiki xWiki) throws XWikiException
    {
        String comment = String.format("sopManager.reviewPage.%s", action);
        sopObj.setStringValue(STATUS, status);
        rulesObjectWriter.persistRulesToObjects(rules, sopDoc, RIGHTS_CLASS_REF, context);
        xWiki.saveDocument(sopDoc, localizationManager.getTranslationPlain(comment), context);
    }

    private String getUserDisplayName(DocumentReference userReference) throws XWikiException
    {
        XWikiContext context = xcontextProvider.get();
        XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        BaseObject userObj = userDoc.getXObject(new LocalDocumentReference(XWIKI, "XWikiUsers"));

        if (userObj == null) {
            return serializer.serialize(userReference);
        }

        String firstName = userObj.getStringValue("first_name");
        String lastName = userObj.getStringValue("last_name");
        String fullName = (StringUtils.defaultString(firstName) + " " + StringUtils.defaultString(lastName)).trim();

        return fullName.isEmpty() ? serializer.serialize(userReference) : fullName;
    }

    private void addEditRight(List<ReadableSecurityRule> rules, DocumentReference userReference)
    {
        rules.add(rightsWriter.createRule(null, List.of(userReference), List.of(Right.EDIT), RuleState.ALLOW));
    }
}
