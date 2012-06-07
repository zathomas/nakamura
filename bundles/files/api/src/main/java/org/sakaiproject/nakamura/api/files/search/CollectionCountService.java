package org.sakaiproject.nakamura.api.files.search;

import org.apache.sling.api.SlingHttpServletRequest;

public interface CollectionCountService {

  public long getCollectionCount(SlingHttpServletRequest request);

}
