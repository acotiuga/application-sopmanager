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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Unit tests for {@link DefaultSOPReminderManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultSOPReminderManagerTest
{
    private static final LocalDocumentReference CONTROLLED_DOCUMENT_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    private static final LocalDocumentReference GROUPS_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "XWikiGroups");

    private static final String RELEASE_DATE = "releaseDate";

    private static final String REMINDER_WEEKS = "reminderWeeks";

    @InjectMockComponents
    private DefaultSOPReminderManager reminderManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @MockComponent
    @Named("document")
    private QueryFilter documentQueryFilter;

    @MockComponent
    @Named("unique")
    private QueryFilter uniqueQueryFilter;

    @MockComponent
    private SOPWorkflowEventNotifier workflowEventNotifier;

    @MockComponent
    private Logger logger;

    private XWikiContext context;

    private XWiki wiki;

    @BeforeEach
    void setUp()
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
    }

    @Test
    void sendDueRemindersSendsReviewReminderForDocumentDueInSevenWeeks() throws Exception
    {
        DefaultSOPReminderManager testedManager = spy(this.reminderManager);

        LocalDate today = LocalDate.of(2026, 5, 18);
        LocalDate releaseDate = today.plusWeeks(7);

        DocumentReference documentReference =
            new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocument");
        DocumentReference reviewerGroupReference =
            new DocumentReference("xwiki", List.of("XWiki"), "Reviewers");
        DocumentReference reviewerReference =
            new DocumentReference("xwiki", List.of("XWiki"), "ReviewerUser");

        doReturn(today).when(testedManager).getToday();
        doReturn(List.of(documentReference)).when(testedManager).getSubmittedDocuments();

        XWikiDocument reviewDocument = mockControlledDocument(documentReference, "submittedForReview", releaseDate,
            "reviewerGroups", "XWiki.Reviewers");
        mockGroup("XWiki.Reviewers", reviewerGroupReference, "XWiki.ReviewerUser", reviewerReference);

        testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocument, reviewerReference,
            getExpectedEventParams(releaseDate, 7));
    }

    @Test
    void sendDueRemindersSendsApprovalReminderForDocumentDueInOneWeek() throws Exception
    {
        DefaultSOPReminderManager testedManager = spy(this.reminderManager);

        LocalDate today = LocalDate.of(2026, 5, 18);
        LocalDate releaseDate = today.plusWeeks(1);

        DocumentReference documentReference =
            new DocumentReference("xwiki", List.of("SOPs"), "ApprovalDocument");
        DocumentReference approverGroupReference =
            new DocumentReference("xwiki", List.of("XWiki"), "Approvers");
        DocumentReference approverReference =
            new DocumentReference("xwiki", List.of("XWiki"), "ApproverUser");

        doReturn(today).when(testedManager).getToday();
        doReturn(List.of(documentReference)).when(testedManager).getSubmittedDocuments();

        XWikiDocument approvalDocument = mockControlledDocument(documentReference, "submittedForApproval", releaseDate,
            "approverGroups", "XWiki.Approvers");
        mockGroup("XWiki.Approvers", approverGroupReference, "XWiki.ApproverUser", approverReference);

        testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyApproveReminder(approvalDocument, approverReference,
            getExpectedEventParams(releaseDate, 1));
    }

    @Test
    void sendDueRemindersSendsOneNotificationPerDocumentForSameUser() throws Exception
    {
        DefaultSOPReminderManager testedManager = spy(this.reminderManager);

        LocalDate today = LocalDate.of(2026, 5, 18);
        LocalDate reviewReleaseDate = today.plusWeeks(7);
        LocalDate approvalReleaseDate = today.plusWeeks(1);

        DocumentReference reviewDocumentA =
            new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocumentA");
        DocumentReference reviewDocumentB =
            new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocumentB");
        DocumentReference approvalDocument =
            new DocumentReference("xwiki", List.of("SOPs"), "ApprovalDocument");

        DocumentReference reviewerGroupReference =
            new DocumentReference("xwiki", List.of("XWiki"), "Reviewers");
        DocumentReference approverGroupReference =
            new DocumentReference("xwiki", List.of("XWiki"), "Approvers");
        DocumentReference userReference =
            new DocumentReference("xwiki", List.of("XWiki"), "ReminderUser");

        doReturn(today).when(testedManager).getToday();
        doReturn(List.of(reviewDocumentA, reviewDocumentB, approvalDocument))
            .when(testedManager).getSubmittedDocuments();

        XWikiDocument reviewDocumentAObject = mockControlledDocument(reviewDocumentA, "submittedForReview",
            reviewReleaseDate, "reviewerGroups", "XWiki.Reviewers");
        XWikiDocument reviewDocumentBObject = mockControlledDocument(reviewDocumentB, "submittedForReview",
            reviewReleaseDate, "reviewerGroups", "XWiki.Reviewers");
        XWikiDocument approvalDocumentObject = mockControlledDocument(approvalDocument, "submittedForApproval",
            approvalReleaseDate, "approverGroups", "XWiki.Approvers");

        mockGroup("XWiki.Reviewers", reviewerGroupReference, "XWiki.ReminderUser", userReference);
        mockGroup("XWiki.Approvers", approverGroupReference, "XWiki.ReminderUser", userReference);

        testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocumentAObject, userReference,
            getExpectedEventParams(reviewReleaseDate, 7));
        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocumentBObject, userReference,
            getExpectedEventParams(reviewReleaseDate, 7));
        verify(this.workflowEventNotifier).notifyApproveReminder(approvalDocumentObject, userReference,
            getExpectedEventParams(approvalReleaseDate, 1));
    }

    private XWikiDocument mockControlledDocument(DocumentReference documentReference, String status,
        LocalDate releaseDate, String groupField, String groupValue) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        BaseObject controlledObject = mock(BaseObject.class);
        Date releaseDateValue = toDate(releaseDate);

        when(this.wiki.getDocument(documentReference, this.context)).thenReturn(document);
        when(document.getDocumentReference()).thenReturn(documentReference);
        when(document.getXObject(CONTROLLED_DOCUMENT_CLASS)).thenReturn(controlledObject);

        when(controlledObject.getStringValue("status")).thenReturn(status);
        when(controlledObject.getDateValue(RELEASE_DATE)).thenReturn(releaseDateValue);
        when(controlledObject.getLargeStringValue(groupField)).thenReturn(groupValue);

        return document;
    }

    private void mockGroup(String serializedGroupReference, DocumentReference groupReference,
        String serializedUserReference, DocumentReference userReference) throws Exception
    {
        XWikiDocument groupDocument = mock(XWikiDocument.class);
        BaseObject groupObject = mock(BaseObject.class);

        when(this.currentStringDocRefResolver.resolve(serializedGroupReference)).thenReturn(groupReference);
        when(this.currentStringDocRefResolver.resolve(serializedUserReference, groupReference))
            .thenReturn(userReference);

        when(this.wiki.getDocument(groupReference, this.context)).thenReturn(groupDocument);
        when(groupDocument.getDocumentReference()).thenReturn(groupReference);
        when(groupDocument.getXObjects(GROUPS_CLASS)).thenReturn(List.of(groupObject));
        when(groupObject.getStringValue("member")).thenReturn(serializedUserReference);
    }

    private Map<String, Object> getExpectedEventParams(LocalDate releaseDate, int reminderWeeks)
    {
        return Map.of(
            RELEASE_DATE, toDate(releaseDate),
            REMINDER_WEEKS, reminderWeeks
        );
    }

    private Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}