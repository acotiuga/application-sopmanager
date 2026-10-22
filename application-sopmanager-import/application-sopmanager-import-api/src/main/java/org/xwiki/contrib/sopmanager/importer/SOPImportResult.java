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
import java.util.List;

import org.xwiki.model.reference.DocumentReference;

/**
 * The result of the import of a file as SOP document.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportResult
{
    /**
     * How well a file can be imported.
     */
    public enum Compatibility
    {
        /**
         * A Word document with its details in the page header and footer, or without page header and footer.
         */
        COMPATIBLE,

        /**
         * A Word document whose page header and footer are in the content, removed from the imported content.
         */
        HEADER_FOOTER_IN_CONTENT,

        /**
         * A Word document converted from PDF, with a fixed layout: the text is imported, the layout is approximated.
         */
        FIXED_LAYOUT,

        /**
         * A PDF with its structure (paragraphs, headings, lists, tables, figures), as exported by Word: the content is
         * imported, the layout and part of the formatting (colors, underlines, alignment) are lost.
         */
        TAGGED_PDF,

        /**
         * A file whose format can't be imported (e.g. a PDF without structure such as a scan, or a Word 97-2003
         * document while the office server is not running).
         */
        NOT_SUPPORTED,

        /**
         * A file that can't be read (e.g. a damaged document, a Word lock file).
         */
        INVALID,

        /**
         * A file whose type is not one of the accepted types.
         */
        NOT_ACCEPTED
    }

    /**
     * What happened to a file.
     */
    public enum Outcome
    {
        /**
         * The file was imported as a new SOP document.
         */
        IMPORTED,

        /**
         * The file was not imported because a page already exists for it.
         */
        SKIPPED_EXISTING,

        /**
         * The file was not imported because of its format or type.
         */
        SKIPPED_FORMAT,

        /**
         * The import of the file failed.
         */
        FAILED
    }

    private final String fileName;

    private Compatibility compatibility;

    private Outcome outcome;

    private DocumentReference documentReference;

    private String message;

    private final List<String> warnings = new ArrayList<>();

    /**
     * @param fileName the name of the file
     */
    public SOPImportResult(String fileName)
    {
        this.fileName = fileName;
    }

    /**
     * @return the name of the file
     */
    public String getFileName()
    {
        return this.fileName;
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
     * @return what happened to the file
     */
    public Outcome getOutcome()
    {
        return this.outcome;
    }

    /**
     * @param outcome what happened to the file
     */
    public void setOutcome(Outcome outcome)
    {
        this.outcome = outcome;
    }

    /**
     * @return the SOP document created for the file, or the existing page for which the file was skipped
     */
    public DocumentReference getDocumentReference()
    {
        return this.documentReference;
    }

    /**
     * @param documentReference the SOP document created for the file, or the existing page
     */
    public void setDocumentReference(DocumentReference documentReference)
    {
        this.documentReference = documentReference;
    }

    /**
     * @return the reason why the file was not imported, {@code null} if it was imported
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * @param message the reason why the file was not imported
     */
    public void setMessage(String message)
    {
        this.message = message;
    }

    /**
     * @return what was not imported as is (e.g. a date that couldn't be read)
     */
    public List<String> getWarnings()
    {
        return Collections.unmodifiableList(this.warnings);
    }

    /**
     * @param warning something that was not imported as is
     */
    public void addWarning(String warning)
    {
        this.warnings.add(warning);
    }
}
