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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.officeimporter.converter.OfficeConverterException;
import org.xwiki.officeimporter.converter.OfficeConverterResult;
import org.xwiki.officeimporter.server.OfficeServer;

/**
 * Reads the documents to import as SOP documents: the Word documents (docx), the Word 97-2003 documents (doc),
 * converted to Word documents by the office server, and the tagged PDF documents.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = DocumentReader.class)
@Singleton
public class DocumentReader
{
    /**
     * The extension of the Word documents.
     */
    public static final String DOCX = "docx";

    /**
     * The extension of the Word 97-2003 documents.
     */
    public static final String DOC = "doc";

    /**
     * The extension of the PDF documents.
     */
    public static final String PDF = "pdf";

    /**
     * The extensions of the documents that can be imported.
     */
    public static final Set<String> FORMATS = Set.of(DOCX, DOC, PDF);

    private static final String MESSAGE_PREFIX = "sopManager.import.message.";

    @Inject
    private OfficeServer officeServer;

    /**
     * @param file a file
     * @return the extension of the file, in lower case
     */
    public static String getExtension(Path file)
    {
        return StringUtils.substringAfterLast(file.getFileName().toString(), ".").toLowerCase(Locale.ROOT);
    }

    /**
     * @param file a document to import
     * @return the content and the details of the document
     * @throws DocumentReadException if the document can't be read or imported
     */
    public ImportedDocument read(Path file) throws DocumentReadException
    {
        String extension = getExtension(file);
        switch (extension) {
            case DOCX:
                return new DocxReader(new DocxPackage(file)).read();
            case PDF:
                return new PdfReader(file).read();
            case DOC:
                return readWord97(file);
            default:
                throw new UnsupportedDocumentException(new ImportWarning(MESSAGE_PREFIX + "notSupported", extension));
        }
    }

    /**
     * @return whether the office server, converting the Word 97-2003 documents, is running
     */
    public boolean isOfficeServerConnected()
    {
        return this.officeServer.getState() == OfficeServer.ServerState.CONNECTED;
    }

    private ImportedDocument readWord97(Path file) throws DocumentReadException
    {
        if (!isOfficeServerConnected()) {
            throw new UnsupportedDocumentException(new ImportWarning(MESSAGE_PREFIX + "officeServer"));
        }
        String input = "document.doc";
        try (InputStream content = Files.newInputStream(file);
            OfficeConverterResult result = this.officeServer.getConverter()
                .convertDocument(Map.of(input, content), input, "document.docx")) {
            ImportedDocument document = new DocxReader(new DocxPackage(result.getOutputFile().toPath())).read();
            // The properties of the Word 97-2003 document, kept by the conversion, but not the application.
            DocumentSource source = document.source();
            return new ImportedDocument(document.content(), document.images(), document.details(),
                document.compatibility(), document.landscape(), document.warnings(),
                new DocumentSource(null, source.author(), source.created(), source.modified(), source.printed(),
                    source.originalDocument(), null));
        } catch (OfficeConverterException | IOException e) {
            throw new DocumentReadException("Failed to convert the document: " + e.getMessage(), e);
        }
    }
}
