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
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 * 
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Limited. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.ajax.gwt.engines.filemanager.client;

import org.jahia.ajax.gwt.tripanelbrowser.client.TriPanelBrowserViewport;
import org.jahia.ajax.gwt.tripanelbrowser.client.components.*;

import org.jahia.ajax.gwt.engines.filemanager.client.components.*;
import org.jahia.ajax.gwt.filemanagement.client.ui.FileToolbar;
import org.jahia.ajax.gwt.filemanagement.client.ui.FileStatusBar;
import org.jahia.ajax.gwt.filemanagement.client.util.actions.ManagerConfiguration;
import org.jahia.ajax.gwt.filemanagement.client.util.actions.ManagerConfigurationFactory;

/**
 * Created by IntelliJ IDEA.
 *
 * @author rfelden
 * @version 19 juin 2008 - 15:22:43
 */
public class FileManager extends TriPanelBrowserViewport {

    public FileManager(String types, String filters, String mimeTypes, String conf) {
        // superclass constructor (define linker)
        super();

        ManagerConfiguration config ;
        if (conf != null && conf.length() > 0) {
            config = ManagerConfigurationFactory.getConfiguration(conf, linker) ;
        } else {
            config = ManagerConfigurationFactory.getFileManagerConfiguration(linker) ;
        }

        if (types != null && types.length() > 0) {
            config.setNodeTypes(types);
        }
        if (mimeTypes != null && mimeTypes.length() > 0) {
            config.setMimeTypes(mimeTypes);
        }
        if (filters != null && filters.length() > 0) {
            config.setFilters(filters);
        }

        // construction of the UI components
        LeftComponent tree = new FolderTree(config);
        final FilesView filesViews = new FilesView(config);
        BottomRightComponent tabs = new FileDetails(config);
        TopBar toolbar = new FileToolbar(config) {
            protected void setListView() {
                filesViews.switchToListView();
            }

            protected void setThumbView() {
                filesViews.switchToThumbView();
            }

            protected void setDetailedThumbView() {
                filesViews.switchToDetailedThumbView();
            }
        };
        BottomBar statusBar = new FileStatusBar();

        // setup widgets in layout
        initWidgets(tree.getComponent(),
                filesViews.getComponent(),
                tabs.getComponent(),
                toolbar.getComponent(),
                statusBar.getComponent());

        // linker initializations
        linker.registerComponents(tree, filesViews, tabs, toolbar, statusBar);
        filesViews.initContextMenu();
        linker.handleNewSelection();
    }
}
