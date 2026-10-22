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

import org.xwiki.model.reference.DocumentReference;

/**
 * A person named in a document to import (e.g. who approved it), and the users of the wiki who may be this person.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportPerson
{
    private final String name;

    private final List<DocumentReference> users;

    /**
     * @param name the name of the person as written in the document, e.g. "Dr. Voigt"
     * @param users the users of the wiki who may be this person, the most likely first
     */
    public SOPImportPerson(String name, List<DocumentReference> users)
    {
        this.name = name;
        this.users = List.copyOf(users);
    }

    /**
     * @return the name of the person as written in the document, e.g. "Dr. Voigt"
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the users of the wiki who may be this person, the most likely first; empty if none was found
     */
    public List<DocumentReference> getUsers()
    {
        return this.users;
    }
}
