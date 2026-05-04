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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rights.RightsWriter;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.contrib.sopmanager.PDFExportManager;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
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
    private static final String REVISION_OWNER = "revisionOwner";

    private static final String REVISION_NUMBER = "revisionNumber";

    private static final String STATUS = "status";

    private static final String APPROVER_GROUPS = "approverGroups";

    private static final String REVIEWER_GROUPS = "reviewerGroups";

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

    private static final String IS_IN_REVIEW = "isInReview";

    private static final String SEPARATOR = ",";

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private Logger logger;

    @Inject
    private SOPWorkflowEventNotifier workflowEventNotifier;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private RightsWriter rightsWriter;

    @Inject
    private PDFExportManager pdfExportManager;

    /**
     * Allows to set rules on page objects without saving them to the database yet.
     */
    @Inject
    @Named("recycling")
    private RulesObjectWriter rulesObjectWriter;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> compactSerializer;

    @Override
    public void addDocumentToReviewProcess(DocumentReference documentReference)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xWiki = context.getWiki();
        try {
            XWikiDocument sopDoc = xWiki.getDocument(documentReference, context);
            BaseObject sopObj = sopDoc.getXObject(SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE);
            if (sopObj == null) {
                return;
            }
            boolean isInReview = sopObj.getIntValue(IS_IN_REVIEW) == 1;
            if (!isInReview) {
                sopObj.setLargeStringValue(REVISION_OWNER, compactSerializer.serialize(context.getUserReference()));
                // Set today as the default revisionDate.
                sopObj.setDateValue("releaseDate", new Date());
                sopObj.setIntValue(REVISION_NUMBER, 1);
                sopObj.setStringValue(STATUS, DRAFT);
                sopObj.setIntValue(IS_IN_REVIEW, 1);
                List<ReadableSecurityRule> rules = new ArrayList<>();
                addUserEditRight(rules, context.getUserReference());

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
                    successMessage = handleSubmitForReview(context, sopDoc, sopObj, rules);
                    status = SUBMITTED_FOR_REVIEW;
                    break;
                case RETURN_FOR_CHANGES:
                    successMessage = handleReturnForChanges(sopDoc, sopObj, rules);
                    status = RETURNED_FOR_CHANGES;
                    break;
                case SUBMIT_FOR_APPROVAL:
                    successMessage = handleSubmitForApproval(context, sopDoc, sopObj, rules);
                    status = SUBMITTED_FOR_APPROVAL;
                    break;
                case APPROVE:
                    successMessage = handleApprove(context, sopDoc, sopObj, rules);
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

    private String handleSubmitForReview(XWikiContext context, XWikiDocument sopDoc, BaseObject sopObj,
        List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String reviewerGroupsString = sopObj.getLargeStringValue(REVIEWER_GROUPS);
        if (StringUtils.isBlank(reviewerGroupsString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.submitForReview.error"));
        }

        List<DocumentReference> reviewerGroupsRef =
            Arrays.stream(reviewerGroupsString.split(SEPARATOR))
                .filter(s -> !s.isBlank())
                .map(group -> currentStringDocRefResolver.resolve(group))
                .toList();

        addGroupsEditRight(rules, reviewerGroupsRef);

        workflowEventNotifier.notifySubmittedForReview(context, sopDoc, reviewerGroupsRef);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.submitForReview.success",
            reviewerGroupsString);
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
        addUserEditRight(rules, revisionOwner);

        workflowEventNotifier.notifyReturnedForChanges(sopDoc, revisionOwner);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.returnForChanges.success",
            getUserDisplayName(revisionOwner));
    }

    private String handleSubmitForApproval(XWikiContext context, XWikiDocument sopDoc, BaseObject sopObj,
        List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String approverGroupsString = sopObj.getLargeStringValue(APPROVER_GROUPS);
        if (StringUtils.isBlank(approverGroupsString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.submitForApproval.error"));
        }

        List<DocumentReference> approverGroupRefs =
            Arrays.stream(approverGroupsString.split(SEPARATOR))
                .filter(s -> !s.isBlank())
                .map(group -> currentStringDocRefResolver.resolve(group))
                .toList();

        addGroupsEditRight(rules, approverGroupRefs);

        workflowEventNotifier.notifySubmittedForApproval(context, sopDoc, approverGroupRefs);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.submitForApproval.success",
            approverGroupsString);
    }

    private String handleApprove(XWikiContext context, XWikiDocument sopDoc, BaseObject sopObj,
        List<ReadableSecurityRule> rules)
        throws XWikiException
    {
        String revisionOwnerString = sopObj.getLargeStringValue(REVISION_OWNER);
        String reviewerGroupsString = sopObj.getLargeStringValue(REVIEWER_GROUPS);
        if (StringUtils.isBlank(revisionOwnerString) && StringUtils.isBlank(reviewerGroupsString)) {
            throw new IllegalArgumentException(
                localizationManager.getTranslationPlain("sopManager.reviewPage.approve.error"));
        }

        DocumentReference pdfTemplateReference = currentStringDocRefResolver.resolve(sopObj.getLargeStringValue(
            "pdfTemplate"));
        pdfExportManager.exportAndAttachPDF(sopDoc.getDocumentReference(), pdfTemplateReference);

        sopObj.setIntValue(REVISION_NUMBER, sopObj.getIntValue(REVISION_NUMBER) + 1);

        DocumentReference revisionOwnerRef = currentStringDocRefResolver.resolve(revisionOwnerString);
        List<DocumentReference> reviewerGroupsRef =
            Arrays.stream(reviewerGroupsString.split(SEPARATOR))
                .filter(s -> !s.isBlank())
                .map(group -> currentStringDocRefResolver.resolve(group))
                .toList();

        addUserEditRight(rules, revisionOwnerRef);

        workflowEventNotifier.notifyApproved(context, sopDoc, revisionOwnerRef, reviewerGroupsRef);

        return localizationManager.getTranslationPlain("sopManager.reviewPage.approve.success",
            getUserDisplayName(revisionOwnerRef));
    }

    private String handleStartNewRevision(BaseObject sopObj, List<ReadableSecurityRule> rules)
    {
        DocumentReference revisionOwner = xcontextProvider.get().getUserReference();
        sopObj.setLargeStringValue(REVISION_OWNER, compactSerializer.serialize(revisionOwner));
        addUserEditRight(rules, revisionOwner);

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

    private void addUserEditRight(List<ReadableSecurityRule> rules, DocumentReference userReference)
    {
        rules.add(rightsWriter.createRule(null, List.of(userReference), List.of(Right.EDIT), RuleState.ALLOW));
    }

    private void addGroupsEditRight(List<ReadableSecurityRule> rules, List<DocumentReference> groupsReferences)
    {
        rules.add(rightsWriter.createRule(groupsReferences, null, List.of(Right.EDIT), RuleState.ALLOW));
    }
}
