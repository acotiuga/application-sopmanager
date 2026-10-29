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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportFolderPages}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportFolderPagesTest
{
    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "Editor");

    private static final DocumentReference BB = new DocumentReference("xwiki", List.of("SOPManager", "BB"), "WebHome");

    private static final DocumentReference AA =
        new DocumentReference("xwiki", List.of("SOPManager", "BB", "AA"), "WebHome");

    private static final DocumentReference CC =
        new DocumentReference("xwiki", List.of("SOPManager", "BB", "AA", "CC"), "WebHome");

    @InjectMockComponents
    private ImportFolderPages folderPages;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    private ImportRights rights;

    private XWikiContext context;

    private XWiki wiki;

    @BeforeEach
    void beforeEach()
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(USER);
        when(this.localizationManager.getTranslationPlain("sopManager.import.folderSaveComment")).thenReturn("Folder");
    }

    @Test
    void create() throws Exception
    {
        // The page of BB exists, the page of CC can't be edited.
        when(this.wiki.exists(BB, this.context)).thenReturn(true);
        when(this.rights.canEdit(AA, this.context)).thenReturn(true);
        XWikiDocument existing = mock(XWikiDocument.class);
        XWikiDocument folder = mock(XWikiDocument.class);
        when(this.wiki.getDocument(AA, this.context)).thenReturn(existing);
        when(existing.clone()).thenReturn(folder);
        when(this.serializer.serialize(AA)).thenReturn("xwiki:SOPManager.BB.AA.WebHome");

        this.folderPages.create(new DocumentReference("xwiki", List.of("SOPManager", "BB", "AA", "CC", "Document"),
            "WebHome"), this.context);

        verify(folder).setSyntax(Syntax.XWIKI_2_1);
        verify(folder).setTitle("AA");
        verify(folder).setContent("{{documentTree root=\"document:xwiki:SOPManager.BB.AA.WebHome\"/}}");
        verify(folder).setCreatorReference(USER);
        verify(folder).setAuthorReference(USER);
        verify(this.wiki).saveDocument(folder, "Folder", this.context);
        verify(this.wiki, never()).getDocument(BB, this.context);
        verify(this.wiki, never()).getDocument(CC, this.context);
    }

    @Test
    void createWithoutFolder() throws Exception
    {
        // A document of the imported folder: no page of folder.
        this.folderPages.create(new DocumentReference("xwiki", List.of("SOPManager", "Document"), "WebHome"),
            this.context);

        verify(this.wiki, never()).exists(any(DocumentReference.class), any(XWikiContext.class));
        verify(this.wiki, never()).saveDocument(any(), anyString(), any());
    }
}
