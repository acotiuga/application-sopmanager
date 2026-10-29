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
package org.xwiki.contrib.sopmanager.importer.script;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.importer.SOPImportAnalysis;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportPreview;
import org.xwiki.contrib.sopmanager.importer.SOPImportReport;
import org.xwiki.contrib.sopmanager.importer.SOPImportVersion;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SOPImportScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class SOPImportScriptServiceTest
{
    private static final String FOLDER = "/import";

    private static final String JOB = "job";

    @InjectMockComponents
    private SOPImportScriptService scriptService;

    @MockComponent
    private SOPImportManager importManager;

    @Test
    void analyseDocuments()
    {
        SOPImportAnalysis analysis = new SOPImportAnalysis(FOLDER);
        when(this.importManager.analyseDocuments(FOLDER, List.of("BB/AA/A.docx"), "docx", JOB)).thenReturn(analysis);
        assertSame(analysis, this.scriptService.analyseDocuments(FOLDER, List.of("BB/AA/A.docx"), "docx", JOB));
        when(this.importManager.getAnalysis(JOB)).thenReturn(analysis);
        assertSame(analysis, this.scriptService.getAnalysis(JOB));
    }

    @Test
    void getFolders()
    {
        SOPImportFolder folder = new SOPImportFolder("import", "");
        when(this.importManager.getFolders(FOLDER)).thenReturn(folder);
        assertSame(folder, this.scriptService.getFolders(FOLDER));
    }

    @Test
    void previewDocument()
    {
        SOPImportPreview preview = new SOPImportPreview(new SOPImportVersion("A.pdf", "pdf"), "A", "A");
        when(this.importManager.previewDocument(FOLDER, "A.pdf")).thenReturn(preview);
        assertSame(preview, this.scriptService.previewDocument(FOLDER, "A.pdf"));
    }

    @Test
    void importDocuments()
    {
        SOPImportReport report = new SOPImportReport(FOLDER);
        Map<String, Map<String, List<String>>> users =
            Map.of("A", Map.of(SOPImportManager.APPROVER, List.of("XWiki.SStaar")));
        when(this.importManager.importDocuments(FOLDER, List.of("A.docx"), users, JOB)).thenReturn(report);
        assertSame(report, this.scriptService.importDocuments(FOLDER, List.of("A.docx"), users, JOB));
        when(this.importManager.getReport(JOB)).thenReturn(report);
        assertSame(report, this.scriptService.getReport(JOB));
    }
}
