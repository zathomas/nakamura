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

package org.sakaiproject.nakamura.upgrade;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.files.migrator.DocMigrator;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Service
@Component
public class UndefinedSakaiDocPageFixer implements PropertyMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(UndefinedSakaiDocPageFixer.class);

  private static final String PAGE_PROP = "page";

  @Reference
  private Repository repository;

  @Override
  public boolean migrate(String rowID, Map<String, Object> properties) {
    boolean handled = false;
    Object page = properties.get(PAGE_PROP);
    Object pathObj = properties.get("_path");
    if (page != null && pathObj != null) {
      String path = pathObj.toString();
      if ("undefined".equals(PathUtils.lastElement(path))) {
        LOGGER.info("We have an content node called undefined with a page prop at " + properties.get("_path"));
        Session session = null;
        try {
          session = repository.loginAdministrative();
          Content parent = session.getContentManager().get(PathUtils.getParentReference(path));
          if (parent != null) {
            LOGGER.info("Got the parent of an undefined page node: " + parent.getPath());
          }
        } catch (AccessDeniedException e) {
          LOGGER.error("admin session denied access?", e);
        } catch (ClientPoolException e) {
          LOGGER.error("storage client pool exception", e);
        } catch (StorageClientException e) {
          LOGGER.error("StorageClientException", e);
        } finally {
          if (session != null) {
            try {
              session.logout();
            } catch (ClientPoolException e) {
              LOGGER.error("Unexpected exception logging out of session", e);
            }
          }
        }
      }
    }
    return handled;
  }

  @Override
  public String[] getDependencies() {
    return new String[]{};
  }

  @Override
  public String getName() {
    return UndefinedSakaiDocPageFixer.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

}
