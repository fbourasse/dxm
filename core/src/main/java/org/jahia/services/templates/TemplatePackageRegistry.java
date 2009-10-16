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
package org.jahia.services.templates;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jahia.data.templates.JahiaTemplateDef;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.content.rules.RulesListener;
import org.jahia.settings.SettingsBean;

import java.io.File;
import java.util.*;

/**
 * Template packages registry service.
 *
 * @author Sergiy Shyrkov
 */
class TemplatePackageRegistry {

    private static Logger logger = Logger
            .getLogger(TemplatePackageRegistry.class);

    private Map<String, JahiaTemplatesPackage> registry = new TreeMap<String, JahiaTemplatesPackage>();
    private Map<String, JahiaTemplatesPackage> fileNameRegistry = new TreeMap<String, JahiaTemplatesPackage>();
    private Map<String, List<JahiaTemplatesPackage>> packagesPerModule = new HashMap<String, List<JahiaTemplatesPackage>>();

    private SettingsBean settingsBean;

    private List<JahiaTemplatesPackage> templatePackages;

    /**
     * Builds a template package hierarchy, resolving inheritance.
     */
    public void buildHierarchy() {
        for (JahiaTemplatesPackage pkg : registry.values()) {
            resolveInheritance(pkg);
        }
    }

    /**
     * Rebuilds a template package hierarchy, resolving inheritance.
     */
    private void rebuildHierarchy() {
        for (JahiaTemplatesPackage pkg : registry.values()) {
            pkg.clearHierarchy();
        }
        for (JahiaTemplatesPackage pkg : registry.values()) {
            resolveInheritance(pkg);
        }
    }

    /**
     * Checks if the specified template set is present in the repository.
     *
     * @param packageName the template package name to check for
     * @return <code>true</code>, if the specified template package already
     *         exists in the repository
     */
    public boolean contains(String packageName) {
        return registry.containsKey(packageName);
    }

    public boolean containsFileName(String fileName) {
        return fileNameRegistry.containsKey(fileName);
    }

    /**
     * Returns a list of all available template packages.
     *
     * @return a list of all available template packages
     */
    public List<JahiaTemplatesPackage> getAvailablePackages() {
        if (null == templatePackages) {
            templatePackages = Collections
                .unmodifiableList(new LinkedList<JahiaTemplatesPackage>(
                    registry.values()));
        }
        return templatePackages;
    }

    /**
     * Returns the number of available template packages in the registry.
     *
     * @return the number of available template packages in the registry
     */
    public int getAvailablePackagesCount() {
        return registry.size();
    }

    public Map<String, List<JahiaTemplatesPackage>> getPackagesPerModule() {
        return packagesPerModule;
    }

    /**
     * Returns the requested template package or <code>null</code> if the
     * package with the specified name is not registered in the repository.
     *
     * @param packageName the template package name to search for
     * @return the requested template package or <code>null</code> if the
     *         package with the specified name is not registered in the
     *         repository
     */
    public JahiaTemplatesPackage lookup(String packageName) {
        if (packageName == null || registry == null) return null;
        return registry.containsKey(packageName) ? registry.get(packageName)
                : null;
    }

    /**
     * Returns the requested template package or <code>null</code> if the
     * package with the specified file name is not registered in the repository.
     *
     * @param fileName the template package fileName to search for
     * @return the requested template package or <code>null</code> if the
     *         package with the specified name is not registered in the
     *         repository
     */
    public JahiaTemplatesPackage lookupByFileName(String fileName) {
        if (fileName == null || registry == null) return null;
        return fileNameRegistry.containsKey(fileName) ? fileNameRegistry.get(fileName)
                : null;
    }

    /**
     * Adds the template package to the repository.
     *
     * @param templatePackage the template package to add
     */
    public void register(JahiaTemplatesPackage templatePackage) {
        templatePackages = null;
        registry.put(templatePackage.getName(), templatePackage);
        fileNameRegistry.put(templatePackage.getFileName(), templatePackage);
        File rootFolder = new File(settingsBean.getJahiaTemplatesDiskPath(), templatePackage.getRootFolder());
        if (!templatePackage.getDefinitionsFiles().isEmpty()) {
            try {
                for (String name : templatePackage.getDefinitionsFiles()) {
                    NodeTypeRegistry.getInstance().addDefinitionsFile(
                            new File(rootFolder, name),
                            templatePackage.getName(), true);
                }
            } catch (Exception e) {
                logger.warn("Cannot parse definitions for "+templatePackage.getName(),e);
            }
        }
        if (!templatePackage.getRulesFiles().isEmpty()) {
            try {
                for (String name : templatePackage.getRulesFiles()) {
                    for (RulesListener listener : RulesListener.getInstances()) {
                        listener.addRules(new File(rootFolder, name));
                    }
                }
            } catch (Exception e) {
                logger.warn("Cannot parse rules for "+templatePackage.getName(),e);
            }
        }

        File[] files = rootFolder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String key = file.getName();
                if (!packagesPerModule.containsKey(key)) {
                    packagesPerModule.put(key, new ArrayList<JahiaTemplatesPackage>());
                }
                packagesPerModule.get(key).add(templatePackage);
            }
        }
    }

    public void unregister(JahiaTemplatesPackage templatePackage) {
        registry.remove(templatePackage.getName());
        fileNameRegistry.remove(templatePackage.getFileName());
        templatePackages = null;
        NodeTypeRegistry.getInstance().unregisterNodeTypes(templatePackage.getName());
        rebuildHierarchy();
    }

    public void reset() {
        for (JahiaTemplatesPackage pkg : new HashSet<JahiaTemplatesPackage>(registry.values())) {
            unregister(pkg);
        }
        templatePackages = null;
    }

    public void resolveInheritance(JahiaTemplatesPackage pkg) {
        // check if the inheritance was not yet resolved for the package
        if (pkg.getHierarchy().isEmpty()) {
            // add itself as a first item in the hierarchy
            pkg.getHierarchy().add(pkg.getName());
            pkg.getLookupPath().add(pkg.getRootFolderPath());
            if (pkg.getResourceBundleName() != null) {
                pkg.getResourceBundleHierarchy().add(pkg.getResourceBundleName());
            }

            if (StringUtils.isNotEmpty(pkg.getExtends())) {
                // TODO implement a check for cyclic dependencies
                JahiaTemplatesPackage parentPkg = lookup(pkg.getExtends());
                if (parentPkg != null) {
                    resolveInheritance(parentPkg);
                    pkg.getHierarchy().addAll(parentPkg.getHierarchy());
                    pkg.getLookupPath().addAll(parentPkg.getLookupPath());
                    pkg.getResourceBundleHierarchy().addAll(parentPkg.getResourceBundleHierarchy());

                    // properties
                    Map<String, String> ownProperties = new HashMap<String, String>(
                            pkg.getProperties());
                    pkg.getProperties().clear();
                    pkg.getProperties().putAll(parentPkg.getProperties());
                    pkg.getProperties().putAll(ownProperties);
                    
                    List<JahiaTemplateDef> ownTemplates = pkg.getTemplates();
                    // clear the list
                    pkg.removeTemplates();
                    // add all inherited templates
                    pkg.addTemplateDefAll(parentPkg.getTemplates(), true);
                    // and override them with own templates
                    pkg.addTemplateDefAll(ownTemplates, false);

                    // check common pages
                    pkg
                            .setMySettingsPageName(pkg.getMySettingsPageName() != null ? pkg
                                    .getMySettingsPageName()
                                    : parentPkg.getMySettingsPageName());
                    pkg.setMySettingsSuccessPageName(pkg
                            .getMySettingsSuccessPageName() != null ? pkg
                            .getMySettingsSuccessPageName() : parentPkg
                            .getMySettingsSuccessPageName());
                    pkg
                            .setSearchResultsPageName(pkg
                                    .getSearchResultsPageName() != null ? pkg
                                    .getSearchResultsPageName() : parentPkg
                                    .getSearchResultsPageName());
                    pkg.setNewUserRegistrationPageName(pkg
                        .getNewUserRegistrationPageName() != null ? pkg
                        .getNewUserRegistrationPageName() : parentPkg
                        .getNewUserRegistrationPageName());                    
                    pkg.setNewUserRegistrationSuccessPageName(pkg
                        .getNewUserRegistrationSuccessPageName() != null ? pkg
                        .getNewUserRegistrationSuccessPageName() : parentPkg
                        .getNewUserRegistrationSuccessPageName());
                    pkg.setSitemapPageName(pkg
                        .getSitemapPageName() != null ? pkg
                        .getSitemapPageName() : parentPkg
                        .getSitemapPageName());                    

                    // check homepage and default page
                    pkg.setHomePageName(pkg.getHomePageName() != null ? pkg
                            .getHomePageName() : parentPkg.getHomePageName());
                    pkg
                            .setDefaultPageName(pkg.getDefaultPageName() != null ? pkg
                                    .getDefaultPageName()
                                    : parentPkg.getDefaultPageName());
                } else {
                    logger
                            .error("Unable to find parent template package with the name '"
                                    + pkg.getExtends()
                                    + "' for the package '"
                                    + pkg.getName()
                                    + "'. Skipping inheritance resolution.");
                    pkg.getLookupPath().add("/templates/default");
                    pkg.getResourceBundleHierarchy().add("jahiatemplates.common");
            		pkg.getResourceBundleHierarchy().add("JahiaTypesResources");
                }
            } else {
                // check homepage and default page
                if (pkg.getHomePageName() == null && pkg.getTemplates().size() > 0) {
                    pkg.setHomePageName(((JahiaTemplateDef) pkg.getTemplates()
                            .get(0)).getName());
                }
                if (pkg.getDefaultPageName() == null && pkg.getTemplates().size() > 0) {
                    pkg.setDefaultPageName(((JahiaTemplateDef) pkg
                            .getTemplates().get(0)).getName());
                }
                pkg.getLookupPath().add("/templates/default");
                pkg.getResourceBundleHierarchy().add("jahiatemplates.common");
            	pkg.getResourceBundleHierarchy().add("JahiaTypesResources");
            }
        }
    }

    public void setSettingsBean(SettingsBean settingsBean) {
        this.settingsBean = settingsBean;
    }

    /**
     * Performs a set of validation tests for deployed template packages.
     */
    public void validate() {
        if (getAvailablePackagesCount() == 0) {
            logger.warn("No available template packages found."
                    + " That will prevent creation of a virtual site.");
        }
        // TODO implement dependency (inheritance) validation for template sets
    }
}