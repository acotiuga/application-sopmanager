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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.sopmanager.internal.event.ApprovedEvent;
import org.xwiki.contrib.sopmanager.internal.event.ReturnedForChangesEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForApprovalEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForReviewEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.observation.event.Event;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Unit tests for {@link DefaultSOPWorkflowEventNotifier}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
class DefaultSOPWorkflowEventNotifierTest
{
    private static final String EVENT_SOURCE = "org.xwiki.contrib:application-sopmanager-api";

    private static final LocalDocumentReference GROUPS_CLASS_REF =
        new LocalDocumentReference(List.of("XWiki"), "XWikiGroups");

    @InjectMockComponents
    private DefaultSOPWorkflowEventNotifier notifier;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument document;

    private XWikiDocument groupDocument;

    private DocumentReference groupReference;

    private DocumentReference reviewer;

    private DocumentReference owner;

    @BeforeEach
    void setUp()
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.document = mock(XWikiDocument.class);
        this.groupDocument = mock(XWikiDocument.class);

        this.groupReference = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup");
        this.reviewer = new DocumentReference("xwiki", List.of("XWiki"), "Reviewer");
        this.owner = new DocumentReference("xwiki", List.of("XWiki"), "Owner");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
    }

    @Test
    void notifySubmittedForReviewUsesTrimmedDirectGroupMembers() throws Exception
    {
        BaseObject reviewerObject = mock(BaseObject.class);
        BaseObject blankObject = mock(BaseObject.class);

        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);
        when(this.groupDocument.getXObjects(GROUPS_CLASS_REF)).thenReturn(Arrays.asList(reviewerObject, null,
            blankObject));

        when(reviewerObject.getStringValue("member")).thenReturn(" xwiki:XWiki.Reviewer ");
        when(blankObject.getStringValue("member")).thenReturn(" ");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.Reviewer")).thenReturn(this.reviewer);
        when(this.serializer.serialize(this.reviewer)).thenReturn("xwiki:XWiki.Reviewer");

        this.notifier.notifySubmittedForReview(this.document, List.of(this.groupReference));

        SubmittedForReviewEvent event = captureEvent(SubmittedForReviewEvent.class);

        assertEquals(Set.of("xwiki:XWiki.Reviewer"), event.getTarget());
        verify(this.currentStringDocRefResolver).resolve("xwiki:XWiki.Reviewer");
        verify(this.serializer).serialize(this.reviewer);
    }

    @Test
    void notifySubmittedForApprovalAllowsEmptyGroupDocuments() throws Exception
    {
        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);
        when(this.groupDocument.getXObjects(GROUPS_CLASS_REF)).thenReturn(null);

        this.notifier.notifySubmittedForApproval(this.document, List.of(this.groupReference));

        SubmittedForApprovalEvent event = captureEvent(SubmittedForApprovalEvent.class);

        assertEquals(Set.of(), event.getTarget());
    }

    @Test
    void notifyReturnedForChangesTargetsRevisionOwnerOnly()
    {
        when(this.serializer.serialize(this.owner)).thenReturn("xwiki:XWiki.Owner");

        this.notifier.notifyReturnedForChanges(this.document, this.owner);

        ReturnedForChangesEvent event = captureEvent(ReturnedForChangesEvent.class);

        assertEquals(Set.of("xwiki:XWiki.Owner"), event.getTarget());
        verify(this.serializer).serialize(this.owner);
    }

    @Test
    void notifyApprovedTargetsRevisionOwnerAndReviewerGroupMembers() throws Exception
    {
        BaseObject reviewerObject = mock(BaseObject.class);

        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);
        when(this.groupDocument.getXObjects(GROUPS_CLASS_REF)).thenReturn(List.of(reviewerObject));

        when(reviewerObject.getStringValue("member")).thenReturn("xwiki:XWiki.Reviewer");
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.Reviewer")).thenReturn(this.reviewer);

        when(this.serializer.serialize(this.reviewer)).thenReturn("xwiki:XWiki.Reviewer");
        when(this.serializer.serialize(this.owner)).thenReturn("xwiki:XWiki.Owner");

        this.notifier.notifyApproved(this.document, this.owner, List.of(this.groupReference));

        ApprovedEvent event = captureEvent(ApprovedEvent.class);

        assertEquals(Set.of("xwiki:XWiki.Owner", "xwiki:XWiki.Reviewer"), event.getTarget());
        verify(this.serializer).serialize(this.owner);
        verify(this.serializer).serialize(this.reviewer);
    }

    private <T extends Event> T captureEvent(Class<T> eventClass)
    {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(this.observationManager).notify(eventCaptor.capture(), eq(EVENT_SOURCE), eq(this.document));

        return assertInstanceOf(eventClass, eventCaptor.getValue());
    }
}