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

/**
 * A valid document that can't be imported, e.g. a PDF without structure (a scan), or a Word 97-2003 document while
 * the office server is not running.
 *
 * @version $Id$
 * @since 1.0
 */
public class UnsupportedDocumentException extends DocumentReadException
{
    private static final long serialVersionUID = 1L;

    private final transient ImportWarning reason;

    /**
     * @param reason why the document can't be imported, translated for the report
     */
    public UnsupportedDocumentException(ImportWarning reason)
    {
        super(reason.key());
        this.reason = reason;
    }

    /**
     * @return why the document can't be imported, translated for the report
     */
    public ImportWarning getReason()
    {
        return this.reason;
    }
}
