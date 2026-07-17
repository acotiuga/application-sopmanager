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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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

    private XWikiDocument loadedDoc;

    private XWikiDocument sopDoc;

    private BaseObject sopObj;

    private DocumentReference documentReference;

    private DocumentReference currentUser;

    @BeforeEach
    void setUp() throws Exception
    {
        context = mock(XWikiContext.class);
        wiki = mock(XWiki.class);
        loadedDoc = mock(XWikiDocument.class);
        sopDoc = mock(XWikiDocument.class);
        sopObj = mock(BaseObject.class);
        documentReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(xcontextProvider.get()).thenReturn(context);
        when(context.getWiki()).thenReturn(wiki);
        when(context.getUserReference()).thenReturn(currentUser);

        when(wiki.getDocument(documentReference, context)).thenReturn(loadedDoc);
        when(loadedDoc.clone()).thenReturn(sopDoc);
        when(sopDoc.getXObject(SOP_CLASS)).thenReturn(sopObj);

        when(localizationManager.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(localizationManager.getTranslationPlain(anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WritableSecurityRule writableSecurityRule = mock(WritableSecurityRule.class);
        when(rightsWriter.createRule(any(), any(), eq(List.of(Right.EDIT)), eq(RuleState.ALLOW)))
            .thenReturn(writableSecurityRule);

        doNothing().when(rulesObjectWriter).persistRulesToObjects(any(), any(), any(), any());
    }

    @Test
    void updateDocumentReviewStateSubmitForReviewResolvesTrimmedReviewerGroups() throws Exception
    {
        DocumentReference reviewerGroup1 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup1");
        DocumentReference reviewerGroup2 =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup2");

        when(sopObj.getStringValue("status")).thenReturn("draft");
        when(sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn(" xwiki:XWiki.ReviewerGroup1, , xwiki:XWiki.ReviewerGroup2 ");

        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1"))
            .thenReturn(reviewerGroup1);
        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2"))
            .thenReturn(reviewerGroup2);

        String result = sopManager.updateDocumentReviewState("submitForReview", documentReference);

        assertEquals("sopManager.reviewPage.submitForReview.success", result);

        verify(currentStringDocRefResolver).resolve("xwiki:XWiki.ReviewerGroup1");
        verify(currentStringDocRefResolver).resolve("xwiki:XWiki.ReviewerGroup2");

        verify(sopObj).setLargeStringValue("reviewerUser", "");
        verify(sopObj).setLargeStringValue("approverUser", "");

        verify(rightsWriter).createRule(
            eq(List.of(reviewerGroup1, reviewerGroup2)),
            isNull(),
            eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW)
        );

        verify(workflowEventNotifier).notifySubmittedForReview(
            sopDoc,
            List.of(reviewerGroup1, reviewerGroup2)
        );

        verify(sopObj).setStringValue("status", "submittedForReview");
        verify(rulesObjectWriter).persistRulesToObjects(
            any(),
            eq(sopDoc),
            any(),
            eq(context)
        );
        verify(wiki).saveDocument(
            eq(sopDoc),
            eq("sopManager.reviewPage.submitForReview"),
            eq(context)
        );
    }

    @Test
    void updateDocumentReviewStateSubmitForApprovalStoresCurrentReviewer() throws Exception
    {
        DocumentReference approverGroup1 =
            new DocumentReference("xwiki", List.of("XWiki"), "ApproverGroup1");
        DocumentReference approverGroup2 =
            new DocumentReference("xwiki", List.of("XWiki"), "ApproverGroup2");

        when(sopObj.getStringValue("status")).thenReturn("submittedForReview");
        when(sopObj.getLargeStringValue("approverGroups"))
            .thenReturn("xwiki:XWiki.ApproverGroup1, xwiki:XWiki.ApproverGroup2");
        when(compactSerializer.serialize(currentUser)).thenReturn("XWiki.Admin");

        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverGroup1"))
            .thenReturn(approverGroup1);
        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverGroup2"))
            .thenReturn(approverGroup2);

        String result = sopManager.updateDocumentReviewState(
            "submitForApproval",
            documentReference
        );

        assertEquals("sopManager.reviewPage.submitForApproval.success", result);

        verify(sopObj).setLargeStringValue("reviewerUser", "XWiki.Admin");
        verify(sopObj).setLargeStringValue("approverUser", "");

        verify(rightsWriter).createRule(
            eq(List.of(approverGroup1, approverGroup2)),
            isNull(),
            eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW)
        );

        verify(workflowEventNotifier).notifySubmittedForApproval(
            sopDoc,
            List.of(approverGroup1, approverGroup2)
        );

        verify(sopObj).setStringValue("status", "submittedForApproval");
        verify(rulesObjectWriter).persistRulesToObjects(
            any(),
            eq(sopDoc),
            any(),
            eq(context)
        );
        verify(wiki).saveDocument(
            eq(sopDoc),
            eq("sopManager.reviewPage.submitForApproval"),
            eq(context)
        );
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
        DocumentReference pdfTemplate =
            new DocumentReference(
                "xwiki",
                List.of("SOPManager", "Code"),
                "GKHPDFTemplateVertical"
            );

        XWikiDocument revisionOwnerDoc = mock(XWikiDocument.class);

        when(wiki.getDocument(revisionOwner, context)).thenReturn(revisionOwnerDoc);
        when(revisionOwnerDoc.getXObject(USERS_CLASS)).thenReturn(null);

        when(sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(sopObj.getLargeStringValue("revisionOwner"))
            .thenReturn("xwiki:XWiki.RevisionOwner");
        when(sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn("xwiki:XWiki.ReviewerGroup1, xwiki:XWiki.ReviewerGroup2");
        when(sopObj.getLargeStringValue("pdfTemplate"))
            .thenReturn("xwiki:SOPManager.Code.GKHPDFTemplateVertical");

        when(currentStringDocRefResolver.resolve("xwiki:XWiki.RevisionOwner"))
            .thenReturn(revisionOwner);
        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup1"))
            .thenReturn(reviewerGroup1);
        when(currentStringDocRefResolver.resolve("xwiki:XWiki.ReviewerGroup2"))
            .thenReturn(reviewerGroup2);
        when(currentStringDocRefResolver.resolve(
            "xwiki:SOPManager.Code.GKHPDFTemplateVertical"
        )).thenReturn(pdfTemplate);

        when(compactSerializer.serialize(currentUser)).thenReturn("XWiki.Admin");
        when(serializer.serialize(revisionOwner))
            .thenReturn("xwiki:XWiki.RevisionOwner");

        Date tomorrow = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        when(sopObj.getDateValue("revisionPlannedDate")).thenReturn(tomorrow);

        String result = sopManager.updateDocumentReviewState(
            "approve",
            documentReference
        );

        assertEquals("sopManager.reviewPage.approve.success", result);

        InOrder inOrder = inOrder(sopObj, wiki, pdfExportManager);

        inOrder.verify(sopObj)
            .setLargeStringValue("approverUser", "XWiki.Admin");
        inOrder.verify(wiki)
            .saveDocument(
                sopDoc,
                "sopManager.reviewPage.approve",
                context
            );
        inOrder.verify(pdfExportManager)
            .exportAndAttachPDF(sopDoc, pdfTemplate);
        inOrder.verify(sopObj)
            .setStringValue("status", "approved");
        inOrder.verify(wiki)
            .saveDocument(
                sopDoc,
                "sopManager.reviewPage.approve",
                context
            );

        verify(rightsWriter).createRule(
            isNull(),
            eq(List.of(revisionOwner)),
            eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW)
        );

        verify(workflowEventNotifier).notifyApproved(
            sopDoc,
            revisionOwner,
            List.of(reviewerGroup1, reviewerGroup2)
        );

        verify(sopObj, never())
            .setIntValue(eq("revisionNumber"), anyInt());

        verify(rulesObjectWriter).persistRulesToObjects(
            any(),
            eq(sopDoc),
            any(),
            eq(context)
        );
    }

    @Test
    void updateDocumentReviewStateApproveFailsWhenPdfTemplateIsMissing()
        throws XWikiException
    {
        when(sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(sopObj.getLargeStringValue("revisionOwner"))
            .thenReturn("xwiki:XWiki.RevisionOwner");
        when(sopObj.getLargeStringValue("reviewerGroups"))
            .thenReturn("xwiki:XWiki.ReviewerGroup");
        when(sopObj.getLargeStringValue("pdfTemplate")).thenReturn("");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> sopManager.updateDocumentReviewState(
                "approve",
                documentReference
            )
        );

        assertEquals("sopManager.reviewPage.approve.error", exception.getMessage());

        verifyNoInteractions(pdfExportManager);
        verifyNoInteractions(workflowEventNotifier);
        verify(rulesObjectWriter, never())
            .persistRulesToObjects(any(), any(), any(), any());
        verify(wiki, never())
            .saveDocument(eq(sopDoc), anyString(), eq(context));
    }

    @Test
    void updateDocumentReviewStateStartNewRevisionIncrementsRevisionAndAssignsCurrentUser()
        throws Exception
    {
        when(sopObj.getStringValue("status")).thenReturn("approved");
        when(sopObj.getIntValue("revisionNumber")).thenReturn(1);
        when(compactSerializer.serialize(currentUser)).thenReturn("XWiki.Admin");

        String result = sopManager.updateDocumentReviewState(
            "startNewRevision",
            documentReference
        );

        assertEquals("sopManager.reviewPage.startNewRevision.success", result);

        verify(sopObj).setLargeStringValue("revisionOwner", "XWiki.Admin");
        verify(sopObj).setLargeStringValue("reviewerUser", "");
        verify(sopObj).setLargeStringValue("approverUser", "");
        verify(sopObj).setIntValue("revisionNumber", 2);

        verify(rightsWriter).createRule(
            isNull(),
            eq(List.of(currentUser)),
            eq(List.of(Right.EDIT)),
            eq(RuleState.ALLOW)
        );

        verify(sopObj).setStringValue("status", "draft");
        verify(rulesObjectWriter).persistRulesToObjects(
            any(),
            eq(sopDoc),
            any(),
            eq(context)
        );
        verify(wiki).saveDocument(
            eq(sopDoc),
            eq("sopManager.reviewPage.startNewRevision"),
            eq(context)
        );
    }

    @Test
    void updateDocumentReviewStateInvalidTransitionThrowsLocalizedError()
        throws XWikiException
    {
        when(sopObj.getStringValue("status")).thenReturn("draft");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> sopManager.updateDocumentReviewState(
                "approve",
                documentReference
            )
        );

        assertEquals(
            "sopManager.defaultSOPManager.error.invalidAction",
            exception.getMessage()
        );

        verifyNoInteractions(pdfExportManager);
        verifyNoInteractions(workflowEventNotifier);
        verify(rulesObjectWriter, never())
            .persistRulesToObjects(any(), any(), any(), any());
        verify(wiki, never())
            .saveDocument(eq(sopDoc), anyString(), eq(context));
    }

    @Test
    void updateDocumentReviewStateReturnsNotInWorkflowWhenControlledDocumentObjectIsMissing()
        throws XWikiException
    {
        when(sopDoc.getXObject(SOP_CLASS)).thenReturn(null);

        String result = sopManager.updateDocumentReviewState(
            "submitForReview",
            documentReference
        );

        assertEquals("sopManager.defaultSOPManager.error.notInWorkflow", result);

        assertEquals(1, logCapture.size());
        assertEquals(
            "Document [xwiki:Sandbox.WebHome] doesn't have the "
                + "[SOPManager.Code.ControlledDocumentClass] object, "
                + "cannot update review state",
            logCapture.getMessage(0)
        );

        verifyNoInteractions(pdfExportManager);
        verifyNoInteractions(workflowEventNotifier);
        verify(rulesObjectWriter, never())
            .persistRulesToObjects(any(), any(), any(), any());
        verify(wiki, never())
            .saveDocument(eq(sopDoc), anyString(), eq(context));
    }
}