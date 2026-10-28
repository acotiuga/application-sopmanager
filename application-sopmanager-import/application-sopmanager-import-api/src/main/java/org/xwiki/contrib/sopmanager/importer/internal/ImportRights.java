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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import com.xpn.xwiki.XWikiContext;

/**
 * Tells who can import documents: the administrators of the wiki and the members of its SOP Editors group,
 * directly or through another group.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportRights.class)
@Singleton
public class ImportRights
{
    private static final LocalDocumentReference SOP_EDITORS_GROUP =
        new LocalDocumentReference("XWiki", "SOPEditorsGroup");

    @Inject
    private Logger logger;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private GroupManager groupManager;

    /**
     * @param context the context of the current user and wiki
     * @return {@code true} if the current user can import documents in the current wiki
     */
    public boolean canImport(XWikiContext context)
    {
        DocumentReference user = context.getUserReference();
        WikiReference wiki = new WikiReference(context.getWikiId());
        if (user == null) {
            return false;
        } else if (this.authorizationManager.hasAccess(Right.ADMIN, user, wiki)) {
            return true;
        }
        try {
            return this.groupManager.getGroups(user, wiki, true).contains(getSOPEditorsGroup(context));
        } catch (GroupException e) {
            this.logger.warn("Failed to read the groups of [{}].", user, e);
            return false;
        }
    }

    /**
     * @param reference the page of a SOP document to create
     * @param context the context of the current user
     * @return {@code true} if the current user can create this page
     */
    public boolean canEdit(DocumentReference reference, XWikiContext context)
    {
        return this.authorizationManager.hasAccess(Right.EDIT, context.getUserReference(), reference);
    }

    /**
     * @param context the context of the current wiki
     * @return the SOP Editors group of the current wiki
     */
    public DocumentReference getSOPEditorsGroup(XWikiContext context)
    {
        return new DocumentReference(SOP_EDITORS_GROUP, new WikiReference(context.getWikiId()));
    }
}
