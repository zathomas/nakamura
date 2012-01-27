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
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.api.user;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AuthorizableUtil {

  public static final Set<String> IGNORE_AUTHIDS = ImmutableSet.of(Group.EVERYONE, User.ANON_USER, User.ADMIN_USER);

  /**
   * Lists the Groups this authorizable is a member of excluding everyone. Includes group that the member is indirectly a memberOf
   * @param au the authorizable
   * @param authorizableManager
   * @return list of unique groups the authorizable is a member of, including indirect and intermediage membership.
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  public static List<Authorizable> getRealGroups(Authorizable au, AuthorizableManager authorizableManager) throws AccessDeniedException, StorageClientException {

    List<Authorizable> realGroups = new ArrayList<Authorizable>();
    for (Iterator<Group> memberOf = au.memberOf(authorizableManager); memberOf.hasNext();) {
      Authorizable group = memberOf.next();
      if (isRealGroup(group)) {
        realGroups.add(group);			
      }
    }
    return realGroups;
  }

  /**
   * Validates if an authorizable is a valid group
   * @param group the authorizable
   * @return true if its an absolute group
   */
  public static Boolean isRealGroup(Authorizable group) {
    if (group == null || !group.isGroup()
        // we don't want to count the everyone groups
        || IGNORE_AUTHIDS.contains(group.getId())
        // don't count if the group is to be excluded
        || Boolean.parseBoolean(String.valueOf(group.getProperty("sakai:excludeSearch")))
        // don't count if the group lacks a title
        || group.getProperty("sakai:group-title") == null
        || StringUtils.isEmpty(String.valueOf(group.getProperty("sakai:group-title")))
        // don't count the special "contacts" group
        || group.getId().startsWith("g-contacts-")) {
      return false;
    }
    else {
      return true;
    }		
  }

  /**
   * Validates that an Authorizable is not null, is a <code>Group</code> but is not a
   * contact group (i.e. title doesn't start with "g-contacts-"
   * 
   * @param group
   * @return
   */
  public static Boolean isContactGroup(Authorizable group) {
    Boolean result = false;
    if (group != null) {
      String title = String.valueOf(group.getProperty("sakai:group-title"));
      result = (group.isGroup() && title.startsWith("g-contacts-"));
    }
    return result;
  }
}
