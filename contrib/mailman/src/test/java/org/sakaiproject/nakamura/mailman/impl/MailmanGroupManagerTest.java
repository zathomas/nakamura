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
package org.sakaiproject.nakamura.mailman.impl;

import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.mailman.MailmanManager;
import org.sakaiproject.nakamura.testutils.easymock.AbstractEasyMockTest;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class MailmanGroupManagerTest extends AbstractEasyMockTest {

  private MailmanManager mailmanManager;
  private Repository slingRepository;
  private MailmanGroupManager groupManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mailmanManager = mock(MailmanManager.class);
    slingRepository = mock(Repository.class);
    groupManager = new MailmanGroupManager(mailmanManager, slingRepository);
  }

  @Test
  public void testHandleGroupAdd() throws MailmanException {
    String groupName = "g-testgroup";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/create";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type", "group");
    properties.put("path", groupName);
    Event event = new Event(topic, properties);

    mailmanManager.createList(groupName, groupName + "@example.com");
    replay();
    groupManager.handleEvent(event);
    verify();
  }

  @Test
  public void testHandleGroupRemove() throws MailmanException {
    String groupName = "g-testgroup";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/delete";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type", "group");
    properties.put("path", groupName);
    Event event = new Event(topic, properties);

    mailmanManager.deleteList(groupName, null);
    replay();
    groupManager.handleEvent(event);
    verify();
  }

  @Test
  public void testHandleGroupJoin() throws MailmanException{
    String groupName = "g-testgroup";
    String testAddress = "test@test.com";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/join";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type", "group");
    properties.put("path", groupName);
    Event event = new Event(topic, properties);

    when(mailmanManager.addMember(groupName, null, testAddress)).thenReturn(Boolean.TRUE);
    groupManager.handleEvent(event);
  }

  @Test
  public void testHandleGroupPart() throws MailmanException {
    String groupName = "g-testgroup";
    String testAddress = "test@test.com";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/part";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type", "group");
    properties.put("path", "testuser");
    Event event = new Event(topic, properties);

    when(mailmanManager.removeMember(groupName, null, testAddress)).thenReturn(Boolean.TRUE);
    groupManager.handleEvent(event);
  }
}
