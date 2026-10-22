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
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.listener.Format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PdfInlineConverter}.
 *
 * @version $Id$
 */
class PdfInlineConverterTest
{
    private static TextPosition position(String text, float x, float y, PDFont font)
    {
        TextPosition position = mock(TextPosition.class);
        when(position.getUnicode()).thenReturn(text);
        when(position.getXDirAdj()).thenReturn(x);
        when(position.getYDirAdj()).thenReturn(y);
        when(position.getWidthDirAdj()).thenReturn(5F);
        // A font without size, scaled by the text matrix: the height of the characters gives the size.
        when(position.getFontSizeInPt()).thenReturn(0F);
        when(position.getHeightDir()).thenReturn(5F);
        when(position.getFont()).thenReturn(font);
        return position;
    }

    private static List<TextPosition> word(String text, float x, float y, PDFont font)
    {
        List<TextPosition> positions = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            positions.add(position(text.substring(i, i + 1), x + i * 5, y, font));
        }
        return positions;
    }

    @Test
    void glyphs()
    {
        PDFont symbol = mock(PDFont.class);
        PDFont heavy = mock(PDFont.class);
        PDFontDescriptor descriptor = mock(PDFontDescriptor.class);
        when(heavy.getName()).thenReturn("ABCDEF+Calibri");
        when(heavy.getFontDescriptor()).thenReturn(descriptor);
        when(descriptor.getFontWeight()).thenReturn(700F);
        when(descriptor.isItalic()).thenReturn(true);
        List<TextPosition> positions = new ArrayList<>();
        // A bullet of a symbol font, separated by a gap; a word without font; a word in a bold italic font.
        positions.add(position("", 0, 10, symbol));
        positions.addAll(word("Ab", 20, 10, null));
        positions.add(position(null, 30, 10, null));
        positions.addAll(word("cd", 40, 10, heavy));

        List<PdfInlineConverter.Glyph> glyphs = PdfInlineConverter.glyphs(positions);

        assertEquals("• Ab cd", PdfInlineConverter.text(glyphs));
        assertEquals(Set.of(), glyphs.get(1).formats());
        assertEquals(Set.of(Format.BOLD, Format.ITALIC), glyphs.get(3).formats());
    }

    @Test
    void glyphsJoinTheWordsHyphenatedAtTheEndOfALine()
    {
        List<TextPosition> positions = new ArrayList<>(word("ge-", 0, 10, null));
        positions.addAll(word("fen OP-", 0, 20, null));
        positions.addAll(word("Bereich", 0, 30, null));
        positions.addAll(word("-", 0, 40, null));

        assertEquals("gefen OP-Bereich -", PdfInlineConverter.text(PdfInlineConverter.glyphs(positions)));
    }
}
