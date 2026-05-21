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
package org.xwiki.contrib.sopmanager.internal.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.sopmanager.SOPReminderManager;

import com.xpn.xwiki.plugin.scheduler.AbstractJob;
import com.xpn.xwiki.web.Utils;

/**
 * Scheduled job that sends SOP workflow reminder notifications.
 * <p>
 * This job runs daily and checks controlled documents that are approaching their planned revision date. Reviewers are
 * reminded when a document is still waiting for review 8, 7, 6, 5, 4, or 3 weeks before the planned revision date.
 * Approvers are reminded when a document is waiting for approval 2 or 1 weeks before the planned revision date.
 * </p>
 *
 * <p>
 * Note that the "Job execution context user" property of this scheduler job should be set to {@code XWiki.XWikiGuest}.
 * This is done on purpose to ensure that notifications are sent independently of user notification filters such as the
 * "System Filter" and "Own Events Filter". For example, if the job execution context user is set to {@code superadmin},
 * notifications may be hidden when the "System Filter" is enabled. Initializing the job context user with the guest
 * user prevents these reminder notifications from being filtered out as system or own events.
 * </p>
 *
 * @version $Id$
 * @since 1.0
 */
public class SOPReminderSchedulerJob extends AbstractJob implements Job
{
    @Override
    protected void executeJob(JobExecutionContext jobContext)
    {
        Logger logger = LoggerFactory.getLogger(SOPReminderSchedulerJob.class);
        logger.debug("SOP reminder scheduler job started.");
        try {
            Utils.getComponent(SOPReminderManager.class).sendDueReminders();
        } catch (Exception e) {
            logger.error("Failed to execute SOP reminder scheduler job.", e);
        }
        logger.debug("SOP reminder scheduler job finished.");
    }
}
