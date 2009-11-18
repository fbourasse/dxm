/**
 *
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2009 Jahia Limited. All rights reserved.
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
 * in Jahia's FLOSS exception. You should have recieved a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license"
 *
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Limited. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.content.nodetypes.initializers;

import org.jahia.bin.Jahia;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.operations.valves.ThemeValve;
import org.jahia.params.ProcessingContext;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;

import javax.jcr.Value;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Choice list implementation that looks up image files.
 *
 * @author : rincevent
 * @since : JAHIA 6.1
 *        Created : 17 nov. 2009
 */
public class ImageChoiceListInitializerImpl implements ChoiceListInitializer {

    public List<ChoiceListValue> getChoiceListValues(ProcessingContext context, ExtendedPropertyDefinition epd,
                                                     String param, String realNodeType, List<ChoiceListValue> values) {
        if (values != null && values.size() > 0) {
            String templatePackageName = context.getSite().getTemplatePackageName();
            JahiaTemplatesPackage pkg = ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackage(
                    templatePackageName);
            for (ChoiceListValue bean : values) {
                String path = null;
                for (Object o : pkg.getLookupPath()) {
                    String rootFolderPath = (String) o;
                    // look for theme png name
                    final Value value = bean.getValue();
                    String lookupFile = Jahia.getStaticServletConfig().getServletContext().getRealPath(
                            rootFolderPath + "/" + epd.getName() + "s/" + value + "_" + context.getAttribute(
                                    ThemeValve.THEME_ATTRIBUTE_NAME + "_" + context.getSite().getID()) + ".png");
                    File ft = new File(lookupFile);
                    if (ft.exists()) {
                        path = rootFolderPath + "/" + epd.getName() + "s/" + value + "_" + context.getAttribute(
                                ThemeValve.THEME_ATTRIBUTE_NAME + "_" + context.getSite().getID()) + ".png";
                    } else {
                        File f = new File(Jahia.getStaticServletConfig().getServletContext().getRealPath(
                                rootFolderPath + "/" + epd.getName() + "s/" + value + ".png"));
                        if (f.exists()) {
                            path = rootFolderPath + "/" + epd.getName() + "s/" + value + ".png";
                        }
                    }
                }
                if (path != null) {
                    bean.addProperty("image", context.getContextPath() + path);
                } else {
                    bean.addProperty("image", context.getContextPath() + "/css/blank.gif");
                }
            }
            return values;
        }
        return new ArrayList<ChoiceListValue>();
    }
}
