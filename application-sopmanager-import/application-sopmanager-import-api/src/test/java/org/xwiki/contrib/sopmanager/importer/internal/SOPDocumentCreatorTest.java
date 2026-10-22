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

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportResult.Compatibility;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SOPDocumentCreator}.
 *
 * @version $Id$
 */
@ComponentTest
class SOPDocumentCreatorTest
{
    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "Editor");

    private static final DocumentReference REFERENCE =
        new DocumentReference("xwiki", List.of("SOPManager", "ANA_AA_9.101_Händedesinfektion"), "WebHome");

    @InjectMockComponents
    private SOPDocumentCreator creator;

    @MockComponent
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument document;

    private BaseObject object;

    private XWikiAttachment attachment;

    @BeforeEach
    void beforeEach() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(USER);
        XWikiDocument existing = mock(XWikiDocument.class);
        this.document = mock(XWikiDocument.class);
        when(this.wiki.getDocument(REFERENCE, this.context)).thenReturn(existing);
        when(existing.clone()).thenReturn(this.document);
        this.object = mock(BaseObject.class);
        when(this.document.newXObject(SOPManager.SOP_CONTROLLED_DOCUMENT_CLASS_REFERENCE, this.context))
            .thenReturn(this.object);
        this.attachment = mock(XWikiAttachment.class);
        when(this.document.setAttachment(anyString(), any(InputStream.class), eq(this.context)))
            .thenReturn(this.attachment);
        doAnswer(invocation -> {
            ((WikiPrinter) invocation.getArgument(1)).print("content");
            return null;
        }).when(this.renderer).render(any(XDOM.class), any(WikiPrinter.class));
        when(this.localizationManager.getTranslationPlain("sopManager.import.saveComment")).thenReturn("Imported");
        when(this.resolver.resolve(anyString())).thenAnswer(
            invocation -> new DocumentReference("xwiki", "XWiki", invocation.getArgument(0)));
        when(this.serializer.serialize(any())).thenAnswer(
            invocation -> "xwiki:XWiki." + ((DocumentReference) invocation.getArgument(0)).getName());
        when(this.wiki.exists(any(DocumentReference.class), eq(this.context))).thenReturn(true);
        when(this.wiki.exists(new DocumentReference("xwiki", "XWiki", "Missing"), this.context)).thenReturn(false);
    }

    private static Date date(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void createWithTheDetailsOfThePageHeaderAndFooter() throws Exception
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        details.readHeader(List.of(List.of("ANA_AA_9.101", "Händedesinfektion")));
        details.setValue("Freigabe am", "05.11.2025");
        details.setValue("Revision geplant", "04.11.2020");
        details.setValue("Überarbeitet von", "K. Beispiel");
        details.setValue("Revision", "05");
        details.setValue("Freigabe durch", "Dr. M. Muster");
        ImportedDocument docx = new ImportedDocument(new XDOM(List.of()), Map.of("photo.png", new byte[] { 1 }), details,
            Compatibility.COMPATIBLE, false, List.of(new ImportWarning("sopManager.import.warning.backgroundImages", 1)),
            DocumentSource.UNKNOWN);

        // The approver selected; the first selected owner that doesn't exist; no reviewer.
        List<ImportWarning> warnings = this.creator.create(REFERENCE, "ANA_AA_9.101_Händedesinfektion", docx,
            Map.of(SOPImportManager.APPROVER, List.of("MMuster"), SOPImportManager.OWNER, List.of("Missing", "KBeispiel")),
            this.context);

        assertEquals(List.of(new ImportWarning("sopManager.import.warning.backgroundImages", 1),
            new ImportWarning("sopManager.import.warning.pastRevisionPlannedDate", "2020-11-04"),
            new ImportWarning("sopManager.import.warning.reviewerNotSet", "K. Beispiel")), warnings);
        verify(this.object).set("approverUser", "xwiki:XWiki.MMuster", this.context);
        verify(this.object).set("revisionOwner", "xwiki:XWiki.KBeispiel", this.context);
        verify(this.object, never()).set(eq("reviewerUser"), any(), any());
        verify(this.document).setSyntax(Syntax.XWIKI_2_1);
        verify(this.document).setTitle("Händedesinfektion");
        verify(this.document).setContent("content");
        verify(this.document).setCreatorReference(USER);
        verify(this.document).setAuthorReference(USER);
        verify(this.document).setContentAuthorReference(USER);
        verify(this.document).setAttachment(eq("photo.png"), any(InputStream.class), eq(this.context));
        verify(this.attachment).setAuthorReference(USER);
        verify(this.object).set("status", "imported", this.context);
        verify(this.object).set("isInReview", 0, this.context);
        verify(this.object).set("pdfTemplate", "SOPManager.Code.GKHPDFTemplateVertical", this.context);
        verify(this.object).set("revisionId", "ANA_AA_9.101", this.context);
        verify(this.object).set("revisionNumber", 5, this.context);
        verify(this.object).set("releaseDate", date(LocalDate.of(2025, 11, 5)), this.context);
        verify(this.object).set("revisionPlannedDate", date(LocalDate.of(2020, 11, 4)), this.context);
        verify(this.wiki).saveDocument(this.document, "Imported", this.context);
    }

    @Test
    void createWithoutDetails() throws Exception
    {
        ImportedDocument docx = new ImportedDocument(new XDOM(List.of()), Map.of(), new SOPDocumentDetails(),
            Compatibility.FIXED_LAYOUT, true, List.of(), DocumentSource.UNKNOWN);

        assertEquals(List.of(), this.creator.create(REFERENCE, "ANA_AA_9.101_Händedesinfektion", docx, Map.of(),
            this.context));

        verify(this.document).setTitle("Händedesinfektion");
        verify(this.object).set("pdfTemplate", "SOPManager.Code.GKHPDFTemplateHorizontal", this.context);
        // The code of the file name.
        verify(this.object).set("revisionId", "ANA_AA_9.101", this.context);
        verify(this.object, never()).set(eq("revisionNumber"), any(), any());
        verify(this.object, never()).set(eq("releaseDate"), any(), any());
        verify(this.document, never()).setAttachment(anyString(), any(InputStream.class), any());
    }

    @Test
    void getTitleAndRevisionId()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        assertEquals("Hausordnung", SOPDocumentCreator.getTitle("Hausordnung", details));
        assertEquals("ANA_AA_9.1", SOPDocumentCreator.getTitle("ANA_AA_9.1", details));
        assertEquals("Titel", SOPDocumentCreator.getTitle("ANA_AA_9.1 - Titel", details));
        assertNull(SOPDocumentCreator.getRevisionId("Hausordnung", details));
        assertEquals(REFERENCE, this.creator.getReference("ANA_AA_9.101_Händedesinfektion", "xwiki"));
    }
}
