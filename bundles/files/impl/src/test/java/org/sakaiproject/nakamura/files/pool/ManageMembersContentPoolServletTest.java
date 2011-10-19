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
package org.sakaiproject.nakamura.files.pool;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_MANAGER;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_VIEWER;

import com.google.common.collect.ImmutableMap;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.testing.jcr.MockNode;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

/**
 *
 */
public class ManageMembersContentPoolServletTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SlingHttpServletRequest request;
  @Mock
  private SlingHttpServletResponse response;
  @Mock
  private Resource resource;
  @Mock
  private Node fileNode;
  @Mock
  private JackrabbitSession session;
  @Mock
  private PrincipalManager principalManager;
  @Mock
  private AccessControlManager acm;
  @Mock
  private JackrabbitSession adminSession;
  @Mock
  private Privilege allPrivilege;
  @Mock
  private UserManager userManager;
  @Mock
  private ResourceResolver resourceResolver;
  @Mock
  private ProfileService profileService;
  @Mock
  private SolrServerService solrSearchService;
  @Mock
  private SolrServer solrServer;
  @Mock
  QueryResponse queryResponse;
  @Mock
  SolrQuery query;
  @Mock
  SolrDocumentList results;
  @Mock
  Iterator iterator;

  private ManageMembersContentPoolServlet servlet;
  private PrintWriter printWriter;
  private StringWriter stringWriter;
  private AccessControlList acl;
  private BaseMemoryRepository baseMemoryRepository;
  private Repository sparseRepository;
  private org.sakaiproject.nakamura.api.lite.Session sparseSession;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    baseMemoryRepository = new BaseMemoryRepository();
    sparseRepository = baseMemoryRepository.getRepository();
    sparseSession = sparseRepository.loginAdministrative();
    sparseSession.getAuthorizableManager().createUser("ieb", "Ian Boston", "test",
        ImmutableMap.of("x", (Object) "y"));
    sparseSession.getContentManager().update(
        new Content("pooled-content-id", ImmutableMap.of("x", (Object) "y",
            POOLED_CONTENT_USER_MANAGER, new String[]{"alice","ieb"}, POOLED_CONTENT_USER_VIEWER,
            new String[]{"bob","mark","john"})));
    sparseSession.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        "pooled-content-id",
        new AclModification[] { new AclModification(AclModification.grantKey("ieb"),
            Permissions.CAN_MANAGE.getPermission(), Operation.OP_REPLACE) });
    sparseSession.logout();
    sparseSession = sparseRepository.loginAdministrative("ieb");
    servlet = new ManageMembersContentPoolServlet();
    // servlet.slingRepository = slingRepository;
    servlet.solrSearchService = solrSearchService;
    when(solrSearchService.getServer()).thenReturn(solrServer);
    when(solrServer.query((SolrParams) Mockito.any())).thenReturn(queryResponse);
    when(queryResponse.getResults()).thenReturn(results);
    when(results.iterator()).thenReturn(iterator);
    // TODO With this, we are testing the internals of the ProfileServiceImpl
    // class as well as the internals of the MeServlet class. Mocking it would
    // reduce the cost of test maintenance.
    servlet.profileService = profileService;
    when(resource.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(Session.class)).thenReturn(session);

    when(resource.adaptTo(org.sakaiproject.nakamura.api.lite.Session.class)).thenReturn(
        sparseSession);
    Content contentNode = sparseSession.getContentManager().get("pooled-content-id");
    Assert.assertNotNull(contentNode);
    when(resource.adaptTo(Content.class)).thenReturn(contentNode);
    when(resource.adaptTo(ContentManager.class)).thenReturn(
        sparseSession.getContentManager());
    // Mock the request and the filenode.
    when(request.getResource()).thenReturn(resource);

    when(resource.getPath()).thenReturn("/path/to/pooled/content/file");
    
    // TODO: Port profile Nodes to Sparse at some point
    when(resource.adaptTo(Node.class)).thenReturn(fileNode);
    when(fileNode.getSession()).thenReturn(session, adminSession);
    when(fileNode.getPath()).thenReturn("/path/to/pooled/content/file");
    when(session.getPrincipalManager()).thenReturn(principalManager);
    when(session.getAccessControlManager()).thenReturn(acm);
    when(session.getUserManager()).thenReturn(userManager);
    Node rootNode = mock(Node.class);
    when(session.getRootNode()).thenReturn(rootNode);
    when(rootNode.hasNode("_user/a/al/alice/public/authprofile")).thenReturn(true);
    when(session.getNode("/_user/a/al/alice/public/authprofile")).thenReturn(
        new MockNode("/_user/a/al/alice/public/authprofile"));
    when(rootNode.hasNode("_user/b/bo/bob/public/authprofile")).thenReturn(true);
    when(session.getNode("/_user/b/bo/bob/public/authprofile")).thenReturn(
        new MockNode("/_user/b/bo/bob/public/authprofile"));
    
    
    
    // Make sure we can write to something.
    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);

    // Mock the users for this file.

  }

  @Test
  public void testGetMembers() throws Exception {
    if ( true ) {
      return;
    }
    // TODO: FIX THIS TEST.
    when(request.getRequestPathInfo().getSelectors()).thenReturn(new String[0]);
    servlet.doGet(request, response);
    printWriter.flush();

    // If all went right, we should have 1 manager Alice and one viewer bob.
    JSONObject json = new JSONObject(stringWriter.toString());
    assertEquals(2, json.getJSONArray("managers").length());
    assertEquals(3, json.getJSONArray("viewers").length());
    assertEquals("alice", json.getJSONArray("managers").getJSONObject(0).get("userid"));
    assertEquals("bob", json.getJSONArray("viewers").getJSONObject(0).get("userid"));
  }

  @Test
  public void testAddManager() throws Exception {
    // We want to add a manager called charly.
    // Alice should be ignored because she is already in there.
    when(request.getParameterValues(":manager")).thenReturn(
        new String[] { "charly", "alice" });

    ItemBasedPrincipal charly = mock(ItemBasedPrincipal.class);
    when(charly.getName()).thenReturn("charly");
    when(principalManager.getPrincipal("charly")).thenReturn(charly);

    // To be able to make someone a manager, we have to be a manager ourselves.
    when(acm.hasPrivileges(fileNode.getPath(), new Privilege[] { allPrivilege }))
        .thenReturn(true);

    // Because we're denying privileges, we intercept the addEntry on the acl object.
    acl = new AccessControlList() {

      // Add an "addEntry" method so AccessControlUtil can execute something.
      // This method doesn't do anything useful.
      @SuppressWarnings("unused")
      public boolean addEntry(Principal principal, Privilege[] privileges, boolean isAllow)
          throws AccessControlException {
        return true;
      }

      public void removeAccessControlEntry(AccessControlEntry ace)
          throws AccessControlException, RepositoryException {
      }

      public AccessControlEntry[] getAccessControlEntries() throws RepositoryException {
        return new AccessControlEntry[0];
      }

      public boolean addAccessControlEntry(Principal principal, Privilege[] privileges)
          throws AccessControlException, RepositoryException {
        return false;
      }
    };
    AccessControlPolicy[] acp = new AccessControlPolicy[] { acl };
    when(acm.getPolicies(Mockito.anyString())).thenReturn(acp);

    when(adminSession.hasPendingChanges()).thenReturn(true);

    servlet.doPost(request, response);

    // Verify we saved everything and then properly logged out.
    verify(response).setStatus(200);
  }

  @Test
  public void testDeleteManager() throws Exception {
    // We want to delete alice as a manager
    when(request.getParameterValues(":manager@Delete")).thenReturn(
        new String[] { "alice" });

    Principal alice = mock(Principal.class);
    when(alice.getName()).thenReturn("alice");
    when(principalManager.getPrincipal("alice")).thenReturn(alice);

    // To be able to make someone a manager, we have to be a manager ourselves.
    when(acm.hasPrivileges(fileNode.getPath(), new Privilege[] { allPrivilege }))
        .thenReturn(true);

    // Because we're denying privileges, we intercept the addEntry on the acl object.
    acl = new AccessControlList() {

      // Add an "addEntry" method so AccessControlUtil can execute something.
      // This method doesn't do anything useful.
      @SuppressWarnings("unused")
      public boolean addEntry(Principal principal, Privilege[] privileges, boolean isAllow)
          throws AccessControlException {
        return true;
      }

      public void removeAccessControlEntry(AccessControlEntry ace)
          throws AccessControlException, RepositoryException {
      }

      public AccessControlEntry[] getAccessControlEntries() throws RepositoryException {
        return new AccessControlEntry[0];
      }

      public boolean addAccessControlEntry(Principal principal, Privilege[] privileges)
          throws AccessControlException, RepositoryException {
        return false;
      }
    };
    AccessControlPolicy[] acp = new AccessControlPolicy[] { acl };
    when(acm.getPolicies(Mockito.anyString())).thenReturn(acp);

    when(adminSession.hasPendingChanges()).thenReturn(true);

    servlet.doPost(request, response);

    // Verify we saved everything and then properly logged out.
    verify(response).setStatus(200);
  }
}
