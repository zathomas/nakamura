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
package org.sakaiproject.nakamura.user.servlet;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.user.BadRequestException;
import org.sakaiproject.nakamura.api.user.PermissionDeniedException;
import org.sakaiproject.nakamura.api.user.SakaiAuthorizationService;
import org.sakaiproject.nakamura.api.user.SakaiPersonService;
import org.sakaiproject.nakamura.api.user.UserConstants;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SlingServlet(paths = { "/system/changePassword" }, generateComponent = false, methods = { "POST" })
public class ChangeSakaiUserPasswordServlet extends SlingAllMethodsServlet {

  @Reference
  protected transient DynamicContentResponseCache responseCache;

  @Reference
  protected transient SakaiPersonService userService;

  @Reference
  protected transient SakaiAuthorizationService authorizationService;

  @Override
  public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      authorizationService.canChangeUserPassword(request.getRemoteUser(), request.getParameter("userId"));
      userService.changePersonAccountPassword(request.getParameter("userId"), request.getParameter("oldPwd"),
          request.getParameter("newPwd"), request.getParameter("newPwdConfirm"));
      responseCache.invalidate(UserConstants.USER_RESPONSE_CACHE, request.getParameter("userId"));
    } catch (PermissionDeniedException pde) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, pde.getLocalizedMessage());
    } catch (BadRequestException bre) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, bre.getLocalizedMessage());
    }
  }
}
