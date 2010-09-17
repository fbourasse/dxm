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

package org.jahia.ajax.gwt.client.widget.edit.contentengine;

import com.allen_sauer.gwt.log.client.Log;
import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.ButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.widget.Info;
import com.extjs.gxt.ui.client.widget.Label;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.extjs.gxt.ui.client.widget.toolbar.FillToolItem;
import com.extjs.gxt.ui.client.widget.toolbar.ToolBar;
import com.google.gwt.user.client.Window;
import org.jahia.ajax.gwt.client.core.BaseAsyncCallback;
import org.jahia.ajax.gwt.client.data.GWTJahiaLanguage;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodeProperty;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.util.definition.FormFieldCreator;
import org.jahia.ajax.gwt.client.widget.definition.PropertiesEditor;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: toto
 * Date: Jan 6, 2010
 * Time: 6:34:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesTabItem extends EditEngineTabItem {
    protected PropertiesEditor propertiesEditor;
    protected Map<String, PropertiesEditor> langPropertiesEditorMap;
    protected String dataType;
    protected List<String> excludedTypes;
    protected boolean multiLang = false;
    


    protected PropertiesTabItem(String title, NodeHolder engine, String dataType) {
        super(title, engine);
        this.dataType = dataType;
        langPropertiesEditorMap = new HashMap<String, PropertiesEditor>();
        setLayout(new FitLayout());
        setScrollMode(Style.Scroll.AUTO);
    }

    /**
     * Get properties editor of the default lang
     *
     * @return
     */
    public PropertiesEditor getPropertiesEditor() {
        return propertiesEditor;
    }

    /**
     * Get properties editor by langCode
     *
     * @param locale
     * @return
     */
    public PropertiesEditor getPropertiesEditorByLang(GWTJahiaLanguage locale) {
        if (locale == null) {
            Log.error("Locale is null");
            return null;
        }
        return langPropertiesEditorMap.get(locale.getLanguage());
    }

    /**
     * set properties editor by lang
     *
     * @param locale
     */
    private void setPropertiesEditorByLang(GWTJahiaLanguage locale) {
        if (langPropertiesEditorMap == null || locale == null) {
            return;
        }
        langPropertiesEditorMap.put(locale.getLanguage(), propertiesEditor);
    }

    @Override
    public void create(GWTJahiaLanguage locale) {
        // do not re-process the view if it's already done and the tabItem is not multilang
        if (!isMultiLang() && isProcessed()) {
            return;
        }
        mask(Messages.get("label.loading","Loading..."), "x-mask-loading");
        if (engine.getMixin() != null) {
            unmask();
            boolean addSharedLangLabel = true;
            List<GWTJahiaNodeProperty> previousNon18nProperties = null;

            if (propertiesEditor != null) {
                if (propertiesEditor == getPropertiesEditorByLang(locale)) {
                    return;
                }
                addSharedLangLabel = false;
                propertiesEditor.setVisible(false);
                // keep track of the old values
                previousNon18nProperties = propertiesEditor.getProperties(false, true, false);
            }
            if (!isMultiLang()) {
                setProcessed(true);
            }
            propertiesEditor = getPropertiesEditorByLang(locale);

            if (propertiesEditor == null) {
                if (engine.isExistingNode() && engine.getNode().isShared()) {
                    // this label is shared among languages.
                    if (addSharedLangLabel) {
                        Label label = new Label(Messages.get("warning.sharedNode", "Important : This is a shared node, editing it will modify its value for all its usages"));
                        label.setStyleAttribute("color", "rgb(200,80,80)");
                        label.setStyleAttribute("font-size", "14px");
                        add(label);
                    }
                }

                propertiesEditor = new PropertiesEditor(engine.getNodeTypes(), engine.getProperties(), dataType);                
                propertiesEditor.setMixin(engine.getMixin());
                propertiesEditor.setInitializersValues(engine.getInitializersValues());
                propertiesEditor.setWriteable(!engine.isExistingNode() || engine.getNode().isWriteable());
                propertiesEditor.setFieldSetGrouping(true);
                propertiesEditor.setExcludedTypes(excludedTypes);
                propertiesEditor.renderNewFormPanel();
                setPropertiesEditorByLang(locale);

                attachPropertiesEditor();

            }

            // synch non18n properties
            if (isMultiLang()) {
                if (previousNon18nProperties != null && !previousNon18nProperties.isEmpty()) {
                    Map<String, Field<?>> fieldsMap = propertiesEditor.getFieldsMap();
                    for (GWTJahiaNodeProperty property : previousNon18nProperties) {
                        if (fieldsMap.containsKey(property.getName()))  {
                            FormFieldCreator.fillValue(fieldsMap.get(property.getName()), propertiesEditor.getGWTJahiaItemDefinition(property), property);
                        }
                    }
                }
            }
            
            if (isMultiLang()) {
                List<GWTJahiaNodeProperty> nodeProperties = propertiesEditor.getProperties(true, false, false, true);
                for (GWTJahiaNodeProperty property : nodeProperties) {
                    Map<String, Field<?>> fieldsMap = propertiesEditor.getFieldsMap();
                    if (fieldsMap.containsKey(property.getName())) {
                        FormFieldCreator.fillValue(fieldsMap.get(property.getName()),
                                propertiesEditor.getGWTJahiaItemDefinition(property), property);
                    }
                }
            }
            propertiesEditor.setVisible(true);

            layout();
        }
    }


    /**
     * Warning: this current layout is a FitLayout. That means that if you overide this method in order to add other subelement, you have to use a wrapper.
     * See ContentTabItem as an example of overriding
     *
     * call after created:
     */
    public void attachPropertiesEditor() {
        add(propertiesEditor);
        layout();
    }

    public boolean isMultiLang() {
        return multiLang;
    }

    public void setMultiLang(boolean multiLang) {
        this.multiLang = multiLang;
    }


    /**
     * Get lang properties per map
     *
     * @return
     * @param modifiedOnly
     */
    public Map<String, List<GWTJahiaNodeProperty>> getLangPropertiesMap(boolean modifiedOnly) {
        Map<String, List<GWTJahiaNodeProperty>> mapProperties = new HashMap<String, List<GWTJahiaNodeProperty>>();
        Iterator<String> langCodes = langPropertiesEditorMap.keySet().iterator();
        while (langCodes.hasNext()) {
            String langCode = langCodes.next();
            mapProperties.put(langCode, langPropertiesEditorMap.get(langCode).getProperties(true, false, modifiedOnly));
        }
        return mapProperties;
    }

    public void setProcessed(boolean processed) {
        if (!processed && langPropertiesEditorMap != null) {
            langPropertiesEditorMap.clear();
            propertiesEditor = null;
        }
        super.setProcessed(processed);
    }
}
