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
package org.sakaiproject.nakamura.message.search;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Reference;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.search.UnreadMessageCountService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

public class UnreadMessageCountServiceImpl implements UnreadMessageCountService {

  protected static final Logger LOGGER = LoggerFactory.getLogger(UnreadMessageCountServiceImpl.class);

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  @Reference
  protected transient LiteMessagingService messagingService;

  @Override
  public long getUnreadMessageCount(SlingHttpServletRequest request) {
    final javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    final Session session = StorageClientUtils.adaptToSession(jcrSession);
    AuthorizableManager authzManager = null;
    Authorizable au = null;
    try {
      authzManager = session.getAuthorizableManager();
      au = authzManager.findAuthorizable(request.getRemoteUser());
    } catch (Exception e) {
      LOGGER.error("error getting Authorizable for remote user", e);
    }

    if (au == null) {
      return 0;
    }

    // We don't do queries for anonymous users. (Possible ddos hole).
    String userID = au.getId();
    if (UserConstants.ANON_USERID.equals(userID)) {
      return 0;
    }

    String store = messagingService.getFullPathToStore(au.getId(), session);
    store = ISO9075.encodePath(store);
    String queryString = "messagestore:" + ClientUtils.escapeQueryChars(store) + " AND type:internal AND messagebox:inbox AND read:false";
    final Map<String, Object> queryOptions = ImmutableMap.of(
       PARAMS_ITEMS_PER_PAGE, (Object) "0",
       CommonParams.START, "0"
    );
    Query query = new Query(queryString, queryOptions);
    LOGGER.debug("Submitting Query {} ", query);

    SolrSearchResultSet resultSet = null;
    try {
      resultSet = searchServiceFactory.getSearchResultSet(
          request, query, false);
    } catch (SolrSearchException e) {
      LOGGER.error("error executing query", e);
      return 0;
    }

    return resultSet.getSize();
  }

}
