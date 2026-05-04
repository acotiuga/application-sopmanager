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
package org.xwiki.contrib.sopmanager;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Interface for notifying SOP workflow events.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface SOPWorkflowEventNotifier
{
    /**
     * Notifies that a document has been submitted for review.
     *
     * @param document the document that has been submitted for review
     * @param userReference the reference of the user who submitted the document for review
     */
    void notifySubmittedForReview(XWikiDocument document, DocumentReference userReference);

    /**
     * Notifies that a document has been returned for changes.
     *
     * @param document the document that has been returned for changes
     * @param userReference the reference of the user who returned the document for changes
     */
    void notifyReturnedForChanges(XWikiDocument document, DocumentReference userReference);

    /**
     * Notifies that a document has been submitted for approval.
     *
     * @param document the document that has been submitted for approval
     * @param userReference the reference of the user who submitted the document for approval
     */
    void notifySubmittedForApproval(XWikiDocument document, DocumentReference userReference);

    /**
     * Notifies that a document has been approved.
     *
     * @param document the document that has been approved
     * @param revisionOwner the reference of the user who owns the revision of the document that has been approved
     * @param revisedBy the reference of the user who made the revision of the document that has been approved
     */
    void notifyApproved(XWikiDocument document, DocumentReference revisionOwner, DocumentReference revisedBy);
}
