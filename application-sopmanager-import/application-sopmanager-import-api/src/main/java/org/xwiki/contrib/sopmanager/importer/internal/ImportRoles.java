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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Sets the roles of an imported document: its owner, its approvers and reviewers, and the groups responsible for its
 * approval and its review, found from these users.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportRoles.class)
@Singleton
public class ImportRoles
{
    private static final String WARNING_PREFIX = "sopManager.import.warning.";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ImportGroups groups;

    /**
     * @param sop the object of the SOP document
     * @param wiki the wiki of the SOP document
     * @param users the users selected for the document, by role ({@link SOPImportManager#OWNER},
     *     {@link SOPImportManager#APPROVER}, {@link SOPImportManager#REVIEWER}): the references of the users, possibly
     *     separated by commas as submitted by the user pickers; the first one is set as owner, all of them as approvers
     *     and reviewers, whose groups of managers are set as the approver and reviewer groups
     * @param details the details read from the document
     * @param context the XWiki context
     * @return the persons named in the document who are not set
     * @throws XWikiException if a user can't be checked, or a value can't be set
     */
    public List<ImportWarning> set(BaseObject sop, String wiki, Map<String, List<String>> users,
        SOPDocumentDetails details, XWikiContext context) throws XWikiException
    {
        List<DocumentReference> owners = getUsers(users.get(SOPImportManager.OWNER), context);
        setReferences(sop, "revisionOwner", owners.isEmpty() ? owners : owners.subList(0, 1), context);

        List<DocumentReference> approvers = getUsers(users.get(SOPImportManager.APPROVER), context);
        setReferences(sop, "approverUser", approvers, context);
        setReferences(sop, "approverGroups", this.groups.getManagerGroups(approvers, wiki), context);

        List<DocumentReference> reviewers = getUsers(users.get(SOPImportManager.REVIEWER), context);
        setReferences(sop, "reviewerUser", reviewers, context);
        setReferences(sop, "reviewerGroups", this.groups.getManagerGroups(reviewers, wiki), context);

        List<ImportWarning> warnings = new ArrayList<>();
        if (approvers.isEmpty() && StringUtils.isNotBlank(details.getApprovedBy())) {
            warnings.add(new ImportWarning(WARNING_PREFIX + "approverNotSet", details.getApprovedBy()));
        }
        if (reviewers.isEmpty() && StringUtils.isNotBlank(details.getRevisedBy())) {
            warnings.add(new ImportWarning(WARNING_PREFIX + "reviewerNotSet", details.getRevisedBy()));
        }
        return warnings;
    }

    /**
     * @return the selected users that exist, each once
     */
    private List<DocumentReference> getUsers(List<String> users, XWikiContext context) throws XWikiException
    {
        List<DocumentReference> existing = new ArrayList<>();
        for (String value : users == null ? List.<String>of() : users) {
            for (String user : StringUtils.split(StringUtils.defaultString(value), ',')) {
                DocumentReference reference = this.resolver.resolve(user.trim());
                if (StringUtils.isNotBlank(user) && !existing.contains(reference)
                    && context.getWiki().exists(reference, context)) {
                    existing.add(reference);
                }
            }
        }
        return existing;
    }

    /**
     * Sets a property holding users or groups, separated by commas, unless there are none.
     */
    private void setReferences(BaseObject sop, String property, List<DocumentReference> references,
        XWikiContext context) throws XWikiException
    {
        if (!references.isEmpty()) {
            sop.set(property, references.stream().map(this.serializer::serialize).collect(Collectors.joining(",")),
                context);
        }
    }
}
