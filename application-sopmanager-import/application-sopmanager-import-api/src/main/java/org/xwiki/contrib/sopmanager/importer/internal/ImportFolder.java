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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;

/**
 * The folder of the server file system whose documents are imported, its sub-folders and their files. The files and
 * the sub-folders are named by their path relative to the imported folder, separated by
 * {@link SOPImportFolder#SEPARATOR}, e.g. "BB/AA/Document.docx".
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

    private static final String SEPARATOR = SOPImportFolder.SEPARATOR;

    /**
     * The maximum depth of the sub-folders, against the loops of the file system.
     */
    private static final int MAX_DEPTH = 32;

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
            return directory.isAbsolute() && Files.isDirectory(directory) ? directory.normalize() : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * @param directory the imported folder
     * @return the imported folder and its sub-folders with their files, without the hidden folders (e.g. ".git"), and
     *     the version of each document likely to be recommended for the import
     * @throws IOException if a folder can't be read
     */
    public static SOPImportFolder listFolders(Path directory) throws IOException
    {
        Path name = directory.getFileName();
        return listFolders(directory, name == null ? directory.toString() : name.toString(), "", 0);
    }

    private static SOPImportFolder listFolders(Path folder, String name, String path, int depth) throws IOException
    {
        SOPImportFolder result = new SOPImportFolder(name, path);
        Map<String, List<Path>> documents = new LinkedHashMap<>();
        for (Path file : listFiles(folder)) {
            String fileName = file.getFileName().toString();
            result.getFiles().add(fileName);
            documents.computeIfAbsent(getBaseName(fileName), baseName -> new ArrayList<>()).add(file);
        }
        for (List<Path> versions : documents.values()) {
            Path recommended = versions.size() > 1 ? ImportVersions.guessRecommended(versions) : null;
            if (recommended != null) {
                result.getRecommendedFiles().add(recommended.getFileName().toString());
            }
        }
        if (depth < MAX_DEPTH) {
            for (Path child : listSubfolders(folder)) {
                String childName = child.getFileName().toString();
                result.getChildren().add(listFolders(child, childName,
                    path.isEmpty() ? childName : path + SEPARATOR + childName, depth + 1));
            }
        }
        return result;
    }

    /**
     * @param folder a folder listed by {@link #listFolders(Path)}
     * @return the paths of the folder and of all its sub-folders, parents first
     */
    public static List<String> getFolderPaths(SOPImportFolder folder)
    {
        List<String> paths = new ArrayList<>();
        paths.add(folder.getPath());
        for (SOPImportFolder child : folder.getChildren()) {
            paths.addAll(getFolderPaths(child));
        }
        return paths;
    }

    /**
     * @param folder a folder listed by {@link #listFolders(Path)}
     * @return the paths of the files of the folder and of all its sub-folders, relative to the imported folder; the
     *     files of each folder before the ones of its sub-folders
     */
    public static List<String> getFilePaths(SOPImportFolder folder)
    {
        List<String> paths = new ArrayList<>();
        for (String file : folder.getFiles()) {
            paths.add(folder.getPath().isEmpty() ? file : folder.getPath() + SEPARATOR + file);
        }
        for (SOPImportFolder child : folder.getChildren()) {
            paths.addAll(getFilePaths(child));
        }
        return paths;
    }

    private static List<Path> listSubfolders(Path folder) throws IOException
    {
        try (Stream<Path> list = Files.list(folder)) {
            // The symbolic links are not followed, against the loops of the file system.
            return list.filter(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                && !child.getFileName().toString().startsWith(DOT)).sorted().toList();
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
     * @param directory the imported folder
     * @param path the path of one of its sub-folders, e.g. sent by the browser; empty for the imported folder
     * @return the sub-folder, {@code null} if it is not a folder inside the imported folder
     */
    public static Path getFolder(Path directory, String path)
    {
        if (path != null && path.isEmpty()) {
            return directory;
        }
        Path folder = resolve(directory, path);
        return folder != null && Files.isDirectory(folder) ? folder : null;
    }

    /**
     * @param directory the imported folder
     * @param fileName the path of a file relative to the imported folder, e.g. sent by the browser
     * @return the file, {@code null} if it is not a file inside the imported folder (e.g. a path to another folder)
     */
    public static Path getFile(Path directory, String fileName)
    {
        Path file = resolve(directory, fileName);
        return file != null && Files.isRegularFile(file) ? file : null;
    }

    /**
     * @return the file or folder, {@code null} if the path is not a plain relative path (e.g. with "..", which would
     *     name a page after it) or leaves the imported folder
     */
    private static Path resolve(Path directory, String path)
    {
        if (StringUtils.isBlank(path) || StringUtils.containsAny(path, '\\', '\0')
            || Stream.of(path.split(SEPARATOR, -1)).anyMatch(name -> name.isEmpty() || DOT.equals(name)
                || "..".equals(name))) {
            return null;
        }
        try {
            Path resolved = directory.resolve(path).normalize();
            return resolved.startsWith(directory) && !resolved.equals(directory) ? resolved : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * @param directory the imported folder
     * @param file a file or a sub-folder of the imported folder
     * @return its path relative to the imported folder, e.g. "BB/AA/Document.docx"
     */
    public static String getPath(Path directory, Path file)
    {
        Path relative = directory.relativize(file);
        return Stream.iterate(0, i -> i + 1).limit(relative.getNameCount())
            .map(i -> relative.getName(i).toString()).collect(Collectors.joining(SEPARATOR));
    }

    /**
     * @param path the path of a file relative to the imported folder, e.g. "BB/AA/Document.docx"
     * @return the name of the file, without its folders, e.g. "Document.docx"
     */
    public static String getName(String path)
    {
        return path.contains(SEPARATOR) ? StringUtils.substringAfterLast(path, SEPARATOR) : path;
    }

    /**
     * @param path the path of a file relative to the imported folder, e.g. "BB/AA/Document.docx"
     * @return the path of its folder, e.g. "BB/AA"; empty for a file of the imported folder
     */
    public static String getParent(String path)
    {
        return path.contains(SEPARATOR) ? StringUtils.substringBeforeLast(path, SEPARATOR) : "";
    }

    /**
     * @param fileName the path of a file relative to the imported folder, e.g. "BB/AA/Document.docx"
     * @return the path of the file without its extension, e.g. "BB/AA/Document": the name of the document, shared by
     *     its versions
     */
    public static String getBaseName(String fileName)
    {
        String name = getName(fileName);
        String baseName = (name.contains(DOT) ? StringUtils.substringBeforeLast(name, DOT) : name).trim();
        String parent = getParent(fileName);
        return parent.isEmpty() ? baseName : parent + SEPARATOR + baseName;
    }

    /**
     * @param fileName the path of a file relative to the imported folder
     * @return whether the file is the lock file of a document open in Word
     */
    public static boolean isLockFile(String fileName)
    {
        return getName(fileName).startsWith(LOCK_FILE_PREFIX);
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
