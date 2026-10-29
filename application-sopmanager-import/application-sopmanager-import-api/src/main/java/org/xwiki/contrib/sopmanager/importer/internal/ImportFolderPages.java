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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rendering.syntax.Syntax;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Creates the pages of the folders of the imported documents, so that the SOP documents keep the tree of the folders:
 * e.g. SOPManager.BB.WebHome and SOPManager.BB.AA.WebHome for the document "BB/AA/Document.docx".
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportFolderPages.class)
@Singleton
public class ImportFolderPages
{
    private static final String WEB_HOME = "WebHome";

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ImportRights rights;

    /**
     * Creates the pages of the folders of an imported document that don't exist yet, between the SOP Manager space
     * and the document: e.g. SOPManager.BB.WebHome and SOPManager.BB.AA.WebHome for SOPManager.BB.AA.Document.WebHome.
     * Each page is titled after its folder, and displays the tree of its child pages. The pages that the current user
     * can't edit are not created.
     *
     * @param reference the SOP document of an imported document
     * @param context the XWiki context
     * @throws XWikiException if a page can't be saved
     */
    public void create(DocumentReference reference, XWikiContext context) throws XWikiException
    {
        List<SpaceReference> spaces = reference.getSpaceReferences();
        // Not the SOP Manager space, nor the space of the document.
        for (SpaceReference space : spaces.subList(1, Math.max(1, spaces.size() - 1))) {
            DocumentReference folder = new DocumentReference(WEB_HOME, space);
            if (!context.getWiki().exists(folder, context) && this.rights.canEdit(folder, context)) {
                XWikiDocument document = context.getWiki().getDocument(folder, context).clone();
                document.setSyntax(Syntax.XWIKI_2_1);
                document.setTitle(space.getName());
                document.setContent(String.format("{{documentTree root=\"document:%s\"/}}",
                    this.serializer.serialize(folder).replace("~", "~~").replace("\"", "~\"")));
                DocumentReference user = context.getUserReference();
                document.setCreatorReference(user);
                document.setAuthorReference(user);
                document.setContentAuthorReference(user);
                context.getWiki().saveDocument(document,
                    this.localizationManager.getTranslationPlain("sopManager.import.folderSaveComment"), context);
            }
        }
    }
}
