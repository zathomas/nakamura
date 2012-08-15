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
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.user.BadRequestException;
import org.sakaiproject.nakamura.api.user.SakaiPerson;
import org.sakaiproject.nakamura.api.user.SakaiPersonService;
import org.sakaiproject.nakamura.util.SparseUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SparseMapPersonService implements SakaiPersonService {

  @Reference
  Repository repository;

  @Override
  public SakaiPerson createPerson(String personId, String firstName, String lastName, String email, String password, String passwordConfirm, Map<String, Object> properties) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void updatePerson(String personId, String firstName, String lastName, String email, Map<String, Object> properties) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public SakaiPerson getPerson(String personId) {
    return new SakaiPerson() { };
  }

  @Override
  public void deletePerson(String personId) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isPersonIdInUse(String personId) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void tagPerson(String personId, Set<String> tags) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void untagPerson(String personId, Set<String> tags) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void changePersonAccountPassword(String userId, String oldPwd, String newPwd, String newPwdConfirm) {
    if (userId == null || oldPwd == null || newPwd == null || newPwdConfirm == null) {
      throw new BadRequestException("All parameters are required: userId, oldPwd, newPwd, newPwdConfirm");
    }
    if (!newPwd.equals(newPwdConfirm)) {
      throw new BadRequestException("New Password does not match the confirmation password");
    }
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      authorizableManager.changePassword(authorizableManager.findAuthorizable(userId), newPwd, oldPwd);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

  @Override
  public List<SakaiPerson> searchPeople(String query, Set<String> tags, boolean alsoSearchProfile, String sortOn, SortOrder sortOrder, int limit, int offset) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
