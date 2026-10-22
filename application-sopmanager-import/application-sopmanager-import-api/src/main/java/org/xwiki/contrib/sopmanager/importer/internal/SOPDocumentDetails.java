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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * The details of a SOP document found in the page header and footer of a Word document: the code and the title in the
 * header, the release details in the table of the footer ("Freigabe am", "Freigabe durch", "Überarbeitet von",
 * "Revision", "Revision geplant", "Seite").
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPDocumentDetails
{
    /**
     * The code of a SOP document, at the start of the page header or of the file name, e.g. "ANA_AA_7.212".
     */
    public static final Pattern CODE = Pattern.compile("^\\s*(\\p{Lu}{2,6}_\\p{Lu}{1,4}_\\d+(?:\\.\\d+)*)");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d.M.uu")
        .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("M/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /**
     * The labels of the release details written on one line, each followed by its value, e.g. "Freigabe am/durch:
     * 19.07.2022, Dr. S. Staar Erstellung/Prüfung: MA ITS und QM Revision am: 07/2025".
     */
    private static final Pattern INLINE_LABEL = Pattern.compile("(freigabe am/durch|freigabe am|freigabe durch"
        + "|freigegeben am|freigegeben durch|überarbeitet von|erstellt von|erstellung/prüfung|geprüft von"
        + "|revision geplant|überprüfung geplant|revision am|nächste revision|revision|version)\\s*:",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * The path of the original document, printed under the release details, e.g.
     * "W:\Gemeinsam\QM\Dokumentenlenkung\ANA_AA_7.212_Bestückung Einleitungswagen.doc".
     */
    private static final Pattern PATH = Pattern.compile("^(?:[A-Za-z]:\\\\|\\\\\\\\)\\S.*\\.(?:docx?|pdf|odt)$",
        Pattern.CASE_INSENSITIVE);

    /**
     * The name of the original document, printed under the release details, e.g. "ANA_AA_7.212_Bestückung
     * Sectio-Tablett Zentral OP".
     */
    private static final Pattern FILE_NAME =
        Pattern.compile("^\\p{Lu}{2,6}_\\p{Lu}{1,4}_\\d+(?:\\.\\d+)*_\\S.{0,150}$");

    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})[,;\\s]*(.*)$");

    private static final String SEPARATORS = " .:-–_";

    private static final String LABEL_END = ": ";

    private static final String RELEASE_DATE_LABEL = "Freigabe am";

    private static final String APPROVED_BY_LABEL = "Freigabe durch";

    /**
     * The labels of the fields of the release details, in lower case and without colon, checked in this order.
     */
    private static final Map<Pattern, Field> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(Pattern.compile("freigabe am/durch"), Field.RELEASE);
        LABELS.put(Pattern.compile("(nächste )?(revision|überprüfung|prüfung) (geplant|am)"
            + "|nächste (revision|überprüfung)|revision planned|next revision"), Field.REVISION_PLANNED_DATE);
        LABELS.put(Pattern.compile("freigabe am|freigegeben am|freigabedatum|release date|released on"),
            Field.RELEASE_DATE);
        LABELS.put(Pattern.compile("freigabe durch|freigegeben (durch|von)|approved by"), Field.APPROVED_BY);
        LABELS.put(Pattern.compile("(überarbeitet|erstellt|geprüft|bearbeitet) (von|durch)|revised by|created by"
            + "|reviewed by|erstellung/prüfung"), Field.REVISED_BY);
        LABELS.put(Pattern.compile("(revision|version|rev\\.?)( ?(nr\\.?|nummer|\\d+))?|revisionsnummer"),
            Field.REVISION_NUMBER);
        LABELS.put(Pattern.compile("seite|page"), Field.PAGE);
    }

    private String revisionId;

    private String title;

    private LocalDate releaseDate;

    private LocalDate revisionPlannedDate;

    private Integer revisionNumber;

    private String approvedBy;

    private String revisedBy;

    private boolean releaseDetailsFound;

    private String originalDocument;

    private final List<ImportWarning> warnings = new ArrayList<>();

    /**
     * The fields of the table of the release details, recognized by their label.
     */
    enum Field
    {
        /**
         * The release date followed by who approved the document, e.g. "Freigabe am/durch: 19.07.2022, Dr. Staar".
         */
        RELEASE,
        RELEASE_DATE,
        APPROVED_BY,
        REVISED_BY,
        REVISION_NUMBER,
        REVISION_PLANNED_DATE,
        PAGE
    }

    /**
     * @param label the text of a cell of the table of the release details
     * @return the field of this label, {@code null} if it's not a label
     */
    static Field getField(String label)
    {
        String normalized = StringUtils.stripEnd(StringUtils.normalizeSpace(label).toLowerCase(Locale.ROOT),
            LABEL_END);
        return LABELS.entrySet().stream().filter(entry -> entry.getKey().matcher(normalized).matches())
            .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /**
     * Reads the code and the title of the page header: the first line starting with a code, followed by the title on
     * the same line or on the next lines of the same group (cell, frame, text box), or else the first line of the
     * next group.
     *
     * @param groups the lines of text of the page header, by group
     * @return whether a code was found
     */
    boolean readHeader(List<List<String>> groups)
    {
        for (int i = 0; i < groups.size(); i++) {
            List<String> lines = groups.get(i);
            for (int j = 0; j < lines.size(); j++) {
                Matcher matcher = CODE.matcher(lines.get(j));
                if (matcher.find()) {
                    List<String> titleLines = new ArrayList<>();
                    titleLines.add(lines.get(j).substring(matcher.end()));
                    titleLines.addAll(lines.subList(j + 1, lines.size()));
                    String headerTitle = StringUtils.strip(StringUtils.normalizeSpace(String.join(" ", titleLines)),
                        SEPARATORS);
                    if (headerTitle.isEmpty() && i + 1 < groups.size() && !groups.get(i + 1).isEmpty()) {
                        headerTitle = StringUtils.normalizeSpace(groups.get(i + 1).get(0));
                    }
                    setRevisionId(matcher.group(1));
                    setTitle(headerTitle);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads the release details from the rows of a table: the labels in a row, the values in the next row.
     *
     * @param rows the text of the cells of each row
     * @return whether the labels were found
     */
    boolean readReleaseTable(List<List<String>> rows)
    {
        for (int i = 0; i < rows.size(); i++) {
            List<String> labels = rows.get(i);
            if (countLabels(labels) >= 2) {
                List<String> values = i + 1 < rows.size() ? rows.get(i + 1) : List.of();
                for (int column = 0; column < labels.size(); column++) {
                    setValue(labels.get(column), column < values.size() ? values.get(column) : "");
                }
                this.releaseDetailsFound = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the release details written on one line, each label followed by a colon and its value.
     *
     * @param line a line of text
     * @param minLabels the minimum number of labels the line must hold to be read
     * @return whether the line starts with a label of the release details and holds at least the given number of them
     */
    boolean readInline(String line, int minLabels)
    {
        String text = StringUtils.normalizeSpace(line);
        Matcher matcher = INLINE_LABEL.matcher(text);
        List<int[]> labels = new ArrayList<>();
        while (matcher.find()) {
            labels.add(new int[] { matcher.start(), matcher.end(), matcher.end(1) });
        }
        if (labels.isEmpty() || labels.size() < minLabels || labels.get(0)[0] != 0) {
            return false;
        }
        for (int i = 0; i < labels.size(); i++) {
            int[] label = labels.get(i);
            int end = i + 1 < labels.size() ? labels.get(i + 1)[0] : text.length();
            setValue(text.substring(label[0], label[2]), text.substring(label[1], end));
        }
        return true;
    }

    /**
     * Reads the path or the name of the original document, printed under the release details.
     *
     * @param text the text of a paragraph
     * @return whether the text is the path or the name of the original document; the first path is kept
     */
    boolean readOriginalDocument(String text)
    {
        String normalized = StringUtils.normalizeSpace(text);
        if (PATH.matcher(normalized).matches()) {
            this.originalDocument = StringUtils.defaultIfEmpty(this.originalDocument, normalized);
            return true;
        }
        return FILE_NAME.matcher(normalized).matches();
    }

    /**
     * @param cells the text of the cells of a row
     * @return the number of labels of the release details in the row
     */
    static int countLabels(List<String> cells)
    {
        return (int) cells.stream().filter(cell -> getField(cell) != null).count();
    }

    /**
     * Sets a release detail, if it was not set yet.
     *
     * @param label the label of the detail
     * @param rawValue the value of the detail
     */
    void setValue(String label, String rawValue)
    {
        Field field = getField(label);
        if (field == null) {
            return;
        }
        String value = StringUtils.normalizeSpace(rawValue);
        this.releaseDetailsFound = true;
        switch (field) {
            case RELEASE:
                setRelease(value);
                break;
            case RELEASE_DATE:
                this.releaseDate = firstDate(this.releaseDate, label, value);
                break;
            case REVISION_PLANNED_DATE:
                this.revisionPlannedDate = firstDate(this.revisionPlannedDate, label, value);
                break;
            case APPROVED_BY:
                this.approvedBy = StringUtils.defaultIfEmpty(this.approvedBy, value);
                break;
            case REVISED_BY:
                this.revisedBy = StringUtils.defaultIfEmpty(this.revisedBy, value);
                break;
            case REVISION_NUMBER:
                this.revisionNumber = this.revisionNumber == null ? parseRevision(label, value) : this.revisionNumber;
                break;
            default:
                break;
        }
    }

    /**
     * @param value the release date followed by who approved the document, e.g. "19.07.2022, Dr. Staar"
     */
    private void setRelease(String value)
    {
        Matcher matcher = DATE_PREFIX.matcher(value);
        if (matcher.matches()) {
            setValue(RELEASE_DATE_LABEL, matcher.group(1));
            setValue(APPROVED_BY_LABEL, matcher.group(2));
        } else {
            setValue(APPROVED_BY_LABEL, value);
        }
    }

    /**
     * @return the date already set, else the parsed date: the page footer is repeated on each page
     */
    private LocalDate firstDate(LocalDate date, String label, String value)
    {
        return date != null ? date : parseDate(label, value);
    }

    private LocalDate parseDate(String label, String value)
    {
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : List.of(DATE, SHORT_DATE)) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException e) {
                // Try the next format.
            }
        }
        try {
            // A month, e.g. "07/2025": its first day.
            return YearMonth.parse(value, MONTH).atDay(1);
        } catch (DateTimeParseException e) {
            // Not a date.
        }
        addWarning(new ImportWarning("sopManager.import.warning.invalidDate", StringUtils.stripEnd(
            StringUtils.normalizeSpace(label), LABEL_END), value));
        return null;
    }

    private Integer parseRevision(String label, String value)
    {
        if (value.matches("\\d{1,4}")) {
            return Integer.valueOf(value);
        }
        // The revision in the label, e.g. "Version 03", above the date of the version.
        Matcher matcher = NUMBER.matcher(label);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group());
        }
        if (!value.isEmpty()) {
            addWarning(new ImportWarning("sopManager.import.warning.invalidRevision", value));
        }
        return null;
    }

    private void addWarning(ImportWarning warning)
    {
        // The page footer is repeated on each page.
        if (!this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }

    /**
     * @return the code of the document, e.g. "ANA_AA_7.212"
     */
    public String getRevisionId()
    {
        return this.revisionId;
    }

    /**
     * @param revisionId the code of the document, set if it was not set yet
     */
    public void setRevisionId(String revisionId)
    {
        this.revisionId = StringUtils.defaultIfEmpty(this.revisionId, revisionId);
    }

    /**
     * @return the title of the document
     */
    public String getTitle()
    {
        return this.title;
    }

    /**
     * @param title the title of the document, set if it was not set yet
     */
    public void setTitle(String title)
    {
        this.title = StringUtils.defaultIfEmpty(this.title, title);
    }

    /**
     * @return the date of the release ("Freigabe am")
     */
    public LocalDate getReleaseDate()
    {
        return this.releaseDate;
    }

    /**
     * @return the date of the next revision ("Revision geplant")
     */
    public LocalDate getRevisionPlannedDate()
    {
        return this.revisionPlannedDate;
    }

    /**
     * @return the number of the revision ("Revision")
     */
    public Integer getRevisionNumber()
    {
        return this.revisionNumber;
    }

    /**
     * @return who approved the document ("Freigabe durch"), as written in the document
     */
    public String getApprovedBy()
    {
        return this.approvedBy;
    }

    /**
     * @return who revised the document ("Überarbeitet von", "Erstellt von"), as written in the document
     */
    public String getRevisedBy()
    {
        return this.revisedBy;
    }

    /**
     * @return whether the release details were found
     */
    public boolean isReleaseDetailsFound()
    {
        return this.releaseDetailsFound;
    }

    /**
     * @return the path of the original document printed in the document, e.g. "W:\QM\ANA_AA_7.212_Wagen.doc",
     *     {@code null} if none
     */
    public String getOriginalDocument()
    {
        return this.originalDocument;
    }

    /**
     * @return the details that couldn't be read
     */
    public List<ImportWarning> getWarnings()
    {
        return this.warnings;
    }
}
