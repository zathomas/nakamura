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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import static org.sakaiproject.nakamura.api.resource.MoveCleaner.RESOURCE_TYPE;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActionRecord;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

@Component(specVersion = "1.1")
@Service(value = SparsePostOperation.class)
@Reference(name = "moveCleaners",
    cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
    policy = ReferencePolicy.DYNAMIC,
    referenceInterface = MoveCleaner.class,
    bind = "bindCleaner", unbind = "unbindCleaner")
@Property(name = "sling.post.operation", value = "move")
public class MoveOperation extends AbstractSparsePostOperation {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoveOperation.class);
  private static final String FORCE_PAR = "force";

  private ConcurrentMap<String, CopyOnWriteArrayList<MoveCleaner>> moveCleaners;
  private static final String DEST = ":dest";

  public MoveOperation() {
    moveCleaners = Maps.newConcurrentMap();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.servlets.HtmlResponse, org.sakaiproject.nakamura.api.lite.content.ContentManager, java.util.List, java.lang.String)
   */
  @Override
  public void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException {

    boolean force = Boolean.parseBoolean(request.getParameter(FORCE_PAR));
    String from = contentPath;
    String to = PathUtils.toUserContentPath(request.getParameter(DEST));
    List<ActionRecord> moves = contentManager.move(from, to, force);
    for (int i = 0; i < moves.size(); i++) {
      ActionRecord move = moves.get(i);
      changes.add(Modification.onMoved(move.getFrom(), move.getTo()));
    }

    // if there are cleaners, see if one can handle this content
    if (!moveCleaners.isEmpty()) {
      // get the content from the new location and clean it before returning
      Content toContent = contentManager.get(to);

      // get the resource type and find the matching cleaners
      String resourceType = String.valueOf(toContent.getProperty(RESOURCE_TYPE));
      List<MoveCleaner> cleaners = moveCleaners.get(resourceType);
      if (cleaners != null) {
        for (MoveCleaner cleaner : cleaners) {
          // consult each cleaner and collect the modifications
          changes.addAll(cleaner.clean(from, toContent, contentManager));
        }
      }
    }
  }

  // ---------- SCR integration ----------
  protected void bindCleaner(MoveCleaner cleaner, Map<?, ?> props) {
    String[] resourceTypes = PropertiesUtil.toStringArray(props.get(RESOURCE_TYPE));

    if (resourceTypes != null && resourceTypes.length > 0) {
      for (String resourceType : resourceTypes) {
        CopyOnWriteArrayList<MoveCleaner> resourceTypeCleaners = moveCleaners.get(resourceType);

        if (resourceTypeCleaners == null) {
          resourceTypeCleaners = new CopyOnWriteArrayList<MoveCleaner>();
        }
        if (resourceTypeCleaners.addIfAbsent(cleaner)) {
          moveCleaners.put(resourceType, resourceTypeCleaners);
        } else {
          LOGGER.info("Same service [{}] already bound for [{}]", cleaner, resourceType);
        }
      }
    } else {
      LOGGER.warn("Can't bind MoveCleaner without a resource type definition [{}]", cleaner);
    }
  }

  protected void unbindCleaner(MoveCleaner cleaner, Map<?, ?> props) {
    String[] resourceTypes = PropertiesUtil.toStringArray(props.get(RESOURCE_TYPE));

    if (resourceTypes != null && resourceTypes.length > 0) {
      for (String resourceType : resourceTypes) {
        List<MoveCleaner> resourceTypeCleaners = moveCleaners.get(resourceType);
        if (resourceTypeCleaners != null) {
          resourceTypeCleaners.remove(cleaner);
        }
      }
    }
  }
}
