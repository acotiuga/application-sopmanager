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
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPWorkflowEventNotifier;
import org.xwiki.contrib.sopmanager.internal.event.ApprovedEvent;
import org.xwiki.contrib.sopmanager.internal.event.ReturnedForChangesEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForApprovalEvent;
import org.xwiki.contrib.sopmanager.internal.event.SubmittedForReviewEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.ObservationManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

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

    private static final LocalDocumentReference GROUPS_CLASS_REF =
        new LocalDocumentReference(List.of("XWiki"), "XWikiGroups");

    @Inject
    private ObservationManager observationManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public void notifySubmittedForReview(XWikiDocument document, List<DocumentReference> groupReferences)
    {
        Set<String> target = getGroupMembers(groupReferences, xcontextProvider.get());

        observationManager.notify(new SubmittedForReviewEvent(target), EVENT_SOURCE, document);
    }

    @Override
    public void notifyReturnedForChanges(XWikiDocument document, DocumentReference userReference)
    {
        Set<String> target = Set.of(serializer.serialize(userReference));
        observationManager.notify(new ReturnedForChangesEvent(target), EVENT_SOURCE, document);
    }

    @Override
    public void notifySubmittedForApproval(XWikiDocument document, List<DocumentReference> groupReferences)
    {
        Set<String> target = getGroupMembers(groupReferences, xcontextProvider.get());

        observationManager.notify(new SubmittedForApprovalEvent(target), EVENT_SOURCE, document);
    }

    private Set<String> getGroupMembers(List<DocumentReference> groupReferences, XWikiContext context)
    {
        XWiki xwiki = context.getWiki();
        Set<String> members = new HashSet<>();

        for (DocumentReference groupRef : groupReferences) {
            try {
                XWikiDocument groupDoc = xwiki.getDocument(groupRef, context);

                // Get all XWiki.XWikiGroup objects.
                List<BaseObject> groupObjects = groupDoc.getXObjects(GROUPS_CLASS_REF);
                if (groupObjects == null || groupObjects.isEmpty()) {
                    continue;
                }

                for (BaseObject groupObj : groupObjects) {
                    if (groupObj == null) {
                        continue;
                    }
                    String member = StringUtils.trimToNull(groupObj.getStringValue("member"));
                    if (StringUtils.isNotBlank(member)) {
                        // Make sure the XWiki.Admin is serialized as xwiki:XWiki.Admin.
                        members.add(serializer.serialize(currentStringDocRefResolver.resolve(member)));
                    }
                }
            } catch (XWikiException e) {
                throw new RuntimeException("Failed to read group document: " + groupRef, e);
            }
        }

        return members;
    }

    @Override
    public void notifyApproved(XWikiDocument document, DocumentReference revisionOwner,
        List<DocumentReference> groupReferences)
    {
        Set<String> target = getGroupMembers(groupReferences, xcontextProvider.get());
        target.add(serializer.serialize(revisionOwner));
        observationManager.notify(new ApprovedEvent(target), EVENT_SOURCE, document);
    }
}
