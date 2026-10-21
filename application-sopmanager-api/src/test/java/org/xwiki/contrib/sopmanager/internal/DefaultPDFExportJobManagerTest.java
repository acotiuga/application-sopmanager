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
package org.xwiki.contrib.sopmanager.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobExecutor;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.logging.tail.LogTail;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

/**
 * Unit tests for {@link DefaultPDFExportJobManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultPDFExportJobManagerTest
{
    private static final String MISSING_FILE_KEY = "sopManager.defaultPDFExportManager.error.missingTemporaryFile";

    @InjectMockComponents
    private DefaultPDFExportJobManager jobManager;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private TemporaryResourceStore temporaryResourceStore;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @TempDir
    private Path temporaryDirectory;

    private PDFExportJobRequest request;

    private Job job;

    private PDFExportJobStatus status;

    private TemporaryResourceReference pdfReference;

    @BeforeEach
    void setUp() throws Exception
    {
        this.request = mock(PDFExportJobRequest.class);
        this.job = mock(Job.class);
        this.status = mock(PDFExportJobStatus.class);
        this.pdfReference = mock(TemporaryResourceReference.class);

        when(this.jobExecutor.execute("export/pdf", this.request)).thenReturn(this.job);
        when(this.job.getStatus()).thenReturn(this.status);
        when(this.localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.error.noTemporaryFile"))
            .thenReturn("No temporary PDF file was generated.");
        when(this.localizationManager.getTranslationPlain(eq(MISSING_FILE_KEY), any()))
            .thenReturn("The temporary PDF file is missing.");
    }

    @Test
    void exportWaitsForTheJobAndReturnsTheTemporaryPDFFile() throws Exception
    {
        File pdfFile = Files.writeString(this.temporaryDirectory.resolve("export.pdf"), "PDF content").toFile();
        when(this.status.getPDFFileReference()).thenReturn(this.pdfReference);
        when(this.temporaryResourceStore.getTemporaryFile(this.pdfReference)).thenReturn(pdfFile);

        assertSame(pdfFile, this.jobManager.export(this.request));

        // The status is only meaningful once the job has finished.
        InOrder inOrder = inOrder(this.job);
        inOrder.verify(this.job).join();
        inOrder.verify(this.job).getStatus();
    }

    @Test
    void exportFailsWhenTheJobProducedNoPDFReference()
    {
        when(this.status.getPDFFileReference()).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("No temporary PDF file was generated.", exception.getMessage());
    }

    @Test
    void exportFailsWithTheErrorOfTheJob()
    {
        IllegalStateException error = new IllegalStateException("Chrome is not available");
        when(this.status.getError()).thenReturn(error);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("No temporary PDF file was generated. IllegalStateException: Chrome is not available",
            exception.getMessage());
        assertSame(error, exception.getCause());
    }

    @Test
    void exportFailsWithTheLastErrorLoggedByTheJob()
    {
        LogTail logTail = mock(LogTail.class);
        LogEvent logEvent = mock(LogEvent.class);
        when(this.status.getLogTail()).thenReturn(logTail);
        when(logTail.getLastLogEvent(LogLevel.ERROR)).thenReturn(logEvent);
        when(logEvent.getFormattedMessage()).thenReturn("Failed to print the page.");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("No temporary PDF file was generated. Failed to print the page.", exception.getMessage());
    }

    @Test
    void exportFailsWhenTheTemporaryStoreHasNoFile() throws Exception
    {
        when(this.status.getPDFFileReference()).thenReturn(this.pdfReference);
        when(this.temporaryResourceStore.getTemporaryFile(this.pdfReference)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("The temporary PDF file is missing.", exception.getMessage());
    }

    @Test
    void exportFailsWhenTheTemporaryFileDoesNotExist() throws Exception
    {
        File missingFile = this.temporaryDirectory.resolve("missing.pdf").toFile();
        when(this.status.getPDFFileReference()).thenReturn(this.pdfReference);
        when(this.temporaryResourceStore.getTemporaryFile(this.pdfReference)).thenReturn(missingFile);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("The temporary PDF file is missing.", exception.getMessage());
    }

    @Test
    void exportFailsWhenTheTemporaryFileIsADirectory() throws Exception
    {
        File directory = Files.createDirectory(this.temporaryDirectory.resolve("export.pdf")).toFile();
        when(this.status.getPDFFileReference()).thenReturn(this.pdfReference);
        when(this.temporaryResourceStore.getTemporaryFile(this.pdfReference)).thenReturn(directory);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> this.jobManager.export(this.request));

        assertEquals("The temporary PDF file is missing.", exception.getMessage());
    }
}
