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

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.contrib.sopmanager.SOPManager;

/**
 * Imports the Word documents of a folder of the server file system as SOP documents.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface SOPImportManager
{
    /**
     * The status of the imported SOP documents, before they are added to the review process.
     */
    String IMPORTED = SOPManager.IMPORTED;

    /**
     * The role of the owner of a SOP document: who last modified the imported document.
     */
    String OWNER = "owner";

    /**
     * The role of the approver of a SOP document: who approved the imported document ("Freigabe durch").
     */
    String APPROVER = "approver";

    /**
     * The role of the reviewer of a SOP document: who revised the imported document ("Überarbeitet von").
     */
    String REVIEWER = "reviewer";

    /**
     * @return {@code true} if the current user can import documents: the administrators of the current wiki and the
     *     members of its SOP Editors group ({@code XWiki.SOPEditorsGroup}), which holds the SOP
     *     Administrators group
     */
    boolean canImport();

    /**
     * Lists the sub-folders and the files of a folder of the server file system, to choose the files to import: the
     * documents of each folder are imported below the page of the folder, e.g. "BB/AA/Document.docx" as
     * SOPManager.BB.AA.Document.WebHome.
     *
     * @param folder the absolute path of the folder
     * @return the folder and its sub-folders with their files, {@code null} if the current user can't import
     *     documents, or the folder doesn't exist or can't be read
     */
    SOPImportFolder getFolders(String folder);

    /**
     * Analyses the documents of a folder of the server file system before their import: groups the files of the same
     * name in different formats in the same folder (e.g. A.doc, A.docx and A.pdf, an original and its conversions),
     * reads each of them (where it comes from, its SOP data, its compatibility), and recommends the version to import,
     * the one keeping the most of the content and of its formatting. The recommended version of each document that can
     * be imported is selected. Nothing is created. The progress and the result of each document are logged, so this
     * is meant to be called from a job. The files are named by their path relative to the folder, e.g.
     * "BB/AA/Document.docx".
     *
     * @param folder the absolute path of the folder
     * @param fileNames the files to analyse, by path relative to the folder, e.g. "BB/AA/Document.docx" (see
     *     {@link #getFolders(String)}); {@code null} to analyse all the files of the folder and of its sub-folders
     * @param acceptedTypes the extensions of the files to analyse, separated by commas or spaces (e.g. "docx, doc,
     *     pdf")
     * @param analysisId the identifier under which the analysis is kept, to be retrieved with
     *     {@link #getAnalysis(String)}; can be {@code null}
     * @return the analysis of the folder
     */
    SOPImportAnalysis analyseDocuments(String folder, List<String> fileNames, String acceptedTypes,
        String analysisId);

    /**
     * @param analysisId the identifier given to {@link #analyseDocuments(String, String, int, String)}
     * @return this analysis, if it's one of the last analyses since the server was started, {@code null} otherwise
     */
    SOPImportAnalysis getAnalysis(String analysisId);

    /**
     * Previews the import of a file: converts it as it would be imported, without creating anything.
     *
     * @param folder the absolute path of the imported folder
     * @param fileName the path of the file relative to the folder, e.g. "BB/AA/Document.docx"
     * @return the preview of the SOP document, or {@code null} if the current user can't import documents, or the
     *     file is not a document of the folder
     */
    SOPImportPreview previewDocument(String folder, String fileName);

    /**
     * Imports files of a folder of the server file system as SOP documents, created in the SOP Manager space with the
     * status {@link #IMPORTED} and outside of the review process. The files of the sub-folders are imported below the
     * pages of their folders, which are created if needed. The details of the page header and footer of the documents
     * (code, title, release details) are stored in the SOP object, and removed from the content. Existing pages are
     * left untouched: only one file of the same name in the same folder is imported. The progress and the result of
     * each file are logged, so this is meant to be called from a job.
     *
     * @param folder the absolute path of the imported folder
     * @param fileNames the paths of the files to import relative to the folder, e.g. the versions selected after the
     *     analysis of the folder
     * @param users the users selected for each document, by name of the document (the path of its files without
     *     extension, see {@link SOPImportCandidate#getName()}) and by role
     *     ({@link #OWNER}, {@link #APPROVER}, {@link #REVIEWER}): the references of the users, e.g. "XWiki.SStaar",
     *     possibly separated by commas; a document has a single owner, but can have several approvers and reviewers
     * @param reportId the identifier under which the report is kept, to be retrieved with {@link #getReport(String)};
     *     can be {@code null}
     * @return the report of the import
     */
    SOPImportReport importDocuments(String folder, List<String> fileNames, Map<String, Map<String, List<String>>> users,
        String reportId);

    /**
     * @param reportId the identifier given to {@link #importDocuments(String, List, Map, String)}
     * @return the report of this import, if it's one of the last imports since the server was started, {@code null}
     *     otherwise
     */
    SOPImportReport getReport(String reportId);
}
