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
package org.sakaiproject.nakamura.files.search;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.files.search.CollectionCountService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Service
public class CollectionCountServiceImpl implements CollectionCountService{

  protected static final Logger LOGGER = LoggerFactory.getLogger(CollectionCountServiceImpl.class);

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  @Reference
  protected LibraryContentQueryHandler queryHandler;

  @Override
  public long getCollectionCount(SlingHttpServletRequest request) {

    /*
      This logic is basically a truncated version of the logic preformed by SolrSearchServlet. Efforts are underway
      under KERN-2880 to decouple that logic so this sort of duplicate code/logic is unnecessary.
     */
    Query query;
    Map<String, String> propertiesMap = new HashMap<String, String>();

    propertiesMap.put("sortOn", "score");
    propertiesMap.put("sortOrder", "desc");
    propertiesMap.put("_q", "");

    queryHandler.loadUserProperties (request, propertiesMap);

    String queryString = propertiesMap.get("_q");

    Map<String, Object> optionsMap = ImmutableMap.of(
       SolrSearchConstants.PARAMS_ITEMS_PER_PAGE, (Object) "0"
    );

    query = new Query(queryString, optionsMap);

    try {
      SolrSearchResultSet rs = searchServiceFactory.getSearchResultSet(request, query);

      return rs.getSize();
    } catch (SolrSearchException e) {
      LOGGER.error("search for collection count failed", e);
    }

    return 0;
  }
}
