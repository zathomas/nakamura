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
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Component(inherit=true)
@Service(value={ SolrSearchResultProcessor.class, SolrSearchPropertyProvider.class })
@Properties(value={
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "MyLibrary"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "MyLibrary")
  })
public class MyLibraryQueryHandler extends AbstractContentSearchQueryHandler {

  /**
   * The maximum number of steps to recurse when looking for principals.
   */
  private static final int PRINCIPAL_MAX_DEPTH = 6;

  public enum TEMPLATE_PROPS {
    _q,
    _all,
    _au
  }
  
  public enum REQUEST_PARAMS {
    q,
    mimetype,
    levels,
    userid
  }
  
  public MyLibraryQueryHandler() {
    
  }
  
  public MyLibraryQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory, repository);
  }
  
  /**
   * This method will aggregate properties into the {@code propertiesMap} that will feed into
   * the template located in {@code auth-all.json}.
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request, Map<String, String> propertiesMap) {
    propertiesMap.put(TEMPLATE_PROPS._q.toString(), configureQString(propertiesMap));

    final String user = SearchRequestUtils.getUser(request);
    if (User.ANON_USER.equals(user)) {
      // stop here, anonymous is not a manager or a viewer of anything
      return;
    }
    
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    Session session = StorageClientUtils.adaptToSession(jcrSession);
    
    // determine the number of levels deep to look
    int levels = 0;
    try {
      if (request.getParameter(REQUEST_PARAMS.levels.toString()) != null) {
        levels = Math.min(Integer.parseInt(request.getParameter(REQUEST_PARAMS.levels.toString())),
            PRINCIPAL_MAX_DEPTH);
      }
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException("The levels parameter must be parseable to an integer.");
    }
    
    // find all groups that the user is "closely" related to
    final Set<String> principals = SearchRequestUtils.getPrincipals(session, user, levels);
    if (!principals.isEmpty()) {
      propertiesMap.put(TEMPLATE_PROPS._au.toString(), Joiner.on(" OR ").join(principals));
    }

    // find all groups that the user is related to
    final Set<String> allPrincipals = SearchRequestUtils.getPrincipals(session, user, PRINCIPAL_MAX_DEPTH);
    if (!allPrincipals.isEmpty()) {
      propertiesMap.put(TEMPLATE_PROPS._all.toString(), Joiner.on(" OR ").join(allPrincipals));
    }
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#getResourceTypeClause(java.util.Map)
   */
  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    return "resourceType:sakai/pooled-content";
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#refineQString(java.util.Map, java.lang.StringBuilder)
   */
  @Override
  public StringBuilder refineQString(Map<String, String> parametersMap,
      StringBuilder qBuilder) {
    String qParam = getSearchParam(parametersMap, REQUEST_PARAMS.q.toString());
    String mimeTypeParam = getSearchParam(parametersMap, REQUEST_PARAMS.mimetype.toString());
    
    List<String> andTerms = new LinkedList<String>();

    if (mimeTypeParam != null) {
      andTerms.add("mimeType:"+ClientUtils.escapeQueryChars(mimeTypeParam));
    }
    
    if (qParam != null) {
      andTerms.add("general:"+ClientUtils.escapeQueryChars(qParam));
    }
    
    if (!andTerms.isEmpty()) {
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      qBuilder.append(Joiner.on(" AND ").join(andTerms));
    }
    
    return qBuilder;
  }

}
