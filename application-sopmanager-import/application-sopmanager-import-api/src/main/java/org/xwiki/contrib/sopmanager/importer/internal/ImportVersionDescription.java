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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;

/**
 * Describes a version of a document to import: where it comes from, and the SOP data it would be imported with.
 *
 * @version $Id$
 * @since 1.0
 */
public final class ImportVersionDescription
{
    private ImportVersionDescription()
    {
    }

    /**
     * @param version a version of a document to import
     * @param document the document read from it
     */
    public static void describe(SOPImportVersion version, ImportedDocument document)
    {
        version.setOrigin(ImportVersions.getOrigin(version.getFormat(), document));
        version.setCompatibility(document.compatibility());

        String baseName = ImportFolder.getBaseName(ImportFolder.getName(version.getFileName()));
        SOPDocumentDetails details = document.details();
        Map<String, Object> data = version.getData();
        put(data, "revisionId", SOPDocumentCreator.getRevisionId(baseName, details));
        put(data, "title", SOPDocumentCreator.getTitle(baseName, details));
        put(data, "releaseDate", toDate(details.getReleaseDate()));
        put(data, "revisionNumber", details.getRevisionNumber());
        put(data, "revisionPlannedDate", toDate(details.getRevisionPlannedDate()));
        put(data, "approvedBy", details.getApprovedBy());
        put(data, "revisedBy", details.getRevisedBy());
        put(data, "pdfTemplate", document.landscape() ? SOPDocumentCreator.PDF_TEMPLATE_HORIZONTAL
            : SOPDocumentCreator.PDF_TEMPLATE_VERTICAL);

        DocumentSource source = document.source();
        Map<String, Object> sourceData = version.getSource();
        put(sourceData, "application", source.application());
        put(sourceData, "author", source.author());
        put(sourceData, "created", toDate(source.created()));
        put(sourceData, "modified", toDate(source.modified()));
        put(sourceData, "printed", toDate(source.printed()));
        put(sourceData, "originalDocument", source.originalDocument());
    }

    private static void put(Map<String, Object> data, String key, Object value)
    {
        if (value != null && !"".equals(value)) {
            data.put(key, value);
        }
    }

    private static Date toDate(LocalDate date)
    {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toDate(Instant instant)
    {
        return instant == null ? null : Date.from(instant);
    }
}
