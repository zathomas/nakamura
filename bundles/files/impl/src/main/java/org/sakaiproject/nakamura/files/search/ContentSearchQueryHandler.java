/*
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
package org.sakaiproject.nakamura.files.search;

import com.google.common.collect.ImmutableMap;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.Query;

import java.util.Formatter;
import java.util.Map;

@Component(inherit=true)
@Properties({
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value="ContentSearchQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "ContentSearchQueryHandler")
})
public class ContentSearchQueryHandler extends AbstractContentSearchQueryHandler {
  private static final String Q_FORMAT =
      "(content:(%s) OR filename:(%<s) OR description:(%<s) OR ngram:(%<s) OR edgengram:(%<s) OR widgetdata:(%<s))";
  private static Map<String, Object> QUERY_OPTIONS_MAP = ImmutableMap.<String, Object> of(
      CommonParams.FL, "path",
      GroupParams.GROUP, Boolean.TRUE,
      GroupParams.GROUP_FIELD, "returnpath",
      GroupParams.GROUP_TOTAL_COUNT, Boolean.TRUE
  );

  public enum REQUEST_PARAMS {
    q,
    mimetype
  }

  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    return "resourceType:(sakai/pooled-content OR sakai/widget-data)";
  }

  @Override
  public void refineQuery(Map<String, String> parametersMap, Query query) {
    query.getOptions().putAll(QUERY_OPTIONS_MAP);
  }

  @Override
  public StringBuilder refineQString(Map<String, String> parametersMap, StringBuilder qBuilder) {
    // Did the client specify a text search?
    String qParam = getSearchParam(parametersMap, REQUEST_PARAMS.q.toString());
    if (qParam != null) {
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      (new Formatter(qBuilder)).format(Q_FORMAT, qParam);
    }
    // MimeType filter.
    String mimeTypeParam = getSearchParam(parametersMap, REQUEST_PARAMS.mimetype.toString());
    if (mimeTypeParam != null) {
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      qBuilder.append("mimeType:").append(mimeTypeParam);
    }
    return qBuilder;
  }
}
