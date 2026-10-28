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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.model.reference.DocumentReference;

/**
 * A document to import, analysed before the import: the files of the same name in different formats (e.g. A.doc,
 * A.docx and A.pdf), the SOP document they would be imported as, and the version recommended for the import.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportCandidate
{
    private final String name;

    private final DocumentReference documentReference;

    private final boolean exists;

    private final List<SOPImportVersion> versions = new ArrayList<>();

    private boolean selected;

    private final Map<String, List<SOPImportPerson>> people = new LinkedHashMap<>();

    private String lastModifiedBy;

    private Date lastModified;

    private String lastModifiedFile;

    /**
     * @param name the name of the document: the name of its files, without extension
     * @param documentReference the SOP document the files would be imported as
     * @param exists whether this SOP document already exists, in which case the document is not imported
     */
    public SOPImportCandidate(String name, DocumentReference documentReference, boolean exists)
    {
        this.name = name;
        this.documentReference = documentReference;
        this.exists = exists;
    }

    /**
     * @return the name of the document: the name of its files, without extension
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the SOP document the files would be imported as
     */
    public DocumentReference getDocumentReference()
    {
        return this.documentReference;
    }

    /**
     * @return whether the SOP document already exists, in which case the document is not imported
     */
    public boolean isExists()
    {
        return this.exists;
    }

    /**
     * @return the files of the document, the recommended one first
     */
    public List<SOPImportVersion> getVersions()
    {
        return this.versions;
    }

    /**
     * @return the version recommended for the import, {@code null} if no file can be imported
     */
    public SOPImportVersion getRecommended()
    {
        return this.versions.stream().filter(SOPImportVersion::isRecommended).findFirst().orElse(null);
    }

    /**
     * @return the original document, of which the other files are conversions; {@code null} if it is not one of the
     *     files
     */
    public SOPImportVersion getOriginal()
    {
        return this.versions.stream().filter(SOPImportVersion::isOriginal).findFirst().orElse(null);
    }

    /**
     * @return whether the recommended version is selected for the import by default
     */
    public boolean isSelected()
    {
        return this.selected;
    }

    /**
     * @param selected whether the recommended version is selected for the import by default
     */
    public void setSelected(boolean selected)
    {
        this.selected = selected;
    }

    /**
     * @return the persons named in the recommended version, by role ({@link SOPImportManager#OWNER},
     *     {@link SOPImportManager#APPROVER}, {@link SOPImportManager#REVIEWER}), with the users who may be them: the
     *     owner is who last modified the document, the approver and the reviewer are named in the recommended version
     */
    public Map<String, List<SOPImportPerson>> getPeople()
    {
        return this.people;
    }

    /**
     * @param role a role: {@link SOPImportManager#OWNER}, {@link SOPImportManager#APPROVER},
     *     {@link SOPImportManager#REVIEWER}
     * @return the users proposed for this role: the most likely user of each person named in the recommended version,
     *     a single one for the owner
     */
    public List<DocumentReference> getUsers(String role)
    {
        List<DocumentReference> users = new ArrayList<>();
        for (SOPImportPerson person : this.people.getOrDefault(role, List.of())) {
            person.getUsers().stream().findFirst().filter(user -> !users.contains(user)).ifPresent(users::add);
        }
        return SOPImportManager.OWNER.equals(role) && users.size() > 1 ? users.subList(0, 1) : users;
    }

    /**
     * @return who last modified the document: the last modifier of the version modified last, e.g. who converted a
     *     document this year rather than who edited its previous format years ago; {@code null} if unknown
     */
    public String getLastModifiedBy()
    {
        return this.lastModifiedBy;
    }

    /**
     * @return when the document was last modified, {@code null} if unknown
     */
    public Date getLastModified()
    {
        return this.lastModified;
    }

    /**
     * @return the version modified last, {@code null} if unknown
     */
    public String getLastModifiedFile()
    {
        return this.lastModifiedFile;
    }

    /**
     * @param by who last modified the document
     * @param on when the document was last modified
     * @param file the version modified last
     */
    public void setLastModification(String by, Date on, String file)
    {
        this.lastModifiedBy = by;
        this.lastModified = on;
        this.lastModifiedFile = file;
    }
}
