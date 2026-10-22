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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.importer.SOPImportPerson;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;

/**
 * Finds the users of the wiki named in a document to import, e.g. "Körber, Anne Katrin", "ÄD Prof. Längler, Dr.
 * Voigt" or "S. Staar": the names are split in persons, without their titles and initials, and each person is matched
 * with the users whose last name is the last name of the person, or whose user name holds it.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportUserResolver.class)
@Singleton
public class ImportUserResolver
{
    /**
     * The separators of the persons named together, e.g. "U. Zieten / Th. Dressler", "MA ITS und QM".
     */
    private static final Pattern PERSONS = Pattern.compile("\\s*(?:[;/&+]|\\bund\\b|\\band\\b)\\s*");

    /**
     * A word, followed or not by a dot.
     */
    private static final Pattern WORD = Pattern.compile("([\\p{L}'-]+)(\\.?)");

    private static final Pattern SPACES = Pattern.compile("[\\s.]+");

    /**
     * The maximum length of an abbreviated given name, e.g. "Th.".
     */
    private static final int ABBREVIATION_LENGTH = 3;

    /**
     * The titles, functions and teams written with the names, in lower case and without dot.
     */
    private static final Set<String> TITLES = Set.of("dr", "prof", "pd", "äd", "ad", "ltd", "ltg", "arzt", "ärztin",
        "ärztl", "ärztliche", "ärztlicher", "anästhesie", "hr", "fr", "herr", "frau", "oa", "ca", "la", "leitender",
        "leitende", "oberarzt", "oberärztin", "chefarzt", "chefärztin", "ma", "qm", "its", "med", "dipl", "team",
        "pflege", "u");

    private static final int GIVEN_NAME_MATCH = 2;

    private static final int LAST_NAME_MATCH = 3;

    private static final int USER_NAME_MATCH = 2;

    private static final int TRUNCATED_LENGTH = 5;

    private static final int SIMILAR_MATCH = 1;

    /**
     * The minimum length of a last name compared with a typing error, e.g. "Saar" for "Staar".
     */
    private static final int SIMILAR_LENGTH = 4;

    @Inject
    private Logger logger;

    @Inject
    private QueryManager queryManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /**
     * A user of the wiki.
     *
     * @param reference the profile of the user
     * @param firstName the first name of the user
     * @param lastName the last name of the user
     */
    public record User(DocumentReference reference, String firstName, String lastName)
    {
    }

    /**
     * A person named in a document.
     *
     * @param written the name as written, e.g. "Dr. Voigt"
     * @param lastName the last name, e.g. "Voigt"
     * @param givenNames the given names, e.g. "Anne", "Katrin"; without the initials
     */
    record Name(String written, String lastName, List<String> givenNames)
    {
    }

    /**
     * @return the users of the main wiki, holding the users of all the wikis
     */
    public List<User> getUsers()
    {
        XWikiContext context = this.xcontextProvider.get();
        WikiReference wiki = new WikiReference(context.getMainXWiki());
        List<User> users = new ArrayList<>();
        try {
            List<Object[]> rows = this.queryManager.createQuery("select doc.fullName, user.first_name, user.last_name "
                + "from Document doc, doc.object(XWiki.XWikiUsers) as user", Query.XWQL).setWiki(wiki.getName())
                .execute();
            for (Object[] row : rows) {
                users.add(new User(this.resolver.resolve((String) row[0], wiki), StringUtils.defaultString(
                    (String) row[1]), StringUtils.defaultString((String) row[2])));
            }
        } catch (QueryException e) {
            this.logger.warn("Failed to list the users of the wiki: [{}].", e.getMessage());
        }
        return users;
    }

    /**
     * @param names the names written in a document, e.g. "ÄD Prof. Längler, Dr. Voigt"
     * @param users the users of the wiki
     * @return the persons named, each with the users who may be this person
     */
    public List<SOPImportPerson> resolve(String names, List<User> users)
    {
        List<SOPImportPerson> persons = new ArrayList<>();
        for (Name name : parse(names)) {
            persons.add(new SOPImportPerson(name.written(), find(name, users)));
        }
        return persons;
    }

    /**
     * @param names the names written in a document
     * @return the persons named: a comma after a single last name separates it from the given names (e.g. "Körber,
     *     Anne Katrin", "Staar,S."), else it separates two persons (e.g. "Längler, Dr. Voigt")
     */
    static List<Name> parse(String names)
    {
        List<Name> persons = new ArrayList<>();
        for (String group : PERSONS.split(StringUtils.defaultString(names))) {
            String[] parts = group.split(",");
            List<String> first = getWords(parts[0]);
            if (parts.length == 2 && first.size() == 1 && !hasTitle(parts[1])) {
                persons.add(new Name(group.trim(), first.get(0), getWords(parts[1])));
            } else {
                for (String part : parts) {
                    List<String> words = getWords(part);
                    if (!words.isEmpty()) {
                        persons.add(new Name(part.trim(), words.get(words.size() - 1),
                            words.subList(0, words.size() - 1)));
                    }
                }
            }
        }
        return persons;
    }

    /**
     * @return the words of a name, without the titles and the initials (e.g. "S.", "Th.")
     */
    private static List<String> getWords(String text)
    {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group(1);
            boolean abbreviated = !matcher.group(2).isEmpty() && word.length() <= ABBREVIATION_LENGTH;
            if (word.length() > 1 && !abbreviated && !isTitle(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static boolean isTitle(String word)
    {
        return TITLES.contains(word.toLowerCase(Locale.ROOT));
    }

    private static boolean hasTitle(String text)
    {
        return Arrays.stream(SPACES.split(text.trim())).anyMatch(ImportUserResolver::isTitle);
    }

    /**
     * @return the users who may be the person, the most likely first
     */
    static List<DocumentReference> find(Name name, List<User> users)
    {
        record Match(User user, int score)
        {
        }
        List<Match> matches = new ArrayList<>();
        for (User user : users) {
            int score = getScore(name, user);
            if (score > 0) {
                matches.add(new Match(user, score));
            }
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed());
        return matches.stream().map(match -> match.user().reference()).toList();
    }

    private static int getScore(Name name, User user)
    {
        int score = 0;
        if (matches(name.lastName(), user.lastName())) {
            score = LAST_NAME_MATCH;
        } else if (holds(user.reference().getName(), name.lastName())) {
            score = USER_NAME_MATCH;
        } else if (isSimilar(name.lastName(), user)) {
            score = SIMILAR_MATCH;
        }
        if (score > 0 && name.givenNames().stream().anyMatch(given -> matches(given, user.firstName())
            || Arrays.stream(user.firstName().split("[\\s-]+")).anyMatch(first -> matches(given, first)))) {
            score += GIVEN_NAME_MATCH;
        }
        return score;
    }

    /**
     * @return whether two names are the same, the umlauts written with or without "e" (e.g. "Körber", "Koerber")
     */
    private static boolean matches(String name, String otherName)
    {
        return !otherName.isEmpty() && (transliterate(name).equals(transliterate(otherName))
            || stripAccents(name).equals(stripAccents(otherName)));
    }

    /**
     * @return whether a user name holds a last name, or its beginning for the long ones (e.g. "KoerbAn" for
     *     "Körber"), or is this name (e.g. the author of a document written by its user name)
     */
    private static boolean holds(String userName, String lastName)
    {
        for (String user : List.of(transliterate(userName), stripAccents(userName))) {
            for (String last : List.of(transliterate(lastName), stripAccents(lastName))) {
                boolean truncated = last.length() >= TRUNCATED_LENGTH
                    && user.contains(last.substring(0, TRUNCATED_LENGTH));
                if (last.length() > 2 && (user.contains(last) || truncated)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return whether the last name differs by one letter (missing, added or different) from the last name of the user
     *     or from a part of its user name, e.g. "Saar" for "Staar", "SStaar"
     */
    private static boolean isSimilar(String lastName, User user)
    {
        String last = transliterate(lastName);
        if (last.length() < SIMILAR_LENGTH) {
            return false;
        }
        if (getDistance(last, transliterate(user.lastName())) <= 1) {
            return true;
        }
        String userName = transliterate(user.reference().getName());
        for (int length = last.length() - 1; length <= last.length() + 1; length++) {
            for (int start = 0; start + length <= userName.length(); start++) {
                if (getDistance(last, userName.substring(start, start + length)) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return the number of letters to insert, delete or replace to change a text into another (Levenshtein)
     */
    static int getDistance(String text, String other)
    {
        int[] previous = new int[other.length() + 1];
        int[] current = new int[other.length() + 1];
        for (int j = 0; j <= other.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= text.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= other.length(); j++) {
                int replace = previous[j - 1] + (text.charAt(i - 1) == other.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(replace, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[other.length()];
    }

    private static String transliterate(String name)
    {
        String text = name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("ß", "ss");
        return stripAccents(text);
    }

    private static String stripAccents(String name)
    {
        String text = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return text.replaceAll("\\p{M}", "").replaceAll("[^a-z]", "");
    }
}
