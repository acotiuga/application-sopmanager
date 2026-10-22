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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;

/**
 * A version of a document to import: one of the files of the same name with different formats (e.g. A.doc, A.docx
 * and A.pdf), analysed before the import.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportVersion
{
    /**
     * Where a file comes from, deduced from its format and its properties.
     */
    public enum Origin
    {
        /**
         * A Word document (or Word 97-2003 document) written with Word: the original document.
         */
        ORIGINAL,

        /**
         * A Word document converted from a Word 97-2003 document (doc), with the same content.
         */
        CONVERTED_FROM_DOC,

        /**
         * A PDF exported from a Word document, with its structure.
         */
        EXPORTED_TO_PDF,

        /**
         * A Word document converted from a PDF (e.g. by Aspose), with a fixed layout.
         */
        CONVERTED_FROM_PDF,

        /**
         * A Word document whose page header and footer are in the content, e.g. rebuilt from a printed document.
         */
        REBUILT,

        /**
         * A file whose origin is not known, e.g. a file that can't be read.
         */
        UNKNOWN
    }

    private final String fileName;

    private final String format;

    private Origin origin = Origin.UNKNOWN;

    private Compatibility compatibility;

    private String message;

    private boolean original;

    private boolean recommended;

    private Integer textMatch;

    private final Map<String, Object> data = new LinkedHashMap<>();

    private final Map<String, Object> source = new LinkedHashMap<>();

    private final List<String> warnings = new ArrayList<>();

    /**
     * @param fileName the name of the file
     * @param format the format of the file: its extension, in lower case
     */
    public SOPImportVersion(String fileName, String format)
    {
        this.fileName = fileName;
        this.format = format;
    }

    /**
     * @return the name of the file
     */
    public String getFileName()
    {
        return this.fileName;
    }

    /**
     * @return the format of the file: its extension, in lower case, e.g. "docx"
     */
    public String getFormat()
    {
        return this.format;
    }

    /**
     * @return where the file comes from
     */
    public Origin getOrigin()
    {
        return this.origin;
    }

    /**
     * @param origin where the file comes from
     */
    public void setOrigin(Origin origin)
    {
        this.origin = origin;
    }

    /**
     * @return how well the file can be imported
     */
    public Compatibility getCompatibility()
    {
        return this.compatibility;
    }

    /**
     * @param compatibility how well the file can be imported
     */
    public void setCompatibility(Compatibility compatibility)
    {
        this.compatibility = compatibility;
    }

    /**
     * @return whether the file can be imported
     */
    public boolean isImportable()
    {
        return this.compatibility != null && this.compatibility != Compatibility.NOT_SUPPORTED
            && this.compatibility != Compatibility.INVALID && this.compatibility != Compatibility.NOT_ACCEPTED;
    }

    /**
     * @return why the file can't be imported, {@code null} if it can
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * @param message why the file can't be imported
     */
    public void setMessage(String message)
    {
        this.message = message;
    }

    /**
     * @return whether the file is the original document, of which the other files are conversions
     */
    public boolean isOriginal()
    {
        return this.original;
    }

    /**
     * @param original whether the file is the original document
     */
    public void setOriginal(boolean original)
    {
        this.original = original;
    }

    /**
     * @return whether the file is the version recommended for the import: the one keeping the most of the content and
     *     of its formatting
     */
    public boolean isRecommended()
    {
        return this.recommended;
    }

    /**
     * @param recommended whether the file is the version recommended for the import
     */
    public void setRecommended(boolean recommended)
    {
        this.recommended = recommended;
    }

    /**
     * @return the percentage of the words of the recommended version found in this version, e.g. 100 when only the
     *     format changed; {@code null} for the recommended version itself, or if it can't be compared
     */
    public Integer getTextMatch()
    {
        return this.textMatch;
    }

    /**
     * @param textMatch the percentage of the words of the recommended version found in this version
     */
    public void setTextMatch(Integer textMatch)
    {
        this.textMatch = textMatch;
    }

    /**
     * @return the SOP data read from the file, by property of the SOP class (e.g. "revisionId", "releaseDate") or by
     *     detail of the document (e.g. "approvedBy"); the dates are {@link java.util.Date}s
     */
    public Map<String, Object> getData()
    {
        return this.data;
    }

    /**
     * @return the properties of the file telling where it comes from: "application", "author", "created",
     *     "modified", "printed" (the dates are {@link java.util.Date}s), "originalDocument" (the path of the original
     *     document printed in the document)
     */
    public Map<String, Object> getSource()
    {
        return this.source;
    }

    /**
     * @return what would not be imported as is
     */
    public List<String> getWarnings()
    {
        return Collections.unmodifiableList(this.warnings);
    }

    /**
     * @param warning something that would not be imported as is
     */
    public void addWarning(String warning)
    {
        this.warnings.add(warning);
    }
}
