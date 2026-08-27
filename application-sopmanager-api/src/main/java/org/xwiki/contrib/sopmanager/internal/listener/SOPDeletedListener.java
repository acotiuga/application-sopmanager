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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.FileManagerStorageManager;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Archives the generated PDF associated with an SOP document after the SOP document is deleted.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(SOPDeletedListener.LISTENER_NAME)
@Singleton
public class SOPDeletedListener extends AbstractEventListener
{
    static final String LISTENER_NAME = "sopManagerSOPDeletedListener";

    private static final String PDF_SECONDARY_TARGET_LOCATION = "pDFSecondaryTargetLocation";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @Inject
    private FileManagerStorageManager fileManagerStorageManager;

    @Inject
    private Logger logger;

    /**
     * Creates a new listener for the {@link DocumentDeletedEvent}.
     */
    public SOPDeletedListener()
    {
        super(LISTENER_NAME, new DocumentDeletedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument deletedDocument = (XWikiDocument) source;
        XWikiDocument originalDocument = deletedDocument.getOriginalDocument();

        BaseObject controlledDocumentObject =
            originalDocument.getXObject(SOPManager.SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE);
        if (controlledDocumentObject == null) {
            return;
        }

        archiveGeneratedPDF(originalDocument, controlledDocumentObject);
    }

    private void archiveGeneratedPDF(XWikiDocument sopDocument, BaseObject controlledDocumentObject)
    {
        String serializedFileReference =
            controlledDocumentObject.getStringValue(PDF_SECONDARY_TARGET_LOCATION);
        if (StringUtils.isBlank(serializedFileReference)) {
            logger.debug("No generated PDF is associated with deleted SOP document [{}].",
                sopDocument.getDocumentReference());
            return;
        }

        try {
            DocumentReference fileReference = currentStringDocRefResolver.resolve(
                serializedFileReference, sopDocument.getDocumentReference());

            fileManagerStorageManager.archiveFile(fileReference, sopDocument.getDocumentReference());

            logger.info("Archived generated PDF [{}] after SOP document [{}] was deleted.",
                fileReference, sopDocument.getDocumentReference());
        } catch (Exception e) {
            /*
             * The SOP document has already been deleted. Archival failures must therefore be logged without being
             * propagated to the document deletion operation.
             */
            logger.error("SOP document [{}] was deleted, but its generated PDF [{}] could not be archived.",
                sopDocument.getDocumentReference(), serializedFileReference, e);
        }
    }
}
