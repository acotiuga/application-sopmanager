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

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.sopmanager.importer.SOPImportPerson;
import org.xwiki.contrib.sopmanager.importer.internal.ImportUserResolver.Name;
import org.xwiki.contrib.sopmanager.importer.internal.ImportUserResolver.User;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportUserResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class ImportUserResolverTest
{
    private static final DocumentReference KOERBER = user("KoerbAn");

    private static final DocumentReference VOIGT = user("MVoigt");

    private static final DocumentReference STAAR = user("SStaar");

    private static final DocumentReference STAAR_PETER = user("PStaar");

    private static final List<User> USERS = List.of(new User(KOERBER, "Anne Katrin", "Körber"),
        new User(VOIGT, "Michael", ""), new User(STAAR, "Stefan", "Staar"), new User(STAAR_PETER, "Peter", "Staar"),
        new User(user("Langer"), "Lena", "Langer"));

    @InjectMockComponents
    private ImportUserResolver userResolver;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    private static DocumentReference user(String name)
    {
        return new DocumentReference("xwiki", "XWiki", name);
    }

    @BeforeEach
    void beforeEach()
    {
        XWikiContext context = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(context);
        when(context.getMainXWiki()).thenReturn("xwiki");
    }

    @Test
    void parse()
    {
        // The last name, then the given names.
        assertEquals(List.of(new Name("Körber, Anne Katrin", "Körber", List.of("Anne", "Katrin"))),
            ImportUserResolver.parse("Körber, Anne Katrin"));
        assertEquals(List.of(new Name("Dr. Staar,S.", "Staar", List.of())), ImportUserResolver.parse("Dr. Staar,S.;Ltd.OA"));
        // Two persons, with their titles.
        assertEquals(List.of(new Name("ÄD Prof. Längler", "Längler", List.of()), new Name("Dr. Voigt", "Voigt",
            List.of())), ImportUserResolver.parse("ÄD Prof. Längler, Dr. Voigt"));
        assertEquals(List.of(new Name("U. Zieten", "Zieten", List.of()), new Name("Th. Dressler", "Dressler",
            List.of())), ImportUserResolver.parse("U. Zieten / Th. Dressler"));
        assertEquals(List.of(new Name("Ärztl. Ltg. Hr. Dr. S. Staar", "Staar", List.of())),
            ImportUserResolver.parse("Ärztl. Ltg. Hr. Dr. S. Staar"));
        // The given name first.
        assertEquals(List.of(new Name("Stefan Staar", "Staar", List.of("Stefan"))),
            ImportUserResolver.parse("Stefan Staar"));
        // Teams, not persons.
        assertEquals(List.of(), ImportUserResolver.parse("MA ITS und QM"));
        assertEquals(List.of(), ImportUserResolver.parse(null));
    }

    @Test
    void resolve()
    {
        // By the full name.
        List<SOPImportPerson> persons = this.userResolver.resolve("Körber, Anne Katrin", USERS);
        assertEquals("Körber, Anne Katrin", persons.get(0).getName());
        assertEquals(List.of(KOERBER), persons.get(0).getUsers());
        // By the user name ending with the last name; no user for the other person.
        persons = this.userResolver.resolve("ÄD Prof. Längler, Dr. Voigt", USERS);
        assertEquals(List.of(), persons.get(0).getUsers());
        assertEquals(List.of(VOIGT), persons.get(1).getUsers());
        // Several users with this last name: the one with the given name first.
        assertEquals(List.of(STAAR, STAAR_PETER), this.userResolver.resolve("S. Staar", USERS).get(0).getUsers());
        assertEquals(List.of(STAAR, STAAR_PETER),
            this.userResolver.resolve("Staar, Stefan", USERS).get(0).getUsers());
        assertEquals(List.of(STAAR_PETER, STAAR),
            this.userResolver.resolve("Peter Staar", USERS).get(0).getUsers());
        // A typing error in the last name, e.g. "Saar" for "Staar", whatever the separators.
        assertEquals(List.of(STAAR, STAAR_PETER), this.userResolver.resolve("Dr.Saar,S.", USERS).get(0).getUsers());
        assertEquals(List.of(STAAR, STAAR_PETER), this.userResolver.resolve("Dr. Staar;S.", USERS).get(0).getUsers());
        assertEquals(List.of(VOIGT), this.userResolver.resolve("Dr. Vogt", USERS).get(0).getUsers());
        // Short names are not compared with typing errors.
        assertEquals(List.of(), this.userResolver.resolve("Dr. Vog", USERS).get(0).getUsers());
        // The umlauts written with "e", and the user name of the author of a document.
        assertEquals(List.of(KOERBER), this.userResolver.resolve("A. Koerber", USERS).get(0).getUsers());
        assertEquals(List.of(KOERBER), this.userResolver.resolve("KoerbAn", USERS).get(0).getUsers());
    }

    @Test
    void getDistance()
    {
        assertEquals(0, ImportUserResolver.getDistance("staar", "staar"));
        assertEquals(1, ImportUserResolver.getDistance("saar", "staar"));
        assertEquals(1, ImportUserResolver.getDistance("staar", "stoar"));
        assertEquals(2, ImportUserResolver.getDistance("saar", "stair"));
        assertEquals(4, ImportUserResolver.getDistance("", "saar"));
    }

    @Test
    void getUsers() throws Exception
    {
        Query query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(query);
        when(query.setWiki("xwiki")).thenReturn(query);
        when(query.execute()).thenReturn(List.of(new Object[] { "XWiki.SStaar", "Stefan", "Staar" },
            new Object[] { "XWiki.Guest", null, null }));
        when(this.resolver.resolve("XWiki.SStaar", new WikiReference("xwiki"))).thenReturn(STAAR);
        when(this.resolver.resolve("XWiki.Guest", new WikiReference("xwiki"))).thenReturn(user("Guest"));

        assertEquals(List.of(new User(STAAR, "Stefan", "Staar"), new User(user("Guest"), "", "")),
            this.userResolver.getUsers());

        when(query.execute()).thenThrow(new QueryException("Failed", query, null));
        assertEquals(List.of(), this.userResolver.getUsers());
        assertEquals("Failed to list the users of the wiki: [Failed. Query statement = [null]].",
            this.logCapture.getMessage(0));
    }
}
