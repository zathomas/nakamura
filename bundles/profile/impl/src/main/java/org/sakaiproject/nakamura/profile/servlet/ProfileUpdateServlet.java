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
package org.sakaiproject.nakamura.profile.servlet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.profile.ProfileConstants;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.resource.JSONResponse;
import org.sakaiproject.nakamura.api.resource.ResourceService;
import org.sakaiproject.nakamura.api.resource.lite.ResourceModifyOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparseContentResource;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostProcessor;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(bindings = {
  @ServiceBinding(type = BindingType.TYPE,
    bindings = {
    ProfileConstants.GROUP_PROFILE_RT, ProfileConstants.USER_PROFILE_RT },
    extensions = { @ServiceExtension(name = "json", description = "json format")
    })
  },
  methods = {
    @ServiceMethod(name = "POST", description = "Responds to POST method requests",
      parameters = {
        @ServiceParameter(name = ":operation", description = "This is usually 'import'"),
        @ServiceParameter(name = ":content",
          description = "The JSON representation of the profile to be written onto the target user or group profile. "
          + "If :operation=import then this parameter must be present.")
      },
      response = {
        @ServiceResponse(code = 200, description = "Responds with a 200 if the request was successful, the output is a json "
          + "tree of the profile with external references expanded."),
        @ServiceResponse(code = 404, description = "Responds with a 404 is the profile node cant be found, body contains no output"),
        @ServiceResponse(code = 400, description = "Bad request. If the :operation parameter is 'import' then the :content parameter must be present."),
        @ServiceResponse(code = 403, description = "Responds with a 403 if the user does not have permission to access the profile or part of it"),
        @ServiceResponse(code = 500, description = "Responds with a 500 on any other error")
      })
  },
  name = "Profile Update Servlet", okForVersion = "0.11",
  shortDescription = "Endpoint for POSTing changes to a user or group profile.",
  description = {
    "Servlet for writing changes to a user or group profile, via JSON content which is passed as a parameter."
    + "The actual update is delegated to the ProfileService."
  })
@SlingServlet(methods = { "POST" }, selectors = "profile", resourceTypes = { SparseContentResource.SPARSE_CONTENT_RT })
public class ProfileUpdateServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = -600556329959608324L;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ProfileUpdateServlet.class);

  private final List<ServiceReference> delayedPostOperations = new ArrayList<ServiceReference>();

  @Reference
  private ProfileService profileService;

  @Reference
  private ResourceService resourceService;
  private SparsePostOperation modifyOperation;

  @Reference(name = "postOperation", referenceInterface = SparsePostOperation.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)
  Map<String, SparsePostOperation> postOperations = new ConcurrentHashMap<String, SparsePostOperation>();

  private ComponentContext componentContext;

  @Override
  public void init() {
    // default operation: create/modify
    modifyOperation = resourceService.getDefaultSparsePostOperation(getServletContext());
  }
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      String operation = request.getParameter(":operation");
      if (operation == null) {
        operation = "";
      }
      if ( "import".equals(operation)) {
      String content = request.getParameter(":content");
      if (StringUtils.isBlank(content)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            ":content parameter is missing");
        return;
      }
      JSONObject json = new JSONObject(content);
      
      Resource resource = request.getResource();
      Session session = resource.adaptTo(Session.class);
      
      // defaults were chosen based on the original values used before getting
      // these from the request
      boolean replace = OsgiUtil.toBoolean(request.getParameter(":replace"), true);
      boolean replaceProperties = OsgiUtil.toBoolean(
          request.getParameter(":replaceProperties"), true);
      boolean removeTree = OsgiUtil.toBoolean(request.getParameter(":removeTree"),
          false);

      LOGGER.info("Got profile update {} ", json);
      String profilePath = PathUtils.toUserContentPath(resource.getPath());
      profilePath = PathUtils.getParentReference(profilePath) + "/"
          + PathUtils.lastElement(profilePath);
      profileService.update(session, profilePath, json, replace, replaceProperties,
          removeTree);

      response.setStatus(200);
      response.getWriter().write("Ok");
      } else {
        // prepare the response
        HtmlResponse htmlResponse = new JSONResponse();
        htmlResponse.setReferer(request.getHeader("referer"));
        Map<String,String> authzProperties = propertiesToUpdate(request, ImmutableSet.of("picture", "sakai:group-title", "sakai:group-description"));
        if (!authzProperties.isEmpty()) {
          final Resource resource = request.getResource();
          final Content targetContent = resource.adaptTo(Content.class);
          final Session session = resource.adaptTo(Session.class);
          String authId = PathUtils.getAuthorizableId(targetContent.getPath());
          AuthorizableManager am = session.getAuthorizableManager();
          Authorizable au = am.findAuthorizable(authId);
          for (String key : authzProperties.keySet()) {
            au.setProperty(key, authzProperties.get(key));
          }
          am.updateAuthorizable(au);
        }
        if (postOperations.containsKey(operation)) {
          postOperations.get(operation).run(request, htmlResponse, new SparsePostProcessor[]{});
        } else {
          modifyOperation.run(request, htmlResponse, new SparsePostProcessor[]{});
        }
        // check for redirect URL if processing succeeded
        if (htmlResponse.isSuccessful()) {
          String redirect = getRedirectUrl(request, htmlResponse);
          if (redirect != null) {
            response.sendRedirect(redirect);
            return;
          }
        }

        // create a html response and send if unsuccessful or no redirect
        htmlResponse.send(response, isSetStatus(request));
      }
    } catch (JSONException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
      return;
    }

  }

  private Map<String, String> propertiesToUpdate(SlingHttpServletRequest request,
      ImmutableSet<String> propNames) {
    Map<String, String> rv = Maps.newHashMap();
    for (String propName : propNames) {
      String propValue = request.getParameter(propName);
      if (propValue != null) {
        rv.put(propName, propValue);
      }
    }
    return rv;
  }
  /**
   * compute redirect URL (SLING-126)
   *
   * @param ctx
   *          the post processor
   * @return the redirect location or <code>null</code>
   */
  protected String getRedirectUrl(HttpServletRequest request, HtmlResponse ctx) {
    // redirect param has priority (but see below, magic star)
    String result = request.getParameter(SlingPostConstants.RP_REDIRECT_TO);
    if (result != null && ctx.getPath() != null) {

      // redirect to created/modified Resource
      int star = result.indexOf('*');
      if (star >= 0) {
        StringBuffer buf = new StringBuffer();

        // anything before the star
        if (star > 0) {
          buf.append(result.substring(0, star));
        }

        // append the name of the manipulated node
        buf.append(ResourceUtil.getName(ctx.getPath()));

        // anything after the star
        if (star < result.length() - 1) {
          buf.append(result.substring(star + 1));
        }

        // use the created path as the redirect result
        result = buf.toString();

      } else if (result.endsWith(SlingPostConstants.DEFAULT_CREATE_SUFFIX)) {
        // if the redirect has a trailing slash, append modified node
        // name
        result = result.concat(ResourceUtil.getName(ctx.getPath()));
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Will redirect to " + result);
      }
    }
    return result;
  }

  protected boolean isSetStatus(SlingHttpServletRequest request) {
    String statusParam = request.getParameter(SlingPostConstants.RP_STATUS);
    if (statusParam == null) {
      LOGGER.debug("getStatusMode: Parameter {} not set, assuming standard status code",
          SlingPostConstants.RP_STATUS);
      return true;
    }

    if (SlingPostConstants.STATUS_VALUE_BROWSER.equals(statusParam)) {
      LOGGER.debug("getStatusMode: Parameter {} asks for user-friendly status code",
          SlingPostConstants.RP_STATUS);
      return false;
    }

    if (SlingPostConstants.STATUS_VALUE_STANDARD.equals(statusParam)) {
      LOGGER.debug("getStatusMode: Parameter {} asks for standard status code",
          SlingPostConstants.RP_STATUS);
      return true;
    }

    LOGGER.debug(
        "getStatusMode: Parameter {} set to unknown value {}, assuming standard status code",
        SlingPostConstants.RP_STATUS);
    return true;
  }

  protected void bindPostOperation(ServiceReference ref) {
    synchronized (this.delayedPostOperations) {
      if (this.componentContext == null) {
        this.delayedPostOperations.add(ref);
      } else {
        this.registerPostOperation(ref);
      }
    }
  }

  protected void registerPostOperation(ServiceReference ref) {
    String operationName = (String) ref
        .getProperty(SparsePostOperation.PROP_OPERATION_NAME);
    SparsePostOperation operation = (SparsePostOperation) this.componentContext
        .locateService("postOperation", ref);
    if (operation != null) {
      synchronized (this.postOperations) {
        this.postOperations.put(operationName, operation);
      }
    }
  }

  protected void unbindPostOperation(ServiceReference ref) {
      String operationName = (String) ref
          .getProperty(SparsePostOperation.PROP_OPERATION_NAME);
      synchronized (this.postOperations) {
        this.postOperations.remove(operationName);
      }
  }

  @Activate
  protected void activate(ComponentContext context) {
    this.componentContext = context;
    synchronized (this.delayedPostOperations) {
      for (final ServiceReference ref : this.delayedPostOperations) {
        this.registerPostOperation(ref);
      }
      this.delayedPostOperations.clear();
    }
  }

}
