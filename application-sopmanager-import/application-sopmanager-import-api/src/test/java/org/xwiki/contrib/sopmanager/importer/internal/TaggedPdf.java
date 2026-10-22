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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDLayoutAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Writes a tagged PDF for the tests, as Word does: the text of each element of the structure tree in a marked content,
 * the page header and footer as artifacts.
 *
 * @version $Id$
 */
class TaggedPdf implements Closeable
{
    static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    static final PDFont ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final float SIZE = 10;

    private final PDDocument document = new PDDocument();

    private final PDPage page = new PDPage(PDRectangle.A4);

    private final PDPageContentStream content;

    private final PDStructureElement root;

    private int nextId;

    /**
     * A piece of text with a font.
     *
     * @param text the text
     * @param font the font
     */
    record Run(String text, PDFont font)
    {
    }

    TaggedPdf() throws IOException
    {
        this.document.addPage(this.page);
        this.content = new PDPageContentStream(this.document, this.page);
        PDStructureTreeRoot treeRoot = new PDStructureTreeRoot();
        // The types of Word or LibreOffice, mapped to standard types.
        treeRoot.setRoleMap(Map.of("Standard", "P", "Textkörper", "Standard"));
        this.document.getDocumentCatalog().setStructureTreeRoot(treeRoot);
        this.root = new PDStructureElement("Document", treeRoot);
        treeRoot.appendKid(this.root);
    }

    PDStructureElement getRoot()
    {
        return this.root;
    }

    /**
     * @return a new element of the structure tree, on the page
     */
    PDStructureElement element(String type, PDStructureNode parent)
    {
        PDStructureElement element = new PDStructureElement(type, parent);
        element.setPage(this.page);
        parent.appendKid(element);
        return element;
    }

    /**
     * Writes a line of text, the runs one after the other, in a marked content of the element.
     */
    void line(PDStructureElement element, float x, float y, Run... runs) throws IOException
    {
        COSDictionary properties = new COSDictionary();
        properties.setInt(COSName.MCID, this.nextId);
        PDMarkedContent markedContent = PDMarkedContent.create(COSName.P, properties);
        this.content.beginMarkedContent(COSName.P, PDPropertyList.create(properties));
        float position = x;
        for (Run run : runs) {
            write(run, position, y);
            position += run.font().getStringWidth(run.text()) / 1000 * SIZE;
        }
        this.content.endMarkedContent();
        element.appendKid(markedContent);
        this.nextId++;
    }

    void line(PDStructureElement element, float x, float y, String text) throws IOException
    {
        line(element, x, y, new Run(text, REGULAR));
    }

    private void write(Run run, float x, float y) throws IOException
    {
        this.content.beginText();
        this.content.setFont(run.font(), SIZE);
        this.content.newLineAtOffset(x, y);
        this.content.showText(run.text());
        this.content.endText();
    }

    /**
     * Writes a text that is not part of the content, e.g. the page header and footer.
     */
    void artifact(String text, float x, float y) throws IOException
    {
        this.content.beginMarkedContent(COSName.ARTIFACT);
        write(new Run(text, REGULAR), x, y);
        this.content.endMarkedContent();
    }

    /**
     * Draws an image in a figure, with its bounding box or not.
     */
    void figure(PDStructureNode parent, float x, float y, boolean boundingBox) throws IOException
    {
        PDStructureElement figure = element("Figure", parent);
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 40, 20);
        graphics.dispose();
        PDImageXObject imageObject = LosslessFactory.createFromImage(this.document, image);
        COSDictionary properties = new COSDictionary();
        properties.setInt(COSName.MCID, this.nextId);
        this.content.beginMarkedContent(COSName.getPDFName("Figure"), PDPropertyList.create(properties));
        this.content.drawImage(imageObject, x, y, 40, 20);
        this.content.endMarkedContent();
        figure.appendKid(PDMarkedContent.create(COSName.getPDFName("Figure"), properties));
        this.nextId++;
        if (boundingBox) {
            PDLayoutAttributeObject layout = new PDLayoutAttributeObject();
            layout.setBBox(new PDRectangle(x, y, 40, 20));
            figure.addAttribute(layout);
        }
    }

    /**
     * Makes a cell span several columns.
     */
    static void span(PDStructureElement cell, int columns)
    {
        PDTableAttributeObject table = new PDTableAttributeObject();
        table.setColSpan(columns);
        cell.addAttribute(table);
    }

    Path save(Path file) throws IOException
    {
        this.content.close();
        this.document.save(file.toFile());
        return file;
    }

    @Override
    public void close() throws IOException
    {
        this.document.close();
    }
}
