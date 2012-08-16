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
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_COMMENT_COUNT;
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
import org.sakaiproject.nakamura.api.files.File;
import org.sakaiproject.nakamura.api.files.FileParams;
import org.sakaiproject.nakamura.api.files.FileService;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.files.PermissionException;
import org.sakaiproject.nakamura.api.files.StorageException;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.sakaiproject.nakamura.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

  public File createFile(FileParams params)
      throws StorageException, IOException {
    File file = null;
    Session adminSession = null;
    try {
      // Grab an admin session so we can create files in the pool space.
      adminSession = sparseRepository.loginAdministrative();

      String poolID = generatePoolId();

      Map<String, Object> contentProperties = new HashMap<String, Object>();
      contentProperties.put(POOLED_CONTENT_FILENAME, params.getFilename());
      contentProperties.put(SLING_RESOURCE_TYPE_PROPERTY, POOLED_CONTENT_RT);
      contentProperties.put(POOLED_CONTENT_CREATED_FOR, params.getCreator());
      if (params.hasStream()) {
        contentProperties.put(POOLED_NEEDS_PROCESSING, "true");
      }
      if (params.getContentType() != null) {
        contentProperties.put(Content.MIMETYPE_FIELD, params.getContentType());
      }
      contentProperties.put(POOLED_CONTENT_USER_MANAGER, new String[]{params.getCreator()});
      contentProperties.put(POOLED_CONTENT_COMMENT_COUNT, 0);
      if (params.getProperties() != null) {
        contentProperties.putAll(params.getProperties());
      }

      Content content = new Content(poolID, contentProperties);
      ContentManager contentManager = adminSession.getContentManager();
      contentManager.update(content);

      // TODO figure out how to make FileUploadFilter work without RequestParam dependency
      // InputStream inputStream = filterUploadInputStream(poolID, value.getInputStream(), contentType, value);
      if (params.hasStream()) {
        contentManager.writeBody(poolID, params.getInputStream());
      }

      // deny anon everything
      // deny everyone everything
      // grant the user everything.
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
      AclModification.addAcl(true, Permissions.CAN_MANAGE, params.getCreator(), modifications);
      AccessControlManager accessControlManager = adminSession.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, poolID, modifications.toArray(new AclModification[modifications.size()]));

      if (params.hasStream()) {
        ActivityUtils.postActivity(eventAdmin, params.getCreator(), poolID, "Content", "default", "pooled content", "CREATED_FILE", null);
      } else {
        ActivityUtils.postActivity(eventAdmin, params.getCreator(), poolID, "Content", "default", "pooled content", "UPDATED_CONTENT", null);
      }

      file = new File(params.getCreator(), params.getFilename(), params.getContentType(), poolID, content.getProperties());

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
      SparseUtils.logoutQuietly(adminSession);
    }

    return file;
  }

  public File createAlternativeStream(FileParams params)
      throws StorageException, IOException {
    File file = null;

    Session adminSession = null;
    try {
      // Grab an admin session so we can create files in the pool space.
      adminSession = sparseRepository.loginAdministrative();

      String[] alternativeStreamParts = StringUtils.split(params.getAlternativeStream(), FilesConstants.ALTERNATIVE_STREAM_SELECTOR_SEPARATOR);
      String pageId = alternativeStreamParts[0];
      String previewSize = alternativeStreamParts[1];
      Content alternativeContent = new Content(params.getPoolID() + "/" + pageId, ImmutableMap.of(
          Content.MIMETYPE_FIELD, (Object) params.getContentType(), SLING_RESOURCE_TYPE_PROPERTY,
          POOLED_CONTENT_RT));
      ContentManager contentManager = adminSession.getContentManager();
      contentManager.update(alternativeContent);

      // TODO figure out how to make FileUploadFilter work without RequestParam dependency
      // InputStream inputStream = filterUploadInputStream(alternativeContent.getPath(), value.getInputStream(), contentType, value);
      contentManager.writeBody(alternativeContent.getPath(), params.getInputStream(), previewSize);

      ActivityUtils.postActivity(eventAdmin, params.getCreator(), params.getPoolID(), "Content", "default",
          "pooled content", "CREATED_ALT_FILE",
          ImmutableMap.<String, Object>of("altPath", params.getPoolID() + "/" + pageId));

      file = new File(params.getCreator(), params.getFilename(), params.getContentType(), params.getPoolID(), alternativeContent.getProperties());

    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new PermissionException("Admin session should be able to do anything!", e);
    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
    return file;

  }

  public File updateFile(FileParams params)
      throws StorageException, IOException {
    File file = null;
    Session adminSession = null;
    try {
      // Grab an admin session so we can create files in the pool space.
      adminSession = sparseRepository.loginAdministrative();
      ContentManager contentManager = adminSession.getContentManager();
      Content content = contentManager.get(params.getPoolID());
      content.setProperty(StorageClientUtils.getAltField(Content.MIMETYPE_FIELD, params.getAlternativeStream()), params.getContentType());
      contentManager.update(content);

      // TODO figure out how to make FileUploadFilter work without RequestParam dependency
      // InputStream inputStream = filterUploadInputStream(poolId, value.getInputStream(), contentType, value);
      contentManager.writeBody(params.getPoolID(), params.getInputStream(), params.getAlternativeStream());
      ActivityUtils.postActivity(eventAdmin, params.getCreator(), params.getPoolID(), "Content", "default", "pooled content", "UPDATED_FILE", null);

      file = new File(params.getCreator(), params.getFilename(), params.getContentType(), params.getPoolID(), content.getProperties());

    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new PermissionException("Admin session should be able to do anything!", e);
    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new StorageException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
    return file;
  }

  private String generatePoolId() throws UnsupportedEncodingException,
      NoSuchAlgorithmException {
    return clusterTrackingService.getClusterUniqueId();
  }

}
