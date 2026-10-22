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

import java.util.List;

/**
 * Something of a Word document that was not imported as is, described by a translation key and its parameters.
 *
 * @param key the translation key of the warning
 * @param parameters the parameters of the translation
 * @version $Id$
 * @since 1.0
 */
public record ImportWarning(String key, List<Object> parameters)
{
    /**
     * @param key the translation key of the warning
     * @param parameters the parameters of the translation
     */
    public ImportWarning(String key, Object... parameters)
    {
        this(key, List.of(parameters));
    }
}
