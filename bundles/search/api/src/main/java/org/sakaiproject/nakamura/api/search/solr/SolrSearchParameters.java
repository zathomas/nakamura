package org.sakaiproject.nakamura.api.search.solr;

import com.google.common.collect.ImmutableMap;
import org.apache.solr.client.solrj.SolrQuery;

import java.util.HashMap;
import java.util.Map;

public class SolrSearchParameters {

  /* DRG TODO - must honor logic from SolrResultSetFactory
    private long[] getOffsetAndSize(SlingHttpServletRequest request,
      final Map<String, Object> options) {
    long nitems;
    if (options != null && options.get(PARAMS_ITEMS_PER_PAGE) != null) {
      nitems = Long.parseLong(String.valueOf(options.get(PARAMS_ITEMS_PER_PAGE)));
    } else {
      nitems = SolrSearchUtil.longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
          DEFAULT_PAGED_ITEMS);
    }
    long page;
    if (options != null && options.get(PARAMS_PAGE) != null) {
      page = Long.parseLong(String.valueOf(options.get(PARAMS_PAGE)));
    } else {
      page = SolrSearchUtil.longRequestParameter(request, PARAMS_PAGE, 0);
    }
    long offset = page * nitems;
    return new long[]{offset, nitems };
  }


   */
  private long page;
  private long recordsPerPage;
//  private int depth;
  private SolrQuery.ORDER order = SolrQuery.ORDER.asc;
  private String path;
  private String sortOn;
//  private Map<String, Object> queryOptions = new HashMap<String, Object>();

  public SolrSearchParameters() {
    page = 0;
    recordsPerPage = Integer.MAX_VALUE;
//    depth = Integer.MAX_VALUE;
  }

  public SolrSearchParameters(long page, long recordsPerPage, /*int depth,*/ SolrQuery.ORDER order, String sortOn) {
    this.page = page;
    this.recordsPerPage = recordsPerPage;
//    this.depth = depth;
    this.order = order;
    this.sortOn = sortOn;

  }

/*
  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }
*/
  public long getRecordsPerPage() {
    return recordsPerPage;
  }

  public void setRecordsPerPage(long recordsPerPage) {
    this.recordsPerPage = recordsPerPage;
  }

  public long getPage() {
    return page;
  }

  public void setPage(long page) {
    this.page = page;
  }

  /*
    / **
   * @param solrQuery
   * @param val
   * /
  private void parseSort(SolrQuery solrQuery, String val) {
    / * disable KERN-1855 for now; needs more discussion. * /
    // final String[] sortFields = solrQuery.getSortFields();
    // we were using setSortField, now using addSortField; verify state
    // if (sortFields != null && sortFields.length > 0) {
    // throw new IllegalStateException("Expected zero sort fields, found: " + sortFields);
    // }
    // final String[] criteria = val.split(",");
    // for (final String criterion : criteria) {
    // final String[] sort = StringUtils.split(criterion);
    final String[] sort = StringUtils.split(val);

    // use the *_sort fields to have predictable sorting.
    // many of the fields in the index have a lot of processing which
    // causes sorting to yield unpredictable results.
    String sortOn = ("score".equals(sort[0])) ? sort[0] : sort[0] + "_sort";
    switch (sort.length) {
    case 1:
      // solrQuery.addSortField(sort[0], ORDER.asc);
      solrQuery.setSortField(sortOn, SolrQuery.ORDER.asc);
      break;
    case 2:
      String sortOrder = sort[1].toLowerCase();
      SolrQuery.ORDER o = SolrQuery.ORDER.asc;
      try {
        o = SolrQuery.ORDER.valueOf(sortOrder);
      } catch (IllegalArgumentException a) {
        if (sortOrder.startsWith("d")) {
          o = SolrQuery.ORDER.desc;
        } else {
          o = SolrQuery.ORDER.asc;
        }
      }
      // solrQuery.addSortField(sort[0], o);
      solrQuery.setSortField(sortOn, o);
      break;
    default:
      LOGGER.warn("Expected the sort option to be 1 or 2 terms. Found: {}", val);
    }
    // }
  }

   */
  /*  DRG

          if (CommonParams.SORT.equals(key)) {
            parseSort(solrQuery, String.valueOf(val));
          } else
  */
  public SolrQuery.ORDER getOrder() {
    return order;
  }

  public void setOrder(SolrQuery.ORDER order) {
    this.order = order;
  }

  public String getSortOn() {
    return sortOn;
  }

  public void setSortOn(String sortOn) {
    this.sortOn = sortOn;
  }

/*
  public void putOption (String key, Object value) {
    queryOptions.put(key, value);
  }

  public Object getOption (String key) {
    return queryOptions.get(key);
  }

  public void setQueryOptions (Map<String, Object> options) {
    queryOptions.clear();
    if (options != null) {
      queryOptions.putAll(options);
    }
  }

  public Map<String, Object> getQueryOptions () {
    final ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>();

    builder.putAll(queryOptions);

    return builder.build();
  }
*/
  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

}
