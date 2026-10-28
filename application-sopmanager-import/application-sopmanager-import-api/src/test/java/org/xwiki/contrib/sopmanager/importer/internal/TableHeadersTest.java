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
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link TableHeaders}.
 *
 * @version $Id$
 */
class TableHeadersTest
{
    private static TableCellBlock cell(boolean header, String... words)
    {
        List<Block> content = new ArrayList<>();
        for (String word : words) {
            if (!content.isEmpty()) {
                content.add(new SpaceBlock());
            }
            content.add(new WordBlock(word));
        }
        return header ? new TableHeadCellBlock(content) : new TableCellBlock(content);
    }

    private static XDOM table(TableCellBlock... cells)
    {
        return new XDOM(List.of(new TableBlock(List.of(new TableRowBlock(List.of(cells))))));
    }

    @Test
    void collect()
    {
        // A header cell, a header cell whose text is also in a cell that is not a header cell, an empty header cell.
        XDOM word = table(cell(true, "Schublade", "1"), cell(true, "Pflege"), cell(false, "Pflege"), cell(true),
            cell(false, "Tupfer"));

        assertEquals(Set.of("schublade1"), TableHeaders.collect(word));
    }

    @Test
    void apply()
    {
        // The words split differently, e.g. in the PDF exported from the Word document.
        TableCellBlock drawer = cell(false, "S", "chublade", "1");
        drawer.setParameter("colspan", "5");
        TableCellBlock content = cell(false, "Tupfer");
        XDOM pdf = table(drawer, content);

        TableHeaders.apply(pdf, Set.of("schublade1"));

        List<Block> cells = pdf.getChildren().get(0).getChildren().get(0).getChildren();
        TableHeadCellBlock header = assertInstanceOf(TableHeadCellBlock.class, cells.get(0));
        assertEquals(Map.of("colspan", "5"), header.getParameters());
        assertEquals("schublade1", TableHeaders.getText(header));
        assertFalse(cells.get(1) instanceof TableHeadCellBlock);
        assertEquals(content, cells.get(1));
    }
}
