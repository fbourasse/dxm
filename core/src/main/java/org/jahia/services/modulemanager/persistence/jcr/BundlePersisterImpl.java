/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.modulemanager.persistence.jcr;

import static org.jahia.services.modulemanager.persistence.jcr.BundleInfoJcrHelper.PATH_BUNDLES;
import static org.jahia.services.modulemanager.persistence.jcr.BundleInfoJcrHelper.findTargetNode;
import static org.jahia.services.modulemanager.persistence.jcr.BundleInfoJcrHelper.getJcrPath;
import static org.jahia.services.modulemanager.persistence.jcr.BundleInfoJcrHelper.getOrCreateTargetNode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.modulemanager.ModuleManagementException;
import org.jahia.services.modulemanager.persistence.BundlePersister;
import org.jahia.services.modulemanager.persistence.PersistedBundle;
import org.jahia.services.modulemanager.persistence.PersistedBundleInfoBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * Responsible bundle persistence into JCR.
 *
 * @author Ahmed Chaabni
 * @author Sergiy Shyrkov
 */
public class BundlePersisterImpl implements BundlePersister {

    private static final Logger logger = LoggerFactory.getLogger(BundlePersisterImpl.class);

    @Override
    public boolean delete(final String bundleKey) throws ModuleManagementException {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    return delete(bundleKey, session);
                }
            });
        } catch (RepositoryException e) {
            throw new ModuleManagementException("Unable to delete information for bundle " + bundleKey, e);
        }
    }

    protected boolean delete(String bundleKey, JCRSessionWrapper session) throws RepositoryException {
        boolean removed = false;
        String path = getJcrPath(bundleKey);
        try {
            JCRNodeWrapper target = session.getNode(path);
            JCRNodeWrapper folder = target.getParent();
            target.remove();
            removed = true;

            // purge empty parent folders if any
            while (!folder.getPath().equals(PATH_BUNDLES) && !folder.hasNodes()) {
                JCRNodeWrapper parent = folder.getParent();
                folder.remove();
                folder = parent;
            }

            session.save();

            logger.debug("Node at {} deleted for bundle key {}", path, bundleKey);
        } catch (PathNotFoundException e) {
            // node does not exist
            logger.debug("Bundle info does not exist for key {}. Skip deleting it.", bundleKey);
        }

        return removed;
    }

    @Override
    public PersistedBundle extract(Resource resource) throws IOException {
        long startTime = System.currentTimeMillis();

        PersistedBundle bundleInfo = PersistedBundleInfoBuilder.build(resource);

        if (logger.isDebugEnabled()) {
            logger.debug("Extracted bundle info {} from resource {} in {} ms",
                    new Object[] { bundleInfo, resource, System.currentTimeMillis() - startTime });
        }

        return bundleInfo;
    }

    @Override
    public PersistedBundle find(final String bundleKey) {
        PersistedBundle found = null;
        try {
            found = JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<PersistedBundle>() {
                @Override
                public PersistedBundle doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    return find(bundleKey, session);
                }
            });
        } catch (RepositoryException e) {
            throw new ModuleManagementException("Unable to find information for bundle " + bundleKey, e);
        }
        return found;
    }

    protected PersistedBundle find(String bundleKey, JCRSessionWrapper session) throws RepositoryException {
        PersistedBundle info = null;
        JCRNodeWrapper node = findTargetNode(bundleKey, session);
        if (node != null) {
            // we've found bundle node for the specified key
            info = new PersistedBundle(node.getPropertyAsString("j:groupId"),
                    node.getPropertyAsString("j:symbolicName"), node.getPropertyAsString("j:version"));
            info.setChecksum(node.getPropertyAsString("j:checksum"));
            info.setDisplayName(node.getPropertyAsString("j:displayName"));
        }
        return info;
    }

    @Override
    public boolean isStorageInitialized() throws ModuleManagementException {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    return session.nodeExists(PATH_BUNDLES) && session.getNode(PATH_BUNDLES).hasNodes();
                }
            });
        } catch (RepositoryException e) {
            throw new ModuleManagementException("Unable to check the persistent storage for bundles", e);
        }
    }

    @Override
    public void store(final PersistedBundle bundleInfo) throws IOException, ModuleManagementException {
        long startTime = System.currentTimeMillis();

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    try {
                        return store(bundleInfo, session);
                    } catch (IOException e) {
                        throw new ModuleManagementException("Unable to store information for bundle " + bundleInfo, e);
                    }
                }
            });
        } catch (RepositoryException e) {
            throw new ModuleManagementException("Unable to store information for bundle " + bundleInfo, e);
        }

        logger.debug("Stored bundle info {} in {} ms", bundleInfo, System.currentTimeMillis() - startTime);
    }

    protected boolean store(PersistedBundle bundleInfo, JCRSessionWrapper session)
            throws RepositoryException, IOException {
        String path = getJcrPath(bundleInfo);
        JCRNodeWrapper bundleNode = null;
        try {
            bundleNode = session.getNode(path);
            if (bundleNode.hasProperty("j:checksum")
                    && bundleInfo.getChecksum().equals(bundleNode.getProperty("j:checksum").getString())) {
                logger.debug(
                        "Resource {} represents same bundle as alreday stored under {}." + " Skip storing it again.",
                        bundleInfo, path);
                return false;
            }
        } catch (PathNotFoundException e) {
            bundleNode = getOrCreateTargetNode(bundleInfo, session);
        }

        bundleNode.setProperty("j:groupId", bundleInfo.getGroupId());
        bundleNode.setProperty("j:symbolicName", bundleInfo.getSymbolicName());
        bundleNode.setProperty("j:version", bundleInfo.getVersion());
        bundleNode.setProperty("j:checksum", bundleInfo.getChecksum());
        bundleNode.setProperty("j:displayName", bundleInfo.getDisplayName());
        try (InputStream is = new BufferedInputStream(bundleInfo.getResource().getInputStream())) {
            bundleNode.getFileContent().uploadFile(is, "application/java-archive");
        }

        session.save();

        logger.debug("Stored bundle info {} under {}.", bundleInfo, path);

        return true;
    }

    @Override
    public PersistedBundle store(Resource resource) throws IOException, ModuleManagementException {
        long startTime = System.currentTimeMillis();
        final PersistedBundle bundleInfo = extract(resource);

        if (bundleInfo == null) {
            throw new ModuleManagementException("Invalid resource for bundle: " + resource);
        }

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    try {
                        return store(bundleInfo, session);
                    } catch (IOException e) {
                        throw new ModuleManagementException("Unable to store information for bundle " + bundleInfo, e);
                    }
                }
            });
        } catch (RepositoryException e) {
            throw new ModuleManagementException("Unable to store information for bundle " + bundleInfo, e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Stored bundle info {} from resource {} in {} ms",
                    new Object[] { bundleInfo, resource, System.currentTimeMillis() - startTime });
        }

        return bundleInfo;
    }

}