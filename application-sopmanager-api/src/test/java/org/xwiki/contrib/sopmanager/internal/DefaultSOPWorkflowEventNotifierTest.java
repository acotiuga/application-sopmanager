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

import java.util.List;
import java.util.Set;

import javax.inject.Named;

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
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    private XWikiDocument document;

    private DocumentReference reviewer;

    private DocumentReference approver;

    private DocumentReference owner;

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument groupDocument;

    private BaseObject groupObject;

    private DocumentReference groupReference;

    @BeforeEach
    void setUp()
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.document = mock(XWikiDocument.class);
        this.groupDocument = mock(XWikiDocument.class);
        this.groupObject = mock(BaseObject.class);
        this.reviewer = new DocumentReference("xwiki", List.of("XWiki"), "Reviewer");
        this.approver = new DocumentReference("xwiki", List.of("XWiki"), "Approver");
        this.owner = new DocumentReference("xwiki", List.of("XWiki"), "Owner");
        this.groupReference = new DocumentReference("xwiki", List.of("XWiki"), "ReviewerGroup");

        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.groupDocument.getXObjects(GROUPS_CLASS_REF)).thenReturn(List.of(this.groupObject));
        when(this.groupObject.getStringValue("member")).thenReturn("xwiki:XWiki.Reviewer");
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.Reviewer")).thenReturn(this.reviewer);

        when(this.serializer.serialize(this.reviewer)).thenReturn("xwiki:XWiki.Reviewer");
        when(this.serializer.serialize(this.approver)).thenReturn("xwiki:XWiki.Approver");
        when(this.serializer.serialize(this.owner)).thenReturn("xwiki:XWiki.Owner");
    }

    @Test
    void notifySubmittedForReview() throws Exception
    {
        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);

        this.notifier.notifySubmittedForReview(this.context, this.document, List.of(this.groupReference));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(this.observationManager).notify((Event) eventCaptor.capture(), eq(EVENT_SOURCE), eq(this.document));
        verify(this.serializer).serialize(this.reviewer);

        SubmittedForReviewEvent event = assertInstanceOf(SubmittedForReviewEvent.class, eventCaptor.getValue());
        assertEquals(Set.of("xwiki:XWiki.Reviewer"), event.getTarget());
    }

    @Test
    void notifyReturnedForChanges()
    {
        this.notifier.notifyReturnedForChanges(this.document, this.owner);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(this.observationManager).notify((Event) eventCaptor.capture(), eq(EVENT_SOURCE), eq(this.document));
        verify(this.serializer).serialize(this.owner);

        assertInstanceOf(ReturnedForChangesEvent.class, eventCaptor.getValue());
    }

    @Test
    void notifySubmittedForApproval() throws Exception
    {
        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);

        this.notifier.notifySubmittedForApproval(this.context, this.document, List.of(this.groupReference));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(this.observationManager).notify((Event) eventCaptor.capture(), eq(EVENT_SOURCE), eq(this.document));
        verify(this.serializer).serialize(this.reviewer);

        SubmittedForApprovalEvent event = assertInstanceOf(SubmittedForApprovalEvent.class, eventCaptor.getValue());
        assertEquals(Set.of("xwiki:XWiki.Reviewer"), event.getTarget());
    }

    @Test
    void notifyApproved() throws Exception
    {
        when(this.wiki.getDocument(this.groupReference, this.context)).thenReturn(this.groupDocument);

        this.notifier.notifyApproved(this.context, this.document, this.owner, List.of(this.groupReference));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(this.observationManager).notify((Event) eventCaptor.capture(), eq(EVENT_SOURCE), eq(this.document));
        verify(this.serializer).serialize(this.owner);
        verify(this.serializer).serialize(this.reviewer);

        ApprovedEvent event = assertInstanceOf(ApprovedEvent.class, eventCaptor.getValue());
        assertEquals(Set.of("xwiki:XWiki.Owner", "xwiki:XWiki.Reviewer"), event.getTarget());
    }
}