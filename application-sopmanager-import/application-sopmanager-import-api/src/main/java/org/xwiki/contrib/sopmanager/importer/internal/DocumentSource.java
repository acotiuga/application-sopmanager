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

/**
 * Where a document comes from, as recorded in its properties and its content: the application that wrote it, who and
 * when, and the original document it was made from.
 *
 * @param application the application that wrote the document, e.g. "Microsoft Office Word", "Aspose" for a Word
 *     document, "Microsoft® Word 2013" for a PDF; {@code null} if unknown
 * @param author who last modified the document, {@code null} if unknown
 * @param created when the document was created, {@code null} if unknown
 * @param modified when the document was last modified, {@code null} if unknown
 * @param printed when the Word document was last printed (e.g. to a PDF), {@code null} if unknown
 * @param originalDocument the path of the original document printed in the document (e.g. in its footer), e.g.
 *     "W:\QM\ANA_AA_7.212_Bestückung Einleitungswagen.doc", {@code null} if none
 * @param compatibilityMode the version of Word whose layout a Word document keeps: below 14 (Word 2010), the
 *     document was converted from a Word 97-2003 document; {@code null} if unknown
 * @version $Id$
 * @since 1.0
 */
public record DocumentSource(String application, String author, Instant created, Instant modified, Instant printed,
    String originalDocument, Integer compatibilityMode)
{
    /**
     * The source of a document whose properties are unknown.
     */
    public static final DocumentSource UNKNOWN = new DocumentSource(null, null, null, null, null, null, null);
}
