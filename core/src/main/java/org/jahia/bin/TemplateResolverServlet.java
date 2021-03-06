/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.bin;

import org.jahia.api.Constants;
import org.jahia.exceptions.JahiaBadRequestException;
import org.jahia.services.content.*;
import org.jahia.services.render.RenderContext;
import org.jahia.services.search.LinkGenerator;
import org.jahia.services.search.MatchInfo;
import org.jahia.services.seo.urlrewrite.UrlRewriteService;
import org.jahia.services.usermanager.JahiaUser;
import org.springframework.web.servlet.ModelAndView;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

/**
 * A servlet providing delayed resolution of JCR nodes targeted by links created by {@link LinkGenerator}. This
 * indirection allows {@link org.jahia.services.search.SearchProvider} implementations to avoid resolving JCR nodes
 * when returning results to be displayed. This servlet is configured in
 * {@code servlet-applicationcontext-renderer.xml} and is mapped on {@code /resolve}.
 *
 * @author Christophe Laprun
 */
public class TemplateResolverServlet extends JahiaController {
    private JCRSessionFactory sessionFactory;
    private UrlRewriteService urlService;

    @Override
    public ModelAndView handleRequest(final HttpServletRequest req, final HttpServletResponse resp) throws Exception {
        final JahiaUser currentUser = sessionFactory.getCurrentUser();
        final RenderContext context = new RenderContext(req, resp, currentUser);
        final String pathInfo = req.getPathInfo();
        final int index = pathInfo.indexOf('/', 1);
        if (index == -1 || index == pathInfo.length() - 1) {
            throw new JahiaBadRequestException("Invalid path");
        }
        final String resolve = pathInfo.substring(0, index);
        context.setServletPath(req.getServletPath() + resolve);

        try {
            // extract match info from URI
            final MatchInfo info = LinkGenerator.decomposeLink(pathInfo);

            // retrieve the path of the displayable node associated with the node identified by the match info
            final String workspace = info.getWorkspace();
            final String lang = info.getLang();
            final Locale locale = Locale.forLanguageTag(lang);
            String redirect = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(
                    currentUser, workspace, locale, new JCRCallback<String>() {
                        @Override
                        public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                            final JCRNodeWrapper node = session.getNodeByIdentifier(info.getId());
                            final JCRNodeWrapper displayableNode = JCRContentUtils.findDisplayableNode(node, context);
                            if (displayableNode != null) {
                                return req.getContextPath() + Render.getRenderServletPath() + '/' + workspace +
                                        '/' + lang + displayableNode.getPath() + ".html";
                            }
                            return null;
                        }
                    });

            // if we have found a displayable node, redirect to it
            if (redirect != null) {
                // check if we have a vanity URL for this node
                if (Constants.LIVE_WORKSPACE.equals(workspace)) {
                    redirect = urlService.rewriteOutbound(redirect, req, resp);
                }
                resp.sendRedirect(redirect);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

        return null;
    }

    /**
     * Injects the entry point to the JCR repository to resolve JCR nodes.
     *
     * @param jcrSessionFactory the JCR repository to resolve JCR nodes
     */
    public void setJcrSessionFactory(JCRSessionFactory jcrSessionFactory) {
        this.sessionFactory = jcrSessionFactory;
    }

    /**
     * Injects the {@link UrlRewriteService} to rewrite URLs if needed (for example, to deal with vanity URLs).
     *
     * @param urlService the {@link UrlRewriteService}
     */
    public void setUrlService(UrlRewriteService urlService) {
        this.urlService = urlService;
    }
}
