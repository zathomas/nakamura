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
package org.sakaiproject.nakamura.files.servlets;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableAction;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentAction;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@SlingServlet( paths = {"/system/dict"})
public class ShowDataDictionaryServlet extends SlingSafeMethodsServlet {
  private static final Logger LOG = LoggerFactory.getLogger(ShowDataDictionaryServlet.class);

  @Reference
  Repository sparseRepository;

  @Override
  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response) throws ServletException,
    IOException {
    LOG.info("Starting data dictionary generation...");
    final Map<String, Set<String>> contentDictionary = Maps.newHashMap();
    final Map<String, Long> contentTypeCounts = Maps.newHashMap();
    final Map<String, Set<String>> authorizableDictionary = Maps.newHashMap();
    final Map<String, Long> authTypeCounts = Maps.newHashMap();
    Session adminSession = null;
    try {
      adminSession = sparseRepository.loginAdministrative();
      AuthorizableManager adminAuthManager = adminSession.getAuthorizableManager();
      ContentManager adminContentManager = adminSession.getContentManager();

      adminAuthManager.invokeWithEveryAuthorizable(new AuthorizableAction() {
        public void doIt(Authorizable authorizable) {
          Map<String, Object> properties = authorizable.getPropertiesForUpdate();
          String type;
          if (properties.containsKey("type")) {
            type = (String)properties.get("type");
          } else {
            type = "untyped";
          }
          Set<String> authPropertyKeys;
          if(authorizableDictionary.containsKey(type)) {
            authPropertyKeys = authorizableDictionary.get(type);
            long currentCount = authTypeCounts.get(type).longValue();
            authTypeCounts.put(type, ++currentCount);
          } else {
            authPropertyKeys = Sets.newHashSet();
            authTypeCounts.put(type, Long.valueOf(1));
          }
          for (String propertyKey : properties.keySet()) {
            authPropertyKeys.add(propertyKey);
          }
          authorizableDictionary.put(type, authPropertyKeys);
        }
      });

      adminContentManager.invokeWithEveryContent(new ContentAction() {
        public void doIt(Content content) {
          Map<String, Object> properties = content.getPropertiesForUpdate();
          String type;
          if (properties.containsKey("sling:resourceType")) {
            type = (String)properties.get("sling:resourceType");
          } else {
            type = "untyped";
          }
          Set<String> contentPropertyKeys;
          if (contentDictionary.containsKey(type)) {
            contentPropertyKeys = contentDictionary.get(type);
            long currentCount = contentTypeCounts.get(type).longValue();
            contentTypeCounts.put(type, ++currentCount);
          } else {
            contentPropertyKeys = Sets.newHashSet();
            contentTypeCounts.put(type, Long.valueOf(1));
          }
          for (String propertyKey : properties.keySet()) {
            contentPropertyKeys.add(propertyKey);
          }
          contentDictionary.put(type, contentPropertyKeys);
        }
      });
      LOG.info("AUTHORIZABLES");
      for (String authType : authorizableDictionary.keySet()) {
        LOG.info("{} ({})", authType, authTypeCounts.get(authType));
        for (String property : authorizableDictionary.get(authType)) {
          LOG.info("    " + property);
        }
      }

      LOG.info("CONTENT");
      for (String contentType : contentDictionary.keySet()) {
        LOG.info("{} ({})", contentType, contentTypeCounts.get(contentType));
        for (String property : contentDictionary.get(contentType)) {
          LOG.info("    " + property);
        }
      }

    } catch (AccessDeniedException e) {
      LOG.error(e.getMessage());
    } catch (ClientPoolException e) {
      LOG.error(e.getMessage());
    } catch (StorageClientException e) {
      LOG.error(e.getMessage());
    } finally {
      if (adminSession != null)
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOG.error(e.getMessage());
        }
    }

  }


}
