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

import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Unit tests for {@link ImportPreviewRenderer}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportPreviewRendererTest
{
    @InjectMockComponents
    private ImportPreviewRenderer previewRenderer;

    @MockComponent
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    private static ImageBlock image(String name)
    {
        ResourceReference reference = new ResourceReference(name, ResourceType.ATTACHMENT);
        reference.setTyped(false);
        ImageBlock image = new ImageBlock(reference, false);
        image.setParameter("width", "10");
        return image;
    }

    @Test
    void renderEmbedsTheImages()
    {
        XDOM content = new XDOM(List.of(new ParagraphBlock(List.of(image("photo.png"), image("drawing.emf"),
            image("missing.jpg")))));
        ImportedDocument document = new ImportedDocument(content,
            Map.of("photo.png", new byte[] { 1, 2, 3 }, "drawing.emf", new byte[] { 4 }), new SOPDocumentDetails(),
            Compatibility.COMPATIBLE, false, List.of(), DocumentSource.UNKNOWN);
        doAnswer(invocation -> {
            List<Block> blocks = ((Block) invocation.getArgument(0)).getChildren().get(0).getChildren();
            ImageBlock photo = (ImageBlock) blocks.get(0);
            assertEquals("data:image/png;base64,AQID", photo.getReference().getReference());
            assertEquals(ResourceType.URL, photo.getReference().getType());
            assertTrue(photo.getReference().isTyped());
            assertEquals("10", photo.getParameter("width"));
            // The images a browser doesn't display, and the missing ones: their name.
            assertEquals("[drawing.emf]", ((WordBlock) blocks.get(1)).getWord());
            assertEquals("[missing.jpg]", ((WordBlock) blocks.get(2)).getWord());
            ((WikiPrinter) invocation.getArgument(1)).print("content");
            return null;
        }).when(this.renderer).render(any(Block.class), any());

        assertEquals("content", this.previewRenderer.render(document));
        // The content of the document is left untouched.
        assertEquals(ImageBlock.class, content.getChildren().get(0).getChildren().get(0).getClass());
    }
}
