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
package org.sakaiproject.nakamura.jaxrs;

import junit.framework.Assert;

import org.apache.sling.auth.core.AuthenticationSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.http.HttpContext;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
@RunWith(value=MockitoJUnitRunner.class)
public class ThreadLocalNakamuraWebContextTest {

  /**
   * Verify an IllegalStateException is thrown when there is an attempt to access the
   * current user id from an uninitialized web context.
   */
  @Test(expected=IllegalStateException.class)
  public void testUnitializedCurrentUserId() {
    (new ThreadLocalNakamuraWebContext()).getCurrentUserId();
  }
  
  /**
   * Verify an IllegalStateException is thrown when there is an attempt to access the
   * current session from an uninitialized web context.
   */
  @Test(expected=IllegalStateException.class)
  public void testUnitializedCurrentSession() {
    (new ThreadLocalNakamuraWebContext()).getCurrentSession();
  }
  
  /**
   * Verify there is a lazy anonymous login to the sparse repository when the getCurrentSession()
   * method is invoked from an anonymous request.
   * @throws Exception
   */
  @Test
  public void testInitializeAnonymous() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    
    Assert.assertEquals(User.ANON_USER, webContext.getCurrentUserId());
    
    // verify we have not created a session yet
    verifyNoLogin(repository);
    
    // verify that we attempted a sling authentication
    Mockito.verify(authenticationSupport, Mockito.times(1)).handleSecurity(request, response);
    
    // verify that there was an anonymous login to the repository after calling getCurrentSession
    webContext.getCurrentSession();
    Mockito.verify(repository, Mockito.times(1)).login();
    
    webContext.destroyWebContext();
  }
  
  /**
   * Verify there is a lazy, user-bound login to the sparse repository when the getCurrentSession()
   * method is invoked from a pre-authenticated (e.g., sling) request.
   * @throws Exception
   */
  @Test
  public void testInitializePreauthenticated() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    
    Mockito.when(request.getRemoteUser()).thenReturn("branden");
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    
    Assert.assertEquals("branden", webContext.getCurrentUserId());
    
    // verify we have not created a session yet
    verifyNoLogin(repository);
    
    // verify no sling authentication was attempted because we were pre-authenticated
    Mockito.verify(authenticationSupport, Mockito.never()).handleSecurity(Mockito.any(HttpServletRequest.class), Mockito.any(HttpServletResponse.class));
    
    // verify that there is a login for "branden"
    webContext.getCurrentSession();
    Mockito.verify(repository, Mockito.times(1)).loginAdministrative("branden");
    
    webContext.destroyWebContext();
  }
  
  /**
   * Verify there is a lazy, user-bound login to the repository when the getCurrentSession()
   * method is invoked from a request that has sling login credentials. The corner-case here
   * is that the request did not previously undergo sling authentication.
   * @throws Exception
   */
  @Test
  public void testInitializePerformAuthentication() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    
    Mockito.when(authenticationSupport.handleSecurity(request, response)).thenReturn(true);
    Mockito.when(request.getAttribute(HttpContext.REMOTE_USER)).thenReturn("branden1");
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    
    Assert.assertEquals("branden1", webContext.getCurrentUserId());
    
    // verify we have not created a session yet
    verifyNoLogin(repository);
    
    // verify that there is a login for "branden1"
    webContext.getCurrentSession();
    Mockito.verify(repository, Mockito.times(1)).loginAdministrative("branden1");
    
    webContext.destroyWebContext();
  }
  
  /**
   * Verify that after a web context is destroyed, accesses to its information will result in an
   * IllegalStateException.
   * @throws Exception
   */
  @Test(expected=IllegalStateException.class)
  public void testAccessDestroyedContext() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    
    try {
      Assert.assertEquals(User.ANON_USER, webContext.getCurrentUserId());
    } catch (IllegalStateException e) {
      Assert.fail("Did not expect webContext to be uninitialized");
    }
    
    webContext.destroyWebContext();
    
    // trigger IllegalStateException
    webContext.getCurrentUserId();
  }
  
  /**
   * Verify that a web context is successfully destroyed when there was never any sparse session
   * created for the context.
   * @throws Exception
   */
  @Test
  public void testDestroyNoSession() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    Session session = Mockito.mock(Session.class);
    
    Mockito.when(repository.login()).thenReturn(session);
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    webContext.getCurrentUserId();
    webContext.destroyWebContext();
    
    // verify there was no session interaction
    Mockito.verify(repository, Mockito.never()).login();
    Mockito.verify(session, Mockito.never()).logout();
    
    // verify destruction was successful
    boolean wasSuccessful = false;
    try {
      webContext.getCurrentUserId();
    } catch (IllegalStateException e) {
      wasSuccessful = true;
    }
    
    Assert.assertTrue("Expected an IllegalStateException after context was destroyed.", wasSuccessful);
  }
  
  /**
   * Verify that a context-bound sparse session is properly released after a session-enabled web context
   * has been destroyed.
   * @throws Exception
   */
  @Test
  public void testDestroySessionReleased() throws Exception {
    Repository repository = Mockito.mock(Repository.class);
    AuthenticationSupport authenticationSupport = Mockito.mock(AuthenticationSupport.class);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    Session session = Mockito.mock(Session.class);
    
    Mockito.when(repository.login()).thenReturn(session);
    
    ThreadLocalNakamuraWebContext webContext = new ThreadLocalNakamuraWebContext(repository, authenticationSupport);
    webContext.initWebContext(request, response);
    webContext.getCurrentSession();
    
    webContext.destroyWebContext();
    
    // verify the session was logged out
    Mockito.verify(session, Mockito.times(1)).logout();
  }
  
  /**
   * Verify no session was ever authenticated from the given repository.
   * @param repository
   * @throws Exception
   */
  private void verifyNoLogin(Repository repository) throws Exception {
    Mockito.verify(repository, Mockito.never()).login();
    Mockito.verify(repository, Mockito.never()).login(Mockito.anyString(), Mockito.anyString());
    Mockito.verify(repository, Mockito.never()).loginAdministrative();
    Mockito.verify(repository, Mockito.never()).loginAdministrative(Mockito.anyString());
  }
}
