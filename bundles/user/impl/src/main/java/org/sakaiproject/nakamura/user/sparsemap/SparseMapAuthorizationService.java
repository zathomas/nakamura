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
package org.sakaiproject.nakamura.user.sparsemap;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.user.PermissionDeniedException;
import org.sakaiproject.nakamura.api.user.SakaiAuthorizationService;
import org.sakaiproject.nakamura.util.SparseUtils;

@Service
public class SparseMapAuthorizationService implements SakaiAuthorizationService {
  @Reference
  Repository repository;

  @Override
  public void canChangeUserPassword(String userId, String userIdToChange) throws PermissionDeniedException {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative(userId);
      AccessControlManager accessControlManager = adminSession.getAccessControlManager();
      accessControlManager.check(Security.ZONE_AUTHORIZABLES, userIdToChange, Permissions.CAN_MANAGE);
    } catch (AccessDeniedException ade) {
      throw new PermissionDeniedException();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }
}
