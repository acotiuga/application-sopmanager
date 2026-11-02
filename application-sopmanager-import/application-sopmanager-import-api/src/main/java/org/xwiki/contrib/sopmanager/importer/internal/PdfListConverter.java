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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ListItemBlock;

/**
 * Converts the lists of a tagged PDF. The label of the items (bullet, number, letter) is either a separate element
 * (LibreOffice) or the start of the text of the item (Word); it gives the type of the list and its first number, the
 * lists of a document being often interrupted by paragraphs. Word also wraps the items in empty lists, one per
 * indentation level: a list following an item is its sub-list, the other ones are flattened.
 *
 * @version $Id$
 * @since 1.0
 */
public class PdfListConverter
{
    /**
     * A bullet, a number (with its parent numbers) or a letter followed by a dot or a parenthesis.
     */
    private static final Pattern LABEL = Pattern.compile("^(?:([•▪●◦·\\-–*o])|(\\(?)(?:(\\d{1,3}(?:\\.\\d{1,3})*)"
        + "|([a-zA-Z]))([.)]))$");

    private static final String LIST = "L";

    private static final String ITEM = "LI";

    private static final String LABEL_TYPE = "Lbl";

    private static final String BODY = "LBody";


    private final PdfStructure structure;

    /**
     * An item of a list.
     *
     * @param format the format of the list of the item
     * @param number the number of the item, 0 if it has none
     * @param content the content of the item
     * @param sublists the lists nested in the item
     */
    private record Item(ListFormat format, int number, List<Block> content, List<Block> sublists)
    {
    }

    /**
     * @param structure the structure tree of the PDF
     */
    public PdfListConverter(PdfStructure structure)
    {
        this.structure = structure;
    }

    /**
     * @param lists consecutive lists of the structure tree, as Word may split a list
     * @param page the page of the lists
     * @return the list blocks: one per kind of consecutive items, an item starting a new list when its number doesn't
     *     follow the previous one
     */
    public List<Block> convert(List<PDStructureElement> lists, PDPage page)
    {
        List<Item> items = new ArrayList<>();
        for (PDStructureElement list : lists) {
            collect(list, page, items);
        }
        return build(nest(items));
    }

    private void collect(PDStructureElement list, PDPage parentPage, List<Item> items)
    {
        PDPage page = PdfStructure.getPage(list, parentPage);
        boolean afterItem = false;
        for (PDStructureElement child : PdfStructure.getElements(list)) {
            String type = this.structure.getType(child);
            if (ITEM.equals(type)) {
                items.add(convertItem(child, PdfStructure.getPage(child, page)));
                afterItem = true;
            } else if (LIST.equals(type) && afterItem) {
                items.get(items.size() - 1).sublists().addAll(convert(List.of(child), page));
            } else if (LIST.equals(type)) {
                collect(child, page, items);
            }
        }
    }

    private Item convertItem(PDStructureElement item, PDPage page)
    {
        PDStructureElement labelElement = this.structure.getElement(item, LABEL_TYPE);
        PDStructureElement body = this.structure.getElement(item, BODY);
        PDStructureElement content = body == null ? item : body;
        List<PdfInlineConverter.Glyph> glyphs = PdfInlineConverter.glyphs(this.structure.getText(content, page,
            child -> isListOrLabel(child)));
        String label;
        if (labelElement != null) {
            label = PdfInlineConverter.text(PdfInlineConverter.glyphs(
                this.structure.getText(labelElement, page, child -> false))).trim();
        } else {
            int end = getLabelEnd(glyphs);
            label = PdfInlineConverter.text(glyphs.subList(0, end)).trim();
            glyphs = glyphs.subList(end, glyphs.size());
        }
        List<Block> sublists = new ArrayList<>();
        for (PDStructureElement child : PdfStructure.getElements(content)) {
            if (LIST.equals(this.structure.getType(child))) {
                sublists.addAll(convert(List.of(child), page));
            }
        }
        Matcher matcher = LABEL.matcher(label);
        return new Item(getFormat(matcher), getNumber(matcher), PdfInlineConverter.convert(glyphs), sublists);
    }

    /**
     * @return the end of the label at the start of the text of an item, up to the first space (a character, or a
     *     gap); 0 if the text doesn't start with a label
     */
    private static int getLabelEnd(List<PdfInlineConverter.Glyph> glyphs)
    {
        int start = 0;
        while (start < glyphs.size() && glyphs.get(start).text().isBlank()) {
            start++;
        }
        int end = start + 1;
        while (end < glyphs.size() && !glyphs.get(end).spaceBefore() && !glyphs.get(end).text().isBlank()) {
            end++;
        }
        boolean label = start < glyphs.size()
            && LABEL.matcher(PdfInlineConverter.text(glyphs.subList(start, end)).trim()).matches();
        return label ? end : 0;
    }

    private boolean isListOrLabel(PDStructureElement element)
    {
        String type = this.structure.getType(element);
        return LIST.equals(type) || LABEL_TYPE.equals(type);
    }

    /**
     * @return the format of a list from the label of an item: numbered for a number or a letter, else bulleted
     */
    private static ListFormat getFormat(Matcher label)
    {
        if (!label.matches() || label.group(1) != null) {
            // No label, or a bullet.
            return ListFormat.BULLET;
        }
        String numbers;
        if (label.group(4) != null) {
            numbers = Character.isUpperCase(label.group(4).charAt(0)) ? "A" : "a";
        } else {
            numbers = label.group(3).contains(".") ? "1.1" : "1";
        }
        return ListFormat.numbered(label.group(2) + numbers + label.group(5));
    }

    private static int getNumber(Matcher label)
    {
        if (!label.matches() || label.group(1) != null) {
            return 0;
        }
        if (label.group(3) != null) {
            // The last number, e.g. 2 for "1.2".
            String[] numbers = label.group(3).split("\\.");
            return Integer.parseInt(numbers[numbers.length - 1]);
        }
        return Character.toLowerCase(label.group(4).charAt(0)) - 'a' + 1;
    }

    /**
     * Nests the items interrupting a numbered list in the last item before them, e.g. the letters of "1. 2. a) b) 3.":
     * all the numbers looking alike once imported, the list would otherwise read 1, 2, 1, 2, 3.
     *
     * @return the items that are not nested, the nested ones being in their sub-lists
     */
    private static List<Item> nest(List<Item> items)
    {
        List<Item> result = new ArrayList<>();
        int index = 0;
        while (index < items.size()) {
            Item item = items.get(index);
            int resume = result.isEmpty() ? -1 : getResume(items, result.get(result.size() - 1), index);
            if (resume < 0) {
                result.add(item);
                index++;
            } else {
                result.get(result.size() - 1).sublists().addAll(build(nest(items.subList(index, resume))));
                index = resume;
            }
        }
        return result;
    }

    /**
     * @return the position of the item continuing the list of the previous item after the items starting at the given
     *     position, -1 if these items don't interrupt the list
     */
    private static int getResume(List<Item> items, Item previous, int start)
    {
        if (previous.format().isInterruptedBy(items.get(start).format())) {
            for (int index = start + 1; index < items.size(); index++) {
                Item item = items.get(index);
                if (item.format().equals(previous.format())) {
                    return previous.format().continues(previous.number(), item.number()) ? index : -1;
                }
            }
        }
        return -1;
    }

    private static List<Block> build(List<Item> items)
    {
        List<Block> lists = new ArrayList<>();
        Block list = null;
        Item previous = null;
        for (Item item : items) {
            if (list == null || !item.format().equals(previous.format())
                || !item.format().continues(previous.number(), item.number())) {
                // Keep the numbers of the document, which continue the lists interrupted by paragraphs.
                list = item.format().newList(item.number());
                lists.add(list);
            }
            previous = item;
            List<Block> content = new ArrayList<>(item.content());
            content.addAll(item.sublists());
            list.addChild(new ListItemBlock(content));
        }
        return lists;
    }
}
