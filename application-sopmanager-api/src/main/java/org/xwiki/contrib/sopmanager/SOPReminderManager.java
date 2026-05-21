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
package org.xwiki.contrib.sopmanager;

import org.xwiki.component.annotation.Role;

/**
 * Interface for managing reminder notifications.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface SOPReminderManager
{
    /**
     * Sends SOP workflow reminder notifications that are due on the current day.
     * <p>
     * Implementations should check controlled documents with an upcoming planned revision date and notify the
     * responsible users according to the SOP reminder schedule. Reviewers are reminded when a document is still waiting
     * for review 8, 7, 6, 5, 4, or 3 weeks before the planned revision date. Approvers are reminded when a document is
     * waiting for approval 2 or 1 weeks before the planned revision date.
     * </p>
     */
    void sendDueReminders();
}
