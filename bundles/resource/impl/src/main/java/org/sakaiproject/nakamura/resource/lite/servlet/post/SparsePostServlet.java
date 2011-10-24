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
package org.sakaiproject.nakamura.resource.lite.servlet.post;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.servlets.post.NodeNameGenerator;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.VersioningConfiguration;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.resource.DateParser;
import org.sakaiproject.nakamura.api.resource.JSONResponse;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostProcessor;
import org.sakaiproject.nakamura.resource.lite.servlet.post.helper.DefaultNodeNameGenerator;
import org.sakaiproject.nakamura.resource.lite.servlet.post.helper.MediaRangeList;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.CheckinOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.CheckoutOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.CopyOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.CreateTreeOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.DeleteOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.ImportOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.ModifyOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.MoveOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.NopOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * POST servlet that implements the sling client library "protocol" for sparse content.
 */
@SlingServlet(methods={"POST"}, resourceTypes={"sparse/Content"} )
@Properties({ @Property(name = "service.description", value = "Sparse Post Servlet"),
    @Property(name = "service.vendor", value = "The Sakai Foundation")})
// Get all SlingPostProcessors
@References({
    @Reference(name = "postProcessor", referenceInterface = SparsePostProcessor.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC),
    @Reference(name = "postOperation", referenceInterface = SparsePostOperation.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC),
    @Reference(name = "nodeNameGenerator", referenceInterface = NodeNameGenerator.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC) })
public class SparsePostServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = 1837674988291697074L;

  /**
   * default log
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final boolean DEFAULT_CHECKIN_ON_CREATE = false;

  private static final boolean DEFAULT_AUTO_CHECKOUT = false;

  private static final boolean DEFAULT_AUTO_CHECKIN = true;

  @Property({ "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "ISO8601", "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
      "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy" })
  private static final String PROP_DATE_FORMAT = "servlet.post.dateFormats";

  @Property({ "title", "jcr:title", "name", "description", "jcr:description", "abstract",
      "text", "jcr:text" })
  private static final String PROP_NODE_NAME_HINT_PROPERTIES = "servlet.post.nodeNameHints";

  @Property(intValue = 20)
  private static final String PROP_NODE_NAME_MAX_LENGTH = "servlet.post.nodeNameMaxLength";

  @Property(boolValue = DEFAULT_CHECKIN_ON_CREATE)
  private static final String PROP_CHECKIN_ON_CREATE = "servlet.post.checkinNewVersionableNodes";

  @Property(boolValue = DEFAULT_AUTO_CHECKOUT)
  private static final String PROP_AUTO_CHECKOUT = "servlet.post.autoCheckout";

  @Property(boolValue = DEFAULT_AUTO_CHECKIN)
  private static final String PROP_AUTO_CHECKIN = "servlet.post.autoCheckin";

  private static final String PARAM_CHECKIN_ON_CREATE = ":checkinNewVersionableNodes";

  private static final String PARAM_AUTO_CHECKOUT = ":autoCheckout";

  private static final String PARAM_AUTO_CHECKIN = ":autoCheckin";

  /**
   * utility class for parsing date strings
   */
  private DateParser dateParser;

  private ModifyOperation modifyOperation;

  private final List<ServiceReference> delayedPostOperations = new ArrayList<ServiceReference>();

  private final Map<String, SparsePostOperation> postOperations = new HashMap<String, SparsePostOperation>();

  private final List<ServiceReference> delayedPostProcessors = new ArrayList<ServiceReference>();

  private final List<ServiceReference> postProcessors = new ArrayList<ServiceReference>();

  private SparsePostProcessor[] cachedPostProcessors = new SparsePostProcessor[0];

  private final List<ServiceReference> delayedNodeNameGenerators = new ArrayList<ServiceReference>();

  private final List<ServiceReference> nodeNameGenerators = new ArrayList<ServiceReference>();

  private NodeNameGenerator[] cachedNodeNameGenerators = new NodeNameGenerator[0];

  private ComponentContext componentContext;

  private NodeNameGenerator defaultNodeNameGenerator;

  private ImportOperation importOperation;

  private VersioningConfiguration baseVersioningConfiguration;

  @Override
  public void init() {
    // default operation: create/modify
    modifyOperation = new ModifyOperation(defaultNodeNameGenerator, dateParser,
        getServletContext());
    modifyOperation.setExtraNodeNameGenerators(cachedNodeNameGenerators);

    // other predefined operations
    postOperations.put(SlingPostConstants.OPERATION_COPY, new CopyOperation());
    postOperations.put(SlingPostConstants.OPERATION_MOVE, new MoveOperation());
    postOperations.put(SlingPostConstants.OPERATION_DELETE, new DeleteOperation());
    postOperations.put(SlingPostConstants.OPERATION_NOP, new NopOperation());
    postOperations.put(SlingPostConstants.OPERATION_CHECKIN, new CheckinOperation());
    postOperations.put(SlingPostConstants.OPERATION_CHECKOUT, new CheckoutOperation());

    importOperation = new ImportOperation(defaultNodeNameGenerator);
    importOperation.setExtraNodeNameGenerators(cachedNodeNameGenerators);
    postOperations.put(SlingPostConstants.OPERATION_IMPORT, importOperation);
    postOperations.put("createTree", new CreateTreeOperation());
  }

  @Override
  public void destroy() {
    modifyOperation = null;
    postOperations.clear();
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws IOException {
    VersioningConfiguration localVersioningConfig = createRequestVersioningConfiguration(request);

    request.setAttribute(VersioningConfiguration.class.getName(), localVersioningConfig);

    // prepare the response
    HtmlResponse htmlResponse = createHtmlResponse(request);
    htmlResponse.setReferer(request.getHeader("referer"));

    SparsePostOperation operation = getSlingPostOperation(request);
    if (operation == null) {

      htmlResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Invalid operation specified for POST request");

    } else {

      final SparsePostProcessor[] processors;
      synchronized (this.delayedPostProcessors) {
        processors = this.cachedPostProcessors;
      }
      try {
        log.info("Performing operation {} ",operation);
        operation.run(request, htmlResponse, processors);
      } catch (ResourceNotFoundException rnfe) {
        htmlResponse.setStatus(HttpServletResponse.SC_NOT_FOUND, rnfe.getMessage());
      } catch (Throwable throwable) {
        log.debug("Exception while handling POST " + request.getResource().getPath()
            + " with " + operation.getClass().getName(), throwable);
        htmlResponse.setError(throwable);
      }

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

  /**
   * Creates an instance of a HtmlResponse.
   *
   * @param req
   *          The request being serviced
   * @return a {@link JSONResponse} if any of
   *         these conditions are true:
   *         <ul>
   *         <li>the request has an <code>Accept</code> header of
   *         <code>application/json</code></li>
   *         <li>the request is a JSON POST request (see SLING-1172)</li>
   *         <li>the request has a request parameter <code>:accept=application/json</code>
   *         </li>
   *         </ul>
   *         or a {@link org.apache.sling.api.servlets.HtmlResponse} otherwise
   */
  HtmlResponse createHtmlResponse(SlingHttpServletRequest req) {
    MediaRangeList mediaRangeList = new MediaRangeList(req);
    if (JSONResponse.RESPONSE_CONTENT_TYPE.equals(mediaRangeList.prefer("text/html",
        JSONResponse.RESPONSE_CONTENT_TYPE))) {
      return new JSONResponse();
    } else {
      return new HtmlResponse();
    }
  }

  private SparsePostOperation getSlingPostOperation(SlingHttpServletRequest request) {
    String operation = request.getParameter(SlingPostConstants.RP_OPERATION);
    if (operation == null || operation.length() == 0) {
      // standard create/modify operation;
      return modifyOperation;
    }

    // named operation, retrieve from map
    return postOperations.get(operation);
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

      if (log.isDebugEnabled()) {
        log.debug("Will redirect to " + result);
      }
    }
    return result;
  }

  protected boolean isSetStatus(SlingHttpServletRequest request) {
    String statusParam = request.getParameter(SlingPostConstants.RP_STATUS);
    if (statusParam == null) {
      log.debug("getStatusMode: Parameter {} not set, assuming standard status code",
          SlingPostConstants.RP_STATUS);
      return true;
    }

    if (SlingPostConstants.STATUS_VALUE_BROWSER.equals(statusParam)) {
      log.debug("getStatusMode: Parameter {} asks for user-friendly status code",
          SlingPostConstants.RP_STATUS);
      return false;
    }

    if (SlingPostConstants.STATUS_VALUE_STANDARD.equals(statusParam)) {
      log.debug("getStatusMode: Parameter {} asks for standard status code",
          SlingPostConstants.RP_STATUS);
      return true;
    }

    log.debug(
        "getStatusMode: Parameter {} set to unknown value {}, assuming standard status code",
        SlingPostConstants.RP_STATUS);
    return true;
  }

  // ---------- SCR Integration ----------------------------------------------

  protected void activate(ComponentContext context) {
    synchronized (this.delayedPostProcessors) {
      this.componentContext = context;
      for (final ServiceReference ref : this.delayedPostProcessors) {
        this.registerPostProcessor(ref);
      }
      this.delayedPostProcessors.clear();
    }
    synchronized (this.delayedPostOperations) {
      for (final ServiceReference ref : this.delayedPostOperations) {
        this.registerPostOperation(ref);
      }
      this.delayedPostOperations.clear();
    }
    Dictionary<?, ?> props = context.getProperties();

    String[] nameHints = OsgiUtil
        .toStringArray(props.get(PROP_NODE_NAME_HINT_PROPERTIES));
    int nameMax = (int) OsgiUtil.toLong(props.get(PROP_NODE_NAME_MAX_LENGTH), -1);
    defaultNodeNameGenerator = new DefaultNodeNameGenerator(nameHints, nameMax);

    dateParser = new DateParser();
    String[] dateFormats = OsgiUtil.toStringArray(props.get(PROP_DATE_FORMAT));
    for (String dateFormat : dateFormats) {
      try {
        dateParser.register(dateFormat);
      } catch (Throwable t) {
        log.warn("activate: Ignoring format {} because it is invalid: {}", dateFormat, t);
      }
    }

    synchronized (this.delayedNodeNameGenerators) {
      for (final ServiceReference ref : this.delayedNodeNameGenerators) {
        this.registerNodeNameGenerator(ref);
      }
      this.delayedNodeNameGenerators.clear();
    }

    this.baseVersioningConfiguration = createBaseVersioningConfiguration(props);
  }

  protected void deactivate(ComponentContext context) {
    dateParser = null;
    this.componentContext = null;
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
    synchronized (this.delayedPostOperations) {
      String operationName = (String) ref
          .getProperty(SparsePostOperation.PROP_OPERATION_NAME);
      synchronized (this.postOperations) {
        this.postOperations.remove(operationName);
      }
    }
  }

  protected void bindPostProcessor(ServiceReference ref) {
    synchronized (this.delayedPostProcessors) {
      if (this.componentContext == null) {
        this.delayedPostProcessors.add(ref);
      } else {
        this.registerPostProcessor(ref);
      }
    }
  }

  protected void unbindPostProcessor(ServiceReference ref) {
    synchronized (this.delayedPostProcessors) {
      this.delayedPostProcessors.remove(ref);
      this.postProcessors.remove(ref);
    }
  }

  protected void registerPostProcessor(ServiceReference ref) {
    final int ranking = OsgiUtil.toInteger(ref.getProperty(Constants.SERVICE_RANKING), 0);
    int index = 0;
    while (index < this.postProcessors.size()
        && ranking < OsgiUtil.toInteger(
            this.postProcessors.get(index).getProperty(Constants.SERVICE_RANKING), 0)) {
      index++;
    }
    if (index == this.postProcessors.size()) {
      this.postProcessors.add(ref);
    } else {
      this.postProcessors.add(index, ref);
    }
    this.cachedPostProcessors = new SparsePostProcessor[this.postProcessors.size()];
    index = 0;
    for (final ServiceReference current : this.postProcessors) {
      final SparsePostProcessor processor = (SparsePostProcessor) this.componentContext
          .locateService("postProcessor", current);
      if (processor != null) {
        this.cachedPostProcessors[index] = processor;
        index++;
      }
    }
    if (index < this.cachedPostProcessors.length) {
      SparsePostProcessor[] oldArray = this.cachedPostProcessors;
      this.cachedPostProcessors = new SparsePostProcessor[index];
      for (int i = 0; i < index; i++) {
        this.cachedPostProcessors[i] = oldArray[i];
      }
    }
  }

  protected void bindNodeNameGenerator(ServiceReference ref) {
    synchronized (this.delayedNodeNameGenerators) {
      if (this.componentContext == null) {
        this.delayedNodeNameGenerators.add(ref);
      } else {
        this.registerNodeNameGenerator(ref);
      }
    }
  }

  protected void unbindNodeNameGenerator(ServiceReference ref) {
    synchronized (this.delayedNodeNameGenerators) {
      this.delayedNodeNameGenerators.remove(ref);
      this.nodeNameGenerators.remove(ref);
    }
  }

  protected void registerNodeNameGenerator(ServiceReference ref) {
    final int ranking = OsgiUtil.toInteger(ref.getProperty(Constants.SERVICE_RANKING), 0);
    int index = 0;
    while (index < this.nodeNameGenerators.size()
        && ranking < OsgiUtil.toInteger(
            this.nodeNameGenerators.get(index).getProperty(Constants.SERVICE_RANKING), 0)) {
      index++;
    }
    if (index == this.nodeNameGenerators.size()) {
      this.nodeNameGenerators.add(ref);
    } else {
      this.nodeNameGenerators.add(index, ref);
    }
    this.cachedNodeNameGenerators = new NodeNameGenerator[this.nodeNameGenerators.size()];
    index = 0;
    for (final ServiceReference current : this.nodeNameGenerators) {
      final NodeNameGenerator generator = (NodeNameGenerator) this.componentContext
          .locateService("nodeNameGenerator", current);
      if (generator != null) {
        this.cachedNodeNameGenerators[index] = generator;
        index++;
      }
    }
    if (index < this.cachedNodeNameGenerators.length) {
      NodeNameGenerator[] oldArray = this.cachedNodeNameGenerators;
      this.cachedNodeNameGenerators = new NodeNameGenerator[index];
      for (int i = 0; i < index; i++) {
        this.cachedNodeNameGenerators[i] = oldArray[i];
      }
    }
    if (this.modifyOperation != null) {
      this.modifyOperation.setExtraNodeNameGenerators(this.cachedNodeNameGenerators);
    }
    if (this.importOperation != null) {
      this.importOperation.setExtraNodeNameGenerators(this.cachedNodeNameGenerators);
    }
  }

  private VersioningConfiguration createBaseVersioningConfiguration(Dictionary<?, ?> props) {
    VersioningConfiguration cfg = new VersioningConfiguration();
    cfg.setCheckinOnNewVersionableNode(OsgiUtil.toBoolean(
        props.get(PROP_CHECKIN_ON_CREATE), DEFAULT_CHECKIN_ON_CREATE));
    cfg.setAutoCheckout(OsgiUtil.toBoolean(props.get(PROP_AUTO_CHECKOUT),
        DEFAULT_AUTO_CHECKOUT));
    cfg.setAutoCheckin(OsgiUtil.toBoolean(props.get(PROP_AUTO_CHECKIN),
        DEFAULT_AUTO_CHECKIN));
    return cfg;
  }

  private VersioningConfiguration createRequestVersioningConfiguration(
      SlingHttpServletRequest request) {
    VersioningConfiguration cfg = baseVersioningConfiguration.clone();

    String paramValue = request.getParameter(PARAM_CHECKIN_ON_CREATE);
    if (paramValue != null) {
      cfg.setCheckinOnNewVersionableNode(Boolean.parseBoolean(paramValue));
    }
    paramValue = request.getParameter(PARAM_AUTO_CHECKOUT);
    if (paramValue != null) {
      cfg.setAutoCheckout(Boolean.parseBoolean(paramValue));
    }
    paramValue = request.getParameter(PARAM_AUTO_CHECKIN);
    if (paramValue != null) {
      cfg.setAutoCheckin(Boolean.parseBoolean(paramValue));
    }
    return cfg;
  }

}
