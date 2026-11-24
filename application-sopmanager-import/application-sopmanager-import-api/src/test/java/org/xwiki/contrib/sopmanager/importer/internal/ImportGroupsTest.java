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
    private static final DocumentReference APPROVERS = reference("SOPApproversGroup");

    private static final DocumentReference REVIEWERS = reference("SOPReviewersGroup");

    private static final DocumentReference MUSTER = reference("MMuster");

    private static final DocumentReference BEISPIEL = reference("KBeispiel");

    private static final DocumentReference ANA = reference("ANA_Leitender_Arzt");

    private static final DocumentReference ITS = reference("ITS_Leitender_Arzt");

    private static final DocumentReference ENDOSKOPIE = reference("gRol_Endoskopie_Pflege_StationsleitungGroup");

    private static final DocumentReference PFLEGE = reference("Pflege");

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
    void getApproverGroups() throws Exception
    {
        // The groups offered for the approval, and a user.
        when(this.groupManager.getMembers(APPROVERS, false)).thenReturn(List.of(ITS, ENDOSKOPIE, ANA, PFLEGE, MUSTER));
        when(this.groupManager.getMembers(ITS, true)).thenReturn(List.of(MUSTER));
        // A member through another group.
        when(this.groupManager.getMembers(ENDOSKOPIE, true)).thenReturn(List.of(reference("Endoskopie"), BEISPIEL));
        when(this.groupManager.getMembers(ANA, true)).thenReturn(List.of(MUSTER, BEISPIEL));
        when(this.groupManager.getMembers(PFLEGE, true)).thenReturn(List.of(reference("Other")));
        // A group the approvers belong to, not offered for the approval.
        when(this.groupManager.getMembers(REVIEWERS, false)).thenReturn(List.of(reference("ANA_Pflege")));
        when(this.groupManager.getMembers(reference("ANA_Pflege"), true)).thenReturn(List.of(MUSTER));

        // Only the groups offered for the approval that the approvers belong to, each once.
        assertEquals(List.of(ANA, ITS, ENDOSKOPIE), this.groups.getApproverGroups(List.of(MUSTER, BEISPIEL), "xwiki"));
    }

    @Test
    void getReviewerGroups() throws Exception
    {
        when(this.groupManager.getMembers(REVIEWERS, false)).thenReturn(List.of(PFLEGE, ANA));
        when(this.groupManager.getMembers(PFLEGE, true)).thenReturn(List.of(BEISPIEL));
        when(this.groupManager.getMembers(ANA, true)).thenReturn(List.of(MUSTER));
        // The groups offered for the approval are not used for the review.
        when(this.groupManager.getMembers(APPROVERS, false)).thenReturn(List.of(ITS));
        when(this.groupManager.getMembers(ITS, true)).thenReturn(List.of(BEISPIEL));

        assertEquals(List.of(PFLEGE), this.groups.getReviewerGroups(List.of(BEISPIEL), "xwiki"));
    }

    @Test
    void getGroupsInAnotherWiki() throws Exception
    {
        DocumentReference approvers = new DocumentReference("sub", "XWiki", "SOPApproversGroup");
        when(this.groupManager.getMembers(approvers, false)).thenReturn(List.of(ANA));
        when(this.groupManager.getMembers(ANA, true)).thenReturn(List.of(MUSTER));

        assertEquals(List.of(ANA), this.groups.getApproverGroups(List.of(MUSTER), "sub"));
        assertEquals(List.of(), this.groups.getApproverGroups(List.of(MUSTER), "xwiki"));
    }

    @Test
    void getGroupsWithoutGroup() throws Exception
    {
        when(this.groupManager.getMembers(APPROVERS, false)).thenReturn(List.of(ANA));
        when(this.groupManager.getMembers(ANA, true)).thenReturn(List.of(BEISPIEL));

        assertEquals(List.of(), this.groups.getApproverGroups(List.of(MUSTER), "xwiki"));
        assertEquals(List.of(), this.groups.getApproverGroups(List.of(), "xwiki"));
        // No group offered for the review.
        assertEquals(List.of(), this.groups.getReviewerGroups(List.of(MUSTER), "xwiki"));
    }

    @Test
    void getGroupsWhenTheMembersOfAGroupCantBeRead() throws Exception
    {
        when(this.groupManager.getMembers(APPROVERS, false)).thenReturn(List.of(ITS, ANA));
        when(this.groupManager.getMembers(ITS, true)).thenThrow(new GroupException("Failed"));
        when(this.groupManager.getMembers(ANA, true)).thenReturn(List.of(MUSTER));

        assertEquals(List.of(ANA), this.groups.getApproverGroups(List.of(MUSTER), "xwiki"));
        assertEquals("Failed to read the members of [xwiki:XWiki.ITS_Leitender_Arzt].", this.logCapture.getMessage(0));

        when(this.groupManager.getMembers(REVIEWERS, false)).thenThrow(new GroupException("Failed"));

        assertEquals(List.of(), this.groups.getReviewerGroups(List.of(MUSTER), "xwiki"));
        assertEquals("Failed to read the members of [xwiki:XWiki.SOPReviewersGroup].", this.logCapture.getMessage(1));
    }
}
