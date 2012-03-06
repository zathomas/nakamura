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
package org.sakaiproject.nakamura.files.migrator;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Component(immediate = true, metatype = true)
@Properties(value = {
  @Property(name = "service.description", value = "Performs migrations as necessary on content."),
  @Property(name = "service.vendor", value = "The Sakai Foundation"),
  @Property(name = "filter.scope", value = "request", propertyPrivate = true),
  @Property(name = "filter.order", intValue = { 1000 }, propertyPrivate = true) })
public class FileMigratorFilter implements Filter {

  private static Logger LOGGER = LoggerFactory.getLogger(FileMigratorFilter.class);

  private Pattern pubspacePathPattern;

  @Reference(cardinality = ReferenceCardinality.OPTIONAL_UNARY, policy = ReferencePolicy.DYNAMIC)
  FileMigrationService fileMigrationService;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    pubspacePathPattern = Pattern.compile("^/~\\w+/(public/pubspace|private/privspace).*$");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    if(fileMigrationService != null
      && request instanceof SlingHttpServletRequest
      && isRequestForPrivspaceOrPubspace((HttpServletRequest) request)) {
      Content userSpaceContent = ((SlingHttpServletRequest)request).getResource().adaptTo(Content.class);
      if (fileMigrationService.fileContentNeedsMigration(userSpaceContent)) {
        fileMigrationService.migrateFileContent(userSpaceContent);
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
  }

  private boolean isRequestForPrivspaceOrPubspace(HttpServletRequest request) {
    Matcher matcher = pubspacePathPattern.matcher(request.getPathInfo());
    return "GET".equals(request.getMethod()) && matcher.matches();
  }
}
