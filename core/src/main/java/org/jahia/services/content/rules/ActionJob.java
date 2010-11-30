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

package org.jahia.services.content.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.jcr.RepositoryException;

public class ActionJob extends BackgroundJob {
	
    public static final String GROUP = "ACTIONS_JOBS";
    
    public static final String NAME_PREFIX = "ACTION_JOB_"; 
    
	private static transient Logger logger = LoggerFactory.getLogger(ActionJob.class);

    public static final String JOB_NODE_UUID = "node";
    public static final String JOB_ACTION_TO_EXECUTE = "actionToExecute";

    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        try {
            final JobDataMap map = jobExecutionContext.getJobDetail().getJobDataMap();
            final JCRSessionFactory sessionFactory = JCRSessionFactory.getInstance();
            final JCRSessionWrapper jcrSessionWrapper = sessionFactory.getCurrentUserSession();
            JCRNodeWrapper node = jcrSessionWrapper.getNodeByUUID(map.getString(JOB_NODE_UUID));
            final BackgroundAction action = ServicesRegistry.getInstance().getJahiaTemplateManagerService().getBackgroundActions().get(
                    map.getString(JOB_ACTION_TO_EXECUTE));
            if (action != null) {
                BackgroundAction backgroundAction = (BackgroundAction) action;
                backgroundAction.executeBackgroundAction(node);
            }
        } catch (RepositoryException e) {
            logger.error(e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}