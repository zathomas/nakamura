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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Provides default functionality for pooled-content search entry-points.
 */
@Component(componentAbstract=true, inherit=true)
public abstract class AbstractContentSearchQueryHandler extends DomainObjectSearchQueryHandler
    implements SolrSearchResultProcessor, SolrSearchPropertyProvider {
  
  private final static Logger LOGGER = LoggerFactory.getLogger(
      AbstractContentSearchQueryHandler.class);

  @Reference
  protected Repository repository;

  public AbstractContentSearchQueryHandler() {
    
  }

  public AbstractContentSearchQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory);
    this.repository = repository;
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#getSearchResultSet(org.apache.sling.api.SlingHttpServletRequest, org.sakaiproject.nakamura.api.search.solr.Query)
   */
  @Override
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    LOGGER.debug("Input Query configuration = {}", query);
    Map<String, String> parametersMap = loadParametersMap(request);
    configureQuery(parametersMap, query);
    return searchServiceFactory.getSearchResultSet(request, query);
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#writeResult(org.sakaiproject.nakamura.api.lite.Session, java.util.Map, org.apache.sling.commons.json.io.JSONWriter, org.sakaiproject.nakamura.api.search.solr.Result)
   */
  @Override
  public void writeResult(Session session, Map<String, String> parametersMap, JSONWriter jsonWriter, Result result)
      throws JSONException {
    String path = result.getPath();
    Content content;
    try {
      content = session.getContentManager().get(path);
      if (content != null) {
        jsonWriter.object();
        // This search defaults to traversing the full tree of hierarchical content,
        // equivalent to a selector of "infinity".
        int traversalDepth = -1;
        if (parametersMap.containsKey(REQUEST_PARAMETERS_PROPS._traversalDepth.toString())) {
          String traversalDepthValue = parametersMap.get(REQUEST_PARAMETERS_PROPS._traversalDepth.toString());
          try {
            traversalDepth = Integer.parseInt(traversalDepthValue);
          } catch (NumberFormatException e) {
            LOGGER.error(e.getMessage(), e);
          }
        }
        ExtendedJSONWriter.writeContentTreeToWriter(jsonWriter, content, true, traversalDepth);
        FileUtils.writeCommentCountProperty(content, session, jsonWriter, repository);
        jsonWriter.endObject();
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

}
