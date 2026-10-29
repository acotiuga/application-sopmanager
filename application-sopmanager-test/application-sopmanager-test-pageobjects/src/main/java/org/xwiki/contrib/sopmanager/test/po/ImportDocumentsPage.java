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
package org.xwiki.contrib.sopmanager.test.po;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The page importing documents as SOP documents: SOPManager.ImportDocuments. The folder to import is chosen, then the
 * sub-folders to import in its tree; their documents are analysed, then the selected versions are imported.
 *
 * @version $Id$
 */
public class ImportDocumentsPage extends ViewPage
{
    private static final By REPORT = By.id("sopImportReport");

    private static final By ANALYSIS = By.id("sopImportAnalysis");

    private static final String NEW_LINE = "\n";

    private static final String TEXT_CONTENT = "textContent";

    private static final String DATA_FILE = "data-file";

    private static final String VALUE = "value";

    private static final String VERSION_CHECKBOX = "input[name=\"file\"]";

    private static final String FOLDER_TREE = "jQuery('#sopImportFolderTree').jstree(true)";

    private static final String RETURN_FOLDER_TREE = "return " + FOLDER_TREE;

    private static final String VAR_FOLDER_TREE = "var tree = " + FOLDER_TREE + ";";

    private static final String FOLDER = "folder";

    private static final String DOCUMENT = "document";

    private static final By PREVIEW_LINK = By.className("sopImportPreviewLink");

    private static final int JOB_TIMEOUT = 180;

    /**
     * A row of the report of an import, or of the other files of an analysis.
     *
     * @param file the name of the file
     * @param compatibility how well the file can be imported
     * @param result the SOP document created for the file, or what happened to the file
     * @param details the messages and the warnings, only for the other files of an analysis
     */
    public record ReportRow(String file, String compatibility, String result, String details)
    {
    }

    /**
     * A version of a document of the analysis.
     *
     * @param file the name of the file
     * @param labels the labels of the version: "Original", "Recommended"
     * @param origin where the file comes from, or why it can't be imported
     * @param selected whether the version is selected for the import
     * @param enabled whether the version can be selected
     */
    public record Version(String file, List<String> labels, String origin, boolean selected, boolean enabled)
    {
    }

    /**
     * @return the page importing documents
     */
    public static ImportDocumentsPage gotoPage()
    {
        getUtil().gotoPage("SOPManager", "ImportDocuments");
        return new ImportDocumentsPage();
    }

    /**
     * @param url the URL of the import page, e.g. of an analysis
     * @return the import page
     */
    public static ImportDocumentsPage gotoURL(String url)
    {
        getUtil().getDriver().get(url);
        return new ImportDocumentsPage();
    }

    /**
     * @return whether the import form is displayed, i.e. the current user can import documents
     */
    public boolean isFormDisplayed()
    {
        return !getDriver().findElementsWithoutWaiting(By.id("sopImportFolderForm")).isEmpty();
    }

    /**
     * @return the error message displayed instead of the form, empty if there's none
     */
    public String getErrorMessage()
    {
        List<WebElement> errors = getDriver().findElementsWithoutWaiting(By.cssSelector("#xwikicontent .errormessage"));
        // The message, without the title of the box.
        String message = errors.isEmpty() ? "" : errors.get(0).getText();
        return message.contains(NEW_LINE) ? StringUtils.substringAfterLast(message, NEW_LINE) : message;
    }

    /**
     * Analyses the documents of a folder and of all its sub-folders, and waits for the analysis.
     *
     * @param folder the folder of the documents, on the server
     * @param types the accepted file types
     * @return the page with the analysis
     */
    public ImportDocumentsPage analyse(String folder, String types)
    {
        return browse(folder).analyse(types);
    }

    /**
     * Displays the tree of the sub-folders of a folder, to select the ones to import.
     *
     * @param folder the folder of the documents, on the server
     * @return the page with the tree of the folder
     */
    public ImportDocumentsPage browse(String folder)
    {
        set("sopImportFolder", folder);
        getDriver().findElement(By.id("sopImportBrowse")).click();
        ImportDocumentsPage page = new ImportDocumentsPage();
        if (page.getFolderError().isEmpty()) {
            getDriver().waitUntilElementIsVisible(By.cssSelector("#sopImportFolderTree .jstree-anchor"));
        }
        return page;
    }

    /**
     * @return the error displayed when the folder to import can't be listed, empty if there's none
     */
    public String getFolderError()
    {
        List<WebElement> errors = getDriver().findElementsWithoutWaiting(By.id("sopImportFolderError"));
        return errors.isEmpty() ? "" : errors.get(0).getText();
    }

    /**
     * @return the paths of the folders of the tree, relative to the folder to import (empty for the folder itself)
     */
    public List<String> getFolders()
    {
        return getTreeNodes(FOLDER, false, false);
    }

    /**
     * @return the documents of the tree, by the path of their files without extension, e.g. "BB/AA/Document" for
     *     "BB/AA/Document.docx" and "BB/AA/Document.pdf"
     */
    public List<String> getTreeDocuments()
    {
        return getTreeNodes(DOCUMENT, false, false);
    }

    /**
     * @return the paths of the files of the documents of the tree, e.g. "BB/AA/Document.docx"
     */
    public List<String> getTreeFiles()
    {
        return getTreeNodes(DOCUMENT, false, true);
    }

    /**
     * @return the paths of the files of the selected documents of the tree, the ones analysed
     */
    public List<String> getSelectedTreeFiles()
    {
        return getTreeNodes(DOCUMENT, true, true);
    }

    @SuppressWarnings("unchecked")
    private List<String> getTreeNodes(String type, boolean selected, boolean files)
    {
        return (List<String>) getDriver().executeScript(VAR_FOLDER_TREE
            + "return Object.values(tree._model.data).filter(node => node.data && node.data.type === arguments[0]"
            + " && (!arguments[1] || tree.is_selected(node))).flatMap(node => arguments[2] ? node.data.paths"
            + " : [node.id.substring(node.id.indexOf(':') + 1)]).sort();", type, selected, files);
    }

    /**
     * @param path the path of a folder of the tree, e.g. "BB/AA"
     * @return the label of the folder in the tree: its name and the number of documents of the folder and of its
     *     sub-folders
     */
    public String getFolderLabel(String path)
    {
        return getTreeLabel(FOLDER + ':' + path);
    }

    /**
     * @param path a document of the tree, by the path of its files without extension, e.g. "BB/AA/Document"
     * @return the label of the document in the tree: the name of its file, or its name and its formats
     */
    public String getDocumentLabel(String path)
    {
        return getTreeLabel(DOCUMENT + ':' + path);
    }

    /**
     * @param path the path of a document of the tree, relative to the imported folder and without extension
     * @return the icon of the document, e.g. "fa fa-file-pdf-o"
     */
    public String getDocumentIcon(String path)
    {
        return (String) getDriver().executeScript(RETURN_FOLDER_TREE + ".get_node(arguments[0]).icon;",
            DOCUMENT + ':' + path);
    }

    private String getTreeLabel(String id)
    {
        return (String) getDriver().executeScript(RETURN_FOLDER_TREE + ".get_node(arguments[0]).text;", id);
    }

    /**
     * @param path the path of a folder of the tree, e.g. "BB/AA"
     * @return whether the folder is expanded, showing its sub-folders and its documents
     */
    public boolean isFolderExpanded(String path)
    {
        return (Boolean) getDriver().executeScript(RETURN_FOLDER_TREE + ".is_open(arguments[0]);",
            FOLDER + ':' + path);
    }

    /**
     * Selects or unselects a folder of the tree, as by checking its box: all its documents are selected or unselected
     * with it.
     *
     * @param path the path of a folder of the tree, e.g. "BB/AA"
     * @param selected whether to select the folder
     */
    public void setFolderSelected(String path, boolean selected)
    {
        setTreeNodeSelected(FOLDER + ':' + path, selected);
    }

    /**
     * Selects or unselects a document of the tree, with all its versions, as by checking its box.
     *
     * @param path a document of the tree, by the path of its files without extension, e.g. "BB/AA/Document"
     * @param selected whether to select the document
     */
    public void setDocumentSelected(String path, boolean selected)
    {
        setTreeNodeSelected(DOCUMENT + ':' + path, selected);
    }

    private void setTreeNodeSelected(String id, boolean selected)
    {
        getDriver().executeScript(VAR_FOLDER_TREE
            + "if (arguments[1]) { tree.select_node(arguments[0]); } else { tree.deselect_node(arguments[0]); }",
            id, selected);
        getDriver().waitUntilCondition(driver -> Boolean.valueOf(selected).equals(getDriver().executeScript(
            RETURN_FOLDER_TREE + ".is_selected(arguments[0]);", id)));
    }

    /**
     * Analyses the selected documents of the tree, and waits for the analysis.
     *
     * @param types the accepted file types
     * @return the page with the analysis
     */
    public ImportDocumentsPage analyse(String types)
    {
        set("sopImportTypes", types);
        getDriver().findElement(By.id("sopImportAnalyse")).click();
        // The page displays the progress of the job, then reloads to display its result.
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(ANALYSIS).isEmpty(),
            JOB_TIMEOUT);
        return new ImportDocumentsPage();
    }

    private void set(String id, String value)
    {
        WebElement input = getDriver().findElement(By.id(id));
        input.clear();
        input.sendKeys(value);
    }

    /**
     * @return the names of the analysed documents
     */
    public List<String> getDocuments()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#sopImportAnalysisTable tr[data-document]"))
            .stream().map(document -> document.getAttribute("data-document")).toList();
    }

    private WebElement getDocument(String name)
    {
        return getDriver().findElementWithoutWaiting(
            By.cssSelector(String.format("#sopImportAnalysisTable tr[data-document=\"%s\"]", name)));
    }

    /**
     * @param name the name of a document
     * @return the SOP data of the recommended version of the document, by property
     */
    public Map<String, String> getDocumentData(String name)
    {
        return getProperties(getDocument(name).findElement(By.className("sopImportRecommendedData")));
    }

    /**
     * @param name the name of a document
     * @param role a role: "owner", "approver", "reviewer"
     * @return the names written in the document for this role
     */
    public String getWrittenNames(String name, String role)
    {
        List<WebElement> written = getRole(name, role).findElements(By.className("sopImportRoleWritten"));
        return written.isEmpty() ? "" : written.get(0).getText();
    }

    private WebElement getRole(String name, String role)
    {
        return getDocument(name).findElement(By.cssSelector(String.format(".sopImportRole[data-role=\"%s\"]", role)));
    }

    private SuggestInputElement getUserPicker(String name, String role)
    {
        return new SuggestInputElement(getRole(name, role).findElement(By.cssSelector("input.suggest-users")));
    }

    /**
     * @param name the name of a document
     * @param role a role: "owner", "approver", "reviewer"
     * @return the user of this role, e.g. "XWiki.SStaar", empty if none
     */
    public String getUser(String name, String role)
    {
        List<String> values = getUserPicker(name, role).getValues();
        return values.isEmpty() ? "" : values.get(0);
    }

    /**
     * Chooses the user of a role with the user picker.
     *
     * @param name the name of a document
     * @param role a role: "owner", "approver", "reviewer"
     * @param search the text to type to find the user
     * @param user the user to choose, e.g. "XWiki.SStaar"
     */
    public void setUser(String name, String role, String search, String user)
    {
        getUserPicker(name, role).clearSelectedSuggestions().sendKeys(search).waitForSuggestions()
            .selectByValue(user).hideSuggestions();
    }

    /**
     * @param name the name of a document
     * @return the formats without version of the document, e.g. "doc"
     */
    public List<String> getMissingFormats(String name)
    {
        return getDocument(name).findElements(By.cssSelector("td.sopImportMissing")).stream()
            .map(cell -> cell.getAttribute("data-format")).toList();
    }

    /**
     * @param name the name of a document
     * @return the notes about the document: the existing page, displayed above its collapsed details
     */
    public String getDocumentNotes(String name)
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector(
                String.format("#sopImportAnalysisTable tr[data-existing-document=\"%s\"] .sopImportExists", name)))
            .stream().map(WebElement::getText).reduce((first, second) -> first + NEW_LINE + second).orElse("");
    }

    /**
     * @param name the name of a document
     * @return {@code true} if the details of the document are collapsed, e.g. because its page exists
     */
    public boolean isCollapsed(String name)
    {
        return !getDocument(name).isDisplayed();
    }

    /**
     * Expands the details of a document whose page exists.
     *
     * @param name the name of a document
     * @return this page
     */
    public ImportDocumentsPage expand(String name)
    {
        getDriver().findElement(By.cssSelector(String.format(
            "#sopImportAnalysisTable tr[data-existing-document=\"%s\"] .sopImportToggle", name))).click();
        getDriver().waitUntilCondition(driver -> getDocument(name).isDisplayed());
        return this;
    }

    /**
     * @param name the name of a document
     * @return the versions of the document, by format: pdf, docx, doc
     */
    public List<Version> getVersions(String name)
    {
        return getDocument(name).findElements(By.cssSelector("td.sopImportFormat[data-file]")).stream().map(cell -> {
            WebElement checkbox = cell.findElement(By.cssSelector(VERSION_CHECKBOX));
            List<String> labels = cell.findElements(By.cssSelector(".sopImportLabels .label")).stream()
                .map(WebElement::getText).toList();
            String origin = cell.findElement(By.className("sopImportOrigin")).getText();
            return new Version(cell.getAttribute(DATA_FILE), labels, origin, checkbox.isSelected(),
                checkbox.isEnabled());
        }).toList();
    }

    private WebElement getVersion(String file)
    {
        return getDriver().findElementWithoutWaiting(
            By.cssSelector(String.format("#sopImportAnalysisTable td[data-file=\"%s\"]", file)));
    }

    /**
     * @param file the name of a version
     * @return the SOP data read from the version, by property
     */
    public Map<String, String> getVersionData(String file)
    {
        return getProperties(getVersion(file).findElement(By.className("sopImportVersionData")));
    }

    /**
     * @param file the name of a version
     * @return the text telling how much of the text of the recommended version is in the version, empty if none
     */
    public String getTextMatch(String file)
    {
        List<WebElement> match = getVersion(file).findElements(By.className("sopImportTextMatch"));
        return match.isEmpty() ? "" : match.get(0).getText();
    }

    /**
     * @param container an element holding properties
     * @return the properties, by name
     */
    static Map<String, String> getProperties(WebElement container)
    {
        Map<String, String> properties = new LinkedHashMap<>();
        for (WebElement value : container.findElements(By.cssSelector(".sopImportProperties [data-property]"))) {
            properties.put(value.getAttribute("data-property"),
                StringUtils.normalizeSpace(value.getAttribute(TEXT_CONTENT)));
        }
        return properties;
    }

    /**
     * Selects a version for the import, which unselects the other versions of its document.
     *
     * @param file the name of the version
     */
    public void select(String file)
    {
        WebElement checkbox = getVersion(file).findElement(By.cssSelector(VERSION_CHECKBOX));
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
    }

    /**
     * Unselects a version for the import.
     *
     * @param file the name of the version
     */
    public void unselect(String file)
    {
        WebElement checkbox = getVersion(file).findElement(By.cssSelector(VERSION_CHECKBOX));
        if (checkbox.isSelected()) {
            checkbox.click();
        }
    }

    /**
     * @return the paths of the folders displayed above their documents in the analysis, relative to the analysed
     *     folder (empty for the folder itself); none when the documents of a single folder are analysed
     */
    public List<String> getAnalysedFolders()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#sopImportAnalysisTable tr.sopImportFolderRow"))
            .stream().map(row -> row.getAttribute("data-folder")).toList();
    }

    /**
     * @return the versions selected for the import
     */
    public List<String> getSelectedFiles()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#sopImportAnalysisTable " + VERSION_CHECKBOX
            + ":checked")).stream().map(checkbox -> checkbox.getAttribute(VALUE)).toList();
    }

    /**
     * @return the text telling the number of selected documents
     */
    public String getSelectedCount()
    {
        return getDriver().findElement(By.id("sopImportSelectedCount")).getText();
    }

    /**
     * Imports the selected versions and waits for the report of the import.
     *
     * @return the page with the report of the import
     */
    public ImportDocumentsPage importSelected()
    {
        getDriver().findElement(By.id("sopImportSubmit")).click();
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(REPORT).isEmpty(),
            JOB_TIMEOUT);
        return new ImportDocumentsPage();
    }

    /**
     * @param file the name of a version
     * @return whether the version can be previewed
     */
    public boolean hasPreview(String file)
    {
        return !getVersion(file).findElements(PREVIEW_LINK).isEmpty();
    }

    /**
     * Opens the preview of a version with its button: the page it would be imported as, in the preview mode of the
     * editor, in a new window.
     *
     * @param file the name of a version
     * @return the preview, in the new window
     */
    public ImportPreviewPage preview(String file)
    {
        String analysisWindow = getDriver().getWindowHandle();
        Set<String> windows = getDriver().getWindowHandles();
        getVersion(file).findElement(PREVIEW_LINK).click();
        getDriver().waitUntilCondition(driver -> driver.getWindowHandles().size() > windows.size());
        String previewWindow = getDriver().getWindowHandles().stream().filter(window -> !windows.contains(window))
            .findFirst().orElseThrow();
        getDriver().switchTo().window(previewWindow);
        return new ImportPreviewPage(analysisWindow);
    }

    /**
     * @return the files of the analysed folder that are not documents to import
     */
    public List<ReportRow> getOtherFiles()
    {
        return getRows("sopImportOtherFiles");
    }

    /**
     * @return the title of the list of imported documents, with their number
     */
    public String getImportedTitle()
    {
        return getDriver().findElement(By.id("sopImportImportedTitle")).getText();
    }

    /**
     * @return the title of the list of documents that were not imported, with their number
     */
    public String getNotImportedTitle()
    {
        return getDriver().findElement(By.id("sopImportNotImportedTitle")).getText();
    }

    /**
     * @return the imported documents: the result is the title of the SOP document
     */
    public List<ReportRow> getImported()
    {
        return getRows("sopImportImported");
    }

    /**
     * @return the documents that were not imported
     */
    public List<ReportRow> getNotImported()
    {
        return getRows("sopImportNotImported");
    }

    private List<ReportRow> getRows(String id)
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#" + id + " tbody tr")).stream()
            .map(row -> {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                return new ReportRow(cells.get(0).getText(), cells.get(1).getText(), cells.get(2).getText(),
                    cells.size() > 3 ? cells.get(3).getText() : "");
            }).toList();
    }

    /**
     * @param file the name of a file of the report
     * @return the link to the SOP document created for the file
     */
    public WebElement getDocumentLink(String file)
    {
        return getDriver().findElement(By.cssSelector(
            String.format("#sopImportImported tr[data-file=\"%s\"] .sopImportDocument a", file)));
    }

    /**
     * @return the messages of the log of the analysis or of the import
     */
    public List<String> getLog()
    {
        // The log is collapsed: read the text of its items even when they are hidden.
        return getDriver().findElementsWithoutWaiting(By.cssSelector(".job-status .log .log-item")).stream()
            .map(item -> StringUtils.normalizeSpace(item.getAttribute(TEXT_CONTENT))).toList();
    }

    /**
     * @return the error of the analysis or of the import of the folder, empty if there's none
     */
    public String getReportError()
    {
        List<WebElement> errors = getDriver().findElementsWithoutWaiting(By.id("sopImportError"));
        return errors.isEmpty() ? "" : errors.get(0).getText();
    }
}
