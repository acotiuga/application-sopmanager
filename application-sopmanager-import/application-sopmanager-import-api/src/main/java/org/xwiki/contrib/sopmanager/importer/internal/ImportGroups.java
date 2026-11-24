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
package org.xwiki.contrib.sopmanager.importer.internal;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

/**
 * Finds the groups responsible for the approval or the review of an imported document: among the groups offered for
 * the approval (the members of the SOP Approvers group) or for the review (the members of the SOP Reviewers group),
 * those its approvers or reviewers belong to, directly or through another group.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportGroups.class)
@Singleton
public class ImportGroups
{
    private static final String XWIKI_SPACE = "XWiki";

    /**
     * The group whose members are the groups that can approve a SOP document.
     */
    private static final LocalDocumentReference SOP_APPROVERS_GROUP =
        new LocalDocumentReference(XWIKI_SPACE, "SOPApproversGroup");

    /**
     * The group whose members are the groups that can review a SOP document.
     */
    private static final LocalDocumentReference SOP_REVIEWERS_GROUP =
        new LocalDocumentReference(XWIKI_SPACE, "SOPReviewersGroup");

    @Inject
    private Logger logger;

    @Inject
    private GroupManager groupManager;

    /**
     * @param users the users approving a document
     * @param wiki the wiki of the document
     * @return the members of the SOP Approvers group of the wiki these users belong to, each once, sorted; none if they
     *     belong to none of them
     */
    public List<DocumentReference> getApproverGroups(List<DocumentReference> users, String wiki)
    {
        return getGroups(users, new DocumentReference(SOP_APPROVERS_GROUP, new WikiReference(wiki)));
    }

    /**
     * @param users the users reviewing a document
     * @param wiki the wiki of the document
     * @return the members of the SOP Reviewers group of the wiki these users belong to, each once, sorted; none if they
     *     belong to none of them
     */
    public List<DocumentReference> getReviewerGroups(List<DocumentReference> users, String wiki)
    {
        return getGroups(users, new DocumentReference(SOP_REVIEWERS_GROUP, new WikiReference(wiki)));
    }

    /**
     * @return the direct members of the given group that have one of the users among their members, directly or
     *     through another group
     */
    private List<DocumentReference> getGroups(List<DocumentReference> users, DocumentReference catalog)
    {
        SortedSet<DocumentReference> groups = new TreeSet<>();
        if (!users.isEmpty()) {
            for (DocumentReference group : getMembers(catalog, false)) {
                if (group != null && getMembers(group, true).stream().anyMatch(users::contains)) {
                    groups.add(group);
                }
            }
        }
        return List.copyOf(groups);
    }

    private Collection<DocumentReference> getMembers(DocumentReference group, boolean recurse)
    {
        try {
            return this.groupManager.getMembers(group, recurse);
        } catch (GroupException e) {
            this.logger.warn("Failed to read the members of [{}].", group, e);
            return List.of();
        }
    }
}
