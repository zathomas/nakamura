package org.sakaiproject.nakamura.http.usercontent;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionService;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionValidator;
import org.sakaiproject.nakamura.api.http.usercontent.ServerProtectionVeto;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.jcr.Node;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * This implementation of the ServerProtectionService allows GETs and POSTs to the host,
 * provided they don't request bodies from areas not consider part of the application, and
 * provided the posts come with a referer header that is trusted.
 * </p>
 * <p>
 * GETs to application content (file bodies and encapsulated feeds) are allowed.
 * </p>
 * <p>
 * POSTS (or non GET/HEAD) operations from locations that are not trusted get a 400, bad
 * request.
 * </p>
 * <p>
 * GETs to non application content get redirected, with a HMAC SHA-512 digest to a host
 * that serves that content.
 * </p>
 * <p>
 * If the GET is an anon GET, then it gets redirected with no HMAC.
 * </p>
 * <p>
 * The default setup is http://localhost:8080 is the trusted server. http://localhost:8082
 * is the content server, referers of /* and http://localhost:8080* are trusted sources
 * of POST operations. Application data is any feed that does not stream a body, and
 * anything under /dev, /devwidgets, /index.html, all other GET operations are assumed to
 * be raw user content.
 * </p>
 */
@Component(immediate = true, metatype = true)
@Service(value = ServerProtectionService.class)
public class ServerProtectionServiceImpl implements ServerProtectionService {
  private static final String HMAC_SHA512 = "HmacSHA512";
  private static final String HMAC_PARAM = ":hmac";
  private static final String[] DEFAULT_TRUSTED_HOSTS = { "http://localhost:8080" };
  private static final String[] DEFAULT_TRUSTED_REFERERS = { "/",
      "http://localhost:8080" };
  private static final String[] DEFAULT_TRUSTED_PATHS = { "/dev", "/devwidgets", "/system", "/logout" };
  private static final String[] DEFAULT_TRUSTED_EXACT_PATHS = { "/", 
    "/index.html", 
    "/index",
    "/403", 
    "/404", 
    "/500", 
    "/acknowledgements", 
    "/categories", 
    "/category", 
    "/content",
    "/favicon.ico",
    "/logout",
    "/me.html",
    "/me",
    "/register",
    "/s2site",
    "/search/sakai2",
    "/search"  };
  private static final String DEFAULT_UNTRUSTED_CONTENT_URL = "http://localhost:8082";
  private static final String DEFAULT_TRUSTED_SECRET_VALUE = "This Must Be set in production";
  private static final String[] DEFAULT_WHITELIST_POST_PATHS = {"/system/console"};
  private static final String[] DEFAULT_ANON_WHITELIST_POST_PATHS = {"/system/userManager/user.create"};

  @Property(boolValue=false)
  private static final String DISABLE_XSS_PROTECTION_FOR_UI_DEV = "disable.protection.for.dev.mode";
  @Property(value = { DEFAULT_UNTRUSTED_CONTENT_URL } )
  static final String UNTRUSTED_CONTENTURL_CONF = "untrusted.contenturl";
  @Property
  static final String UNTRUSTED_REDIRECT_HOST = "untrusted.redirect.host";
  @Property(value = { "/dev", "/devwidgets", "/system", "/logout" })
  private static final String TRUSTED_PATHS_CONF = "trusted.paths";
  @Property(value = { "/", 
      "/index.html", 
      "/403", 
      "/404", 
      "/500", 
      "/acknowledgements", 
      "/categories", 
      "/category", 
      "/content",
      "/favicon.ico",
      "/logout",
      "/me",
      "/register",
      "/search/sakai2",
      "/search"  })
  private static final String TRUSTED_EXACT_PATHS_CONF = "trusted.exact.paths";
  @Property(value = { "/", "http://localhost:8080" })
  static final String TRUSTED_REFERER_CONF = "trusted.referer";
  @Property(value = { "http://localhost:8080" }, cardinality = 9999999)
  static final String TRUSTED_HOSTS_CONF = "trusted.hosts";
  @Property(value = { DEFAULT_TRUSTED_SECRET_VALUE })
  private static final String TRUSTED_SECRET_CONF = "trusted.secret";
  @Property(value = {"/system/console"}, cardinality = 9999999)
  private static final String WHITELIST_POST_PATHS_CONF = "trusted.postwhitelist";
  @Property(value = {"/system/userManager/user.create", "/system/batch"})
  private static final String ANON_WHITELIST_POST_PATHS_CONF = "trusted.anonpostwhitelist";
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ServerProtectionServiceImpl.class);

  /**
   * Set of hosts, that it is safe to receive non GET operations from.
   */
  private Set<String> safeHosts;
  /**
   * List of referer stems its safe to accept non GET operations from
   */
  private String[] safeReferers;
  /**
   * List of path stems its safe to stream content bodies from using a trusted host
   */
  private String[] safeToStreamPaths;
  /**
   * List of path stems its safe to stream content bodies from using a trusted host
   */
  private Set<String> safeToStreamExactPaths;
  /**
   * The protocol, domain, and port used to deliver untrusted content bodies, as
   * specified in the URL of the internal request as seen by the application, after
   * any proxying.
   */
  private String contentUrl;
  /**
   * The protocol, domain, and port to which streaming requests for untrusted content
   * should be redirected. This is the host of the external redirect URL as seen by
   * the browser. This is only needed if a front-end proxies to the application from
   * a different protocol, domain, or port. If not specified, the contentUrl is used.
   */
  private String contentRedirectHost;
  /**
   * Array of keys created from the secret, indexed by the second digit of the timestamp
   */
  private Key[] transferKeys;
  /**
   * List of url stems that are always Ok to accept posts from on any URL (eg
   * /system/console). You will want to add additional protection on these.
   */
  private String[] postWhiteList;
  /**
   * list of paths where its safe for anon to post to.
   */
  private String[] safeForAnonToPostPaths;

  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC, strategy = ReferenceStrategy.EVENT, bind = "bindServerProtectionValidator", unbind = "unbindServerProtectionValidator")
  private ServerProtectionValidator[] serverProtectionValidators = new ServerProtectionValidator[0];
  private Map<ServiceReference, ServerProtectionValidator> serverProtectionValidatorsStore = Maps
      .newConcurrentMap();
  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC, strategy = ReferenceStrategy.EVENT, bind = "bindServerProtectionVeto", unbind = "unbindServerProtectionVeto")
  private ServerProtectionVeto[] serverProtectionVetos = new ServerProtectionVeto[0];
  private Map<ServiceReference, ServerProtectionVeto> serverProtectionVetosStore = Maps
      .newConcurrentMap();

  private BundleContext bundleContext;
  private boolean disableProtectionForDevMode;

  @Modified
  public void modified(ComponentContext componentContext)
      throws NoSuchAlgorithmException, UnsupportedEncodingException,
      InvalidSyntaxException {
    @SuppressWarnings("unchecked")

    Dictionary<String, Object> properties = componentContext.getProperties();
    disableProtectionForDevMode = OsgiUtil.toBoolean(properties.get(DISABLE_XSS_PROTECTION_FOR_UI_DEV), false);
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return;
    }
    safeHosts = ImmutableSet.of(OsgiUtil.toStringArray(
        properties.get(TRUSTED_HOSTS_CONF), DEFAULT_TRUSTED_HOSTS));
    safeReferers = OsgiUtil.toStringArray(properties.get(TRUSTED_REFERER_CONF),
        DEFAULT_TRUSTED_REFERERS);
    safeToStreamPaths = OsgiUtil.toStringArray(properties.get(TRUSTED_PATHS_CONF),
        DEFAULT_TRUSTED_PATHS);
    safeToStreamExactPaths = ImmutableSet.of(OsgiUtil.toStringArray(
        properties.get(TRUSTED_EXACT_PATHS_CONF), DEFAULT_TRUSTED_EXACT_PATHS));
    contentUrl = OsgiUtil.toString(properties.get(UNTRUSTED_CONTENTURL_CONF),
        DEFAULT_UNTRUSTED_CONTENT_URL);
    contentRedirectHost = OsgiUtil.toString(properties.get(UNTRUSTED_REDIRECT_HOST),
        "");
    postWhiteList = OsgiUtil.toStringArray(
        properties.get(WHITELIST_POST_PATHS_CONF), DEFAULT_WHITELIST_POST_PATHS);
    safeForAnonToPostPaths = OsgiUtil.toStringArray(
        properties.get(ANON_WHITELIST_POST_PATHS_CONF), DEFAULT_ANON_WHITELIST_POST_PATHS);
    String transferSharedSecret = OsgiUtil.toString(properties.get(TRUSTED_SECRET_CONF),
        DEFAULT_TRUSTED_SECRET_VALUE);
    if (DEFAULT_TRUSTED_SECRET_VALUE.equals(transferSharedSecret)) {
      LOGGER.error("Configuration Error =============================");
      LOGGER
          .error("Configuration Error: Please set {} to secure Content Server in procuction ",TRUSTED_SECRET_CONF);
      LOGGER.error("Configuration Error =============================");
    }

    LOGGER.info("Trusted Hosts {}",safeHosts);
    LOGGER.info("Trusted Referers {} ",Arrays.toString(safeReferers));
    LOGGER.info("Trusted Stream Paths {} ",Arrays.toString(safeToStreamPaths));
    LOGGER.info("Trusted Stream Resources {} ",safeToStreamExactPaths);
    LOGGER.info("POST Whitelist {} ",postWhiteList);
    LOGGER.info("Content Host {} ",contentUrl);
    LOGGER.info("Content Redirect Host {} ",contentRedirectHost);
    LOGGER.info("Content Shared Secret [{}] ",transferSharedSecret);

    transferKeys = new Key[10];
    MessageDigest md = MessageDigest.getInstance("SHA-512");
    Base64 encoder = new Base64(true);
    byte[] input = transferSharedSecret.getBytes("UTF-8");
    // create a static ring of 10 keys by repeatedly hashing the last key seed
    // starting with the transferSharedSecret
    for (int i = 0; i < transferKeys.length; i++) {
      md.reset();
      byte[] data = md.digest(input);
      transferKeys[i] = new SecretKeySpec(data, HMAC_SHA512);
      input = encoder.encode(data);
    }

    bundleContext = componentContext.getBundleContext();
    ServiceReference[] srs = bundleContext.getAllServiceReferences(
        ServerProtectionValidator.class.getName(), null);
    if ( srs != null ) {
      for (ServiceReference sr : srs) {
        bindServerProtectionValidator(sr);
      }
    }
    ServiceReference[] srsVeto = bundleContext.getAllServiceReferences(
        ServerProtectionVeto.class.getName(), null);
    if ( srsVeto != null ) {
      for (ServiceReference sr : srsVeto) {
        bindServerProtectionVeto(sr);
      }
    }
  }
  @Activate
  public void activate(ComponentContext componentContext) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidSyntaxException {
    modified(componentContext);
  }

  @Deactivate
  public void destroy(ComponentContext c) {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return;
    }
    BundleContext bc = c.getBundleContext();
    for (Entry<ServiceReference, ServerProtectionValidator> e : serverProtectionValidatorsStore
        .entrySet()) {
      bc.ungetService(e.getKey());
    }
    serverProtectionValidatorsStore.clear();
    serverProtectionValidators = null;
  }

  public boolean isRequestSafe(SlingHttpServletRequest srequest,
      SlingHttpServletResponse sresponse) throws UnsupportedEncodingException,
      IOException {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return true;
    }
    // if the method is not safe, the request can't be safe.
    if (!isMethodSafe(srequest, sresponse)) {
      return false;
    }
    String method = srequest.getMethod();
    if ( "GET|OPTIONS|HEAD".indexOf(method) < 0 ) {
      String userId = srequest.getRemoteUser();
      if ( User.ANON_USER.equals(userId) ) {
        String path = srequest.getRequestURI();
        boolean safeForAnonToPost = false;
        for (String safePath : safeForAnonToPostPaths) {
          if (path.startsWith(safePath)) {
            safeForAnonToPost = true;
            break;
          }
        }
        if ( ! safeForAnonToPost ) {
          sresponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Anon users may not perform POST operations");
          return false;
        }
      }
    }
    boolean safeHost = isSafeHost(srequest);
    if (safeHost && "GET".equals(method)) {
      boolean safeToStream = false;
      RequestPathInfo requestPathInfo = srequest.getRequestPathInfo();
      String ext = requestPathInfo.getExtension();
      if (ext == null || "res".equals(ext)) {
        // this is going to stream
        String path = srequest.getRequestURI();
        LOGGER.debug("Checking [{}] RequestPathInfo {}", path, requestPathInfo);
        safeToStream = safeToStreamExactPaths.contains(path);
        if (!safeToStream) {
          LOGGER.debug("Checking [{}] looks like not safe to stream ", path );
          for (String safePath : safeToStreamPaths) {
            if (path.startsWith(safePath)) {
              safeToStream = true;
              break;
            }
          }
          if (!safeToStream) {
            Resource resource = srequest.getResource();
            if ( resource != null ) {
              Node node = resource.adaptTo(Node.class);
              if (node != null || "sling:nonexisting".equals(resource.getResourceType())) {
                // JCR content is trusted, as users dont have write to the JCR, lets hope thats true!
                // KERN-1930 and list discussion.
                // Also trust a "GET" of non-existing content so that the 404 comes from
                // the right port (KERN-2001)
                return true;
              }
              String resourcePath = resource.getPath();
              LOGGER.debug("Checking Resource Path [{}]",resourcePath);
              if (!safeToStream) {
                for (ServerProtectionValidator serverProtectionValidator : serverProtectionValidators) {
                  if ( serverProtectionValidator.safeToStream(srequest, resource)) {
                    LOGGER.debug(" {} said this {} is safe to stream ",serverProtectionValidator,resourcePath);
                    safeToStream = true;
                    break;
                  }
                }
              }
            }
          }
        }
      } else {
        safeToStream = true;
        LOGGER.debug("doesnt look like a body, checking with vetos" );
      }
      LOGGER.debug("Checking server vetos, safe to stream ? {} ", safeToStream);
      for (ServerProtectionVeto serverProtectionVeto : serverProtectionVetos) {
        LOGGER.debug("Checking for Veto on {} ",serverProtectionVeto);
        if ( serverProtectionVeto.willVeto(srequest)) {
          safeToStream = serverProtectionVeto.safeToStream(srequest);
          LOGGER.debug("{} vetoed {}  ", serverProtectionVeto, safeToStream);
          break;
        }
      }
      if (!safeToStream) {
        redirectToContent(srequest, sresponse);
        return false;
      }
      LOGGER.debug("Request will be sent from this host, no redirect {}", srequest.getRequestURL().toString());
    }
    return true;
  }

  private void redirectToContent(HttpServletRequest request, HttpServletResponse response)
      throws UnsupportedEncodingException, IOException {
    StringBuffer requestURL = request.getRequestURL();
    String queryString = request.getQueryString();
    if (queryString != null) {
      requestURL.append("?").append(queryString);
    }
    String url = requestURL.toString();
    // replace the protocol and host with the CDN host.
    int pathStart = requestURL.indexOf("/", requestURL.indexOf(":") + 3);
    url = getTransferUrl(request, url.substring(pathStart));
    // send via the session establisher
    LOGGER.debug("Sending redirect for {} {} ",request.getMethod(), url);
    response.sendRedirect(url);
  }

  /**
   * @param request
   * @param urlPath
   * @return
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws IllegalStateException
   * @throws UnsupportedEncodingException
   */
  private String getTransferUrl(HttpServletRequest request, String urlPath) {
    final String finalUrl = contentUrl + urlPath;
    String redirectUrl;
    if (StringUtils.isBlank(contentRedirectHost)) {
      redirectUrl = finalUrl;
    } else {
      redirectUrl = contentRedirectHost + urlPath;
    }
    // only transfer authN from a trusted safe host
    if (isSafeHost(request)) {
      String userId = request.getRemoteUser();
      if (userId != null && !User.ANON_USER.equals(userId)) {
        try {
          long ts = System.currentTimeMillis();
          int keyIndex = (int) (ts - ((ts / 10) * 10));
          Mac m = Mac.getInstance(HMAC_SHA512);
          m.init(transferKeys[keyIndex]);

          String message = finalUrl + ";" + userId + ";" + ts;
          m.update(message.getBytes("UTF-8"));
          String hmac = Base64.encodeBase64URLSafeString(m.doFinal());
          hmac = Base64.encodeBase64URLSafeString((hmac + ";" + userId + ";" + ts)
              .getBytes("UTF-8"));
          String spacer = "?";
          if ( finalUrl.indexOf('?') >  0) {
            spacer = "&";
          }
          redirectUrl = redirectUrl + spacer + HMAC_PARAM + "=" + hmac;
        } catch (Exception e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
    return redirectUrl;
  }

  public String getTransferUserId(HttpServletRequest request) {
    // only ever get a user ID in this way on a non trusted safe host.
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return null;
    }
    // the host must not be safe to decode the user transfer UserID, and the method must be a GET or HEAD
    String method = request.getMethod();
    if (!isSafeHost(request) && ("GET".equals(method) || "HEAD".equals(method))) {
      String hmac = request.getParameter(HMAC_PARAM);
      if (hmac != null) {
        try {
          hmac = new String(Base64.decodeBase64(hmac.getBytes("UTF-8")), "UTF-8");
          String[] parts = StringUtils.split(hmac, ';');
          String requestUrl = request.getRequestURL().append("?")
              .append(request.getQueryString()).toString();
          LOGGER.debug("Checking requestUrl [{}", requestUrl);
          int i = requestUrl.indexOf("&" + HMAC_PARAM);
          if ( i < 0 ) {
            i = requestUrl.indexOf("?" + HMAC_PARAM);
          }
          String finalUrl = requestUrl.substring(0, i);
          String requestHmac = parts[0];
          String requestUserId = parts[1];
          String requestTs = parts[2];
          String message = finalUrl + ";" + requestUserId + ";" + requestTs;
          long requestTsL = Long.parseLong(requestTs);
          if (Math.abs(System.currentTimeMillis() - requestTsL) < 60000L) {
            int keyIndex = (int) (requestTsL - ((requestTsL / 10) * 10));
            Mac m = Mac.getInstance(HMAC_SHA512);
            m.init(transferKeys[keyIndex]);
            m.update(message.getBytes("UTF-8"));
            String testHmac = Base64.encodeBase64URLSafeString(m.doFinal());
            if (testHmac.equals(requestHmac)) {
              LOGGER.debug("Successfully extracted requestUserId {} from HMAC", requestUserId);
              return requestUserId;
            } else {
              LOGGER.info("Mismatched HMAC. Request HMAC was '{}'", hmac);
            }
          } else {
            LOGGER.info("Out of date HMAC. Request TsL = {}, current time = {}", requestTs, String.valueOf(System.currentTimeMillis()));
          }
        } catch (Exception e) {
          LOGGER.warn(e.getMessage());
          LOGGER.debug(e.getMessage(), e);
        }
      }
    } else {
      LOGGER.debug("Request is to a safe host, wont look for a transfer of trust to this host. {} ",request.getRequestURL().toString());
    }
    return null;
  }

  public boolean isMethodSafe(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return true;
    }
    String method = hrequest.getMethod();
    boolean safeHost = isSafeHost(hrequest);

    // protect against POST originating from other domains, this assumes that there is no
    // browser bug in this area
    // and no flash bug.
    if (!("GET".equals(method) || "HEAD".equals(method))) {
      String path = hrequest.getRequestURI();
      for (String okPostStem : postWhiteList) {
        if (path.startsWith(okPostStem)) {
          return true;
        }
      }
      // check the Referer
      @SuppressWarnings("unchecked")
      Enumeration<String> referers = hrequest.getHeaders("Referer");
      String referer = null;
      if (referers == null || !referers.hasMoreElements()) {
        LOGGER.debug("No Referer header present ");
        hresponse.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "POST Requests with no Referer are not acceptable");
        return false;
      }
      referer = referers.nextElement();
      if (referer == null) {
        LOGGER.debug("No Referer header present, was null ");
        hresponse.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "POST Requests with no Referer are not acceptable");
        return false;
      }

      // Do we allow non get operations to this host ?
      if (safeHost) {
        // and if we do, do we accept them from the Referer mentioned ?
        safeHost = false;
        for (String safeReferer : safeReferers) {
          if (referer.startsWith(safeReferer)) {
            safeHost = true;
            LOGGER.debug("Accepted referred {}  {}", safeReferer, referer);
            break;
          } else {
            LOGGER.debug("Rejecting referred {}  {}", safeReferer, referer);
          }
        }
      }
      if (!safeHost) {
        hresponse
            .sendError(
                HttpServletResponse.SC_BAD_REQUEST,
                "POST Requests are only accepted from the Application, this request was not from the application.");
        return false;
      }
    }
    return true;
  }

  public boolean isSafeHost(HttpServletRequest hrequest) {
    if ( disableProtectionForDevMode ) {
      LOGGER.warn("XSS Protection is disabled");
      return true;
    }
    // special case for ssl referers, which come in with no port, usually
    if ( "https".equals(hrequest.getScheme()) ) {
      String portlessHost = "https://" + hrequest.getServerName();
      if ( safeHosts.contains(portlessHost)) {
        return true;
      }
    }

    String requestHost = hrequest.getScheme() + "://" + hrequest.getServerName() + ":"
          + hrequest.getServerPort();
    // safe hosts are defiend as hosts from which we we can accept non get operations
    return safeHosts.contains(requestHost);
  }

  public void bindServerProtectionValidator(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionValidatorsStore.put(serviceReference,
          (ServerProtectionValidator) bundleContext.getService(serviceReference));
      serverProtectionValidators = serverProtectionValidatorsStore.values().toArray(
          new ServerProtectionValidator[serverProtectionValidatorsStore.size()]);
    }
  }

  public void unbindServerProtectionValidator(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionValidatorsStore.remove(serviceReference);
      bundleContext.ungetService(serviceReference);
      serverProtectionValidators = serverProtectionValidatorsStore.values().toArray(
          new ServerProtectionValidator[serverProtectionValidatorsStore.size()]);
    }
  }

  public void bindServerProtectionVeto(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionVetosStore.put(serviceReference,
          (ServerProtectionVeto) bundleContext.getService(serviceReference));
      serverProtectionVetos = serverProtectionVetosStore.values().toArray(
          new ServerProtectionVeto[serverProtectionVetosStore.size()]);
    }
  }

  public void unbindServerProtectionVeto(ServiceReference serviceReference) {
    if (bundleContext != null) {
      serverProtectionVetosStore.remove(serviceReference);
      bundleContext.ungetService(serviceReference);
      serverProtectionVetos = serverProtectionVetosStore.values().toArray(
          new ServerProtectionVeto[serverProtectionVetosStore.size()]);
    }
  }

}
