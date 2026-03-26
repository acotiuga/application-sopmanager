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
package org.xwiki.contrib.sopmanager.internal.event;

import java.util.Set;

import org.xwiki.eventstream.TargetableEvent;

/**
 * Event fired when a document is submitted for review.
 *
 * @version $Id$
 * @since 1.0
 */
public class SubmittedForReviewEvent implements TargetableEvent
{
    private final Set<String> target;

    /**
     * Default constructor.
     *
     * @param target the list of users targeted by the event.
     */
    public SubmittedForReviewEvent(Set<String> target)
    {
        this.target = target;
    }

    @Override
    public Set<String> getTarget()
    {
        return this.target;
    }

    @Override
    public boolean matches(Object otherEvent)
    {
        return otherEvent instanceof SubmittedForReviewEvent;
    }
}
