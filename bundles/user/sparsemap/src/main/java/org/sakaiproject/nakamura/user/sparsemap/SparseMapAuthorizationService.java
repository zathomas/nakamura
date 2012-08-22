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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.people.PermissionDeniedException;
import org.sakaiproject.nakamura.api.people.SakaiAuthorizationService;
import org.sakaiproject.nakamura.util.SparseUtils;

import java.util.Dictionary;

@Component(metatype = true)
@Service
public class SparseMapAuthorizationService implements SakaiAuthorizationService {
  private static final String PROP_SELF_REGISTRATION_ENABLED = "self.registration.enabled";

  private static final Boolean DEFAULT_SELF_REGISTRATION_ENABLED = Boolean.TRUE;

  private Boolean selfRegistrationEnabled = DEFAULT_SELF_REGISTRATION_ENABLED;

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

  @Override
  public void canCreateNewSakaiPerson(String personId) throws PermissionDeniedException {
    if (User.ADMIN_USER.equals(personId) || selfRegistrationEnabled) {
      return;
    } else {
      throw new PermissionDeniedException();
    }
  }

  @Override
  public void canModifySakaiPerson(String personId, String personIdToChange) throws PermissionDeniedException {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative(personId);
      AccessControlManager accessControlManager = adminSession.getAccessControlManager();
      accessControlManager.check(Security.ZONE_AUTHORIZABLES, personIdToChange, Permissions.CAN_WRITE);
    } catch (AccessDeniedException ade) {
      throw new PermissionDeniedException();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

  @Activate @Modified
  protected void activate(ComponentContext componentContext) {
    Dictionary<?, ?> props = componentContext.getProperties();
    selfRegistrationEnabled = PropertiesUtil.toBoolean(props.get(PROP_SELF_REGISTRATION_ENABLED), DEFAULT_SELF_REGISTRATION_ENABLED);
  }
}
