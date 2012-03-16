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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.util.PathUtils;

import com.google.common.collect.Lists;

/**
 *
 */
@Component
@Service
@Property(name = MoveCleaner.RESOURCE_TYPE, value = "sakai/pooled-content")
public class SakaiDocMoveCleaner implements MoveCleaner {
  public static final String MIMETYPE = "_mimeType";
  public static final String SAKAI_DOC_MIMETYPE = "x-sakai/document";
  public static final String MESSAGESTORE_PROP = "sakai:messagestore";
  public static final List<String> TYPES = Lists.newArrayList("comments", "discussion");

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.resource.MoveCleaner#clean(String, Content,
   *      ContentManager)
   */
  @Override
  public List<Modification> clean(String fromPath, Content toContent, ContentManager cm)
      throws StorageClientException, AccessDeniedException {
    /*
     * There are checks for nodes that start with "id" because we can assume that widget
     * container nodes will start that way. This keeps the processing from hitting the
     * database for a node that doesn't contain widget information.
     */

    // make sure we're working with a sakai document
    if (!SAKAI_DOC_MIMETYPE.equals(toContent.getProperty(MIMETYPE))) {
        return Collections.emptyList();
    }

    // setup the var to return
    List<Modification> mods = Lists.newArrayList();

    // walk through the children looking for pages. a page will have a 'rows' element.
    // we want the widgets that are stored in the page.
    String toPath = toContent.getPath();
    Iterator<String> pagePaths = cm.listChildPaths(toPath);
    while (pagePaths.hasNext()) {

      // make the shortcut assumption that what we're interested in starts with id*.
      // there should also be a 'rows' node that pages have.
      String pagePath = pagePaths.next();
      String pageId = PathUtils.lastElement(pagePath);
      String rowsPath = pagePath + "/rows";
      if (pageId.startsWith("id") && cm.exists(rowsPath)) {

        // looks like we found a page. look at the child nodes to find the widget
        // containers
        Iterator<String> widgetContainerPaths = cm.listChildPaths(pagePath);
        while (widgetContainerPaths.hasNext()) {

          // check for a type subnode to clean the message store path
          String widgetContainerPath = widgetContainerPaths.next();
          String widgetContainerId = PathUtils.lastElement(widgetContainerPath);
          if (widgetContainerId.startsWith("id")) {
            for (String type : TYPES) {
              String widgetPath = widgetContainerPath + "/" + type;
              if (cm.exists(widgetPath)) {
                Content widget = cm.get(widgetPath);
                mods.addAll(cleanWidgetMessageStore(fromPath, toPath, widget, cm));
              }
            }
          }
        }
      }
    }
    return mods;
  }

  /**
   * Clean the path of a message store to point to the new location of the widget.
   * 
   * @param fromPath Where the widget was.
   * @param toPath Where the widget is now.
   * @param type The type of widget
   * @param widgetContainerPath
   * @param cm
   * @return
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private List<Modification> cleanWidgetMessageStore(String fromPath, String toPath,
      Content widget, ContentManager cm) throws AccessDeniedException,
      StorageClientException {
    List<Modification> mods = Lists.newArrayList();

    // check for a name that starts with id* and a path to the message
    if (widget.hasProperty(MESSAGESTORE_PROP)) {
      String messagestore = String.valueOf(widget.getProperty(MESSAGESTORE_PROP));
      String newMessageStore = StringUtils.replace(messagestore, fromPath, toPath);
      if (!messagestore.equals(newMessageStore)) {
        widget.setProperty(MESSAGESTORE_PROP, newMessageStore);
        cm.update(widget);
        mods.add(Modification.onModified(toPath + "/" + MESSAGESTORE_PROP));
      }
    }
    return mods;
  }
}
