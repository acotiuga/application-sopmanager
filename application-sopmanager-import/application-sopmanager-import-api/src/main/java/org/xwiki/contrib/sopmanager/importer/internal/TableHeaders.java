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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

/**
 * The header cells of the tables of a document, shared between its versions: the header cells are marked in some
 * formats only (e.g. shaded in the Word document, but not tagged in the PDF exported from it), so the cells of the
 * other versions with the same text are header cells too.
 *
 * @version $Id$
 * @since 1.0
 */
public final class TableHeaders
{
    private TableHeaders()
    {
    }

    /**
     * @param content the content of a version of a document
     * @return the texts of its header cells, compared without spaces and punctuation; the texts that are also in
     *     cells that are not header cells are left out, since they don't tell a header cell
     */
    public static Set<String> collect(Block content)
    {
        Set<String> headers = new HashSet<>();
        Set<String> others = new HashSet<>();
        for (TableCellBlock cell : getCells(content)) {
            String text = getText(cell);
            if (!text.isEmpty()) {
                (cell instanceof TableHeadCellBlock ? headers : others).add(text);
            }
        }
        headers.removeAll(others);
        return headers;
    }

    /**
     * Marks as header cells the cells of a version with the text of header cells of the other versions.
     *
     * @param content the content of a version of a document
     * @param headers the texts of the header cells of the other versions, as returned by {@link #collect(Block)}
     */
    public static void apply(Block content, Set<String> headers)
    {
        if (headers.isEmpty()) {
            return;
        }
        for (TableCellBlock cell : getCells(content)) {
            if (!(cell instanceof TableHeadCellBlock) && headers.contains(getText(cell))) {
                cell.getParent().replaceChild(new TableHeadCellBlock(cell.getChildren(), cell.getParameters()), cell);
            }
        }
    }

    private static List<TableCellBlock> getCells(Block content)
    {
        return content.getBlocks(new ClassBlockMatcher(TableCellBlock.class), Block.Axes.DESCENDANT);
    }

    /**
     * @return the letters and digits of the cell, in lower case: the versions split the words differently
     */
    static String getText(Block cell)
    {
        StringBuilder text = new StringBuilder();
        for (Block block : cell.getBlocks(new ClassBlockMatcher(WordBlock.class), Block.Axes.DESCENDANT)) {
            text.append(((WordBlock) block).getWord());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
