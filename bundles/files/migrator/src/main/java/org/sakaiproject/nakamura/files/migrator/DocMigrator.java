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

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

@Service
@Component(enabled = true)
public class DocMigrator implements FileMigrationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);

  @Reference
  protected Repository repository;

  public static final ThreadLocal javascriptThreadContext = new ThreadLocal();
  public static final ThreadLocal javascriptThreadScope = new ThreadLocal();

  private static final Set TIME_PROPS = ImmutableSet.of("_created", "_lastModified", "time", FilesConstants.SCHEMA_VERSION);
  
  private ClassLoader classLoader = DocMigrator.class.getClassLoader();

  public DocMigrator() {
    threadInit();
  }
  
  private Context threadInit() {
    Context cx = (Context) javascriptThreadContext.get();
    if (cx != null) {
      return cx;
    } else {
      LOGGER.debug("Initializing JavaScript migrator on a new thread.");
      cx = ContextFactory.getGlobal().enterContext();
      cx.setOptimizationLevel(-1);
      cx.setLanguageVersion(Context.VERSION_1_6);
      ScriptableObject sharedScope = cx.initStandardObjects();
      Global global = Main.getGlobal();
      if (!global.isInitialized()) {
        global.init(cx);
        Main.setOut(System.out);
        InputStream envJsStream = classLoader.getResourceAsStream("env.rhino.js");
        InputStream jqueryStream = classLoader.getResourceAsStream("jquery.js");
        InputStream migratorStream = classLoader.getResourceAsStream("migrator.js");
        Script envJs = null;
        Script jquery = null;
        Script migrator = null;
        try {
          envJs = cx.compileReader(new InputStreamReader(envJsStream),"env.rhino.js", 1, null);
          jquery = cx.compileReader(new InputStreamReader(jqueryStream), "jquery.js", 1, null);
          migrator = cx.compileReader(new InputStreamReader(migratorStream), "migrator.js", 1, null);
        } catch (IOException e) {
          LOGGER.error(e.getMessage());
        }
        envJs.exec(cx, global);
        jquery.exec(cx, global);
        migrator.exec(cx, global);
      }
      javascriptThreadContext.set(cx);
      javascriptThreadScope.set(sharedScope);
      return cx;
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

  private boolean isNotSakaiDoc(Content content) {
    return !content.hasProperty("structure0");
  }

  private boolean contentHasUpToDateStructure(Content content) throws SakaiDocMigrationException {
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    try {
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, content, false, -1);
      String structureString = (String)content.getProperty("structure0");
      String docJson = stringWriter.toString();
      Object result = callFunction(getScope(), "requiresMigration", new Object[]{structureString, docJson, false});
      if (result instanceof Boolean) {
        return !(Boolean)result;
      } else {
        throw new SakaiDocMigrationException();
      }
    } catch (JSONException e) {
      LOGGER.error(e.getLocalizedMessage());
      return false;
    }
  }

  private boolean schemaVersionIsCurrent(Content content) {
    return (content.hasProperty(FilesConstants.SCHEMA_VERSION)
        && StorageClientUtils.toInt(content.getProperty(FilesConstants.SCHEMA_VERSION)) >= CURRENT_SCHEMA_VERSION);
  }

  @Override
  public Content migrateFileContent(Content content) {
    if (content == null) {
      return null;
    }
    LOGGER.debug("Starting migration of {}", content.getPath());
    Content returnContent = content;
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, content, false,  -1);
      adminSession = repository.loginAdministrative();
      JSONObject newPageStructure = migratePageStructure(stringWriter.toString());
      validateStructure(newPageStructure);
      LOGGER.debug("Generated new page structure. Saving content {}", content.getPath());
      LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
      liteJsonImporter.importContent(adminSession.getContentManager(), newPageStructure, content.getPath(), true, true, true, adminSession.getAccessControlManager());
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

  private void validateStructure(JSONObject newPageStructure) throws JSONException, SakaiDocMigrationException {
    if (newPageStructure.get(FilesConstants.SCHEMA_VERSION) == null) {
      throw new SakaiDocMigrationException();
    }
    LOGGER.debug("new page structure passes validation.");
  }

  private Scriptable getScope() {
    Context cx = threadInit();
    Scriptable scope = cx.newObject((ScriptableObject) javascriptThreadScope.get());
    scope.setPrototype((ScriptableObject) javascriptThreadScope.get());
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
    Context context = threadInit();
    Function f = (Function) Main.getGlobal().get(functionName, scope);
    return f.call(context, scope, scope, args);
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