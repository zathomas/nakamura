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
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
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
import org.sakaiproject.nakamura.api.files.FileMigrationCheck;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Component(metatype = true, enabled = true)
public class DocMigrator implements FileMigrationCheck, FileMigrationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);

  @Reference
  protected Repository repository;

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

  @Override
  public boolean fileContentNeedsMigration(Content content) {
    return !(content.hasProperty("sakai:schemaversion") && StorageClientUtils.toInt(content.getProperty("sakai:schemaversion")) >= CURRENT_SCHEMA_VERSION);
  }

  @Override
  public Content migrateFileContent(Content content) {
    Content returnContent = content;
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, content, false,  -1);
      adminSession = repository.loginAdministrative();
      JSONObject newPageStructure = migratePageStructure(stringWriter.toString());
      LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
      liteJsonImporter.internalImportContent(adminSession.getContentManager(), newPageStructure, content.getPath(), Boolean.TRUE, adminSession.getAccessControlManager());
      returnContent = adminSession.getContentManager().get(content.getPath());
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
    return returnContent;
  }

  private Scriptable getScope() {
    Scriptable scope = cx.newObject(sharedScope);
    scope.setPrototype(sharedScope);
    scope.setParentScope(null);
    return scope;
  }

  protected String getMigratedPageStructureString(String structureString) throws Exception {
    return migratePageStructure(structureString).toString();
  }

  private JSONObject migratePageStructure(String structureString) throws JSONException {
    Object result = callFunction(getScope(), "migratePageStructure", new Object[]{structureString});
    return convertToJson((NativeObject)result);
  }

  private Object callFunction(Scriptable scope, String functionName, Object[] args) {
    Function f = (Function) Main.getGlobal().get(functionName, scope);
    return f.call(cx, scope, scope, args);
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
}