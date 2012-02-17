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

import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;
import org.sakaiproject.nakamura.api.lite.content.Content;

public class DocMigrator {

  private final Context cx;
  private final ScriptableObject sharedScope;

  public DocMigrator() {
    cx = ContextFactory.getGlobal().enter();
    cx.setOptimizationLevel(-1);
    cx.setLanguageVersion(Context.VERSION_1_6);
    Global global = Main.getGlobal();
    global.init(cx);
    Main.setOut(System.out);
    sharedScope = cx.initStandardObjects();
    Main.processSource(cx, "/Users/zach/opt/envjs/dist/env.rhino.js");
    Main.processSource(cx, "/Users/zach/dev/nakamura/bundles/files/impl/src/main/resources/jquery.js");
    Main.processSource(cx, "/Users/zach/dev/nakamura/bundles/files/impl/src/main/resources/hello.js");
  }
  public boolean contentNeedsMigration(Content poolContent) {
//    return contentHasStructure(poolContent) && pageDefinitionsMissingRows(poolContent);
    return true;
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
    Object result = callFunction(getScope(), "processStructure0", new Object[]{getStructure0String(structureString), structureString, ""});
    return (String)result;
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
}