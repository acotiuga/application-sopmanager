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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads the properties of a Word document telling where it comes from: the application that wrote it (Word, or a
 * converter such as Aspose), who and when, and the version of Word whose layout it keeps.
 *
 * @version $Id$
 * @since 1.0
 */
public final class DocxProperties
{
    private static final String COMPATIBILITY_MODE = "compatibilityMode";

    private DocxProperties()
    {
    }

    /**
     * @param docx the Word document
     * @param originalDocument the path of the original document printed in the document, {@code null} if none
     * @return the source of the document; the properties that are missing or not valid are {@code null}
     */
    public static DocumentSource read(DocxPackage docx, String originalDocument)
    {
        Element core = getXml(docx, "docProps/core.xml");
        Element app = getXml(docx, "docProps/app.xml");
        String author = StringUtils.defaultIfEmpty(text(core, "lastModifiedBy"), text(core, "creator"));
        return new DocumentSource(StringUtils.trimToNull(text(app, "Application")), StringUtils.trimToNull(author),
            date(text(core, "created")), date(text(core, "modified")), date(text(core, "lastPrinted")),
            originalDocument, getCompatibilityMode(getXml(docx, "word/settings.xml")));
    }

    private static Element getXml(DocxPackage docx, String name)
    {
        try {
            return docx.getXml(name);
        } catch (DocumentReadException e) {
            // The properties are optional.
            return null;
        }
    }

    private static String text(Element root, String localName)
    {
        if (root == null) {
            return "";
        }
        NodeList elements = root.getElementsByTagNameNS("*", localName);
        return elements.getLength() == 0 ? "" : elements.item(0).getTextContent().trim();
    }

    private static Instant date(String value)
    {
        try {
            return value.isEmpty() ? null : OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer getCompatibilityMode(Element settings)
    {
        if (settings != null) {
            NodeList compatSettings = settings.getElementsByTagNameNS(DocxPackage.W, "compatSetting");
            for (int i = 0; i < compatSettings.getLength(); i++) {
                Element compatSetting = (Element) compatSettings.item(i);
                if (COMPATIBILITY_MODE.equals(compatSetting.getAttributeNS(DocxPackage.W, "name"))) {
                    String value = compatSetting.getAttributeNS(DocxPackage.W, "val");
                    return value.matches("\\d{1,3}") ? Integer.valueOf(value) : null;
                }
            }
        }
        return null;
    }
}
