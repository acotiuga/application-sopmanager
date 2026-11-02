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

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.model.validation.EntityNameValidationConfiguration;
import org.xwiki.model.validation.EntityNameValidationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportPageNames}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportPageNamesTest
{
    @InjectMockComponents
    private ImportPageNames pageNames;

    @MockComponent
    private EntityNameValidationConfiguration configuration;

    @MockComponent
    private EntityNameValidationManager validationManager;

    private static DocumentReference reference(String... spaces)
    {
        return new DocumentReference("xwiki", List.of(spaces), "WebHome");
    }

    @Test
    void removeCode()
    {
        assertEquals("Bestückung Einleitungswagen", ImportPageNames.removeCode("ANA_AA_7.212_Bestückung Einleitungswagen"));
        assertEquals("Umgang mit BTM", ImportPageNames.removeCode("ANA_AA_6.4.6_Umgang mit BTM"));
        // The numbers of the code separated by a space.
        assertEquals("Inhalt Notfallrucksack", ImportPageNames.removeCode("ITS_AA_7 137 Inhalt Notfallrucksack"));
        // A number or a word which are not part of the code.
        assertEquals("5mg Lösung", ImportPageNames.removeCode("ANA_AA_7 5mg Lösung"));
        // No code, or nothing but a code.
        assertEquals("Hausordnung", ImportPageNames.removeCode("Hausordnung"));
        assertEquals("ANA_AA_9.101", ImportPageNames.removeCode("ANA_AA_9.101"));
    }

    @Test
    void getCode()
    {
        assertEquals("ANA_AA_7.212", ImportPageNames.getCode("ANA_AA_7.212_Bestückung"));
        assertEquals("ITS_AA_7.137", ImportPageNames.getCode("ITS_AA_7 137 Inhalt"));
        assertNull(ImportPageNames.getCode("Hausordnung"));
    }

    @Test
    void getReferenceWithoutNamingStrategy()
    {
        // The code is only removed from the name of the document.
        assertEquals(reference("SOPManager", "BB", "ANA_AA_1", "Bestückung Wagen"),
            this.pageNames.getReference("BB/ANA_AA_1/ANA_AA_7.212_Bestückung Wagen", "xwiki"));
        assertEquals(reference("SOPManager", "Hausordnung"), this.pageNames.getReference("Hausordnung", "xwiki"));
        verifyNoInteractions(this.validationManager);
    }

    @Test
    void getReferenceFollowsTheNamingStrategy()
    {
        EntityNameValidation strategy = mock(EntityNameValidation.class);
        when(this.configuration.useTransformation()).thenReturn(true);
        when(this.validationManager.getEntityReferenceNameStrategy()).thenReturn(strategy);
        when(strategy.transform(anyString())).thenAnswer(
            invocation -> ((String) invocation.getArgument(0)).replace(" ", "-"));

        assertEquals(reference("SOPManager", "Mein-Ordner", "Bestückung-Wagen"),
            this.pageNames.getReference("Mein Ordner/ANA_AA_7.212_Bestückung Wagen", "xwiki"));
    }

    @Test
    void getReferenceWithoutConfiguredStrategy()
    {
        when(this.configuration.useTransformation()).thenReturn(true);

        assertEquals(reference("SOPManager", "Mein Ordner"), this.pageNames.getReference("Mein Ordner", "xwiki"));
    }

    @Test
    void isValid()
    {
        DocumentReference reference = reference("SOPManager", "Mein Ordner", "Wagen");
        assertTrue(this.pageNames.isValid(reference));
        verifyNoInteractions(this.validationManager);

        EntityNameValidation strategy = mock(EntityNameValidation.class);
        when(this.configuration.useValidation()).thenReturn(true);
        when(this.validationManager.getEntityReferenceNameStrategy()).thenReturn(strategy);
        when(strategy.isValid("Wagen")).thenReturn(true);
        assertFalse(this.pageNames.isValid(reference));

        // The SOP Manager space is not named by the import.
        when(strategy.isValid("Mein Ordner")).thenReturn(true);
        assertTrue(this.pageNames.isValid(reference));
    }
}
