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
package org.xwiki.contrib.sopmanager.script;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;

/**
 * Script service wrapping a {@link SOPManager} component.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("sopService")
@Singleton
public class SOPScriptService implements ScriptService
{
    @Inject
    private SOPManager sopManager;

    /**
     * Adds a document to the review process.
     *
     * @param documentReference the reference of the document to be added to the review process.
     */
    public void addDocumentToReviewProcess(DocumentReference documentReference)
    {
        sopManager.addDocumentToReviewProcess(documentReference);
    }

    /**
     * Updates the review state of a document based on the specified action.
     *
     * @param action the action taken in the review process (e.g., "draft", "submittedForReview")
     * @param documentReference a reference to the document whose review state is to be updated
     * @return a success message after applying the specified action
     */
    public String updateDocumentReviewState(String action, DocumentReference documentReference)
    {
        return sopManager.updateDocumentReviewState(action, documentReference);
    }
}
