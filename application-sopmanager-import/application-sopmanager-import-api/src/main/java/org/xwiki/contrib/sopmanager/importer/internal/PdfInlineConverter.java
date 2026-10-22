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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.TextPosition;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.listener.Format;

/**
 * Converts the characters of a PDF to rendering blocks: words, spaces and special symbols, bold and italic as written
 * by the fonts. The spaces are written as characters by most applications; the missing ones are deduced from the
 * position of the characters (a gap between two characters, or a new line).
 *
 * @version $Id$
 * @since 1.0
 */
public final class PdfInlineConverter
{
    /**
     * The bullet replacing the characters of the symbol fonts (e.g. Wingdings), mapped to the private use area.
     */
    static final String BULLET = "•";

    private static final String HYPHEN = "-";

    private static final float WORD_GAP = 0.2F;

    private static final float LINE_GAP = 0.5F;

    private static final int BOLD_WEIGHT = 600;

    private static final char PRIVATE_USE_START = '';

    private static final char PRIVATE_USE_END = '';

    /**
     * A character of a PDF: its text, whether it is separated from the previous character, and its formatting.
     *
     * @param text the text of the character
     * @param spaceBefore whether a space separates it from the previous character
     * @param formats its formatting
     */
    public record Glyph(String text, boolean spaceBefore, Set<Format> formats)
    {
    }

    private PdfInlineConverter()
    {
    }

    /**
     * @param positions the characters of a PDF, in reading order
     * @return the characters, with the spaces deduced from their position
     */
    public static List<Glyph> glyphs(List<TextPosition> positions)
    {
        List<Glyph> glyphs = new ArrayList<>();
        TextPosition previous = null;
        for (TextPosition position : positions) {
            String text = clean(position.getUnicode());
            if (!text.isEmpty()) {
                if (previous != null && isHyphenation(glyphs, previous, position, text)) {
                    // A word hyphenated at the end of a line: the word, without the hyphen.
                    glyphs.remove(glyphs.size() - 1);
                    glyphs.add(new Glyph(text, false, getFormats(position)));
                } else {
                    glyphs.add(new Glyph(text, previous != null && isSeparated(previous, position),
                        getFormats(position)));
                }
                previous = position;
            }
        }
        return glyphs;
    }

    /**
     * @return whether the previous character is a hyphen at the end of a line, between two lower case letters, e.g.
     *     the hyphenation of a word by Word (a hyphen joining two German words is followed by an upper case letter)
     */
    private static boolean isHyphenation(List<Glyph> glyphs, TextPosition previous, TextPosition next, String text)
    {
        if (glyphs.size() < 2 || !HYPHEN.equals(previous.getUnicode()) || !isNewLine(previous, next)) {
            return false;
        }
        String before = glyphs.get(glyphs.size() - 2).text();
        return Character.isLowerCase(before.charAt(before.length() - 1)) && Character.isLowerCase(text.charAt(0));
    }

    private static boolean isNewLine(TextPosition previous, TextPosition next)
    {
        float size = Math.max(getSize(previous), getSize(next));
        return Math.abs(next.getYDirAdj() - previous.getYDirAdj()) > size * LINE_GAP;
    }

    /**
     * @param glyphs characters
     * @return the text of the characters, with their spaces
     */
    public static String text(List<Glyph> glyphs)
    {
        StringBuilder text = new StringBuilder();
        for (Glyph glyph : glyphs) {
            text.append(glyph.spaceBefore() ? " " : "").append(glyph.text());
        }
        return text.toString();
    }

    /**
     * @param glyphs characters
     * @return the inline blocks of the characters, the consecutive characters with the same formatting grouped, the
     *     spaces at the edges of a formatted text kept out of the formatting
     */
    public static List<Block> convert(List<Glyph> glyphs)
    {
        List<Block> blocks = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        Set<Format> formats = Set.of();
        for (Glyph glyph : glyphs) {
            if (!glyph.formats().equals(formats) && !glyph.text().isBlank()) {
                addSegment(segment.toString(), formats, blocks);
                segment.setLength(0);
                formats = glyph.formats();
            }
            segment.append(glyph.spaceBefore() ? " " : "").append(glyph.text());
        }
        addSegment(segment.toString(), formats, blocks);
        return trim(blocks);
    }

    private static String clean(String unicode)
    {
        if (unicode == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (char character : unicode.toCharArray()) {
            // The characters of the symbol fonts, used for the bullets.
            boolean symbol = character >= PRIVATE_USE_START && character <= PRIVATE_USE_END;
            text.append(symbol ? BULLET : String.valueOf(character));
        }
        return text.toString();
    }

    private static float getSize(TextPosition position)
    {
        float size = position.getFontSizeInPt() > 0 ? position.getFontSizeInPt() : position.getHeightDir() * 2;
        return Math.max(1, size);
    }

    private static boolean isSeparated(TextPosition previous, TextPosition next)
    {
        String previousText = previous.getUnicode();
        if (previousText.isBlank() || next.getUnicode().isBlank()) {
            return false;
        }
        if (isNewLine(previous, next)) {
            // A new line: the words are separated, unless the previous line ends with a hyphen.
            return !previousText.endsWith(HYPHEN);
        }
        float size = Math.max(getSize(previous), getSize(next));
        float gap = next.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
        return gap > size * WORD_GAP;
    }

    private static Set<Format> getFormats(TextPosition position)
    {
        Set<Format> formats = EnumSet.noneOf(Format.class);
        PDFont font = position.getFont();
        if (font == null) {
            return formats;
        }
        String name = font.getName() == null ? "" : font.getName().toLowerCase(Locale.ROOT);
        PDFontDescriptor descriptor = font.getFontDescriptor();
        if (isBold(name, descriptor)) {
            formats.add(Format.BOLD);
        }
        if (name.contains("italic") || name.contains("oblique") || descriptor != null && descriptor.isItalic()) {
            formats.add(Format.ITALIC);
        }
        return formats;
    }

    private static boolean isBold(String name, PDFontDescriptor descriptor)
    {
        if (name.contains("bold") || name.contains("black")) {
            return true;
        }
        return descriptor != null && (descriptor.isForceBold() || descriptor.getFontWeight() >= BOLD_WEIGHT);
    }

    private static void addSegment(String text, Set<Format> formats, List<Block> blocks)
    {
        if (text.isEmpty()) {
            return;
        }
        if (formats.isEmpty()) {
            blocks.addAll(DocxInlineConverter.text(text));
            return;
        }
        String content = text.strip();
        if (text.length() > 0 && Character.isWhitespace(text.charAt(0))) {
            blocks.add(new SpaceBlock());
        }
        if (!content.isEmpty()) {
            List<Block> formatted = DocxInlineConverter.text(content);
            for (Format format : List.of(Format.ITALIC, Format.BOLD)) {
                if (formats.contains(format)) {
                    formatted = List.of(new FormatBlock(formatted, format));
                }
            }
            blocks.addAll(formatted);
        }
        if (!content.isEmpty() && Character.isWhitespace(text.charAt(text.length() - 1))) {
            blocks.add(new SpaceBlock());
        }
    }

    /**
     * @return the blocks without the spaces at their start and end, nor the repeated spaces
     */
    private static List<Block> trim(List<Block> blocks)
    {
        List<Block> trimmed = new ArrayList<>();
        for (Block block : blocks) {
            boolean space = block instanceof SpaceBlock;
            boolean afterSpace = trimmed.isEmpty() || trimmed.get(trimmed.size() - 1) instanceof SpaceBlock;
            if (!space || !afterSpace) {
                trimmed.add(block);
            }
        }
        while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1) instanceof SpaceBlock) {
            trimmed.remove(trimmed.size() - 1);
        }
        return trimmed;
    }
}
