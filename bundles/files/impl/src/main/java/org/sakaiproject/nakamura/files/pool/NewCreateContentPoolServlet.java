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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.files.File;
import org.sakaiproject.nakamura.api.files.FileParams;
import org.sakaiproject.nakamura.api.files.FileService;
import org.sakaiproject.nakamura.api.files.FileUploadFilter;
import org.sakaiproject.nakamura.api.files.FileUploadHandler;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.files.StorageException;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.AuthorizableCountChanger;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods = "POST", paths = "/api/content")
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Allows for uploading files to the pool.")})
@ServiceDocumentation(name = "Create Content Pool Servlet", okForVersion = "1.2",
    description = "Creates and Updates files in the pool",
    shortDescription = "Creates and Updates files in the pool",
    bindings = @ServiceBinding(type = BindingType.PATH, bindings = {"/system/pool/createfile"},
        extensions = @ServiceExtension(name = "*", description = "If an extension is provided it is assumed to be the PoolID which is to be updated.")),
    methods = @ServiceMethod(name = "POST",
        description = {"A normal file post. If this is to create files, each file in the multipart file will create a new file in the pool. If a PoolID is supplied only the first file in the upload is used to overwrite the file." +
            "If versioning is required, then a POST must be performed to /p/poolID.save ",
            "Example<br>" +
                "<pre>A Multipart file upload to http://localhost:8080/system/pool/createfile will create one Pool file per file in the upload</pre>",
            "Example<br>" +
                "<pre>A Multipart file upload to http://localhost:8080/system/pool/createfile.3sd23a4QW4WD will update the file content for PoolID 3sd23a4QW4WD </pre>",
            "Response is of the form " +
                "<pre>" +
                "   { \"file1\" : \"3sd23a4QW4WD\", \"file2\" : \"3sd23a4QW4ZS\" } " +
                "</pre>"
        },
        response = {
            @ServiceResponse(code = 201, description = "Where files are created"),
            @ServiceResponse(code = 400, description = "Where the request is invalid"),
            @ServiceResponse(code = 403, description = "Anonymous users my not upload files to the content pool."),
            @ServiceResponse(code = 200, description = "Where the file is updated"),
            @ServiceResponse(code = 500, description = "Failure with HTML explanation.")
        }))

@References(value = {
    @Reference(name = "fileUploadHandler",
        referenceInterface = FileUploadHandler.class,
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        bind = "bindFileUploadHandler",
        unbind = "unbindFileUploadHandler"),
    @Reference(name = "fileUploadFilter",
        referenceInterface = FileUploadFilter.class,
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        bind = "bindFileUploadFilter",
        unbind = "unbindFileUploadFilter")
})
public class NewCreateContentPoolServlet extends SlingAllMethodsServlet {

  @Reference
  protected ClusterTrackingService clusterTrackingService;

  @Reference
  protected Repository sparseRepository;

  @Reference
  protected EventAdmin eventAdmin;

  @Reference
  protected transient AuthorizableCountChanger authorizableCountChanger;

  @Reference
  protected FileService fileService;

  private static final long serialVersionUID = -5099697955361286370L;


  private static final Logger LOGGER = LoggerFactory
      .getLogger(NewCreateContentPoolServlet.class);

  private Set<FileUploadHandler> fileUploadHandlers = new HashSet<FileUploadHandler>();

  public void bindFileUploadHandler(FileUploadHandler fileUploadHandler) {
    fileUploadHandlers.add(fileUploadHandler);
  }

  public void unbindFileUploadHandler(FileUploadHandler fileUploadHandler) {
    fileUploadHandlers.remove(fileUploadHandler);
  }


  private Set<FileUploadFilter> fileUploadFilters = new HashSet<FileUploadFilter>();

  public void bindFileUploadFilter(FileUploadFilter fileUploadFilter) {
    fileUploadFilters.add(fileUploadFilter);
  }

  public void unbindFileUploadFilter(FileUploadFilter fileUploadFilter) {
    fileUploadFilters.remove(fileUploadFilter);
  }


  private void notifyFileUploadHandlers(Map<String, Object> results, Session session,
                                        String poolId, RequestParameter p,
                                        String userId, boolean isNew)
      throws AccessDeniedException, StorageClientException {
    ContentManager contentManager = session.getContentManager();

    for (FileUploadHandler fileUploadHandler : fileUploadHandlers) {
      try {
        InputStream inputStream = contentManager.getInputStream(poolId);
        fileUploadHandler.handleFile(results, poolId, inputStream, userId, isNew);
        inputStream.close();
      } catch (Throwable t) {
        LOGGER.error("FileUploadHandler '{}' failed to handle upload of file '{}' for userid '{}': {}",
            new Object[]{fileUploadHandler, p.getFileName(), userId, t.getMessage()});
        LOGGER.error(t.getMessage(), t);
      }
    }
  }


  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    String userId = request.getRemoteUser();

    RequestPathInfo rpi = request.getRequestPathInfo();
    String poolId = rpi.getExtension();
    String[] selectors = rpi.getSelectors();
    String alternativeStream = null;
    if (selectors != null && selectors.length > 0) {
      alternativeStream = poolId;
      poolId = selectors[0];
    }

    Session adminSession = null;
    try {
      // Grab an admin session so we can create files in the pool space.
      adminSession = sparseRepository.loginAdministrative();
      AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      // We need the authorizable for the user node that we'll create under the file.


      Authorizable au = authorizableManager.findAuthorizable(userId);

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
            String fileName = FilenameUtils.getName(p.getFileName()); // IE still sends in an absolute path sometimes.
            FileParams params = new FileParams();
            params.setCreator(userId);
            params.setFilename(fileName);
            params.setContentType(getContentType(p));
            params.setInputStream(p.getInputStream());
            params.setPoolID(poolId);
            params.setAlternativeStream(alternativeStream);

            File thisFile;
            boolean isNew = false;
            fileUpload = true;

            if (poolId == null) {
              thisFile = fileService.createFile(params);
              statusCode = HttpServletResponse.SC_CREATED;
              isNew = true;
            } else if (alternativeStream != null && alternativeStream.indexOf(FilesConstants.ALTERNATIVE_STREAM_SELECTOR_SEPARATOR) > 0) {
              thisFile = fileService.createAlternativeStream(params);
              statusCode = HttpServletResponse.SC_OK;
            } else {
              thisFile = fileService.updateFile(params);
              statusCode = HttpServletResponse.SC_OK;
            }

            results.put(fileName, ImmutableMap.of("poolId", thisFile.getPoolID(), "item", thisFile.getProperties()));
            notifyFileUploadHandlers(results, adminSession, thisFile.getPoolID(), p, au.getId(), isNew);

          }
        }
      }

      if (!fileUpload) {
        // not a file upload, ok, create an item and use all the request parameters, only
        // if there was no poolId specified
        if (poolId == null) {
          File streamlessFile = createStreamlessFile(request, userId);
          results.put(streamlessFile.getFilename(), ImmutableMap.of("poolId", streamlessFile.getPoolID(), "item",
              streamlessFile.getProperties()));
          statusCode = HttpServletResponse.SC_CREATED;
        }
      }

      this.authorizableCountChanger.notify(UserConstants.CONTENT_ITEMS_PROP, userId);

      // Make sure we're outputting proper json.
      if (statusCode == HttpServletResponse.SC_BAD_REQUEST) {
        response.setStatus(statusCode);
      } else {
        response.setStatus(statusCode);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        JSONWriter jsonWriter = new JSONWriter(response.getWriter());
        ExtendedJSONWriter.writeValueMap(jsonWriter, results);
      }
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
    } catch (StorageException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new ServletException(e.getMessage(), e);
    } finally {
      // Make sure we're logged out.
      try {
        if (adminSession != null) {
          adminSession.logout();
        }
      } catch (ClientPoolException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
  }

  private File createStreamlessFile(SlingHttpServletRequest request, String creator) throws StorageException, IOException {
    Map<String, Object> contentProperties = new HashMap<String, Object>();
    for (Entry<String, RequestParameter[]> e : request.getRequestParameterMap().entrySet()) {
      String k = e.getKey();
      if (!(k.startsWith("_") || k.startsWith(":")) && !FilesConstants.RESERVED_POOL_KEYS.contains(k)) {
        RequestParameter[] rp = e.getValue();
        if (rp != null && rp.length > 0) {
          if (rp.length == 1) {
            if (rp[0].isFormField()) {
              // Since this is a non-file upload allow override of the mimetype
              if ("mimeType".equals(k)) {
                contentProperties.put(Content.MIMETYPE_FIELD, rp[0].getString());
              } else {
                contentProperties.put(k, rp[0].getString());
              }
            }
          } else {
            List<String> values = Lists.newArrayList();
            for (RequestParameter rpp : rp) {
              if (rpp.isFormField()) {
                values.add(rpp.getString());
              }
            }
            if (values.size() > 0) {
              contentProperties.put(k, values.toArray(new String[values.size()]));
            }
          }
        }
      }
    }
    FileParams params = new FileParams();
    params.setCreator(creator);
    params.setFilename("_contentItem");
    params.setProperties(contentProperties);

    return fileService.createFile(params);
  }

  /**
   * Get the content type of a file that's in a {@link org.apache.sling.api.request.RequestParameter}.
   *
   * @param value The request parameter.
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
