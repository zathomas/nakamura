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

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;
import static org.apache.jackrabbit.JcrConstants.NT_RESOURCE;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_NODE;

import org.sakaiproject.nakamura.api.files.FileUploadHandler;

import com.google.common.collect.ImmutableMap;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.kahadb.util.ByteArrayInputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.lite.jackrabbit.SparseMapUserManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

public class CreateContentPoolServletTest {

  @Mock
  private SlingRepository slingRepository;
  @Mock
  private JackrabbitSession adminSession;
  @Mock
  private JackrabbitSession jcrSesson;
  @Mock
  private UserManager userManager;
  @Mock
  private PrincipalManager principalManager;
  @Mock
  private Node parentNode;
  @Mock
  private Node resourceNode;
  @Mock
  private Node membersNode;
  @Mock
  private Node memberNode;
  @Mock
  private AccessControlManager accessControlManager;
  @Mock
  private Privilege allPrivilege;

  private AccessControlList accessControlList;
  @Mock
  private ValueFactory valueFactory;
  @Mock
  private Binary binary;
  @Mock
  private ClusterTrackingService clusterTrackingService;
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private SlingHttpServletResponse response;
  @Mock
  private RequestParameterMap requestParameterMap;
  @Mock
  private RequestParameter requestParameter1;
  @Mock
  private RequestParameter requestParameter2;
  @Mock
  private RequestParameter requestParameterNot;
  @Mock
  private RequestPathInfo requestPathInfo;
  @Mock
  private ResourceResolver resourceResolver;
  @Mock
  private SparseMapUserManager sparseMapUserManager;
  @Mock
  private EventAdmin eventAdmin;
  private RepositoryImpl repository;

  CreateContentPoolServlet cp;


  @Before
  public void setUp() throws Exception {
    when(slingRepository.loginAdministrative(null)).thenReturn(adminSession);
    
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSesson);
    Session session = repository.loginAdministrative("ieb");
    when(jcrSesson.getUserManager()).thenReturn(sparseMapUserManager);
    when(sparseMapUserManager.getSession()).thenReturn(session);
    when(clusterTrackingService.getClusterUniqueId()).thenReturn(String.valueOf(System.currentTimeMillis()));

    when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
    when(requestPathInfo.getExtension()).thenReturn(null);

    when(adminSession.getUserManager()).thenReturn(userManager);
    when(adminSession.getPrincipalManager()).thenReturn(principalManager);
    when(adminSession.getAccessControlManager()).thenReturn(accessControlManager);
    when(request.getRemoteUser()).thenReturn("ieb");

    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    Map<String, RequestParameter[]> map = new HashMap<String, RequestParameter[]>();

    RequestParameter[] requestParameters = new RequestParameter[] {
      requestParameter1, requestParameterNot, requestParameter2
    };
    map.put("files", requestParameters);

    when(requestParameterMap.entrySet()).thenReturn(map.entrySet());

    when(requestParameter1.isFormField()).thenReturn(false);
    when(requestParameter1.getContentType()).thenReturn("application/pdf");
    when(requestParameter1.getFileName()).thenReturn("testfilename.pdf");
    InputStream input1 = new ByteArrayInputStream(new byte[10]);
    when(requestParameter1.getInputStream()).thenReturn(input1);

    when(requestParameter2.isFormField()).thenReturn(false);
    when(requestParameter2.getContentType()).thenReturn("text/html");
    when(requestParameter2.getFileName()).thenReturn("index.html");
    InputStream input2 = new ByteArrayInputStream(new byte[10]);
    when(requestParameter2.getInputStream()).thenReturn(input2);

    when(requestParameterNot.isFormField()).thenReturn(true);

    // deep create
    // when(adminSession.nodeExists(CreateContentPoolServlet.POOLED_CONTENT_ROOT)).thenReturn(true);
    when(adminSession.itemExists(Mockito.anyString())).thenReturn(true);

    // Because the pooled content paths are generated by a private method,
    // mocking the repository is more problematic than usual. The test
    // therefore relies on inside knowledge that there should be three
    // calls to deepGetOrCreateNode for each file: one for the pooled content
    // node, one for its members node, and one for the manager node.
    when(adminSession.getItem(Mockito.anyString())).thenAnswer(new Answer<Item>() {
        public Item answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          String path = (String) args[0];
          if (path.endsWith(POOLED_CONTENT_MEMBERS_NODE)) {
            return membersNode;
          } else if (path.endsWith("ieb")) {
            return memberNode;
          } else {
            return parentNode;
          }
        }
      });

    when(parentNode.addNode(JCR_CONTENT, NT_RESOURCE)).thenReturn(resourceNode);
    when(adminSession.getValueFactory()).thenReturn(valueFactory);
    when(valueFactory.createBinary(Mockito.any(InputStream.class))).thenReturn(binary);

    // access control utils
    accessControlList = new AccessControlList() {

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
    when(accessControlManager.privilegeFromName(Mockito.anyString())).thenReturn(
                                                                                 allPrivilege);
    AccessControlPolicy[] acp = new AccessControlPolicy[] { accessControlList };
    when(accessControlManager.getPolicies(Mockito.anyString())).thenReturn(acp);

    cp = new CreateContentPoolServlet();
    cp.eventAdmin = eventAdmin;
    cp.clusterTrackingService = clusterTrackingService;
    cp.sparseRepository = repository;
  }


  public CreateContentPoolServletTest() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    MockitoAnnotations.initMocks(this);
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    Session session = repository.loginAdministrative();
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    authorizableManager.createUser("ieb", "Ian Boston", "test", ImmutableMap.of("x",(Object)"y"));
    org.sakaiproject.nakamura.api.lite.authorizable.Authorizable authorizable = authorizableManager.findAuthorizable("ieb");
    System.err.println("Got ieb as "+authorizable);
    session.logout();

  }

  @Test
  public void testCreate() throws Exception {

    StringWriter stringWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

    cp.doPost(request, response);

    // Verify that we created all the nodes.
    JSONObject jsonObject = new JSONObject(stringWriter.toString());
    Assert.assertNotNull(jsonObject.getString("testfilename.pdf"));
    Assert.assertNotNull(jsonObject.getString("index.html"));
    Assert.assertEquals(2, jsonObject.length());
  }


  @Test
  public void testFileUploadHandler() throws Exception {

    StringWriter stringWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

    // Exceptions in a handler should be caught and logged, but shouldn't stop
    // other handlers from running.
    cp.bindFileUploadHandler(new FileUploadHandler() {
        public void handleFile(Map<String, Object> results, String poolId, InputStream fileInputStream,
                               String userId, boolean isNew) throws IOException {
          throw new RuntimeException("Handler failed!");
        }
      });

    final ArrayList notifiedFiles = new ArrayList();
    cp.bindFileUploadHandler(new FileUploadHandler() {
        public void handleFile(Map<String, Object> results, String poolId, InputStream fileInputStream,
                               String userId, boolean isNew) throws IOException {
          notifiedFiles.add(poolId);
        }
      });

    cp.doPost(request, response);
    Assert.assertTrue(notifiedFiles.size () == 2);
  }

}
