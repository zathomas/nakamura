package org.sakaiproject.nakamura.basiclti;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Dictionary;

import javax.servlet.ServletException;

@SlingServlet(methods = { "POST", "GET" }, generateService = true, paths = { "/var/basiclti/cletool" })
public class BasicLTICLEToolPropertyServlet extends SlingAllMethodsServlet {

  @Property(value = "http://localhost")
  protected static final String CLE_SERVER_URL = "sakai.cle.server.url";

  @Property(value = "12345")
  protected static final String CLE_BASICLTI_KEY = "sakai.cle.basiclti.key";

  @Property(value = "secret")
  protected static final String CLE_BASICLTI_SECRET = "sakai.cle.basiclti.secret";

  private static String cleUrl;
  private static String ltiKey;
  private static String ltiSecret;

  private static final long serialVersionUID = 1L;

  private static final Logger LOG = LoggerFactory
      .getLogger(BasicLTICLEToolPropertyServlet.class);

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    getTool(request, response, false);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    getTool(request, response, true);
  }

  private void getTool(SlingHttpServletRequest request,
      SlingHttpServletResponse response, boolean b) throws IOException {
    ExtendedJSONWriter ejw = new ExtendedJSONWriter(response.getWriter());
    try {
      ejw.object();
      ejw.key("ltiurl").value(
          cleUrl + "/imsblti/provider/" + request.getRequestParameter("t"));
      ejw.key("ltiurl_lock").value(true);
      ejw.key("ltikey_lock").value(true);
      ejw.key("ltisecret_lock").value(true);
      ejw.key("release_names").value(true);
      ejw.key("release_names_lock").value(true);
      ejw.key("frame_height").value("100");
      ejw.key("release_email").value(true);
      ejw.key("release_email_lock").value(true);
      ejw.key("release_principal_name").value(true);
      ejw.key("release_principal_name_lock").value(true);
      ejw.key("debug").value(false);
      ejw.key("ltiKeys").object();
      ejw.key("ltikey").value(ltiKey);
      ejw.key("ltisecret").value(ltiSecret);
      ejw.endObject();
      ejw.endObject();
    } catch (JSONException e) {
      final Writer trace = new StringWriter();
      final PrintWriter pw = new PrintWriter(trace);
      e.printStackTrace(pw);
      response.sendError(500, trace.toString());
    }
  }

  @Activate
  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> properties = componentContext.getProperties();
    LOG.info("Starting Activation of Hybrid BasicLTI CLE tool property servlet");
    cleUrl = (String) properties.get(CLE_SERVER_URL);
    ltiKey = (String) properties.get(CLE_BASICLTI_KEY);
    ltiSecret = (String) properties.get(CLE_BASICLTI_SECRET);
  }
}
