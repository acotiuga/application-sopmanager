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
 * A folder of the documents to import, with its sub-folders: the documents of each folder are imported as the child
 * pages of the page of the folder, so that the SOP documents keep the tree of the folders.
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPImportFolder
{
    /**
     * The separator of the folders in the path of a folder or of a file, relative to the imported folder.
     */
    public static final String SEPARATOR = "/";

    private final String name;

    private final String path;

    private final List<String> files = new ArrayList<>();

    private final List<String> recommendedFiles = new ArrayList<>();

    private final List<SOPImportFolder> children = new ArrayList<>();

    /**
     * @param name the name of the folder
     * @param path the path of the folder relative to the imported folder, separated by {@link #SEPARATOR}; empty for
     *     the imported folder
     */
    public SOPImportFolder(String name, String path)
    {
        this.name = name;
        this.path = path;
    }

    /**
     * @return the name of the folder
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the path of the folder relative to the imported folder, e.g. "BB/AA"; empty for the imported folder
     */
    public String getPath()
    {
        return this.path;
    }

    /**
     * @return the names of the files of the folder, not of its sub-folders, sorted
     */
    public List<String> getFiles()
    {
        return this.files;
    }

    /**
     * @return the names of the files of the folder that are likely to be the version recommended for the import of
     *     their document, among its files with the same name in different formats (e.g. "A.pdf" rather than "A.docx"
     *     when the Word document was converted from the PDF); none for the documents having a single file
     */
    public List<String> getRecommendedFiles()
    {
        return this.recommendedFiles;
    }

    /**
     * @return the number of files of the folder, not counting the files of its sub-folders
     */
    public int getFileCount()
    {
        return this.files.size();
    }

    /**
     * @return the sub-folders, sorted by name
     */
    public List<SOPImportFolder> getChildren()
    {
        return this.children;
    }
}
