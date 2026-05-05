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
 * PDF Export Manager allowing to export review pages list to PDF.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface PDFExportManager
{
    /**
     * Exports the given document to PDF and attaches the generated PDF to the document.
     *
     * @param document the document to export and attach the PDF to
     * @param pdfTemplateReference the document reference of the PDF template to use
     * @return the URL of the attached PDF
     */
    String exportAndAttachPDF(XWikiDocument document, DocumentReference pdfTemplateReference);
}
