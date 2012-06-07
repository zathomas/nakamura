package org.sakaiproject.nakamura.files.search;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.files.search.CollectionCountService;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.templates.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class CollectionCountServiceImpl implements CollectionCountService{

  protected static final String COLLECTION_COUNT_QUERY =
     "resourceType:sakai/pooled-content AND ((manager:(${au}) OR editor:(${au}) OR viewer:(${au})) OR (showalways:true AND (manager:(${all}) OR editor:(${all}) OR viewer:(${all}))))${_q}";

  protected static final Logger LOGGER = LoggerFactory.getLogger(CollectionCountServiceImpl.class);

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  @Reference
  protected TemplateService templateService;

  @Reference
  protected MeManagerViewerSearchPropertyProvider propProvider;

  @Override
  public long getCollectionCount(SlingHttpServletRequest request) {

    Query query;
    Map<String, String> propertiesMap = new HashMap<String, String>();

    propertiesMap.put("sortOn", "score");
    propertiesMap.put("sortOrder", "desc");
    propertiesMap.put("_q", "");

    propProvider.loadUserProperties(request, propertiesMap);

    String queryString = templateService.evaluateTemplate(propertiesMap, COLLECTION_COUNT_QUERY);

     // check for any missing terms & process the query template
    Collection<String> missingTerms = templateService.missingTerms(queryString);
    if (!missingTerms.isEmpty()) {
      throw new MissingParameterException(
          "Your request is missing parameters for the template: "
              + StringUtils.join(missingTerms, ", "));
    }

    Map<String, Object> optionsMap = new HashMap<String, Object>();

    optionsMap.put("sort", "${sortOn} ${sortOrder}");
    optionsMap.put("facet", true);
    optionsMap.put("facet.field", "tagname");
    optionsMap.put("facet.mincount", 1);
    optionsMap.put("items", 0);
    optionsMap.put("page", 0);

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
