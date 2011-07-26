/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.content;

import static org.jahia.api.Constants.EDIT_WORKSPACE;
import static org.jahia.api.Constants.LIVE_WORKSPACE;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.collections.map.UnmodifiableMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.jahia.api.Constants;
import org.jahia.bin.Jahia;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.decorator.JCRPortletNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Template;
import org.jahia.services.sites.JahiaSitesBaseService;
import org.jahia.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;

import com.ibm.icu.text.Normalizer;

import javax.jcr.*;
import javax.jcr.nodetype.ItemDefinition;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for accessing and manipulation JCR properties.
 * 
 * @author Sergiy Shyrkov
 */
public final class JCRContentUtils {
    
    private  static final Map<String, Boolean> iconsPresence = new ConcurrentHashMap<String, Boolean>(512,0.8f,32);
    private static JCRContentUtils instance;

    private static final Logger logger = LoggerFactory.getLogger(JCRContentUtils.class);
    
    public static boolean check(String icon) {
        Boolean present = iconsPresence.get(icon);
        if (present == null) {
            try {
                present = Jahia.getStaticServletConfig().getServletContext().getResource("/modules/" + icon + ".png") != null;
                iconsPresence.put(icon, present);
            } catch (MalformedURLException e) {
                logger.warn(e.getMessage(), e);
                present = Boolean.FALSE;
            }
        }
        return present;
    }
    
    /**
     * Creates the JCR property value, depending on the type of the corresponding value.
     * 
     * @param objectValue
     *            the object value to be converted
     * @param factory
     *            the {@link ValueFactory} instance
     * @return the JCR property value
     */
    public static Value createValue(Object objectValue, ValueFactory factory) {
        if (objectValue instanceof String) {
            return factory.createValue((String) objectValue);
        } else if (objectValue instanceof Boolean) {
            return factory.createValue((Boolean) objectValue);
        } else if (objectValue instanceof Long) {
            return factory.createValue((Long) objectValue);
        } else if (objectValue instanceof Integer) {
            return factory.createValue(((Integer) objectValue).longValue());
        } else if (objectValue instanceof Calendar) {
            return factory.createValue((Calendar) objectValue);
        } else if (objectValue instanceof Date) {
            Calendar c = new GregorianCalendar();
            c.setTime((Date) objectValue);
            return factory.createValue(c);
        } else if (objectValue instanceof byte[] || objectValue instanceof File) {
            InputStream is = null;
            try {
                is = objectValue instanceof File ? new BufferedInputStream(new FileInputStream((File) objectValue))
                        : new ByteArrayInputStream((byte[]) objectValue);
                return factory.createValue(factory.createBinary(is));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        } else {
            logger.warn("Do not know, how to handle value of type {}", objectValue.getClass()
                    .getName());
        }
        return null;
    }

    

    /**
     * Reverse operation of encodeJCRNamePrefix.
     * Note : this is not yet complete as it depends on the restrictions imposed on XML namespaces as
     * defined here http://www.w3.org/TR/REC-xml-names/
     * @param encodedPrefix
     * @return
     */
    public static String decodeJCRNamePrefix(final String encodedPrefix) {
        String originalPrefix = encodedPrefix.replace("\\3A", ":");
        return originalPrefix;
    }

    /**
     * Can be used to delete the Jackrabbit indexes folders for version and workspaces to force re-indexing on the next repository startup.
     * Note please that this method can only be invoked when the repository is not started (offline).
     * 
     * @param repositoryHome
     *            the repository home folder
     */
    public static void deleteJackrabbitIndexes(File repositoryHome) {
        if (repositoryHome == null || !repositoryHome.isDirectory()) {
            return;
        }
        logger.info("Removing JCR repository indexes in repository {}", repositoryHome);

        // Remove index directories to force re-indexing on next startup
        org.apache.commons.io.FileUtils.deleteQuietly(new File(repositoryHome, "index"));
        File workspaces = new File(repositoryHome, "workspaces");
        org.apache.commons.io.FileUtils.deleteQuietly(new File(new File(workspaces,
                Constants.EDIT_WORKSPACE), "index"));
        org.apache.commons.io.FileUtils.deleteQuietly(new File(new File(workspaces,
                Constants.LIVE_WORKSPACE), "index"));

        logger.info("...done removing index folders.");
    }

    /**
     * Downloads the JCR content to a temporary file. 
     * @param node the JCR node with the file content 
     * @return the target file descriptor
     * @throws IOException in case of an error
     */
    public static File downloadFileContent(JCRNodeWrapper node) throws IOException {
        return downloadFileContent(node, File.createTempFile("data", null));
    }

    /**
     * Downloads the JCR content to a specified file. 
     * @param node the JCR node with the file content 
     * @param targetFile target file to write data into
     * @return the target file descriptor
     * @throws IOException in case of an error
     */
    public static File downloadFileContent(JCRNodeWrapper node, File targetFile) throws IOException {
        InputStream is = node.getFileContent().downloadFile();
        if (is == null) {
            throw new IllegalArgumentException("Provided node has no file content");
        }
        OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile));
        try {
            IOUtils.copy(is, os);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
        return targetFile;
    }

    /**
     * Encode a JCR qualified form name prefix, according to the grammar defined in section 3.2.5.2
     * of the JCR 2.0 specification http://www.day.com/specs/jcr/2.0/3_Repository_Model.html#Names
     * Note : this is not yet complete as it depends on the restrictions imposed on XML namespaces as
     * defined here http://www.w3.org/TR/REC-xml-names/
     * @param originalPrefix
     * @return
     */
    public static String encodeJCRNamePrefix(final String originalPrefix) {
        String encodedPrefix = originalPrefix.replace(":", "\\3A");
        return encodedPrefix;
    }
    
    public static String escapeLocalNodeName(String name) {
        name = name.trim();
        StringBuilder buffer = new StringBuilder(name.length() * 2);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '/' || ch == ':' || ch == '[' || ch == ']'
                || ch == '*' || ch == '|') {
                buffer.append('%');
                buffer.append(Character.toUpperCase(Character.forDigit(ch / 16, 16)));
                buffer.append(Character.toUpperCase(Character.forDigit(ch % 16, 16)));
            } else if (ch == '\t' || ch == '\r' || ch == '\n') {
                buffer.append(' ');
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }
    
    public static String escapeNodePath(String path) {
        StringBuilder buffer = new StringBuilder(path.length() * 2);
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == ':' || ch == '[' || ch == ']'
                || ch == '*' || ch == '|') {
                buffer.append('%');
                buffer.append(Character.toUpperCase(Character.forDigit(ch / 16, 16)));
                buffer.append(Character.toUpperCase(Character.forDigit(ch % 16, 16)));
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }

    /**
     * Returns the next available name for a node, appending if needed numbers.
     * 
     * @param dest the destination node, where the new one will be created
     * @param name the name of the new node
     * @return the next available name for a node, appending if needed numbers
     */
    public static String findAvailableNodeName(Node dest, String name) {
        int i = 1;

        String basename = name;
        int dot = basename.lastIndexOf('.');
        String ext = "";
        if (dot > 0) {
            ext = basename.substring(dot);
            basename = basename.substring(0, dot);
        }
        int und = basename.lastIndexOf('-');
        if (und > -1 && basename.substring(und + 1).matches("[0-9]+")) {
            basename = basename.substring(0, und);
        }

        do {
            try {
                dest.getNode(name);
                String newSuffix = "-" + (i++) + ext;
                name = basename + newSuffix;
                //name has a sizelimit of 32 chars
                if(name.length() > 32) {
                    name = basename.substring(0,(basename.length()<=32?basename.length():32)-newSuffix.length())+newSuffix;
                }
            } catch (RepositoryException e) {
                break;
            }

        } while (true);

        return name;
    }

    public static JCRNodeWrapper findDisplayableNode(JCRNodeWrapper node, RenderContext context) {
        Template template = null;
        JCRNodeWrapper currentNode = node;
        try {
            while (template == null && currentNode != null) {
                try {
                    template = RenderService.getInstance().resolveTemplate(
                            new org.jahia.services.render.Resource(currentNode, "html", null, org.jahia.services.render.Resource.CONFIGURATION_PAGE), context);
                } catch (Exception e) {
                    // template cannot be resolved, so try on the parent
                }
                if (template == null) {
                    currentNode = currentNode.getParent();
                }
            }
        } catch (Exception e) {
            currentNode = node;
        }
        if (currentNode == null) {
            currentNode = node;
        }
        return currentNode;
    }

    /**
     * Generates the JCR node name from the provided text by normalizing it,
     * converting to lower case, replacing spaces with dashes and truncating to
     * ${@code maxLength} characters.
     * 
     * @param text the original text to be used as a source
     * @param maxLength the maximum length of the resulting name (it will be
     *            truncated if needed)
     * @return the JCR node name from the provided text by normalizing it,
     *         converting to lower case, replacing spaces with dashes and
     *         truncating to ${@code maxLength} characters
     */
    public static String generateNodeName(String text, int maxLength) {
        String nodeName = text;
        final char[] chars = Normalizer.normalize(nodeName, Normalizer.NFKD).toCharArray();
        final char[] newChars = new char[chars.length];
        int j = 0;
        for (char aChar : chars) {
            if (CharUtils.isAsciiAlphanumeric(aChar) || aChar == 32 || aChar == '-') {
                newChars[j++] = aChar;
            }
        }
        nodeName = new String(newChars, 0, j).trim().replaceAll(" ", "-").toLowerCase();
        if (nodeName.length() > maxLength) {
            nodeName = nodeName.substring(0, maxLength);
            if (nodeName.endsWith("-") && nodeName.length() > 2) {
                nodeName = nodeName.substring(0, nodeName.length() - 1);
            }
        }

        return StringUtils.isNotEmpty(nodeName) ? nodeName : "untitled";
    }

    /**
     * Returns a list of child nodes of the provided node, matching the specified node type (multiple node types can be specified, separated
     * by a comma).
     * 
     * @param node
     *            the parent node to retrieve children from
     * @param type
     *            the node type to be matched by a retrieved child. Multiple node types can be specified, separated by a comma.
     * @return a list of child nodes of the provided node, matching the specified node type (multiple node types can be specified, separated
     *         by a comma).
     */
    public static List<JCRNodeWrapper> getChildrenOfType(JCRNodeWrapper node, String type) {
        List<JCRNodeWrapper> children = null;
        if (node == null) {
            return null;
        }
        if (type.contains(",")) {
            String[] typesToCheck = type.split(",");
            List<JCRNodeWrapper> matchingChildren = new LinkedList<JCRNodeWrapper>();
            try {
                for (NodeIterator iterator = node.getNodes(); iterator.hasNext();) {
                    Node child = iterator.nextNode();
                    for (String matchType : typesToCheck) {
                        if (child.isNodeType(matchType)) {
                            matchingChildren.add((JCRNodeWrapper) child);
                            break;
                        }
                    }
                }
            } catch (RepositoryException e) {
                logger.warn(e.getMessage(), e);
            }
            children = matchingChildren;
        } else {
            children = getNodes(node, type);
        }
        return children;
    }

    /**
     * Returns a content object key for a node if available. Otherwise returns
     * node name.
     * 
     * @param node
     *            the node to get name
     * @return a content object key for a node if available. Otherwise returns
     *         node name
     * @throws RepositoryException
     */
    public static String getContentNodeName(Node node)
            throws RepositoryException {
        return node.getName();
    }

    /**
     * Returns a node path, composed using only content object keys, i.e.
     * 
     * <code>/mySite/ContentPage_1/ContentPage_19/ContentContainerList_21/ContentContainer_13</code>
     * 
     * @param node
     *            the content node to compute path for
     * @return a node path, composed using only content object keys
     * @throws RepositoryException
     */
    public static String getContentObjectPath(Node node)
            throws RepositoryException {
        
        StringBuilder path = new StringBuilder(64);
        path.append("/").append(getContentNodeName(node));
        Node parent = null;
        try {
            parent = node.getParent();
        } catch (ItemNotFoundException e) {
            parent = null;
        }
        while (parent != null) {
            String name = getContentNodeName(parent);
            path.insert(0, name);
            if (!"/".equals(name) && name.length() > 0) {
                path.insert(0, "/");
            }
            try {
                parent = parent.getParent();
            } catch (ItemNotFoundException e) {
                parent = null;
            }
        }
        
        return path.toString();
    }

    public static NodeIterator getDescendantNodes(JCRNodeWrapper node, String type) {
        try {
            return node.getSession().getWorkspace().getQueryManager().createQuery("select * from ["+type+"] as sel where isdescendantnode(sel,['"+node.getPath()+"'])",
                                                                                  Query.JCR_SQL2).execute().getNodes();
        } catch (InvalidQueryException e) {
            logger.error("Error while retrieving nodes", e);
        } catch (RepositoryException e) {
            logger.error("Error while retrieving nodes", e);
        }
        return NodeIteratorImpl.EMPTY;
    }
    
    /**
     * Get the node or property display name depending on the locale
     *
     * @param item the item to get the label for
     * @param locale current locale
     * @param nodeTypeForSearchingLabel
     * @return the node or property display name depending on the locale
     */
    public static String getDisplayLabel(Object item, Locale locale, ExtendedNodeType nodeTypeForSearchingLabel) {
        if (item != null) {

            try {
                // case of property
                if (item instanceof Property) {
                    Property property = (Property) item;
                    PropertyDefinition propertyDefintion = property.getDefinition();
                    if (propertyDefintion != null && propertyDefintion instanceof ExtendedPropertyDefinition) {
                        ExtendedPropertyDefinition itemDef = (ExtendedPropertyDefinition) propertyDefintion;
                        return itemDef.getLabel(locale,nodeTypeForSearchingLabel);
                    } else {
                        logger.error("PropertyDefinition doesn't implement 'org.jahia.services.content.nodetypes.ExtendedPropertyDefinition'");
                    }
                }
                // case of PropertyDefinition
                else if (item instanceof PropertyDefinition) {
                    if (item instanceof ExtendedPropertyDefinition) {
                        ExtendedPropertyDefinition itemDef = (ExtendedPropertyDefinition) item;
                        return itemDef.getLabel(locale,nodeTypeForSearchingLabel);
                    } else {
                        logger.error("PropertyDefinition doesn't implement 'org.jahia.services.content.nodetypes.ExtendedPropertyDefinition'");
                    }
                }
                // case of node type
                else if (item instanceof NodeType) {
                    NodeType nodeType = (NodeType) item;
                    if (nodeType instanceof ExtendedNodeType) {
                        ExtendedNodeType extendNodeType = (ExtendedNodeType) nodeType;
                        return extendNodeType.getLabel(locale);
                    } else {
                        logger.error("nodeType doesn't implement 'org.jahia.services.content.nodetypes.ExtendedNodeType'");
                    }
                } else {
                    logger.error("Object must be a 'javax.jcr.Property' or 'javax.jcr.nodetype.NodeType'");
                }
            } catch (RepositoryException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }    

    public static String getExpandedName(String name, NamespaceRegistry namespaceRegistry) throws RepositoryException {
        if (!name.startsWith("{")) {
            if (name.contains(":")) {
                name = "{" + namespaceRegistry.getURI(StringUtils.substringBefore(name, ":")) + "}" +
                        StringUtils.substringAfter(name, ":");
            } else {
                name = "{}" + name;
            }
        }
        
        return name;
    }
    
    public static String getIcon(ExtendedNodeType type) throws RepositoryException {
        String icon = getIconsFolder(type) + type.getName().replace(':', '_');
        if (check(icon)) {
            return icon;
        }
        for (ExtendedNodeType nodeType : type.getSupertypes()) {
            icon = getIconsFolder(nodeType) + nodeType.getName().replace(':', '_');
            if (check(icon)) {
                return icon;
            }
        }
        return null;
    }    

    public static String getIcon(JCRNodeWrapper f) throws RepositoryException {
        String folder = JCRContentUtils.getIconsFolder(f.getPrimaryNodeType());
        if (f.isFile()) {
            return folder + "jnt_file_" + FileUtils.getFileIcon(f.getName());
        } else if (f.isPortlet()) {
            try {
                JCRPortletNode portletNode = new JCRPortletNode(f);
                if (portletNode.getContextName().equalsIgnoreCase("/rss")) {
                    return folder + "jnt_portlet_rss";
                } else {
                    return folder + "jnt_portlet";
                }
            } catch (RepositoryException e) {
                return folder + "jnt_portlet";
            }
        } else {
            final ExtendedNodeType type = f.getPrimaryNodeType();
            String icon = JCRContentUtils.getIcon(type);
            return icon;
        }
    }
    
    public static String getIconsFolder(final ExtendedNodeType primaryNodeType) throws RepositoryException {
        String folder = primaryNodeType.getSystemId();
        if (folder.startsWith("system-")) {
            folder = "assets";
        } else {
            final JahiaTemplatesPackage aPackage =
                    ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackage(folder);
            if (aPackage != null) {
                folder = aPackage.getRootFolder().equals("default")?"assets":aPackage.getRootFolder();
            } else {
                folder = "assets"; // todo handle portlets
            }
        }
        folder += "/icons/";
        return folder;
    }

    public static JCRContentUtils getInstance() {
        if (instance == null) {
            throw new UnsupportedOperationException("JCRContentUtils is not initialized yet");
        }
        return instance;
    }

    /**
     * Returns the last part of a path, so the last name. This method supports expanded form names in the path, so the
     * last slash detection will be properly handled.
     * @param path the path to get the name from
     * @return the name of the leaf in the path.
     */
    public static String getNameFromPath(String path) {
        String[] pathNames = splitJCRPath(path);
        return pathNames[pathNames.length-1];
    }

    public static List<JCRNodeWrapper> getNodes(JCRNodeWrapper node, String type) {
        try {
            List<JCRNodeWrapper> res = new ArrayList<JCRNodeWrapper>();
            NodeIterator ni = node.getNodes();
            while (ni.hasNext()) {
                JCRNodeWrapper child = (JCRNodeWrapper) ni.next();
                if (StringUtils.isEmpty(type) || child.isNodeType(type)) {
                    res.add(child);
                }
            }
            return res;
//            return node.getSession().getWorkspace().getQueryManager().createQuery("select * from ["+type+"] as sel where ischildnode(sel,['"+node.getPath()+"'])",
//                                                                                  Query.JCR_SQL2).execute().getNodes();
//        } catch (InvalidQueryException e) {
//            logger.error("Error while retrieving nodes", e);
        } catch (RepositoryException e) {
            logger.error("Error while retrieving nodes", e);
        }
        return new LinkedList<JCRNodeWrapper>();
    }

    /**
     * Little utility method to retrieve or create a path, building it if necessary. For example let's say that we want
     * to get or create the path from a parentNode : messages/inbox . We can simply pass the parent node, the session
     * and the path to check, and it will either retrieve it if it exists, or create it if it doesn't.
     *
     * Please note that this method also checks out the parent nodes.
     * @param session
     * @param parentNode the parent node in from which we want to retrieve the relative path.
     * @param path the path to retrieve or create. Note that this path MUST be relative
     * @param pathNodeType the type to use for the intermediary nodes (and the last path too !) if they need to be
     * created. Usually you'll want to use Constants.JAHIANT_CONTENTLIST here.
     * @return the leaf that is equivalent to the lowest path value.
     * @throws RepositoryException occurs if there is any problem accessing content or creating the nodes.
     */
    public static JCRNodeWrapper getOrAddPath(JCRSessionWrapper session, JCRNodeWrapper parentNode, String path, String pathNodeType) throws RepositoryException {
        String[] subPaths = splitJCRPath(path);
        JCRNodeWrapper node = parentNode;
        for (String subPath : subPaths) {
            if (StringUtils.isNotBlank(subPath) && !"*".equals(subPath)) {
                try {
                    node = node.getNode(subPath);
                    session.checkout(node);
                } catch (PathNotFoundException e) {
                    if (node != null) {
                        session.checkout(node);
                        node = node.addNode(subPath, pathNodeType);
                    }
                }
            }
        }
        return node;
    }

    /**
     * A small utility method to retrieve the parent path of a path that contains expanded form names.
     * @param path the path for which we want to retrieve the parent path.
     * @return the parent path including all names in expanded or qualified form.
     */
    public static String getParentJCRPath(String path) {
        String[] pathNames = splitJCRPath(path);
        StringBuffer parentPath = new StringBuffer();
        // if we are dealing with an absolute path, we add the initial separator
        if (path.startsWith("/")) {
            parentPath.append("/");
        }
        for (int i=0; i < pathNames.length -1; i++) {
            parentPath.append(pathNames[i]);
            if (i < pathNames.length -2) {
                parentPath.append("/");
            }
        }
        return parentPath.toString();
    }

    public static JCRNodeWrapper getParentOfType(JCRNodeWrapper node,
            String type) {
        JCRNodeWrapper matchingParent = null;
        try {
            JCRNodeWrapper parent = node.getParent();
            while (parent != null) {
                if (parent.isNodeType(type)) {
                    matchingParent = parent;
                    break;
                }
                parent = parent.getParent();
            }
        } catch (ItemNotFoundException e) {
            // we reached the hierarchy top
        } catch (RepositoryException e) {
            logger.error("Error while retrieving nodes parent node. Cause: "
                    + e.getMessage(), e);
        }
        return matchingParent;
    }

    public static JCRNodeWrapper getPathFolder(JCRNodeWrapper root, String name, String options, String nodeType) throws RepositoryException {
        JCRNodeWrapper result = root;
        if (options.contains("initials")) {
            String s = "" + Character.toUpperCase(name.charAt(0));
            if (!result.hasNode(s)) {
                result = result.addNode(s, nodeType);
            } else {
                result = result.getNode(s);
            }
        }
        return result;
    }

    private static ExtendedPropertyDefinition getPropertyDefExtension(
            PropertyDefinition propDef) {
        try {
            return NodeTypeRegistry.getInstance().getNodeType(
                    propDef.getDeclaringNodeType().getName()).getPropertyDefinition(
                    propDef.getName());
        } catch (NoSuchNodeTypeException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static PropertyDefinition getPropertyDefinition(NodeType type,
            String property) throws RepositoryException {
        PropertyDefinition foundDefintion = null;
        PropertyDefinition[] pds = type.getDeclaredPropertyDefinitions();
        for (int i = 0; i < pds.length; i++) {
            PropertyDefinition pd = pds[i];
            if (pd.getName().equals(property)) {
                foundDefintion = pd;
                break;
            }
        }

        return foundDefintion;
    }
    
    public static PropertyDefinition getPropertyDefinition(String nodeType,
            String property) throws RepositoryException {
        return getPropertyDefinition(NodeTypeRegistry.getInstance().getNodeType(
                        nodeType), property);
    }

    public static int getPropertyDefSelector(ItemDefinition itemDef) {
        ExtendedPropertyDefinition propDefExtension = null;
        if (itemDef instanceof PropertyDefinition) {
            propDefExtension = getPropertyDefExtension((PropertyDefinition) itemDef);
        }

        return propDefExtension != null ? propDefExtension.getSelector() : 0;
    }

    /**
     * If the node path contains site information (i.e.
     * <code>/sites/&lt;siteKey&gt;/...</code>) this method returns the site key
     * part; otherwise <code>null</code> is returned.
     * 
     * @param jcrNodePath the JCR node path
     * @return if the node path contains site information (i.e.
     *         <code>/sites/&lt;siteKey&gt;/...</code>) this method returns the
     *         site key part; otherwise <code>null</code> is returned
     */
    public static String getSiteKey(String jcrNodePath) {
        return jcrNodePath != null ? (jcrNodePath.startsWith("/sites/") ? StringUtils.substringBetween(jcrNodePath,
                "/sites/", "/") : null) : null;
    }

    public static String getSystemSitePath() {
        return "/sites/" + JahiaSitesBaseService.SYSTEM_SITE_KEY;        
    }

    /**
     * Returns an object value that corresponds to the provided JCR property value depending on its type.
     * 
     * @param propertyValue
     *            the JCR property value to be converted
     * @return the object value
     * @throws RepositoryException
     *             in case of a conversion error
     * @throws ValueFormatException
     *             in case of a conversion error
     */
    public static Object getValue(Value propertyValue) throws ValueFormatException,
            RepositoryException {
        Object value = propertyValue.getString();
        switch (propertyValue.getType()) {
            case PropertyType.BOOLEAN:
                value = Boolean.valueOf(propertyValue.getBoolean());
                break;
            case PropertyType.DATE:
                value = propertyValue.getDate();
                break;
            case PropertyType.DECIMAL:
            case PropertyType.LONG:
                value = Long.valueOf(propertyValue.getDecimal().longValue());
                break;
            case PropertyType.DOUBLE:
                value = Double.valueOf(propertyValue.getDouble());
                break;
        }
        return value;
    }

    /**
     * Used by portlet backends to determine if a user is part of a specific permissionName on a node specified by it's
     * UUID
     *
     *
     * @param workspaceName the name of the workspace in which we load the node from.
     * @param permissionName
     * @param nodeUUID
     * @return
     */
    public static boolean hasPermission(String workspaceName, final String permissionName, final String nodeUUID) {
        try {
            JCRSessionWrapper session = JCRTemplate.getInstance().getSessionFactory().getCurrentUserSession(workspaceName);
            JCRNodeWrapper node = session.getNodeByIdentifier(nodeUUID);
            return node.hasPermission(permissionName);
        } catch (Exception e) {
            logger.error("Error while checking permission "+ permissionName +" for node UUID " + nodeUUID, e);
        }
        return false;
    }

    /**
     * Performs import of JCR data using provided skeleton locations. This method is used when a new virtual site or a new user is created.
     * 
     *
     * @param skeletonLocations
     *            the (pattern-based) location to search for resources. Multiple locations can be provided separated by comma (or any
     *            delimiter, defined in {@link org.springframework.context.ConfigurableApplicationContext#CONFIG_LOCATION_DELIMITERS} )
     * @param targetPath
     *            target JCR path to perform import into
     * @param session
     *            the current JCR session
     * @param replacements
     * @throws IOException
     *             in case of skeleton lookup error
     * @throws InvalidSerializedDataException
     *             import related exception
     * @throws RepositoryException
     *             general JCR exception
     */
    public static void importSkeletons(String skeletonLocations, String targetPath,
                                       JCRSessionWrapper session) throws IOException, InvalidSerializedDataException,
            RepositoryException {
        importSkeletons(skeletonLocations, targetPath, session, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW, null);
    }

    /**
     * Performs import of JCR data using provided skeleton locations. This method is used when a new virtual site or a new user is created.
     * 
     *
     * @param skeletonLocations
     *            the (pattern-based) location to search for resources. Multiple locations can be provided separated by comma (or any
     *            delimiter, defined in {@link org.springframework.context.ConfigurableApplicationContext#CONFIG_LOCATION_DELIMITERS} )
     * @param targetPath
     *            target JCR path to perform import into
     * @param session
     *            the current JCR session
     * @param importUUIDBehavior the {@link javax.jcr.ImportUUIDBehavior} to use during import
     * @param replacements
     * @throws IOException
     *             in case of skeleton lookup error
     * @throws InvalidSerializedDataException
     *             import related exception
     * @throws RepositoryException
     *             general JCR exception
     */
    public static void importSkeletons(String skeletonLocations, String targetPath,
                                       JCRSessionWrapper session, int importUUIDBehavior, Map<String, String> replacements) throws IOException, InvalidSerializedDataException,
            RepositoryException {
        for (Resource resource : SpringContextSingleton.getInstance().getResources(
                skeletonLocations)) {
            logger.info("Importing data using skeleton [{}]", resource);
            InputStream is = null;
            try {
                is = resource.getInputStream();
                session.importXML(targetPath, is, importUUIDBehavior, true, replacements);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    /**
     * Performs import of JCR data using provided skeleton locations. This method is used when a new virtual site or a new user is created.
     *
     *
     * @param skeletonLocations
     *            the (pattern-based) location to search for resources. Multiple locations can be provided separated by comma (or any
     *            delimiter, defined in {@link org.springframework.context.ConfigurableApplicationContext#CONFIG_LOCATION_DELIMITERS} )
     * @param targetPath
     *            target JCR path to perform import into
     * @param session
     *            the current JCR session
     * @param replacements
     * @throws IOException
     *             in case of skeleton lookup error
     * @throws InvalidSerializedDataException
     *             import related exception
     * @throws RepositoryException
     *             general JCR exception
     */
    public static void importSkeletons(String skeletonLocations, String targetPath,
                                       JCRSessionWrapper session, Map<String, String> replacements) throws IOException, InvalidSerializedDataException,
            RepositoryException {
        importSkeletons(skeletonLocations, targetPath, session, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW, replacements);
    }

    
    /**
     * Returns <code>true</code> if the provided node matches the specified node type (multiple node types can be specified, separated by a
     * comma).
     * 
     * @param node
     *            the node to be tested
     * @param type
     *            the node type to be matched. Multiple node types can be specified, separated by a comma.
     * @return <code>true</code> if the provided node matches the specified node type (multiple node types can be specified, separated by a
     *         comma).
     * @throws RepositoryException
     *             in case of a JCR error
     */
    public static boolean isNodeType(JCRNodeWrapper node, String type) throws RepositoryException {
        if (node == null || StringUtils.isEmpty(type)) {
            return false;
        }
        boolean matches = false;
        if (type.contains(",")) {
            String[] types = type.split(",");
            for (String matchType : types) {
                if (node.isNodeType(matchType)) {
                    matches = true;
                    break;
                }
            }
        } else {
            matches = node.isNodeType(type);
        }

        return matches;
    }

    /**
     * Returns <code>true</code> if the provided node matches one of the specified node types.
     * 
     * @param node
     *            the node to be tested
     * @param types
     *            an array of node types to be matched.
     * @return <code>true</code> if the provided node matches one of the specified node types
     * @throws RepositoryException
     *             in case of a JCR error
     */
    public static boolean isNodeType(JCRNodeWrapper node, Iterable<String> types)
            throws RepositoryException {
        if (node == null || types == null) {
            return false;
        }
        boolean matches = false;
        for (String matchType : types) {
            if (node.isNodeType(matchType)) {
                matches = true;
                break;
            }
        }

        return matches;
    }

    /**
     * Returns <code>true</code> if the provided UUID string does not seem like a valid Jackrabbit node UUID. In such a case it comes from a
     * different provide, like VFS.
     * 
     * @param uuid
     *            the UUID string to check
     * @return code>true</code> if the provided UUID string does not seem like a valid Jackrabbit node UUID. In such a case it comes from a
     *         different provide, like VFS
     */
    public static boolean isNotJcrUuid(String uuid) {
        return StringUtils.isEmpty(uuid) || uuid.contains("/");
    }

    public static boolean isValidFilename(String name) {
        return (!name.startsWith(" ") && !name.endsWith(" ") && name.matches("([^\\*:/\\\\<>|?\"])*"));
    }

    /**
     * Validates if the specified name is a valid JCR workspace (either <code>default</code> or <code>live</code>).
     * 
     * @param workspace
     *            the workspace name to check
     * @return <code>true</code> if the specified name is a valid JCR workspace (either <code>default</code> or <code>live</code>);
     *         otherwise returns <code>false</code>
     */
    public static final boolean isValidWorkspace(String workspace) {
        return isValidWorkspace(workspace, false);
    }

    /**
     * Validates if the specified name is a valid JCR workspace (either <code>default</code> or <code>live</code>).
     * 
     * @param workspace
     *            the workspace name to check
     * @param allowBlank set to true if the workspace name is allowed to be null or empty
     * @return <code>true</code> if the specified name is a valid JCR workspace (either <code>default</code> or <code>live</code>);
     *         otherwise returns <code>false</code>
     */
    public static final boolean isValidWorkspace(String workspace, boolean allowBlank) {
        return StringUtils.isEmpty(workspace) ? allowBlank : EDIT_WORKSPACE.equals(workspace)
                || LIVE_WORKSPACE.equals(workspace);
    }

    /**
     * Small utility method to help with proper namespace registration in all JCR providers.
     * @param session
     * @param prefix
     * @param uri
     * @throws RepositoryException
     */
    public static void registerNamespace(Session session, String prefix, String uri) throws RepositoryException {
        NamespaceRegistry namespaceRegistry = session.getWorkspace().getNamespaceRegistry();
        Set<String> prefixes = ImmutableSet.copyOf(namespaceRegistry.getPrefixes());
        if (!prefixes.contains(prefix)) {
            namespaceRegistry.registerNamespace(prefix, uri);
            session.setNamespacePrefix(prefix, uri);
        }

    }


    /**
     * Returns the number of elements in the provided iterator.
     * 
     * @param iterator the item iterator to check the size
     * @return the number of elements in the provided iterator
     */
    public static long size(RangeIterator iterator) {
        long size = iterator.getSize();
        if (size <= 0) {
            size = 0;
            while (iterator.hasNext()) {
                size++;
                iterator.next();
            }
        }

        return size;
    }

    /**
     * Utility method to split a JCR path into names. Note that this method supports expanded name notation (using
     * URIs), such as {http://www.jcp.org/jcr/1.0}read, as it is tricky to split simply using the "/" character when
     * URIs are present.
     * @param path a relative or absolute path, with node names in qualified or expanded form
     * @return an array of String instances that contain the node names in order of the path.
     */
    public static String[] splitJCRPath(String path) {
        List<String> result = new ArrayList<String>();
        int pathPos = 0;

        if (path.startsWith("/")) {
            pathPos++;
        }

        int nextSlashPos = -1;
        do {
            StringBuffer currentName = new StringBuffer();
            if (path.indexOf('{', pathPos) == pathPos) {
                int endingBracketPos = path.indexOf('}', pathPos+1);
                currentName.append(path.substring(pathPos, endingBracketPos+1));
                pathPos = endingBracketPos + 1;
            }
            nextSlashPos = path.indexOf('/', pathPos);
            if (nextSlashPos > -1) {
                currentName.append(path.substring(pathPos, nextSlashPos));
                pathPos = nextSlashPos + 1;
            } else {
                currentName.append(path.substring(pathPos));
            }
            result.add(currentName.toString());
        } while (nextSlashPos > -1);

        return result.toArray(new String[result.size()]);
    }

    /**
     * Convert a path string to encoded path Strings in XPATH queries
     * 
     * @param str
     *            Any string.
     * @return A valid string path suitable for use in XPATH queries
     */
    public static String stringToJCRPathExp(String str) {
        return ISO9075.encode(Text.escapeIllegalJcrChars(str));
    }

    /**
     * Convert a string to a JCR search expression literal, suitable for use in
     * jcr:contains() (inside XPath) or contains (SQL2). The characters - and " have
     * special meaning, and may be escaped with a backslash to obtain their
     * literal value. See JSR-283 spec v2.0, Sec. 4.6.6.19.
     * 
     * @param str
     *            Any string.
     * @return A valid string literal suitable for use in
     *         JCR contains clauses, including enclosing quotes.
     */
    public static String stringToJCRSearchExp(String str) {
        // Escape ' and \ everywhere, preceding them with \ except when \
        // appears
        // in one of the combinations \" or \-
        return stringToQueryLiteral(Text.escapeIllegalXpathSearchChars(str));
    }


    /**
     * Convert a string to a literal, suitable for inclusion
     * in a query. See JSR-283 spec v2.0, Sec. 4.6.6.19.
     * 
     * @param str
     *            Any string.
     * @return A valid JCR query string literal, including enclosing quotes.
     */
    public static String stringToQueryLiteral(String str) {
        // Single quotes needed for jcr:contains()
        return "'" + str.replaceAll("'", "''") + "'";
    }

    /**
     * Decode an encoded JCR local name encoded with the encodeJCRLocalName method
     * Note : this implementation is not yet complete as it does not handle the XML
     * restrictions yet, only the JCR ones.
     * @param encodedLocalName
     * @return
     */
    public static String unescapeLocalNodeName(final String encodedLocalName) {
        return Text.unescapeIllegalJcrChars(encodedLocalName);
    }

    private Map<String, String> fileExtensionIcons;
    
    private Map<String, List<String>> mimeTypes;

    private NameGenerationHelper nameGenerationHelper;

    /**
     * Initializes an instance of this class.
     * 
     * @param mimeTypes
     *            a map with mime type mappings
     * @param fileExtensionIcons mapping between file extensions and corresponding icons 
     */
    @SuppressWarnings("unchecked")
    public JCRContentUtils(Map<String, List<String>> mimeTypes, Map<String, String> fileExtensionIcons) {
        super();
        instance = this;
        this.mimeTypes = UnmodifiableMap.decorate(mimeTypes);
        this.fileExtensionIcons = UnmodifiableMap.decorate(fileExtensionIcons);
    }

    /**
     * Generates the node name by using a configurable NameGenerationHelper implementation.
     * 
     * @return a node name generated by configurable helper service
     */    
    public String generateNodeName(JCRNodeWrapper parent, String nodeType) {
        return getNameGenerationHelper().generatNodeName(parent, nodeType);
    }

    /**
     * Generates the node name by using a configurable NameGenerationHelper implementation.
     * 
     * @return a node name generated by configurable helper service
     */    
    public String generateNodeName(JCRNodeWrapper parent, String defaultLanguage, ExtendedNodeType nodeType) {
        return getNameGenerationHelper().generatNodeName(parent, defaultLanguage, nodeType);
    }
    
    /**
     * Returns a mapping between file extensions and corresponding icons.
     * 
     * @return a mapping between file extensions and corresponding icons
     */
    public Map<String, String> getFileExtensionIcons() {
        return fileExtensionIcons;
    }

    /**
     * Return a map of mime types (file formats) to be available in the
     * advanced search form.
     * 
     * @return a map of mime types (file formats) to be available in the
     *         advanced search form
     */
    public Map<String, List<String>> getMimeTypes() {
        return mimeTypes;
    }

    public NameGenerationHelper getNameGenerationHelper() {
        return nameGenerationHelper;
    }

    public void setNameGenerationHelper(NameGenerationHelper nameGenerationHelper) {
        this.nameGenerationHelper = nameGenerationHelper;
    }
}
