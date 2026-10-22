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
 * The format of a list of an imported document: bulleted, or numbered with a style ("1.", "a.", "i."...) and the
 * character closing the numbers, a dot or a parenthesis ("1)", "a)"). The numbered lists have a class, styled by the
 * SOP Manager as in the imported documents: the numbers at the left of the page and the text indented, the numbers
 * closed by a dot or by a parenthesis (the syntax of the wiki has no such list).
 *
 * @param bullet whether the list is bulleted
 * @param style the CSS style of the numbers, e.g. "lower-alpha", empty for decimal numbers
 * @param parenthesis whether the numbers are closed by a parenthesis, e.g. "1)"
 * @version $Id$
 * @since 1.0
 */
public record ListFormat(boolean bullet, String style, boolean parenthesis)
{
    /**
     * A bulleted list.
     */
    public static final ListFormat BULLET = new ListFormat(true, "", false);

    /**
     * A list numbered "1.", "2.".
     */
    public static final ListFormat DECIMAL = new ListFormat(false, "", false);

    /**
     * The class of the numbered lists whose numbers are closed by a dot.
     */
    public static final String DOT_CLASS = "sop-list";

    /**
     * The class of the numbered lists whose numbers are closed by a parenthesis.
     */
    public static final String PARENTHESIS_CLASS = "sop-list-parenthesis";

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
        if (!this.style.isEmpty()) {
            list.setParameter("style", "list-style-type: " + this.style);
        }
        list.setParameter("class", this.parenthesis ? PARENTHESIS_CLASS : DOT_CLASS);
        return list;
    }
}
