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

package org.sakaiproject.nakamura.files;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_CREATED_FOR;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_FILENAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_RT;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_MANAGER;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_NEEDS_PROCESSING;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.activity.ActivityUtils;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.files.FileService;
import org.sakaiproject.nakamura.api.files.PermissionException;
import org.sakaiproject.nakamura.api.files.StorageException;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Service(value = FileService.class)
public class SparseFileServiceImpl implements FileService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparseFileServiceImpl.class);

  @Reference
  protected Repository sparseRepository;

  @Reference
  protected ClusterTrackingService clusterTrackingService;

  @Reference
  protected EventAdmin eventAdmin;

  public Map<String, Object> createFile(String creator, String filename, String contentType, InputStream inputStream)
      throws StorageException, IOException {
    Map<String, Object> fileProps = new HashMap<String, Object>();
    Session adminSession = null;
    try {
      // Grab an admin session so we can create files in the pool space.
      adminSession = sparseRepository.loginAdministrative();

      ContentManager contentManager = adminSession.getContentManager();
      String poolID = generatePoolId();

      Map<String, Object> contentProperties = new HashMap<String, Object>();
      contentProperties.put(POOLED_CONTENT_FILENAME, filename);
      contentProperties.put(SLING_RESOURCE_TYPE_PROPERTY, POOLED_CONTENT_RT);
      contentProperties.put(POOLED_CONTENT_CREATED_FOR, creator);
      contentProperties.put(POOLED_NEEDS_PROCESSING, "true");
      contentProperties.put(Content.MIMETYPE_FIELD, contentType);
      contentProperties.put(POOLED_CONTENT_USER_MANAGER, new String[]{creator});

      Content content = new Content(poolID, contentProperties);
      contentManager.update(content);

      // TODO figure out how to make FileUploadFilter work without RequestParam dependency
      // InputStream inputStream = filterUploadInputStream(poolID, value.getInputStream(), contentType, value);
      contentManager.writeBody(poolID, inputStream);

      // deny anon everything
      // deny everyone everything
      // grant the user everything.
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
      AclModification.addAcl(true, Permissions.CAN_MANAGE, creator, modifications);
      AccessControlManager accessControlManager = adminSession.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, poolID, modifications.toArray(new AclModification[modifications.size()]));

      ActivityUtils.postActivity(eventAdmin, creator, poolID, "Content", "default", "pooled content", "CREATED_FILE", null);

      fileProps = ImmutableMap.of("poolId", poolID, "item", content.getProperties());

    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new PermissionException("Admin session should be able to do anything!", e);
    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } catch (NoSuchAlgorithmException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } catch (UnsupportedEncodingException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("Could not log out of admin session", e);
        }
      }
    }

    return fileProps;
  }

  private String generatePoolId() throws UnsupportedEncodingException,
      NoSuchAlgorithmException {
    return clusterTrackingService.getClusterUniqueId();
  }

}
