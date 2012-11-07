package org.jahia.ajax.gwt.client.widget.contentengine;

import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.ButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.MessageBox;
import com.extjs.gxt.ui.client.widget.Window;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.form.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import org.jahia.ajax.gwt.client.core.JahiaGWTParameters;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.util.icons.StandardIconsProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * User: david
 * Date: 11/5/12
 * Time: 9:59 AM
 * Defines a button that opens a popup and allows to save a view ( see ModuleDataSource ) with custom parameters
 */
public class SaveAsViewButtonItem extends SaveButtonItem {

    public Button create(final AbstractContentEngine engine) {
        Button button = new Button(Messages.get("label.saveasnewview","Save as ..."));
        button.setHeight(BUTTON_HEIGHT);
        button.setIcon(StandardIconsProvider.STANDARD_ICONS.engineButtonOK());
        button.addSelectionListener(new SelectionListener<ButtonEvent>() {
            public void componentSelected(ButtonEvent event) {
                final String[] filePath = engine.getLinker().getSelectionContext().getMainNode().getPath().split("/");
                if (filePath.length != 7) {
                    MessageBox.alert("save not work as excpected","An issue occurs when trying to resolve " + engine.getLinker().getSelectionContext().getMainNode().getPath(),null);
                }
                final String modulePath = "/" + filePath[1] + "/"+ filePath[2];
                final String moduleName = filePath[2];
                final String moduleVersion =filePath[3];
                final String fileName = filePath[6];
                final String fileView = fileName.substring(fileName.indexOf(".") + 1,fileName.lastIndexOf("."));
                final String fileType = filePath[4];
                final String fileTemplateType = filePath[5];


                // Open popup to select module

                final Window popup = new Window();
                popup.setHeading(Messages.get("label.saveAsView","Save as view"));
                popup.setHeight(200);
                popup.setWidth(350);
                popup.setModal(true);
                FormPanel f = new FormPanel();
                f.setHeaderVisible(false);
                final SimpleComboBox<String> dependenciesCombo = new SimpleComboBox<String>();
                if (JahiaGWTParameters.getSiteNode() != null && JahiaGWTParameters.getSiteNode().getProperties().get("j:dependencies") != null) {
                    dependenciesCombo.setStore(new ListStore<SimpleComboValue<String>>());
                    dependenciesCombo.setFieldLabel("module");
                    dependenciesCombo.setTriggerAction(ComboBox.TriggerAction.ALL);
                    dependenciesCombo.add(moduleName);
                    for (String s : (List<String>) JahiaGWTParameters.getSiteNode().getProperties().get("j:dependencies")) {
                        dependenciesCombo.add(s);
                    }
                    dependenciesCombo.setSimpleValue(moduleName);
                    f.add(dependenciesCombo);
                }
                final TextField<String> templateType = new TextField<String>();
                templateType.setFieldLabel("template Type");
                templateType.setValue(fileTemplateType);
                f.add(templateType);

                final TextField<String> viewName = new TextField<String>();
                viewName.setFieldLabel("View name");
                viewName.setValue(fileView);
                f.add(viewName);

                Button b = new Button("submit");
                f.addButton(b);
                b.addSelectionListener(new SelectionListener<ButtonEvent>() {
                    @Override
                    public void componentSelected(ButtonEvent buttonEvent) {
                        String newModuleName = moduleName;
                        String newModulePath = modulePath;
                        String newModuleVersion = moduleVersion;

                        for (GWTJahiaNode n : JahiaGWTParameters.getSitesMap().values()) {
                            if (n.getName().equals(dependenciesCombo.getSimpleValue())) {
                                newModuleName = dependenciesCombo.getSimpleValue();
                                newModulePath = n.getPath().replace("/modules/","/" + filePath[1] + "/");
                                newModuleVersion = (String) n.getProperties().get("j:versionInfo");
                                break;
                            }
                        }
                        String newfileTemplateType = !"".equals(templateType.getValue())?templateType.getValue():fileTemplateType;
                        String newfileView = !"".equals(viewName.getValue())?viewName.getValue():fileView;
                        newModulePath = newModulePath + "/" +
                                newModuleVersion + "/" +
                                fileType + "/" +
                                newfileTemplateType + "/";

                        String newViewName = fileType.split("_")[1] + "." + newfileView + fileName.substring(fileName.lastIndexOf("."));
                        Map<String, String> parentNodesType = new LinkedHashMap<java.lang.String, java.lang.String>();

                        parentNodesType.put(filePath[1], "jnt:folder");
                        parentNodesType.put(newModuleName, "jnt:folder");
                        parentNodesType.put(newModuleVersion, "jnt:folder");
                        parentNodesType.put(fileType, "jnt:folder");
                        parentNodesType.put(newfileTemplateType, "jnt:folder");
                        prepareAndSave(newModulePath, newViewName, parentNodesType, engine);
                        popup.hide();
                    }
                });
                Button c = new Button("Cancel");
                c.addSelectionListener(new SelectionListener<ButtonEvent>() {
                    @Override
                    public void componentSelected(ButtonEvent buttonEvent) {
                        popup.hide();
                    }
                });
                f.addButton(c);
                f.setButtonAlign(Style.HorizontalAlignment.CENTER);

                FormButtonBinding binding = new FormButtonBinding(f);
                binding.addButton(b);
                popup.add(f);
                popup.show();
            }
        });
        return button;
    }

    protected void prepareAndSave(String modulePath,String viewName,Map<String, String> parentNodesType, final AbstractContentEngine engine) {

        JahiaContentManagementService.App.getInstance().createNode(modulePath, viewName, "jnt:viewFile", null, null, null, null, parentNodesType, new AsyncCallback<GWTJahiaNode>() {
            @Override
            public void onFailure(Throwable throwable) {
                MessageBox.alert("save not work as excpected",throwable.getMessage(),null);
            }

            @Override
            public void onSuccess(GWTJahiaNode gwtJahiaNode) {
                prepareAndSave(engine,true);
            }
        });


    }

    @Override
    protected void prepareAndSave(final AbstractContentEngine engine, boolean closeAfterSave) {
        engine.close();
    }
}
