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

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobExecutor;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

@ComponentTest
class DefaultPDFExportJobManagerTest
{
    @InjectMockComponents
    private DefaultPDFExportJobManager jobManager;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private TemporaryResourceStore temporaryResourceStore;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    private Job job;

    private PDFExportJobStatus status;

    private TemporaryResourceReference pdfReference;

    private File pdfFile;

    @BeforeEach
    void setUp() throws Exception
    {
        job = mock(Job.class);
        status = mock(PDFExportJobStatus.class);
        pdfReference = mock(TemporaryResourceReference.class);
        pdfFile = File.createTempFile("sopmanager-test", ".pdf");
        pdfFile.deleteOnExit();

        when(jobExecutor.execute(eq("export/pdf"), any(PDFExportJobRequest.class))).thenReturn(job);
        when(job.getStatus()).thenReturn(status);
        when(localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.error.noTemporaryFile"))
            .thenReturn("No temporary PDF file was generated.");
        when(localizationManager.getTranslationPlain(
            eq("sopManager.defaultPDFExportManager.error.missingTemporaryFile"), any()))
            .thenReturn("The temporary PDF file is missing.");
    }

    @Test
    void exportReturnsTheTemporaryPdfFile() throws Exception
    {
        when(status.getPDFFileReference()).thenReturn(pdfReference);
        when(temporaryResourceStore.getTemporaryFile(pdfReference)).thenReturn(pdfFile);

        File result = jobManager.export(mock(PDFExportJobRequest.class));

        assertEquals(pdfFile, result);
    }

    @Test
    void exportThrowsWhenPdfReferenceIsMissing() throws Exception
    {
        when(status.getPDFFileReference()).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> jobManager.export(mock(PDFExportJobRequest.class)));

        assertEquals("No temporary PDF file was generated.", exception.getMessage());
    }

    @Test
    void exportThrowsWhenTemporaryFileIsMissing() throws Exception
    {
        when(status.getPDFFileReference()).thenReturn(pdfReference);
        when(temporaryResourceStore.getTemporaryFile(pdfReference)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> jobManager.export(mock(PDFExportJobRequest.class)));

        assertEquals("The temporary PDF file is missing.", exception.getMessage());
    }

    @Test
    void exportThrowsWhenTemporaryFileDoesNotExist() throws Exception
    {
        when(status.getPDFFileReference()).thenReturn(pdfReference);
        File missingFile = new File(pdfFile.getParentFile(), pdfFile.getName() + ".missing");
        when(temporaryResourceStore.getTemporaryFile(pdfReference)).thenReturn(missingFile);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> jobManager.export(mock(PDFExportJobRequest.class)));

        assertEquals("The temporary PDF file is missing.", exception.getMessage());
    }
}