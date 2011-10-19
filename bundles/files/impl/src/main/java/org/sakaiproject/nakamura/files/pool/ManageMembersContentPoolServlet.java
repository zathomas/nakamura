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
package org.sakaiproject.nakamura.files.pool;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_MANAGER;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_VIEWER;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods = { "GET", "POST" }, resourceTypes = { "sakai/pooled-content" }, selectors = { "members" })
@Properties(value = {
  @Property(name = "service.vendor", value = "The Sakai Foundation"),
  @Property(name = "service.description", value = "Manages the Managers and Viewers for pooled content.") })
@ServiceDocumentation(name = "Manage Members Content Pool Servlet", okForVersion = "0.11",
  shortDescription = "List and manage the managers and viewers for a file in the content pool.",
  description = "List and manage the managers and viewers for a file in the content pool.",
  bindings = { @ServiceBinding(type = BindingType.TYPE, bindings = { "sakai/pooled-content" },
    selectors = {
      @ServiceSelector(name = "members", description = "Binds to the selector members."),
      @ServiceSelector(name = "detailed", description = "(optional) Provides more detailed profile information."),
      @ServiceSelector(name = "tidy", description = "(optional) Provides formatted JSON output.")
    })
  },
  methods = {
    @ServiceMethod(name = "GET",
      description = {
        "Retrieves a list of members for this pooled content item.",
        "<pre>curl http://localhost:8080/p/hESoXumAT.members.tidy.json</pre>",
        "<pre>{\n" +
          "    \"managers\": [{\n" +
          "        \"hash\": \"suzy\",\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {\n" +
          "                \"picture\": {\n" +
          "                    \"value\": \"{\\\"name\\\":\\\"256x256_tmp1309269939493.jpg\\\",\\\"_name\\\":\\\"tmp1309269939493.jpg\\\",\\\"_charset_\\\":\\\"utf-8\\\",\\\"selectedx1\\\":0,\\\"selectedy1\\\":3,\\\"selectedx2\\\":85,\\\"selectedy2\\\":88}\"\n" +
          "                },\n" +
          "                \"lastName\": {\n" +
          "                    \"value\": \"Queue\"\n" +
          "                },\n" +
          "                \"email\": {\n" +
          "                    \"value\": \"suzy@aeroplanesoftware.com\"\n" +
          "                },\n" +
          "                \"firstName\": {\n" +
          "                    \"value\": \"Suzy\"\n" +
          "                }\n" +
          "            }\n" +
          "        },\n" +
          "        \"rep:userId\": \"suzy\",\n" +
          "        \"userid\": \"suzy\",\n" +
          "        \"counts\": {\n" +
          "            \"contactsCount\": 0,\n" +
          "            \"membershipsCount\": 0,\n" +
          "            \"contentCount\": 3,\n" +
          "            \"countLastUpdate\": 1309287542572\n" +
          "        },\n" +
          "        \"sakai:excludeSearch\": false\n" +
          "    }],\n" +
          "    \"viewers\": [{\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {\n" +
          "                \"lastName\": {\n" +
          "                    \"value\": \"User\"\n" +
          "                },\n" +
          "                \"email\": {\n" +
          "                    \"value\": \"anon@sakai.invalid\"\n" +
          "                },\n" +
          "                \"firstName\": {\n" +
          "                    \"value\": \"Anonymous\"\n" +
          "                }\n" +
          "            }\n" +
          "        },\n" +
          "        \"rep:userId\": \"anonymous\"\n" +
          "    },\n" +
          "    {\n" +
          "        \"sakai:category\": null,\n" +
          "        \"sakai:group-description\": null,\n" +
          "        \"sakai:group-id\": \"everyone\",\n" +
          "        \"createdBy\": null,\n" +
          "        \"lastModified\": null,\n" +
          "        \"sakai:group-title\": null,\n" +
          "        \"created\": null,\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {}\n" +
          "        },\n" +
          "        \"lastModifiedBy\": null,\n" +
          "        \"groupid\": \"everyone\",\n" +
          "        \"counts\": {},\n" +
          "        \"sakai:excludeSearch\": false\n" +
          "    }]\n" +
          "}</pre>"
      },
      response = {
        @ServiceResponse(code = 200, description = "All processing finished successfully.  Output is in the JSON format."),
        @ServiceResponse(code = 500, description = "Any exceptions encountered during processing.")
      }),
    @ServiceMethod(name = "POST", description = "Manipulate the member list for a pooled content item.",
      parameters = {
        @ServiceParameter(name = ":manager", description = "Set the managers on the ACL of a file."),
        @ServiceParameter(name = ":viewer", description = "Set the viewers on the ACL of a file.")
      },
      response = {
        @ServiceResponse(code = 200, description = "All processing finished successfully."),
        @ServiceResponse(code = 401, description = "POST by anonymous user, or current user doesn't have permission to update this content."),
        @ServiceResponse(code = 500, description = "Any exceptions encountered during processing.")
      })
  })
  public class ManageMembersContentPoolServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = 3385014961034481906L;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ManageMembersContentPoolServlet.class);


  @Reference
  protected transient ProfileService profileService;
  @Reference
  protected transient BasicUserInfoService basicUserInfoService;
  @Reference
  protected transient SolrServerService solrSearchService;

  /**
   * Retrieves the list of members.
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      // Get hold of the actual file.
      Resource resource = request.getResource();
     javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
      Session session = resource.adaptTo(Session.class);

      AuthorizableManager am = session.getAuthorizableManager();
      AccessControlManager acm = session.getAccessControlManager();
      Content node = resource.adaptTo(Content.class);
      Authorizable thisUser = am.findAuthorizable(session.getUserId());

      if (!acm.can(thisUser, Security.ZONE_CONTENT, resource.getPath(), Permissions.CAN_READ)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      Map<String, Object> properties = node.getProperties();
      String[] managers = (String[]) properties
          .get(POOLED_CONTENT_USER_MANAGER);
      String[] viewers = (String[]) properties
          .get(POOLED_CONTENT_USER_VIEWER);


      boolean detailed = false;
      boolean tidy = false;
      for (String selector : request.getRequestPathInfo().getSelectors()) {
        if ("detailed".equals(selector)) {
          detailed = true;
        } else if ("tidy".equals(selector)) {
          tidy = true;
        }
      }

      // Loop over the sets and output it.
      ExtendedJSONWriter writer = new ExtendedJSONWriter(response.getWriter());
      writer.setTidy(tidy);
      writer.object();
      writer.key("managers");
      writer.array();
      for (String manager : StorageClientUtils.nonNullStringArray(managers)) {
        try {
          writeProfileMap(jcrSession, am, writer, manager, detailed);
        } catch (AccessDeniedException e) {
          LOGGER.debug("Skipping private manager [{}]", manager);
        }
      }
      writer.endArray();
      writer.key("viewers");
      writer.array();
      for (String viewer : StorageClientUtils.nonNullStringArray(viewers)) {
        try {
          writeProfileMap(jcrSession, am, writer, viewer, detailed);
        } catch (AccessDeniedException e) {
          LOGGER.debug("Skipping private viewer [{}]", viewer);
        }
      }
      writer.endArray();
      writer.endObject();
    } catch (JSONException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (StorageClientException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (RepositoryException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    }

  }

  private void writeProfileMap(javax.jcr.Session jcrSession, AuthorizableManager um,
      ExtendedJSONWriter writer, String user, boolean detailed)
      throws JSONException, AccessDeniedException, StorageClientException, RepositoryException {
    Authorizable au = um.findAuthorizable(user);
    if (au != null) {
      ValueMap profileMap = null;
      if (detailed) {
        profileMap = profileService.getProfileMap(au, jcrSession);
      } else {
        profileMap = new ValueMapDecorator(basicUserInfoService.getProperties(au));
      }
      if (profileMap != null) {
        writer.valueMap(profileMap);
      }
    } else {
      writer.object();
      writer.key("userid");
      writer.value(user);
      writer.endObject();
    }
  }

  /**
   * Manipulate the member list for this file.
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // Anonymous users cannot do anything.
    // This is just a safety check really, they SHOULD NOT even be able to get to this
    // point.
    if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
      response.sendError(SC_UNAUTHORIZED, "Anonymous users cannot manipulate content.");
      return;
    }
    boolean releaseSession = false;
    Session session = null;
    try {
      // Get the node.
      Resource resource = request.getResource();
      session = resource.adaptTo(Session.class);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      Authorizable thisUser = authorizableManager.findAuthorizable(session.getUserId());
      Content node = resource.adaptTo(Content.class);
      Map<String, Object> properties = node.getProperties();
      String[] managers = StorageClientUtils.nonNullStringArray((String[]) properties
          .get(POOLED_CONTENT_USER_MANAGER));
      String[] viewers = StorageClientUtils.nonNullStringArray((String[]) properties
          .get(POOLED_CONTENT_USER_VIEWER));

      Set<String> managerSet = null;
      if ( managers == null ) {
        managerSet = Sets.newHashSet();
      } else {
        managerSet = Sets.newHashSet(managers);
      }

      Set<String> viewersSet = null;
      if ( viewers == null ) {
        viewersSet = Sets.newHashSet();
      } else {
        viewersSet = Sets.newHashSet(viewers);
      }
      Set<String> managedGroupsSet = findMyManagedGroups(thisUser, authorizableManager);
      if (!canModify(accessControlManager, thisUser, node, request, managerSet, viewersSet, managedGroupsSet)
          && isRequestingNonPublicOperations(request)) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
      if (!isRequestingNonPublicOperations(request)
          || userOrGroupInTargetSet(request, thisUser, managerSet, viewersSet, managedGroupsSet)) {
        session = session.getRepository().loginAdministrative();
        releaseSession = true;
        accessControlManager = session.getAccessControlManager();
      }
      ContentManager contentManager = session.getContentManager();

      List<AclModification> aclModifications = Lists.newArrayList();

      for (String addManager : StorageClientUtils.nonNullStringArray(request.getParameterValues(":manager"))) {
        if ((addManager.length() > 0) && !managerSet.contains(addManager)) {
          managerSet.add(addManager);
          AclModification.addAcl(true, Permissions.CAN_MANAGE, addManager,
              aclModifications);
        }
      }

      for (String removeManager : StorageClientUtils.nonNullStringArray(request.getParameterValues(":manager@Delete"))) {
        if ((removeManager.length() > 0) && managerSet.contains(removeManager)) {
          managerSet.remove(removeManager);
          AclModification.removeAcl(true, Permissions.CAN_MANAGE, removeManager,
              aclModifications);
        }
      }

      for (String addViewer : StorageClientUtils.nonNullStringArray(request.getParameterValues(":viewer"))) {
        if ((addViewer.length() > 0) && !viewersSet.contains(addViewer)) {
          viewersSet.add(addViewer);
          AclModification.addAcl(true, Permissions.CAN_READ, addViewer, aclModifications);
        }
      }
      String[] removeViewers = StorageClientUtils.nonNullStringArray(request
          .getParameterValues(":viewer@Delete"));
      for (String removeViewer : removeViewers) {
        removeViewer = removeViewer.trim();
        // a user can only remove themselves or a group they manage from the viewer list
        if (viewersSet.contains(removeViewer)
            && (removeViewer.equals(thisUser.getId()) || managedGroupsSet
                .contains(removeViewer))) {
          viewersSet.remove(removeViewer);
          if (!managerSet.contains(removeViewer)) {
            AclModification.removeAcl(true, Permissions.CAN_READ, removeViewer,
                aclModifications);
          }
        }
      }

      node.setProperty(POOLED_CONTENT_USER_VIEWER,
          viewersSet.toArray(new String[viewersSet.size()]));
      node.setProperty(POOLED_CONTENT_USER_MANAGER,
          managerSet.toArray(new String[managerSet.size()]));
      LOGGER.debug("Set Managers to {}",Arrays.toString(managerSet.toArray(new String[managerSet.size()])));
      LOGGER.debug("Set Viewsers to {}",Arrays.toString(viewersSet.toArray(new String[managerSet.size()])));
      LOGGER.debug("ACL Modifications {}",Arrays.toString(aclModifications.toArray(new AclModification[aclModifications.size()])));

      contentManager.update(node);
      accessControlManager.setAcl(Security.ZONE_CONTENT, node.getPath(),
          aclModifications.toArray(new AclModification[aclModifications.size()]));

      response.setStatus(SC_OK);
    } catch (AccessDeniedException e) {
      LOGGER.error("Insufficient permissions to modify [{}] Cause:{}",
          request.getPathInfo(), e.getMessage());
      LOGGER.debug(e.getMessage(), e);
      response.sendError(SC_UNAUTHORIZED, "Could not set permissions.");
    } catch (StorageClientException e) {
      LOGGER.error("Could not set some permissions on [{}] Cause:{}",
          request.getPathInfo(), e.getMessage());
      LOGGER.debug("Cause: ", e);
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save content node.");
    } finally {
      if (releaseSession && session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("Unable to logout from administrative session.", e);
        }
      }
    }
  }

  // does thisUser have write permissions for content or are they a member of the target set
  // being operated on
  private boolean canModify(AccessControlManager accessControlManager,
      Authorizable thisUser, Content node, SlingHttpServletRequest request,
      Set<String> managerSet, Set<String> viewersSet, Set<String> managedGroupsSet) {
    boolean canModify = false;
    if (accessControlManager.can(thisUser, Security.ZONE_CONTENT, node.getPath(),
        Permissions.CAN_WRITE) || userOrGroupInTargetSet(request, thisUser, managerSet, viewersSet, managedGroupsSet)) {
      canModify = true;
    }
    return canModify;
  }

  // is thisUser a member of the target set or does a group belong
  // to the set of managed groups
  @SuppressWarnings("rawtypes")
  private boolean userOrGroupInTargetSet(SlingHttpServletRequest request,
      Authorizable thisUser, Set<String> managerSet, Set<String> viewersSet,
      Set<String> managedGroupsSet) {
    boolean userOrGroupInTargetSet = false;
    String userId = thisUser.getId();
    Map parameterMap = request.getParameterMap();
    if ((parameterMap.containsKey(":manager") || parameterMap
        .containsKey(":manager@Delete")) && managerSet.contains(userId)) {
      userOrGroupInTargetSet = true;
    } else if ((parameterMap.containsKey(":viewer") || parameterMap
        .containsKey(":viewer@Delete"))) {
      Set<String> managedGroupsSetIntersection = Sets.newHashSet(managedGroupsSet);
      // one or more of the viewers is a group managed by the user
      managedGroupsSetIntersection.retainAll(viewersSet);
      if (viewersSet.contains(userId) || managedGroupsSetIntersection.size() > 0) {
        userOrGroupInTargetSet = true;
      }
    }
    return userOrGroupInTargetSet;
  }


  private Set<String> findMyManagedGroups(Authorizable au,
      AuthorizableManager authorizableManager) {
    String userId = au.getId();
    Set<String> managedGroups = Sets.newHashSet();
    SolrServer solrServer = solrSearchService.getServer();
    StringBuilder querySB = new StringBuilder(
        "resourceType:authorizable AND type:g AND manager:").append(userId);
    SolrQuery solrQuery = new SolrQuery(querySB.toString());
    String groupId;
    QueryResponse response;
    try {
      response = solrServer.query(solrQuery);
      SolrDocumentList results = response.getResults();
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("with query {}, found {} groups managed by {}", new Object[] {
            solrQuery, results.size(), userId });
      for (Iterator iterator = results.iterator(); iterator.hasNext();) {
        SolrDocument solrDocument = (SolrDocument) iterator.next();
        groupId = (String) solrDocument.getFieldValue("id");
        managedGroups.add(groupId);
      }
    } catch (SolrServerException e) {
      LOGGER.warn(e.getMessage(), e);
    }
    if (LOGGER.isDebugEnabled()) LOGGER.debug("my managed groups: " + managedGroups);
    return managedGroups;
  }

  @SuppressWarnings("rawtypes")
  private boolean isRequestingNonPublicOperations(SlingHttpServletRequest request) {
    Map parameterMap = request.getParameterMap();
    return (parameterMap.containsKey(":manager")
        || parameterMap.containsKey(":manager@Delete")
        || parameterMap.containsKey(":viewer@Delete"));
  }


}
