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
package org.sakaiproject.nakamura.presence;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.api.presence.PresenceService;
import org.sakaiproject.nakamura.api.presence.PresenceStatus;
import org.sakaiproject.nakamura.memory.MapCacheImpl;

import java.util.List;
import java.util.Map;

/**
 * 
 */
public class PresenceServiceImplTest {

  private CacheManagerService cacheManagerService;
  private PresenceServiceImpl presenceService;
  private Cache<Object> presenceLocationCache;
  private Cache<Object> presenceStatusCache;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {

    presenceLocationCache = new MapCacheImpl<Object>("testlocation", CacheScope.INSTANCE);
    presenceStatusCache = new MapCacheImpl<Object>("teststatus", CacheScope.INSTANCE);

    cacheManagerService = createMock(CacheManagerService.class);
    expect(
        cacheManagerService.getCache("presence.location",
            CacheScope.CLUSTERREPLICATED)).andReturn(presenceLocationCache)
        .anyTimes();
    expect(
        cacheManagerService.getCache("presence.status",
            CacheScope.CLUSTERREPLICATED)).andReturn(presenceStatusCache)
        .anyTimes();
    replay(cacheManagerService);

    presenceService = new PresenceServiceImpl();
    presenceService.bindCacheManagerService(cacheManagerService);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
    presenceService.unbindCacheManagerService(cacheManagerService);
    verify(cacheManagerService);
  }

  @Test
  public void testClear() {
    String uuid = "jack";
    String status = "online";
    presenceService.setStatus(uuid, status);
    
    String result = presenceService.getStatus(uuid);
    assertEquals(status, result);

    // Clear everything
    presenceService.clear(uuid);
    result = presenceService.getStatus(uuid);
    assertEquals(PresenceStatus.offline.name(), result);
  }
  
  @Test
  public void testSetStatus() {
    String uuid = "jack";
    presenceService.setStatus(uuid, "foo:bar");
    String status = presenceService.getStatus(uuid);
    Assert.assertEquals("foo:bar", status);
  }
  
  /**
   * Test method for
   * {@link org.sakaiproject.nakamura.presence.PresenceServiceImpl#online(java.util.List)}
   * .
   */
  @Test
  public void testOnlineFriends() {
    for (int j = 0; j < 100; j++) {
      for (int i = 0; i < 100; i++) {
        if (i == 50) {
          Map<String, String> onlineLocations = presenceService
              .online("location" + j);
          assertEquals(50, onlineLocations.size());
        }
        presenceService.ping("user" + i, "location" + j);
        if (i % 7 == 0) {
          presenceService.setStatus("user" + i, "drinking beer");
        } else {
          presenceService.setStatus("user" + i, "user" + i + "at" + j);
        }
      }
      Map<String, String> onlineLocations = presenceService.online("location"
          + j);
      assertEquals(100, onlineLocations.size());
    }
    List<String> friends = Lists.newArrayList();
    for (int i = 0; i < 50; i++) {
      friends.add("user" + i);
    }
    for (int i = 0; i < 50; i++) {
      friends.add("otheruser" + i);
    }
    Map<String, String> online = presenceService.online(friends);
    assertEquals(100, online.size());
    for (int i = 0; i < 50; i++) {
      String status = online.get("otheruser" + i);
      assertEquals("offline", status);
    }
    for (int i = 0; i < 50; i++) {
      String status = online.get("user" + i);
      if (i % 7 == 0) {
        assertEquals("drinking beer", status);
      } else {
        assertEquals("user" + i + "at99", status);
      }
    }
  }

  /**
   * Test method for
   * {@link org.sakaiproject.nakamura.presence.PresenceServiceImpl#ping(java.lang.String, java.lang.String)}
   * .
   */
  @Test
  public void testPing() {
    presenceService.ping(null, null);
    presenceService.ping(null, "locationA");
    for (int i = 0; i < 100; i++) {
      presenceService.ping("user" + i, null);
    }
  }
  
  public PresenceService getPresenceService() {
    return this.presenceService;
  }

}
