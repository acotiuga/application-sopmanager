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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPReminderManager;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of the {@link SOPReminderManager} role.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultSOPReminderManager implements SOPReminderManager
{
    private static final Set<Integer> APPROVAL_REMINDER_WEEKS = Set.of(1, 2);

    private static final Set<Integer> REVIEW_REMINDER_WEEKS = Set.of(3, 4, 5, 6, 7, 8);

    private static final LocalDocumentReference CONTROLLED_DOCUMENT_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    private static final LocalDocumentReference GROUPS_CLASS =
        new LocalDocumentReference(List.of("XWiki"), "XWikiGroups");

    private static final String STATUS = "status";

    private static final String APPROVER_GROUPS = "approverGroups";

    private static final String REVIEWER_GROUPS = "reviewerGroups";

    private static final String RELEASE_DATE = "releaseDate";

    private static final String SUBMITTED_FOR_REVIEW = "submittedForReview";

    private static final String SUBMITTED_FOR_APPROVAL = "submittedForApproval";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    @Named("document")
    private QueryFilter documentQueryFilter;

    @Inject
    @Named("unique")
    private QueryFilter uniqueQueryFilter;

    @Inject
    private SOPWorkflowEventNotifier workflowEventNotifier;

    @Inject
    private Logger logger;

    @Override
    public void sendDueReminders()
    {
        Map<String, Map<DocumentReference, List<DocumentReference>>> remindersToSend = getRemindersToSend();

        for (Map.Entry<String, Map<DocumentReference, List<DocumentReference>>> reminderEntry
            : remindersToSend.entrySet()) {
            sendReminderType(reminderEntry.getKey(), reminderEntry.getValue());
        }
    }

    Map<String, Map<DocumentReference, List<DocumentReference>>> getRemindersToSend()
    {
        Map<String, Map<DocumentReference, List<DocumentReference>>> remindersToSend = new HashMap<>();

        XWikiContext context = xcontextProvider.get();
        XWiki xwiki = context.getWiki();
        LocalDate today = getToday();

        try {
            for (DocumentReference documentReference : getSubmittedDocuments()) {
                XWikiDocument document = xwiki.getDocument(documentReference, context);
                BaseObject controlledObject = document.getXObject(CONTROLLED_DOCUMENT_CLASS);

                if (controlledObject != null) {
                    addDocumentReminder(remindersToSend, documentReference, controlledObject, today);
                }
            }
        } catch (QueryException e) {
            logger.error("Failed to query SOP documents due for reminders.", e);
            return Collections.emptyMap();
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }

        return remindersToSend;
    }

    LocalDate getToday()
    {
        return LocalDate.now(ZoneId.systemDefault());
    }

    List<DocumentReference> getSubmittedDocuments() throws QueryException
    {
        String xwql = "from doc.object(SOPManager.Code.ControlledDocumentClass) as sop "
            + "where sop.status in ('submittedForReview', 'submittedForApproval') "
            + "and sop.isInReview = 1";

        Query query = queryManager.createQuery(xwql, Query.XWQL);
        query.addFilter(documentQueryFilter).addFilter(uniqueQueryFilter);

        return query.execute();
    }

    private void addDocumentReminder(Map<String, Map<DocumentReference, List<DocumentReference>>> remindersToSend,
        DocumentReference documentReference, BaseObject controlledObject, LocalDate today)
    {
        String status = controlledObject.getStringValue(STATUS);
        Date releaseDate = controlledObject.getDateValue(RELEASE_DATE);
        int weeksBeforeDeadline = getWeeksBeforeDeadline(today, releaseDate);

        if (SUBMITTED_FOR_REVIEW.equals(status)) {
            addReminderIfDue(remindersToSend, documentReference, controlledObject, REVIEW_REMINDER_WEEKS,
                weeksBeforeDeadline, REVIEWER_GROUPS, status);
        } else if (SUBMITTED_FOR_APPROVAL.equals(status)) {
            addReminderIfDue(remindersToSend, documentReference, controlledObject, APPROVAL_REMINDER_WEEKS,
                weeksBeforeDeadline, APPROVER_GROUPS, status);
        }
    }

    private void addReminderIfDue(Map<String, Map<DocumentReference, List<DocumentReference>>> remindersToSend,
        DocumentReference documentReference, BaseObject controlledObject, Set<Integer> reminderWeeks,
        int weeksBeforeDeadline, String groupField, String reminderType)
    {
        if (!reminderWeeks.contains(weeksBeforeDeadline)) {
            return;
        }

        List<DocumentReference> users = resolveUsersFromGroups(controlledObject, groupField);
        if (users.isEmpty()) {
            logger.debug("No users found for SOP [{}] reminder on document [{}].", reminderType, documentReference);
            return;
        }

        remindersToSend
            .computeIfAbsent(reminderType, key -> new HashMap<>())
            .computeIfAbsent(documentReference, key -> new ArrayList<>())
            .addAll(users);
    }

    private List<DocumentReference> resolveUsersFromGroups(BaseObject controlledObject, String groupField)
    {
        List<DocumentReference> groupReferences = resolveDocumentReferences(controlledObject, groupField);
        List<DocumentReference> users = new ArrayList<>();

        XWikiContext context = xcontextProvider.get();
        XWiki xwiki = context.getWiki();

        for (DocumentReference groupReference : groupReferences) {
            try {
                users.addAll(getGroupMembers(xwiki.getDocument(groupReference, context)));
            } catch (XWikiException e) {
                logger.warn("Failed to read SOP reminder group [{}].", groupReference, e);
            }
        }

        return users.stream()
            .distinct()
            .toList();
    }

    private List<DocumentReference> getGroupMembers(XWikiDocument groupDocument)
    {
        List<BaseObject> groupObjects = groupDocument.getXObjects(GROUPS_CLASS);
        if (groupObjects == null || groupObjects.isEmpty()) {
            return List.of();
        }

        return groupObjects.stream()
            .filter(Objects::nonNull)
            .map(groupObject -> groupObject.getStringValue("member"))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .map(member -> currentStringDocRefResolver.resolve(
                member, groupDocument.getDocumentReference()))
            .distinct()
            .toList();
    }

    private List<DocumentReference> resolveDocumentReferences(BaseObject object, String fieldName)
    {
        return getSerializedReferences(object, fieldName).stream()
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .map(currentStringDocRefResolver::resolve)
            .distinct()
            .toList();
    }

    private List<String> getSerializedReferences(BaseObject object, String fieldName)
    {
        String serializedReferences = object.getLargeStringValue(fieldName);
        if (StringUtils.isBlank(serializedReferences)) {
            return List.of();
        }

        return Arrays.asList(serializedReferences.split(","));
    }

    private String getReminderType(Map<String, Integer> reminderWeeks, int weeksBeforeDeadline)
    {
        return reminderWeeks.entrySet().stream()
            .filter(entry -> entry.getValue() == weeksBeforeDeadline)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private int getWeeksBeforeDeadline(LocalDate today, Date releaseDate)
    {
        if (releaseDate == null) {
            return 0;
        }

        LocalDate releaseLocalDate = releaseDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();

        long daysBeforeDeadline = ChronoUnit.DAYS.between(today, releaseLocalDate);

        if (daysBeforeDeadline <= 0 || daysBeforeDeadline % 7 != 0) {
            return 0;
        }

        return (int) (daysBeforeDeadline / 7);
    }

    private void sendReminderType(String reminderStatus, Map<DocumentReference, List<DocumentReference>> documentUsers)
    {
        XWikiContext context = xcontextProvider.get();
        XWiki xwiki = context.getWiki();

        for (Map.Entry<DocumentReference, List<DocumentReference>> documentEntry : documentUsers.entrySet()) {
            DocumentReference documentReference = documentEntry.getKey();

            try {
                XWikiDocument document = xwiki.getDocument(documentReference, context);
                BaseObject controlledObject = document.getXObject(CONTROLLED_DOCUMENT_CLASS);
                sendDocumentReminder(reminderStatus, document, controlledObject, documentEntry.getValue());
            } catch (XWikiException e) {
                logger.warn("Failed to load SOP reminder document [{}].", documentReference, e);
            }
        }
    }

    private void sendDocumentReminder(String reminderStatus, XWikiDocument document,
        BaseObject controlledObject, List<DocumentReference> userReferences)
    {
        Date releaseDate = controlledObject.getDateValue(RELEASE_DATE);

        Map<String, Object> eventParams = new HashMap<>();

        if (releaseDate != null) {
            eventParams.put(RELEASE_DATE, releaseDate);
            eventParams.put("reminderWeeks", getWeeksBeforeDeadline(getToday(), releaseDate));
        }
        for (DocumentReference userReference : userReferences.stream().distinct().toList()) {
            try {
                notifyReminder(reminderStatus, document, userReference, eventParams);
                logger.debug("Sent SOP reminder [{}] for document [{}] to user [{}].", reminderStatus,
                    document.getDocumentReference(), userReference);
            } catch (Exception e) {
                logger.warn("Failed to send SOP reminder [{}] for document [{}] to user [{}].", reminderStatus,
                    document.getDocumentReference(), userReference, e);
            }
        }
    }

    private void notifyReminder(String reminderStatus, XWikiDocument document, DocumentReference userReference,
        Map<String, Object> eventParams)
    {
        if (SUBMITTED_FOR_REVIEW.equals(reminderStatus)) {
            workflowEventNotifier.notifyReviewReminder(document, userReference, eventParams);
        } else if (SUBMITTED_FOR_APPROVAL.equals(reminderStatus)) {
            workflowEventNotifier.notifyApproveReminder(document, userReference, eventParams);
        }
    }
}
