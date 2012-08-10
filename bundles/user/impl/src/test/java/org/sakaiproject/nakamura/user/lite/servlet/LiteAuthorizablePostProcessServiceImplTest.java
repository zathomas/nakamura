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
package org.sakaiproject.nakamura.user.lite.servlet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessor;
import org.sakaiproject.nakamura.user.postprocessors.DefaultPostProcessor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KERN-3089 Please keep in mind that at least rSmart depends on this functionality for
 * our extensions around user creation.
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteAuthorizablePostProcessServiceImplTest {
  LiteAuthorizablePostProcessServiceImpl liteAuthorizablePostProcessServiceImpl;

  @Mock
  LiteAuthorizablePostProcessor service1;
  @Mock
  LiteAuthorizablePostProcessor service2;
  @Mock
  Authorizable authorizable;
  @Mock
  Group group;
  @Mock
  Session session;
  @Mock
  DefaultPostProcessor defaultPostProcessor;

  @Before
  public void setUp() throws Exception {
    liteAuthorizablePostProcessServiceImpl = new LiteAuthorizablePostProcessServiceImpl();
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put(Constants.SERVICE_ID, new Long(1L));
    liteAuthorizablePostProcessServiceImpl.bindAuthorizablePostProcessor(service1, map);
    map.put(Constants.SERVICE_ID, new Long(2L));
    liteAuthorizablePostProcessServiceImpl.bindAuthorizablePostProcessor(service2, map);
    liteAuthorizablePostProcessServiceImpl.defaultPostProcessor = defaultPostProcessor;
  }

  /**
   * Ensure we can support multiple bindings of LiteAuthorizablePostProcessor.
   * {@link LiteAuthorizablePostProcessServiceImpl#bindAuthorizablePostProcessor(LiteAuthorizablePostProcessor, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testBindAuthorizablePostProcessor() throws Exception {
    final List<LiteAuthorizablePostProcessor> orderedServices = Arrays
        .asList(liteAuthorizablePostProcessServiceImpl.orderedServices);
    assertTrue("rSmart depends on this", orderedServices.contains(service1));
    assertTrue("rSmart depends on this", orderedServices.contains(service2));
  }

  /**
   * Ensure we can support multiple bindings of LiteAuthorizablePostProcessor.
   * {@link LiteAuthorizablePostProcessServiceImpl#unbindAuthorizablePostProcessor(LiteAuthorizablePostProcessor, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testUnbindAuthorizablePostProcessor() throws Exception {
    liteAuthorizablePostProcessServiceImpl
        .unbindAuthorizablePostProcessor(service1, null);

    final List<LiteAuthorizablePostProcessor> orderedServices = Arrays
        .asList(liteAuthorizablePostProcessServiceImpl.orderedServices);
    assertFalse("rSmart depends on this", orderedServices.contains(service1));
    assertTrue("rSmart depends on this", orderedServices.contains(service2));
  }

  /**
   * {@link LiteAuthorizablePostProcessServiceImpl#process(Authorizable, Session, ModificationType, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testProcess() throws Exception {
    final Map<String, Object[]> parameters = new HashMap<String, Object[]>();
    liteAuthorizablePostProcessServiceImpl.process(authorizable, session,
        ModificationType.CREATE, parameters);
    verify(defaultPostProcessor).process(eq(authorizable), eq(session),
        any(Modification.class), eq(parameters));
    verify(service1).process(eq(authorizable), eq(session), any(Modification.class),
        eq(parameters));
    verify(service2).process(eq(authorizable), eq(session), any(Modification.class),
        eq(parameters));
  }

  /**
   * {@link LiteAuthorizablePostProcessServiceImpl#process(Authorizable, Session, ModificationType, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testProcessGroup() throws Exception {
    final Map<String, Object[]> parameters = new HashMap<String, Object[]>();
    liteAuthorizablePostProcessServiceImpl.process(group, session,
        ModificationType.CREATE, parameters);
    verify(defaultPostProcessor).process(eq(group), eq(session), any(Modification.class),
        eq(parameters));
    verify(service1).process(eq(group), eq(session), any(Modification.class),
        eq(parameters));
    verify(service2).process(eq(group), eq(session), any(Modification.class),
        eq(parameters));
  }

  /**
   * {@link LiteAuthorizablePostProcessServiceImpl#process(Authorizable, Session, ModificationType, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testProcessDelete() throws Exception {
    final Map<String, Object[]> parameters = new HashMap<String, Object[]>();
    liteAuthorizablePostProcessServiceImpl.process(authorizable, session,
        ModificationType.DELETE, parameters);
    verify(defaultPostProcessor).process(eq(authorizable), eq(session),
        any(Modification.class), eq(parameters));
    verify(service1).process(eq(authorizable), eq(session), any(Modification.class),
        eq(parameters));
    verify(service2).process(eq(authorizable), eq(session), any(Modification.class),
        eq(parameters));
  }

}
