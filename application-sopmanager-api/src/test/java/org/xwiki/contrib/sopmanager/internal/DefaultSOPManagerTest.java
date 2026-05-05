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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
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
 * @since 1.0
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
    @Named("compact")
    private EntityReferenceSerializer<String> compactSerializer;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private static LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument sopDoc;

    private BaseObject sopObj;

    private DocumentReference documentReference;

    private DocumentReference currentUser;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.sopDoc = mock(XWikiDocument.class);
        this.sopObj = mock(BaseObject.class);
        this.documentReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        this.currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(this.currentUser);

        when(this.wiki.getDocument(this.documentReference, this.context)).thenReturn(this.sopDoc);
        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(this.sopObj);

        when(this.localizationManager.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.localizationManager.getTranslationPlain(anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WritableSecurityRule writableSecurityRule = mock(WritableSecurityRule.class);
        when(this.rightsWriter.createRule(any(), any(), eq(List.of(Right.EDIT)), eq(RuleState.ALLOW)))
            .thenReturn(writableSecurityRule);

        doNothing().when(this.rulesObjectWriter).persistRulesToObjects(any(), any(), any(), any());
    }

    @Test
    void updateDocumentReviewStateSubmitForReviewResolvesTrimmedReviewerGroups() throws Exception
    {
        DocumentReference reviewerGroup1 = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup1");
        DocumentReference reviewerGroup2 = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup2");

        when(this.sopObj.getStringValue("status")).thenReturn("draft");
        when(this.sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn(" xwiki:XWiki.ReviewerGroup1, , xwiki:XWiki.ReviewerGroup2 ");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1")).thenReturn(reviewerGroup1);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2")).thenReturn(reviewerGroup2);

        String result = this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference);

        assertEquals("sopManager.reviewPage.submitForReview.success", result);

        verify(this.currentStringDocRefResolver).resolve("xwiki:XWiki.ReviewerGroup1");
        verify(this.currentStringDocRefResolver).resolve("xwiki:XWiki.ReviewerGroup2");

        verify(this.rightsWriter).createRule(eq(List.of(reviewerGroup1, reviewerGroup2)), isNull(),
            eq(List.of(Right.EDIT)), eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifySubmittedForReview(this.sopDoc,
            List.of(reviewerGroup1, reviewerGroup2));

        verify(this.sopObj).setStringValue("status", "submittedForReview");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(), eq(this.context));
        verify(this.wiki).saveDocument(eq(this.sopDoc), eq("sopManager.reviewPage.submitForReview"), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateApproveExportsPdfNotifiesAndDoesNotIncrementRevision() throws Exception
    {
        DocumentReference revisionOwner = new DocumentReference("xwiki", List.of("XWiki"), "RevisionOwner");
        DocumentReference reviewerGroup1 = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup1");
        DocumentReference reviewerGroup2 = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup2");
        DocumentReference pdfTemplate = new DocumentReference("xwiki", List.of("SOPManager", "Code"),
            "GKHPDFTemplateVertical");

        XWikiDocument revisionOwnerDoc = mock(XWikiDocument.class);

        when(this.wiki.getDocument(revisionOwner, this.context)).thenReturn(revisionOwnerDoc);
        when(revisionOwnerDoc.getXObject(USERS_CLASS)).thenReturn(null);

        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn("xwiki:XWiki.ReviewerGroup1, xwiki:XWiki.ReviewerGroup2");
        when(this.sopObj.getLargeStringValue("pdfTemplate"))
            .thenReturn("xwiki:SOPManager.Code.GKHPDFTemplateVertical");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.RevisionOwner")).thenReturn(revisionOwner);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1")).thenReturn(reviewerGroup1);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2")).thenReturn(reviewerGroup2);
        when(this.currentStringDocRefResolver.resolve("xwiki:SOPManager.Code.GKHPDFTemplateVertical"))
            .thenReturn(pdfTemplate);

        when(this.serializer.serialize(revisionOwner)).thenReturn("xwiki:XWiki.RevisionOwner");

        String result = this.sopManager.updateDocumentReviewState("approve", this.documentReference);

        assertEquals("sopManager.reviewPage.approve.success", result);

        verify(this.pdfExportManager).exportAndAttachPDF(this.sopDoc, pdfTemplate);

        verify(this.rightsWriter).createRule(isNull(), eq(List.of(revisionOwner)), eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW));
        verify(this.workflowEventNotifier).notifyApproved(this.sopDoc, revisionOwner,
            List.of(reviewerGroup1, reviewerGroup2));

        verify(this.sopObj).setStringValue("status", "approved");
        verify(this.sopObj, never()).setIntValue(eq("revisionNumber"), anyInt());

        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(), eq(this.context));
        verify(this.wiki).saveDocument(eq(this.sopDoc), eq("sopManager.reviewPage.approve"), eq(this.context));
    }

    @Test
    void updateDocumentReviewStateApproveFailsWhenPdfTemplateIsMissing() throws XWikiException
    {
        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("reviewerGroups")).thenReturn("xwiki:XWiki.ReviewerGroup");
        when(this.sopObj.getLargeStringValue("pdfTemplate")).thenReturn("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> this.sopManager.updateDocumentReviewState("approve", this.documentReference));

        assertEquals("sopManager.reviewPage.approve.error", exception.getMessage());

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
        when(this.compactSerializer.serialize(this.currentUser)).thenReturn("XWiki.Admin");

        String result = this.sopManager.updateDocumentReviewState("startNewRevision", this.documentReference);

        assertEquals("sopManager.reviewPage.startNewRevision.success", result);

        verify(this.sopObj).setLargeStringValue("revisionOwner", "XWiki.Admin");
        verify(this.sopObj).setIntValue("revisionNumber", 2);
        verify(this.rightsWriter).createRule(isNull(), eq(List.of(this.currentUser)), eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW));

        verify(this.sopObj).setStringValue("status", "draft");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(), eq(this.context));
        verify(this.wiki).saveDocument(eq(this.sopDoc), eq("sopManager.reviewPage.startNewRevision"),
            eq(this.context));
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
    void updateDocumentReviewStateReturnsNotInWorkflowWhenControlledDocumentObjectIsMissing() throws XWikiException
    {
        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(null);

        String result = this.sopManager.updateDocumentReviewState("submitForReview", this.documentReference);

        assertEquals("sopManager.defaultSOPManager.error.notInWorkflow", result);

        assertEquals(1, logCapture.size());
        assertEquals("Document [xwiki:Sandbox.WebHome] doesn't have the "
                + "[SOPManager.Code.ControlledDocumentClass] object, cannot update review state",
            logCapture.getMessage(0));

        verifyNoInteractions(this.pdfExportManager);
        verifyNoInteractions(this.workflowEventNotifier);
        verify(this.rulesObjectWriter, never()).persistRulesToObjects(any(), any(), any(), any());
        verify(this.wiki, never()).saveDocument(eq(this.sopDoc), anyString(), eq(this.context));
    }
}