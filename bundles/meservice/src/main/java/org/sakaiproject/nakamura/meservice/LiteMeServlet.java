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
package org.sakaiproject.nakamura.meservice;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

import com.google.common.collect.ImmutableMap;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.perf4j.aop.Profiled;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessagingException;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.util.LocaleUtils;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TimeZone;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(name = "MeServlet", okForVersion = "1.2",
    shortDescription = "Returns information about the current active user.",
    description = "Presents information about current user in JSON format.",
    bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/me"),
    methods = @ServiceMethod(name = "GET", description = "Get information about current user.",
        parameters = { @ServiceParameter(name="uid", description="If present the user id of the me feed to be returned")},
        response = {
    @ServiceResponse(code = 200, description = "Request for information was successful. <br />"
        + "A JSON representation of the current user is returned. E.g. for an anonymous user:"
        + "<pre>{\n" +
      "    \"user\": {\n" +
      "        \"anon\": true,\n" +
      "        \"subjects\": [],\n" +
      "        \"superUser\": false\n" +
      "    },\n" +
      "    \"profile\": {\n" +
      "        \"basic\": {\n" +
      "            \"access\": \"everybody\",\n" +
      "            \"elements\": {\n" +
      "                \"lastName\": {\n" +
      "                    \"value\": \"User\"\n" +
      "                },\n" +
      "                \"email\": {\n" +
      "                    \"value\": \"anon@sakai.invalid\"\n" +
      "                },\n" +
      "                \"firstName\": {\n" +
      "                    \"value\": \"Anonymous\"\n" +
      "                }\n" +
      "            }\n" +
      "        },\n" +
      "        \"rep:userId\": \"anonymous\"\n" +
      "    },\n" +
      "    \"messages\": {\n" +
      "        \"unread\": 0\n" +
      "    },\n" +
      "    \"contacts\": {},\n" +
      "    \"groups\": []\n" +
      "}<pre>"),
    @ServiceResponse(code = 401, description = "Unauthorized: credentials provided were not acceptable to return information for."),
    @ServiceResponse(code = 500, description = "Unable to return information about current user.") }))
@SlingServlet(paths = { "/system/me" }, generateComponent = false, methods = { "GET" })
@Component
public class LiteMeServlet extends SlingSafeMethodsServlet {
  private static final long serialVersionUID = -3786472219389695181L;
  private static final Logger LOG = LoggerFactory.getLogger(LiteMeServlet.class);

  private static final String PROFILE_ELEMENTS[] = {UserConstants.USER_FIRSTNAME_PROPERTY,
    UserConstants.USER_LASTNAME_PROPERTY, UserConstants.USER_EMAIL_PROPERTY, UserConstants.USER_PICTURE};

  @Reference
  protected transient LiteMessagingService messagingService;

  @Reference
  protected transient ConnectionManager connectionManager;

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  @Reference
  protected BasicUserInfoService basicUserInfoService;

  @Reference
  protected DynamicContentResponseCache dynamicContentResponseCache;

  @Reference
  protected transient LocaleUtils localeUtils;

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    if (dynamicContentResponseCache.send304WhenClientHasFreshETag(UserConstants.USER_RESPONSE_CACHE, request, response)) {
      return;
    }
    getUncached(request, response);

  }

  /**
   * Perform the standard get operation, bypassing the etag cache.
   * 
   * @param request
   * @param response
   * @throws IOException
   * @see {@link #doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   */
  @Profiled(tag="meservice:LiteMeServlet:/system/me")
  private void getUncached(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws IOException {
    TelemetryCounter.incrementValue("meservice", "LiteMeServlet", "/system/me");
    try {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      final Session session = StorageClientUtils.adaptToSession(request
          .getResourceResolver().adaptTo(javax.jcr.Session.class));
      if (session == null) {
        LOG.error("########## session is null");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
        "Access denied error.");
        return;
      }
      AuthorizableManager um = session.getAuthorizableManager();
      String userId = session.getUserId();
      String requestedUserId = request.getParameter("uid");
      if ( requestedUserId != null && requestedUserId.length() > 0) {
        userId = requestedUserId;
      }
      Authorizable au = um.findAuthorizable(userId);
      if ( au == null ) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,"User "+userId+" not found.");
        return;
      }
      PrintWriter w = response.getWriter();
      ExtendedJSONWriter writer = new ExtendedJSONWriter(w);
      writer.object(); // start output object

      writer.key("userid");
      writer.value(userId);

      Map<String, Object> userProps = basicUserInfoService.getProperties(au);
      Map<String, Object> counts = (Map<String, Object>)userProps.remove(UserConstants.COUNTS_PROP);

      // Dump this user his info
      writeProfile(userProps, writer);
      writeLocale(writer, localeUtils.getProperties(au), request);

      writeCounts(request, response, session, au, writer, counts);

      dynamicContentResponseCache.recordResponse(UserConstants.USER_RESPONSE_CACHE, request, response);

      writer.endObject(); // end output object
    } catch (JSONException e) {
      LOG.error("Failed to create proper JSON response in /system/me", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to create proper JSON response.");
    } catch (StorageClientException e) {
      LOG.error("Failed to get a user his profile node in /system/me", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Sparse storage client error.");
    } catch (AccessDeniedException e) {
      LOG.error("Failed to get a user his profile node in /system/me", e);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          "Access denied error.");
    } catch (MessagingException e) {
      LOG.error("Failed to get a user his message counts in /system/me", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Messaging error.");
    } catch (SolrSearchException e) {
      LOG.error("Failed to execute a Solr search in /system/me", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Solr search error.");
    }
  }

  /**
   * @param props
   * @param writer
   * @return
   * @throws JSONException
   */
  protected void writeProfile(Map<String, Object> props, ExtendedJSONWriter writer)
      throws JSONException {
    writer.key("homePath");
    writer.value(props.get("homePath"));
    writer.key("profile");

    Map<String, Object> filteredProps = new HashMap<String, Object>();

    for (String profileElement : PROFILE_ELEMENTS) {
      if (props.containsKey(profileElement)) {
        filteredProps.put(profileElement, props.get(profileElement));
      }
    }

    ValueMap profileMap = new ValueMapDecorator (filteredProps);
    writer.valueMap(profileMap);
  }

  /**
   * @param request
   * @param session
   * @param au
   * @param writer
   * @param counts
   * @throws JSONException
   * @throws SolrSearchException
   */
  protected void writeCounts(SlingHttpServletRequest request,
      SlingHttpServletResponse response, Session session, Authorizable au,
      ExtendedJSONWriter writer, Map<String, Object> counts) throws JSONException,
      SolrSearchException, IOException {
    writer.key(UserConstants.COUNTS_PROP);
    writer.object(); // start "counts"

    writer.key("content");
    writer.value(counts.get(UserConstants.CONTENT_ITEMS_PROP));

    writer.key("contacts");
    writer.value(counts.get(UserConstants.CONTACTS_PROP));

    writer.key("memberships");
    writer.value(counts.get(UserConstants.GROUP_MEMBERSHIPS_PROP));

    /*
     * TODO unreadmessages and collections come from solr queries. If possible, these
     * should be stored on the authorizable to save recalculating them every time.
     */
    writer.key("unreadmessages");
    writer.value(getUnreadMessageCount(session, au, request));

    // this is just nasty. we should move away from http calls and just call solr directly
    // if we can't store on the authorizable.
//    writer.key("collections");
//    JSONObject json = new JSONObject();
//    json.put("url", "/var/search/pool/auth-all.json");
//    json.put("method", "GET");
//
//    // /var/search/pool/auth-all.json?
//    //   mimetype=x-sakai/collection
//    //   page=0
//    //   items=0
//    JSONObject params = new JSONObject();
//    params.put("_charset_", "utf-8");
//    params.put("page", 0);
//    params.put("items", 0);
//
//    json.put("parameters", params);
//
//    RequestInfo requestInfo = new RequestInfo(json);
//    RequestWrapper requestWrapper = new RequestWrapper(request, requestInfo);
//    ResponseWrapper responseWrapper = new ResponseWrapper(response);
//    request.getRequestDispatcher(requestInfo.getUrl()).forward(requestWrapper, responseWrapper);
//    String jsonStr = responseWrapper.getDataAsString();
//    JSONObject jsonCount = new JSONObject(jsonStr);
//    int collectionsCount = jsonCount.getInt("count");
//    writer.value(collectionsCount);

    writer.endObject(); // end "counts"
  }

  /**
   * Writes a JSON Object that contains the unread messages for a user.
   *
   * @param session
   *          A JCR session to perform queries with. This session needs read access on the
   *          authorizable's message box.
   * @param au
   *          An authorizable to look up the messages for.
   * @param request
   * @throws JSONException
   * @throws RepositoryException
   * @throws MessagingException
   * @throws SolrSearchException
   */
  protected long getUnreadMessageCount(Session session, Authorizable au,
      SlingHttpServletRequest request) throws JSONException, MessagingException,
      SolrSearchException {
    // We don't do queries for anonymous users. (Possible ddos hole).
    String userID = au.getId();
    if (UserConstants.ANON_USERID.equals(userID)) {
      return 0;
    }

    String store = messagingService.getFullPathToStore(au.getId(), session);
    store = ISO9075.encodePath(store);
    String queryString = "messagestore:" + ClientUtils.escapeQueryChars(store) + " AND type:internal AND messagebox:inbox AND read:false";
    final Map<String, Object> queryOptions = ImmutableMap.of(
        PARAMS_ITEMS_PER_PAGE, (Object) "0",
        CommonParams.START, "0"
    );
    Query query = new Query(queryString, queryOptions);
    LOG.debug("Submitting Query {} ", query);
    SolrSearchResultSet resultSet = searchServiceFactory.getSearchResultSet(
        request, query, false);
    long count = resultSet.getSize();
    return count;
  }

  /**
   * Writes the local and timezone information.
   *
   * @param write
   * @param properties
   * @throws JSONException
   */
  protected void writeLocale(ExtendedJSONWriter write, Map<String, Object> properties,
      SlingHttpServletRequest request) throws JSONException {

    final Locale locale = localeUtils.getLocale(properties);
    final TimeZone tz = localeUtils.getTimeZone(properties);

    /* Add the locale information into the output */
    write.key("locale");
    write.object(); // start "locale"
    write.key("country");
    write.value(locale.getCountry());
    write.key("displayCountry");
    write.value(locale.getDisplayCountry(locale));
    write.key("displayLanguage");
    write.value(locale.getDisplayLanguage(locale));
    write.key("displayName");
    write.value(locale.getDisplayName(locale));
    write.key("ISO3Country");
    try {
      write.value(locale.getISO3Country());
    } catch (MissingResourceException e) {
      write.value("");
      LOG.debug("Unable to find ISO3 country [{}]", locale);
    }
    write.key("ISO3Language");
    try {
      write.value(locale.getISO3Language());
    } catch (MissingResourceException e) {
      write.value("");
      LOG.debug("Unable to find ISO3 language [{}]", locale);
    }
    write.key("language");
    write.value(locale.getLanguage());

    /* Add the timezone information into the output */
    write.key("timezone");
    write.object(); // start "timezone"
    write.key("name");
    write.value(tz.getID());
    write.key("GMT");
    write.value(localeUtils.getOffset(tz));
    write.endObject();

    write.endObject(); // end "locale"
  }
}
