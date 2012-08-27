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
package org.sakaiproject.nakamura.api.search.solr;

import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;

public interface SolrSearchServiceFactory {

  /**
   * @deprecated
   *
   * @param request
   * @param query
   * @param asAnon
   * @return
   * @throws SolrSearchException
   */
  SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query,
      boolean asAnon) throws SolrSearchException;

  /**
   * @deprecated
   * @param request
   * @param query
   * @return
   * @throws SolrSearchException
   */
  SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query)
      throws SolrSearchException;

  public SolrSearchResultSet getSearchResultSet (String searchUserId, Query query,
     SolrSearchParameters params) throws SolrSearchException;

}
