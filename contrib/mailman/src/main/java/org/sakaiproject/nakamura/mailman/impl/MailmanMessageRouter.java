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
package org.sakaiproject.nakamura.mailman.impl;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.LiteMessageRouter;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.mailman.MailmanManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component(inherit = true, immediate = true, label = "%mail.manager.router.label")
@Service
public class MailmanMessageRouter implements LiteMessageRouter {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailmanMessageRouter.class);
  
  @SuppressWarnings("unused")
  @Property(value = "The Sakai Foundation")
  private static final String SERVICE_VENDOR = "service.vendor";

  @SuppressWarnings("unused")
  @Property(value = "Manages Routing for group mailing lists.")
  private static final String SERVICE_DESCRIPTION = "service.description";

  @Reference
  private MailmanManager mailmanManager;
  
  public int getPriority() {
    return 1;
  }

  public void route(Content n, MessageRoutes routing) {
    LOGGER.info("Mailman routing message: " + n);
    List<MessageRoute> toRemove = new ArrayList<MessageRoute>();
    List<MessageRoute> toAdd = new ArrayList<MessageRoute>();
    for (MessageRoute route : routing) {
      if ("internal".equals(route.getTransport()) && route.getRcpt().startsWith("g-")) {
        LOGGER.info("Found an internal group message. Routing to SMTP");
        toRemove.add(route);
        toAdd.add(mailmanManager.generateMessageRouteForGroup(route.getRcpt()));
      }
    }
    routing.removeAll(toRemove);
    routing.addAll(toAdd);
  }

}
