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

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The home page of the SOP Manager, listing the SOP documents: SOPManager.WebHome.
 *
 * @version $Id$
 */
public class SOPManagerHomePage extends ViewPage
{
    private static final String TITLE = "Title";

    private static final By START_REVIEW = By.className("sopStartReview");

    /**
     * @return the home page of the SOP Manager
     */
    public static SOPManagerHomePage gotoPage()
    {
        getUtil().gotoPage("SOPManager", "WebHome");
        return new SOPManagerHomePage();
    }

    /**
     * @return the table of the SOP documents, once loaded
     */
    public TableLayoutElement getTable()
    {
        TableLayoutElement table = new LiveDataElement("sopManagerTable").getTableLayout();
        table.waitUntilReady();
        return table;
    }

    /**
     * @return whether the link to the import of documents is displayed
     */
    public boolean hasImportLink()
    {
        return !getDriver().findElementsWithoutWaiting(By.id("sopImportDocumentsLink")).isEmpty();
    }

    /**
     * @param title the title of a SOP document
     * @return the number of the row of the document in the table, starting at 1, 0 if it's not in the table
     */
    public int getRow(String title)
    {
        List<WebElement> titles = getTable().getAllCells(TITLE);
        for (int i = 0; i < titles.size(); i++) {
            if (titles.get(i).getText().trim().equals(title)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * @param title the title of a SOP document
     * @return the status of the document, as displayed in the table
     */
    public String getStatus(String title)
    {
        return getTable().getCell("Status", getRow(title)).getText().trim();
    }

    /**
     * @param title the title of a SOP document
     * @return whether the action starting the review of the document is displayed
     */
    public boolean canStartReview(String title)
    {
        return !getTable().findElementsInRow(getRow(title), START_REVIEW).isEmpty();
    }

    /**
     * Starts the review of a document from the table.
     *
     * @param title the title of a SOP document
     * @return the document, with its review section
     */
    public SOPDocumentPage startReview(String title)
    {
        String currentURL = getDriver().getCurrentUrl();
        getTable().findElementInRow(getRow(title), START_REVIEW).click();
        getDriver().waitUntilCondition(driver -> !driver.getCurrentUrl().equals(currentURL));
        return new SOPDocumentPage();
    }
}
