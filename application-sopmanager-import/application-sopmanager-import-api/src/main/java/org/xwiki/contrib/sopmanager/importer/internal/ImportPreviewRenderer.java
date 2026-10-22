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

import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;

/**
 * Renders the content of a document to import in the syntax of the wiki, to preview it (e.g. with the preview action of
 * the editor): its images, not attached to a page yet, are embedded as data URIs.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportPreviewRenderer.class)
@Singleton
public class ImportPreviewRenderer
{
    private static final String JPEG = "image/jpeg";

    private static final Map<String, String> MEDIA_TYPES = Map.of("png", "image/png", "jpg", JPEG, "jpeg", JPEG,
        "gif", "image/gif", "bmp", "image/bmp", "svg", "image/svg+xml");

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    /**
     * @param document a document read for its import
     * @return its content in the xwiki/2.1 syntax, its images embedded as data URIs
     */
    public String render(ImportedDocument document)
    {
        XDOM content = document.content().clone();
        for (Block block : content.getBlocks(new ClassBlockMatcher(ImageBlock.class), Block.Axes.DESCENDANT)) {
            ImageBlock image = (ImageBlock) block;
            String name = image.getReference().getReference();
            byte[] bytes = document.images().get(name);
            String mediaType = MEDIA_TYPES.get(StringUtils.substringAfterLast(name, ".").toLowerCase(Locale.ROOT));
            if (bytes == null || mediaType == null) {
                // Not an image a browser displays (e.g. an EMF or WMF drawing): its name.
                block.getParent().replaceChild(new WordBlock(String.format("[%s]", name)), block);
            } else {
                String uri = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                ResourceReference reference = new ResourceReference(uri, ResourceType.URL);
                reference.setTyped(true);
                block.getParent().replaceChild(new ImageBlock(reference, false, image.getParameters()), block);
            }
        }
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.renderer.render(content, printer);
        return printer.toString();
    }
}
