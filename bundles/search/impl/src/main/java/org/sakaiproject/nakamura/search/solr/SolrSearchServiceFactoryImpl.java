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
package org.sakaiproject.nakamura.search.solr;

import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.ResultSetFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchParameters;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@Component
@Service
public class SolrSearchServiceFactoryImpl implements SolrSearchServiceFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchServiceFactoryImpl.class);

  @Reference(referenceInterface = ResultSetFactory.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)
  private ConcurrentMap<String, ResultSetFactory> resultSetFactories = Maps.newConcurrentMap();

  protected void bindResultSetFactories(ResultSetFactory factory, Map<?, ?> props) {
    String type = PropertiesUtil.toString(props.get("type"), null);
    if (!StringUtils.isBlank(type)) {
      resultSetFactories.put(type, factory);
    } else {
      LOGGER.warn("Unable to bind result set factory: no 'type' property [{}]", factory);
    }
  }

  protected void unbindResultSetFactories(ResultSetFactory factory, Map<?, ?> props) {
    String type = PropertiesUtil.toString(props.get("type"), null);
    if (!StringUtils.isBlank(type)) {
      resultSetFactories.remove(type);
    }
  }

  /**
   * @deprecated
   * @param request
   * @param query
   * @param asAnon
   * @return
   * @throws SolrSearchException
   */
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      Query query, boolean asAnon) throws SolrSearchException {
    SolrSearchResultSet rs = null;
    ResultSetFactory factory = resultSetFactories.get(query.getType());
    if (factory != null) {
      rs = factory.processQuery(request, query, asAnon);
    }
    return rs;
  }

  /**
   * @deprecated
   * @param request
   * @param query
   * @return
   * @throws SolrSearchException
   */
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      Query query) throws SolrSearchException {
    return getSearchResultSet(request, query, false);
  }

  public SolrSearchResultSet getSearchResultSet (String searchUserId,
     final Query query, final SolrSearchParameters params) throws SolrSearchException {
    SolrSearchResultSet rs = null;
    ResultSetFactory factory = resultSetFactories.get(Query.SOLR);
    if (factory != null) {
      rs = factory.processQuery(searchUserId, query, params);
    }
    return rs;
  }
}
