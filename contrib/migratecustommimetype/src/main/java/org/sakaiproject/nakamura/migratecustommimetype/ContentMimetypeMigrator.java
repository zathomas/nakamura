package org.sakaiproject.nakamura.migratecustommimetype;

import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.lite.content.InternalContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate=true, metatype=true)
@Service(value=PropertyMigrator.class)
public class ContentMimetypeMigrator implements PropertyMigrator {

  private static final Logger log = LoggerFactory.getLogger(ContentMimetypeMigrator.class);

  private static final String OLD_MIME_FIELD = "sakai:custom-mimetype";

  public boolean migrate(String rid, Map<String, Object> properties) {
      String contentMimetype = (String)properties.get(OLD_MIME_FIELD);
      if (contentMimetype != null){
    	  properties.put(InternalContent.MIMETYPE_FIELD, contentMimetype);
    	  properties.remove(OLD_MIME_FIELD);
          log.debug("Updated {} {} ",rid,contentMimetype);
          return true;
      }
      return false;
	}

}
