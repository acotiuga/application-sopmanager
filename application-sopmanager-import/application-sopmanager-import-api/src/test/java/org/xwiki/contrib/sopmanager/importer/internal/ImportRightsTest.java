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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportRights}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportRightsTest
{
    private static final DocumentReference USER = new DocumentReference("sub", "XWiki", "Editor");

    private static final WikiReference WIKI = new WikiReference("sub");

    private static final DocumentReference SOP_EDITORS = new DocumentReference("sub", "XWiki", "SOPEditorsGroup");

    @InjectMockComponents
    private ImportRights rights;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    private GroupManager groupManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiContext context;

    @BeforeEach
    void beforeEach()
    {
        this.context = mock(XWikiContext.class);
        when(this.context.getWikiId()).thenReturn("sub");
        when(this.context.getUserReference()).thenReturn(USER);
    }

    @Test
    void canEdit()
    {
        DocumentReference page = new DocumentReference("sub", "SOPManager", "Page");
        assertFalse(this.rights.canEdit(page, this.context));

        when(this.authorizationManager.hasAccess(Right.EDIT, USER, page)).thenReturn(true);
        assertTrue(this.rights.canEdit(page, this.context));
    }

    @Test
    void getSOPEditorsGroup()
    {
        assertEquals(SOP_EDITORS, this.rights.getSOPEditorsGroup(this.context));
    }

    @Test
    void sopEditorsCanImport() throws Exception
    {
        when(this.groupManager.getGroups(USER, WIKI, true)).thenReturn(List.of(SOP_EDITORS));

        assertTrue(this.rights.canImport(this.context));
    }

    @Test
    void wikiAdministratorsCanImport() throws Exception
    {
        when(this.groupManager.getGroups(USER, WIKI, true)).thenReturn(List.of());
        assertFalse(this.rights.canImport(this.context));

        when(this.authorizationManager.hasAccess(Right.ADMIN, USER, WIKI)).thenReturn(true);
        assertTrue(this.rights.canImport(this.context));
    }

    @Test
    void otherUsersCannotImport() throws Exception
    {
        // The edit right is not enough: the users who can edit the SOP documents must also be SOP editors.
        when(this.authorizationManager.hasAccess(Right.EDIT, USER, WIKI)).thenReturn(true);
        when(this.groupManager.getGroups(USER, WIKI, true))
            .thenReturn(List.of(new DocumentReference("sub", "XWiki", "SOPReviewersGroup")));
        assertFalse(this.rights.canImport(this.context));

        // The guest user.
        when(this.context.getUserReference()).thenReturn(null);
        assertFalse(this.rights.canImport(this.context));
    }

    @Test
    void cannotImportWhenTheGroupsCannotBeRead() throws Exception
    {
        when(this.groupManager.getGroups(USER, WIKI, true)).thenThrow(new GroupException("Failure", null));

        assertFalse(this.rights.canImport(this.context));
        assertEquals("Failed to read the groups of [sub:XWiki.Editor].", this.logCapture.getMessage(0));
    }
}
