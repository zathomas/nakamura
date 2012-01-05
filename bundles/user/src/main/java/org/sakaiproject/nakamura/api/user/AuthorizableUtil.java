package org.sakaiproject.nakamura.api.user;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;

import com.google.common.collect.ImmutableSet;

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
  public static Boolean isRealGroup(Authorizable group){
    if (group == null || !(group instanceof Group)
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
}


