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
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.resource.DateParser;
import org.sakaiproject.nakamura.api.people.BadRequestException;
import org.sakaiproject.nakamura.api.people.DefaultSakaiPerson;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.people.SakaiPerson;
import org.sakaiproject.nakamura.api.people.SakaiPersonService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.UserFinder;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component(metatype = true)
@Service
public class SparseMapPersonService implements SakaiPersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparseMapPersonService.class);

  private DateParser dateParser;

  @Property(label="Restricted name patterns",
      description="A regular expression string to check usernames against and report the conflict.",
      value=SparseMapPersonService.RESTRICTED_USERNAME_REGEX_DEFAULT)
  public static final String RESTRICTED_USERNAME_REGEX_PROPERTY = "restricted.username.regex";
  public static final String RESTRICTED_USERNAME_REGEX_DEFAULT  = "admin|administrator.*";
  protected Pattern restrictedUsernamePattern;

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
  public void updatePerson(String personId, String firstName, String lastName, String email, Map<String, Object[]> properties) {

    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      Authorizable person = authorizableManager.findAuthorizable(personId);
      for (Map.Entry<String, Object[]> property : properties.entrySet()) {
        if (property.getKey().endsWith("@Delete")) {
          person.removeProperty(property.getKey().substring(0, property.getKey().lastIndexOf("@Delete")));
        } else {
          if (property.getValue().length == 1) {
            person.setProperty(property.getKey(), property.getValue()[0]);
          } else {
            person.setProperty(property.getKey(), property.getValue());
          }
        }
      }
      authorizableManager.updateAuthorizable(person);
      try {
        postProcessorService.process(person, adminSession, ModificationType.MODIFY, properties);
      } catch (Exception e) {
        LOGGER.warn(e.getMessage(), e);
        throw new RuntimeException(e);
      }
      // Launch an OSGi event for updating a user.
      try {
        Dictionary<String, String> eventProps = new Hashtable<String, String>();
        eventProps.put(UserConstants.EVENT_PROP_USERID, personId);
        EventUtils.sendOsgiEvent(eventProps, UserConstants.TOPIC_USER_UPDATE, eventAdmin);
      } catch (Exception e) {
        LOGGER.error("Failed to launch an OSGi event for updating a user.", e);
      }
    } catch (AccessDeniedException e) {
      throw new RuntimeException();
    } catch (StorageClientException e) {
      throw new RuntimeException();
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

  @Override
  public SakaiPerson getPerson(String personId) {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      Authorizable person = authorizableManager.findAuthorizable(personId);

      return new DefaultSakaiPerson(person.getId(),
          (String) person.getProperty("firstName"),
          (String) person.getProperty("lastName"),
          (String) person.getProperty("email"),
          person.getOriginalProperties());
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e);
    } catch (ClientPoolException e) {
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deletePerson(String personId) {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      Authorizable person = authorizableManager.findAuthorizable(personId);
      authorizableManager.delete(personId);
      postProcessorService.process(person, adminSession, ModificationType.DELETE, Collections.<String, Object[]> emptyMap());

    } catch (AccessDeniedException e) {
      throw new RuntimeException();
    } catch (StorageClientException e) {
      throw new RuntimeException();
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(), e);
      throw new RuntimeException();
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

  @Override
  public boolean isPersonIdInUse(String personId) {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      if (userFinder.userExists(personId) || authorizableManager.findAuthorizable(personId) != null) {
        return true;
      }
      else {
        return restrictedUsernamePattern.matcher(personId).matches();
      }
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
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

  @Property(value={"sha1"})
  private static final String PROP_PASSWORD_DIGEST_ALGORITHM = "password.digest.algorithm";

  private static final String DEFAULT_PASSWORD_DIGEST_ALGORITHM = "sha1";

  private String passwordDigestAlgorithm = null;

  @Deactivate
  protected void deactivate(ComponentContext context) {
    dateParser = null;
    passwordDigestAlgorithm = null;
  }

  /**
   * Digest the given password using the configured digest algorithm
   *
   * @param pwd the value to digest
   * @return the digested value
   * @throws IllegalArgumentException
   */
  protected String digestPassword(String pwd) throws IllegalArgumentException {
    return digestPassword(pwd, passwordDigestAlgorithm);
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
    restrictedUsernamePattern = Pattern.compile(PropertiesUtil.toString(props.get(RESTRICTED_USERNAME_REGEX_PROPERTY),
        RESTRICTED_USERNAME_REGEX_DEFAULT), Pattern.CASE_INSENSITIVE);
    passwordDigestAlgorithm = PropertiesUtil.toString(props.get(PROP_PASSWORD_DIGEST_ALGORITHM), DEFAULT_PASSWORD_DIGEST_ALGORITHM);
  }

  @Override
  public SakaiPerson createPerson(String principalName, String firstName, String lastName, String email, String pwd, String pwdConfirm, Map<String, Object[]> parameters) {

    // check that the submitted parameter values have valid values.
    if (principalName == null) {
      throw new BadRequestException("User name was not submitted");
    }

    SparsePersonNameSanitizer san = new SparsePersonNameSanitizer(principalName);
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
      throw new RuntimeException();
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
   */
  private Session getSession() throws StorageClientException, AccessDeniedException {
    return getRepository().loginAdministrative();
  }

  /** Returns the JCR repository used by this service. */
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(justification = "OSGi Managed", value = { "UWF_UNWRITTEN_FIELD" })
  protected Repository getRepository() {
    return repository;
  }
}
