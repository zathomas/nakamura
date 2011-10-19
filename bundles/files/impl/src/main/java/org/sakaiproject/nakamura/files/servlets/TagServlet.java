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

import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang.CharEncoding;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.resource.lite.SparseContentResource;
import org.sakaiproject.nakamura.api.search.SearchException;
import org.sakaiproject.nakamura.api.search.SearchResultSet;
import org.sakaiproject.nakamura.api.search.SearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.files.search.FileSearchBatchResultProcessor;
import org.sakaiproject.nakamura.files.search.LiteFileSearchBatchResultProcessor;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "TagServlet", okForVersion = "0.11",
    shortDescription = "Get information about a tag.",
    description = {
      "This servlet is able to give all the necessary information about tags.",
      "It's able to give json feeds for the child tags, parent tags or give a dump of the files who are tagged with this tag.",
      "Must specify a selector of children, parents, tagged. tidy, {number} are optional and ineffective by themselves."
    },
    bindings = {
      @ServiceBinding(type = BindingType.TYPE, bindings = { "sakai/tag" },
          extensions = @ServiceExtension(name = "json", description = "This servlet outputs JSON data."),
          selectors = {
            @ServiceSelector(name = "children", description = "Will dump all the children of this tag."),
            @ServiceSelector(name = "parents", description = "Will dump all the parents of this tag."),
            @ServiceSelector(name = "tagged", description = "Will dump all the files who are tagged with this tag."),
            @ServiceSelector(name = "tidy", description = "Optional sub-selector. Will send back 'tidy' output."),
            @ServiceSelector(name = "{number}", description = "Optional sub-selector. Specifies the depth of data to output.")
          }
      )
    },
    methods = {
      @ServiceMethod(name = "GET",  parameters = {@ServiceParameter(name = "type", description = "For use with tagged selector. Valid values are: user, group, or content") },
          description = { "This servlet only responds to GET requests." },
          response = {
            @ServiceResponse(code = 200, description = "Succesful request, json can be found in the body"),
            @ServiceResponse(code = 500, description = "Failure to retrieve tags or files, an explanation can be found in the HTMl.")
          }
      )
    }
)
@SlingServlet(extensions = { "json" }, generateComponent = true, generateService = true,
    methods = { "GET" }, resourceTypes = { "sakai/tag" },
    selectors = {"children", "parents", "tagged" }
)
@Properties(value = {
    @Property(name = "service.description", value = "Provides support for file tagging."),
    @Property(name = "service.vendor", value = "The Sakai Foundation")
})
public class TagServlet extends SlingSafeMethodsServlet {
  private static final Logger LOG = LoggerFactory.getLogger(TagServlet.class);
  private static final long serialVersionUID = -8815248520601921760L;

  @Reference
  protected transient SearchServiceFactory searchServiceFactory;

  @Reference
  protected transient SolrSearchServiceFactory solrSearchServiceFactory;
  
  @Reference
  protected transient Repository sparseRepository;
  
  @Reference
  private ProfileService profileService;

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {

    // digest the selectors to determine if we should send a tidy result
    // or if we need to traverse deeper into the tagged node.
    boolean tidy = false;
    int depth = 0;
    String[] selectors = request.getRequestPathInfo().getSelectors();
    String selector = null;
    for (String sel : selectors) {
      if ("tidy".equals(sel)) {
        tidy = true;
      } else if ("infinity".equals(sel)) {
        depth = -1;
      } else {
        // check if the selector is telling us the depth of detail to return
        Integer d = null;
        try { d = Integer.parseInt(sel); } catch (NumberFormatException e) {}
        if (d != null) {
          depth = d;
        } else {
          selector = sel;
        }
      }
    }
    
    request.setAttribute("depth", depth);

    JSONWriter write = new JSONWriter(response.getWriter());
    write.setTidy(tidy);
    Resource tagResource = request.getResource();
    String tagUuid = "";
    try {
      if (tagResource instanceof SparseContentResource) {
        Content contentTag = tagResource.adaptTo(Content.class);
        tagUuid = (String) contentTag.getProperty(Content.getUuidField());
      } else {
        Node nodeTag = tagResource.adaptTo(Node.class);
        tagUuid = nodeTag.getIdentifier();
      }
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
      return;
    }

    try {
      if ("children".equals(selector)) {
        if (tagResource instanceof SparseContentResource) {
          sendChildren(tagResource.adaptTo(Content.class), write);
        } else {
          sendChildren(tagResource.adaptTo(Node.class), write);
        }
      } else if ("parents".equals(selector)) {
        if (tagResource instanceof SparseContentResource) {
          sendParents(tagResource.adaptTo(Content.class), write);
        } else {
          sendParents(tagResource.adaptTo(Node.class), write);
        }
      } else if ("tagged".equals(selector)) {
        sendFiles(tagUuid, request, write, depth);
      }
      response.setContentType("application/json");
      response.setCharacterEncoding(CharEncoding.UTF_8);
    } catch (JSONException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (RepositoryException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (SearchException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(e.getCode(), e.getMessage());
    } catch (SolrSearchException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(e.getCode(), e.getMessage());
    } catch (ClientPoolException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (StorageClientException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (AccessDeniedException e) {
      LOG.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

  }

  /**
   * @param tag
   * @param request
   * @throws RepositoryException
   * @throws JSONException
   * @throws SearchException
   * @throws SolrSearchException
   */
  protected void sendFiles(String uuid, SlingHttpServletRequest request, JSONWriter write,
      int depth) throws RepositoryException, JSONException, SearchException, SolrSearchException {
    
    // We expect tags to be referencable, if this tag is not..
    // it will throw an exception.

    // Tagging on any item will be performed by adding a weak reference to the content
    // item. Put simply a sakai:tag-uuid property with the UUID of the tag node. We use
    // the UUID to uniquely identify the tag in question, a string of the tag name is not
    // sufficient. This allows the tag to be renamed and moved without breaking the
    // relationship.
    String statement = "//*[@sakai:tag-uuid='" + uuid + "']";
    Session session = request.getResourceResolver().adaptTo(Session.class);
    QueryManager qm = session.getWorkspace().getQueryManager();
    @SuppressWarnings("deprecation")
    Query query = qm.createQuery(statement, Query.XPATH);

    FileSearchBatchResultProcessor proc = new FileSearchBatchResultProcessor(searchServiceFactory);

    SearchResultSet rs = proc.getSearchResultSet(request, query);
    write.array();
    proc.writeNodes(request, write, null, rs.getRowIterator());

    // BL120 KERN-1617 Need to include Content tagged with tag uuid
    final StringBuilder sb = new StringBuilder();
    sb.append("taguuid:");
    sb.append(ClientUtils.escapeQueryChars(uuid));
    final RequestParameter typeP = request.getRequestParameter("type");
    if (typeP != null) {
      final String type = typeP.getString();
      sb.append(" AND ");
      if ("user".equals(type)) {
        sb.append("type:u");
      } else if ("group".equals(type)) {
        sb.append("type:g");
      } else {
        if ("content".equals(type)) {
          sb.append("resourceType:");
          sb.append(ClientUtils.escapeQueryChars(FilesConstants.POOLED_CONTENT_RT));
        } else {
          LOG.info("Unknown type parameter specified: type={}", type);
          write.endArray();
          return;
        }
      }
    }
    final String queryString = sb.toString();
    org.sakaiproject.nakamura.api.search.solr.Query solrQuery = new org.sakaiproject.nakamura.api.search.solr.Query(
        queryString, ImmutableMap.of("sort", (Object) "score desc"));
    final SolrSearchBatchResultProcessor rp = new LiteFileSearchBatchResultProcessor(
        solrSearchServiceFactory, profileService);
    final SolrSearchResultSet srs = rp.getSearchResultSet(request, solrQuery);
    rp.writeResults(request, write, srs.getResultSetIterator());
    write.endArray();
  }

  /**
   * Write all the parent tags of the passed in tag.
   *
   * @param tag
   *          The tag that should be sent and get it's children parsed.
   * @param write
   *          The JSONWriter to write to
   * @throws JSONException
   * @throws RepositoryException
   */
  protected void sendParents(Node tag, JSONWriter write) throws JSONException,
      RepositoryException {
    write.object();
    ExtendedJSONWriter.writeNodeContentsToWriter(write, tag);
    write.key("parent");
    try {
      Node parent = tag.getParent();
      if (FileUtils.isTag(parent)) {
        sendParents(parent, write);
      } else {
        write.value(false);
      }
    } catch (ItemNotFoundException e) {
      write.value(false);
    }

    write.endObject();
  }
  
  /**
   * Write all the parent tags of the passed in tag.
   *
   * @param tag
   *          The tag that should be sent and get it's children parsed.
   * @param write
   *          The JSONWriter to write to
   * @throws JSONException
   * @throws RepositoryException
   * @throws AccessDeniedException 
   * @throws StorageClientException 
   * @throws ClientPoolException 
   */
  protected void sendParents(Content tag, JSONWriter write) throws JSONException,
  RepositoryException, ClientPoolException, StorageClientException, AccessDeniedException {
    org.sakaiproject.nakamura.api.lite.Session sparseSession = null;
    try {
      sparseSession = sparseRepository.loginAdministrative();
      ContentManager contentManager = sparseSession.getContentManager();
      write.object();
      ExtendedJSONWriter.writeNodeContentsToWriter(write, tag);
      write.key("parent");
      try {
        Content parent = contentManager.get(PathUtils.getParentReference(tag.getPath()));
        if (FileUtils.isTag(parent)) {
          sendParents(parent, write);
        } else {
          write.value(false);
        }
      } catch (ItemNotFoundException e) {
        write.value(false);
      }

      write.endObject();
    } finally {
      if (sparseSession != null) {
        sparseSession.logout();
      }
    }
  }

  /**
   * Write all the child tags of the passed in tag.
   *
   * @param tag
   *          The tag that should be sent and get it's children parsed.
   * @param write
   *          The JSONWriter to write to
   * @throws JSONException
   * @throws RepositoryException
   */
  protected void sendChildren(Node tag, JSONWriter write) throws JSONException,
      RepositoryException {

    write.object();
    ExtendedJSONWriter.writeNodeContentsToWriter(write, tag);
    write.key("children");
    write.array();
    NodeIterator iterator = tag.getNodes();
    while (iterator.hasNext()) {
      Node node = iterator.nextNode();
      if (FileUtils.isTag(node)) {
        sendChildren(node, write);
      }
    }
    write.endArray();
    write.endObject();

  }
  
  /**
   * Write all the child tags of the passed in tag.
   *
   * @param tag
   *          The tag that should be sent and get it's children parsed.
   * @param write
   *          The JSONWriter to write to
   * @throws JSONException
   * @throws RepositoryException
   */
  protected void sendChildren(Content tag, JSONWriter write) throws JSONException,
      RepositoryException {

    write.object();
    ExtendedJSONWriter.writeNodeContentsToWriter(write, tag);
    write.key("children");
    write.array();
    for(Content node : tag.listChildren()) {
      if (FileUtils.isTag(node)) {
        sendChildren(node, write);
      }
    }
    write.endArray();
    write.endObject();

  }
}
