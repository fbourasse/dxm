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

package org.jahia.test.services.render.filter.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jahia.api.Constants;
import org.jahia.bin.Jahia;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.CacheEntry;
import org.jahia.services.content.*;
import org.jahia.services.render.filter.cache.AggregateCacheFilter;
import org.jahia.services.render.filter.cache.ModuleCacheProvider;
import org.jahia.services.render.filter.cache.ModuleGeneratorQueue;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.usermanager.jcr.JCRGroup;
import org.jahia.services.usermanager.jcr.JCRGroupManagerProvider;
import org.jahia.services.usermanager.jcr.JCRUser;
import org.jahia.services.usermanager.jcr.JCRUserManagerProvider;
import org.jahia.test.JahiaTestCase;
import org.jahia.test.TestHelper;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.NodeIterator;
import javax.jcr.query.Query;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class CacheFilterHttpTest extends JahiaTestCase {
    transient static Logger logger = LoggerFactory.getLogger(CacheFilterTest.class);
    private final static String TESTSITE_NAME = "cachetest";
    private static final String SITECONTENT_ROOT_NODE = "/sites/" + TESTSITE_NAME;

    @BeforeClass
    public static void oneTimeSetUp() throws Exception {
        try {
            JahiaSite site = TestHelper.createSite(TESTSITE_NAME, "localhost", "templates-web-blue");
            assertNotNull(site);
            JCRStoreService jcrService = ServicesRegistry.getInstance()
                    .getJCRStoreService();
            JCRSessionWrapper session = jcrService.getSessionFactory()
                    .getCurrentUserSession();

            ServicesRegistry.getInstance().getJahiaTemplateManagerService().installModule("jahia-test-module", SITECONTENT_ROOT_NODE, session.getUser().getUsername());

            final JCRUserManagerProvider userManagerProvider = JCRUserManagerProvider.getInstance();
            final JCRUser userAB = userManagerProvider.createUser("userAB", "password", new Properties());
            final JCRUser userAC = userManagerProvider.createUser("userAC", "password", new Properties());
            final JCRUser userBC = userManagerProvider.createUser("userBC", "password", new Properties());

            // Create three groups
            final JCRGroupManagerProvider groupManagerProvider = JCRGroupManagerProvider.getInstance();
            final JCRGroup groupA = groupManagerProvider.createGroup(site.getID(), "groupA", new Properties(), false);
            final JCRGroup groupB = groupManagerProvider.createGroup(site.getID(), "groupB", new Properties(), false);
            final JCRGroup groupC = groupManagerProvider.createGroup(site.getID(), "groupC", new Properties(), false);
            // Associate each user to two group
            groupA.addMember(userAB);
            groupA.addMember(userAC);
            groupB.addMember(userAB);
            groupB.addMember(userBC);
            groupC.addMember(userAC);
            groupC.addMember(userBC);

            InputStream importStream = CacheFilterHttpTest.class.getClassLoader().getResourceAsStream("imports/cachetest-site.xml");
            session.importXML(SITECONTENT_ROOT_NODE, importStream,
                    ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);
            importStream.close();
            session.save();
            JCRNodeWrapper siteNode = session.getNode(SITECONTENT_ROOT_NODE);
            JCRPublicationService.getInstance().publishByMainId(siteNode.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null,
                    true, null);

        } catch (Exception e) {
            logger.warn("Exception during test setUp", e);
        }
    }


    @AfterClass
    public static void oneTimeTearDown() throws Exception {
        try {
            TestHelper.deleteSite(TESTSITE_NAME);
            final JCRUserManagerProvider userManagerProvider = JCRUserManagerProvider.getInstance();
            userManagerProvider.deleteUser(userManagerProvider.lookupUser("userAB"));
            userManagerProvider.deleteUser(userManagerProvider.lookupUser("userAC"));
            userManagerProvider.deleteUser(userManagerProvider.lookupUser("userBC"));
        } catch (Exception e) {
            logger.warn("Exception during test tearDown", e);
        }
        JCRSessionFactory.getInstance().closeAllSessions();
    }

    @Before
    public void setUp() {
        ModuleCacheProvider cacheProvider = ModuleCacheProvider.getInstance();
        Ehcache cache = cacheProvider.getCache();
        Ehcache depCache = cacheProvider.getDependenciesCache();
        cache.flush();
        cache.removeAll();
        depCache.flush();
        depCache.removeAll();
        AggregateCacheFilter.flushNotCacheableFragment();
        CacheFilterCheckFilter.clear();
        cache.getCacheConfiguration().setEternal(true);
        depCache.getCacheConfiguration().setEternal(true);
    }

    @After
    public void tearDown() {
        ModuleCacheProvider cacheProvider = ModuleCacheProvider.getInstance();
        Ehcache cache = cacheProvider.getCache();
        Ehcache depCache = cacheProvider.getDependenciesCache();
        cache.getCacheConfiguration().setEternal(false);
        depCache.getCacheConfiguration().setEternal(false);
    }

    @Test
    public void testStartPage() throws Exception {
        final URL url = new URL(getBaseServerURL() + Jahia.getContextPath() + "/start");

        HttpThread t1 = new HttpThread(url, "root", "root1234", null);
        HttpThread t2 = new HttpThread(url, "userAB", "password", null);
        HttpThread t3 = new HttpThread(url, "userBC", "password", null);
        HttpThread t4 = new HttpThread(url, "userAC", "password", null);

        t1.start();
        t2.start();
        t3.start();
        t4.start();

        t1.join();
        t2.join();
        t3.join();
        t4.join();

        String root = t1.getResult();
        String userAB = t2.getResult();
        String userBC = t3.getResult();
        String userAC = t4.getResult();

        assertEquals("Content served is not the same", root, getContent(url, "root", "root1234", null));
        assertEquals("Content served is not the same", userAB, getContent(url, "userAB", "password", null));
        assertEquals("Content served is not the same", userBC, getContent(url, "userBC", "password", null));
        assertEquals("Content served is not the same", userAC, getContent(url, "userAC", "password", null));
        assertEquals("Content served is not the same", userBC, getContent(url, "userBC", "password", null));
        assertEquals("Content served is not the same", userAC, getContent(url, "userAC", "password", null));
        assertEquals("Content served is not the same", root, getContent(url, "root", "root1234", null));
        assertEquals("Content served is not the same", userAB, getContent(url, "userAB", "password", null));

        ServicesRegistry.getInstance().getCacheService().flushAllCaches();
        assertEquals("Content served is not the same", root, getContent(url, "root", "root1234", null));
        assertEquals("Content served is not the same", userAB, getContent(url, "userAB", "password", null));
        assertEquals("Content served is not the same", userBC, getContent(url, "userBC", "password", null));
        assertEquals("Content served is not the same", userAC, getContent(url, "userAC", "password", null));
    }

    @Test
    public void testACLs() throws Exception {
        testACLs(SITECONTENT_ROOT_NODE + "/home/acl1");
        testACLs(SITECONTENT_ROOT_NODE + "/home/acl2");
    }

    private void testACLs(String path) throws Exception {
        String guest = getContent(getUrl(path), null, null, null);
        String root = getContent(getUrl(path), "root", "root1234", null);
        String userAB = getContent(getUrl(path), "userAB", "password", null);
        String userBC = getContent(getUrl(path), "userBC", "password", null);
        String userAC = getContent(getUrl(path), "userAC", "password", null);

        checkAcl(guest, new boolean[]{false, false, false, false, false, false, false, false});
        checkAcl(root, new boolean[]{true, true, true, true, true, true, true, true});
        checkAcl(userAB, new boolean[]{false, true, true, false, false, true, true, false});
        checkAcl(userBC, new boolean[]{false, true, false, true, false, false, true, true});
        checkAcl(userAC, new boolean[]{false, true, false, false, true, true, false, true});

        JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("live", new Locale("en"));
        JCRNodeWrapper n = session.getNode(path + "/maincontent/simple-text-A");
        try {
            n.revokeRolesForPrincipal("g:groupA");
            n.grantRoles("g:groupB", new HashSet<String>(Arrays.asList("reader")));
            session.save();

            String guest2 = getContent(getUrl(path), null, null, "testACLs1");
            String root2 = getContent(getUrl(path), "root", "root1234", "testACLs2");
            String userAB2 = getContent(getUrl(path), "userAB", "password", "testACLs3");
            String userBC2 = getContent(getUrl(path), "userBC", "password", "testACLs4");
            String userAC2 = getContent(getUrl(path), "userAC", "password", "testACLs5");

            checkAcl(guest2, new boolean[]{false, false, false, false, false, false, false, false});
            checkAcl(root2, new boolean[]{true, true, true, true, true, true, true, true});
            checkAcl(userAB2, new boolean[]{false, true, true, false, false, true, true, false});
            checkAcl(userBC2, new boolean[]{false, true, false, true, false, true, true, true});
            checkAcl(userAC2, new boolean[]{false, true, false, false, true, false, false, true});
        } finally {
            n.revokeRolesForPrincipal("g:groupB");
            n.grantRoles("g:groupA", new HashSet<String>(Arrays.asList("reader")));
            session.save();
        }

        assertEquals("Content served is not the same", guest, getContent(getUrl(path), null, null, "testACLs6"));
        assertEquals("Content served is not the same", root, getContent(getUrl(path), "root", "root1234", "testACLs7"));
        assertEquals("Content served is not the same", userAB, getContent(getUrl(path), "userAB", "password", "testACLs8"));
        assertEquals("Content served is not the same", userBC, getContent(getUrl(path), "userBC", "password", "testACLs9"));
        assertEquals("Content served is not the same", userAC, getContent(getUrl(path), "userAC", "password", "testACLs10"));
    }

    private void checkAcl(String content, boolean[] b) {
        assertEquals(b[0], content.contains("visible for root"));
        assertEquals(b[1], content.contains("visible for users only"));
        assertEquals(b[2], content.contains("visible for userAB"));
        assertEquals(b[3], content.contains("visible for userBC"));
        assertEquals(b[4], content.contains("visible for userAC"));
        assertEquals(b[5], content.contains("visible for groupA"));
        assertEquals(b[6], content.contains("visible for groupB"));
        assertEquals(b[7], content.contains("visible for groupC"));
    }

    @Test
    public void testModuleError() throws Exception {
        String s = getContent(getUrl(SITECONTENT_ROOT_NODE + "/home/error"), "root", "root1234", "error1");
        assertTrue(s.contains("<!-- Module error :"));
        getContent(getUrl(SITECONTENT_ROOT_NODE + "/home/error"), "root", "root1234", "error2");
        // All served from cache
        assertEquals(1, CacheFilterCheckFilter.getData("error2").getCount());
        Thread.sleep(5000);
        // Error should be flushed
        getContent(getUrl(SITECONTENT_ROOT_NODE + "/home/error"), "root", "root1234", "error3");
        assertEquals(2, CacheFilterCheckFilter.getData("error3").getCount());
    }

    @Test
    public void testModuleWait() throws Exception {
        long previousModuleGenerationWaitTime = ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).getModuleGenerationWaitTime();
        try {
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setModuleGenerationWaitTime(1000);
            URL url = getUrl(SITECONTENT_ROOT_NODE + "/home/long");
            HttpThread t1 = new HttpThread(url, "root", "root1234", "testModuleWait1");
            t1.start();
            Thread.sleep(200);

            HttpThread t2 = new HttpThread(url, "root", "root1234", "testModuleWait2");
            t2.start();
            t2.join();

            String content = getContent(url, "root", "root1234", "testModuleWait3");

            t1.join();

            String content1 = getContent(url, "root", "root1234", "testModuleWait4");

            // Long module is left blank
            assertFalse(t2.getResult().contains("Very long to appear"));
            assertTrue(t2.getResult().contains("<h2 class=\"pageTitle\">long</h2>"));
            assertTrue("Second thread did not spend correct time", CacheFilterCheckFilter.getData("testModuleWait2").getTime() > 1000 && CacheFilterCheckFilter.getData("testModuleWait2").getTime() < 1900);

            // Entry is cached without the long module
            assertFalse(content.contains("Very long to appear"));
            assertTrue(content.contains("<h2 class=\"pageTitle\">long</h2>"));
            assertEquals(1, CacheFilterCheckFilter.getData("testModuleWait3").getCount());

            assertTrue(t1.getResult().contains("Very long to appear"));
            assertTrue(t1.getResult().contains("<h2 class=\"pageTitle\">long</h2>"));
            assertTrue("First thread did not spend correct time", CacheFilterCheckFilter.getData("testModuleWait1").getTime() > 2000 && CacheFilterCheckFilter.getData("testModuleWait2").getTime() < 3000);

            // Entry is now cached with the long module
            assertTrue(content1.contains("Very long to appear"));
            assertTrue(content1.contains("<h2 class=\"pageTitle\">long</h2>"));
            assertEquals(1, CacheFilterCheckFilter.getData("testModuleWait4").getCount());
        } finally {
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setModuleGenerationWaitTime(previousModuleGenerationWaitTime);
        }
    }

    @Test
    public void testMaxConcurrent() throws Exception {
        long previousModuleGenerationWaitTime = ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).getModuleGenerationWaitTime();
        int previousMaxModulesToGenerateInParallel = ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).getMaxModulesToGenerateInParallel();
        try {
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setModuleGenerationWaitTime(1000);
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setMaxModulesToGenerateInParallel(1);

            HttpThread t1 = new HttpThread(getUrl(SITECONTENT_ROOT_NODE + "/home/long"), "root", "root1234", "testMaxConcurrent1");
            t1.start();
            Thread.sleep(500);

            HttpThread t2 = new HttpThread(getUrl(SITECONTENT_ROOT_NODE + "/home"), "root", "root1234", "testMaxConcurrent2");
            t2.start();
            t2.join();
            t1.join();

            assertEquals("Incorrect response code for first thread", 200, t1.resultCode);
            assertEquals("Incorrect response code for second thread", 500, t2.resultCode);

            assertTrue(getContent(getUrl(SITECONTENT_ROOT_NODE + "/home"), "root", "root1234", "testMaxConcurrent3").contains("<title>Home</title>"));
        } finally {
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setModuleGenerationWaitTime(previousModuleGenerationWaitTime);
            ((ModuleGeneratorQueue) SpringContextSingleton.getBean("moduleGeneratorQueue")).setMaxModulesToGenerateInParallel(previousMaxModulesToGenerateInParallel);
        }
    }

    @Test
    public void testReferencesFlush() throws Exception {
        URL url = getUrl(SITECONTENT_ROOT_NODE + "/home/references");
        getContent(url, "root", "root1234", null);

        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("live", new Locale("en"));
            JCRNodeWrapper n = session.getNode(SITECONTENT_ROOT_NODE + "/home/references/maincontent/simple-text");
            try {
                n.setProperty("text", "text content updated");
                session.save();
                String newvalue = getContent(url, "root", "root1234", "testReferencesFlush1");
                Matcher m = Pattern.compile("text content updated").matcher(newvalue);
                assertTrue("Value has not been updated", m.find());
                assertTrue("References have not been flushed", m.find());
                assertTrue("References have not been flushed", m.find());
            } finally {
                n.setProperty("text", "text content");
                session.save();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Test
    public void testRandomFlush() throws Exception {
        JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("live", new Locale("en"));
        Query q = session.getWorkspace().getQueryManager().createQuery("select * from [jnt:page] as p where isdescendantnode(p,'" + SITECONTENT_ROOT_NODE + "/home')", Query.JCR_SQL2);
        List<String> paths = new ArrayList<String>();
        NodeIterator nodes = q.execute().getNodes();
        while (nodes.hasNext()) {
            JCRNodeWrapper next = (JCRNodeWrapper) nodes.next();
            if (!next.getName().equals("long") && !next.getName().equals("error")) {
                paths.add(next.getPath());
            }
        }

        List<String> users = Arrays.asList("userAB", "userAC", "userBC");
        Map<String, String> m = new HashMap<String, String>();
        for (String user : users) {
            for (String path : paths) {
                m.put(user + path, getContent(getUrl(path), user, "password", null));
            }
        }

        final Cache cache = ModuleCacheProvider.getInstance().getCache();
        List<String> keysBefore = cache.getKeys();

        Map<String, Object> cacheCopy = new HashMap<String, Object>();
        for (String s : keysBefore) {
            final Element element = cache.get(s);
            if (element != null) {
                cacheCopy.put(s, element.getObjectValue());
            }
        }

        for (int j = 0; j < 10; j++) {
            System.out.println("flush " + j);
            List<String> toFlush = randomizeFlush(keysBefore, 10);
            for (String user : users) {
                for (String path : paths) {
                    System.out.println(user + " - " + path);
                    if (!m.get(user + path).equals(getContent(getUrl(path), user, "password", null))) {
                        fail("Different content for " + user + " , " + path + " when flushing : " + toFlush);
                    }
                    checkCacheContent(cache, cacheCopy, toFlush);
                }
            }
            List<String> keysAfter = cache.getKeys();
            Collections.sort(keysBefore);
            Collections.sort(keysAfter);
            if (!keysBefore.equals(keysAfter)) {
                List<String> onlyInBefore = new ArrayList<String>(keysBefore);
                onlyInBefore.removeAll(keysAfter);
                List<String> onlyInAfter = new ArrayList<String>(keysAfter);
                onlyInAfter.removeAll(keysBefore);
                fail("Key sets are not the same before and after flushing : " + toFlush + "\n Before flushs :" + onlyInBefore + " ,\n After flush : " + onlyInAfter);
            }
            checkCacheContent(cache, cacheCopy, toFlush);
        }
    }

    private void checkCacheContent(Cache cache, Map<String, Object> cacheCopy, List<String> toFlush) {
        List<String> keysNow = cache.getKeys();
        for (String s : keysNow) {
            CacheEntry c1 = ((CacheEntry) cacheCopy.get(s));
            final Element element = cache.get(s);
            if (element != null && c1 != null) {
                CacheEntry c2 = ((CacheEntry) element.getObjectValue());
                assertEquals("Cache fragment different for : " + s + " after flushing : " + toFlush, c1.getObject(), c2.getObject());
                assertEquals("Cache properties different for : " + s + " after flushing : " + toFlush, c1.getExtendedProperties(), c2.getExtendedProperties());
            }
        }
    }

    private List<String> randomizeFlush(List<String> l, int number) {
        Random r = new Random();
        List<String> toFlush = new ArrayList<String>();
        for (int i = 0; i < number; i++) {
            String s = l.get(r.nextInt(l.size()));
            toFlush.add(s);
            ModuleCacheProvider.getInstance().getCache().remove(s);
        }
        return toFlush;
    }

    private String getContent(URL url, String user, String password, String requestId) throws Exception {
        GetMethod method = executeCall(url, user, password, requestId);
        assertEquals("Bad result code", 200, method.getStatusCode());
        return method.getResponseBodyAsString();
    }

    private GetMethod executeCall(URL url, String user, String password, String requestId) throws IOException {
        HttpClient client = new HttpClient();
        client.getParams().setAuthenticationPreemptive(true);

        if (user != null && password != null) {
            Credentials defaultcreds = new UsernamePasswordCredentials(user, password);
            client.getState().setCredentials(new AuthScope(url.getHost(), url.getPort(), AuthScope.ANY_REALM), defaultcreds);
        }

        client.getHostConfiguration().setHost(url.getHost(), url.getPort(), url.getProtocol());

        GetMethod method = new GetMethod(url.toExternalForm());
        if (requestId != null) {
            method.setRequestHeader("request-id", requestId);
        }
        client.executeMethod(method);
        return method;
    }

    private URL getUrl(String path) throws MalformedURLException {
        String baseurl = getBaseServerURL() + Jahia.getContextPath() + "/cms";
        return new URL(baseurl + "/render/live/en" + path + ".html");
    }

    class HttpThread extends Thread {
        private String result;
        private int resultCode;
        private URL url;
        private String user;
        private String password;
        private String requestId;

        HttpThread(URL url, String user, String password, String requestId) {
            this.url = url;
            this.user = user;
            this.password = password;
            this.requestId = requestId;
        }


        String getResult() {
            return result;
        }

        @Override
        public void run() {
            try {
                GetMethod method = executeCall(url, user, password, requestId);
                resultCode = method.getStatusCode();
                result = method.getResponseBodyAsString();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}