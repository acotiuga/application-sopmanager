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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.importer.SOPImportAnalysis;
import org.xwiki.contrib.sopmanager.importer.SOPImportCandidate;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportPreview;
import org.xwiki.contrib.sopmanager.importer.SOPImportReport;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Outcome;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.logging.marker.TranslationMarker;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;

/**
 * Default implementation of {@link SOPImportManager}: analyses and imports the Word documents (docx), Word 97-2003
 * documents (doc) and tagged PDF documents of a folder.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultSOPImportManager implements SOPImportManager
{
    private static final int MAX_KEPT = 20;

    private static final String LOG_PREFIX = "sopManager.import.log.";

    private static final String MESSAGE_PREFIX = "sopManager.import.message.";

    private static final String COMMA = ", ";

    private static final String NONE = "-";

    private static final String NOT_ALLOWED = "notAllowed";

    private static final String FAILED = "failed";

    private static final String LOCK_FILE = "lockFile";

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ImportRights rights;

    @Inject
    private JobProgressManager progressManager;

    @Inject
    private SOPDocumentCreator documentCreator;

    @Inject
    private ImportDocumentAnalyser analyser;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    private final Map<String, SOPImportReport> reports = newRecentMap();

    private final Map<String, SOPImportAnalysis> analyses = newRecentMap();

    private static <V> Map<String, V> newRecentMap()
    {
        return Collections.synchronizedMap(new LinkedHashMap<String, V>()
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest)
            {
                return size() > MAX_KEPT;
            }
        });
    }

    @Override
    public SOPImportAnalysis analyseDocuments(String folder, String acceptedTypes, int maxDocuments,
        String analysisId)
    {
        SOPImportAnalysis analysis = new SOPImportAnalysis(StringUtils.trimToEmpty(folder));
        if (analysisId != null) {
            this.analyses.put(analysisId, analysis);
        }
        XWikiContext context = this.xcontextProvider.get();
        List<Path> files = getFiles(analysis.getFolder(), context, analysis::setError);
        if (files == null) {
            return analysis;
        }
        Set<String> types = ImportFolder.parseTypes(acceptedTypes);
        this.logger.info(new TranslationMarker(LOG_PREFIX + "analysis.start"), "Analysing the {} files of the folder"
            + " [{}] (accepted types: {}).", files.size(), analysis.getFolder(), String.join(COMMA, types));
        Map<String, List<Path>> documents = new LinkedHashMap<>();
        for (Path file : files) {
            SOPImportResult skipped = checkFile(file, types);
            if (skipped == null) {
                documents.computeIfAbsent(ImportFolder.getBaseName(file.getFileName().toString()),
                    name -> new ArrayList<>()).add(file);
            } else {
                analysis.getOtherFiles().add(skipped);
                log(skipped);
            }
        }
        List<ImportUserResolver.User> users = this.analyser.getUsers();
        this.progressManager.pushLevelProgress(documents.size(), this);
        try {
            for (Map.Entry<String, List<Path>> document : documents.entrySet()) {
                this.progressManager.startStep(this);
                analysis.getDocuments().add(
                    this.analyser.analyse(document.getKey(), document.getValue(), users, context));
                this.progressManager.endStep(this);
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }
        select(analysis, maxDocuments);
        return analysis;
    }

    @Override
    public SOPImportAnalysis getAnalysis(String analysisId)
    {
        return analysisId == null || !canImport(this.xcontextProvider.get()) ? null : this.analyses.get(analysisId);
    }

    @Override
    public SOPImportPreview previewDocument(String folder, String fileName)
    {
        XWikiContext context = this.xcontextProvider.get();
        Path directory = canImport(context) ? ImportFolder.getDirectory(StringUtils.trimToEmpty(folder)) : null;
        Path file = directory == null ? null : ImportFolder.getFile(directory, fileName);
        if (file == null || fileName.startsWith(ImportFolder.LOCK_FILE_PREFIX)) {
            return null;
        }
        return this.analyser.preview(file);
    }

    @Override
    public SOPImportReport importDocuments(String folder, List<String> fileNames,
        Map<String, Map<String, List<String>>> users, String reportId)
    {
        SOPImportReport report = new SOPImportReport(StringUtils.trimToEmpty(folder));
        if (reportId != null) {
            this.reports.put(reportId, report);
        }
        XWikiContext context = this.xcontextProvider.get();
        if (getFiles(report.getFolder(), context, report::setError) == null) {
            return report;
        }
        Path directory = ImportFolder.getDirectory(report.getFolder());
        // Each file once, in the order of the selection.
        List<String> names = fileNames == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(fileNames));
        this.logger.info(new TranslationMarker(LOG_PREFIX + "start"),
            "Importing the {} selected files of the folder [{}].", names.size(), report.getFolder());
        this.progressManager.pushLevelProgress(names.size(), this);
        try {
            for (String name : names) {
                this.progressManager.startStep(this);
                Map<String, List<String>> documentUsers = users == null ? Map.of()
                    : users.getOrDefault(ImportFolder.getBaseName(name), Map.of());
                SOPImportResult result = importFile(directory, name, documentUsers, context);
                report.addResult(result);
                log(result);
                this.progressManager.endStep(this);
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }
        logSummary(report);
        return report;
    }

    @Override
    public SOPImportReport getReport(String reportId)
    {
        return reportId == null || !canImport(this.xcontextProvider.get()) ? null : this.reports.get(reportId);
    }

    @Override
    public boolean canImport()
    {
        return canImport(this.xcontextProvider.get());
    }

    private boolean canImport(XWikiContext context)
    {
        return this.rights.canImport(context);
    }

    /**
     * @return the files of the folder, {@code null} if the folder can't be read (the error is given to the consumer)
     */
    private List<Path> getFiles(String folder, XWikiContext context, Consumer<String> error)
    {
        String key;
        Object parameter = folder;
        Path directory = null;
        if (!canImport(context)) {
            key = "notImporter";
            parameter = this.rights.getSOPEditorsGroup(context);
        } else if (folder.isEmpty()) {
            key = "noFolder";
        } else {
            directory = ImportFolder.getDirectory(folder);
            key = directory == null ? "noSuchFolder" : null;
        }
        if (key == null) {
            try {
                return ImportFolder.listFiles(directory);
            } catch (IOException | SecurityException e) {
                key = "unreadableFolder";
            }
        }
        error.accept(translate(MESSAGE_PREFIX + key, parameter));
        this.logger.error(new TranslationMarker(MESSAGE_PREFIX + key), "The import failed: [{}].", parameter);
        return null;
    }

    /**
     * @return the result of a file that is not a document to import, {@code null} if it is one
     */
    private SOPImportResult checkFile(Path file, Set<String> types)
    {
        String fileName = file.getFileName().toString();
        String extension = DocumentReader.getExtension(file);
        SOPImportResult result = new SOPImportResult(fileName);
        if (!types.contains(extension)) {
            skip(result, Compatibility.NOT_ACCEPTED, "notAccepted", extension);
        } else if (fileName.startsWith(ImportFolder.LOCK_FILE_PREFIX)) {
            skip(result, Compatibility.INVALID, LOCK_FILE, fileName);
        } else if (!DocumentReader.FORMATS.contains(extension)) {
            skip(result, Compatibility.NOT_SUPPORTED, "notSupported", extension);
        } else {
            return null;
        }
        return result;
    }

    private void skip(SOPImportResult result, Compatibility compatibility, String key, Object parameter)
    {
        result.setCompatibility(compatibility);
        result.setOutcome(Outcome.SKIPPED_FORMAT);
        result.setMessage(translate(MESSAGE_PREFIX + key, parameter));
    }

    /**
     * Selects the documents to import by default: the recommended version of the documents that are not imported
     * yet, up to the maximum number of documents.
     */
    private void select(SOPImportAnalysis analysis, int maxDocuments)
    {
        int selected = 0;
        int importable = 0;
        for (SOPImportCandidate candidate : analysis.getDocuments()) {
            if (!candidate.isExists() && candidate.getRecommended() != null) {
                importable++;
                candidate.setSelected(maxDocuments <= 0 || selected < maxDocuments);
                selected += candidate.isSelected() ? 1 : 0;
            }
        }
        this.logger.info(new TranslationMarker(LOG_PREFIX + "analysis.summary"),
            "Documents that can be imported: {} of {}, selected: {}.", importable, analysis.getDocuments().size(),
            selected);
    }

    private SOPImportResult importFile(Path directory, String name, Map<String, List<String>> users,
        XWikiContext context)
    {
        SOPImportResult result = new SOPImportResult(name);
        Path file = ImportFolder.getFile(directory, name);
        if (file == null) {
            result.setOutcome(Outcome.FAILED);
            result.setMessage(translate(MESSAGE_PREFIX + "notInFolder", name));
        } else if (name.startsWith(ImportFolder.LOCK_FILE_PREFIX)) {
            skip(result, Compatibility.INVALID, LOCK_FILE, name);
        } else {
            ImportedDocument document = this.analyser.read(file, result);
            if (document != null) {
                create(document, result, users, context);
            }
        }
        return result;
    }

    private void create(ImportedDocument document, SOPImportResult result, Map<String, List<String>> users,
        XWikiContext context)
    {
        String baseName = ImportFolder.getBaseName(result.getFileName());
        DocumentReference reference = this.documentCreator.getReference(baseName, context.getWikiId());
        result.setDocumentReference(reference);
        try {
            if (context.getWiki().exists(reference, context)) {
                result.setOutcome(Outcome.SKIPPED_EXISTING);
                result.setMessage(translate(MESSAGE_PREFIX + "exists", this.serializer.serialize(reference)));
            } else if (!this.rights.canEdit(reference, context)) {
                result.setOutcome(Outcome.FAILED);
                result.setMessage(translate(MESSAGE_PREFIX + NOT_ALLOWED, this.serializer.serialize(reference)));
            } else {
                for (ImportWarning warning : this.documentCreator.create(reference, baseName, document, users,
                    context)) {
                    result.addWarning(this.analyser.translate(warning));
                }
                result.setOutcome(Outcome.IMPORTED);
            }
        } catch (Exception e) {
            this.logger.debug("Failed to import [{}].", result.getFileName(), e);
            result.setOutcome(Outcome.FAILED);
            result.setMessage(translate(MESSAGE_PREFIX + FAILED, ImportDocumentAnalyser.getMessage(e)));
        }
    }

    private String translate(String key, Object... parameters)
    {
        return this.analyser.translate(new ImportWarning(key, parameters));
    }

    private void log(SOPImportResult result)
    {
        String compatibility = result.getCompatibility() == null ? NONE
            : translate("sopManager.import.compatibility." + result.getCompatibility());
        String reference = result.getDocumentReference() == null ? ""
            : this.serializer.serialize(result.getDocumentReference());
        switch (result.getOutcome()) {
            case IMPORTED:
                this.logger.info(new TranslationMarker(LOG_PREFIX + "imported"),
                    "[{}] ({}): imported as [{}].", result.getFileName(), compatibility, reference);
                break;
            case FAILED:
                this.logger.error(new TranslationMarker(LOG_PREFIX + FAILED), "[{}] ({}): failed: {}",
                    result.getFileName(), compatibility, result.getMessage());
                break;
            default:
                this.logger.warn(new TranslationMarker(LOG_PREFIX + "skipped"), "[{}] ({}): not imported: {}",
                    result.getFileName(), compatibility, result.getMessage());
                break;
        }
        for (String warning : result.getWarnings()) {
            this.logger.warn(new TranslationMarker(LOG_PREFIX + "warning"), "[{}]: {}", result.getFileName(),
                warning);
        }
    }

    private void logSummary(SOPImportReport report)
    {
        List<String> imported = new ArrayList<>();
        report.getImported().forEach(result -> imported.add(result.getFileName()));
        List<String> notImported = new ArrayList<>();
        report.getNotImported().forEach(result -> notImported.add(result.getFileName()));
        this.logger.info(new TranslationMarker(LOG_PREFIX + "summary.imported"), "Imported documents ({}): {}",
            imported.size(), String.join(COMMA, imported));
        this.logger.info(new TranslationMarker(LOG_PREFIX + "summary.notImported"),
            "Documents not imported ({}): {}", notImported.size(), String.join(COMMA, notImported));
    }
}
