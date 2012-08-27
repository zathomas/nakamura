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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;

public abstract class AbstractOAEServlet extends SlingAllMethodsServlet {

  @Override
  public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    validateRequest(request);
    checkPermission(request.getRemoteUser(), request);
    setResponseHeaders(response);
    writeResponse(response.getWriter());
  }

  @Override
  public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    validateRequest(request);
    checkPermission(request.getRemoteUser(), request);
    setResponseHeaders(response);
    writeResponse(response.getWriter());
  }

  abstract void validateRequest(SlingHttpServletRequest request);

  abstract void checkPermission(String userId, SlingHttpServletRequest request);

  abstract void setResponseHeaders(SlingHttpServletResponse response);

  abstract void writeResponse(PrintWriter out);


}
