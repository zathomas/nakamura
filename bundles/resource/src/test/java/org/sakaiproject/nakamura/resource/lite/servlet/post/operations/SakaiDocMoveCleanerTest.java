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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.sakaiproject.nakamura.api.resource.MoveCleaner.RESOURCE_TYPE;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocMoveCleaner.MESSAGESTORE_PROP;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocMoveCleaner.MIMETYPE;
import static org.sakaiproject.nakamura.resource.lite.servlet.post.operations.SakaiDocMoveCleaner.SAKAI_DOC_MIMETYPE;

import java.util.List;

import org.apache.sling.servlets.post.Modification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SakaiDocMoveCleanerTest {
  SakaiDocMoveCleaner cleaner;
  
  @Before
  public void setUp() {
    cleaner = new SakaiDocMoveCleaner();
  }

  @Test
  public void testNothingToClean() throws Exception {
    String fromPath = "from";
    String toPath = "to";

    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    adminContentManager.update(new Content(toPath, ImmutableMap.<String, Object> of(
        MIMETYPE, SAKAI_DOC_MIMETYPE)));
    Content to = adminContentManager.get(toPath);

    List<Modification> mods = cleaner.clean(fromPath, to, adminContentManager);
    assertNotNull(mods);
    assertEquals(0, mods.size());
  }

  @Test
  public void testCleanAfterMove() throws Exception {
    String fromPath = "from";
    String toPath = "to";

    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    adminContentManager.update(new Content(toPath, ImmutableMap.<String, Object> of(
        RESOURCE_TYPE, "sakai/pooled-content", MIMETYPE, SAKAI_DOC_MIMETYPE)));
    adminContentManager.update(new Content(toPath + "/id123", ImmutableMap.<String, Object>of("yay", "data")));
    adminContentManager.update(new Content(toPath + "/id123/rows", ImmutableMap.<String, Object>of("yay", "page")));

    // test with a discussion widget.
    adminContentManager.update(new Content(toPath + "/id123/id234/discussion", ImmutableMap.<String, Object>of(MESSAGESTORE_PROP, fromPath + "/weee")));
    Content to = adminContentManager.get(toPath);

    List<Modification> mods = cleaner.clean(fromPath, to, adminContentManager);
    assertNotNull(mods);
    assertEquals(1, mods.size());

    Content discussion = adminContentManager.get(toPath + "/id123/id234/discussion");
    assertEquals(toPath + "/weee", discussion.getProperty(MESSAGESTORE_PROP));


    // test with a comments widget. since discussion widget was fixed above we should only see 1 modification.
    adminContentManager.update(new Content(toPath + "/id123/id234/comments", ImmutableMap.<String, Object>of(MESSAGESTORE_PROP, fromPath + "/good_times")));
    to = adminContentManager.get(toPath);

    mods = cleaner.clean(fromPath, to, adminContentManager);
    assertNotNull(mods);
    assertEquals(1, mods.size());

    Content comments = adminContentManager.get(toPath + "/id123/id234/comments");
    assertEquals(toPath + "/good_times", comments.getProperty(MESSAGESTORE_PROP));


    // test with 2 new nodes. should be 2 modifications
    adminContentManager.update(new Content(toPath + "/id123/id235/comments", ImmutableMap.<String, Object>of(MESSAGESTORE_PROP, fromPath + "/more_fun")));
    adminContentManager.update(new Content(toPath + "/id123/id235/discussion", ImmutableMap.<String, Object>of(MESSAGESTORE_PROP, fromPath + "/going_crazy")));
    to = adminContentManager.get(toPath);

    mods = cleaner.clean(fromPath, to, adminContentManager);
    assertNotNull(mods);
    assertEquals(2, mods.size());

    comments = adminContentManager.get(toPath + "/id123/id235/comments");
    discussion = adminContentManager.get(toPath + "/id123/id235/discussion");
    assertEquals(toPath + "/more_fun", comments.getProperty(MESSAGESTORE_PROP));
    assertEquals(toPath + "/going_crazy", discussion.getProperty(MESSAGESTORE_PROP));
  }
}
