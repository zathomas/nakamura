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
package org.sakaiproject.nakamura.files.servlets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class DeleteTagOperationTest {
  DeleteTagOperation operation;
  HtmlResponse response;
  Content content;
  Content tag2Node;
  Content tags;
  javax.jcr.Session jcrSession;
  List<Modification> changes;

  @Mock
  Repository repository;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  ResourceResolver resolver;
  @Mock
  ContentManager contentManager;
  @Mock
  Session session;
  @Mock
  Resource resource;

  @Before
  public void setUp() throws Exception {
    response = new HtmlResponse();

    operation = new DeleteTagOperation();
    operation.repository = repository;

    when(request.getRemoteUser()).thenReturn("testUser");
    when(request.getResourceResolver()).thenReturn(resolver);
    when(request.getResource()).thenReturn(resource);

    when(repository.loginAdministrative()).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);

    jcrSession = Mockito.mock(javax.jcr.Session.class,
        withSettings().extraInterfaces(SessionAdaptable.class));
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);

    tag2Node = new Content("/tags/tag2", ImmutableMap.<String, Object> of(
        FilesConstants.SAKAI_TAG_COUNT, 5));
    when(contentManager.get(tag2Node.getPath())).thenReturn(tag2Node);
    when(contentManager.listChildren(tag2Node.getPath())).thenReturn(
        Iterators.<Content>emptyIterator());

    tags = new Content("/tags", null);
    when(contentManager.get(tags.getPath())).thenReturn(tags);
    when(contentManager.listChildren("/tags")).thenReturn(
        Lists.newArrayList(tag2Node).iterator());

    content = new Content("/some/path", ImmutableMap.<String, Object> of(
        FilesConstants.SAKAI_TAGS, new String[] { "tag1", "tag2" }));
    when(contentManager.get(content.getPath())).thenReturn(content);
    when(resource.adaptTo(Content.class)).thenReturn(content);

    changes = Lists.newArrayList();
  }

  /**
   * Request is bad if the "key" parameter isn't found in the request.
   *
   * @throws Exception
   */
  @Test
  public void badRequestWhenTagAbsent() throws Exception {
    operation.doRun(request, response, contentManager, changes, content.getPath());

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusCode());
  }

  /**
   * Request is bad when the request tag to delete doesn't start with "/tags/"
   *
   * @throws Exception
   */
  @Test
  public void badRequestWhenTagNameDoesntStartWithTags() throws Exception {
    when(request.getParameter("key")).thenReturn("not there");
    operation.doRun(request, response, contentManager, changes, content.getPath());

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusCode());
  }

  /**
   * No changes happen when the content doesn't contain the tag to delete.
   *
   * @throws Exception
   */
  @Test
  public void noChangesWhenContentLacksTag() throws Exception {
    when(request.getParameter("key")).thenReturn("/tags/not there");
    operation.doRun(request, response, contentManager, changes, content.getPath());

    assertEquals(HttpServletResponse.SC_OK, response.getStatusCode());
  }

  /**
   * Delete a tag from the content even when the tag node doesn't exist in /tags/.
   *
   * @throws Exception
   */
  @Test
  public void deleteTagWithoutTagNode() throws Exception {
    when(request.getParameter("key")).thenReturn("/tags/tag1");
    operation.doRun(request, response, contentManager, changes, content.getPath());

    assertEquals(HttpServletResponse.SC_OK, response.getStatusCode());
    assertEquals(1, ((String[]) content.getProperty(FilesConstants.SAKAI_TAGS)).length);
    assertEquals("tag2", ((String[]) content.getProperty(FilesConstants.SAKAI_TAGS))[0]);
  }

  /**
   * Delete a tag from the content.
   *
   * @throws Exception
   */
  @Test
  public void deleteTag() throws Exception {
    when(request.getParameter("key")).thenReturn(tag2Node.getPath());
    operation.doRun(request, response, contentManager, changes, content.getPath());

    assertEquals(HttpServletResponse.SC_OK, response.getStatusCode());
    assertEquals(1, ((String[]) content.getProperty(FilesConstants.SAKAI_TAGS)).length);
    assertEquals("tag1", ((String[]) content.getProperty(FilesConstants.SAKAI_TAGS))[0]);
    assertEquals(4L, tag2Node.getProperty(FilesConstants.SAKAI_TAG_COUNT));
  }
}
