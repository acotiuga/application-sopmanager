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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.sopmanager.SOPManager;
import org.xwiki.contrib.sopmanager.importer.SOPImportFolder;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.model.validation.EntityNameValidationConfiguration;
import org.xwiki.model.validation.EntityNameValidationManager;

/**
 * Names the pages of the imported documents: a page of the SOP Manager space named after the file without the code of
 * the document, below the pages of its folders, the names following the naming strategy of the wiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ImportPageNames.class)
@Singleton
public class ImportPageNames
{
    /**
     * The code of a document at the start of a file name, whose numbers may be separated by a space, e.g.
     * "ITS_AA_7 137 Title".
     */
    private static final Pattern FILE_CODE =
        Pattern.compile("^\\s*(\\p{Lu}{2,6}_\\p{Lu}{1,4}_\\d+(?:[. ]\\d+(?![^\\s._-]))*)");

    private static final String WEB_HOME = "WebHome";

    @Inject
    private EntityNameValidationConfiguration configuration;

    @Inject
    private EntityNameValidationManager validationManager;

    /**
     * @param fileName the name of a file, without extension nor folder
     * @return the code of the document at the start of the name, its numbers separated by dots, {@code null} if it has
     *     none
     */
    public static String getCode(String fileName)
    {
        Matcher matcher = FILE_CODE.matcher(fileName);
        return matcher.find() ? matcher.group(1).replace(' ', '.') : null;
    }

    /**
     * @param fileName the name of a file, without extension nor folder
     * @return the name without the code of the document at its start; the name itself if nothing else is left
     */
    public static String removeCode(String fileName)
    {
        Matcher matcher = FILE_CODE.matcher(fileName);
        String name = matcher.find() ? StringUtils.strip(fileName.substring(matcher.end()), " ._-") : fileName;
        return name.isEmpty() ? fileName : name;
    }

    /**
     * @param baseName the path of the imported file relative to the imported folder, without extension, e.g.
     *     "BB/AA/ANA_AA_7.212_Document"
     * @param wiki the wiki where to import the file
     * @return the SOP document of the imported file, e.g. SOPManager.BB.AA.Document.WebHome
     */
    public DocumentReference getReference(String baseName, String wiki)
    {
        List<String> spaces = new ArrayList<>();
        spaces.add(SOPManager.SOP_MANAGER);
        String[] names = baseName.split(SOPImportFolder.SEPARATOR);
        for (int i = 0; i < names.length; i++) {
            spaces.add(transform(i < names.length - 1 ? names[i] : removeCode(names[i])));
        }
        return new DocumentReference(wiki, spaces, WEB_HOME);
    }

    /**
     * @param reference the SOP document of an imported file
     * @return whether the names of the document and of its folders are allowed by the naming strategy of the wiki
     */
    public boolean isValid(DocumentReference reference)
    {
        EntityNameValidation strategy = this.configuration.useValidation()
            ? this.validationManager.getEntityReferenceNameStrategy() : null;
        if (strategy != null) {
            List<SpaceReference> spaces = reference.getSpaceReferences();
            // Not the SOP Manager space, which is not named by the import.
            return spaces.subList(1, spaces.size()).stream().allMatch(space -> strategy.isValid(space.getName()));
        }
        return true;
    }

    private String transform(String name)
    {
        EntityNameValidation strategy = this.configuration.useTransformation()
            ? this.validationManager.getEntityReferenceNameStrategy() : null;
        return strategy == null ? name : StringUtils.defaultIfBlank(strategy.transform(name), name);
    }
}
