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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;
import org.xwiki.contrib.sopmanager.importer.SOPImportPreview;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Outcome;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.logging.marker.TranslationMarker;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;

/**
 * Reads the files to import, and analyses the documents they are versions of.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportDocumentAnalyser.class)
@Singleton
public class ImportDocumentAnalyser
{
    private static final String LOG_PREFIX = "sopManager.import.log.analysis.";

    private static final String UNREADABLE = "sopManager.import.message.unreadable";

    private static final String FAILED_TO_READ = "Failed to read [{}].";

    @Inject
    private Logger logger;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    private SOPDocumentCreator documentCreator;

    @Inject
    private DocumentReader documentReader;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ImportPreviewRenderer previewRenderer;

    @Inject
    private ImportUserResolver userResolver;

    /**
     * A file read for its import: its description, and the document read from it.
     *
     * @param version the description of the file
     * @param document the document read from the file, {@code null} if it can't be imported
     */
    public record ReadVersion(SOPImportVersion version, ImportedDocument document)
    {
    }

    /**
     * @return the users of the wiki, who may be named in the documents to import
     */
    public List<ImportUserResolver.User> getUsers()
    {
        return this.userResolver.getUsers();
    }

    /**
     * Analyses a document to import and logs the result.
     *
     * @param name the name of the document: the path of its files relative to the imported folder, without
     *     extension, e.g. "BB/AA/Document"
     * @param files its files, in different formats, all in the same folder
     * @param users the users of the wiki, who may be named in the document
     * @param context the XWiki context
     * @return the document, its versions compared, the persons named in its recommended version
     */
    public SOPImportCandidate analyse(String name, List<Path> files, List<ImportUserResolver.User> users,
        XWikiContext context)
    {
        DocumentReference reference = this.documentCreator.getReference(name, context.getWikiId());
        boolean exists;
        try {
            exists = context.getWiki().exists(reference, context);
        } catch (Exception e) {
            // Not imported if it can't be checked.
            exists = true;
        }
        SOPImportCandidate candidate = new SOPImportCandidate(name, reference, exists);
        Map<SOPImportVersion, ImportedDocument> documents = new HashMap<>();
        String folder = ImportFolder.getParent(name);
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            if (!folder.isEmpty()) {
                fileName = folder + SOPImportFolder.SEPARATOR + fileName;
            }
            ReadVersion version = readVersion(file, fileName);
            if (version.document() != null) {
                documents.put(version.version(), version.document());
            }
            candidate.getVersions().add(version.version());
        }
        ImportVersions.compare(candidate, documents);
        ImportPeople.describe(candidate, documents, users, this.userResolver);
        log(candidate);
        return candidate;
    }

    /**
     * Reads a file, and describes it.
     *
     * @param file a file to import
     * @return the description of the file, and the document read from it
     */
    public ReadVersion read(Path file)
    {
        return readVersion(file, file.getFileName().toString());
    }

    /**
     * Reads a file, and describes it.
     *
     * @param file a file to import
     * @param fileName the path of the file relative to the imported folder, naming its version
     * @return the description of the file, and the document read from it
     */
    public ReadVersion readVersion(Path file, String fileName)
    {
        SOPImportVersion version = new SOPImportVersion(fileName, DocumentReader.getExtension(file));
        try {
            ImportedDocument document = this.documentReader.read(file);
            ImportVersionDescription.describe(version, document);
            for (ImportWarning warning : document.warnings()) {
                version.addWarning(translate(warning));
            }
            return new ReadVersion(version, document);
        } catch (UnsupportedDocumentException e) {
            version.setCompatibility(Compatibility.NOT_SUPPORTED);
            version.setMessage(translate(e.getReason()));
        } catch (DocumentReadException | RuntimeException e) {
            this.logger.debug(FAILED_TO_READ, file, e);
            version.setCompatibility(Compatibility.INVALID);
            version.setMessage(translate(new ImportWarning(UNREADABLE, getMessage(e))));
        }
        return new ReadVersion(version, null);
    }

    /**
     * Previews the import of a file.
     *
     * @param file a file to import
     * @return the SOP document it would be imported as: its title, its content with its images, the header cells of
     *     its tables marked as in the other versions of the document
     */
    public SOPImportPreview preview(Path file)
    {
        return preview(file, file.getFileName().toString());
    }

    /**
     * Previews the import of a file.
     *
     * @param file a file to import
     * @param fileName the path of the file relative to the imported folder, naming its version
     * @return the SOP document it would be imported as
     * @see #preview(Path)
     */
    public SOPImportPreview preview(Path file, String fileName)
    {
        ReadVersion version = readVersion(file, fileName);
        ImportedDocument document = version.document();
        if (document != null) {
            shareTableHeaders(file, document);
        }
        String title = SOPDocumentCreator.getTitle(ImportFolder.getBaseName(file.getFileName().toString()),
            document == null ? new SOPDocumentDetails() : document.details());
        return new SOPImportPreview(version.version(), title,
            document == null ? "" : this.previewRenderer.render(document));
    }

    /**
     * Reads a file to import it.
     *
     * @param file a file to import
     * @param result the result of the import of the file, telling why it can't be imported
     * @return the document read from the file, the header cells of its tables marked as in the other versions of the
     *     document; {@code null} if it can't be imported
     */
    public ImportedDocument read(Path file, SOPImportResult result)
    {
        try {
            ImportedDocument document = this.documentReader.read(file);
            result.setCompatibility(document.compatibility());
            shareTableHeaders(file, document);
            return document;
        } catch (UnsupportedDocumentException e) {
            result.setCompatibility(Compatibility.NOT_SUPPORTED);
            result.setOutcome(Outcome.SKIPPED_FORMAT);
            result.setMessage(translate(e.getReason()));
        } catch (DocumentReadException | RuntimeException e) {
            this.logger.debug(FAILED_TO_READ, file, e);
            result.setCompatibility(Compatibility.INVALID);
            result.setOutcome(Outcome.FAILED);
            result.setMessage(translate(new ImportWarning(UNREADABLE, getMessage(e))));
        }
        return null;
    }

    /**
     * Marks the header cells of the tables of a document as in the other versions of the document, the files of the
     * folder with the same name in other formats: e.g. the cells of a PDF that are shaded in the Word document it was
     * exported from.
     */
    private void shareTableHeaders(Path file, ImportedDocument document)
    {
        Set<String> headers = new HashSet<>();
        for (Path otherVersion : getOtherVersions(file)) {
            try {
                headers.addAll(TableHeaders.collect(this.documentReader.read(otherVersion).content()));
            } catch (DocumentReadException | RuntimeException e) {
                // A version that can't be read tells no header cell.
                this.logger.debug(FAILED_TO_READ, otherVersion, e);
            }
        }
        TableHeaders.apply(document.content(), headers);
    }

    private List<Path> getOtherVersions(Path file)
    {
        String fileName = file.getFileName().toString();
        String baseName = ImportFolder.getBaseName(fileName);
        try {
            return ImportFolder.listFiles(file.getParent()).stream().filter(other -> {
                String otherName = other.getFileName().toString();
                return !otherName.equals(fileName) && baseName.equals(ImportFolder.getBaseName(otherName))
                    && !otherName.startsWith(ImportFolder.LOCK_FILE_PREFIX)
                    && DocumentReader.FORMATS.contains(DocumentReader.getExtension(other));
            }).toList();
        } catch (IOException | SecurityException e) {
            this.logger.debug("Failed to list the other versions of [{}].", file, e);
            return List.of();
        }
    }

    /**
     * @param e an error
     * @return its message, else its type
     */
    static String getMessage(Exception e)
    {
        return StringUtils.defaultIfEmpty(e.getMessage(), e.getClass().getSimpleName());
    }

    /**
     * @param message a message
     * @return the message translated, else its key
     */
    public String translate(ImportWarning message)
    {
        return StringUtils.defaultIfEmpty(this.localizationManager.getTranslationPlain(message.key(),
            message.parameters().toArray()), message.key());
    }

    private void log(SOPImportCandidate candidate)
    {
        List<String> versions = new ArrayList<>();
        for (SOPImportVersion version : candidate.getVersions()) {
            String description = version.isImportable()
                ? translate(new ImportWarning("sopManager.import.origin." + version.getOrigin()))
                : version.getMessage();
            versions.add(String.format("%s (%s)", version.getFileName(), description));
        }
        String files = String.join(", ", versions);
        if (candidate.isExists()) {
            this.logger.warn(new TranslationMarker(LOG_PREFIX + "exists"),
                "[{}]: {}: the page [{}] already exists, the document will not be imported.", candidate.getName(),
                files, this.serializer.serialize(candidate.getDocumentReference()));
        } else if (candidate.getRecommended() == null) {
            this.logger.warn(new TranslationMarker(LOG_PREFIX + "notImportable"),
                "[{}]: {}: no file can be imported.", candidate.getName(), files);
        } else {
            this.logger.info(new TranslationMarker(LOG_PREFIX + "document"),
                "[{}]: {}: recommended version: [{}].", candidate.getName(), files,
                candidate.getRecommended().getFileName());
        }
    }
}
