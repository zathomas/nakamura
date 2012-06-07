package org.sakaiproject.nakamura.api.message.search;

import org.apache.sling.api.SlingHttpServletRequest;

public interface UnreadMessageCountService {

  public long getUnreadMessageCount (SlingHttpServletRequest request);

}
