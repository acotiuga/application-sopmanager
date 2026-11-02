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

import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

/**
 * Finds the groups responsible for the review or the approval of an imported document: the groups of its reviewers or
 * approvers named as the groups of the managers, directly or through another group.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportGroups.class)
@Singleton
public class ImportGroups
{
    /**
     * What the name of a group of managers contains, e.g. "ANA_Leitender_Arzt".
     */
    private static final String MANAGERS = "_Leitender_";

    @Inject
    private Logger logger;

    @Inject
    private GroupManager groupManager;

    /**
     * @param users the users reviewing or approving a document
     * @param wiki the wiki of the document
     * @return the groups of managers of these users, each once, sorted; none if they have no such group
     */
    public List<DocumentReference> getManagerGroups(List<DocumentReference> users, String wiki)
    {
        SortedSet<DocumentReference> groups = new TreeSet<>();
        for (DocumentReference user : users) {
            try {
                this.groupManager.getGroups(user, new WikiReference(wiki), true).stream().filter(Objects::nonNull)
                    .filter(group -> group.getName().contains(MANAGERS)).forEach(groups::add);
            } catch (GroupException e) {
                this.logger.warn("Failed to read the groups of [{}].", user, e);
            }
        }
        return List.copyOf(groups);
    }
}
