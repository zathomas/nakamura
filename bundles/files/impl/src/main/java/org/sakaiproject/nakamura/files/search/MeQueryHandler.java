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
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.files.search;

import com.google.common.base.Joiner;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.AuthorizableUtil;
import org.sakaiproject.nakamura.util.SparseUtils;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Service(value={ SolrSearchPropertyProvider.class, SolrSearchResultProcessor.class })
@Component(inherit=true)
@Properties(value={
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "Me"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "Me")
  })
public class MeQueryHandler extends AbstractContentSearchQueryHandler {

  private final static String Q_TEMPLATE = "(content:(%s) OR filename:(%<s) OR " +
  		"tag:(%<s) OR description:(%<s) OR ngram:(%<s) OR edgengram:(%<s) OR widgetdata(%<s))";
  private final static String ROLE_TEMPLATE = "(%s:(%s))";
  
  private final static Joiner JOINER_OR = Joiner.on(" OR ");
  
  public static enum SearchableRole {
    manager,
    editor,
    viewer
  }
  
  public static enum TEMPLATE_PARAMETERS {
    _q
  }
  
  public static enum REQUEST_PARAMETERS {
    q,
    role,
    userid,
    mimetype
  }

  public MeQueryHandler() {
    
  }
  
  public MeQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory, repository);
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest, java.util.Map)
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
    
    // verify that we can determine a userid
    String userid = getSearchParam(propertiesMap, REQUEST_PARAMETERS.userid.toString());
    if (userid == null) {
      userid = request.getRemoteUser();
      propertiesMap.put(REQUEST_PARAMETERS.userid.toString(), userid);
    }

    if (userid == null || userid.equals(User.ANON_USER)) {
      throw new IllegalArgumentException("Cannot search with anonymous user. " +
          "Must authenticate, or specify 'userid' parameter.");
    }
    
    // verify there is a valid role specified
    String roleStr = getSearchParam(propertiesMap, REQUEST_PARAMETERS.role.toString());
    if (roleStr != null) {
      try {
        SearchableRole.valueOf(roleStr);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Provided role parameter is not a valid role: "+roleStr);
      }
    } else {
      throw new IllegalArgumentException("Required parameter 'role' was not found.");
    }
    
    propertiesMap.put(TEMPLATE_PARAMETERS._q.toString(), configureQString(propertiesMap));

  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#getResourceTypeClause(java.util.Map)
   */
  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    /*
     * If there is a query string parameter 'q', then we need to search widget data content
     * for that match as well.
     */
    if (hasGeneralQuery(parametersMap)) {
      return "resourceType:(sakai/pooled-content OR sakai/widget-data)";
    } else {
      return "resourceType:sakai/pooled-content";
    }
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#refineQuery(java.util.Map, org.sakaiproject.nakamura.api.search.solr.Query)
   */
  @Override
  public void refineQuery(Map<String, String> parametersMap, Query query) {
    /*
     * If there is a query string 'q' specified, then we will also need to search on
     * widget data contents. Because of this, to avoid duplicate content (e.g, multiple
     * widgets of a pooled content item match on the content), we need to group by the
     * widget content "returnpath". See also the #getResourceTypeClause(Map) method to
     * see how the widget data resourceType is dynamically included in the query.
     */
    if (hasGeneralQuery(parametersMap)) {
      query.getOptions().put(GroupParams.GROUP, "true");
      query.getOptions().put(GroupParams.GROUP_FIELD, "returnpath");
    }
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#configureQString(java.util.Map)
   */
  @Override
  public StringBuilder refineQString(Map<String, String> parametersMap, StringBuilder queryString) {
    
    if (queryString.length() > 0) {
      queryString.append(" AND ");
    }
    
    buildSearchByRoleQuery(parametersMap, queryString);
    buildSearchByGeneralQuery(parametersMap, queryString);
    buildSearchByMimetype(parametersMap, queryString);
    
    return queryString;
  }

  /**
   * Apply the 'search by mimetype' filter to the lucene query string.
   * @param parametersMap
   * @param queryString
   */
  private void buildSearchByMimetype(Map<String, String> parametersMap,
      StringBuilder queryString) {
    String mimeType = getSearchParam(parametersMap, REQUEST_PARAMETERS.mimetype.toString());
    if (mimeType != null) {
      queryString.append(" AND mimeType:").append(ClientUtils.escapeQueryChars(mimeType));
    }
  }

  /**
   * Apply the 'search by general text' filter to the lucene query string.
   * 
   * @param parametersMap
   * @param queryString
   */
  private void buildSearchByGeneralQuery(Map<String, String> parametersMap,
      StringBuilder queryString) {
    String q = getSearchParam(parametersMap, REQUEST_PARAMETERS.q.toString());
    if (q != null) {
      queryString.append(" AND ");
      new Formatter(queryString).format(Q_TEMPLATE, ClientUtils.escapeQueryChars(q));
    }
  }

  /**
   * Apply the 'search by role' filter to the lucene query string.
   * 
   * @param parametersMap
   * @param queryString
   */
  private void buildSearchByRoleQuery(Map<String, String> parametersMap,
      StringBuilder queryString) {
    SearchableRole role = SearchableRole.valueOf(getSearchParam(parametersMap, REQUEST_PARAMETERS.role.toString()));
    String userid = getSearchParam(parametersMap, REQUEST_PARAMETERS.userid.toString());
    AuthorizableManager authorizableManager = null;
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      authorizableManager = adminSession.getAuthorizableManager();
      Authorizable au = authorizableManager.findAuthorizable(userid);
      List<Authorizable> groups = AuthorizableUtil.getUserFacingGroups(au, authorizableManager);
      groups.add(au);
      
      List<String> groupStrs = new ArrayList<String>(groups.size());
      for (Authorizable memberAuthz : groups) {
        groupStrs.add(ClientUtils.escapeQueryChars(memberAuthz.getId()));
      }
      
      new Formatter(queryString).format(ROLE_TEMPLATE, role.toString(), JOINER_OR.join(groupStrs));
      adminSession.logout();
    } catch (ClientPoolException e) {
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      throw new RuntimeException(e);
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

  /**
   * Determine whether or not general text is being queried on in this search (i.e., a 'q'
   * query string parameters was provided).
   * 
   * @param parametersMap
   * @return
   */
  private boolean hasGeneralQuery(Map<String, String> parametersMap) {
    return getSearchParam(parametersMap, REQUEST_PARAMETERS.q.toString()) != null;
  }
}
