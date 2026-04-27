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
package org.xwiki.contrib.sopmanager.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.contrib.rights.RightsWriter;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.contrib.rights.WritableSecurityRule;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.RuleState;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

@ComponentTest
class DefaultSOPManagerTest
{
    private static final LocalDocumentReference SOP_CLASS =
        new LocalDocumentReference(List.of("SOPManager", "Code"), "ControlledDocumentClass");

    @InjectMockComponents
    private DefaultSOPManager sopManager;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @MockComponent
    @Named("current")
    @SuppressWarnings("unchecked")
    private DocumentReferenceResolver<String> currentStringDocRefResolver;

    @MockComponent
    private Logger logger;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private RightsWriter rightsWriter;

    @MockComponent
    @Named("recycling")
    private RulesObjectWriter rulesObjectWriter;

    @MockComponent
    @SuppressWarnings("unchecked")
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("compact")
    @SuppressWarnings("unchecked")
    private EntityReferenceSerializer<String> compactSerializer;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private XWikiContext context;
    private XWiki wiki;
    private XWikiDocument sopDoc;
    private BaseObject sopObj;
    private DocumentReference currentUser;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        this.sopDoc = mock(XWikiDocument.class);
        this.sopObj = mock(BaseObject.class);
        this.currentUser = new DocumentReference("xwiki", List.of("XWiki"), "Admin");

        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);
        when(this.context.getUserReference()).thenReturn(this.currentUser);

        when(this.localizationManager.getTranslationPlain(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.localizationManager.getTranslationPlain(anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WritableSecurityRule writableSecurityRule = mock(WritableSecurityRule.class);
        when(this.rightsWriter.createRule(any(), anyList(), eq(List.of(Right.EDIT)), eq(RuleState.ALLOW)))
            .thenReturn(writableSecurityRule);

        doNothing().when(this.rulesObjectWriter).persistRulesToObjects(any(), any(), any(), any());
    }

    @Test
    void updateDocumentReviewStateApproveIncrementsEmptyRevisionToOne() throws Exception
    {
        DocumentReference documentReference = new DocumentReference("xwiki", List.of("Sandbox"), "WebHome");
        DocumentReference revisionOwner = new DocumentReference("xwiki", List.of("XWiki"), "RevisionOwner");
        DocumentReference revisedBy = new DocumentReference("xwiki", List.of("XWiki"), "RevisedBy");

        XWikiDocument revisionOwnerDoc = mock(XWikiDocument.class);
        XWikiDocument revisedByDoc = mock(XWikiDocument.class);

        when(this.wiki.getDocument(documentReference, this.context)).thenReturn(this.sopDoc);
        when(this.wiki.getDocument(revisionOwner, this.context)).thenReturn(revisionOwnerDoc);
        when(this.wiki.getDocument(revisedBy, this.context)).thenReturn(revisedByDoc);

        when(this.sopDoc.getXObject(SOP_CLASS)).thenReturn(this.sopObj);

        when(this.sopObj.getStringValue("status")).thenReturn("submittedForApproval");
        when(this.sopObj.getStringValue("revisionNumber")).thenReturn(null);
        when(this.sopObj.getLargeStringValue("revisionOwner")).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.sopObj.getLargeStringValue("revisedBy")).thenReturn("xwiki:XWiki.RevisedBy");

        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.RevisionOwner")).thenReturn(revisionOwner);
        when(this.currentStringDocRefResolver.resolve("xwiki:XWiki.RevisedBy")).thenReturn(revisedBy);

        when(this.serializer.serialize(revisionOwner)).thenReturn("xwiki:XWiki.RevisionOwner");
        when(this.serializer.serialize(revisedBy)).thenReturn("xwiki:XWiki.RevisedBy");

        when(revisionOwnerDoc.getXObject(new LocalDocumentReference("XWiki", "XWikiUsers"))).thenReturn(null);
        when(revisedByDoc.getXObject(new LocalDocumentReference("XWiki", "XWikiUsers"))).thenReturn(null);

        String result = this.sopManager.updateDocumentReviewState("approve", documentReference);

        assertEquals("sopManager.reviewPage.approve.success", result);
        verify(this.sopObj).setStringValue("revisionNumber", "1");
        verify(this.sopObj).setStringValue("status", "approved");
        verify(this.rulesObjectWriter).persistRulesToObjects(any(), eq(this.sopDoc), any(), eq(this.context));
        verify(this.wiki).saveDocument(eq(this.sopDoc), eq("sopManager.reviewPage.approve"), eq(this.context));
        verify(this.observationManager).notify(
            argThat(event -> event.getClass().getSimpleName().equals("ApprovedEvent")),
            eq("org.xwiki.contrib:application-sopmanager-api"),
            eq(this.sopDoc)
        );
    }
}
