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
package org.xwiki.contrib.sopmanager.importer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SOPImportCandidate}.
 *
 * @version $Id$
 */
class SOPImportCandidateTest
{
    private static final DocumentReference MUSTER = new DocumentReference("xwiki", "XWiki", "MMuster");

    private static final DocumentReference MUSTER_ANNA = new DocumentReference("xwiki", "XWiki", "AMuster");

    private static final DocumentReference VOIGT = new DocumentReference("xwiki", "XWiki", "SVoigt");

    @Test
    void getUsers()
    {
        SOPImportCandidate candidate =
            new SOPImportCandidate("A", new DocumentReference("xwiki", "SOPManager", "A"), false);
        List<SOPImportPerson> persons = List.of(new SOPImportPerson("Dr. Muster", List.of(MUSTER, MUSTER_ANNA)),
            new SOPImportPerson("Unbekannt", List.of()), new SOPImportPerson("Voigt", List.of(VOIGT)),
            new SOPImportPerson("M. Muster", List.of(MUSTER)));
        candidate.getPeople().put(SOPImportManager.APPROVER, persons);
        candidate.getPeople().put(SOPImportManager.OWNER, persons);

        // The most likely user of each person named, once.
        assertEquals(List.of(MUSTER, VOIGT), candidate.getUsers(SOPImportManager.APPROVER));
        // A single owner.
        assertEquals(List.of(MUSTER), candidate.getUsers(SOPImportManager.OWNER));
        assertEquals(List.of(), candidate.getUsers(SOPImportManager.REVIEWER));
    }
}
