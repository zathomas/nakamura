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
package org.sakaiproject.nakamura.user.solr;

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.SolrQuery;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchParameters;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.SakaiPerson;
import org.sakaiproject.nakamura.api.user.SakaiPersonSearchService;
import org.sakaiproject.nakamura.util.SparseUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SakaiPersonSolrSearch implements SakaiPersonSearchService {

  @Reference
  SolrSearchServiceFactory searchServiceFactory;

  @Reference
  Repository repository;

  @Override
  public List<SakaiPerson> searchPeople(String query, Set<String> tags, boolean alsoSearchProfile, String sortOn, SortOrder sortOrder, int limit, int offset) {
    List<SakaiPerson> results = Lists.newArrayList();
    Query solrQuery = makeSolrQuery(query, tags, alsoSearchProfile);
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      Authorizable dude = authorizableManager.findAuthorizable("dude");
      SolrSearchResultSet resultSet = searchServiceFactory.getSearchResultSet(adminSession, dude, solrQuery, new SolrSearchParameters(offset, limit, SolrQuery.ORDER.valueOf(sortOrder.toString()), sortOn));
      for (Iterator<Result> iterator = resultSet.getResultSetIterator(); iterator.hasNext();) {
        Result result = iterator.next();
        results.add(makeSakaiPerson(result));
      }
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      throw new RuntimeException(e);
    } catch (SolrSearchException e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
    return results;
  }

  private Query makeSolrQuery(String query, Set<String> tags, boolean alsoSearchProfile) {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  private SakaiPerson makeSakaiPerson(Result searchResult) {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }
}
