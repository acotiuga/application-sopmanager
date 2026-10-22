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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.Revisions;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDLayoutAttributeObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * Extracts the figures of a tagged PDF: the area of the page given by the bounding box of the figure, rendered as
 * image. The figures without bounding box are counted, to warn that they were not imported.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfImages
{
    private static final float DPI = 150;

    private static final float POINTS_PER_INCH = 72;

    private static final float PIXELS_PER_INCH = 96;

    private final PDDocument document;

    private final PDFRenderer renderer;

    private final Map<String, byte[]> images = new LinkedHashMap<>();

    private int renderedPage = -1;

    private BufferedImage pageImage;

    private int missing;

    /**
     * @param document the PDF
     */
    public PdfImages(PDDocument document)
    {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    /**
     * @param figure a figure of the structure tree
     * @param page the page of the figure
     * @return the image of the figure, an attachment of the imported page; {@code null} if the figure has no bounding
     *     box or can't be rendered
     */
    public ImageBlock extract(PDStructureElement figure, PDPage page)
    {
        PDRectangle box = getBoundingBox(figure);
        int pageIndex = page == null ? -1 : this.document.getPages().indexOf(page);
        byte[] content = box == null || pageIndex < 0 ? null : render(pageIndex, page, box);
        if (content == null) {
            this.missing++;
            return null;
        }
        String name = String.format("figure-%d.png", this.images.size() + 1);
        this.images.put(name, content);
        ResourceReference reference = new ResourceReference(name, ResourceType.ATTACHMENT);
        // An attachment of the current page: rendered as "image:name".
        reference.setTyped(false);
        ImageBlock image = new ImageBlock(reference, false);
        image.setParameter("width", String.valueOf(Math.round(box.getWidth() * PIXELS_PER_INCH / POINTS_PER_INCH)));
        return image;
    }

    private static PDRectangle getBoundingBox(PDStructureElement figure)
    {
        Revisions<PDAttributeObject> attributes = figure.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.getObject(i) instanceof PDLayoutAttributeObject layout && layout.getBBox() != null) {
                return layout.getBBox();
            }
        }
        return null;
    }

    private byte[] render(int pageIndex, PDPage page, PDRectangle box)
    {
        try {
            if (pageIndex != this.renderedPage) {
                this.pageImage = this.renderer.renderImageWithDPI(pageIndex, DPI, ImageType.RGB);
                this.renderedPage = pageIndex;
            }
            float scale = DPI / POINTS_PER_INCH;
            PDRectangle cropBox = page.getCropBox();
            int x = Math.max(0, Math.round((box.getLowerLeftX() - cropBox.getLowerLeftX()) * scale));
            int y = Math.max(0, Math.round((cropBox.getUpperRightY() - box.getUpperRightY()) * scale));
            int width = Math.min(this.pageImage.getWidth() - x, Math.round(box.getWidth() * scale));
            int height = Math.min(this.pageImage.getHeight() - y, Math.round(box.getHeight() * scale));
            if (width < 1 || height < 1) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(this.pageImage.getSubimage(x, y, width, height), "png", output);
            return output.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * @return the images of the figures, by attachment name
     */
    public Map<String, byte[]> getImages()
    {
        return this.images;
    }

    /**
     * @return the number of figures that could not be extracted
     */
    public int getMissing()
    {
        return this.missing;
    }
}
