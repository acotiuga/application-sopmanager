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

import java.util.List;
import java.util.Map;

import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.rendering.block.XDOM;

/**
 * A document (Word document or PDF) read for its import as SOP document.
 *
 * @param content the content of the document, without its page header and footer
 * @param images the images of the content, by attachment name
 * @param details the details of the page header and footer
 * @param compatibility where the page header and footer were found, and whether the layout is approximated
 * @param landscape whether the pages of the document are in landscape orientation
 * @param warnings what was not imported as is
 * @param source where the document comes from: the application that wrote it, its dates, the original document
 * @version $Id$
 * @since 1.0
 */
public record ImportedDocument(XDOM content, Map<String, byte[]> images, SOPDocumentDetails details,
    Compatibility compatibility, boolean landscape, List<ImportWarning> warnings, DocumentSource source)
{
}
