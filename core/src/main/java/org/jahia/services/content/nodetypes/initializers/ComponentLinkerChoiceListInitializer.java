/**
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2010 Jahia Solutions Group SA. All rights reserved.
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
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Solutions Group SA. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.content.nodetypes.initializers;

import org.apache.commons.collections.Factory;
import org.apache.commons.collections.map.LazyMap;
import org.apache.log4j.Logger;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.content.nodetypes.ValueImpl;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author : rincevent
 * @since : JAHIA 6.1
 *        Created : 11 mai 2010
 */
public class ComponentLinkerChoiceListInitializer implements ChoiceListInitializer {
    private transient static Logger logger = Logger.getLogger(ComponentLinkerChoiceListInitializer.class);
    private JCRSessionFactory sessionFactory;

    public void setSessionFactory(JCRSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values, Locale locale,
                                                     Map<String, Object> context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        JCRNodeWrapper nodeWrapper = (JCRNodeWrapper) context.get("contextNode");
        ExtendedPropertyDefinition[] propertyDefs;
        if (nodeWrapper == null) {
            return Collections.emptyList();
        }
        try {
            if (nodeWrapper.hasProperty(param)) {
                JCRNodeWrapper bindedNode = (JCRNodeWrapper) nodeWrapper.getProperty(param).getNode();
                if (bindedNode.isNodeType("jnt:contentList")  || bindedNode.isNodeType("jnt:absoluteArea") || bindedNode.isNodeType("jnt:levelAbsoluteArea") || bindedNode.isNodeType("jnt:mainResourceArea")) {
                    if (bindedNode.hasProperty("j:allowedTypes") && bindedNode.isNodeType("jmix:listRestrictions")) {
                        final Value[] values1 = bindedNode.getProperty("j:allowedTypes").getValues();
                        propertyDefs = getCommonChildNodeDefinitions(values1, true, true,
                                                                     new LinkedHashSet<String>());
                    } else if (bindedNode.hasNodes()) {
                        propertyDefs = SortableFieldnamesChoiceListInitializerImpl.getCommonChildNodeDefinitions(
                                bindedNode, true, true, new LinkedHashSet<String>());
                    } else {
                        return Collections.emptyList();
                    }
                } else {
                    propertyDefs = bindedNode.getPrimaryNodeType().getPropertyDefinitions();
                }
            } else {
                return Collections.emptyList();
            }
        } catch (RepositoryException e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
        for (ExtendedPropertyDefinition propertyDef : propertyDefs) {
            choiceListValues.add(new ChoiceListValue(propertyDef.getLabel(locale), null, new ValueImpl(
                    propertyDef.getName(), PropertyType.STRING, false)));
        }
        Collections.sort(choiceListValues);
        return choiceListValues;
    }

    @SuppressWarnings("unchecked")
    public static ExtendedPropertyDefinition[] getCommonChildNodeDefinitions(Value[] values, boolean showHidden,
                                                                             boolean showProtected,
                                                                             Set<String> excludedNodeTypes)
            throws RepositoryException {
        Map<String, Map<String, ExtendedPropertyDefinition>> defs = null;
        final NodeTypeRegistry typeRegistry = NodeTypeRegistry.getInstance();
        if (values.length > 0) {
            for (Value value : values) {
                final ExtendedNodeType type = typeRegistry.getNodeType(value.getString());
                if (defs == null) {
                    // first child
                    defs = LazyMap.decorate(new HashMap<String, Map<String, ExtendedPropertyDefinition>>(),
                                            new Factory() {
                                                public Object create() {
                                                    return new HashMap<String, ExtendedPropertyDefinition>();
                                                }
                                            });
                    for (ExtendedPropertyDefinition propertyDef : type.getPropertyDefinitions()) {
                        // filter out hidden and protected if needed
                        if ((showHidden || !propertyDef.isHidden()) && (showProtected || !propertyDef.isProtected())) {
                            ExtendedNodeType nodeType = propertyDef.getDeclaringNodeType();
                            if (excludedNodeTypes.isEmpty() || !excludedNodeTypes.contains(nodeType.getName())) {
                                defs.get(nodeType.getName()).put(propertyDef.getName(), propertyDef);
                            }
                        }
                    }
                } else {
                    // filter out node types
                    for (Iterator<String> iterator = defs.keySet().iterator(); iterator.hasNext();) {
                        String commonType = iterator.next();
                        if (!type.isNodeType(commonType)) {
                            // the node has no such type --> remove the type from common
                            iterator.remove();
                        }
                    }
                }

                if (defs.isEmpty()) {
                    // no common property definitions found -> stop
                    break;
                }
            }
            List<ExtendedPropertyDefinition> propertyDefinitions = new LinkedList<ExtendedPropertyDefinition>();
            if (defs != null) {
                for (Map<String, ExtendedPropertyDefinition> props : defs.values()) {
                    propertyDefinitions.addAll(props.values());
                }
            }
            return propertyDefinitions.toArray(new ExtendedPropertyDefinition[propertyDefinitions.size()]);
        } else {
            return new ExtendedPropertyDefinition[0];
        }
    }
}
