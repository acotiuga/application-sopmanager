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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.xwiki.contrib.rights.RightsWriter;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.contrib.rights.WritableSecurityRule;
import org.xwiki.contrib.sopmanager.PDFExportManager;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.RuleState;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Unit tests for {@link DefaultSOPManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultSOPManagerTest
{
    private static final LocalDocumentReference SOP_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    private static final LocalDocumentReference USERS_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "XWikiUsers");

    @InjectMockComponents
    private DefaultSOPManager sopManager;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @MockComponent
    private SOPWorkflowEventNotifier workflowEventNotifier;

    @MockComponent
    private PDFExportManager pdfExportManager;

    @MockComponent
    private RightsWriter rightsWriter;

    @MockComponent
    @Named("recycling")
    private RulesObjectWriter rulesObjectWriter;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument loadedDoc;

    private XWikiDocument sopDoc;

    private BaseObject sopObj;

    private DocumentReference documentReference;

    private DocumentReference currentUser;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.loadedDoc = mock(XWikiDocument.class);
        this.sopDoc = mock(XWikiDocument.class);
        this.sopObj = mock(BaseObject.class);
        this.documentReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        this.currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(this.currentUser);

        when(this.wiki.getDocument(this.documentReference, this.context)).thenReturn(this.loadedDoc);
        when(this.loadedDoc.clone()).thenReturn(this.sopDoc);
        when(this.sopDoc.getDocumentReference()).thenReturn(this.documentReference);
        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(this.sopObj);

        when(this.localizationManager.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.localizationManager.getTranslationPlain(anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WritableSecurityRule writableSecurityRule = mock(WritableSecurityRule.class);
        when(this.rightsWriter.createRule(any(), any(), eq(List.of(Right.EDIT)), eq(RuleState.ALLOW)))
            .thenReturn(writableSecurityRule);
    }

    @Test
    void addDocumentToReviewProcessStoresFullRevisionOwnerReference() throws Exception
    {
        when(this.sopObj.getIntValue("isInReview")).thenReturn(0);
        when(this.serializer.serialize(this.currentUser)).thenReturn("xwiki:XWiki.Admin");

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        verify(this.sopObj).setLargeStringValue("revisionOwner", "xwiki:XWiki.Admin");
        verify(this.sopObj).setDateValue(eq("releaseDate"), any(Date.class));
        verifyDefaultRevisionPlannedDate();
        verify(this.sopObj).setIntValue("revisionNumber", 1);
        verify(this.sopObj).setStringValue("status", "draft");
        verify(this.sopObj).setIntValue("isInReview", 1);
        verify(this.sopObj).setLargeStringValue("reviewerUser", "");
        verify(this.sopObj).setLargeStringValue("approverUser", "");

        verify(this.rightsWriter).createRule(isNull(), eq(List.of(this.currentUser)),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(),
            eq(this.context));
        verify(this.wiki).saveDocument(this.sopDoc, "sopManager.addPage.added", this.context);
    }

    @Test
    void updateDocumentReviewStateSubmitForReviewResolvesTrimmedReviewerGroups() throws Exception
    {
        DocumentReference reviewerGroup1 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup1");
        DocumentReference reviewerGroup2 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup2");

        when(this.sopObj.getStringValue("status")).thenReturn("draft");
        when(this.sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn(" xwiki:XWiki.ReviewerGroup1, , xwiki:XWiki.ReviewerGroup2 ");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1",
            this.documentReference)).thenReturn(reviewerGroup1);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2",
            this.documentReference)).thenReturn(reviewerGroup2);

        String result =
            this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference);

        assertEquals("sopManager.reviewPage.submitForReview.success", result);

        verify(this.sopObj).setLargeStringValue("reviewerUser", "");
        verify(this.sopObj).setLargeStringValue("approverUser", "");

        verify(this.rightsWriter).createRule(eq(List.of(reviewerGroup1, reviewerGroup2)), isNull(),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifySubmittedForReview(this.sopDoc,
            List.of(reviewerGroup1, reviewerGroup2));

        verify(this.sopObj).setStringValue("status", "submittedForReview");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(),
            eq(this.context));
        verify(this.wiki).saveDocument(this.sopDoc, "sopManager.reviewPage.submitForReview",
            this.context);
    }

    @Test
    void updateDocumentReviewStateSubmitForApprovalStoresCurrentReviewer() throws Exception
    {
        DocumentReference approverGroup1 =
            new DocumentReference("xwiki", List.of("XWiki"), "ApproverGroup1");
        DocumentReference approverGroup2 =
            new DocumentReference("xwiki", List.of("XWiki"), "ApproverGroup2");

        when(this.sopObj.getStringValue("status")).thenReturn("submittedForReview");
        when(this.sopObj.getLargeStringValue("approverGroups"))
            .thenReturn("xwiki:XWiki.ApproverGroup1, xwiki:XWiki.ApproverGroup2");
        when(this.serializer.serialize(this.currentUser)).thenReturn("xwiki:XWiki.Admin");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverGroup1",
            this.documentReference)).thenReturn(approverGroup1);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverGroup2",
            this.documentReference)).thenReturn(approverGroup2);

        String result =
            this.sopManager.updateDocumentReviewState("submitForApproval", this.documentReference);

        assertEquals("sopManager.reviewPage.submitForApproval.success", result);

        verify(this.sopObj).setLargeStringValue("reviewerUser", "xwiki:XWiki.Admin");
        verify(this.sopObj).setLargeStringValue("approverUser", "");

        verify(this.rightsWriter).createRule(eq(List.of(approverGroup1, approverGroup2)), isNull(),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifySubmittedForApproval(this.sopDoc,
            List.of(approverGroup1, approverGroup2));

        verify(this.sopObj).setStringValue("status", "submittedForApproval");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(),
            eq(this.context));
        verify(this.wiki).saveDocument(this.sopDoc, "sopManager.reviewPage.submitForApproval",
            this.context);
    }

    @Test
    void updateDocumentReviewStateApproveStoresApproverExportsPdfAndNotifies() throws Exception
    {
        DocumentReference revisionOwner =
            new DocumentReference("xwiki", List.of("XWiki"), "RevisionOwner");
        DocumentReference reviewerGroup1 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup1");
        DocumentReference reviewerGroup2 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup2");
        DocumentReference pdfTemplate = new DocumentReference("xwiki",
            List.of("SOPManager", "Code"), "GKHPDFTemplateVertical");

        XWikiDocument revisionOwnerDoc = mock(XWikiDocument.class);

        when(this.wiki.getDocument(revisionOwner, this.context)).thenReturn(revisionOwnerDoc);
        when(revisionOwnerDoc.getXObject(USERS_CLASS)).thenReturn(null);

        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner"))
            .thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn("xwiki:XWiki.ReviewerGroup1, xwiki:XWiki.ReviewerGroup2");
        when(this.sopObj.getLargeStringValue("pdfTemplate"))
            .thenReturn("xwiki:SOPManager.Code.GKHPDFTemplateVertical");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.RevisionOwner",
            this.documentReference)).thenReturn(revisionOwner);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1",
            this.documentReference)).thenReturn(reviewerGroup1);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2",
            this.documentReference)).thenReturn(reviewerGroup2);
        when(this.currentStringDocRefResolver.resolve(
            "xwiki:SOPManager.Code.GKHPDFTemplateVertical", this.documentReference)).thenReturn(pdfTemplate);

        when(this.serializer.serialize(this.currentUser)).thenReturn("xwiki:XWiki.Admin");
        when(this.serializer.serialize(revisionOwner)).thenReturn("xwiki:XWiki.RevisionOwner");

        Date tomorrow = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        when(this.sopObj.getDateValue("revisionPlannedDate")).thenReturn(tomorrow);

        String result =
            this.sopManager.updateDocumentReviewState("approve", this.documentReference);

        assertEquals("sopManager.reviewPage.approve.success", result);

        InOrder inOrder = inOrder(this.sopObj, this.wiki, this.pdfExportManager);

        inOrder.verify(this.sopObj)
            .setLargeStringValue("approverUser", "xwiki:XWiki.Admin");
        inOrder.verify(this.wiki)
            .saveDocument(this.sopDoc, "sopManager.reviewPage.approve", this.context);
        inOrder.verify(this.pdfExportManager)
            .exportAndAttachPDF(this.sopDoc, pdfTemplate);
        inOrder.verify(this.sopObj)
            .setStringValue("status", "approved");
        inOrder.verify(this.wiki)
            .saveDocument(this.sopDoc, "sopManager.reviewPage.approve", this.context);

        verify(this.rightsWriter).createRule(isNull(), eq(List.of(revisionOwner)),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifyApproved(this.sopDoc, revisionOwner,
            List.of(reviewerGroup1, reviewerGroup2));

        verify(this.sopObj, never()).setIntValue(eq("revisionNumber"), anyInt());
        verify(this.sopObj, never()).setIntValue("isInReview", 0);
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(),
            eq(this.context));
    }

    @Test
    void updateDocumentReviewStateApproveFailsWhenPdfTemplateIsMissing() throws XWikiException
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner"))
            .thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn("xwiki:XWiki.ReviewerGroup");
        when(this.sopObj.getLargeStringValue("pdfTemplate")).thenReturn("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("approve", this.documentReference));

        assertEquals("sopManager.reviewPage.approve.error", exception.getMessage());

        verify(this.sopObj, never()).setLargeStringValue(eq("approverUser"), anyString());
        verifyNoInteractions(this.pdfExportManager);
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.rulesObjectWriter, never()).persistRulesToObjects(any(), any(), any(), any());
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateStartNewRevisionIncrementsRevisionAndAssignsCurrentUser() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("approved");
        when(this.sopObj.getIntValue("revisionNumber")).thenReturn(1);
        when(this.serializer.serialize(this.currentUser)).thenReturn("xwiki:XWiki.Admin");

        String result =
            this.sopManager.updateDocumentReviewState("startNewRevision", this.documentReference);

        assertEquals("sopManager.reviewPage.startNewRevision.success", result);

        verify(this.sopObj).setLargeStringValue("revisionOwner", "xwiki:XWiki.Admin");
        verify(this.sopObj).setLargeStringValue("reviewerUser", "");
        verify(this.sopObj).setLargeStringValue("approverUser", "");
        verifyDefaultRevisionPlannedDate();
        verify(this.sopObj).setIntValue("revisionNumber", 2);

        verify(this.rightsWriter).createRule(isNull(), eq(List.of(this.currentUser)),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));

        verify(this.sopObj).setStringValue("status", "draft");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(),
            eq(this.context));
        verify(this.wiki).saveDocument(this.sopDoc,
            "sopManager.reviewPage.startNewRevision", this.context);
    }

    @Test
    void updateDocumentReviewStateInvalidTransitionThrowsLocalizedError() throws XWikiException
    {
        when(this.sopObj.getStringValue("status")).thenReturn("draft");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("approve", this.documentReference));

        assertEquals("sopManager.defaultSOPManager.error.invalidAction", exception.getMessage());

        verifyNoInteractions(this.pdfExportManager);
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.rulesObjectWriter, never()).persistRulesToObjects(any(), any(), any(), any());
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateReturnsNotInWorkflowWhenControlledDocumentObjectIsMissing()
        throws XWikiException
    {
        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(null);

        String result =
            this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference);

        assertEquals("sopManager.defaultSOPManager.error.notInWorkflow", result);

        assertEquals(1, this.logCapture.size());
        assertEquals("Document [xwiki:Sandbox.WebHome] doesn't have the "
                + "[SOPManager.Code.ControlledDocumentClass] object, "
                + "cannot update review state",
            this.logCapture.getMessage(0));

        verifyNoInteractions(this.pdfExportManager);
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.rulesObjectWriter, never()).persistRulesToObjects(any(), any(), any(), any());
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void addDocumentToReviewProcessKeepsTheRevisionOfAnImportedDocument() throws Exception
    {
        Date plannedDate = Date.from(LocalDate.now().plusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(this.sopObj.getIntValue("isInReview")).thenReturn(0);
        when(this.sopObj.getStringValue("status")).thenReturn("imported");
        when(this.sopObj.getDateValue("revisionPlannedDate")).thenReturn(plannedDate);
        when(this.sopObj.getIntValue("revisionNumber")).thenReturn(5);
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.KoerbAn");

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        verify(this.sopObj, never()).setLargeStringValue(eq("revisionOwner"), anyString());
        verify(this.sopObj, never()).setDateValue(eq("revisionPlannedDate"), any(Date.class));
        verify(this.sopObj, never()).setIntValue(eq("revisionNumber"), anyInt());
        verify(this.sopObj).setDateValue(eq("releaseDate"), any(Date.class));
        verify(this.sopObj).setStringValue("status", "draft");
        verify(this.sopObj).setIntValue("isInReview", 1);
        verify(this.wiki).saveDocument(this.sopDoc, "sopManager.addPage.added", this.context);
    }

    @Test
    void addDocumentToReviewProcessResetsThePastRevisionOfAnImportedDocument() throws Exception
    {
        Date plannedDate = Date.from(LocalDate.now().minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(this.sopObj.getIntValue("isInReview")).thenReturn(0);
        when(this.sopObj.getStringValue("status")).thenReturn("imported");
        when(this.sopObj.getDateValue("revisionPlannedDate")).thenReturn(plannedDate);
        when(this.sopObj.getIntValue("revisionNumber")).thenReturn(0);
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("");

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        verify(this.sopObj).setLargeStringValue(eq("revisionOwner"), any());

        verifyDefaultRevisionPlannedDate();
        verify(this.sopObj).setIntValue("revisionNumber", 1);
    }

    @Test
    void addDocumentToReviewProcessDoesNotResetRevisionPlannedDateWhenAlreadyInReview() throws Exception
    {
        when(this.sopObj.getIntValue("isInReview")).thenReturn(1);

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        verify(this.sopObj, never()).setDateValue(eq("revisionPlannedDate"), any(Date.class));
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void addDocumentToReviewProcessIgnoresDocumentsWithoutControlledDocumentObject() throws Exception
    {
        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(null);

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        verifyNoInteractions(this.rulesObjectWriter);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void addDocumentToReviewProcessLogsSaveFailure() throws Exception
    {
        when(this.sopObj.getIntValue("isInReview")).thenReturn(0);
        doThrow(new XWikiException()).when(this.wiki).saveDocument(this.sopDoc, "sopManager.addPage.added",
            this.context);

        this.sopManager.addDocumentToReviewProcess(this.documentReference);

        assertEquals("An error appeared when adding document [xwiki:Sandbox.WebHome] to review process",
            this.logCapture.getMessage(0));
    }

    @Test
    void updateDocumentReviewStateSubmitForReviewFailsWithoutReviewerGroups() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("returnedForChanges");
        when(this.sopObj.getLargeStringValue("reviewerGroups")).thenReturn(" ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference));

        assertEquals("sopManager.reviewPage.submitForReview.error", exception.getMessage());
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateSubmitForApprovalFailsWithoutApproverGroups() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForReview");
        when(this.sopObj.getLargeStringValue("approverGroups")).thenReturn("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("submitForApproval", this.documentReference));

        assertEquals("sopManager.reviewPage.submitForApproval.error", exception.getMessage());
        verify(this.sopObj, never()).setLargeStringValue(eq("reviewerUser"), anyString());
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateReturnForChangesGivesEditRightBackToRevisionOwner() throws Exception
    {
        DocumentReference revisionOwner = new DocumentReference("xwiki", List.of("XWiki"), "RevisionOwner");
        mockRevisionOwner(revisionOwner, "John", "Doe");
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.localizationManager.getTranslationPlain("sopManager.reviewPage.returnForChanges.success",
            "John Doe")).thenReturn("Returned to John Doe");

        String result = this.sopManager.updateDocumentReviewState("returnForChanges", this.documentReference);

        assertEquals("Returned to John Doe", result);
        verify(this.rightsWriter).createRule(isNull(), eq(List.of(revisionOwner)), eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifyReturnedForChanges(this.sopDoc, revisionOwner);
        verify(this.sopObj).setStringValue("status", "returnedForChanges");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(), eq(this.context));
        verify(this.wiki).saveDocument(this.sopDoc, "sopManager.reviewPage.returnForChanges", this.context);
    }

    @Test
    void updateDocumentReviewStateReturnForChangesFailsWithoutRevisionOwner() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForReview");
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("returnForChanges", this.documentReference));

        assertEquals("sopManager.reviewPage.returnForChanges.error", exception.getMessage());
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateApproveFromReviewStoresCurrentUserAsReviewerAndApprover() throws Exception
    {
        DocumentReference revisionOwner = new DocumentReference("xwiki", List.of("XWiki"), "RevisionOwner");
        DocumentReference reviewerGroup = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup");
        DocumentReference pdfTemplate =
            new DocumentReference("xwiki", List.of("SOPManager", "Code"), "GKHPDFTemplateVertical");
        // A user without first name is presented by its last name alone.
        mockRevisionOwner(revisionOwner, "", "Doe");

        when(this.sopObj.getStringValue("status")).thenReturn("submittedForReview");
        when(this.sopObj.getLargeStringValue("reviewerGroups")).thenReturn("xwiki:XWiki.ReviewerGroup");
        when(this.sopObj.getLargeStringValue("pdfTemplate")).thenReturn("SOPManager.Code.GKHPDFTemplateVertical");
        when(this.sopObj.getDateValue("revisionPlannedDate")).thenReturn(toDate(LocalDate.now().plusDays(1)));
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup", this.documentReference))
            .thenReturn(reviewerGroup);
        when(this.currentStringDocRefResolver.resolve("SOPManager.Code.GKHPDFTemplateVertical",
            this.documentReference)).thenReturn(pdfTemplate);
        when(this.serializer.serialize(this.currentUser)).thenReturn("xwiki:XWiki.Admin");
        when(this.localizationManager.getTranslationPlain("sopManager.reviewPage.approve.success", "Doe"))
            .thenReturn("Approved, Doe notified");

        String result = this.sopManager.updateDocumentReviewState("approve", this.documentReference);

        assertEquals("Approved, Doe notified", result);
        verify(this.sopObj).setLargeStringValue("reviewerUser", "xwiki:XWiki.Admin");
        verify(this.sopObj).setLargeStringValue("approverUser", "xwiki:XWiki.Admin");
        verify(this.pdfExportManager).exportAndAttachPDF(this.sopDoc, pdfTemplate);
        verify(this.workflowEventNotifier).notifyApproved(this.sopDoc, revisionOwner, List.of(reviewerGroup));
        verify(this.sopObj).setStringValue("status", "approved");
    }

    @Test
    void updateDocumentReviewStateApproveFailsWhenRevisionPlannedDateIsNotInTheFuture() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups")).thenReturn("xwiki:XWiki.ReviewerGroup");
        when(this.sopObj.getLargeStringValue("pdfTemplate")).thenReturn("SOPManager.Code.GKHPDFTemplateVertical");
        when(this.sopObj.getDateValue("revisionPlannedDate")).thenReturn(toDate(LocalDate.now()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("approve", this.documentReference));

        assertEquals("sopManager.defaultSOPManager.error.revisionPlannedDateMustBeFuture", exception.getMessage());
        verifyNoInteractions(this.pdfExportManager);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateApproveFailsWithoutRevisionPlannedDate() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups")).thenReturn("xwiki:XWiki.ReviewerGroup");
        when(this.sopObj.getLargeStringValue("pdfTemplate")).thenReturn("SOPManager.Code.GKHPDFTemplateVertical");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("approve", this.documentReference));

        assertEquals("sopManager.defaultSOPManager.error.revisionPlannedDateMustBeFuture", exception.getMessage());
        verifyNoInteractions(this.pdfExportManager);
    }

    @Test
    void updateDocumentReviewStateRejectsUnknownAction() throws Exception
    {
        when(this.sopObj.getStringValue("status")).thenReturn("draft");

        assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("publish", this.documentReference));

        verifyNoInteractions(this.rightsWriter, this.workflowEventNotifier, this.rulesObjectWriter);
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateWrapsDocumentLoadFailure() throws Exception
    {
        XWikiException loadException = new XWikiException();
        when(this.wiki.getDocument(this.documentReference, this.context)).thenThrow(loadException);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference));

        assertSame(loadException, exception.getCause());
    }

    private void mockRevisionOwner(DocumentReference revisionOwner, String firstName, String lastName)
        throws XWikiException
    {
        XWikiDocument revisionOwnerDoc = mock(XWikiDocument.class);
        BaseObject userObj = mock(BaseObject.class);

        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.RevisionOwner", this.documentReference))
            .thenReturn(revisionOwner);
        when(this.wiki.getDocument(revisionOwner, this.context)).thenReturn(revisionOwnerDoc);
        when(revisionOwnerDoc.getXObject(USERS_CLASS)).thenReturn(userObj);
        when(userObj.getStringValue("first_name")).thenReturn(firstName);
        when(userObj.getStringValue("last_name")).thenReturn(lastName);
    }

    private Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private void verifyDefaultRevisionPlannedDate()
    {
        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        verify(this.sopObj).setDateValue(eq("revisionPlannedDate"), dateCaptor.capture());

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate actualDate = dateCaptor.getValue().toInstant().atZone(zoneId).toLocalDate();

        assertEquals(LocalDate.now(zoneId).plusYears(3), actualDate);
    }
}