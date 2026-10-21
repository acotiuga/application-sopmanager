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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.PDFExportJobManager;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobExecutor;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;

/**
 * Default implementation of {@link PDFExportJobManager}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultPDFExportJobManager implements PDFExportJobManager
{
    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private TemporaryResourceStore temporaryResourceStore;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Override
    public File export(PDFExportJobRequest request) throws Exception
    {
        Job job = jobExecutor.execute("export/pdf", request);
        job.join();

        PDFExportJobStatus status = (PDFExportJobStatus) job.getStatus();
        TemporaryResourceReference pdfReference = status.getPDFFileReference();

        if (pdfReference == null) {
            // Tell why the export failed: the error of the job, else the last error it logged.
            String message =
                localizationManager.getTranslationPlain("sopManager.defaultPDFExportManager.error.noTemporaryFile");
            String cause = getCause(status);
            throw new RuntimeException(cause == null ? message : message + ' ' + cause, status.getError());
        }

        File pdfFile = temporaryResourceStore.getTemporaryFile(pdfReference);
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.isFile()) {
            throw new RuntimeException(localizationManager.getTranslationPlain(
                "sopManager.defaultPDFExportManager.error.missingTemporaryFile", pdfFile));
        }

        return pdfFile;
    }

    private static String getCause(PDFExportJobStatus status)
    {
        if (status.getError() != null) {
            return ExceptionUtils.getRootCauseMessage(status.getError());
        }
        LogEvent error = status.getLogTail() == null ? null : status.getLogTail().getLastLogEvent(LogLevel.ERROR);
        return error == null ? null : error.getFormattedMessage();
    }
}
