/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
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
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.ajax.gwt.client.widget.form;

import com.extjs.gxt.ui.client.widget.form.TextArea;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Element;

public class CodeMirrorField extends TextArea {

    private Object codeMirror;
    private String mode = "jsp";
    private boolean readOnly = false;

    public void setMode(String mode) {
        this.mode = mode;
        if (codeMirror != null) {
            updateMode(mode);
        }
    }

    @Override
    protected void onRender(Element target, int index) {
        super.onRender(target, index);
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                codeMirror = initEditor(getInputEl().dom, mode, readOnly?"nocursor":"");
                updateSize();
            }
        });
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        updateSize();
    }

    private native Object initEditor(Element textArea, String mode, String readOnly)/*-{
        var myCodeMirror = $wnd.CodeMirror.fromTextArea(textArea, {mode:mode, lineNumbers:true, matchBrackets:true, readOnly:readOnly});
        return myCodeMirror;
    }-*/;

    @Override
    public String getRawValue() {
        if (codeMirror == null) {
            return super.getRawValue();
        }
        return getCodeMirrorValue();
    }

    @Override
    public boolean isDirty() {
        return isCodeMirrorDirty();
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    private native String getCodeMirrorValue()/*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        return myCodeMirror.getValue();
    }-*/;

    public void updateSize() {
        if (codeMirror != null) {
            setCodeMirrorSize(getWidth()-2, getHeight()-2);
        }
    }

    private native void setCodeMirrorSize(int width, int height)/*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        return myCodeMirror.setSize(width, height);
    }-*/;

    public void insertProperty(String value) {
        if(codeMirror!=null && !"".equals(value)) {
            insertPropertyAtCursor(value);
        }
    }

    private native void insertPropertyAtCursor(String value)/*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        myCodeMirror.replaceSelection(value);
        myCodeMirror.setCursor(myCodeMirror.getCursor("end"));
        myCodeMirror.focus();

    }-*/;

    public native void indent() /*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        var last = myCodeMirror.lineCount();
        myCodeMirror.operation(function() {
            for (var i = 0; i < last; ++i) myCodeMirror.indentLine(i);
        });
    }-*/;

    private native boolean isCodeMirrorDirty()/*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        return myCodeMirror.isClean();
    }-*/;

    private native void updateMode(String newMode)/*-{
        var myCodeMirror = this.@org.jahia.ajax.gwt.client.widget.form.CodeMirrorField::codeMirror;
        myCodeMirror.setOption("mode", newMode);
    }-*/;

}