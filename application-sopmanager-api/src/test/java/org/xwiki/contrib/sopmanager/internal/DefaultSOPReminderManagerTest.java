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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
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

    private static final LocalDocumentReference USERS_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "XWikiUsers");

    private static final String SOP_ADMINISTRATORS_GROUP = "xwiki:XWiki.SOPAdministratorsGroup";

    private static final DocumentReference SOP_ADMINISTRATORS_GROUP_REFERENCE =
        new DocumentReference("xwiki", List.of("XWiki"), "SOPAdministratorsGroup");

    private static final DocumentReference REVIEWERS_GROUP_REFERENCE =
        new DocumentReference("xwiki", List.of("XWiki"), "Reviewers");

    private static final DocumentReference APPROVERS_GROUP_REFERENCE =
        new DocumentReference("xwiki", List.of("XWiki"), "Approvers");

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

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

    private DefaultSOPReminderManager testedManager;

    private XWikiContext context;

    private XWiki wiki;

    private Query submittedQuery;

    private Query approvedQuery;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.submittedQuery = mockQuery("sop.status in ('submittedForReview', 'submittedForApproval')");
        this.approvedQuery = mockQuery("sop.status = 'approved'");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);

        // The SOP administrators group is the fallback recipient; it is empty unless a test fills it.
        when(this.currentStringDocRefResolver.resolve(SOP_ADMINISTRATORS_GROUP))
            .thenReturn(SOP_ADMINISTRATORS_GROUP_REFERENCE);
        when(this.wiki.getDocument(SOP_ADMINISTRATORS_GROUP_REFERENCE, this.context))
            .thenReturn(mock(XWikiDocument.class));

        this.testedManager = spy(this.reminderManager);
        doReturn(TODAY).when(this.testedManager).getToday();
    }

    @Test
    void sendDueRemindersSendsReviewReminderForDocumentDueInSevenWeeks() throws Exception
    {
        LocalDate releaseDate = TODAY.plusWeeks(7);
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocument");
        DocumentReference reviewerReference = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerUser");

        when(this.submittedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        XWikiDocument reviewDocument = mockControlledDocument(documentReference, "submittedForReview", releaseDate,
            "reviewerGroups", "XWiki.Reviewers");
        mockGroup("XWiki.Reviewers", REVIEWERS_GROUP_REFERENCE, "XWiki.ReviewerUser", reviewerReference, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocument, reviewerReference,
            getExpectedEventParams(releaseDate, 7));
        verifyNoMoreInteractions(this.workflowEventNotifier);
        verify(this.submittedQuery).addFilter(this.documentQueryFilter);
        verify(this.submittedQuery).addFilter(this.uniqueQueryFilter);
    }

    @Test
    void sendDueRemindersSendsApprovalReminderForDocumentDueInOneWeek() throws Exception
    {
        LocalDate releaseDate = TODAY.plusWeeks(1);
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ApprovalDocument");
        DocumentReference approverReference = new DocumentReference("xwiki", List.of("XWiki"), "ApproverUser");

        when(this.submittedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        XWikiDocument approvalDocument = mockControlledDocument(documentReference, "submittedForApproval",
            releaseDate, "approverGroups", "XWiki.Approvers");
        mockGroup("XWiki.Approvers", APPROVERS_GROUP_REFERENCE, "XWiki.ApproverUser", approverReference, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyApproveReminder(approvalDocument, approverReference,
            getExpectedEventParams(releaseDate, 1));
        verifyNoMoreInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersSendsOneNotificationPerDocumentForSameUser() throws Exception
    {
        LocalDate reviewReleaseDate = TODAY.plusWeeks(7);
        LocalDate approvalReleaseDate = TODAY.plusWeeks(1);
        DocumentReference reviewDocumentA = new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocumentA");
        DocumentReference reviewDocumentB = new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocumentB");
        DocumentReference approvalDocument = new DocumentReference("xwiki", List.of("SOPs"), "ApprovalDocument");
        DocumentReference userReference = new DocumentReference("xwiki", List.of("XWiki"), "ReminderUser");

        when(this.submittedQuery.<DocumentReference>execute())
            .thenReturn(List.of(reviewDocumentA, reviewDocumentB, approvalDocument));
        XWikiDocument reviewDocumentAObject = mockControlledDocument(reviewDocumentA, "submittedForReview",
            reviewReleaseDate, "reviewerGroups", "XWiki.Reviewers");
        XWikiDocument reviewDocumentBObject = mockControlledDocument(reviewDocumentB, "submittedForReview",
            reviewReleaseDate, "reviewerGroups", "XWiki.Reviewers");
        XWikiDocument approvalDocumentObject = mockControlledDocument(approvalDocument, "submittedForApproval",
            approvalReleaseDate, "approverGroups", "XWiki.Approvers");
        mockGroup("XWiki.Reviewers", REVIEWERS_GROUP_REFERENCE, "XWiki.ReminderUser", userReference, true);
        mockGroup("XWiki.Approvers", APPROVERS_GROUP_REFERENCE, "XWiki.ReminderUser", userReference, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocumentAObject, userReference,
            getExpectedEventParams(reviewReleaseDate, 7));
        verify(this.workflowEventNotifier).notifyReviewReminder(reviewDocumentBObject, userReference,
            getExpectedEventParams(reviewReleaseDate, 7));
        verify(this.workflowEventNotifier).notifyApproveReminder(approvalDocumentObject, userReference,
            getExpectedEventParams(approvalReleaseDate, 1));
        verifyNoMoreInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersSkipsDocumentsOutsideTheirReminderWeeks() throws Exception
    {
        DocumentReference reviewTooEarly = new DocumentReference("xwiki", List.of("SOPs"), "ReviewTooEarly");
        DocumentReference reviewOffWeek = new DocumentReference("xwiki", List.of("SOPs"), "ReviewOffWeek");
        DocumentReference approvalTooEarly = new DocumentReference("xwiki", List.of("SOPs"), "ApprovalTooEarly");
        DocumentReference approvalOverdue = new DocumentReference("xwiki", List.of("SOPs"), "ApprovalOverdue");
        DocumentReference withoutReleaseDate = new DocumentReference("xwiki", List.of("SOPs"), "NoReleaseDate");
        DocumentReference notControlled = new DocumentReference("xwiki", List.of("SOPs"), "NotControlled");

        when(this.submittedQuery.<DocumentReference>execute()).thenReturn(List.of(reviewTooEarly, reviewOffWeek,
            approvalTooEarly, approvalOverdue, withoutReleaseDate, notControlled));
        mockControlledDocument(reviewTooEarly, "submittedForReview", TODAY.plusWeeks(9), "reviewerGroups",
            "XWiki.Reviewers");
        mockControlledDocument(reviewOffWeek, "submittedForReview", TODAY.plusWeeks(5).plusDays(3),
            "reviewerGroups", "XWiki.Reviewers");
        mockControlledDocument(approvalTooEarly, "submittedForApproval", TODAY.plusWeeks(3), "approverGroups",
            "XWiki.Approvers");
        mockControlledDocument(approvalOverdue, "submittedForApproval", TODAY.minusWeeks(1), "approverGroups",
            "XWiki.Approvers");
        mockControlledDocument(withoutReleaseDate, "submittedForApproval", null, "approverGroups",
            "XWiki.Approvers");
        when(this.wiki.getDocument(notControlled, this.context)).thenReturn(mock(XWikiDocument.class));

        this.testedManager.sendDueReminders();

        verifyNoInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersFallsBackToSOPAdministratorsWhenNoGroupMemberIsActive() throws Exception
    {
        LocalDate releaseDate = TODAY.plusWeeks(2);
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ApprovalDocument");
        DocumentReference inactiveApprover = new DocumentReference("xwiki", List.of("XWiki"), "InactiveUser");
        DocumentReference administrator = new DocumentReference("xwiki", List.of("XWiki"), "SOPAdmin");

        when(this.submittedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        XWikiDocument approvalDocument = mockControlledDocument(documentReference, "submittedForApproval",
            releaseDate, "approverGroups", "XWiki.Approvers");
        mockGroup("XWiki.Approvers", APPROVERS_GROUP_REFERENCE, "XWiki.InactiveUser", inactiveApprover, false);
        mockGroup(SOP_ADMINISTRATORS_GROUP, SOP_ADMINISTRATORS_GROUP_REFERENCE, "XWiki.SOPAdmin", administrator, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyApproveReminder(approvalDocument, administrator,
            getExpectedEventParams(releaseDate, 2));
        verifyNoMoreInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersSendsNothingWhenNeitherGroupMembersNorSOPAdministratorsExist() throws Exception
    {
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ReviewDocument");

        when(this.submittedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        mockControlledDocument(documentReference, "submittedForReview", TODAY.plusWeeks(3), "reviewerGroups", "");

        this.testedManager.sendDueReminders();

        verifyNoInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersSendsRevisionReminderToApproverOneMonthBeforePlannedRevision() throws Exception
    {
        Date revisionPlannedDate = toDate(TODAY.plusMonths(1));
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ApprovedDocument");
        DocumentReference approver = new DocumentReference("xwiki", List.of("XWiki"), "ApproverUser");

        when(this.approvedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        XWikiDocument approvedDocument = mockApprovedDocument(documentReference, revisionPlannedDate,
            " xwiki:XWiki.ApproverUser ");
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverUser")).thenReturn(approver);
        mockUser(approver, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyApproveReminder(approvedDocument, approver,
            Map.of("revisionReminder", true, "revisionPlannedDate", revisionPlannedDate));
        verifyNoMoreInteractions(this.workflowEventNotifier);
        verify(this.approvedQuery).addFilter(this.documentQueryFilter);
        verify(this.approvedQuery).addFilter(this.uniqueQueryFilter);
    }

    @Test
    void sendDueRemindersSendsRevisionReminderToSOPAdministratorsWhenApproverIsInactive() throws Exception
    {
        Date revisionPlannedDate = toDate(TODAY.plusMonths(1));
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("SOPs"), "ApprovedDocument");
        DocumentReference approver = new DocumentReference("xwiki", List.of("XWiki"), "ApproverUser");
        DocumentReference administrator = new DocumentReference("xwiki", List.of("XWiki"), "SOPAdmin");

        when(this.approvedQuery.<DocumentReference>execute()).thenReturn(List.of(documentReference));
        XWikiDocument approvedDocument = mockApprovedDocument(documentReference, revisionPlannedDate,
            "xwiki:XWiki.ApproverUser");
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.ApproverUser")).thenReturn(approver);
        mockUser(approver, false);
        mockGroup(SOP_ADMINISTRATORS_GROUP, SOP_ADMINISTRATORS_GROUP_REFERENCE, "XWiki.SOPAdmin", administrator, true);

        this.testedManager.sendDueReminders();

        verify(this.workflowEventNotifier).notifyApproveReminder(approvedDocument, administrator,
            Map.of("revisionReminder", true, "revisionPlannedDate", revisionPlannedDate));
        verifyNoMoreInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersSkipsRevisionReminderWhenNotExactlyOneMonthAhead() throws Exception
    {
        DocumentReference notDue = new DocumentReference("xwiki", List.of("SOPs"), "NotDue");
        DocumentReference withoutPlannedDate = new DocumentReference("xwiki", List.of("SOPs"), "NoPlannedDate");
        DocumentReference notControlled = new DocumentReference("xwiki", List.of("SOPs"), "NotControlled");

        when(this.approvedQuery.<DocumentReference>execute())
            .thenReturn(List.of(notDue, withoutPlannedDate, notControlled));
        mockApprovedDocument(notDue, toDate(TODAY.plusMonths(1).plusDays(1)), "xwiki:XWiki.ApproverUser");
        mockApprovedDocument(withoutPlannedDate, null, "xwiki:XWiki.ApproverUser");
        when(this.wiki.getDocument(notControlled, this.context)).thenReturn(mock(XWikiDocument.class));

        this.testedManager.sendDueReminders();

        verifyNoInteractions(this.workflowEventNotifier);
    }

    @Test
    void sendDueRemindersLogsQueryFailures() throws Exception
    {
        when(this.submittedQuery.execute()).thenThrow(new QueryException("Submitted query failed", null, null));
        when(this.approvedQuery.execute()).thenThrow(new QueryException("Approved query failed", null, null));

        this.testedManager.sendDueReminders();

        verifyNoInteractions(this.workflowEventNotifier);
        assertEquals("Failed to query SOP documents due for reminders.", this.logCapture.getMessage(0));
        assertEquals("Failed to send SOP revision reminders.", this.logCapture.getMessage(1));
    }

    private Query mockQuery(String statementPart) throws QueryException
    {
        Query query = mock(Query.class);
        when(this.queryManager.createQuery(argThat(statement -> statement != null
            && statement.contains(statementPart)), eq(Query.XWQL))).thenReturn(query);
        when(query.addFilter(any())).thenReturn(query);
        when(query.execute()).thenReturn(List.of());

        return query;
    }

    private XWikiDocument mockControlledDocument(DocumentReference documentReference, String status,
        LocalDate releaseDate, String groupField, String groupValue) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        BaseObject controlledObject = mock(BaseObject.class);

        when(this.wiki.getDocument(documentReference, this.context)).thenReturn(document);
        when(document.getDocumentReference()).thenReturn(documentReference);
        when(document.getXObject(CONTROLLED_DOCUMENT_CLASS)).thenReturn(controlledObject);

        when(controlledObject.getStringValue("status")).thenReturn(status);
        when(controlledObject.getDateValue("releaseDate")).thenReturn(releaseDate != null ? toDate(releaseDate) : null);
        when(controlledObject.getLargeStringValue(groupField)).thenReturn(groupValue);

        return document;
    }

    private XWikiDocument mockApprovedDocument(DocumentReference documentReference, Date revisionPlannedDate,
        String approverUser) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        BaseObject controlledObject = mock(BaseObject.class);

        when(this.wiki.getDocument(documentReference, this.context)).thenReturn(document);
        when(document.getXObject(CONTROLLED_DOCUMENT_CLASS)).thenReturn(controlledObject);
        when(controlledObject.getDateValue("revisionPlannedDate")).thenReturn(revisionPlannedDate);
        when(controlledObject.getLargeStringValue("approverUser")).thenReturn(approverUser);

        return document;
    }

    private void mockGroup(String serializedGroupReference, DocumentReference groupReference,
        String serializedUserReference, DocumentReference userReference, boolean active) throws Exception
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

        mockUser(userReference, active);
    }

    private void mockUser(DocumentReference userReference, boolean active) throws Exception
    {
        XWikiDocument userDocument = mock(XWikiDocument.class);
        BaseObject userObject = mock(BaseObject.class);

        when(this.wiki.getDocument(userReference, this.context)).thenReturn(userDocument);
        when(userDocument.getXObject(USERS_CLASS)).thenReturn(userObject);
        when(userObject.getIntValue("active", 1)).thenReturn(active ? 1 : 0);
    }

    private Map<String, Object> getExpectedEventParams(LocalDate releaseDate, int reminderWeeks)
    {
        return Map.of("releaseDate", toDate(releaseDate), "reminderWeeks", reminderWeeks);
    }

    private Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
