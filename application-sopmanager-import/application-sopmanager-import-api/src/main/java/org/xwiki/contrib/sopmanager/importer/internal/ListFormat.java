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

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BulletedListBlock;
import org.xwiki.rendering.block.NumberedListBlock;

/**
 * The format of a list of an imported document: bulleted or numbered. The numbered lists are the standard ordered lists
 * of the wiki, without any list style, whatever the numbers of the imported document: "1)", "a)" and "i." are all
 * imported as "1.". The way the numbers are written in the document is only used to tell its lists apart, e.g. in
 * "1. 2. a) b) 3.", where the letters are nested in the item 2 so that the numbers stay unambiguous.
 *
 * @param bullet whether the list is bulleted
 * @param numbering how the numbers of the list are written in the document, e.g. "a)"; it's not imported
 * @version $Id$
 * @since 1.0
 */
public record ListFormat(boolean bullet, String numbering)
{
    /**
     * A bulleted list.
     */
    public static final ListFormat BULLET = new ListFormat(true, "");

    /**
     * A numbered list whose numbers are not known.
     */
    public static final ListFormat NUMBERED = new ListFormat(false, "");

    /**
     * @param numbering how the numbers of the list are written in the document, e.g. "a)"
     * @return a numbered list
     */
    public static ListFormat numbered(String numbering)
    {
        return new ListFormat(false, numbering);
    }

    /**
     * @param previous the number of the previous item, in a list of this format
     * @param number the number of an item of this format
     * @return whether the item continues the list: the numbers of a numbered list follow each other
     */
    public boolean continues(int previous, int number)
    {
        return this.bullet || number == previous + 1;
    }

    /**
     * @param other the format of the items following an item of this format
     * @return whether these items may interrupt a list of this format, which continues after them: two numbered lists
     *     whose numbers are written differently
     */
    public boolean isInterruptedBy(ListFormat other)
    {
        return !this.bullet && !other.bullet && !equals(other);
    }

    /**
     * @param start the number of the first item, which may continue a list interrupted by paragraphs
     * @return a new list of this format, empty
     */
    public Block newList(int start)
    {
        if (this.bullet) {
            return new BulletedListBlock(List.of());
        }
        Block list = new NumberedListBlock(List.of());
        if (start > 1) {
            list.setParameter("start", String.valueOf(start));
        }
        return list;
    }
}
