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

import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.PPR;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.attribute;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.child;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.children;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.path;
import static org.xwiki.contrib.sopmanager.importer.internal.DocxXml.value;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.w3c.dom.Element;

/**
 * The headings and the lists of a Word document, from its styles and numbering definitions.
 *
 * @version $Id$
 * @since 1.0
 */
public class DocxStyles
{
    private static final String OUTLINE_LEVEL = "outlineLvl";

    private static final String STYLE_ID = "styleId";

    private static final String ABSTRACT_NUM_ID = "abstractNumId";

    private static final String ILVL = "ilvl";

    private static final int MAX_HEADING = 6;

    /**
     * The CSS styles of the number formats of Word.
     */
    private final Map<String, Integer> headingLevels = new HashMap<>();

    private final Map<String, ListFormat> listFormats = new HashMap<>();

    private final Map<String, Integer> listStarts = new HashMap<>();

    private final Map<String, String> numberings = new HashMap<>();

    /**
     * @param styles the root of the styles part, can be {@code null}
     * @param numbering the root of the numbering part, can be {@code null}
     */
    public DocxStyles(Element styles, Element numbering)
    {
        Map<String, Element> byId = new HashMap<>();
        for (Element style : children(styles, "style")) {
            byId.put(attribute(style, STYLE_ID), style);
        }
        for (String id : byId.keySet()) {
            int level = getStyleLevel(byId, id, 0);
            if (level > 0) {
                this.headingLevels.put(id, level);
            }
        }
        if (numbering != null) {
            readNumbering(numbering);
        }
    }

    private static int getStyleLevel(Map<String, Element> byId, String id, int depth)
    {
        Element style = byId.get(id);
        if (style == null || depth > 10) {
            return 0;
        }
        String name = value(style, "name").toLowerCase(Locale.ROOT);
        int level;
        if (name.matches("heading [1-6]")) {
            level = Integer.parseInt(name.substring(name.length() - 1));
        } else if ("title".equals(name)) {
            level = 1;
        } else if ("header".equals(name) || "footer".equals(name)) {
            level = 0;
        } else {
            String outline = value(child(style, PPR), OUTLINE_LEVEL);
            String basedOn = value(style, "basedOn");
            if (!outline.isEmpty()) {
                level = toLevel(outline);
            } else {
                level = basedOn.isEmpty() ? 0 : getStyleLevel(byId, basedOn, depth + 1);
            }
        }
        return level;
    }

    private static int toLevel(String outline)
    {
        try {
            int level = Integer.parseInt(outline) + 1;
            return level <= MAX_HEADING ? level : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void readNumbering(Element numbering)
    {
        Map<String, Element> abstracts = new HashMap<>();
        for (Element abstractNum : children(numbering, "abstractNum")) {
            abstracts.put(attribute(abstractNum, ABSTRACT_NUM_ID), abstractNum);
        }
        for (Element num : children(numbering, "num")) {
            String numId = attribute(num, "numId");
            String abstractNumId = value(num, ABSTRACT_NUM_ID);
            // The lists of the same definition continue each other, unless they start again.
            this.numberings.put(numId, abstractNumId);
            for (Element level : children(abstracts.get(abstractNumId), "lvl")) {
                String key = numId + '/' + attribute(level, ILVL);
                this.listFormats.put(key, getFormat(level));
                this.listStarts.put(key, NumberUtils.toInt(value(level, "start"), 1));
            }
            for (Element override : children(num, "lvlOverride")) {
                String start = value(override, "startOverride");
                if (!start.isEmpty()) {
                    this.numberings.put(numId, numId + "/override");
                    this.listStarts.put(numId + '/' + attribute(override, ILVL), NumberUtils.toInt(start, 1));
                }
            }
        }
    }

    /**
     * @return the format of a level of a list: bulleted, or numbered as told by its numbers (e.g. "lowerLetter") and
     *     its text (e.g. "%1)")
     */
    private static ListFormat getFormat(Element level)
    {
        String format = value(level, "numFmt");
        return "bullet".equals(format) ? ListFormat.BULLET
            : ListFormat.numbered(format + value(level, "lvlText").replaceAll("%\\d", ""));
    }

    /**
     * @param paragraph a paragraph
     * @return the level of the heading (1 to 6), 0 if the paragraph is not a heading
     */
    public int getHeadingLevel(Element paragraph)
    {
        Element properties = child(paragraph, PPR);
        String outline = value(properties, OUTLINE_LEVEL);
        if (!outline.isEmpty()) {
            return toLevel(outline);
        }
        return this.headingLevels.getOrDefault(value(properties, "pStyle"), 0);
    }

    /**
     * @param numId the numbering of a list paragraph
     * @return what identifies the counters of the numbering, shared by the numberings continuing each other
     */
    public String getCounters(String numId)
    {
        return this.numberings.getOrDefault(numId, numId);
    }

    /**
     * @param numId the numbering of a list paragraph
     * @param level the level of the paragraph in the list
     * @return the number of the first item of this level
     */
    public int getListStart(String numId, int level)
    {
        return this.listStarts.getOrDefault(numId + '/' + level, 1);
    }

    /**
     * @param numId the numbering of a list paragraph
     * @param level the level of the paragraph in the list
     * @return the format of the items of this level, numbered if it is not defined
     */
    public ListFormat getListFormat(String numId, int level)
    {
        return this.listFormats.getOrDefault(numId + '/' + level, ListFormat.NUMBERED);
    }

    /**
     * @param paragraph a paragraph
     * @return whether the paragraph is positioned in a frame (text of a fixed layout)
     */
    public static boolean isFramed(Element paragraph)
    {
        return path(paragraph, PPR, "framePr") != null;
    }
}
