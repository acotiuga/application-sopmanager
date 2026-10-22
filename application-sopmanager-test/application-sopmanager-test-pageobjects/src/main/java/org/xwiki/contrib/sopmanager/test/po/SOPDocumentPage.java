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
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * A SOP document, with its review section at the bottom of the page.
 *
 * @version $Id$
 */
public class SOPDocumentPage extends ViewPage
{
    private static final String PANE = "SOPReviewPagespane";

    private static final By SUCCESS = By.cssSelector(".reviewNotificationSuccess:not(.hidden)");

    private static final By ERROR = By.cssSelector(".reviewNotificationError:not(.hidden)");

    /**
     * @return whether the review section of the document is displayed: the document is in the review process
     */
    public boolean hasReviewSection()
    {
        return !getDriver().findElementsWithoutWaiting(By.id("SOPReviewPagestab")).isEmpty()
            || !getDriver().findElementsWithoutWaiting(By.id(PANE)).isEmpty();
    }

    /**
     * @return the text of the review section, opened if needed
     */
    public String getReviewSectionText()
    {
        return openReviewSection().getText();
    }

    private WebElement openReviewSection()
    {
        WebElement pane = getDriver().findElement(By.id(PANE));
        if (!pane.isDisplayed()) {
            getDriver().findElement(By.id("SOPReviewPageslink")).click();
        }
        // The content of the tab is loaded when the tab is opened.
        getDriver().waitUntilCondition(driver -> pane.isDisplayed()
            && !driver.findElements(By.cssSelector("#" + PANE + " .reviewActions")).isEmpty(), 60);
        return pane;
    }

    /**
     * @param action the identifier of the button of a workflow action, e.g. "submitForReview", "approve"
     * @return whether the button is displayed
     */
    public boolean hasAction(String action)
    {
        return !openReviewSection().findElements(By.id(action)).isEmpty();
    }

    /**
     * Runs a workflow action, and waits for its result.
     *
     * @param action the identifier of the button of the action, e.g. "submitForReview", "approve"
     * @return the message of the result: the success, or the error
     */
    public String runAction(String action)
    {
        openReviewSection().findElement(By.id(action)).click();
        // Approving exports the PDF document, which can take a while the first time.
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(SUCCESS).isEmpty()
            || !getDriver().findElementsWithoutWaiting(ERROR).isEmpty(), 240);
        WebElement success = getDriver().findElementWithoutWaiting(By.cssSelector(".reviewNotificationSuccess"));
        return success.isDisplayed() ? success.getText()
            : "ERROR: " + getDriver().findElementWithoutWaiting(By.cssSelector(".reviewNotificationError")).getText();
    }
}
