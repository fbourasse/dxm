/**
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2009 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.ajax.gwt.helper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.jackrabbit.value.*;
import org.apache.log4j.Logger;
import org.jahia.ajax.gwt.client.data.GWTJahiaValueDisplayBean;
import org.jahia.ajax.gwt.client.data.definition.*;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.service.GWTJahiaServiceException;
import org.jahia.params.ProcessingContext;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.nodetypes.*;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializerService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;

import javax.jcr.*;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeTypeIterator;
import java.util.*;

/**
 * Helper class for accessing node types and definitions.
 *
 * @author Thomas Draier
 *         Date: Sep 12, 2008 - 11:48:20 AM
 */
public class ContentDefinitionHelper {
    private static final Logger logger = Logger.getLogger(ContentDefinitionHelper.class);

    private NavigationHelper navigation;
    private ChoiceListInitializerService choiceListInitializerService;
    private JCRSessionFactory sessionFactory;

    public void setNavigation(NavigationHelper navigation) {
        this.navigation = navigation;
    }

    public void setChoiceListInitializerService(ChoiceListInitializerService choiceListInitializerService) {
        this.choiceListInitializerService = choiceListInitializerService;
    }

    public void setSessionFactory(JCRSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private static final List<String> excludedItems = Arrays.asList("j:locktoken", "jcr:lockOwner", "jcr:lockIsDeep",
            "j:nodename", "j:fullpath", "j:applyAcl", "jcr:uuid", "j:fieldsinuse");

    private static final List<String> excludedTypes = Arrays.asList("nt:base", "mix:versionable", "jnt:workflow", "jnt:extraResource");

    private final Comparator<GWTJahiaNodeType> gwtJahiaNodeTypeNameComparator = new Comparator<GWTJahiaNodeType>() {
        public int compare(GWTJahiaNodeType o1, GWTJahiaNodeType o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    public GWTJahiaNodeType getNodeType(String name, ProcessingContext context) {
        ExtendedNodeType nodeType = null;
        try {
            nodeType = NodeTypeRegistry.getInstance().getNodeType(name);
        } catch (NoSuchNodeTypeException e) {
            return null;
        }
        GWTJahiaNodeType gwt = getGWTJahiaNodeType(context, nodeType);
        return gwt;
    }

    public Map<GWTJahiaNodeType, List<GWTJahiaNode>> getNodeTypeWithReusableComponents(String name, ProcessingContext context) throws GWTJahiaServiceException {
        try {
            ExtendedNodeType type = NodeTypeRegistry.getInstance().getNodeType(name);
            List<GWTJahiaNode> nodes = getReusableComponents(type, context);
            GWTJahiaNodeType gwtType = getGWTJahiaNodeType(context, type);
            Map<GWTJahiaNodeType, List<GWTJahiaNode>> result = new HashMap<GWTJahiaNodeType, List<GWTJahiaNode>>();
            result.put(gwtType, nodes);
            return  result;
        } catch (RepositoryException e) {
            logger.error("Cannot find type",e);
            throw new GWTJahiaServiceException(e.toString());
        }
    }

    private GWTJahiaNodeType getGWTJahiaNodeType(ProcessingContext context, ExtendedNodeType nodeType) {
        return getGWTJahiaNodeType(context, nodeType, nodeType);
    }

    private GWTJahiaNodeType getGWTJahiaNodeType(ProcessingContext context, ExtendedNodeType nodeType, ExtendedNodeType realNodeType) {
        GWTJahiaNodeType gwt = new GWTJahiaNodeType();
        gwt.setName(nodeType.getName());
        Locale loc = context.getUILocale();
        String label = nodeType.getLabel(loc);
        gwt.setLabel(label);

        List<ExtendedItemDefinition> defs;

        defs = nodeType.getItems();

        List<GWTJahiaItemDefinition> items = new ArrayList<GWTJahiaItemDefinition>();
        List<GWTJahiaItemDefinition> inheritedItems = new ArrayList<GWTJahiaItemDefinition>();

        for (ExtendedItemDefinition def : defs) {
            if (!excludedTypes.contains(def.getDeclaringNodeType().getName()) && !excludedItems.contains(def.getName())) {
                GWTJahiaItemDefinition item;
                if (def.isNode()) {
                    GWTJahiaNodeDefinition node = new GWTJahiaNodeDefinition();
                    ExtendedNodeDefinition end = (ExtendedNodeDefinition) def;
                    item = node;
                    node.setRequiredPrimaryTypes(end.getRequiredPrimaryTypeNames());
                    node.setDefaultPrimaryType(end.getDefaultPrimaryTypeName());
                    node.setAllowsSameNameSiblings(end.allowsSameNameSiblings());
                    node.setWorkflow(end.getWorkflow());
                } else {
                    GWTJahiaPropertyDefinition prop = new GWTJahiaPropertyDefinition();
                    ExtendedPropertyDefinition epd = (ExtendedPropertyDefinition) def;
                    prop.setInternationalized(epd.isInternationalized());
                    prop.setRequiredType(epd.getRequiredType());
                    prop.setMultiple(epd.isMultiple());
                    context.setAttribute("contextDefinition", nodeType);
                    String[] constr = epd.getValueConstraints();
                    boolean constrained = constr != null && constr.length > 0;
                    prop.setConstrained(constrained);
                    final Map<String, String> map = epd.getSelectorOptions();
                    final ArrayList<GWTJahiaValueDisplayBean> displayBeans = new ArrayList<GWTJahiaValueDisplayBean>(
                            32);
                    if (map.size() > 0) {
                        final Map<String, ChoiceListInitializer> initializers = choiceListInitializerService.getInitializers();
                        List<ChoiceListValue> listValues = null;
                        for (Map.Entry<String, String> entry : map.entrySet()) {
                            if (initializers.containsKey(entry.getKey())) {
                                listValues = initializers.get(entry.getKey()).getChoiceListValues(context, epd,
                                        realNodeType, entry.getValue(), listValues);
                            }
                        }
                        if (listValues != null) {
                            for (ChoiceListValue choiceListValue : listValues) {
                                try {
                                    final GWTJahiaValueDisplayBean displayBean = new GWTJahiaValueDisplayBean(
                                            choiceListValue.getValue().getString(), choiceListValue.getDisplayName());
                                    final Map<String, Object> props = choiceListValue.getProperties();
                                    if (props != null) {
                                        for (Map.Entry<String, Object> objectEntry : props.entrySet()) {
                                            displayBean.set(objectEntry.getKey(), objectEntry.getValue());
                                        }
                                    }
                                    displayBeans.add(displayBean);
                                } catch (RepositoryException e) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }
                    } else {
                        if (constrained) {
                            for (String s : constr) {
                                displayBeans.add(new GWTJahiaValueDisplayBean(s, s));
                            }
                        }
                    }
                    prop.setValueConstraints(displayBeans);
                    List<GWTJahiaNodePropertyValue> gwtValues = new ArrayList<GWTJahiaNodePropertyValue>();
                    for (Value value : epd.getDefaultValues()) {
                        try {
                            gwtValues.add(convertValue(value, epd.getRequiredType()));
                        } catch (RepositoryException e) {
                            e.printStackTrace();
                        }
                    }
                    prop.setDefaultValues(gwtValues);
                    item = prop;
                }
                item.setAutoCreated(def.isAutoCreated());
                item.setLabel(def.getLabel(context.getUILocale()));
                item.setMandatory(def.isMandatory());
                item.setHidden(def.isHidden());
                item.setName(def.getName());
                item.setProtected(def.isProtected());
                item.setDeclaringNodeType(def.getDeclaringNodeType().getName());
                item.setDeclaringNodeTypeLabel(def.getDeclaringNodeType().getLabel(context.getUILocale()));
                item.setSelector(def.getSelector());
                item.setSelectorOptions(new HashMap<String, String>(def.getSelectorOptions()));
                if (def.isContentItem()) {
                    item.setDataType(GWTJahiaItemDefinition.CONTENT);
                } else if (def.isLayoutItem()) {
                    item.setDataType(GWTJahiaItemDefinition.LAYOUT);
                } else if (def.isMetadataItem()) {
                    item.setDataType(GWTJahiaItemDefinition.METADATA);
                } else if (def.isOptionItem()) {
                    item.setDataType(GWTJahiaItemDefinition.OPTIONS);
                } else if (def.isPublicationItem()) {
                    item.setDataType(GWTJahiaItemDefinition.PUBLICATION);
                } else if (def.isSystemItem()) {
                    item.setDataType(GWTJahiaItemDefinition.SYSTEM);
                }
                if (def.getDeclaringNodeType().getName().equals(nodeType.getName())) {
                    items.add(item);
                } else {
                    inheritedItems.add(item);
                }
            }
        }
        gwt.setItems(items);
        gwt.setInheritedItems(inheritedItems);
        List<String> supertypesNames = new ArrayList<String>();
        ExtendedNodeType[] nodeTypes = nodeType.getSupertypes();
        for (ExtendedNodeType type : nodeTypes) {
            supertypesNames.add(type.getName());
        }
        gwt.setSuperTypes(supertypesNames);
        String icon = navigation.getNodetypeIcons().get(nodeType.getName());
        if (icon != null) {
            gwt.setIcon("icon-" + icon);
        } else {
            gwt.setIcon("icon-" + navigation.getNodetypeIcons().get("default"));
        }


        return gwt;
    }

    public List<GWTJahiaNodeType> getNodeTypes(List<String> names, ProcessingContext context) {
        try {
            List<GWTJahiaNodeType> list = new ArrayList<GWTJahiaNodeType>();
            for (String name : names) {
                list.add(getNodeType(name, context));
            }
            return list;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public Map<GWTJahiaNodeType, Map<GWTJahiaNodeType,List<GWTJahiaNode>>> getNodeTypes(ProcessingContext ctx) {
        Map<GWTJahiaNodeType, Map<GWTJahiaNodeType,List<GWTJahiaNode>>> map = new HashMap<GWTJahiaNodeType,Map<GWTJahiaNodeType, List<GWTJahiaNode>>>();
        try {
            ExtendedNodeType nt = NodeTypeRegistry.getInstance().getNodeType("jmix:content");
            NodeTypeIterator typeIterator = nt.getDeclaredSubtypes();
            while (typeIterator.hasNext()) {
                ExtendedNodeType mainType = (ExtendedNodeType) typeIterator.next();
                Map<GWTJahiaNodeType,List<GWTJahiaNode>> l = new HashMap<GWTJahiaNodeType, List<GWTJahiaNode>>();
                map.put(getGWTJahiaNodeType(ctx, mainType), l);
                NodeTypeIterator subtypes = mainType.getDeclaredSubtypes();
                while (subtypes.hasNext()) {
                    ExtendedNodeType nodeType = (ExtendedNodeType) subtypes.next();
                    List<GWTJahiaNode> nodes = getReusableComponents(nodeType, ctx);
                    l.put(getGWTJahiaNodeType(ctx, nodeType),nodes);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return map;
    }

    /**
     * Returns a list of node types with name and label populated that are the
     * sub-types of the specified base type or that are allowed to be created in
     * the specified parent node (if the baseType parameter is null).
     *
     * @param baseType   the node type name to find sub-types
     * @param parentNode the parent node, where the wizard was called
     * @param ctx        current processing context instance
     * @return a list of node types with name and label populated that are the
     *         sub-types of the specified base type or that are allowed to be
     *         created in the specified parent node (if the baseType parameter
     *         is null)
     */
    public Map<GWTJahiaNodeType, Map<GWTJahiaNodeType, List<GWTJahiaNode>>> getNodeSubtypes(String baseType,
                                                  GWTJahiaNode parentNode, ProcessingContext ctx) {
        Map<GWTJahiaNodeType, Map<GWTJahiaNodeType,List<GWTJahiaNode>>> map = new HashMap<GWTJahiaNodeType,Map<GWTJahiaNodeType, List<GWTJahiaNode>>>();
        List<GWTJahiaNodeType> gwtNodeTypes = new ArrayList<GWTJahiaNodeType>();
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        try {
            ExtendedNodeType content = registry.getNodeType("jmix:content");
            NodeTypeIterator typeIterator = content.getDeclaredSubtypes();
            Map<String,ExtendedNodeType> contentTypes = new HashMap<String, ExtendedNodeType>();
            while (typeIterator.hasNext()) {
                ExtendedNodeType type = (ExtendedNodeType) typeIterator.next();
                contentTypes.put(type.getName(),type);
            }

            if (baseType == null && parentNode != null) {
                Collection<ExtendedNodeDefinition> definitions = new ArrayList<ExtendedNodeDefinition>();
                for (String nodeTypeName : parentNode.getNodeTypes()) {
                    ExtendedNodeType nodeType = registry
                            .getNodeType(nodeTypeName);
                    definitions.addAll(nodeType.getUnstructuredChildNodeDefinitions().values());
                }
                for (ExtendedNodeDefinition nodeDef : definitions) {
                    ExtendedNodeType[] requiredPrimaryTypes = nodeDef
                            .getRequiredPrimaryTypes();
                    for (ExtendedNodeType extendedNodeType : requiredPrimaryTypes) {
                        if (!excludedTypes.contains(extendedNodeType.getName()) && !extendedNodeType.isMixin() && !extendedNodeType.isAbstract() && CollectionUtils.containsAny(Arrays.asList(extendedNodeType.getDeclaredSupertypeNames()), contentTypes.keySet())) {
                            gwtNodeTypes.add(getGWTJahiaNodeType(ctx, extendedNodeType));
                        }

                        NodeTypeIterator subtypes = extendedNodeType.getSubtypes();
                        while (subtypes.hasNext()) {
                            ExtendedNodeType nodeType = (ExtendedNodeType) subtypes.next();
                            if (!excludedTypes.contains(nodeType.getName()) && !nodeType.isMixin() && !nodeType.isAbstract() && CollectionUtils.containsAny(Arrays.asList(nodeType.getDeclaredSupertypeNames()), contentTypes.keySet())) {
                                gwtNodeTypes.add(getGWTJahiaNodeType(ctx, nodeType));
                            }
                        }
                    }
                }
            } else {
                baseType = baseType != null ? baseType : "jnt:container";
                ExtendedNodeType baseNodeType = null;
                try {
                    baseNodeType = NodeTypeRegistry.getInstance().getNodeType(
                            baseType);
                } catch (NoSuchNodeTypeException e) {
                    logger.warn("Node type with the name '" + baseType
                            + "' cannot be found in the registry", e);
                }
                if (baseNodeType != null) {
                    NodeTypeIterator types = baseNodeType.getSubtypes();
                    while (types.hasNext()) {
                        ExtendedNodeType nodeType = (ExtendedNodeType) types.next();
                        if (!excludedTypes.contains(nodeType.getName())) {
                            gwtNodeTypes.add(getGWTJahiaNodeType(ctx, nodeType));
                        }
                    }
                }
            }
            typeIterator = content.getDeclaredSubtypes();
            while (typeIterator.hasNext()) {
                ExtendedNodeType mainType = (ExtendedNodeType) typeIterator.next();
                Map<GWTJahiaNodeType,List<GWTJahiaNode>> l = new HashMap<GWTJahiaNodeType, List<GWTJahiaNode>>();
                map.put(getGWTJahiaNodeType(ctx, mainType), l);
                NodeTypeIterator subtypes = mainType.getDeclaredSubtypes();
                while (subtypes.hasNext()) {
                    ExtendedNodeType nodeType = (ExtendedNodeType) subtypes.next();
                    if(gwtNodeTypes.contains(getGWTJahiaNodeType(ctx, nodeType))) {
                        List<GWTJahiaNode> nodes = getReusableComponents(nodeType, ctx);
                        l.put(getGWTJahiaNodeType(ctx, nodeType),nodes);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return map;
    }

    private List<GWTJahiaNode> getReusableComponents(ExtendedNodeType nodeType, ProcessingContext ctx) throws RepositoryException {
        JCRSessionWrapper sessionWrapper = sessionFactory.getCurrentUserSession();
        List<GWTJahiaNode> nodes = new ArrayList<GWTJahiaNode>();
        try {
            JCRNodeWrapper node = sessionWrapper.getNode("/reusableComponents/" + nodeType.getName().replaceAll(":",
                    "_"));
            NodeIterator iterator = node.getNodes();
            while (iterator.hasNext()) {
                JCRNodeWrapper jcrNodeWrapper = (JCRNodeWrapper) iterator.next();
                nodes.add(navigation.getGWTJahiaNode(jcrNodeWrapper, false));
            }
        } catch (PathNotFoundException e) {
        }

        try {
            JCRNodeWrapper node = sessionWrapper.getNode("/reusableComponents/" + ctx.getSite().getTemplatePackageName() + "/" +
                    nodeType.getName().replaceAll(":","_"));
            NodeIterator iterator = node.getNodes();
            while (iterator.hasNext()) {
                JCRNodeWrapper jcrNodeWrapper = (JCRNodeWrapper) iterator.next();
                nodes.add(navigation.getGWTJahiaNode(jcrNodeWrapper, false));
            }
        } catch (PathNotFoundException e) {
        }
        return nodes;
    }

    public GWTJahiaNodePropertyValue convertValue(Value val, int requiredType) throws RepositoryException {
        String theValue;
        int type;

        switch (requiredType) {
            case PropertyType.BINARY:
                type = GWTJahiaNodePropertyType.BINARY;
                theValue = val.getString();
                break;
            case PropertyType.BOOLEAN:
                type = GWTJahiaNodePropertyType.BOOLEAN;
                theValue = String.valueOf(val.getBoolean());
                break;
            case PropertyType.DATE:
                type = GWTJahiaNodePropertyType.DATE;
                theValue = String.valueOf(val.getDate().getTimeInMillis());
                break;
            case PropertyType.DOUBLE:
                type = GWTJahiaNodePropertyType.DOUBLE;
                theValue = String.valueOf(val.getDouble());
                break;
            case PropertyType.LONG:
                type = GWTJahiaNodePropertyType.LONG;
                theValue = String.valueOf(val.getLong());
                break;
            case PropertyType.NAME:
                type = GWTJahiaNodePropertyType.NAME;
                theValue = val.getString();
                break;
            case PropertyType.PATH:
                type = GWTJahiaNodePropertyType.PATH;
                theValue = val.getString();
                break;
            case PropertyType.WEAKREFERENCE:
                return new GWTJahiaNodePropertyValue(navigation.getGWTJahiaNode((JCRNodeWrapper) ((JCRValueWrapper) val).getNode(), false), GWTJahiaNodePropertyType.WEAKREFERENCE);
            case PropertyType.REFERENCE:
                return new GWTJahiaNodePropertyValue(navigation.getGWTJahiaNode((JCRNodeWrapper) ((JCRValueWrapper) val).getNode(), false));
            case PropertyType.STRING:
                type = GWTJahiaNodePropertyType.STRING;
                theValue = val.getString();
                break;
            case PropertyType.UNDEFINED:
                type = GWTJahiaNodePropertyType.UNDEFINED;
                theValue = val.getString();
                break;
            default:
                type = GWTJahiaNodePropertyType.UNDEFINED;
                theValue = val.getString();
        }

        return new GWTJahiaNodePropertyValue(theValue, type);
    }

    public Value convertValue(GWTJahiaNodePropertyValue val) throws RepositoryException {
        Value value;
        switch (val.getType()) {
            case GWTJahiaNodePropertyType.BINARY:
                value = new BinaryValue(val.getBinary());
                break;
            case GWTJahiaNodePropertyType.BOOLEAN:
                value = new BooleanValue(val.getBoolean());
                break;
            case GWTJahiaNodePropertyType.DATE:
                Calendar cal = Calendar.getInstance();
                cal.setTime(val.getDate());
                value = new DateValue(cal);
                break;
            case GWTJahiaNodePropertyType.DOUBLE:
                value = new DoubleValue(val.getDouble());
                break;
            case GWTJahiaNodePropertyType.LONG:
                value = new LongValue(val.getLong());
                break;
            case GWTJahiaNodePropertyType.NAME:
                value = NameValue.valueOf(val.getString());
                break;
            case GWTJahiaNodePropertyType.PATH:
                value = PathValue.valueOf(val.getString());
                break;
            case GWTJahiaNodePropertyType.REFERENCE:
                value = ReferenceValue.valueOf(val.getString());
                break;
            case GWTJahiaNodePropertyType.WEAKREFERENCE:
                value = WeakReferenceValue.valueOf(val.getString());
                break;
            case GWTJahiaNodePropertyType.STRING:
                value = new StringValue(val.getString());
                break;
            case GWTJahiaNodePropertyType.UNDEFINED:
                value = new StringValue(val.getString());
                break;
            default:
                value = new StringValue(val.getString());
        }

        return value;
    }

    public List<GWTJahiaNodeType> getAvailableMixin(GWTJahiaNodeType type, ProcessingContext ctx) {
        ArrayList<GWTJahiaNodeType> res = new ArrayList<GWTJahiaNodeType>();
        Set<String> foundTypes = new HashSet<String>();
        try {
            ExtendedNodeType realType = NodeTypeRegistry.getInstance().getNodeType(type.getName());
            if (type.getSuperTypes().contains("jmix:list")) {
                ExtendedNodeType baseMixin = NodeTypeRegistry.getInstance().getNodeType("jmix:listMixin");
                NodeTypeIterator it = baseMixin.getSubtypes();
                while (it.hasNext()) {
                    ExtendedNodeType nodeType = (ExtendedNodeType) it.next();
                    if (nodeType.isMixin() && !foundTypes.contains(nodeType.getName())) {
                        res.add(getGWTJahiaNodeType(ctx, nodeType, realType));
                        foundTypes.add(nodeType.getName());
                    }
                }
            } else if (type.getName().equals("jnt:page")) {
                ExtendedNodeType baseMixin = NodeTypeRegistry.getInstance().getNodeType("jmix:pageMixin");
                NodeTypeIterator it = baseMixin.getSubtypes();
                while (it.hasNext()) {
                    ExtendedNodeType nodeType = (ExtendedNodeType) it.next();
                    if (nodeType.isMixin() && !foundTypes.contains(nodeType.getName())) {
                        res.add(getGWTJahiaNodeType(ctx, nodeType, realType));
                        foundTypes.add(nodeType.getName());
                    }
                }
            } else if (type.getName().equals("jnt:nodeReference")) {
                ExtendedNodeType baseMixin = NodeTypeRegistry.getInstance().getNodeType("jmix:renderableReference");
                res.add(getGWTJahiaNodeType(ctx, baseMixin, realType));
                foundTypes.add("jmix:renderable");
                foundTypes.add("jmix:renderableReference");
            }


            ExtendedNodeType baseMixin = NodeTypeRegistry.getInstance().getNodeType("jmix:contentMixin");
            NodeTypeIterator it = baseMixin.getSubtypes();
            while (it.hasNext()) {
                ExtendedNodeType nodeType = (ExtendedNodeType) it.next();
                if (nodeType.isMixin() && !foundTypes.contains(nodeType.getName())) {
                    res.add(getGWTJahiaNodeType(ctx, nodeType, realType));
                    foundTypes.add(nodeType.getName());
                }
            }
        } catch (NoSuchNodeTypeException e) {

        }
        return res;
    }

}
