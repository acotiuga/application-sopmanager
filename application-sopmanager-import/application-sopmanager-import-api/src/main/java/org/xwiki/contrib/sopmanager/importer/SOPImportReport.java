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

import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Outcome;

/**
 * The report of an import of documents as SOP documents: the result of each file of the folder.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportReport
{
    private final String folder;

    private final List<SOPImportResult> results = new ArrayList<>();

    private String error;

    /**
     * @param folder the imported folder
     */
    public SOPImportReport(String folder)
    {
        this.folder = folder;
    }

    /**
     * @return the imported folder
     */
    public String getFolder()
    {
        return this.folder;
    }

    /**
     * @return the result of each file of the folder, in the order of their names
     */
    public List<SOPImportResult> getResults()
    {
        return Collections.unmodifiableList(this.results);
    }

    /**
     * @param result the result of a file of the folder
     */
    public void addResult(SOPImportResult result)
    {
        this.results.add(result);
    }

    /**
     * @return the files imported as SOP documents
     */
    public List<SOPImportResult> getImported()
    {
        return this.results.stream().filter(result -> result.getOutcome() == Outcome.IMPORTED).toList();
    }

    /**
     * @return the files that were not imported: skipped or failed
     */
    public List<SOPImportResult> getNotImported()
    {
        return this.results.stream().filter(result -> result.getOutcome() != Outcome.IMPORTED).toList();
    }

    /**
     * @return the reason why the folder couldn't be imported at all (e.g. it doesn't exist), {@code null} otherwise
     */
    public String getError()
    {
        return this.error;
    }

    /**
     * @param error the reason why the folder couldn't be imported at all
     */
    public void setError(String error)
    {
        this.error = error;
    }
}
