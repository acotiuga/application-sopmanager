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
package org.xwiki.contrib.sopmanager.notification.event;

import java.util.Map;
import java.util.Set;

/**
 * Event fired as a reminder when a document release due date is approaching and approve action is needed.
 *
 * @version $Id$
 * @since 1.0
 */
public class ReviewReminderEvent extends AbstractReminderEvent
{
    /**
     * Constructs a {@code ApproveReminderEvent} with the specified target users and event parameters.
     *
     * @param target a set of user identifiers to whom the event is targeted
     * @param eventParams a map containing event parameters
     */
    public ReviewReminderEvent(Set<String> target, Map<String, Object> eventParams)
    {
        super(target, eventParams);
    }

    /**
     * Constructs an empty {@code ApproveReminderEvent} with no target or parameters. This constructor may be used for
     * deserialization or manual population of event data.
     */
    public ReviewReminderEvent()
    {
    }

    @Override
    public boolean matches(Object otherEvent)
    {
        return otherEvent instanceof ReviewReminderEvent;
    }
}
