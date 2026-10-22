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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.officeimporter.converter.OfficeConverter;
import org.xwiki.officeimporter.converter.OfficeConverterException;
import org.xwiki.officeimporter.converter.OfficeConverterResult;
import org.xwiki.officeimporter.server.OfficeServer;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentReader}.
 *
 * @version $Id$
 */
@ComponentTest
class DocumentReaderTest
{
    private static final String WORD_97 = "ANA_AA_9.105_Dummy-Altformat";

    @InjectMockComponents
    private DocumentReader reader;

    @MockComponent
    private OfficeServer officeServer;

    private OfficeConverter converter;

    @BeforeEach
    void beforeEach()
    {
        this.converter = mock(OfficeConverter.class);
        when(this.officeServer.getConverter()).thenReturn(this.converter);
        when(this.officeServer.getState()).thenReturn(OfficeServer.ServerState.CONNECTED);
    }

    private static Path document(String name) throws Exception
    {
        return Paths.get(DocumentReaderTest.class.getResource("/documents/" + name).toURI());
    }

    @Test
    void readEachFormat() throws Exception
    {
        assertEquals(Compatibility.COMPATIBLE,
            this.reader.read(document("ANA_AA_9.101_Dummy-Haendedesinfektion.docx")).compatibility());
        assertEquals(Compatibility.TAGGED_PDF,
            this.reader.read(document("ANA_AA_9.101_Dummy-Haendedesinfektion.pdf")).compatibility());
        UnsupportedDocumentException exception =
            assertThrows(UnsupportedDocumentException.class, () -> this.reader.read(document("Notizen.txt")));
        assertEquals(new ImportWarning("sopManager.import.message.notSupported", "txt"), exception.getReason());
    }

    @Test
    void readWord97Document() throws Exception
    {
        // The conversion of the office server: the Word document converted from the Word 97-2003 document.
        OfficeConverterResult result = mock(OfficeConverterResult.class);
        when(result.getOutputFile()).thenReturn(document(WORD_97 + ".docx").toFile());
        when(this.converter.convertDocument(anyMap(), eq("document.doc"), eq("document.docx"))).thenReturn(result);

        ImportedDocument document = this.reader.read(document(WORD_97 + ".doc"));

        assertEquals("ANA_AA_9.105", document.details().getRevisionId());
        assertEquals(7, document.details().getRevisionNumber());
        // The properties of the Word 97-2003 document, not the ones of the application converting it.
        assertNull(document.source().application());
        assertNull(document.source().compatibilityMode());
        assertEquals("Dummy", document.source().author());
        verify(result).close();
    }

    @Test
    void readWord97DocumentWithoutOfficeServer()
    {
        when(this.officeServer.getState()).thenReturn(OfficeServer.ServerState.NOT_CONNECTED);

        UnsupportedDocumentException exception =
            assertThrows(UnsupportedDocumentException.class, () -> this.reader.read(document(WORD_97 + ".doc")));

        assertEquals(new ImportWarning("sopManager.import.message.officeServer"), exception.getReason());
    }

    @Test
    void readWord97DocumentWhenTheConversionFails() throws Exception
    {
        when(this.converter.convertDocument(anyMap(), any(), any()))
            .thenThrow(new OfficeConverterException("Conversion failed"));

        DocumentReadException exception =
            assertThrows(DocumentReadException.class, () -> this.reader.read(document(WORD_97 + ".doc")));

        assertEquals("Failed to convert the document: Conversion failed", exception.getMessage());
        assertTrue(exception.getCause() instanceof OfficeConverterException);
    }

    @Test
    void getExtension()
    {
        assertEquals("pdf", DocumentReader.getExtension(Paths.get("/import/A.B.PDF")));
        assertEquals("", DocumentReader.getExtension(Paths.get("/import/A")));
    }
}
