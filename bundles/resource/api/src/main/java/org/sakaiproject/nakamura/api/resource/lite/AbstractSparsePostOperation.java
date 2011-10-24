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
package org.sakaiproject.nakamura.api.resource.lite;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.api.wrappers.SlingRequestPaths;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.VersioningConfiguration;
import org.sakaiproject.nakamura.api.lite.DataFormatException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletResponse;


/**
 * Holds various states and encapsulates methods that are needed to handle a
 * post request.
 */
public abstract class AbstractSparsePostOperation implements SparsePostOperation {

    /**
     * default log
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Prepares and finalizes the actual operation. Preparation encompasses
     * getting the absolute path of the item to operate on by calling the
     * {@link #getItemPath(SlingHttpServletRequest)} method and setting the
     * location and parent location on the response. After the operation has
     * been done in the {@link #doRun(SlingHttpServletRequest, HtmlResponse, List)}
     * method the session is saved if there are unsaved modifications. In case
     * of errorrs, the unsaved changes in the session are rolled back.
     *
     * @param request the request to operate on
     * @param response The <code>HtmlResponse</code> to record execution
     *            progress.
     */
    public void run(SlingHttpServletRequest request,
                    HtmlResponse response,
                    SparsePostProcessor[] processors) {
        Resource resource = request.getResource();
        ContentManager contentManager = resource.adaptTo(ContentManager.class);

        VersioningConfiguration versionableConfiguration = getVersioningConfiguration(request);

        try {
            // calculate the paths
            String contentPath = getItemPath(request);
            String resourcePath = getFinalResourcePath(request, contentPath);
            resourcePath = removeAndValidateWorkspace(resourcePath);
            response.setPath(resourcePath);

            // location
            response.setLocation(externalizePath(request, resourcePath));

            // parent location
            String parentResourcePath = ResourceUtil.getParent(resourcePath);
            if (parentResourcePath != null) {
                response.setParentLocation(externalizePath(request, parentResourcePath));
            }

            final List<Modification> changes = new ArrayList<Modification>();

            doRun(request, response, contentManager, changes, contentPath);

            // invoke processors
            for(int i=0; i<processors.length; i++) {
                processors[i].process(request, changes);
            }

            Set<String> nodesToCheckin = new LinkedHashSet<String>();

            // set changes on html response
            for(Modification change : changes) {
                switch ( change.getType() ) {
                    case MODIFY : response.onModified(change.getSource()); break;
                    case DELETE : response.onDeleted(change.getSource()); break;
                    case MOVE :   response.onMoved(change.getSource(), change.getDestination()); break;
                    case COPY :   response.onCopied(change.getSource(), change.getDestination()); break;
                    case CREATE :
                        response.onCreated(change.getSource());
                        if (versionableConfiguration.isCheckinOnNewVersionableNode()) {
                            nodesToCheckin.add(change.getSource());
                        }
                        break;
                    case ORDER : response.onChange("ordered", change.getSource(), change.getDestination()); break;
                    case CHECKOUT :
                        response.onChange("checkout", change.getSource());
                        nodesToCheckin.add(change.getSource());
                        break;
                    case CHECKIN :
                        response.onChange("checkin", change.getSource());
                        nodesToCheckin.remove(change.getSource());
                        break;
                }
            }

            if (!isSkipCheckin(request)) {
                // now do the checkins
                for(String checkinPath : nodesToCheckin) {
                    if (checkin(checkinPath, contentManager)) {
                        response.onChange("checkin", checkinPath);
                    }
                }
            }

        } catch ( AccessDeniedException e ) {
            log.error("Access Denied {} ",e.getMessage());
            log.debug("Access Denied Cause ", e);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN, "Access denied for " + request.getRequestURI());
            response.setError(e);
        } catch (Exception e) {

            if(e.getCause() instanceof DataFormatException) {
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST, e.getLocalizedMessage());
            } else {
              log.error("Exception during response processing.", e);
            }
          response.setError(e);
        }

    }

    protected VersioningConfiguration getVersioningConfiguration(SlingHttpServletRequest request) {
        VersioningConfiguration versionableConfiguration =
            (VersioningConfiguration) request.getAttribute(VersioningConfiguration.class.getName());
        return versionableConfiguration != null ? versionableConfiguration : new VersioningConfiguration();
    }

    protected boolean isSkipCheckin(SlingHttpServletRequest request) {
        return !getVersioningConfiguration(request).isAutoCheckin();
    }

    /**
     * Remove the workspace name, if any, from the start of the path and validate that the
     * session's workspace name matches the path workspace name.
     */
    protected String removeAndValidateWorkspace(String path) {
        final int wsSepPos = path.indexOf(":/");
        if (wsSepPos != -1) {
            return path.substring(wsSepPos + 1);
        } else {
            return path;
        }
    }


    /**
     * Returns the storage-system path corresponding to the resource of
     * the request. For Sparse storage, this may differ from the Resource
     * path.
     */
    protected String getItemPath(SlingHttpServletRequest request) {
        Resource resource = request.getResource();
        if ( resource instanceof SparseNonExistingResource ) {
          return ((SparseNonExistingResource) resource).getTargetContentPath();
        } else {
          Content content = resource.adaptTo(Content.class);
          if (content != null) {
              return content.getPath();
          } else {
              return resource.getPath();
          }
        }
    }

    protected String getFinalResourcePath(SlingHttpServletRequest request, String finalContentPath) {
      return request.getResource().getPath();
    }

    protected abstract void doRun(SlingHttpServletRequest request,
            HtmlResponse response,
            ContentManager contentManager, List<Modification> changes, String contentPath) throws StorageClientException, AccessDeniedException, IOException;

    /**
     * Returns an iterator on <code>Resource</code> instances addressed in the
     * {@link SlingPostConstants#RP_APPLY_TO} request parameter. If the request
     * parameter is not set, <code>null</code> is returned. If the parameter
     * is set with valid resources an empty iterator is returned. Any resources
     * addressed in the {@link SlingPostConstants#RP_APPLY_TO} parameter is
     * ignored.
     *
     * @param request The <code>SlingHttpServletRequest</code> object used to
     *            get the {@link SlingPostConstants#RP_APPLY_TO} parameter.
     * @return The iterator of resources listed in the parameter or
     *         <code>null</code> if the parameter is not set in the request.
     */
    protected Iterator<Resource> getApplyToResources(
            SlingHttpServletRequest request) {

        String[] applyTo = request.getParameterValues(SlingPostConstants.RP_APPLY_TO);
        if (applyTo == null) {
            return null;
        }

        return new ApplyToIterator(request, applyTo);
    }

    /**
     * Returns an external form of the given path prepending the context path
     * and appending a display extension.
     *
     * @param path the path to externalize
     * @return the url
     */
    protected final String externalizePath(SlingHttpServletRequest request,
            String path) {
        StringBuffer ret = new StringBuffer();
        ret.append(SlingRequestPaths.getContextPath(request));
        ret.append(path);
        if ( false ) {
          // TODO This is currently an expensive no-op, as JcrResourceResolver tries
          // in vain to resolve the Content path as a Resource path.
          ResourceResolver resourceResolver = request.getResourceResolver();
          try {
            ret.append(resourceResolver.map(path));
          } catch (java.lang.StringIndexOutOfBoundsException e) {
            log.error("During POST operation, resource resolver failed to map this path: " + path);
          }
        }

        // append optional extension
        String ext = request.getParameter(SlingPostConstants.RP_DISPLAY_EXTENSION);
        if (ext != null && ext.length() > 0) {
            if (ext.charAt(0) != '.') {
                ret.append('.');
            }
            ret.append(ext);
        }

        return ret.toString();
    }

    /**
     * Resolves the given path with respect to the current root path.
     *
     * @param relPath the path to resolve
     * @return the given path if it starts with a '/'; a resolved path
     *         otherwise.
     */
    protected final String resolvePath(String absPath, String relPath) {
        if (relPath.startsWith("/")) {
            return relPath;
        }
        return absPath + "/" + relPath;
    }

    /**
     * Returns true if any of the request parameters starts with
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT <code>./</code>}.
     * In this case only parameters starting with either of the prefixes
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT <code>./</code>},
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_PARENT <code>../</code>}
     * and {@link SlingPostConstants#ITEM_PREFIX_ABSOLUTE <code>/</code>} are
     * considered as providing content to be stored. Otherwise all parameters
     * not starting with the command prefix <code>:</code> are considered as
     * parameters to be stored.
     */
    protected final boolean requireItemPathPrefix(
            SlingHttpServletRequest request) {

        boolean requirePrefix = false;

        Enumeration<?> names = request.getParameterNames();
        while (names.hasMoreElements() && !requirePrefix) {
            String name = (String) names.nextElement();
            requirePrefix = name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_CURRENT);
        }

        return requirePrefix;
    }

    /**
     * Returns <code>true</code> if the <code>name</code> starts with either
     * of the prefixes
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT <code>./</code>},
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_PARENT <code>../</code>}
     * and {@link SlingPostConstants#ITEM_PREFIX_ABSOLUTE <code>/</code>}.
     */
    protected boolean hasItemPathPrefix(String name) {
        return name.startsWith(SlingPostConstants.ITEM_PREFIX_ABSOLUTE)
            || name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_CURRENT)
            || name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_PARENT);
    }

    /**
     * Orders the given node according to the specified command. The following
     * syntax is supported: <xmp> | first | before all child nodes | before A |
     * before child node A | after A | after child node A | last | after all
     * nodes | N | at a specific position, N being an integer </xmp>
     *
     * @param item node to order
     * @throws RepositoryException if an error occurs
     */
    protected void orderNode(SlingHttpServletRequest request, Content item,
            List<Modification> changes) throws StorageClientException {

      // TODO: modify the content Manager to allow ordering of child nodes.
        String command = request.getParameter(SlingPostConstants.RP_ORDER);
        if (command == null || command.length() == 0) {
            // nothing to do
            return;
        }

        return;

    }


    private boolean checkin(String path, ContentManager contentManager) throws StorageClientException, AccessDeniedException  {
      contentManager.saveVersion(path);
      return true;
    }

    private static class ApplyToIterator implements Iterator<Resource> {

        private final ResourceResolver resolver;
        private final Resource baseResource;
        private final String[] paths;

        private int pathIndex;

        private Resource nextResource;

        ApplyToIterator(SlingHttpServletRequest request, String[] paths) {
            this.resolver = request.getResourceResolver();
            this.baseResource = request.getResource();
            this.paths = paths;
            this.pathIndex = 0;

            nextResource = seek();
        }

        public boolean hasNext() {
            return nextResource != null;
        }

        public Resource next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Resource result = nextResource;
            nextResource = seek();

            return result;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private Resource seek() {
            while (pathIndex < paths.length) {
                String path = paths[pathIndex];
                pathIndex++;

                Resource res = resolver.getResource(baseResource, path);
                if (res != null) {
                    return res;
                }
            }

            // no more elements in the array
            return null;
        }
    }
}