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
package org.xwiki.contrib.sopmanager.test.ui;

import org.junit.jupiter.api.Nested;
import org.xwiki.test.docker.junit5.UITest;

/**
 * All the functional tests of the SOP Manager, sharing the same XWiki instance.
 *
 * @version $Id$
 */
@UITest(
    // An XWiki installed from the standard distribution, with its legacy modules: the job macro used by the import
    // page relies on an API they keep. The standard distribution also provides the macros and the Live Data used by
    // the SOP Manager, and the Groovy macro of the script of the tests.
    standardFlavor = true,
    profiles = "legacy",
    // The office server, converting the Word 97-2003 documents to import.
    office = true,
    properties = {
        // The job macro, used by the import page, requires the programming right, as does the script of the tests
        // writing the documents to import in a folder of the server.
        "xwikiPropertiesAdditionalProperties=test.prchecker.excludePattern="
            + ".*:(SOPManager\\.ImportDocuments|Test\\.Execute\\..*)"
    }
)
class AllIT
{
    @Nested
    class NestedImportDocumentsIT extends ImportDocumentsIT
    {
    }
}
