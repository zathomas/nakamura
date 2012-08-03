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
package org.sakaiproject.nakamura.user.lite.servlet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessor;
import org.sakaiproject.nakamura.user.lite.resource.LiteAuthorizableResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;



@Component(immediate=true, metatype=true)
@Service(value=LiteAuthorizablePostProcessService.class)
public class LiteAuthorizablePostProcessServiceImpl implements LiteAuthorizablePostProcessService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteAuthorizablePostProcessServiceImpl.class);

  @Reference(target="(default=true)")
  protected LiteAuthorizablePostProcessor defaultPostProcessor;

  public LiteAuthorizablePostProcessServiceImpl() {
  }

  public void process(Authorizable authorizable, Session session,
      ModificationType change, Map<String, Object[]> parameters) throws Exception {

    final String pathPrefix = (authorizable instanceof Group) ?
        LiteAuthorizableResourceProvider.SYSTEM_USER_MANAGER_GROUP_PREFIX :
          LiteAuthorizableResourceProvider.SYSTEM_USER_MANAGER_USER_PREFIX;
    Modification modification = new Modification(change, pathPrefix + authorizable.getId(), null);

    LOGGER.debug("responding to modification event for authorizable: {}", authorizable.getId());
    defaultPostProcessor.process(authorizable, session, modification, parameters);
    }


}
