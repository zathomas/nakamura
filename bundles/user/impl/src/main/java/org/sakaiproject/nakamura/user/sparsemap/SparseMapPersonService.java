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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.resource.DateParser;
import org.sakaiproject.nakamura.api.user.BadRequestException;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.PermissionDeniedException;
import org.sakaiproject.nakamura.api.user.SakaiPerson;
import org.sakaiproject.nakamura.api.user.SakaiPersonService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.UserFinder;
import org.sakaiproject.nakamura.user.lite.resource.LiteNameSanitizer;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(metatype = true)
@Service
public class SparseMapPersonService implements SakaiPersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparseMapPersonService.class);

  private DateParser dateParser;

  /**
   * Used to post process authorizable creation request.
   *
   */
  @Reference
  protected transient LiteAuthorizablePostProcessService postProcessorService;

  @Reference
  protected UserFinder userFinder;

  @Reference
  Repository repository;

  /**
   * Used to launch OSGi events.
   *
   */
  @Reference
  protected transient EventAdmin eventAdmin;

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

  @Property(value={"sha1"})
  private static final String PROP_PASSWORD_DIGEST_ALGORITHM = "password.digest.algorithm";

  private static final String DEFAULT_PASSWORD_DIGEST_ALGORITHM = "sha1";

  private String passwordDigestAlgoritm = null;

  @Deactivate
  protected void deactivate(ComponentContext context) {
    dateParser = null;
    passwordDigestAlgoritm = null;
  }

  /**
   * Digest the given password using the configured digest algorithm
   *
   * @param pwd the value to digest
   * @return the digested value
   * @throws IllegalArgumentException
   */
  protected String digestPassword(String pwd) throws IllegalArgumentException {
    return digestPassword(pwd, passwordDigestAlgoritm);
  }

  /**
   * Digest the given password using the given digest algorithm
   *
   * @param pwd the value to digest
   * @param digest the digest algorithm to use for digesting
   * @return the digested value
   * @throws IllegalArgumentException
   */
  protected String digestPassword(String pwd, String digest) throws IllegalArgumentException {
    try {
      StringBuffer password = new StringBuffer();
      password.append("{").append(digest).append("}");
      password.append(Text.digest(digest, pwd.getBytes("UTF-8")));
      return password.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e.toString());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e.toString());
    }
  }

  @Activate
  @Modified
  protected void activate(ComponentContext componentContext) {
    Dictionary<?, ?> props = componentContext.getProperties();
    passwordDigestAlgoritm = PropertiesUtil.toString(props.get(PROP_PASSWORD_DIGEST_ALGORITHM), DEFAULT_PASSWORD_DIGEST_ALGORITHM);
  }

  @Override
  public SakaiPerson createPerson(String principalName, String firstName, String lastName, String email, String pwd, String pwdConfirm, Map<String, Object[]> parameters) {

    // check that the submitted parameter values have valid values.
    if (principalName == null) {
      throw new BadRequestException("User name was not submitted");
    }

    LiteNameSanitizer san = new LiteNameSanitizer(principalName, true);
    san.validate();


    if (pwd == null) {
      throw new BadRequestException("Password was not submitted");
    }

    if (!pwd.equals(pwdConfirm)) {
      throw new BadRequestException(
          "Password value does not match the confirmation password");
    }

    Session selfRegSession = null;
    try {
      selfRegSession = getSession();
      AuthorizableManager authorizableManager = selfRegSession.getAuthorizableManager();
      try {
        if (!userFinder.userExists(principalName)) {
          if (authorizableManager.createUser(principalName, principalName,
              digestPassword(pwd), selectPersonParameters(parameters))) {
            LOGGER.info("User {} created", principalName);
            User user = (User) authorizableManager.findAuthorizable(principalName);

            try {
              // this is the call to DefaultPostProcessor
              postProcessorService.process(user, selfRegSession, ModificationType.CREATE, parameters);
            } catch (Exception e) {
              LOGGER.warn(e.getMessage(), e);
              throw new RuntimeException(e);
            }
            // Launch an OSGi event for creating a user.
            try {
              Dictionary<String, String> properties = new Hashtable<String, String>();
              properties.put(UserConstants.EVENT_PROP_USERID, principalName);
              EventUtils.sendOsgiEvent(properties, UserConstants.TOPIC_USER_CREATED,
                  eventAdmin);
            } catch (Exception e) {
              // Trap all exception so we don't disrupt the normal behaviour.
              LOGGER.error("Failed to launch an OSGi event for creating a user.", e);
            }
          } else {
            throw new RuntimeException("Failed to create authorizable " + principalName);
          }
        } else {
          throw new BadRequestException("User with name " + principalName + " already exists.");
        }
      } catch (Exception e1) {
        LOGGER.warn("Could not create user " + principalName, e1);
        throw new RuntimeException("Could not create SakaiPerson " + principalName + " because: " + e1.getLocalizedMessage());
      }
    } catch (AccessDeniedException e) {
      throw new RuntimeException();
    } catch (StorageClientException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } finally {
      SparseUtils.logoutQuietly(selfRegSession);
    }
    return new SakaiPerson() { };
  }

  private final Set<String> PARAMETERS_NOT_STORED = ImmutableSet.of("_charset_", "pwd", "pwdConfirm");

  private Map<String,Object> selectPersonParameters(Map<String,Object[]> parameters) {
    Map<String, Object> personPropertyMap = Maps.newHashMap();
    for (Map.Entry<String, Object[]> parameter : parameters.entrySet()) {
      if (PARAMETERS_NOT_STORED.contains(parameter.getKey())) {
        continue;
      }
      if (parameter.getKey().startsWith(SlingPostConstants.RP_PREFIX)) {
        continue;
      }
      if (parameter.getKey().startsWith("SlingHttpServletRequest")) {
        continue;
      }
      Object[] parameterValue = parameter.getValue();
      Object parameterToStore;
      if (parameterValue.length == 1) {
        parameterToStore = parameterValue[0];
      } else {
        parameterToStore = parameterValue;
      }
      personPropertyMap.put(parameter.getKey(), parameterToStore);
    }
    return personPropertyMap;
  }

  /**
   * Returns an administrative session to the default workspace.
   *
   * @throws AccessDeniedException
   * @throws StorageClientException
   * @throws org.sakaiproject.nakamura.api.lite.ClientPoolException
   */
  private Session getSession() throws ClientPoolException, StorageClientException,
      AccessDeniedException {
    return getRepository().loginAdministrative();
  }

  /** Returns the JCR repository used by this service. */
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(justification = "OSGi Managed", value = { "UWF_UNWRITTEN_FIELD" })
  protected Repository getRepository() {
    return repository;
  }
}