/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.services.preferences;

import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;

/**
 * 
 * User: jahia
 * Date: 11 mars 2009
 * Time: 15:06:47
 */
public class JahiaPreferencesQueryHelper {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JahiaPreferencesQueryHelper.class);

    public static String getSimpleSQL(String prefName) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("j:prefName", prefName);
        return convertToSQL(properties);
    }


    public static String getPortletSQL(String portletName, String prefName) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("j:portletName", portletName);
        properties.put("j:prefName", prefName);
        return convertToSQL(properties);
    }

    public static String getPortletSQL(String portletName) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("j:portletName", portletName);
        return convertToSQL(properties);
    }

    /**
     * Convert a map of String Object to an sqlConstraint
     *
     * @param propertiesMap Map of property key value
     * @return a string containing sql constraint to be used with JahiaPreferencesProvider
     */
    public static String convertToSQL(Map<String, Object> propertiesMap) {
        StringBuilder prefPath = new StringBuilder();
        if (propertiesMap != null && !propertiesMap.isEmpty()) {
            Iterator<?> propertiesIterator = propertiesMap.keySet().iterator();
            boolean isFirstProperty = true;
            while (propertiesIterator.hasNext()) {
                String propertyName = (String) propertiesIterator.next();
                Object propertyValue = propertiesMap.get(propertyName);
                // add only if value is not null
                if (propertyValue != null) {
                    if (isFirstProperty) {
                        isFirstProperty = false;
                    } else {
                        prefPath.append(" and ");
                    }
                    prefPath.append("p.[").append(propertyName).append("]='").append(propertyValue.toString()).append("'");
                }
            }
        }
        return prefPath.toString();
    }

    /**
     * Convert a map of String String to an sqlConstraint
     *
     * @param propertiesMap Map of property key value
     * @return a string containing sql constraint to be used with JahiaPreferencesProvider
     */
    public static String convertToSQLPureStringProperties(Map<String, String> propertiesMap) {
        StringBuilder prefPath = new StringBuilder();
        if (propertiesMap != null && !propertiesMap.isEmpty()) {
            Iterator<?> propertiesIterator = propertiesMap.keySet().iterator();
            boolean isFirstProperty = true;
            while (propertiesIterator.hasNext()) {
                String propertyName = (String) propertiesIterator.next();
                String propertyvalue = propertiesMap.get(propertyName);
                // add only if value is not null
                if (propertyvalue != null) {
                    if (isFirstProperty) {
                        isFirstProperty = false;
                    } else {
                        prefPath.append(" and ");
                    }
                    prefPath.append("p.[").append(propertyName).append("]='").append(propertyvalue).append("'");
                }
            }
        }
        return prefPath.toString();
    }

}
