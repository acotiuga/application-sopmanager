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
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;

/**
 * Reads a tagged PDF for its import as SOP document. The applications exporting a document to PDF (Word, LibreOffice,
 * Acrobat) write its structure (paragraphs, headings, lists, tables, figures) in the PDF, for accessibility, and mark
 * the page header and footer as artifacts: the content is converted from the structure, the details of the SOP
 * document are read from the artifacts. A PDF without structure (e.g. a scan) can't be imported.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfReader
{
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    private static final int MAX_PAGES = 500;

    private final Path file;

    /**
     * @param file the PDF
     */
    public PdfReader(Path file)
    {
        this.file = file;
    }

    /**
     * @return the content and the details of the document
     * @throws DocumentReadException if the PDF can't be read, or has no structure
     */
    public ImportedDocument read() throws DocumentReadException
    {
        try {
            if (Files.size(this.file) > MAX_SIZE) {
                throw new DocumentReadException("The document is too large.");
            }
        } catch (IOException e) {
            throw new DocumentReadException("Failed to read the document: " + e.getMessage(), e);
        }
        try (PDDocument document = Loader.loadPDF(this.file.toFile())) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new DocumentReadException("The document has too many pages.");
            }
            PDStructureTreeRoot root = document.getDocumentCatalog().getStructureTreeRoot();
            if (root == null || root.getKids().isEmpty()) {
                throw new UnsupportedDocumentException(new ImportWarning("sopManager.import.message.untaggedPdf"));
            }
            PdfPageText text = new PdfPageText(document);
            SOPDocumentDetails details = new SOPDocumentDetails();
            PdfPageDecoration.read(document, text, details);
            PdfImages images = new PdfImages(document);
            List<Block> blocks =
                new PdfContentConverter(new PdfStructure(text, root.getRoleMap()), images).convertChildren(root, null);
            List<ImportWarning> warnings = new ArrayList<>(details.getWarnings());
            if (images.getMissing() > 0) {
                warnings.add(new ImportWarning("sopManager.import.warning.missingFigures", images.getMissing()));
            }
            return new ImportedDocument(new XDOM(blocks), images.getImages(), details, Compatibility.TAGGED_PDF,
                isLandscape(document), warnings, getSource(document, details));
        } catch (IOException e) {
            throw new DocumentReadException("Not a valid PDF: " + e.getMessage(), e);
        }
    }

    private static boolean isLandscape(PDDocument document)
    {
        if (document.getNumberOfPages() == 0) {
            return false;
        }
        PDPage page = document.getPage(0);
        PDRectangle box = page.getCropBox();
        boolean rotated = page.getRotation() % 180 != 0;
        return rotated ? box.getHeight() > box.getWidth() : box.getWidth() > box.getHeight();
    }

    private static DocumentSource getSource(PDDocument document, SOPDocumentDetails details)
    {
        PDDocumentInformation information = document.getDocumentInformation();
        String application =
            StringUtils.defaultIfBlank(information.getCreator(), StringUtils.trimToNull(information.getProducer()));
        return new DocumentSource(StringUtils.trimToNull(application), StringUtils.trimToNull(information.getAuthor()),
            toInstant(information.getCreationDate()), toInstant(information.getModificationDate()), null,
            details.getOriginalDocument(), null);
    }

    private static Instant toInstant(Calendar calendar)
    {
        return calendar == null ? null : calendar.toInstant();
    }
}
