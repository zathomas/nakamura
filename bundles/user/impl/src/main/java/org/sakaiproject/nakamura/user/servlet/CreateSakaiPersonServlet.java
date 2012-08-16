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

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidator;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidatorService;
import org.sakaiproject.nakamura.api.user.BadRequestException;
import org.sakaiproject.nakamura.api.user.PermissionDeniedException;
import org.sakaiproject.nakamura.api.user.SakaiAuthorizationService;
import org.sakaiproject.nakamura.api.user.SakaiPersonService;
import org.sakaiproject.nakamura.api.user.UntrustedRequestException;
import org.sakaiproject.nakamura.util.parameters.ParameterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@SlingServlet(paths = { "/api/people" }, methods = { "POST" })
@Properties(value = {
    @Property(name = "password.digest.algorithm", value = "sha1"),
    @Property(name = "servlet.post.dateFormats", value = {
        "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy" }),
    @Property(name = "self.registration.enabled", boolValue = true) })
public class CreateSakaiPersonServlet extends SlingAllMethodsServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(CreateSakaiPersonServlet.class);

  @Reference
  protected transient RequestTrustValidatorService requestTrustValidatorService;

  @Reference
  protected SakaiAuthorizationService sakaiAuthorizationService;

  @Reference
  protected SakaiPersonService sakaiPersonService;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse httpResponse)
      throws ServletException, IOException {
    try {
      boolean trustedRequest = false;
      String trustMechanism = request.getParameter(":create-auth");
      if (trustMechanism != null) {
        RequestTrustValidator validator = requestTrustValidatorService
            .getValidator(trustMechanism);
        trustedRequest = validator != null
            && validator.getLevel() >= RequestTrustValidator.CREATE_USER
            && validator.isTrusted(request);
      }
      if (!"admin".equals(request.getRemoteUser()) && !trustedRequest) {
        throw new UntrustedRequestException();
      }
      sakaiAuthorizationService.canCreateNewSakaiPerson(request.getRemoteUser());
      String principalName = request.getParameter(SlingPostConstants.RP_NODE_NAME);
      String pwd = request.getParameter("pwd");
      String pwdConfirm = request.getParameter("pwdConfirm");
      String firstName = request.getParameter("firstName");
      String lastName = request.getParameter("lastName");
      String email = request.getParameter("email");
      Map<String, Object[]> parameters = ParameterMap.extractParameters(request);
      sakaiPersonService.createPerson(principalName, firstName, lastName, email, pwd, pwdConfirm, parameters);
    } catch (BadRequestException bre) {
      httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, bre.getLocalizedMessage());
    } catch (UntrustedRequestException ure) {
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    } catch (PermissionDeniedException pde) {
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, pde.getLocalizedMessage());
    } catch (ResourceNotFoundException e) {
      httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND,
          e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Unhandled Exception while handling POST "
          + request.getResource().getPath() + " with "
          + getClass().getName(), e);
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
    }
  }

}
