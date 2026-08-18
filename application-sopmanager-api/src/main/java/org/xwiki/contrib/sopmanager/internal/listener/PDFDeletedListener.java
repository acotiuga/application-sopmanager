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
package org.xwiki.contrib.sopmanager.internal.listener;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Clears the generated PDF reference from an SOP document when the referenced PDF document is deleted.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(PDFDeletedListener.LISTENER_NAME)
@Singleton
public class PDFDeletedListener extends AbstractEventListener
{
    static final String LISTENER_NAME = "sopManagerPDFDeletedListener";

    private static final LocalDocumentReference ORIGINAL_DETAILS_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "OriginalDocumentDetailsClass");

    private static final String PDF_SECONDARY_TARGET_LOCATION = "pDFSecondaryTargetLocation";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private ContextualLocalizationManager localizationManager;

    @Inject
    private Logger logger;

    /**
     * Creates a new listener for the {@link DocumentDeletedEvent}.
     */
    public PDFDeletedListener()
    {
        super(LISTENER_NAME, new DocumentDeletedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument deletedDocument = (XWikiDocument) source;
        XWikiDocument originalDocument = deletedDocument.getOriginalDocument();
        BaseObject originalDetailsObject = originalDocument.getXObject(ORIGINAL_DETAILS_CLASS);
        if (originalDetailsObject == null) {
            return;
        }

        String backlink = originalDetailsObject.getStringValue("backlink");
        if (StringUtils.isBlank(backlink)) {
            return;
        }

        XWikiContext context = (XWikiContext) data;

        try {
            DocumentReference sourceDocumentReference =
                currentStringDocRefResolver.resolve(backlink, originalDocument.getDocumentReference());
            XWikiDocument sourceDocument = context.getWiki().getDocument(sourceDocumentReference, context);
            if (sourceDocument.isNew()) {
                return;
            }

            BaseObject controlledDocumentObject =
                sourceDocument.getXObject(SOPManager.SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE);
            if (controlledDocumentObject == null) {
                return;
            }

            controlledDocumentObject.setStringValue(PDF_SECONDARY_TARGET_LOCATION, "");
            context.getWiki().saveDocument(sourceDocument,
                localizationManager.getTranslationPlain("sopManager.pdfDeletedListener.clearPDFLocation"), context);
        } catch (Exception e) {
            logger.warn("Failed to clean the PDF reference from SOP document linked to deleted document [{}].",
                deletedDocument.getDocumentReference(), e);
        }
    }
}
