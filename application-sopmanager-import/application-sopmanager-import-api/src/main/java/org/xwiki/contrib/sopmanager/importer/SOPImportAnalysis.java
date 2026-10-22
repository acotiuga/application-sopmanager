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
import java.util.List;

/**
 * The analysis of the documents of a folder, before their import: what would be imported, from which file, and why
 * the other files would not.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportAnalysis
{
    private final String folder;

    private final List<SOPImportCandidate> documents = new ArrayList<>();

    private final List<SOPImportResult> otherFiles = new ArrayList<>();

    private String error;

    /**
     * @param folder the analysed folder
     */
    public SOPImportAnalysis(String folder)
    {
        this.folder = folder;
    }

    /**
     * @return the analysed folder
     */
    public String getFolder()
    {
        return this.folder;
    }

    /**
     * @return the documents of the folder, each with its files
     */
    public List<SOPImportCandidate> getDocuments()
    {
        return this.documents;
    }

    /**
     * @return the files that are not documents to import (e.g. a type not accepted, a Word lock file)
     */
    public List<SOPImportResult> getOtherFiles()
    {
        return this.otherFiles;
    }

    /**
     * @return why the folder could not be analysed, {@code null} if it was
     */
    public String getError()
    {
        return this.error;
    }

    /**
     * @param error why the folder could not be analysed
     */
    public void setError(String error)
    {
        this.error = error;
    }
}
