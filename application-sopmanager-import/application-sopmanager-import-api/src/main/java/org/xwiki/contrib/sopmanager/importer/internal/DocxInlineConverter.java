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

import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.attribute;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.child;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.children;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.is;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.isOn;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * Converts the content of the paragraphs of a Word document to inline rendering blocks: the text with its bold,
 * italic, underlined and struck out formatting, the line breaks, the hyperlinks and the images.
 *
 * @version $Id$
 * @since 1.0
 */
public class DocxInlineConverter
{
    /**
     * The characters converted to special symbols, as the plain text parser of XWiki does.
     */
    private static final String SPECIAL_SYMBOLS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    private static final String DRAWINGML = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static final String WORDPROCESSING_DRAWING =
        "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

    private static final String VML = "urn:schemas-microsoft-com:vml";

    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    private static final int EMU_PER_PIXEL = 9525;

    private static final String ID = "id";

    /**
     * The elements whose content is converted as the content of the paragraph.
     */
    private static final Set<String> CONTAINERS = Set.of("fldSimple", "ins", "smartTag", "customXml",
        DocxXml.SDT_CONTENT);

    /**
     * The links kept: web pages, e-mail addresses and file transfers, not scripts.
     */
    private static final Pattern SAFE_LINK = Pattern.compile("^(?:https?|mailto|ftp):", Pattern.CASE_INSENSITIVE);

    private static final Pattern BEHIND_TEXT = Pattern.compile("z-index:\\s*-");

    private static final String RUN = "r";

    private static final String RUN_PROPERTIES = "rPr";

    private static final String FIELD_CHARACTER = "fldCharType";

    private final DocxPackage docx;

    private final Map<String, DocxPackage.Relationship> relationships;

    private final Map<String, String> imageNames = new LinkedHashMap<>();

    private final Map<String, byte[]> images = new LinkedHashMap<>();

    private DocxBlockConverter blockConverter;

    private int backgroundImages;

    /**
     * A sequence of inline blocks with the same formatting.
     */
    private static final class Segment
    {
        private final Set<Format> formats;

        private final List<Block> blocks = new ArrayList<>();

        Segment(Set<Format> formats)
        {
            this.formats = formats;
        }
    }

    /**
     * The state of the fields of a paragraph: their instructions are not displayed, their result is.
     */
    private static final class FieldState
    {
        private int depth;

        private boolean instruction;
    }

    /**
     * @param docx the Word document
     * @param relationships the relationships of the main part, to find the images and the hyperlinks
     */
    public DocxInlineConverter(DocxPackage docx, Map<String, DocxPackage.Relationship> relationships)
    {
        this.docx = docx;
        this.relationships = relationships;
    }

    /**
     * @param blockConverter converts the paragraphs of the text boxes
     */
    public void setBlockConverter(DocxBlockConverter blockConverter)
    {
        this.blockConverter = blockConverter;
    }

    /**
     * @param paragraph a paragraph
     * @param after where to add the blocks to display after the paragraph: the content of its text boxes
     * @return the inline content of the paragraph
     */
    public List<Block> convert(Element paragraph, List<Block> after)
    {
        List<Segment> segments = new ArrayList<>();
        convertChildren(paragraph, segments, after, new FieldState());
        return build(segments);
    }

    /**
     * @return the images of the converted content, by attachment name
     */
    public Map<String, byte[]> getImages()
    {
        return this.images;
    }

    /**
     * @return the number of images behind the text that were not converted (backgrounds of the pages)
     */
    public int getBackgroundImages()
    {
        return this.backgroundImages;
    }

    private void convertChildren(Element parent, List<Segment> segments, List<Block> after, FieldState fields)
    {
        for (Element child : children(parent)) {
            if (is(child, RUN)) {
                convertRun(child, segments, after, fields);
            } else if (is(child, "hyperlink")) {
                convertHyperlink(child, segments, after, fields);
            } else if (CONTAINERS.contains(child.getLocalName()) && DocxPackage.W.equals(child.getNamespaceURI())) {
                convertChildren(child, segments, after, fields);
            } else if (is(child, DocxXml.SDT)) {
                convertChildren(child(child, DocxXml.SDT_CONTENT), segments, after, fields);
            }
        }
    }

    private void convertHyperlink(Element hyperlink, List<Segment> segments, List<Block> after, FieldState fields)
    {
        List<Segment> linkSegments = new ArrayList<>();
        convertChildren(hyperlink, linkSegments, after, fields);
        DocxPackage.Relationship target =
            this.relationships.get(hyperlink.getAttributeNS(DocxPackage.R, ID));
        List<Block> content = build(linkSegments);
        if (target != null && target.external() && SAFE_LINK.matcher(target.target()).find() && !content.isEmpty()) {
            Segment segment = new Segment(EnumSet.noneOf(Format.class));
            segment.blocks.add(new LinkBlock(content, new ResourceReference(target.target(), ResourceType.URL),
                false));
            segments.add(segment);
        } else {
            segments.addAll(linkSegments);
        }
    }

    private void convertRun(Element run, List<Segment> segments, List<Block> after, FieldState fields)
    {
        Segment segment = new Segment(getFormats(child(run, RUN_PROPERTIES)));
        for (Element content : children(run)) {
            if (is(content, "fldChar")) {
                updateFields(fields, attribute(content, FIELD_CHARACTER));
            } else if (!fields.instruction) {
                convertRunContent(content, segment.blocks, after);
            }
        }
        if (!segment.blocks.isEmpty()) {
            segments.add(segment);
        }
    }

    private static void updateFields(FieldState fields, String type)
    {
        if ("begin".equals(type)) {
            fields.depth++;
            fields.instruction = true;
        } else if ("separate".equals(type)) {
            fields.instruction = false;
        } else if ("end".equals(type)) {
            fields.depth = Math.max(0, fields.depth - 1);
            fields.instruction = false;
        }
    }

    private void convertRunContent(Element content, List<Block> blocks, List<Block> after)
    {
        if (convertText(content, blocks)) {
            return;
        }
        if (is(content, "drawing") || is(content, "pict")) {
            convertDrawing(content, blocks, after);
        } else if ("AlternateContent".equals(content.getLocalName()) && MC.equals(content.getNamespaceURI())) {
            // The choice holds the drawing, the fallback a VML copy of it for older readers.
            Element choice = children(content).stream().filter(c -> "Choice".equals(c.getLocalName()))
                .findFirst().orElse(null);
            if (choice != null) {
                for (Element drawing : children(choice)) {
                    convertRunContent(drawing, blocks, after);
                }
            }
        }
    }

    /**
     * Converts the text of a run: its characters, tabs, line breaks and non-breaking hyphens.
     *
     * @return whether the content was text
     */
    private static boolean convertText(Element content, List<Block> blocks)
    {
        boolean converted = true;
        if (is(content, "t")) {
            blocks.addAll(text(content.getTextContent()));
        } else if (is(content, "tab")) {
            blocks.add(new SpaceBlock());
        } else if (is(content, "br") && !"page".equals(attribute(content, "type")) || is(content, "cr")) {
            blocks.add(new NewLineBlock());
        } else if (is(content, "noBreakHyphen")) {
            blocks.add(new SpecialSymbolBlock('-'));
        } else {
            converted = false;
        }
        return converted;
    }

    private void convertDrawing(Element drawing, List<Block> blocks, List<Block> after)
    {
        // The images of the text boxes are converted with the paragraphs of the text boxes.
        List<Element> descendants = new ArrayList<>();
        collectOutsideTextBoxes(drawing, descendants);
        boolean behind = descendants.stream().anyMatch(DocxInlineConverter::isBehindText);
        for (Element element : descendants) {
            String relationship = getImageRelationship(element);
            if (relationship != null && behind) {
                this.backgroundImages++;
            } else if (relationship != null) {
                addImage(relationship, getWidth(descendants), blocks);
            } else if (is(element, DocxXml.TXBX_CONTENT) && this.blockConverter != null) {
                after.addAll(this.blockConverter.convert(children(element)));
            }
        }
    }

    /**
     * @return the relationship of the image of a drawing or VML element, {@code null} if it's not an image
     */
    private static String getImageRelationship(Element element)
    {
        String relationship = null;
        if ("blip".equals(element.getLocalName()) && DRAWINGML.equals(element.getNamespaceURI())) {
            relationship = element.getAttributeNS(DocxPackage.R, "embed");
        } else if ("imagedata".equals(element.getLocalName()) && VML.equals(element.getNamespaceURI())) {
            relationship = element.getAttributeNS(DocxPackage.R, ID);
        }
        return relationship;
    }

    /**
     * @return whether the element is a drawing anchored behind the text, or a VML shape below the text
     */
    private static boolean isBehindText(Element element)
    {
        if (VML.equals(element.getNamespaceURI())) {
            return BEHIND_TEXT.matcher(element.getAttribute("style")).find();
        }
        return "anchor".equals(element.getLocalName()) && WORDPROCESSING_DRAWING.equals(element.getNamespaceURI())
            && "1".equals(element.getAttribute("behindDoc"));
    }

    private static void collectOutsideTextBoxes(Element element, List<Element> descendants)
    {
        for (Element child : children(element)) {
            if ("Fallback".equals(child.getLocalName()) && MC.equals(child.getNamespaceURI())) {
                continue;
            }
            descendants.add(child);
            if (!is(child, DocxXml.TXBX_CONTENT)) {
                collectOutsideTextBoxes(child, descendants);
            }
        }
    }

    private void addImage(String relationship, String width, List<Block> blocks)
    {
        DocxPackage.Relationship target = this.relationships.get(relationship);
        byte[] content = target == null || target.external() ? null : this.docx.getBytes(target.target());
        if (content == null) {
            return;
        }
        String name = this.imageNames.computeIfAbsent(target.target(), this::newImageName);
        this.images.put(name, content);
        ResourceReference reference = new ResourceReference(name, ResourceType.ATTACHMENT);
        // An attachment of the current page: rendered as "image:name".
        reference.setTyped(false);
        ImageBlock image = new ImageBlock(reference, false);
        if (width != null) {
            image.setParameter("width", width);
        }
        blocks.add(image);
    }

    private String newImageName(String target)
    {
        String name = target.substring(target.lastIndexOf('/') + 1);
        String candidate = name;
        for (int i = 2; this.images.containsKey(candidate); i++) {
            candidate = i + "-" + name;
        }
        return candidate;
    }

    private static String getWidth(List<Element> descendants)
    {
        for (Element element : descendants) {
            if ("extent".equals(element.getLocalName())
                && WORDPROCESSING_DRAWING.equals(element.getNamespaceURI())) {
                try {
                    long width = Long.parseLong(element.getAttribute("cx")) / EMU_PER_PIXEL;
                    return width > 0 ? String.valueOf(width) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Set<Format> getFormats(Element properties)
    {
        Set<Format> formats = EnumSet.noneOf(Format.class);
        if (isOn(properties, "b")) {
            formats.add(Format.BOLD);
        }
        if (isOn(properties, "i")) {
            formats.add(Format.ITALIC);
        }
        if (isOn(properties, "u")) {
            formats.add(Format.UNDERLINED);
        }
        if (isOn(properties, "strike") || isOn(properties, "dstrike")) {
            formats.add(Format.STRIKEDOUT);
        }
        return formats;
    }

    /**
     * Builds the inline blocks of segments, merging the consecutive segments with the same formatting, and keeping
     * the spaces at the edges of a formatted text out of the formatting.
     */
    private static List<Block> build(List<Segment> segments)
    {
        List<Block> blocks = new ArrayList<>();
        merge(segments).forEach(segment -> addSegment(segment, blocks));
        return trim(blocks);
    }

    /**
     * @return the segments, the consecutive segments with the same formatting merged
     */
    private static List<Segment> merge(List<Segment> segments)
    {
        List<Segment> merged = new ArrayList<>();
        for (Segment segment : segments) {
            Segment last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && Objects.equals(last.formats, segment.formats)) {
                last.blocks.addAll(segment.blocks);
            } else {
                merged.add(segment);
            }
        }
        return merged;
    }

    /**
     * Adds the blocks of a segment, formatted, the spaces at its edges kept out of the formatting.
     */
    private static void addSegment(Segment segment, List<Block> blocks)
    {
        List<Block> content = new ArrayList<>(segment.blocks);
        if (segment.formats.isEmpty()) {
            blocks.addAll(content);
            return;
        }
        int start = 0;
        while (start < content.size() && content.get(start) instanceof SpaceBlock) {
            start++;
        }
        int end = content.size();
        while (end > start && content.get(end - 1) instanceof SpaceBlock) {
            end--;
        }
        blocks.addAll(content.subList(0, start));
        if (end > start) {
            blocks.addAll(format(content.subList(start, end), segment.formats));
        }
        blocks.addAll(content.subList(end, content.size()));
    }

    private static List<Block> format(List<Block> content, Set<Format> formats)
    {
        List<Block> formatted = content;
        for (Format format : List.of(Format.STRIKEDOUT, Format.UNDERLINED, Format.ITALIC, Format.BOLD)) {
            if (formats.contains(format)) {
                formatted = List.of(new FormatBlock(formatted, format));
            }
        }
        return formatted;
    }

    /**
     * @return the blocks without the spaces and the new lines at their start and end, nor the repeated spaces
     */
    private static List<Block> trim(List<Block> blocks)
    {
        List<Block> trimmed = new ArrayList<>();
        for (Block block : blocks) {
            Block last = trimmed.isEmpty() ? null : trimmed.get(trimmed.size() - 1);
            if (last == null ? !isBlank(block) : !(block instanceof SpaceBlock && isBlank(last))) {
                trimmed.add(block);
            }
        }
        while (!trimmed.isEmpty() && isBlank(trimmed.get(trimmed.size() - 1))) {
            trimmed.remove(trimmed.size() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(Block block)
    {
        return block instanceof SpaceBlock || block instanceof NewLineBlock;
    }

    /**
     * @param text a text
     * @return the words, spaces and special symbols of the text
     */
    public static List<Block> text(String text)
    {
        List<Block> blocks = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            boolean space = Character.isWhitespace(character) || Character.isSpaceChar(character);
            if (space || SPECIAL_SYMBOLS.indexOf(character) >= 0) {
                if (word.length() > 0) {
                    blocks.add(new WordBlock(word.toString()));
                    word.setLength(0);
                }
                blocks.add(space ? new SpaceBlock() : new SpecialSymbolBlock(character));
            } else {
                word.append(character);
            }
        }
        if (word.length() > 0) {
            blocks.add(new WordBlock(word.toString()));
        }
        return blocks;
    }
}
