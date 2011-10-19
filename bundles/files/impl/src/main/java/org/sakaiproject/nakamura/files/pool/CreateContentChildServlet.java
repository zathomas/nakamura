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
package org.sakaiproject.nakamura.files.pool;

import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_FILENAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_NEEDS_PROCESSING;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.jackrabbit.JackrabbitSparseUtils;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods ={"POST"}, resourceTypes = { "sakai/pooled-content" }, selectors = { "resource" })
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Allows for uploading resource files into a pool content item.") })
@ServiceDocumentation(name = "Create Content Pool Resource Child Servlet", okForVersion = "0.11",
    description = "Creates and Updates resource children (e.g. metadata, supporting files, or alternate versions) attached to an existing Content Pool Item",
    shortDescription = "Creates and Updates resource children in the pool",
    bindings = @ServiceBinding(type=BindingType.TYPE, bindings={"sakai/pooled-content"},
      selectors={@ServiceSelector(name="resoure"), @ServiceSelector(name="optional resource ID for updating")},
      extensions=@ServiceExtension(name="*", description="If an extension is provided it is assumed to be the  resourceID which is to be updated.")),
    methods = @ServiceMethod(name="POST",
      description={ "A normal file post. If this is to create files, each file in the multipart file will create a new child resource associated with the Pool Content Item. If a resource ID is supplied only the first file in the upload is used to overwrite the file.",
        "Example<br>" +
        "<pre>A Multipart file upload to http://localhost:8080/p/23d8Jaw.resource will create one Resource file per file in the upload</pre>",
        "Example<br>" +
        "<pre>A Multipart file upload to http://localhost:8080/system/pool/23d8Jaw.resource.3sd23a4QW4WD will update the resource identified by 3sd23a4QW4WD in the Pool Content Item 23d8Jaw  </pre>",
        "Response is of the form " +
        "<pre>" +
        "   { \"file1\" : \"3sd23a4QW4WD\", \"file2\" : \"3sd23a4QW4ZS\" } " +
        "</pre>"
        },
      response={
        @ServiceResponse(code=201,description="Where files are created"),
        @ServiceResponse(code=400,description="Where the request is invalid"),
        @ServiceResponse(code=200,description="Where the file is updated"),
        @ServiceResponse(code=500,description="Failure with HTML explanation.")}

      ))

public class CreateContentChildServlet extends SlingAllMethodsServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 2914462192836863764L;

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateContentChildServlet.class);

  @Reference
  protected ClusterTrackingService clusterTrackingService;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    String userId = request.getRemoteUser();

    // Anonymous users cannot upload files.
    if (UserConstants.ANON_USERID.equals(userId)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN,
          "Anonymous users cannot upload files to the pool.");
      return;
    }
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
    Session session;
    try {
      session = JackrabbitSparseUtils.getSparseSession(jcrSession);
    } catch (RepositoryException e) {
      throw new ServletException(e.getMessage(), e);
    }
    
    RequestPathInfo rpi = request.getRequestPathInfo();
    String[] selectors = rpi.getSelectors();
    String resourceId = null;
    String alternativeStream = null;
    // TODO: check that we have the order of poolID and StreamID correct.
    // I think its /p/<poolID>.resource.<resourceID>
    // and /p/<poolID>.resource.<resourceID>.<StreamId>
    if ( selectors != null && selectors.length > 1 ) {
      resourceId = selectors[selectors.length-1];
      if ( selectors.length > 2 ) {
        alternativeStream = resourceId;
        resourceId = selectors[selectors.length-2];
      }
    }

    
    try {

    Content content = request.getResource().adaptTo(Content.class);
    String parentPath = content.getPath();
    LOGGER.debug("Resource Pool {}, Resource ID is {}, alternativeStream is {} ", new Object[]{parentPath, resourceId, alternativeStream});


    // Loop over all the parameters
    // All the ones that are files will be stored.
    int statusCode = HttpServletResponse.SC_BAD_REQUEST;
    boolean fileUpload = false;
    Map<String, Object> results = new HashMap<String, Object>();
    for (Entry<String, RequestParameter[]> e : request.getRequestParameterMap()
        .entrySet()) {
      for (RequestParameter p : e.getValue()) {
        if (!p.isFormField()) {
          // This is a file upload.
          // Generate an ID and store it.
          if (resourceId == null) {
            String createResourceId = generatePoolId();
            results.put(p.getFileName(), ImmutableMap.of("resourceId", (Object)createResourceId,  "item", createFile(StorageClientUtils.newPath(parentPath, createResourceId), null, session, p, true).getProperties()));
            statusCode = HttpServletResponse.SC_CREATED;
            fileUpload = true;
          } else {
            // Add it to the map so we can output something to the UI.
            results.put(p.getFileName(), ImmutableMap.of("resourceId", (Object)resourceId,  "item", createFile(StorageClientUtils.newPath(parentPath, resourceId), alternativeStream, session, p, false).getProperties()));
            results.put(p.getFileName(), resourceId);
            statusCode = HttpServletResponse.SC_OK;
            fileUpload = true;
            break;
          }

        }
      }
    }
    
    if (!fileUpload ) {
      // not a file upload, ok, create an item and use all the request paremeters, only if there was no poolId specified
      if ( resourceId == null ) {
        String createResourceId = generatePoolId();
        results.put("_contentItem", ImmutableMap.of("resourceId", (Object)createResourceId,  "item", createContentItem(StorageClientUtils.newPath(parentPath,createResourceId), session, request).getProperties()));
        statusCode = HttpServletResponse.SC_CREATED;
      } else {
        String resourcePath = StorageClientUtils.newPath(parentPath,resourceId);
        LOGGER.debug("Updating {} ", resourcePath);
        ContentManager contentManager = session.getContentManager();
        Content resourceContent = contentManager.get(resourcePath);
        if ( resourceContent != null ) {
          updateContentItem(resourceContent, request);
          results.put("_contentItem", ImmutableMap.of("resourceId", (Object)resourceId,  "item", resourceContent.getProperties()));
          results.put("_contentItem", resourceId);
          contentManager.update(resourceContent);
          statusCode = HttpServletResponse.SC_OK;
        } else {
          statusCode = HttpServletResponse.SC_NOT_FOUND;
        }
      }
    }


    // Make sure we're outputting proper json.
    if ( statusCode == HttpServletResponse.SC_BAD_REQUEST ) {
      response.setStatus(statusCode);
    } else {
      response.setStatus(statusCode);
      response.setContentType("text/plain");
      response.setCharacterEncoding("UTF-8");

      JSONWriter jsonWriter = new JSONWriter(response.getWriter());
      ExtendedJSONWriter.writeValueMap(jsonWriter, results);
    }
    } catch (NoSuchAlgorithmException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    } catch (ClientPoolException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    }

  }

  private String generatePoolId() throws UnsupportedEncodingException,
      NoSuchAlgorithmException {
    return clusterTrackingService.getClusterUniqueId();
  }
  
  private void updateContentItem(Content content,
      SlingHttpServletRequest request) throws StorageClientException, AccessDeniedException {
    for ( Entry<String, RequestParameter[]>   e : request.getRequestParameterMap().entrySet() ) {
      String k = e.getKey();
      if ( !(k.startsWith("_") || k.startsWith(":")) && !FilesConstants.RESERVED_POOL_KEYS.contains(k) ) {
        RequestParameter[] rp = e.getValue();
        if ( rp != null && rp.length > 0 ) {
          if ( rp.length == 1) {
              if ( rp[0].isFormField() ) {
                content.setProperty(k, rp[0].getString());
              }
          } else {
            List<String> values = Lists.newArrayList();
            for ( RequestParameter rpp : rp) {
              if ( rpp.isFormField() ) {
                values.add(rpp.getString());
              }
            }
            if ( values.size() > 0 ) {
              content.setProperty(k,values.toArray(new String[values.size()]));
            }
          }
        }
      }
    }

  }

  
  private Content createContentItem(String poolId, Session session,
      SlingHttpServletRequest request) throws StorageClientException, AccessDeniedException {
    ContentManager contentManager = session.getContentManager();
    Map<String, Object> contentProperties = new HashMap<String, Object>();
    for ( Entry<String, RequestParameter[]>   e : request.getRequestParameterMap().entrySet() ) {
      String k = e.getKey();
      if ( !(k.startsWith("_") || k.startsWith(":")) && !FilesConstants.RESERVED_POOL_KEYS.contains(k) ) {
        RequestParameter[] rp = e.getValue();
        if ( rp != null && rp.length > 0 ) {
          if ( rp.length == 1) {
              if ( rp[0].isFormField() ) {
                contentProperties.put(k, rp[0].getString());
              }
          } else {
            List<String> values = Lists.newArrayList();
            for ( RequestParameter rpp : rp) {
              if ( rpp.isFormField() ) {
                values.add(rpp.getString());
              }
            }
            if ( values.size() > 0 ) {
              contentProperties.put(k,values.toArray(new String[values.size()]));
            }
          }
        }
      }
    }
    Content content = new Content(poolId,contentProperties);

    contentManager.update(content);
    return contentManager.get(poolId);

  }

  private Content createFile(String poolId, String alternativeStream, Session session, RequestParameter value, boolean create) throws IOException, AccessDeniedException, StorageClientException {
    // Get the content type.
    String contentType = getContentType(value);
    ContentManager contentManager = session.getContentManager();
    if ( create ) {
      // Create a proper nt:file node in jcr with some properties on it to make it possible
      // to locate this pool file without having to use the path.
      Map<String, Object> contentProperties = new HashMap<String, Object>();
      contentProperties.put(POOLED_CONTENT_FILENAME, value.getFileName());
      contentProperties.put(POOLED_NEEDS_PROCESSING, "true");
      contentProperties.put(Content.MIMETYPE_FIELD, contentType);
      
      Content content = new Content(poolId,contentProperties);
      
      contentManager.update(content);
      
      contentManager.writeBody(poolId, value.getInputStream());
      
      

    } else {
      Content content = contentManager.get(poolId);
      content.setProperty(StorageClientUtils.getAltField(Content.MIMETYPE_FIELD, alternativeStream), contentType);
      contentManager.update(content);
      contentManager.writeBody(poolId, value.getInputStream(),alternativeStream);
    }
    return contentManager.get(poolId);
  }


  /**
   * Get the content type of a file that's in a {@link RequestParameter}.
   *
   * @param value
   *          The request parameter.
   * @return The content type.
   */
  private String getContentType(RequestParameter value) {
    String contentType = value.getContentType();
    if (contentType != null) {
      int idx = contentType.indexOf(';');
      if (idx > 0) {
        contentType = contentType.substring(0, idx);
      }
    }
    if (contentType == null || contentType.equals("application/octet-stream")) {
      // try to find a better content type
      contentType = getServletContext().getMimeType(value.getFileName());
      if (contentType == null || contentType.equals("application/octet-stream")) {
        contentType = "application/octet-stream";
      }
    }
    return contentType;
  }

}
