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

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.contrib.sopmanager.internal.event.ApprovedEvent;
import org.xwiki.contrib.sopmanager.internal.event.ReturnedForChangesEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForApprovalEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForReviewEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.ObservationManager;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Default implementation of {@link SOPWorkflowEventNotifier}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultSOPWorkflowEventNotifier implements SOPWorkflowEventNotifier
{
    private static final String EVENT_SOURCE = "org.xwiki.contrib:application-sopmanager-api";

    @Inject
    private ObservationManager observationManager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public void notifySubmittedForReview(XWikiDocument document, DocumentReference userReference)
    {
        Set<String> target = Set.of(serializer.serialize(userReference));
        observationManager.notify(new SubmittedForReviewEvent(target), EVENT_SOURCE, document);
    }

    @Override
    public void notifyReturnedForChanges(XWikiDocument document, DocumentReference userReference)
    {
        Set<String> target = Set.of(serializer.serialize(userReference));
        observationManager.notify(new ReturnedForChangesEvent(target), EVENT_SOURCE, document);
    }

    @Override
    public void notifySubmittedForApproval(XWikiDocument document, DocumentReference userReference)
    {
        Set<String> target = Set.of(serializer.serialize(userReference));
        observationManager.notify(new SubmittedForApprovalEvent(target), EVENT_SOURCE, document);
    }

    @Override
    public void notifyApproved(XWikiDocument document, DocumentReference revisionOwner,
        DocumentReference revisedBy)
    {
        Set<String> target = new HashSet<>();
        target.add(serializer.serialize(revisionOwner));
        target.add(serializer.serialize(revisedBy));
        observationManager.notify(new ApprovedEvent(target), EVENT_SOURCE, document);
    }
}
