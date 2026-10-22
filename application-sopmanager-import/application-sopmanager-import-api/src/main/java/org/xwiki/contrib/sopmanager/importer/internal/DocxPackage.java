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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The parts of a Word document (Office Open XML package), read from a ZIP archive with limits on its size, and parsed
 * without DTDs nor external entities.
 *
 * @version $Id$
 * @since 1.0
 */
public class DocxPackage
{
    /**
     * The namespace of the elements of the Word documents.
     */
    public static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /**
     * The namespace of the relationships.
     */
    public static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /**
     * The main part of a Word document.
     */
    public static final String DOCUMENT = "word/document.xml";

    private static final int MAX_ENTRIES = 5000;

    private static final long MAX_PART_SIZE = 64L * 1024 * 1024;

    private static final long MAX_TOTAL_SIZE = 256L * 1024 * 1024;

    private static final String RELATIONSHIP = "Relationship";

    private static final String SLASH = "/";

    private static final String PARENT = "/../";

    private final Map<String, byte[]> parts = new HashMap<>();

    private final Map<String, Element> xmlParts = new HashMap<>();

    /**
     * A relationship of a part.
     *
     * @param type the type of the relationship, the last segment of its URI (e.g. "image", "header")
     * @param target the name of the target part in the package, or the URI of an external target
     * @param external whether the target is outside of the package (e.g. the URL of a hyperlink)
     */
    public record Relationship(String type, String target, boolean external)
    {
    }

    /**
     * Reads a Word document.
     *
     * @param file the Word document
     * @throws DocumentReadException if the file is not a Word document, or is too large
     */
    public DocxPackage(Path file) throws DocumentReadException
    {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            long total = 0;
            int count = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new DocumentReadException("Too many parts.");
                }
                if (!entry.isDirectory()) {
                    byte[] content = read(zip, entry);
                    total += content.length;
                    if (total > MAX_TOTAL_SIZE) {
                        throw new DocumentReadException("The document is too large.");
                    }
                    this.parts.put(entry.getName(), content);
                }
            }
        } catch (ZipException e) {
            throw new DocumentReadException("Not a Word document: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new DocumentReadException("Failed to read the document: " + e.getMessage(), e);
        }
        if (!this.parts.containsKey(DOCUMENT)) {
            throw new DocumentReadException("Not a Word document: no main part.");
        }
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws IOException, DocumentReadException
    {
        try (InputStream input = zip.getInputStream(entry);
            ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long size = 0;
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                size += read;
                if (size > MAX_PART_SIZE) {
                    throw new DocumentReadException(String.format("The part [%s] is too large.", entry.getName()));
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /**
     * @param name the name of a part
     * @return the content of the part, {@code null} if there's no such part
     */
    public byte[] getBytes(String name)
    {
        return this.parts.get(name);
    }

    /**
     * @param name the name of an XML part
     * @return the root element of the part, {@code null} if there's no such part
     * @throws DocumentReadException if the part is not well-formed XML
     */
    public Element getXml(String name) throws DocumentReadException
    {
        Element root = this.xmlParts.get(name);
        byte[] content = this.parts.get(name);
        if (root == null && content != null) {
            try {
                root = newDocumentBuilder().parse(new ByteArrayInputStream(content)).getDocumentElement();
            } catch (SAXException | IOException | ParserConfigurationException e) {
                throw new DocumentReadException(String.format("The part [%s] is not valid: %s", name, e.getMessage()),
                    e);
            }
            this.xmlParts.put(name, root);
        }
        return root;
    }

    /**
     * @param partName the name of a part
     * @return the relationships of the part, by identifier
     * @throws DocumentReadException if the relationships are not well-formed XML
     */
    public Map<String, Relationship> getRelationships(String partName) throws DocumentReadException
    {
        int slash = partName.lastIndexOf('/');
        String folder = slash < 0 ? "" : partName.substring(0, slash + 1);
        Element root = getXml(folder + "_rels/" + partName.substring(slash + 1) + ".rels");
        Map<String, Relationship> relationships = new HashMap<>();
        if (root != null) {
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element relationship && RELATIONSHIP.equals(relationship.getLocalName())) {
                    String type = relationship.getAttribute("Type");
                    String target = relationship.getAttribute("Target");
                    boolean external = "External".equals(relationship.getAttribute("TargetMode"));
                    relationships.put(relationship.getAttribute("Id"),
                        new Relationship(type.substring(type.lastIndexOf('/') + 1),
                            external ? target : resolve(folder, target), external));
                }
            }
        }
        return relationships;
    }

    private static String resolve(String folder, String target)
    {
        if (target.startsWith(SLASH)) {
            return target.substring(1);
        }
        String path = folder + target;
        // Resolve the ".." segments, e.g. "word/../customXml/item1.xml".
        for (int parent = path.indexOf(PARENT); parent > 0; parent = path.indexOf(PARENT)) {
            int start = path.lastIndexOf('/', parent - 1);
            path = path.substring(0, start + 1) + path.substring(parent + PARENT.length());
        }
        return path;
    }

    private static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // Report the errors through the exceptions only, not on the standard error output.
        builder.setErrorHandler(new DefaultHandler());
        return builder;
    }
}
