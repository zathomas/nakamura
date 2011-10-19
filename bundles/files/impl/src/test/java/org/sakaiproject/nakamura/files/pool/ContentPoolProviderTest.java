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

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

import junit.framework.Assert;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.jackrabbit.SparseMapUserManager;

import java.io.IOException;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;

public class ContentPoolProviderTest {

  @Mock
  private ResourceResolver resourceResolver;
  @Mock
  private Resource resource;
  @Mock
  private JackrabbitSession jackrabbitSession;
  @Mock
  private SparseMapUserManager sparseMapUserManager;
  
  private Repository repository;
  
  private ContentPoolProvider cp;

  public ContentPoolProviderTest() throws ClientPoolException, StorageClientException, org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException, ClassNotFoundException, IOException {
    MockitoAnnotations.initMocks(this);
    cp = new ContentPoolProvider();
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
  }

  @SuppressWarnings(value = { "DLS_DEAD_LOCAL_STORE" }, justification = "Unit testing fail mode")
  @Test
  public void testNonExisting() throws AccessDeniedException,
      UnsupportedRepositoryOperationException, RepositoryException,
      StorageClientException, org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException {
    Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
    Mockito
        .when(resourceResolver.resolve(Mockito.eq("/_p/j/yy/qe/u1/nonexisting")))
        .thenReturn(new NonExistingResource(resourceResolver, "/_p/aa/bb/cc/nonexisting"));
    Mockito.when(resource.getPath()).thenReturn("/_p/AA/BB/CC/DD/testing");
    ResourceMetadata resourceMetadata = new ResourceMetadata();
    Mockito.when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
    
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jackrabbitSession);
    
    Mockito.when(jackrabbitSession.getUserManager()).thenReturn(sparseMapUserManager);
    Session session = repository.loginAdministrative();
    Mockito.when(sparseMapUserManager.getSession()).thenReturn(session);

    Resource result = cp.getResource(resourceResolver, "/");
    Assert.assertNull(result);
    result = cp.getResource(resourceResolver, "/_");
    Assert.assertNull(result);
    try {
      result = cp.getResource(resourceResolver, "/p/nonexisting");
      Assert.fail("Should have refused to create a none existing resource ");
    } catch (SlingException e) {

    }

  }

  @Test
  public void testNonMatching() {
    Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
    Mockito.when(resource.getPath()).thenReturn("/_p/AA/BB/CC/DD/testing");
    ResourceMetadata resourceMetadata = new ResourceMetadata();
    Mockito.when(resource.getResourceMetadata()).thenReturn(resourceMetadata);

    Resource result = cp.getResource(resourceResolver, "/");
    Assert.assertNull(result);
    result = cp.getResource(resourceResolver, "/_");
    Assert.assertNull(result);
    result = cp.getResource(resourceResolver, "/p/");
    Assert.assertNull(result);

  }

  public void testProviderWithId(String selectors, String extra) {
    Resource result = cp.getResource(resourceResolver, "/p/testing" + selectors + extra);
    Assert.assertEquals(null, result);
  }

  @Test
  public void testProviderExt() {
    // A ResourceProvider should only return a resource when the full path to it matches
    // the JCR path.
    resource = null;
    testProviderWithId(".json", "");

  }

  @Test
  public void testProviderExtAndSelector() {

    testProviderWithId(".tidy.json", "");

  }

  @Test
  public void testProviderExtAndSelectorAndExtra() {
    testProviderWithId(".tidy.json", "/some/other/path/with.dots.in.it.pdf");
  }

}
