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

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.sopmanager.importer.internal.SOPDocumentDetails.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SOPDocumentDetails}.
 *
 * @version $Id$
 */
class SOPDocumentDetailsTest
{
    @Test
    void getField()
    {
        assertEquals(Field.RELEASE_DATE, SOPDocumentDetails.getField("Freigabe am:"));
        assertEquals(Field.RELEASE, SOPDocumentDetails.getField("Freigabe am/durch"));
        assertEquals(Field.APPROVED_BY, SOPDocumentDetails.getField(" Freigabe  durch: "));
        assertEquals(Field.REVISED_BY, SOPDocumentDetails.getField("Überarbeitet von:"));
        assertEquals(Field.REVISED_BY, SOPDocumentDetails.getField("Erstellt von"));
        assertEquals(Field.REVISED_BY, SOPDocumentDetails.getField("Erstellung/Prüfung"));
        assertEquals(Field.REVISION_NUMBER, SOPDocumentDetails.getField("Revision"));
        assertEquals(Field.REVISION_NUMBER, SOPDocumentDetails.getField("Version 03"));
        assertEquals(Field.REVISION_PLANNED_DATE, SOPDocumentDetails.getField("Revision geplant"));
        assertEquals(Field.REVISION_PLANNED_DATE, SOPDocumentDetails.getField("Überprüfung geplant"));
        assertEquals(Field.REVISION_PLANNED_DATE, SOPDocumentDetails.getField("Revision am:"));
        assertEquals(Field.PAGE, SOPDocumentDetails.getField("Seite"));
        assertNull(SOPDocumentDetails.getField("Geltungsbereich"));
        assertNull(SOPDocumentDetails.getField("Seite 1 / 2"));
    }

    @Test
    void readHeader()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        assertFalse(details.readHeader(List.of(List.of("Logo"), List.of())));
        // The code alone, the title in the next group.
        assertTrue(details.readHeader(List.of(List.of(), List.of("ANA_AA_7.212."), List.of("Bestückung Wagen"))));
        assertEquals("ANA_AA_7.212", details.getRevisionId());
        assertEquals("Bestückung Wagen", details.getTitle());

        // The code and the title on the same line, without space; the first details are kept.
        details.readHeader(List.of(List.of("ITS_AA_7.137Inhalt")));
        assertEquals("ANA_AA_7.212", details.getRevisionId());
        details = new SOPDocumentDetails();
        details.readHeader(List.of(List.of("ITS_AA_7.137Inhalt", "Notfallrucksack")));
        assertEquals("ITS_AA_7.137", details.getRevisionId());
        assertEquals("Inhalt Notfallrucksack", details.getTitle());
    }

    @Test
    void readReleaseTable()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        assertFalse(details.readReleaseTable(List.of(List.of("Geltungsbereich", "Anästhesie"))));
        assertTrue(details.readReleaseTable(List.of(
            List.of("Freigabe am:", "Freigabe durch:", "Erstellt von", "Version 03", "Überprüfung geplant", "Seite"),
            List.of("3.6.18", "Dr. St. Staar", "U. Zieten", "2.6.2020", "2023", "Seite 1 / 1"))));
        assertEquals(LocalDate.of(2018, 6, 3), details.getReleaseDate());
        assertEquals("Dr. St. Staar", details.getApprovedBy());
        assertEquals("U. Zieten", details.getRevisedBy());
        // The revision is in the label, above the date of the version.
        assertEquals(3, details.getRevisionNumber());
        assertNull(details.getRevisionPlannedDate());
        assertEquals(List.of(new ImportWarning("sopManager.import.warning.invalidDate", "Überprüfung geplant", "2023")),
            details.getWarnings());

        // Labels without values.
        details = new SOPDocumentDetails();
        assertTrue(details.readReleaseTable(List.of(List.of("Freigabe am", "Revision"))));
        assertNull(details.getReleaseDate());
        assertNull(details.getRevisionNumber());
    }

    @Test
    void setValueWarnsOnceAboutInvalidValues()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        details.setValue("Freigabe am:", "23.01.214");
        details.setValue("Freigabe am:", "23.01.214");
        details.setValue("Revision", "a");
        details.setValue("Geltungsbereich", "Anästhesie");
        assertNull(details.getReleaseDate());
        assertNull(details.getRevisionNumber());
        assertEquals(List.of(new ImportWarning("sopManager.import.warning.invalidDate", "Freigabe am", "23.01.214"),
            new ImportWarning("sopManager.import.warning.invalidRevision", "a")), details.getWarnings());

        details.setValue("Freigabe am/durch", "Dr. Muster");
        assertEquals("Dr. Muster", details.getApprovedBy());
    }

    @Test
    void readOriginalDocument()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        assertTrue(details.readOriginalDocument("ANA_AA_7.212_Bestückung Sectio-Tablett Zentral OP"));
        assertNull(details.getOriginalDocument());
        assertTrue(details.readOriginalDocument("  W:\\QM\\ANA_AA_7.212_Wagen.doc "));
        assertTrue(details.readOriginalDocument("\\\\server\\QM\\ANA_AA_7.212_Wagen.pdf"));
        assertFalse(details.readOriginalDocument("Siehe W:\\QM\\ANA_AA_7.212_Wagen.doc"));
        // The first path is kept.
        assertEquals("W:\\QM\\ANA_AA_7.212_Wagen.doc", details.getOriginalDocument());
    }

    @Test
    void readInline()
    {
        SOPDocumentDetails details = new SOPDocumentDetails();
        assertFalse(details.readInline("Die Revision: siehe Freigabe am: 01.01.2020", 2));
        assertFalse(details.readInline("Freigabe am: 01.01.2020", 2));
        assertTrue(details.readInline("Freigabe am: 01.01.2020", 1));
        assertEquals(LocalDate.of(2020, 1, 1), details.getReleaseDate());
        assertTrue(details.readInline("Freigabe durch: Dr. A  Revision: 4", 2));
        assertEquals("Dr. A", details.getApprovedBy());
        assertEquals(4, details.getRevisionNumber());
    }
}
