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

import java.util.Arrays;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * Interface for managing SOPs (Standard Operating Procedures) in XWiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface SOPManager
{
    /**
     * The name of the space where the SOP Manager application is located.
     */
    String SOP_MANAGER = "SOPManager";
    /**
     * The name of the subspace where code-related/technical documents are stored.
     */
    String CODE = "Code";
    /**
     * Reference to the class that stores review metadata for pages.
     */
    LocalDocumentReference SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE =
        new LocalDocumentReference(Arrays.asList(SOP_MANAGER, CODE), "ControlledDocumentClass");

    /**
     * Adds a document to the review process.
     *
     * @param documentReference the reference of the document to be added to the review process.
     */
    void addDocumentToReviewProcess(DocumentReference documentReference);
}
