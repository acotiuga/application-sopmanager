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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

/**
 * The folder of the server file system whose documents are imported, and its files.
 *
 * @version $Id$
 * @since 1.0
 */
public final class ImportFolder
{
    /**
     * The prefix of the lock files of the documents open in Word.
     */
    public static final String LOCK_FILE_PREFIX = "~$";

    private static final String DOT = ".";

    private ImportFolder()
    {
    }

    /**
     * @param folder the absolute path of a folder
     * @return the folder, {@code null} if the path is not absolute or is not an existing folder
     */
    public static Path getDirectory(String folder)
    {
        try {
            Path directory = Paths.get(folder);
            return directory.isAbsolute() && Files.isDirectory(directory) ? directory : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * @param directory a folder
     * @return the files of the folder, not of its sub-folders, sorted by name
     * @throws IOException if the folder can't be read
     */
    public static List<Path> listFiles(Path directory) throws IOException
    {
        try (Stream<Path> list = Files.list(directory)) {
            return list.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /**
     * @param directory a folder
     * @param fileName the name of a file, e.g. sent by the browser
     * @return the file, {@code null} if it is not a file of the folder (e.g. a path to another folder)
     */
    public static Path getFile(Path directory, String fileName)
    {
        if (StringUtils.isBlank(fileName) || StringUtils.containsAny(fileName, '/', '\\', '\0')) {
            return null;
        }
        try {
            Path file = directory.resolve(fileName).normalize();
            return directory.equals(file.getParent()) && Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * @param fileName the name of a file
     * @return the name of the file without its extension: the name of the document, shared by its versions
     */
    public static String getBaseName(String fileName)
    {
        return (fileName.contains(DOT) ? StringUtils.substringBeforeLast(fileName, DOT) : fileName).trim();
    }

    /**
     * @param acceptedTypes the extensions of the files to import, separated by commas, semicolons or spaces
     * @return the extensions, in lower case and without dot; "docx" if none is given
     */
    public static Set<String> parseTypes(String acceptedTypes)
    {
        Set<String> types = new LinkedHashSet<>();
        for (String type : StringUtils.defaultString(acceptedTypes).split("[,;\\s]+")) {
            String extension = StringUtils.stripStart(type.trim(), DOT).toLowerCase(Locale.ROOT);
            if (!extension.isEmpty()) {
                types.add(extension);
            }
        }
        if (types.isEmpty()) {
            types.add(DocumentReader.DOCX);
        }
        return types;
    }
}
