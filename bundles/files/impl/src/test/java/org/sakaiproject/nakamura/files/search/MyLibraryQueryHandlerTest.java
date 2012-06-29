/*
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
package org.sakaiproject.nakamura.files.search;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.files.search.MyLibraryQueryHandler.REQUEST_PARAMS;
import org.sakaiproject.nakamura.files.search.MyLibraryQueryHandler.TEMPLATE_PROPS;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class MyLibraryQueryHandlerTest {

  @Mock
  Repository repository;
  
  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Test
  public void testAnonUser() {
    MyLibraryQueryHandler handler = new MyLibraryQueryHandler(searchServiceFactory, repository);
    Map<String, String> params = new HashMap<String, String>();
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    mockRequestForUserId(request, User.ANON_USER);
    handler.loadUserProperties(request, params);
    Assert.assertEquals("resourceType:sakai/pooled-content", params.get(TEMPLATE_PROPS._q.toString()));
  }
  
  @Test
  public void testAnonWithSearch() {
    MyLibraryQueryHandler handler = new MyLibraryQueryHandler(searchServiceFactory, repository);
    Map<String, String> params = new HashMap<String, String>();
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    
    mockRequestForUserId(request, User.ANON_USER);
    mockQuery(params, "blah-query");
    mockMime(params, "sakai/x-collection");
    
    handler.loadUserProperties(request, params);
    
    String q = params.get(TEMPLATE_PROPS._q.toString());
    Assert.assertEquals("mimeType:sakai/x\\-collection AND general:blah\\-query", q);
  }
  
  @Test
  public void testUser() throws AccessDeniedException, StorageClientException {
    MyLibraryQueryHandler handler = new MyLibraryQueryHandler(searchServiceFactory, repository);
    Map<String, String> params = new HashMap<String, String>();
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);

    mockRequestForUserId(request, "mrvisser");
    mockQuery(params, "blah-query");
    mockMime(params, "sakai/x-collection");
    mockRequestForLevels(request, "1");
    
    ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
    Session session = Mockito.mock(Session.class);
    javax.jcr.Session jcrSession = Mockito.mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    AuthorizableManager am = Mockito.mock(AuthorizableManager.class);
    Authorizable mrvisser = Mockito.mock(Authorizable.class);
    Authorizable mrvisserGroup = Mockito.mock(Authorizable.class);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    Mockito.when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    Mockito.when(session.getAuthorizableManager()).thenReturn(am);
    Mockito.when(am.findAuthorizable("mrvisser")).thenReturn(mrvisser);
    Mockito.when(am.findAuthorizable("mrvisser-group")).thenReturn(mrvisserGroup);
    Mockito.when(mrvisser.getPrincipals()).thenReturn(new String[] { "mrvisser-group", "everyone" });
    Mockito.when(mrvisserGroup.getPrincipals()).thenReturn(new String[0]);
    
    handler.loadUserProperties(request, params);
    
    String q = params.get(TEMPLATE_PROPS._q.toString());
    String _all = params.get(TEMPLATE_PROPS._all.toString());
    String _au = params.get(TEMPLATE_PROPS._au.toString());
    Assert.assertEquals("mimeType:sakai/x\\-collection AND general:blah\\-query", q);
    Assert.assertEquals("mrvisser OR mrvisser\\-group", _all);
    Assert.assertEquals("mrvisser OR mrvisser\\-group", _au);
  }
  
  private void mockRequestForUserId(SlingHttpServletRequest request, String userId) {
    Mockito.when(request.getRemoteUser()).thenReturn(userId);
  }
  
  private void mockQuery(Map<String, String> params, String query) {
    params.put(REQUEST_PARAMS.q.toString(), query);
  }
  
  private void mockMime(Map<String, String> params, String mime) {
    params.put(REQUEST_PARAMS.mimetype.toString(), mime);
  }
  
  private void mockRequestForLevels(SlingHttpServletRequest request, String levels) {
    Mockito.when(request.getParameter(REQUEST_PARAMS.levels.toString())).thenReturn(levels);
  }
}
