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

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;

@Service
@Component(enabled = true)
public class DocMigrator implements FileMigrationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);
  private static final String EMPTY_DIV = "<div />";
  private static final String STRUCTURE_ZERO = FilesConstants.STRUCTURE_FIELD_STEM + "0";

  @Reference
  protected Repository repository;

  protected JSONObject generateEmptyRow(int columnCount) throws JSONException {
    JSONObject row = new JSONObject();
    row.put("id", generateWidgetId());
    JSONArray columns = new JSONArray();
    for (int i = 0; i < columnCount; i++) {
      JSONObject column = new JSONObject();
      column.put("width", 1 / columnCount);
      column.put("elements", new JSONArray());
      columns.put(column);
    }
    row.put("columns", columns);
    return row;
  }
  
  protected String generateWidgetId() {
    return "id" + Math.round(Math.random() * 10000000);
  }
  
  protected void ensureRowPresent(JSONObject page) throws JSONException {
    JSONArray rows = page.getJSONArray("rows");
    if (rows == null || rows.length() < 1) {
      rows = new JSONArray();
      rows.put(generateEmptyRow(1));
      page.put("rows", rows);
    }
  }
  
  protected void generateNewCell(String id, String type, JSONObject page, JSONObject row, int column, JSONObject widgetData) throws JSONException {
    if (!"tooltip".equals(type) && !"joinrequestbuttons".equals(type)) {
      String columnId = id == null ? generateWidgetId() : id;
      JSONObject element = new JSONObject();
      element.put("id", columnId);
      element.put("type", type);
      row.getJSONArray("columns").getJSONObject(column).accumulate("elements", element);
      if (widgetData != null) {
        page.put(columnId, widgetData);
      }
    }
  }
  
  protected JSONObject addRowToPage(JSONObject row, JSONObject page, int columnsForNextRow, Element htmlElement) throws JSONException {
    if (!isEmpty(htmlElement)) {
      generateNewCell(null, "htmlblock", page, row, 0, generateHtmlBlock(htmlElement.html()));
    }
    boolean rowHasContent = false;
    for (int i = 0; i < row.getJSONArray("columns").length(); i++) {
      if (row.getJSONArray("columns").getJSONObject(i).getJSONArray("elements").length() > 0) {
        rowHasContent = true;
        break;
      }
    }
    boolean rowAlreadyPresent = false;
    for (int i = 0; i < page.getJSONArray("rows").length(); i++) {
      if ( row == page.getJSONArray("rows").getJSONObject(i)) {
        rowAlreadyPresent = true;
        break;
      }
    }
    if (rowHasContent && !rowAlreadyPresent) {
      page.accumulate("rows", row);
    }

    return generateEmptyRow(columnsForNextRow > 0 ? columnsForNextRow : 1);
  }
  
  protected JSONObject generateHtmlBlock(String html) throws JSONException {
    JSONObject contentObject = new JSONObject();
    contentObject.put("content", html);
    JSONObject htmlBlockObject = new JSONObject();
    htmlBlockObject.put("htmlblock", contentObject);
    return htmlBlockObject;
  }
  
  boolean isEmpty(Element htmlElement) {
    // filter out TinyMCE instances
    htmlElement.select(".mceEditor").remove();
    String htmlContent = htmlElement.text().trim();
    String[] elementNames = new String[]{"img", "iframe", "frame", "input", "select", "option"};
    boolean containsElement = false;
    for (String elementName : elementNames) {
      if (!htmlElement.select(elementName).isEmpty()) {
        containsElement = true;
      }
    }
    return !(htmlElement.hasText() || containsElement);
  }
  
  protected void processStructure0(JSONObject subtree, JSONObject originalStructure, JSONObject newStructure) throws JSONException {
    Set<String> widgetsUsed = Sets.newHashSet();
    for (Iterator<String> keyIterator = subtree.keys(); keyIterator.hasNext();) {
      String key = keyIterator.next();
      if (key.startsWith("_")) {
        continue;
      }
      JSONObject structureItem = subtree.getJSONObject(key);
      String ref = structureItem.getString("_ref");
      if (originalStructure.has(ref) && originalStructure.getJSONObject(ref).has("rows")) {
        // newStructure.put(ref, originalStructure.getJSONObject(ref));
        LOGGER.debug("ref {} already has new structure.", ref);
      } else {
        // page needs migration
        Document page = Jsoup.parse(originalStructure.getJSONObject(ref).getString("page"));
        Document currentHtmlBlock = Jsoup.parse(EMPTY_DIV);
        JSONObject currentPage = new JSONObject();
        currentPage.put("rows", new JSONArray());
        JSONObject currentRow = generateEmptyRow(1);
        Elements topLevelElements = page.select("body").first().children();
        for (Element topLevelElement : topLevelElements) {
          if (topLevelElement.hasClass("widget_inline")) {
            addRowToPage(currentRow, currentPage, 0, currentHtmlBlock.select("body").first());
            currentHtmlBlock = Jsoup.parse(EMPTY_DIV);
            String[] widgetIdParts = topLevelElement.attr("id").split("_");
            String widgetType = widgetIdParts[1];
            String widgetId = widgetIdParts.length > 2 ? widgetIdParts[2] : generateWidgetId();
            generateNewCell(widgetId, widgetType, currentPage, currentRow, 0, getJSONObjectOrNull(originalStructure, widgetId));
            widgetsUsed.add(widgetId);
          } else if (topLevelElement.select(".widget_inline").size() > 0) {
            addRowToPage(currentRow, currentPage, 0, currentHtmlBlock.select("body").first());
            currentHtmlBlock = Jsoup.parse(EMPTY_DIV);
            int numColumns = 1;
            int leftSideColumn = topLevelElement.select(".widget_inline.block_image_left").size() > 0 ? 1 : 0;
            numColumns += leftSideColumn;
            int rightSideColumn = topLevelElement.select(".widget_inline.block_image_right").size() > 0 ? 1 : 0;
            numColumns += rightSideColumn;
            if (numColumns > 1) {
              currentRow = addRowToPage(currentRow, currentPage, numColumns, currentHtmlBlock.select("body").first());
            }
            for (Element widgetElement : topLevelElement.select(".widget_inline")) {
              String[] widgetIdParts = widgetElement.attr("id").split("_");
              String widgetType = widgetIdParts[1];
              String widgetId = widgetIdParts.length > 2 ? widgetIdParts[2] : generateWidgetId();
              if (widgetElement.hasClass("block_image_left")) {
                generateNewCell(widgetId, widgetType, currentPage, currentRow, 0, getJSONObjectOrNull(originalStructure, widgetId));
              } else if (widgetElement.hasClass("block_image_right")) {
                generateNewCell(widgetId, widgetType, currentPage, currentRow, (leftSideColumn > 0 ? 2 : 1), getJSONObjectOrNull(originalStructure, widgetId));
              } else {
                generateNewCell(widgetId, widgetType, currentPage, currentRow, (leftSideColumn > 0 ? 1 : 0), getJSONObjectOrNull(originalStructure, widgetId));
              }
              widgetsUsed.add(widgetId);
              if ("discussion".equals(widgetType)) {
                String newMessageStorePath = newStructure.getString("_path") + "/" + ref + "/" + widgetId + "/discussion/message";
                String newAbsoluteMessageStorePath = "/p/" + newMessageStorePath;
                JSONObject inbox = currentPage.getJSONObject(widgetId).getJSONObject("discussion").getJSONObject("message").getJSONObject("inbox");
                for(Iterator<String> inboxIterator = inbox.keys(); inboxIterator.hasNext();) {
                  String inboxKey = inboxIterator.next();
                  if (inboxKey.startsWith("_")) {
                    continue;
                  }
                  inbox.getJSONObject(inboxKey).put("sakai:to", newAbsoluteMessageStorePath);
                  inbox.getJSONObject(inboxKey).put("sakai:writeto", newAbsoluteMessageStorePath);
                  inbox.getJSONObject(inboxKey).put("sakai:messagestore", newMessageStorePath + "/");
                }
              }
              newStructure.remove(widgetId);
              widgetElement.remove();
            }
            generateNewCell(null, "htmlblock", currentPage, currentRow, (leftSideColumn > 0 ? 1 : 0), generateHtmlBlock(topLevelElement.outerHtml()));

            if (numColumns > 1) {
              currentRow = addRowToPage(currentRow, currentPage, 1, currentHtmlBlock.select("body").first());
            }

          } else {
            currentHtmlBlock.select("div").first().appendChild(topLevelElement);
          }
        }
        addRowToPage(currentRow, currentPage, 1, currentHtmlBlock.select("body").first());
        ensureRowPresent(currentPage);

        newStructure.put(ref, currentPage);
      }
      processStructure0(structureItem, originalStructure, newStructure);
    }
    // pruning the widgets at the top level
    for (String widgetId : widgetsUsed) {
      if (newStructure.has(widgetId)) {
        newStructure.remove(widgetId);
      }
    }
  }
  
  protected JSONObject createNewPageStructure(JSONObject structure0, JSONObject originalDoc) throws JSONException {
    JSONObject newDoc = new JSONObject(originalDoc.toString());
    processStructure0(structure0, originalDoc, newDoc);
    // the finishing touches
    newDoc.put(FilesConstants.SCHEMA_VERSION, 2);
    return newDoc;
  }
  
  protected JSONObject getJSONObjectOrNull(JSONObject jsonObject, String key) throws JSONException {
    if (jsonObject.has(key)) {
      return jsonObject.getJSONObject(key);
    } else {
      return null;
    }
  }

  @Override
  public boolean fileContentNeedsMigration(Content content) {
    try {
      return !(content == null || isNotSakaiDoc(content) || schemaVersionIsCurrent(content) || contentHasUpToDateStructure(content));
    } catch (SakaiDocMigrationException e) {
      LOGGER.error("Could not determine requiresMigration with content {}", content.getPath());
      throw new RuntimeException("Could not determine requiresMigration with content " + content.getPath());
    }
  }
  
  protected boolean requiresMigration(JSONObject subtree, Content originalStructure, ContentManager contentManager) throws JSONException {
    boolean requiresMigration = false;
    for (Iterator<String> keysIterator = subtree.keys(); keysIterator.hasNext();) {
      String key = keysIterator.next();
      if (!key.startsWith("_")) {
        JSONObject structureItem = subtree.getJSONObject(key);
        String ref = structureItem.getString("_ref");
        if (!contentManager.exists(originalStructure.getPath() + "/" + ref + "/rows")) {
          return true;
        }
       requiresMigration = requiresMigration(structureItem, originalStructure, contentManager); 
      }
    }
    return requiresMigration;
  }

  private boolean isNotSakaiDoc(Content content) {
    return !content.hasProperty(STRUCTURE_ZERO);
  }

  private boolean contentHasUpToDateStructure(Content content) throws SakaiDocMigrationException {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      JSONObject structure0 = new JSONObject((String)content.getProperty(STRUCTURE_ZERO));
      return !requiresMigration(structure0, content, adminSession.getContentManager());
    } catch (Exception e) {
      throw new SakaiDocMigrationException();
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  private boolean schemaVersionIsCurrent(Content content) {
    return (content.hasProperty(FilesConstants.SCHEMA_VERSION)
        && StorageClientUtils.toInt(content.getProperty(FilesConstants.SCHEMA_VERSION)) >= CURRENT_SCHEMA_VERSION);
  }

  @Override
  public void migrateFileContent(Content content) {
    if (content == null || !content.hasProperty(STRUCTURE_ZERO)) {
      return;
    }
    LOGGER.debug("Starting migration of {}", content.getPath());
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, content, false,  -1);
      adminSession = repository.loginAdministrative();
      JSONObject newPageStructure = createNewPageStructure(new JSONObject((String) content.getProperty(STRUCTURE_ZERO)), new JSONObject(stringWriter.toString()));

      JSONObject convertedStructure = (JSONObject) convertArraysToObjects(newPageStructure);
      validateStructure(convertedStructure);
      LOGGER.debug("Generated new page structure. Saving content {}", content.getPath());
      LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
      liteJsonImporter.importContent(adminSession.getContentManager(), convertedStructure, content.getPath(), true, true, true, adminSession.getAccessControlManager());
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }
  
  protected Object convertArraysToObjects(Object json) throws JSONException {
    if (json instanceof JSONObject) {
      JSONObject jsonObject = (JSONObject)json;
      for (Iterator<String> keyIterator = jsonObject.keys(); keyIterator.hasNext();) {
        String key = keyIterator.next();
        if (objectIsArrayOfJSONObject(jsonObject.get(key))) {
          jsonObject.put(key, convertArrayToObject((JSONArray) jsonObject.get(key)));
        } else if (jsonObject.get(key) instanceof JSONObject) {
          jsonObject.put(key, convertArraysToObjects(jsonObject.get(key)));
        }
      }
      return jsonObject;
    } else if (objectIsArrayOfJSONObject(json)) {
      return convertArrayToObject((JSONArray)json);
    } else {
      return json;
    }
  }

  private boolean objectIsArrayOfJSONObject(Object json) throws JSONException {
    return json instanceof JSONArray && ((JSONArray)json).length() > 0 && 
      (((JSONArray)json).get(0) instanceof JSONObject || ((JSONArray)json).get(0) instanceof JSONArray);
  }

  protected JSONObject convertArrayToObject(JSONArray jsonArray) throws JSONException {
    JSONObject arrayObject = new JSONObject();
    for(int i = 0; i < jsonArray.length(); i++) {
      Object value = convertArraysToObjects(jsonArray.get(i));
      arrayObject.put("__array__" + i + "__", convertArraysToObjects(jsonArray.get(i)));
    }
    return arrayObject;
  }

  protected void validateStructure(JSONObject newPageStructure) throws JSONException, SakaiDocMigrationException {
    if (!newPageStructure.has(FilesConstants.SCHEMA_VERSION) || !newPageStructure.has(STRUCTURE_ZERO)) {
      throw new SakaiDocMigrationException();
    }
    LOGGER.debug("new page structure passes validation.");
  }

}