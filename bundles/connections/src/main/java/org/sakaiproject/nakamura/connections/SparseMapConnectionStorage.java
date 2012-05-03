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
package org.sakaiproject.nakamura.connections;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.connections.*;
import org.sakaiproject.nakamura.api.lite.*;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Component
public class SparseMapConnectionStorage implements ConnectionStorage {

  @Reference
  protected Repository repository;

  @Override
  public ContactConnection getOrCreateContactConnection(Authorizable fromUser, Authorizable toUser)
      throws ConnectionException {
    String nodePath = ConnectionUtils.getConnectionPath(fromUser, toUser);
    Session session = null;
    try {
      session = repository.loginAdministrative();
      ContentManager contentManager = session.getContentManager();
      if (!contentManager.exists(nodePath)) {
        // Add auth name for sorting (KERN-1924)
        String firstName = "";
        String lastName = "";
        if (toUser.getProperty("firstName") != null) {
          firstName = (String) toUser.getProperty("firstName");
        }
        if (toUser.getProperty("lastName") != null) {
          lastName = (String) toUser.getProperty("lastName");
        }

        contentManager.update(new Content(nodePath, ImmutableMap.of("sling:resourceType",
            (Object) ConnectionConstants.SAKAI_CONTACT_RT,
            "reference", LitePersonalUtils.getProfilePath(toUser.getId()),
            "sakai:contactstorepath",
            ConnectionUtils.getConnectionPathBase(fromUser), "firstName",
            firstName,
            "lastName", lastName)));
      }
      return makeContactConnection(fromUser, toUser, contentManager.get(nodePath));
    } catch (Exception e) {
      throw new ConnectionException(500, e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          throw new ConnectionException(500, e);
        }
      }
    }
  }

  private ContactConnection makeContactConnection(Authorizable fromUser, Authorizable toUser, Content connectionContent) {
    if (fromUser == null || toUser == null || connectionContent == null) {
      return null;
    }
    ConnectionState connectionState = connectionContent.hasProperty(ConnectionConstants.SAKAI_CONNECTION_STATE) ?
        ConnectionState.valueOf((String) connectionContent.getProperty(ConnectionConstants.SAKAI_CONNECTION_STATE)) : ConnectionState.NONE;
    Set<String> connectionTypes = Sets.newHashSet(StorageClientUtils.nonNullStringArray((String[]) connectionContent.getProperty(ConnectionConstants.SAKAI_CONNECTION_TYPES)));
    Map<String, Object> additionalProperties = Maps.newHashMap();
    additionalProperties.putAll(connectionContent.getProperties());
    additionalProperties.remove(ConnectionConstants.SAKAI_CONNECTION_STATE);
    additionalProperties.remove(ConnectionConstants.SAKAI_CONNECTION_TYPES);
    additionalProperties.remove("firstName");
    additionalProperties.remove("lastName");
    return new ContactConnection(connectionState, connectionTypes, fromUser.getId(),
        toUser.getId(), (String)toUser.getProperty("firstName"), (String)toUser.getProperty("lastName"),
        additionalProperties);
  }

  @Override
  public void saveContactConnectionPair(ContactConnection thisNode, ContactConnection otherNode)
      throws ConnectionException {
    Session session = null;
    try {
      session = repository.loginAdministrative();
      ContentManager contentManager = session.getContentManager();
      contentManager.update(contentFromContactConnection(thisNode));
      contentManager.update(contentFromContactConnection(otherNode));
    } catch (Exception e) {
      throw new ConnectionException(500, e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          throw new ConnectionException(500, e);
        }
      }
    }
  }

  private Content contentFromContactConnection(ContactConnection contactConnection) {
    String resourceType = ConnectionConstants.SAKAI_CONTACT_RT;
    String contentPath = ConnectionUtils.getConnectionPath(contactConnection.getFromUserId(), contactConnection.getToUserId(), null);
    String connectionState = contactConnection.getConnectionState().toString();
    String[] connectionTypes = contactConnection.getConnectionTypes()
        .toArray(new String[contactConnection.getConnectionTypes().size()]);
    Map<String, Object> additionalProperties = contactConnection.getProperties();

    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    safePut(builder, additionalProperties, "sling:resourceType", resourceType);

    safePut(builder, additionalProperties, "reference", LitePersonalUtils.getProfilePath(contactConnection.getFromUserId()));
    safePut(builder, additionalProperties, "sakai:contactstorepath", ConnectionUtils.getConnectionPathBase(contactConnection.getFromUserId()));
    safePut(builder, additionalProperties, "firstName", contactConnection.getFirstName());
    safePut(builder, additionalProperties, "lastName", contactConnection.getLastName());
    safePut(builder, additionalProperties, ConnectionConstants.SAKAI_CONNECTION_STATE, connectionState);
    safePut(builder, additionalProperties, ConnectionConstants.SAKAI_CONNECTION_TYPES, connectionTypes);
    builder.putAll(additionalProperties);

    return new Content(contentPath, builder.build());
  }

  private void safePut(ImmutableMap.Builder<String, Object> builder, Map<String, Object> properties, String key, Object value) {
    if (!properties.containsKey(key)) {
      builder.put(key, value);
    }
  }

  @Override
  public ContactConnection getContactConnection(Authorizable thisUser, Authorizable otherUser) throws ConnectionException {
    String contentPath = ConnectionUtils.getConnectionPath(thisUser, otherUser, null);
    Session session = null;
    try {
      session = repository.loginAdministrative();
      ContentManager contentManager = session.getContentManager();
      return makeContactConnection(thisUser, otherUser, contentManager.get(contentPath));
    } catch (Exception e) {
      throw new ConnectionException(500, e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          throw new ConnectionException(500, e);
        }
      }
    }
  }

  @Override
  public List<String> getConnectedUsers(Session session, String userId, ConnectionState state) throws ConnectionException {
    List<String> connections = Lists.newArrayList();
    try {
      ContentManager contentManager = session.getContentManager();
      String path = ConnectionUtils.getConnectionPathBase(userId);
      Content content = contentManager.get(path);
      if (content != null) {
        for ( Content connection : content.listChildren() ) {
          String resourceType = (String) connection.getProperty("sling:resourceType");
          String connectionStateValue = (String) connection.getProperty(ConnectionConstants.SAKAI_CONNECTION_STATE);
          ConnectionState connectionState = ConnectionState.NONE;
          if (connectionStateValue != null ) {
            connectionState = ConnectionState.valueOf(connectionStateValue);
          }
          if ( ConnectionConstants.SAKAI_CONTACT_RT.equals(resourceType) && state.equals(connectionState)) {
            connections.add(StorageClientUtils.getObjectName(connection.getPath()));
          }
        }
      }
    } catch (StorageClientException e) {
      throw new IllegalStateException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    return connections;
  }
}
