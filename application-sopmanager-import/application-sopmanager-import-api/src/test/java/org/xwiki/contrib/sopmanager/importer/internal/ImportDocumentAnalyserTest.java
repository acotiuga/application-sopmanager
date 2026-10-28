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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportDocumentAnalyser}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportDocumentAnalyserTest
{
    @InjectMockComponents
    private ImportDocumentAnalyser analyser;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @MockComponent
    private SOPDocumentCreator documentCreator;

    @MockComponent
    private DocumentReader documentReader;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    private ImportPreviewRenderer previewRenderer;

    @MockComponent
    private ImportUserResolver userResolver;

    @TempDir
    private Path folder;

    private Path pdf;

    private XDOM pdfContent;

    private Path word;

    private Path other;

    private Path lockFile;

    private static ImportedDocument document(TableCellBlock... cells)
    {
        XDOM content = new XDOM(List.of(new TableBlock(List.of(new TableRowBlock(List.of(cells))))));
        return new ImportedDocument(content, Map.of(), new SOPDocumentDetails(), Compatibility.TAGGED_PDF, false,
            List.of(), DocumentSource.UNKNOWN);
    }

    private static List<String> getHeaders(Block content)
    {
        return content.<TableHeadCellBlock>getBlocks(new ClassBlockMatcher(TableHeadCellBlock.class),
            Block.Axes.DESCENDANT).stream().map(TableHeaders::getText).toList();
    }

    @BeforeEach
    void beforeEach() throws Exception
    {
        this.pdf = Files.writeString(this.folder.resolve("A.pdf"), "pdf");
        this.word = Files.writeString(this.folder.resolve("A.docx"), "docx");
        this.other = Files.writeString(this.folder.resolve("B.docx"), "docx");
        this.lockFile = Files.writeString(this.folder.resolve("~$A.docx"), "lock");
        Files.writeString(this.folder.resolve("A.txt"), "txt");
        // The PDF exported from the Word document, whose header cells are shaded: the PDF doesn't tell them.
        when(this.documentReader.read(this.pdf)).thenAnswer(invocation -> {
            ImportedDocument document = document(new TableCellBlock(List.of(new WordBlock("Schublade"))),
                new TableCellBlock(List.of(new WordBlock("Tupfer"))));
            this.pdfContent = document.content();
            return document;
        });
        when(this.documentReader.read(this.word)).thenReturn(document(
            new TableHeadCellBlock(List.of(new WordBlock("Schublade"))),
            new TableCellBlock(List.of(new WordBlock("Tupfer")))));
        when(this.previewRenderer.render(any())).thenReturn("preview");
    }

    @Test
    void previewWithTheHeaderCellsOfTheOtherVersions() throws Exception
    {
        assertEquals("preview", this.analyser.preview(this.pdf).content());

        assertEquals(List.of("schublade"), getHeaders(this.pdfContent));
        // Only the versions of the same document.
        verify(this.documentReader, never()).read(this.other);
        verify(this.documentReader, never()).read(this.lockFile);
    }

    @Test
    void readWithTheHeaderCellsOfTheOtherVersions() throws Exception
    {
        ImportedDocument document = this.analyser.read(this.pdf, new SOPImportResult("A.pdf"));

        assertEquals(List.of("schublade"), getHeaders(document.content()));
    }

    @Test
    void readWhenAnotherVersionCanNotBeRead() throws Exception
    {
        when(this.documentReader.read(this.word)).thenThrow(new DocumentReadException("Damaged"));

        ImportedDocument document = this.analyser.read(this.pdf, new SOPImportResult("A.pdf"));

        assertEquals(List.of(), getHeaders(document.content()));
    }
}
