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
package org.sakaiproject.nakamura.files;

import com.google.common.collect.ImmutableSet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DocMigrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);

  private static final Set TIME_PROPS = ImmutableSet.of("_created", "_lastModified", "time");

  private final Context cx;
  private ScriptableObject sharedScope = null;

  public DocMigrator() {
    cx = ContextFactory.getGlobal().enterContext();
    cx.setOptimizationLevel(-1);
    cx.setLanguageVersion(Context.VERSION_1_6);
    sharedScope = cx.initStandardObjects();
    Global global = Main.getGlobal();
    if (!global.isInitialized()) {
      global.init(cx);
      Main.setOut(System.out);
      Main.processSource(cx, "/Users/zach/opt/envjs/dist/env.rhino.js");
      Main.processSource(cx, "/Users/zach/dev/nakamura/bundles/files/impl/src/main/resources/jquery.js");
      Main.processSource(cx, "/Users/zach/dev/nakamura/bundles/files/impl/src/main/resources/hello.js");
    }
  }
  public boolean contentNeedsMigration(Content poolContent) {
    return !(poolContent.hasProperty("sakai:schemaversion") && StorageClientUtils.toInt(poolContent.getProperty("sakai:schemaversion")) >= 2);
  }

  private boolean pageDefinitionsMissingRows(Content poolContent) {
    boolean missingRows = false;

    return missingRows;
  }

  private boolean contentHasStructure(Content poolContent) {
    return poolContent.hasProperty("structure0");
  }

  public boolean requiresMigration(String structureString) throws Exception {
    return (Boolean)callFunction(getScope(), "requiresMigration", new Object[]{getStructure0String(structureString), structureString, Boolean.FALSE});
  }

  private Scriptable getScope() {
    Scriptable scope = cx.newObject(sharedScope);
    scope.setPrototype(sharedScope);
    scope.setParentScope(null);
    return scope;
  }

  public String processStructure(String structureString) throws Exception {
    Object result = callFunction(getScope(), "processStructure0", new Object[]{getStructure0String(structureString), structureString, new NativeObject()});
    return convertToJson((NativeObject) result).toString();
  }

  public String getMigratedPageStructureString(String structureString) throws Exception {
    return migratePageStructure(structureString).toString();
  }

  public JSONObject migratePageStructure(String structureString) throws Exception {
    Object result = callFunction(getScope(), "migratePageStructure", new Object[]{structureString});
    return convertToJson((NativeObject)result);
  }

  private Object callFunction(Scriptable scope, String functionName, Object[] args) {
    Function f = (Function) Main.getGlobal().get(functionName, scope);
    return f.call(cx, scope, scope, args);
  }

  private String getStructure0String(String structureString) throws Exception {
    JSONObject pageStructure = new JSONObject(structureString);
    JSONObject structure0 = new JSONObject(pageStructure.getString("structure0"));
    return structure0.toString();
  }

  private JSONObject convertToJson(NativeObject javascriptObject) throws JSONException {
    JSONObject json = new JSONObject();
     for (Map.Entry js : javascriptObject.entrySet()) {
       String key = js.getKey().toString();
       Object value = js.getValue();
       putInJson(key, value, json);
     }
    return json;
  }

  private void putInJson(String key, Object item, JSONObject jsonObject) throws JSONException {
    if (item instanceof NativeObject) {
      jsonObject.accumulate(key, convertToJson((NativeObject) item));
    } else if (item instanceof NativeArray) {
      jsonObject.put(key, new JSONArray());
      for (Object arrayItem : ((NativeArray)item).toArray()) {
        putInJson(key, arrayItem, jsonObject);
      }
    } else {
      if (TIME_PROPS.contains(key)) {
        jsonObject.accumulate(key, ((Double)item).longValue());
      } else {
        jsonObject.accumulate(key, item);
      }
    }
  }

  public JSONObject jsonFromString(String structure) throws JSONException {
    return convertToJson((NativeObject) callFunction(getScope(), "parseJSON", new Object[]{structure}));
  }
}