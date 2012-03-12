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

import org.apache.commons.lang.StringUtils;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;

public class PageMigrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PageMigrator.class);
  
  private static final String EMPTY_DIV = "<div />";
  private final DocMigrator docMigrator;

  public PageMigrator(DocMigrator docMigrator) {
    this.docMigrator = docMigrator;
  }

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
      if (row == page.getJSONArray("rows").getJSONObject(i)) {
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

  void processPageReference(JSONObject subtree, JSONObject originalStructure, JSONObject newStructure, Set<String> widgetsUsed, String reference) throws JSONException {
    if (reference.startsWith("_")) {
      return;
    }
    JSONObject structureItem = subtree.getJSONObject(reference);
    if (!structureItem.has("_ref") || StringUtils.isBlank(structureItem.getString("_ref"))) {
      return;
    }
    LOGGER.debug("processing page {} from structure0", reference);
    String ref = structureItem.getString("_ref");
    if (!originalStructure.has(ref) || !originalStructure.getJSONObject(ref).has("rows")) {
      newStructure.put(ref, migratePage(originalStructure, newStructure.getString("_path"), widgetsUsed, ref));
    }
    docMigrator.processStructure0(structureItem, originalStructure, newStructure);
  }

  JSONObject migratePage(JSONObject originalStructure, String contentId, Set<String> widgetsUsed, String ref) throws JSONException {
    Document page = Jsoup.parse(originalStructure.getJSONObject(ref).getString("page"));
    Document currentHtmlBlock = Jsoup.parse(EMPTY_DIV);
    JSONObject currentPage = new JSONObject();
    currentPage.put("rows", new JSONArray());
    JSONObject currentRow = generateEmptyRow(1);
    Elements topLevelElements = page.select("body").first().children();
    for (Element topLevelElement : topLevelElements) {
      if (topLevelElement.select(".widget_inline").size() > 0) {
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
          extractWidget(originalStructure, contentId, widgetsUsed, ref, currentPage, currentRow, leftSideColumn, widgetElement);
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

    return currentPage;
  }

  void extractWidget(JSONObject originalStructure, String contentId, Set<String> widgetsUsed, String ref, JSONObject currentPage, JSONObject currentRow, int leftSideColumn, Element widgetElement) throws JSONException {
    String[] widgetIdParts = widgetElement.attr("id").split("_");
    String widgetType = widgetIdParts[1];
    String widgetId = widgetIdParts.length > 2 ? widgetIdParts[2] : generateWidgetId();
    int columnIndex;
    if (widgetElement.hasClass("block_image_left")) {
      columnIndex = 0;
    } else if (widgetElement.hasClass("block_image_right")) {
      columnIndex = leftSideColumn > 0 ? 2 : 1;
    } else {
      columnIndex = leftSideColumn > 0 ? 1 : 0;
    }
    generateNewCell(widgetId, widgetType, currentPage, currentRow, columnIndex, getJSONObjectOrNull(originalStructure, widgetId));
    widgetsUsed.add(widgetId);
    if ("discussion".equals(widgetType)) {
      migrateDiscussionWidget(contentId, ref, currentPage, widgetId);
    }
    widgetElement.remove();
  }

  void migrateDiscussionWidget(String contentId, String ref, JSONObject currentPage, String widgetId) throws JSONException {
    String newMessageStorePath = contentId + "/" + ref + "/" + widgetId + "/discussion/message";
    String newAbsoluteMessageStorePath = "/p/" + newMessageStorePath;
    JSONObject inbox = currentPage.getJSONObject(widgetId).getJSONObject("discussion").getJSONObject("message").getJSONObject("inbox");
    for (Iterator<String> inboxIterator = inbox.keys(); inboxIterator.hasNext(); ) {
      String inboxKey = inboxIterator.next();
      if (inboxKey.startsWith("_")) {
        continue;
      }
      inbox.getJSONObject(inboxKey).put("sakai:to", newAbsoluteMessageStorePath);
      inbox.getJSONObject(inboxKey).put("sakai:writeto", newAbsoluteMessageStorePath);
      inbox.getJSONObject(inboxKey).put("sakai:messagestore", newMessageStorePath + "/");
    }
  }

  protected JSONObject getJSONObjectOrNull(JSONObject jsonObject, String key) throws JSONException {
    if (jsonObject.has(key)) {
      return jsonObject.getJSONObject(key);
    } else {
      return null;
    }
  }
}