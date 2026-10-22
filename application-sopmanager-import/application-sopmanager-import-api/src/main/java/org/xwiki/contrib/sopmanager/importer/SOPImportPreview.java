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

/**
 * The preview of the import of a file: the SOP document it would be imported as.
 *
 * @param version the file, with the SOP data read from it
 * @param title the title of the SOP document
 * @param content the content of the SOP document, in the xwiki/2.1 syntax, with its images embedded as data URIs
 * @version $Id$
 * @since 1.0
 */
public record SOPImportPreview(SOPImportVersion version, String title, String content)
{
}
