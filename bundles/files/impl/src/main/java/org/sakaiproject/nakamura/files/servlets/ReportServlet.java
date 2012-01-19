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
package org.sakaiproject.nakamura.files.servlets;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SlingServlet(resourceTypes = { "report" })
public class ReportServlet extends SlingSafeMethodsServlet {
  private static final long ONE_WEEK = 1000 * 60 * 60 * 24 * 7;

  @Override
  protected void doGet(SlingHttpServletRequest request,
    SlingHttpServletResponse response) throws ServletException,
    IOException {
    Content content = request.getResource().adaptTo(Content.class);
    if (isExpired(content)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "That content is expired.");
    } else {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      try {
        ExtendedJSONWriter.writeContentTreeToWriter(jsonWriter, content, 0);
      } catch (JSONException e) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Trouble writing JSON for this report.");
      }
    }
  }

  private boolean isExpired(Content content) {
    return StorageClientUtils.toLong(content.getProperty("_created")) + ONE_WEEK < System.currentTimeMillis();
  }
}
