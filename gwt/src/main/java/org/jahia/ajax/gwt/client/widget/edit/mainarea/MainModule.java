package org.jahia.ajax.gwt.client.widget.edit.mainarea;

import com.allen_sauer.gwt.log.client.Log;
import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.*;
import com.extjs.gxt.ui.client.widget.Header;
import com.extjs.gxt.ui.client.widget.button.ToolButton;
import com.extjs.gxt.ui.client.widget.layout.FlowLayout;
import com.extjs.gxt.ui.client.widget.tips.ToolTipConfig;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import org.jahia.ajax.gwt.client.core.BaseAsyncCallback;
import org.jahia.ajax.gwt.client.core.JahiaGWTParameters;
import org.jahia.ajax.gwt.client.data.GWTRenderResult;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.data.toolbar.GWTEditConfiguration;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.widget.Linker;
import org.jahia.ajax.gwt.client.widget.edit.EditLinker;
import org.jahia.ajax.gwt.client.widget.edit.contentengine.EditContentEnginePopupListener;
import org.jahia.ajax.gwt.client.widget.toolbar.ActionMenu;

import java.util.*;

/**
 * First module of any rendered element.
 * Sub content will be created as ListModule or SimpleModule.
 */
public class MainModule extends Module {

    private static MainModule module;
    private String originalHtml;
    private EditLinker editLinker;
    private ActionMenu contextMenu;
    private GWTEditConfiguration config;

    Map<Element, Module> m;

    public MainModule(final String html, final String path, final String template, GWTEditConfiguration config) {
        super("main", path, template, null, null, null, null, new FlowLayout());
        setScrollMode(Style.Scroll.AUTO);

        this.id = "main";
        this.originalHtml = html;
        this.path = path;
        this.template = template;
        this.config = config;
        this.depth = 0;

        head = new Header();
        head.setText("Page : " + path);
        head.addStyleName("x-panel-header");
        head.setStyleAttribute("z-index", "999");
        head.setStyleAttribute("position", "relative");
        head.addTool(new ToolButton("x-tool-refresh", new SelectionListener<IconButtonEvent>() {
            public void componentSelected(IconButtonEvent event) {
                mask("Loading", "x-mask-loading");
                refresh(EditLinker.REFRESH_MAIN);
            }
        }));

        Hover.getInstance().setMainModule(this);
        Selection.getInstance().setMainModule(this);

        module = this;
        exportStaticMethod();
    }

    public void initWithLinker(EditLinker linker) {
        this.editLinker = linker;
        display(originalHtml);

        sinkEvents(Event.ONCLICK + Event.ONDBLCLICK + Event.ONMOUSEOVER + Event.ONMOUSEOUT);

        Listener<ComponentEvent> listener = new Listener<ComponentEvent>() {
            public void handleEvent(ComponentEvent ce) {
                makeSelected();
            }
        };

        // on click listener
        addListener(Events.OnClick, listener);

        // on double click listener
        addListener(Events.OnDoubleClick, new EditContentEnginePopupListener(this, editLinker));

        if (config.getContextMenu() != null) {
            // contextMenu
            contextMenu = new ActionMenu(config.getContextMenu(), editLinker) {
                @Override
                public void beforeShow() {
                    makeSelected();
                    super.beforeShow();
                }
            };
            setContextMenu(contextMenu);
        }

    }

    /**
     * select current module
     */
    public void makeSelected() {
        if (selectable) {
            editLinker.onModuleSelection(MainModule.this);
        }
    }

    public EditLinker getEditLinker() {
        return editLinker;
    }

    public void refresh(int flag) {
        if ((flag & Linker.REFRESH_MAIN) != 0) {
            refresh();
        }
    }

    private void refresh() {
        JahiaContentManagementService.App.getInstance()
                .getRenderedContent(path, null, editLinker.getLocale(), template, "gwt", moduleParams, true,
                        config.getName(), new BaseAsyncCallback<GWTRenderResult>() {
                            public void onSuccess(GWTRenderResult result) {
                                int i = getVScrollPosition();
                                head.setText("Page : " + path);
                                removeAll();
                                Selection.getInstance().hide();
                                Hover.getInstance().removeAll();
                                display(result.getResult());

                                setVScrollPosition(i);
                                List<String> list = new ArrayList<String>(1);
                                list.add(path);
                                editLinker.getMainModule().unmask();
                                editLinker.onModuleSelection(MainModule.this);
                                editLinker.getSidePanel().refresh(Linker.REFRESH_WORKFLOW);
                                switchStaticAssets(result.getStaticAssets());
                            }

                        });

    }

    private void switchStaticAssets(Map<String, Set<String>> assets) {
        removeAllAssets();
        int i = 1;
        for (Map.Entry<String, Set<String>> entry : assets.entrySet()) {
            for (String s : entry.getValue()) {
                addAsset(entry.getKey().toLowerCase(), s, i++);
            }
        }
    }

    private native void removeAllAssets() /*-{
        var links = $doc.getElementsByTagName("link");
        for (var i=links.length; i>=0; i--){ //search backwards within nodelist for matching elements to remove
            if (links[i] && links[i].getAttribute("id")!=null && links[i].getAttribute("id").indexOf("staticAsset")==0)
                links[i].parentNode.removeChild(links[i]) //remove element by calling parentNode.removeChild()
        }
        var scripts = $doc.getElementsByTagName("script");
        for (var i=scripts.length; i>=0; i--){ //search backwards within nodelist for matching elements to remove
            if (scripts[i] && scripts[i].getAttribute("id")!=null && scripts[i].getAttribute("id").indexOf("staticAsset")==0)
                scripts[i].parentNode.removeChild(scripts[i]) //remove element by calling parentNode.removeChild()
        }
    }-*/;

    private native void addAsset(String filetype, String filename, int i) /*-{
        if (filetype=="javascript"){ //if filename is a external JavaScript file
            var fileref=$doc.createElement('script')
            fileref.setAttribute("id","staticAsset"+i)
            fileref.setAttribute("type","text/javascript")
            fileref.setAttribute("src", filename)
            $doc.getElementsByTagName("head")[0].appendChild(fileref)
        } else if (filetype=="css"){ //if filename is an external CSS file
            var fileref=$doc.createElement("link")
            fileref.setAttribute("id","staticAsset"+i)
            fileref.setAttribute("rel", "stylesheet")
            fileref.setAttribute("type", "text/css")
            fileref.setAttribute("href", filename)
            $doc.getElementsByTagName("head")[0].appendChild(fileref)
        }

    }-*/;

    private void display(String result) {
        add(head);
        html = new HTML(result);
        add(html);
        ModuleHelper.tranformLinks(html);
        ModuleHelper.initAllModules(this, html);
        ModuleHelper.buildTree(this);
        long start = System.currentTimeMillis();
        parse();
        Log.info("Parse : "+(System.currentTimeMillis() - start));
        layout();
    }

    @Override
    protected void onAfterLayout() {
        super.onAfterLayout();
        if (m != null) {
            ModuleHelper.move(m);
        }
    }

    public void parse() {
        m = ModuleHelper.parse(this, null);
    }

    public String getModuleId() {
        return "main";
    }

    public void goTo(String path, String template) {
        mask("Loading", "x-mask-loading");
        this.path = path;
        this.template = template;
        refresh();
    }

    public static void staticGoTo(String path, String template, String param) {
        Map<String,String> params = null;
        if (param.length() > 0) {
            params = new HashMap<String,String>();
            for (String s : param.split("&")) {
                final String[] key = param.split("=");
                params.put(key[0], key[1]);
            }
        }
        module.mask("Loading", "x-mask-loading");
        module.path = path;
        module.template = template;
        module.moduleParams = params;
        module.refresh();
    }

    public void switchLanguage(String language) {
        mask("Loading", "x-mask-loading");
        editLinker.setLocale(language);
        refresh();
    }

    public void setNode(GWTJahiaNode node) {
        this.node = node;
        if (node.getNodeTypes().contains("jnt:page") || node.getInheritedNodeTypes().contains("jnt:page")) {
//            editManager.getEditLinker().getCreatePageButton().setEnabled(true);
        }
        if (node.isShared()) {
//            this.setStyleAttribute("background","rgb(210,50,50) url("+ JahiaGWTParameters.getContextPath()+"/css/images/andromeda/rayure.png)");
            this.setToolTip(new ToolTipConfig(Messages.get("info_important", "Important"),
                    Messages.get("info_sharednode", "This is a shared node")));
        }
        if (node.getSiteUUID() != null && !JahiaGWTParameters.getSiteUUID().equals(node.getSiteUUID())) {
            JahiaGWTParameters.setSiteUUID(node.getSiteUUID());
        }
    }

    public GWTEditConfiguration getConfig() {
        return config;
    }

    public void handleNewModuleSelection(Module selectedModule) {
        Selection l = Selection.getInstance();
        l.hide();
        if (selectedModule != null) {
            l.select(selectedModule);
            l.show();
        }
        l.layout();
    }

    public void handleNewSidePanelSelection(GWTJahiaNode node) {

    }

    public boolean isDraggable() {
        return false;
    }

    public static native void exportStaticMethod() /*-{
       $wnd.goTo = function(path,template,params) {
          @org.jahia.ajax.gwt.client.widget.edit.mainarea.MainModule::staticGoTo(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(path,template,params);
       }
    }-*/;


}
