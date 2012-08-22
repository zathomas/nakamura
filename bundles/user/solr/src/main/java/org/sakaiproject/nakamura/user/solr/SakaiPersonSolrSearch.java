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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.SolrQuery;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchParameters;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.people.SakaiPerson;
import org.sakaiproject.nakamura.api.people.SakaiPersonSearchService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SakaiPersonSolrSearch implements SakaiPersonSearchService {

  @Reference
  SolrSearchServiceFactory searchServiceFactory;

  @Override
  public List<SakaiPerson> searchPeople(String searchUserId, String query,
                                        Set<String> tags, boolean alsoSearchProfile,
                                        String sortOn, SortOrder sortOrder,
                                        int limit, int offset) {
    List<SakaiPerson> results = Lists.newArrayList();
    PersonSearchQueryHandler personSearchQueryHandler = new PersonSearchQueryHandler();
    Map<String, String> searchParams = ImmutableMap.of(PersonSearchQueryHandler.REQUEST_PARAMS.q.toString(), query,
        PersonSearchQueryHandler.REQUEST_PARAMS.fullprofile.toString(), Boolean.toString(alsoSearchProfile));
    Query solrQuery = personSearchQueryHandler.getQuery(searchParams);
    try {
      SolrSearchResultSet resultSet = searchServiceFactory.getSearchResultSet(searchUserId, solrQuery,
          new SolrSearchParameters(offset, limit, SolrQuery.ORDER.valueOf(sortOrder.toString()), sortOn));
      for (Iterator<Result> iterator = resultSet.getResultSetIterator(); iterator.hasNext();) {
        Result result = iterator.next();
        results.add(personSearchQueryHandler.makePersonFromResult(result));
      }
    } catch (SolrSearchException e) {
      throw new RuntimeException(e);
    }
    return results;
  }
}
