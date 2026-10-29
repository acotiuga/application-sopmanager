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
package org.xwiki.contrib.sopmanager.importer.script;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.importer.SOPImportAnalysis;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportPreview;
import org.xwiki.contrib.sopmanager.importer.SOPImportReport;
import org.xwiki.script.service.ScriptService;

/**
 * Script service of the import of documents (Word documents, Word 97-2003 documents, PDF documents) as SOP documents,
 * wrapping a {@link SOPImportManager} component.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("sopImport")
@Singleton
public class SOPImportScriptService implements ScriptService
{
    @Inject
    private SOPImportManager importManager;

    /**
     * @return {@code true} if the current user can import documents: the administrators of the current wiki and the
     *     members of its SOP Editors group
     * @see SOPImportManager#canImport()
     */
    public boolean canImport()
    {
        return this.importManager.canImport();
    }

    /**
     * @param folder the absolute path of a folder of the server file system
     * @return the folder and its sub-folders with their files, to choose the files to import; {@code null} if the
     *     current user can't import documents, or the folder doesn't exist
     * @see SOPImportManager#getFolders(String)
     */
    public SOPImportFolder getFolders(String folder)
    {
        return this.importManager.getFolders(folder);
    }

    /**
     * Analyses the documents of a folder of the server file system before their import: the files of the same name in
     * different formats, where each comes from, the SOP data read from them, the users named in them, and the version
     * recommended for the import. Nothing is created. Meant to be called from a job (e.g. the job macro), which
     * displays the progress and the log of each document.
     *
     * @param folder the absolute path of the folder
     * @param fileNames the files to analyse, by path relative to the folder, e.g. "BB/AA/Document.docx";
     *     {@code null} for all the files of the folder and of its sub-folders
     * @param acceptedTypes the extensions of the files to analyse, separated by commas (e.g. "docx, doc, pdf")
     * @param analysisId the identifier under which the analysis is kept, e.g. the identifier of the job analysing the
     *     documents
     * @return the analysis of the folder
     * @see SOPImportManager#analyseDocuments(String, List, String, String)
     */
    public SOPImportAnalysis analyseDocuments(String folder, List<String> fileNames, String acceptedTypes,
        String analysisId)
    {
        return this.importManager.analyseDocuments(folder, fileNames, acceptedTypes, analysisId);
    }

    /**
     * @param analysisId the identifier given to {@link #analyseDocuments(String, String, int, String)}
     * @return the analysis, {@code null} if it's not one of the last analyses since the server started, or the current
     *     user can't import documents
     */
    public SOPImportAnalysis getAnalysis(String analysisId)
    {
        return this.importManager.getAnalysis(analysisId);
    }

    /**
     * Previews the import of a file, without creating anything.
     *
     * @param folder the absolute path of the imported folder
     * @param fileName the path of the file relative to the folder, e.g. "BB/AA/Document.docx"
     * @return the preview, {@code null} if the current user can't import documents, or the file is not a document of
     *     the folder
     * @see SOPImportManager#previewDocument(String, String)
     */
    public SOPImportPreview previewDocument(String folder, String fileName)
    {
        return this.importManager.previewDocument(folder, fileName);
    }

    /**
     * Imports files of a folder of the server file system as SOP documents, with the status "imported". Meant to be
     * called from a job (e.g. the job macro), which displays the progress and the log of each file.
     *
     * @param folder the absolute path of the imported folder
     * @param fileNames the paths of the files to import relative to the folder, e.g. the versions selected after the
     *     analysis of the folder
     * @param users the users selected for each file, by file name and role ("owner", "approver", "reviewer"): the
     *     references of the users, e.g. "XWiki.SStaar"
     * @param reportId the identifier under which the report is kept, e.g. the identifier of the job importing the
     *     documents
     * @return the report of the import
     * @see SOPImportManager#importDocuments(String, List, Map, String)
     */
    public SOPImportReport importDocuments(String folder, List<String> fileNames,
        Map<String, Map<String, List<String>>> users, String reportId)
    {
        return this.importManager.importDocuments(folder, fileNames, users, reportId);
    }

    /**
     * @param reportId the identifier given to {@link #importDocuments(String, List, Map, String)}
     * @return the report of this import, {@code null} if it's not one of the last imports since the server started, or
     *     the current user can't import documents
     */
    public SOPImportReport getReport(String reportId)
    {
        return this.importManager.getReport(reportId);
    }
}
