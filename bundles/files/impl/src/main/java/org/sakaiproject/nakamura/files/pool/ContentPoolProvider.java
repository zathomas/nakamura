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

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceProvider;
import org.apache.sling.api.resource.ResourceResolver;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.jackrabbit.JackrabbitSparseUtils;
import org.sakaiproject.nakamura.api.resource.lite.SparseContentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

@Component(immediate = true, metatype = true)
@Service(value = ResourceProvider.class)
@Property(name = ResourceProvider.ROOTS, value = { "/", "/p" })
public class ContentPoolProvider implements ResourceProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContentPoolProvider.class);
  public static final String CONTENT_RESOURCE_PROVIDER = ContentPoolProvider.class
      .getName();

  // this 36*36 = 1296, so /a/aa/aa/aa will have 36 at the first level, then 46656 at the
  // second and then 60M, then 7e10 items at the last level.

  /**
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.resource.ResourceProvider#getResource(org.apache.sling.api.resource.ResourceResolver,
   *      javax.servlet.http.HttpServletRequest, java.lang.String)
   */
  public Resource getResource(ResourceResolver resourceResolver,
      HttpServletRequest request, String path) {
    LOGGER.debug("Got Resource URI [{}]  Path [{}] ", request.getRequestURI(), path);
    return getResource(resourceResolver, path);
  }

  /**
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.resource.ResourceProvider#getResource(org.apache.sling.api.resource.ResourceResolver,
   *      java.lang.String)
   */
  public Resource getResource(ResourceResolver resourceResolver, String path) {

    if (path == null || path.length() < 2) {
      return null;
    }
    char c = path.charAt(1);
    if (!(c == 'p')) {
      return null;
    }
    try {
      return resolveMappedResource(resourceResolver, path);
    } catch (RepositoryException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage());
      LOGGER.debug(e.getMessage(), e);
    }
    return null;
  }

  private Resource resolveMappedResource(ResourceResolver resourceResolver, String path)
      throws StorageClientException, AccessDeniedException, RepositoryException {
    String poolId = null;
    Session session = JackrabbitSparseUtils.getSparseSession(resourceResolver
        .adaptTo(javax.jcr.Session.class));
    ContentManager contentManager = session.getContentManager();

    if (path.startsWith("/p/")) {
      poolId = path.substring("/p/".length());
    }
    if (poolId != null && poolId.length() > 0) {
      // Resolution Process.
      // 1. Test the whole path
      // 2. Locate the PoolID and get that content item out
      // 3. See if it has a File name property that matches the remainder

      // 1. Test the whole path.
      Content content = contentManager.get(poolId);
      if ( content != null ) {
        SparseContentResource cpr = new SparseContentResource(content, session,
            resourceResolver, path);
        cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
        LOGGER.debug("Resolved {} as {} ",path,cpr);
        return cpr;
      }

      // 2. get the PoolID
      int i = poolId.indexOf('/');
      String resourceId = null;
      if (i > 0) {
        resourceId = poolId.substring(i+1);
        poolId = poolId.substring(0, i);
      }
      content = contentManager.get(poolId);
      LOGGER.debug("Got PooID {} as {} ", poolId, content);
      if ( content != null ) {
        // 3. See if he resource is on the Content Pool node.
        if (resourceId != null && resourceId.length() > 0 ) {
          String[] possibleStructure = StringUtils.split(resourceId, "/", 2);
          if ( resourceId.equals(content.getProperty(FilesConstants.POOLED_CONTENT_FILENAME))) {
            SparseContentResource cpr = new SparseContentResource(content, session,
                resourceResolver, path);
            cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
            LOGGER.debug("Resolved {} as {} ",path,cpr);
            return cpr;
          } else if ( possibleStructure != null && possibleStructure.length > 0 && content.hasProperty(FilesConstants.STRUCTURE_FIELD_STEM+possibleStructure[0]) ) {
            SparseContentResource cpr = new SparseContentResource(content, session,
                resourceResolver, path);
            cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
            LOGGER.debug("Resolved {} as {} ",path,cpr);
            return cpr;
          } else if (possibleStructure != null && possibleStructure.length == 1) {
            String[] possibleAltStream = StringUtils.split(resourceId, ".", 4);
            if (possibleAltStream.length == 3) {
              try {
                Content altParent = contentManager.get(poolId + "/" + possibleAltStream[0]);
                String altField = StorageClientUtils.getAltField(Content.BODY_CREATED_FIELD, possibleAltStream[1]);
                if (altParent.hasProperty(altField)) {
                  SparseContentResource cpr = new SparseContentResource(altParent, session,
                      resourceResolver, path);
                  cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
                  LOGGER.debug("Resolved {} as {} ",path,cpr);
                  return cpr;
                }
              } catch (StorageClientException e) {
                LOGGER.warn(e.getMessage(), e);
              } catch (AccessDeniedException e) {
                LOGGER.warn(e.getMessage(), e);
              }
            }
          }
        } else {
          SparseContentResource cpr = new SparseContentResource(content, session,
              resourceResolver, path);
          cpr.getResourceMetadata().put(CONTENT_RESOURCE_PROVIDER, this);
          LOGGER.debug("Resolved {} as {} ",path,cpr);
          return cpr;
        }
      }
      LOGGER.debug("THrowing Exception on {} as {} ",path);
      throw new SlingException("Creating a pool item is not allowed via this URL ",
            new AccessDeniedException(Security.ZONE_CONTENT, poolId,
                "Cant create Pool Item", ""));
    }
    LOGGER.debug("Returning null; ",path);
    return null;
  }

  public Iterator<Resource> listChildren(Resource parent) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("List Children [{}] ", parent.getPath());
    }
    return null;
  }

}
