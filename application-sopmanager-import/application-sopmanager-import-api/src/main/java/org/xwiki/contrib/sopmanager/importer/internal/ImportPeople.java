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

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportPerson;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;

/**
 * The persons of a document to import: who last modified it, the owner, and the approver and the reviewer named in its
 * recommended version, with the users of the wiki who may be them.
 *
 * @version $Id$
 * @since 1.0
 */
public final class ImportPeople
{
    private ImportPeople()
    {
    }

    /**
     * @param candidate a document to import, its versions compared
     * @param documents the documents read from its versions
     * @param users the users of the wiki
     * @param resolver finds the users named in the document
     */
    public static void describe(SOPImportCandidate candidate, Map<SOPImportVersion, ImportedDocument> documents,
        List<ImportUserResolver.User> users, ImportUserResolver resolver)
    {
        setLastModification(candidate, documents);
        if (candidate.getRecommended() != null) {
            SOPDocumentDetails details = documents.get(candidate.getRecommended()).details();
            Map<String, List<SOPImportPerson>> people = candidate.getPeople();
            people.put(SOPImportManager.OWNER, resolver.resolve(candidate.getLastModifiedBy(), users));
            people.put(SOPImportManager.APPROVER, resolver.resolve(details.getApprovedBy(), users));
            people.put(SOPImportManager.REVIEWER, resolver.resolve(details.getRevisedBy(), users));
        }
    }

    /**
     * Sets who last modified the document: the last modifier of the version modified (or created) last, e.g. who
     * converted a document this year rather than who edited its previous format years ago.
     */
    private static void setLastModification(SOPImportCandidate candidate,
        Map<SOPImportVersion, ImportedDocument> documents)
    {
        SOPImportVersion lastVersion = null;
        Instant last = null;
        for (Map.Entry<SOPImportVersion, ImportedDocument> entry : documents.entrySet()) {
            DocumentSource source = entry.getValue().source();
            Instant modified = source.modified() != null ? source.modified() : source.created();
            boolean later = last == null || modified != null && modified.isAfter(last);
            if (StringUtils.isNotBlank(source.author()) && later) {
                lastVersion = entry.getKey();
                last = modified;
            }
        }
        if (lastVersion != null) {
            candidate.setLastModification(documents.get(lastVersion).source().author(),
                last == null ? null : Date.from(last), lastVersion.getFileName());
        }
    }
}
