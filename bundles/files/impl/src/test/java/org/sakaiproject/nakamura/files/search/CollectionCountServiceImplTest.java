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
package org.sakaiproject.nakamura.files.search;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;

@RunWith(MockitoJUnitRunner.class)
public class CollectionCountServiceImplTest {

  CollectionCountServiceImpl collectionCountService;

  LibraryContentQueryHandler queryHandler;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  SlingHttpServletRequest request;

  @Mock
  Session session;

  @Mock
  AuthorizableManager authzManager;

  @Mock
  User authzBob;

  @Mock
  Group managerGroup;

  @Mock
  ResourceResolver resolver;

  @Mock
  SolrSearchResultSet resultSet;

  @Mock
  Repository repository;

  @Test
  public void testCollectionCount() throws Exception {

    collectionCountService = new CollectionCountServiceImpl();
    queryHandler = new LibraryContentQueryHandler();
    queryHandler.repository = repository;

    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));

    when(request.getResourceResolver()).thenReturn(resolver);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    when(request.getRemoteUser()).thenReturn("bob");

    when(session.getAuthorizableManager()).thenReturn(authzManager);
    String[] bobPrincipals = new String[] {"bogus-manager-group"};
    when(authzBob.getId()).thenReturn("bob");
    when(authzBob.getPrincipals()).thenReturn(bobPrincipals);
    when(authzManager.findAuthorizable(eq("bob"))).thenReturn(authzBob);
    String[] managerPrincipals = new String[0];
    when(managerGroup.getId()).thenReturn("bogus-manager-group");
    when(managerGroup.getPrincipals()).thenReturn(managerPrincipals);
    when(authzManager.findAuthorizable(eq("bogus-manager-group"))).thenReturn(managerGroup);

    when(resultSet.getSize()).thenReturn(10l);

    when(searchServiceFactory.getSearchResultSet(any(SlingHttpServletRequest.class), any(Query.class))).thenReturn(resultSet);

    when(repository.loginAdministrative()).thenReturn(session);
    
    collectionCountService.searchServiceFactory = searchServiceFactory;
    collectionCountService.queryHandler = queryHandler;

    assertEquals(10, collectionCountService.getCollectionCount(request));
  }

}
