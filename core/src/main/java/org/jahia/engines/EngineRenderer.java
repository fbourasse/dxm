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
//  EV      04.12.2000

package org.jahia.engines;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.jahia.data.JahiaData;
import org.jahia.exceptions.JahiaException;
import org.jahia.params.ParamBean;
import org.jahia.params.ProcessingContext;
import org.jahia.spring.aop.interceptor.SilentJamonPerformanceMonitorInterceptor;
import org.jahia.utils.FileUtils;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

public class EngineRenderer {

    private static Logger logger = Logger.getLogger (EngineRenderer.class);

    private static final Logger monitorLogger = Logger.getLogger(SilentJamonPerformanceMonitorInterceptor.class);

    public static final String FORM_TOKEN = "<!--ENGINE_CONTENT-->";
    private static final EngineRenderer instance = new EngineRenderer ();
    private static final String ENGINE_JSP = "/engines/engine.jsp";

    /**
     */
    private EngineRenderer () {
    }

    /**
     */
    public static EngineRenderer getInstance () {
        return instance;
    }

    /**
     * render ProcessingContext render EV    23.12.2000 AK    04.01.2001  remove the fileName from
     * method arguments
     */
    public void render (final ProcessingContext jParams, final Map<String, Object> engineHashMap)
            throws JahiaException {
        renderCore (jParams, engineHashMap);
    }

    /**
     * render JahiaData render EV    23.12.2000 AK    04.01.2001  remove the fileName from
     * method arguments
     */
    public void render (final JahiaData jData, final Map<String, Object> engineHashMap)
            throws JahiaException {
        jData.getProcessingContext().setAttribute ("org.jahia.data.JahiaData",
                jData);
        renderCore (jData.getProcessingContext (), engineHashMap);
    }

    /**
     * renderCore called by ProcessingContext render, JahiaData render EV    23.12.2000 AK    04.01.2001
     *  get the fileName from the engineHashMap and not from method arguments AK    04.01.2001
     * get the render type from the engineHashMap (include or forward) AK    04.01.2001  correct
     * the jahiaChrono call consoleMsg
     */
    private void renderCore (final ProcessingContext processingContext, final Map<String, Object> engineHashMap)
            throws JahiaException {

        String fileName = JahiaEngine.EMPTY_STRING;
        final ParamBean jParams = (ParamBean) processingContext;
        try {
            // add default & common properties to the hashmap...
            engineHashMap.put ("jahiaBuild",
                    new Integer (jParams.settings ().getBuildNumber ()));
            engineHashMap.put ("javaScriptPath",
                    jParams.settings ().getJsHttpPath ());
            engineHashMap.put ("imagesPath",
                    jParams.settings ().getEnginesContext () +
                    "engines/images/");
            if (engineHashMap.get (JahiaEngine.ENGINE_OUTPUT_FILE_PARAM) == null)
                engineHashMap.put (JahiaEngine.ENGINE_OUTPUT_FILE_PARAM, ENGINE_JSP);

            if (engineHashMap.get (JahiaEngine.ENGINE_URL_PARAM) == null)
                engineHashMap.put (JahiaEngine.ENGINE_URL_PARAM, JahiaEngine.EMPTY_STRING);

            String mimeType = "text/html";
            // did we override the mime type ?
            if (jParams.getResponseMimeType() != null) {
                mimeType = jParams.getResponseMimeType();
            }
            // set response attributes...
            final HttpServletResponse response = jParams.getResponse();
            response.setContentType (mimeType + ";charset=" + jParams.settings().getCharacterEncoding());

            final HttpServletRequest request = jParams.getRequest();

            // todo FIXME is this attribute name still used ? If not remove it
            jParams.getRequest ().setAttribute ("org.jahia.data.JahiaParams", jParams);

            request.setAttribute ("org.jahia.params.ParamBean", jParams);

            request.setAttribute ("org.jahia.engines.EngineHashMap",
                    engineHashMap);

            String jspSource = (String) engineHashMap.get ("jspSource");
            if (jspSource == null)
                jspSource = JahiaEngine.EMPTY_STRING;
            request.setAttribute ("jspSource", jspSource);

            if (request.getAttribute ("engineTitle") == null)
                request.setAttribute ("engineTitle", "No title");

            // get the fileName from the engineHashMap...
            fileName = (String) engineHashMap.get (JahiaEngine.ENGINE_OUTPUT_FILE_PARAM);

            // get the render type from the engineHashMap... (include or forward)
            final Integer renderType = (Integer) engineHashMap.get (JahiaEngine.RENDER_TYPE_PARAM);

            if (logger.isDebugEnabled()) {
                logger.debug ("Dispatching request to " + fileName +
                    " using render type " + renderType + "...");
            }

            Monitor listenerMonitor = null;
            if (monitorLogger.isDebugEnabled()) listenerMonitor = MonitorFactory.start(fileName);
            if (renderType.intValue () == JahiaEngine.RENDERTYPE_INCLUDE) {
                jParams.getContext().getRequestDispatcher (fileName).include( request, response);
            } else if (renderType.intValue() == JahiaEngine.RENDERTYPE_FORWARD) {
                jParams.getContext().getRequestDispatcher(fileName).forward( request, response);
            } else if (renderType.intValue () == JahiaEngine.RENDERTYPE_REDIRECT) {
                response.sendRedirect (fileName);
            } else if (renderType.intValue() == JahiaEngine.RENDERTYPE_NAMED_DISPATCHER) {
                jParams.getContext().getNamedDispatcher(fileName).forward( request, response);
            }
            if (listenerMonitor != null) listenerMonitor.stop();
        } catch (IOException ie) {
            if (fileName == null)
                fileName = "undefined file";
            final String errorMsg = "Error while drawing the Engine " + fileName +
                    " : " + ie.getMessage () + " -> BAILING OUT";
            logger.error (errorMsg, ie);
            throw new JahiaException (
                    "Error while drawing a Jahia engine's content",
                    errorMsg, JahiaException.WINDOW_ERROR,
                    JahiaException.CRITICAL_SEVERITY, ie);
        } catch (ServletException se) {
            if (fileName == null)
                fileName = "undefined file";
            if (se.getRootCause () != null) {
                String errorMsg = "Root cause : Error while forwarding the Engine " +
                        fileName + " : " +
                        se.getRootCause ().getMessage () +
                        " -> BAILING OUT";
                logger.error (errorMsg, se.getRootCause());
                throw new JahiaException (
                        "Error while forwarding a Jahia engine's content",
                        errorMsg,
                        JahiaException.WINDOW_ERROR,
                        JahiaException.CRITICAL_SEVERITY,
                        se);
            } else {
                String errorMsg = "Error while forwarding the Engine " +
                        fileName +
                        " : " +
                        se.getMessage () +
                        " -> BAILING OUT";
                logger.error (errorMsg, se);
                throw new JahiaException (
                        "Error while forwarding a Jahia engine's content",
                        errorMsg,
                        JahiaException.WINDOW_ERROR,
                        JahiaException.CRITICAL_SEVERITY,
                        se);
            }
        } catch(IllegalStateException ise) {
            logger.debug("Error while forwarding the Engine "+fileName,ise);
        }
    }

    /*******************************************************************************
     ****                                                                       ****
     ****        A PARTIR DE LA CES METHODES SONT DESTINEES A MOURIR....        ****
     ****                                                                       ****
     *******************************************************************************/

    /**
     * render EV    04.12.2000
     * @deprecated
     */
    public void render (JahiaData jData, String sourceFileName,
                        String formString)
            throws JahiaException {
        String fileName = jData.getProcessingContext ().settings ().getJahiaEnginesDiskPath () +
                File.separator + sourceFileName + ".html";
        String finalSource = decomposeSource (fileName, formString);
        displaySource (finalSource, jData.getProcessingContext ());
    } // end render

    /**
     * render EV    04.12.2000
     *  @deprecated
     */
    public void render (ProcessingContext jParams, String sourceFileName,
                        String formString)
            throws JahiaException {
        String fileName = jParams.settings ().getJahiaEnginesDiskPath () +
                File.separator + sourceFileName + ".html";
        String finalSource = decomposeSource (fileName, formString);
        displaySource (finalSource, jParams);
    } // end render

    /**
     * decomposeSource EV    04.12.2000
     *  @deprecated
     */

    private String decomposeSource (String fileName, String form)
            throws JahiaException {
        String source = FileUtils.readFile (fileName);
        String firstPart = source.substring (0, source.indexOf (FORM_TOKEN));
        String secondPart = source.substring (source.indexOf (FORM_TOKEN) +
                FORM_TOKEN.length (), source.length ());
        return firstPart + form + secondPart;
    } // end decomposeSource

    /**
     * displaySource EV    04.12.2000
     *  @deprecated
     */
    public void displaySource (String source, ProcessingContext processingContext)
            throws JahiaException {
        try {
            ParamBean jParams = (ParamBean) processingContext;
            PrintWriter out = jParams.getResponse ().getWriter ();
            jParams.getResponse ().setContentType ("text/html");
            out.println (source);
        } catch (IOException ie) {
            String errorMsg = "Error while drawing the Engine Window : " +
                    ie.getMessage () + " -> BAILING OUT";
            logger.error (errorMsg, ie);
            throw new JahiaException (
                    "Error while drawing a Jahia window's content",
                    errorMsg, JahiaException.WINDOW_ERROR,
                    JahiaException.CRITICAL_SEVERITY, ie);
        }
    } // end displaySource
}
