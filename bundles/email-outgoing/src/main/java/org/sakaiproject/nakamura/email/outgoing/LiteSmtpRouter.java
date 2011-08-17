/*
 * Licensed to the Sakai Foundation (SF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. The SF licenses this file to you
 * under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sakaiproject.nakamura.email.outgoing;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.AbstractMessageRoute;
import org.sakaiproject.nakamura.api.message.LiteMessageRouter;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;

@Service
@Component
public class LiteSmtpRouter implements LiteMessageRouter {
  private static final Logger LOG = LoggerFactory.getLogger(LiteSmtpRouter.class);

  /**
   * The Content Repository we access.
   *
   */
  @Reference
  private Repository contentRepository;

  @Reference
  private ProfileService profileService;

  @Reference
  private BasicUserInfoService basicUserInfo;

  @Reference
  private SlingRepository slingRepo;

  public LiteSmtpRouter() {
  }

  LiteSmtpRouter(Repository contentRepository, SlingRepository slingRepository, ProfileService profileService) {
    this.contentRepository = contentRepository;
    this.slingRepo = slingRepository;
    this.profileService = profileService;
  }

  public int getPriority() {
    return 0;
  }

  public void route(Content message, MessageRoutes routing) {
    Collection<MessageRoute> rewrittenRoutes = new ArrayList<MessageRoute>();
    Iterator<MessageRoute> routeIterator = routing.iterator();
    while (routeIterator.hasNext()) {
      MessageRoute route = routeIterator.next();
      String rcpt = route.getRcpt();
      String transport = route.getTransport();

      LOG.debug("Checking Message Route {} ",route);
      boolean rcptNotNull = rcpt != null;
      boolean transportNullOrInternal = transport == null || "internal".equals(transport);

      if (rcptNotNull && transportNullOrInternal) {
        // check the user's profile for message delivery preference. if the
        // preference is set to smtp, change the transport to 'smtp'.
        try {
          Session session = contentRepository.loginAdministrative();
          Authorizable au = session.getAuthorizableManager().findAuthorizable(rcpt);
          if (au != null) {
            String profilePath = LitePersonalUtils.getProfilePath(au.getId());
            Content profileNode = session.getContentManager().get(profilePath);
            boolean smtpPreferred = isPreferredTransportSmtp(profileNode);
            boolean smtpMessage = isMessageTypeSmtp(message);
            if (smtpPreferred || smtpMessage) {
              LOG.debug("Message is an SMTP Message, getting email address for the authorizable {}", au.getId());
              String rcptEmailAddress;
              if (au instanceof Group) {
                // Can just use the ID of the group, as the members will
                // be looked up and email sent to them
                // TODO: If a group can have an email address sometime in the
                //  future, remove this check
                rcptEmailAddress = au.getId();
              } else {
                rcptEmailAddress = getEmailAddress(au, session);
              }

              if (StringUtils.isBlank(rcptEmailAddress)) {
                LOG.warn("Can't find a primary email address for [{}]; smtp message will not be sent to authorizable.", rcpt);
              } else {
                AbstractMessageRoute smtpRoute = new AbstractMessageRoute(
                    MessageConstants.TYPE_SMTP + ":" + rcptEmailAddress) {
                };
                rewrittenRoutes.add(smtpRoute);
                routeIterator.remove();
              }
            }
          }
        } catch (RepositoryException e) {
          LOG.error(e.getMessage());
        } catch (ClientPoolException e) {
          LOG.error(e.getMessage());
        } catch (StorageClientException e) {
          LOG.error(e.getMessage());
        } catch (AccessDeniedException e) {
          LOG.error(e.getMessage());
        }
      }
    }
    routing.addAll(rewrittenRoutes);

    LOG.debug("Final Routing is [{}]", Arrays.toString(routing.toArray(new MessageRoute[routing.size()])));
  }

  private boolean isMessageTypeSmtp(Content message) throws RepositoryException {
    boolean isSmtp = false;

    if (message != null && message.hasProperty(MessageConstants.PROP_SAKAI_TYPE)) {
      String prop = (String) message.getProperty(MessageConstants.PROP_SAKAI_TYPE);
      isSmtp = MessageConstants.TYPE_SMTP.equals(prop);
    }


    return isSmtp;
  }

  private boolean isPreferredTransportSmtp(Content profileNode) throws RepositoryException {
    boolean prefersSmtp = false;

    if (profileNode != null) {
      // Get this user's preferred message transport
      String transport = LitePersonalUtils.getPreferredMessageTransport(profileNode);
      prefersSmtp = MessageConstants.TYPE_SMTP.equals(transport);
    }

    return prefersSmtp;
  }

  /**
   * Get the email address for a user.
   * <p>
   * There are places that are checked to improve efficiency when getting the email field.
   * Processing in this order should allow for more efficient look ups of the email
   * address in descending order.
   * <ol>
   * <li>If ProfileService.getEmailLocation() is blank, check BasicUserInfoService to see
   * if the field is in 'basic'. If so, retrieve directly from <code>user</code>.</li>
   * <li>Check if ProfileService.getEmailLocation() exists in ContentManager already (not
   * from an external provider). If so, retrieve from there.</li>
   * <li>Look up ProfileService.getEmailLocation() in the full profile
   * (ProfileService.getProfileMap(..)).</li>
   * </ol>
   *
   * @param user
   * @param session
   * @return
   * @throws RepositoryException
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private String getEmailAddress(Authorizable user, Session session) throws RepositoryException,
      AccessDeniedException, StorageClientException {
    String email = null;
    String emailLocationPath = profileService.getEmailLocation();

    // check the default location first. this gives a shortcut with a more efficient check
    // for the assumed case without having to go into a deeper lookup of the full profile
    if (StringUtils.isBlank(emailLocationPath)) {
      email = getEmailFromBasic(user);
    } else {
      String profilePath = LitePersonalUtils.getProfilePath(user.getId());
      String fieldNode = PathUtils.getParentReference(emailLocationPath);
      String fieldName = StorageClientUtils.getObjectName(emailLocationPath);
      ContentManager cm = session.getContentManager();

      // check if the path is available locally before checking the full profile
      String emailPath = profilePath + ("/".equals(fieldNode) ? "" : "/" + fieldNode);
      if (cm.exists(emailPath)) {
        email = getEmailFromProfile(session, emailPath, fieldName);
        if (email == null) {
          LOG.warn("Unable to find email address location: {}", emailLocationPath);
        }
      } else {
        email = getEmailFromFullProfile(user, emailLocationPath);
        if (email == null) {
          LOG.warn("Unable to find email address location in full profile: {}", emailLocationPath);
        }
      }
    }
    return email;
  }

  /**
   * Get the email field from the "basic" profile section (i.e. from the authorizable)
   *
   * @param user
   *          The user for whom to find an email address.
   * @return The email address from "basic" if it is set to be managed in "basic"
   *         according to the BasicUserInfoService. null otherwise.
   */
  private String getEmailFromBasic(Authorizable user) {
    String email = null;
    String[] basicFields = basicUserInfo.getBasicProfileElements();
    for (String basicField : basicFields) {
      if ("email".equals(basicField)) {
        email = String.valueOf(user.getProperty("email"));
        break;
      }
    }
    return email;
  }

  /**
   * Get the email field by checking the locally stored profile information. This does not
   * check "basic" or externally provided profile information.
   *
   * @param session
   * @param email
   * @param emailLocationPath
   * @param fieldName
   * @param emailPath
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private String getEmailFromProfile(Session session, String emailPath, String fieldName)
      throws StorageClientException, AccessDeniedException {
    String email = null;
    Content emailNode = session.getContentManager().get(emailPath);
    if (emailNode != null && emailNode.hasProperty(fieldName)) {
      email = String.valueOf(emailNode.getProperty(fieldName));
    }
    return email;
  }

  /**
   * Get the email field by searching through the full profile. This will check "basic"
   * (on authorizable), profile information stored locally and profile information
   * provided externally.
   *
   * @param user
   * @param emailLocationPath
   * @return
   * @throws RepositoryException
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @SuppressWarnings("unchecked")
  private String getEmailFromFullProfile(Authorizable user, String emailLocationPath)
      throws RepositoryException, StorageClientException, AccessDeniedException {
    String email = null;
    javax.jcr.Session jcrSession = null;
    try {
      jcrSession = slingRepo.loginAdministrative(null);
      // the profile service returns a map/tree that is like the json seen in /system/me, so we
      // have to break up the path and walk over the returned structure
      String[] emailLocation = StringUtils.split(emailLocationPath, '/');
      Map<String, Object> profile = profileService.getProfileMap(user, jcrSession);
      for (int i = 0; i < emailLocation.length; i++) {
        if (profile.containsKey(emailLocation[i])) {
          if (i == emailLocation.length - 1) {
            // looks like we've reached the last path element which should be the name of
            // the field where the value lives
            email = String.valueOf(profile.get(emailLocation[i]));
          } else {
            // set our profile reference to be the next segment down to keep digging
            profile = (Map<String, Object>) profile.get(emailLocation[i]);
          }
        }
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
    return email;
  }
}
