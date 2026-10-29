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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ImportFolder}.
 *
 * @version $Id$
 */
class ImportFolderTest
{
    @TempDir
    private Path folder;

    @Test
    void getFile() throws Exception
    {
        Path file = Files.writeString(this.folder.resolve("A.docx"), "A");
        Path subfolder = Files.createDirectory(this.folder.resolve("Sub"));
        Files.writeString(subfolder.resolve("B.docx"), "B");

        assertEquals(file, ImportFolder.getFile(this.folder, "A.docx"));
        assertEquals(subfolder.resolve("B.docx"), ImportFolder.getFile(this.folder, "Sub/B.docx"));
        // Only the files inside the folder, by plain relative path.
        assertNull(ImportFolder.getFile(this.folder, "Sub"));
        assertNull(ImportFolder.getFile(subfolder, "../A.docx"));
        assertNull(ImportFolder.getFile(this.folder, "Sub/../A.docx"));
        assertNull(ImportFolder.getFile(this.folder, "./A.docx"));
        assertNull(ImportFolder.getFile(this.folder, "Sub//B.docx"));
        assertNull(ImportFolder.getFile(this.folder, this.folder.resolve("A.docx").toString()));
        assertNull(ImportFolder.getFile(subfolder, ".."));
        assertNull(ImportFolder.getFile(this.folder, "..\\A.docx"));
        assertNull(ImportFolder.getFile(this.folder, "A\0.docx"));
        assertNull(ImportFolder.getFile(this.folder, " "));
        assertNull(ImportFolder.getFile(this.folder, null));
        assertNull(ImportFolder.getFile(this.folder, "C.docx"));
        assertEquals(List.of(file), ImportFolder.listFiles(this.folder));
    }

    @Test
    void getFolder() throws Exception
    {
        Path subfolder = Files.createDirectories(this.folder.resolve("BB/AA"));
        Files.writeString(subfolder.resolve("A.docx"), "A");

        assertEquals(this.folder, ImportFolder.getFolder(this.folder, ""));
        assertEquals(subfolder, ImportFolder.getFolder(this.folder, "BB/AA"));
        assertEquals("BB/AA/A.docx", ImportFolder.getPath(this.folder, subfolder.resolve("A.docx")));
        assertNull(ImportFolder.getFolder(this.folder, "BB/AA/A.docx"));
        assertNull(ImportFolder.getFolder(this.folder, ".."));
        assertNull(ImportFolder.getFolder(this.folder, "BB/.."));
        assertNull(ImportFolder.getFolder(this.folder, null));
    }

    @Test
    void listFolders() throws Exception
    {
        Files.createDirectories(this.folder.resolve("BB/CC/AA"));
        Files.createDirectories(this.folder.resolve("BB/AA"));
        Files.createDirectories(this.folder.resolve(".git/objects"));
        Files.writeString(this.folder.resolve("BB/AA/A.docx"), "A");
        Files.writeString(this.folder.resolve("BB/AA/A.pdf"), "A");

        SOPImportFolder root = ImportFolder.listFolders(this.folder);

        assertEquals(this.folder.getFileName().toString(), root.getName());
        assertEquals("", root.getPath());
        // Sorted by name, without the hidden folders.
        assertEquals(List.of("", "BB", "BB/AA", "BB/CC", "BB/CC/AA"), ImportFolder.getFolderPaths(root));
        assertEquals(List.of("A.docx", "A.pdf"), root.getChildren().get(0).getChildren().get(0).getFiles());
        assertEquals(2, root.getChildren().get(0).getChildren().get(0).getFileCount());
        assertEquals(List.of("BB/AA/A.docx", "BB/AA/A.pdf"), ImportFolder.getFilePaths(root));
        // The Word document can't be read: the PDF is the version to import.
        assertEquals(List.of("A.pdf"), root.getChildren().get(0).getChildren().get(0).getRecommendedFiles());
        assertEquals(List.of(), root.getRecommendedFiles());
    }

    @Test
    void getDirectory()
    {
        assertEquals(this.folder, ImportFolder.getDirectory(this.folder.toString()));
        assertNull(ImportFolder.getDirectory("relative"));
        assertNull(ImportFolder.getDirectory("/a\0b"));
    }

    @Test
    void getBaseName()
    {
        assertEquals("ANA_AA_7.212_Wagen", ImportFolder.getBaseName("ANA_AA_7.212_Wagen.docx"));
        assertEquals("Notes", ImportFolder.getBaseName("Notes"));
        assertEquals("BB/AA/ANA_AA_7.212_Wagen", ImportFolder.getBaseName("BB/AA/ANA_AA_7.212_Wagen.docx"));
        assertEquals("BB.2/Notes", ImportFolder.getBaseName("BB.2/Notes"));
        assertEquals("Wagen.docx", ImportFolder.getName("BB/AA/Wagen.docx"));
        assertEquals("BB/AA", ImportFolder.getParent("BB/AA/Wagen.docx"));
        assertEquals("", ImportFolder.getParent("Wagen.docx"));
        assertTrue(ImportFolder.isLockFile("BB/~$Wagen.docx"));
        assertFalse(ImportFolder.isLockFile("~$BB/Wagen.docx"));
    }

    @Test
    void parseTypes()
    {
        assertEquals(Set.of("docx"), ImportFolder.parseTypes(" , "));
        assertEquals(List.of("docx", "pdf"), List.copyOf(ImportFolder.parseTypes(".docx pdf;PDF")));
    }
}
