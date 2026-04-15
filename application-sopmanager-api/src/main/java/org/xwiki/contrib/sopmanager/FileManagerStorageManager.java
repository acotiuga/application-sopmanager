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

import com.xpn.xwiki.doc.XWikiAttachment;

/**
 * File Manager Storage Manager allowing to store PDF files in the File Manager.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface FileManagerStorageManager
{
    /**
     * Stores the given attachment in the File Manager and links it to the given document.
     *
     * @param sourceDocumentReference the reference of the document to link the attachment to
     * @param attachment the attachment to store
     * @param fileName the name of the file to store
     */
    void storeAttachment(DocumentReference sourceDocumentReference, XWikiAttachment attachment, String fileName);
}
