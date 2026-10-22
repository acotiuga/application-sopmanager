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

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The preview of a version of a document of an analysis: the page it would be imported as, in the preview mode of
 * the editor.
 *
 * @version $Id$
 */
public class ImportPreviewPage extends ViewPage
{
    private static final By CONTENT = By.id("xwikicontent");

    private final String analysisWindow;

    /**
     * Waits for the preview: the import page sends the content to the preview action.
     *
     * @param analysisWindow the window of the analysis, which opened the preview
     */
    public ImportPreviewPage(String analysisWindow)
    {
        this.analysisWindow = analysisWindow;
        getDriver().waitUntilCondition(driver -> driver.getCurrentUrl().contains("/preview/")
            && !getDriver().findElementsWithoutWaiting(CONTENT).isEmpty());
    }

    /**
     * Closes the window of the preview.
     *
     * @return the analysis, as it was left
     */
    public ImportDocumentsPage close()
    {
        getDriver().close();
        getDriver().switchTo().window(this.analysisWindow);
        return new ImportDocumentsPage();
    }

    /**
     * @return the text of the content of the previewed page
     */
    public String getPreviewContent()
    {
        return getDriver().findElement(CONTENT).getText();
    }

    /**
     * @return the number of images of the content of the previewed page
     */
    public int getImageCount()
    {
        return getDriver().findElements(By.cssSelector("#xwikicontent img")).size();
    }

    /**
     * @return the number of tables of the content of the previewed page
     */
    public int getTableCount()
    {
        return getDriver().findElements(By.cssSelector("#xwikicontent table")).size();
    }
}
