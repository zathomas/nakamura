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
package org.sakaiproject.nakamura.user.http;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.people.BadRequestException;
import org.sakaiproject.nakamura.api.people.PermissionDeniedException;
import org.sakaiproject.nakamura.api.people.SakaiAuthorizationService;
import org.sakaiproject.nakamura.api.people.SakaiPersonService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.parameters.ParameterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@SlingServlet(paths = { "/system/personUpdate" }, methods = { "POST" })
public class UpdateSakaiPersonServlet  extends SlingAllMethodsServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateSakaiPersonServlet.class);

  @Reference
  SakaiPersonService sakaiPersonService;

  @Reference
  SakaiAuthorizationService sakaiAuthorizationService;

  @Reference
  protected transient DynamicContentResponseCache responseCache;

  @Override
  public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
    throws ServletException, IOException {
    try {
      String personId = request.getParameter("personId");
      sakaiAuthorizationService.canModifySakaiPerson(request.getRemoteUser(), personId);
      String firstName = request.getParameter("firstName");
      String lastName = request.getParameter("lastName");
      String email = request.getParameter("email");
      Map<String, Object[]> parameters = ParameterMap.extractParameters(request);
      sakaiPersonService.updatePerson(personId, firstName, lastName, email, parameters);

      responseCache.invalidate(UserConstants.USER_RESPONSE_CACHE, personId);
    } catch (PermissionDeniedException e) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    } catch (BadRequestException bre) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

}
