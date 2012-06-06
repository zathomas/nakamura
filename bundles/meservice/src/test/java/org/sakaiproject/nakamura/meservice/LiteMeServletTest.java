/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.meservice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.util.LocaleUtils;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteMeServletTest {
  LiteMeServlet meServlet;
  @Mock
  LiteMessagingService messagingService;

  @Mock
  ConnectionManager connectionManager;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  BasicUserInfoService basicUserInfoService;

  @Mock
  DynamicContentResponseCache dynamicContentResponseCache;

  @Mock
  LocaleUtils localeUtils;

  @Before
  public void setUp() {
    meServlet = new LiteMeServlet();
    meServlet.messagingService = messagingService;
    meServlet.connectionManager = connectionManager;
    meServlet.searchServiceFactory = searchServiceFactory;
    meServlet.basicUserInfoService = basicUserInfoService;
    meServlet.dynamicContentResponseCache = dynamicContentResponseCache;
    meServlet.localeUtils = localeUtils;
  }

  @Test
  public void testNothingForNow() {
    // I assume someone will add test coverage in the future; otherwise I would just
    // remove the entire class.
  }

  private void assertJSONKeys (Set<String> expectedKeys, JSONObject json) throws Exception {
    Iterator<String> keyIt = json.keys();
    while(keyIt.hasNext()) {
      String key = keyIt.next();
      assertTrue(expectedKeys.contains(key));
      expectedKeys.remove(key);
    }

    assertTrue(expectedKeys.isEmpty());
  }

  @Test
  public void testValidateMeFeedContent() throws Exception {

    SlingHttpServletRequest request = mock (SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock (SlingHttpServletResponse.class);
    StringWriter writer = new StringWriter();
    PrintWriter wrappedWriter = new PrintWriter(writer);

    when(dynamicContentResponseCache.send304WhenClientHasFreshETag(anyString(), any(HttpServletRequest.class),
       any(HttpServletResponse.class))).thenReturn(false);

    Map<String, Object> localeProps = new HashMap<String, Object>();

    when(localeUtils.getLocale(any(Map.class))).thenReturn(Locale.UK);
    when(localeUtils.getTimeZone(any(Map.class))).thenReturn(TimeZone.getTimeZone("GMT"));

    when(localeUtils.getOffset(any(TimeZone.class))).thenReturn(0);

    when(response.getWriter()).thenReturn(wrappedWriter);

    when(request.getParameter(eq("uid"))).thenReturn("testUser");

    Repository repository = new BaseMemoryRepository().getRepository();
    Session session = repository.loginAdministrative();
    AuthorizableManager am = session.getAuthorizableManager();

    Map<String, Object> userProps = new HashMap<String, Object>();

    userProps.put("firstName", "first");
    userProps.put("lastName", "last");
    userProps.put("email", "email");
    userProps.put("homePath", "path");

    Map<String, Object> counts = new HashMap<String, Object> ();

    counts.put(UserConstants.CONTENT_ITEMS_PROP, 100);
    counts.put(UserConstants.CONTACTS_PROP, 50);
    counts.put(UserConstants.GROUP_MEMBERSHIPS_PROP, 5);

    userProps.put(UserConstants.COUNTS_PROP, counts);

    HashMap<String, Object> picInfo = new HashMap<String, Object>();

    picInfo.put("name", "256x256_tmp1336495195251.jpg");
    picInfo.put("original", "tmp1336495195251.jpg");
    picInfo.put("selectedx1", 30);
    picInfo.put("selectedy1", 108);
    picInfo.put("selectedx2", 94);
    picInfo.put("selectedy2", 172);

    userProps.put("picture", picInfo);
    am.createUser("testUser", "testUser", "password", userProps);

    ResourceResolver resolver = mock(ResourceResolver.class);
    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));

    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    when(basicUserInfoService.getProperties(any(Authorizable.class))).thenReturn(userProps);

    when(messagingService.getFullPathToStore(anyString(), any(Session.class))).thenReturn("bogusPath");

    SolrSearchResultSet msgSet = mock(SolrSearchResultSet.class);
    when(msgSet.getSize()).thenReturn((long)5);
    when(searchServiceFactory.getSearchResultSet(any(SlingHttpServletRequest.class), any(Query.class), anyBoolean())).thenReturn(
       msgSet);

    meServlet.doGet(request, response);

    writer.getBuffer();

    JSONObject json = new JSONObject(writer.getBuffer().toString());

    HashSet<String> expectedKeys = new HashSet<String>();

    expectedKeys.add("homePath");
    expectedKeys.add("userid");
    expectedKeys.add("profile");
    expectedKeys.add("locale");
    expectedKeys.add("counts");

    assertJSONKeys(expectedKeys, json);

    JSONObject profile = json.getJSONObject("profile");

    expectedKeys = new HashSet<String>();

    expectedKeys.add("picture");
    expectedKeys.add("firstName");
    expectedKeys.add("lastName");
    expectedKeys.add("email");

    assertJSONKeys(expectedKeys, profile);

    JSONObject picture = profile.getJSONObject("picture");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("name");
    expectedKeys.add("original");
    expectedKeys.add("selectedx1");
    expectedKeys.add("selectedy1");
    expectedKeys.add("selectedx2");
    expectedKeys.add("selectedy2");

    assertJSONKeys(expectedKeys, picture);

    JSONObject locale = json.getJSONObject("locale");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("country");
    expectedKeys.add("displayCountry");
    expectedKeys.add("displayLanguage");
    expectedKeys.add("displayName");
    expectedKeys.add("ISO3Country");
    expectedKeys.add("ISO3Language");
    expectedKeys.add("language");
    expectedKeys.add("timezone");

    assertJSONKeys(expectedKeys, locale);

    JSONObject timezone = locale.getJSONObject("timezone");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("name");
    expectedKeys.add("GMT");

    assertJSONKeys(expectedKeys, timezone);

    JSONObject countsJSON = json.getJSONObject("counts");

    expectedKeys = new HashSet<String>();
    expectedKeys.add("content");
    expectedKeys.add("contacts");
    expectedKeys.add("memberships");
    //expectedKeys.add("collections");
    expectedKeys.add("unreadmessages");

    assertJSONKeys(expectedKeys, countsJSON);

  }

}
