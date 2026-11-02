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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportGroups}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportGroupsTest
{
    private static final WikiReference WIKI = new WikiReference("xwiki");

    private static final DocumentReference MUSTER = reference("MMuster");

    private static final DocumentReference BEISPIEL = reference("KBeispiel");

    private static final DocumentReference ANA = reference("ANA_Leitender_Arzt");

    private static final DocumentReference ITS = reference("ITS_Leitender_Arzt");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private ImportGroups groups;

    @MockComponent
    private GroupManager groupManager;

    private static DocumentReference reference(String name)
    {
        return new DocumentReference("xwiki", "XWiki", name);
    }

    @Test
    void getManagerGroups() throws Exception
    {
        when(this.groupManager.getGroups(MUSTER, WIKI, true)).thenReturn(List.of(ITS, reference("XWikiAllGroup"), ANA,
            reference("leitender_arzt"), reference("Leitender_Arzt")));
        when(this.groupManager.getGroups(BEISPIEL, WIKI, true)).thenReturn(List.of(ANA));

        // Only the groups of managers, each once.
        assertEquals(List.of(ANA, ITS), this.groups.getManagerGroups(List.of(MUSTER, BEISPIEL), "xwiki"));
    }

    @Test
    void getManagerGroupsWithoutGroup() throws Exception
    {
        when(this.groupManager.getGroups(MUSTER, WIKI, true)).thenReturn(List.of(reference("XWikiAllGroup")));

        assertEquals(List.of(), this.groups.getManagerGroups(List.of(MUSTER), "xwiki"));
        assertEquals(List.of(), this.groups.getManagerGroups(List.of(), "xwiki"));
    }

    @Test
    void getManagerGroupsWhenTheGroupsOfAUserCantBeRead() throws Exception
    {
        when(this.groupManager.getGroups(MUSTER, WIKI, true)).thenThrow(new GroupException("Failed"));
        when(this.groupManager.getGroups(BEISPIEL, WIKI, true)).thenReturn(List.of(ANA));

        assertEquals(List.of(ANA), this.groups.getManagerGroups(List.of(MUSTER, BEISPIEL), "xwiki"));
        assertEquals("Failed to read the groups of [xwiki:XWiki.MMuster].", this.logCapture.getMessage(0));
    }
}
