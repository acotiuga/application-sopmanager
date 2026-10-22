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
package org.xwiki.contrib.sopmanager.script;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SOPScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class SOPScriptServiceTest
{
    private static final DocumentReference DOCUMENT = new DocumentReference("xwiki", "SOPManager", "Page");

    @InjectMockComponents
    private SOPScriptService scriptService;

    @MockComponent
    private SOPManager sopManager;

    @Test
    void reviewProcess()
    {
        when(this.sopManager.updateDocumentReviewState("approve", DOCUMENT)).thenReturn("Approved");
        this.scriptService.addDocumentToReviewProcess(DOCUMENT);
        verify(this.sopManager).addDocumentToReviewProcess(DOCUMENT);
        assertEquals("Approved", this.scriptService.updateDocumentReviewState("approve", DOCUMENT));
    }
}
