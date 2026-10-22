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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        // Only the files of the folder.
        assertNull(ImportFolder.getFile(this.folder, "Sub"));
        assertNull(ImportFolder.getFile(this.folder, "Sub/B.docx"));
        assertNull(ImportFolder.getFile(subfolder, "../A.docx"));
        assertNull(ImportFolder.getFile(subfolder, ".."));
        assertNull(ImportFolder.getFile(this.folder, "..\\A.docx"));
        assertNull(ImportFolder.getFile(this.folder, "A\0.docx"));
        assertNull(ImportFolder.getFile(this.folder, " "));
        assertNull(ImportFolder.getFile(this.folder, null));
        assertNull(ImportFolder.getFile(this.folder, "C.docx"));
        assertEquals(List.of(file), ImportFolder.listFiles(this.folder));
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
    }

    @Test
    void parseTypes()
    {
        assertEquals(Set.of("docx"), ImportFolder.parseTypes(" , "));
        assertEquals(List.of("docx", "pdf"), List.copyOf(ImportFolder.parseTypes(".docx pdf;PDF")));
    }
}
