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
package org.sakaiproject.nakamura.api.resource.lite;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.RemoveProperty;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class LiteJsonImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteJsonImporter.class);
  private static Map<String, Class<?>> TYPES = null;

  static {
    Builder<String, Class<?>> b = ImmutableMap.builder();
    b.put("TypeInteger", Integer.class);
    b.put("TypeLong", Long.class);
    b.put("TypeDouble", Double.class);
    b.put("TypeBigDecimal", BigDecimal.class);
    b.put("TypeDate", Calendar.class);
    b.put("TypeString", String.class);
    b.put("TypeBoolean", Boolean.class);
    TYPES = b.build();
  }
  
  public void importContent(ContentManager contentManager, JSONObject json,
      String path, boolean continueIfExists, boolean replaceProperties, boolean removeTree, AccessControlManager accessControlManager) throws JSONException, StorageClientException, AccessDeniedException  {
    if ( !continueIfExists && contentManager.get(path) != null) {
      LOGGER.debug("replace=false and path exists, so discontinuing JSON import: " + path);
      return;
    }
    if ( removeTree ) {
      for ( Iterator<String> i = contentManager.listChildPaths(path); i.hasNext(); ) {
        String childPath = i.next();
        LOGGER.info("Deleting {} ",childPath);
        StorageClientUtils.deleteTree(contentManager, childPath);
        LOGGER.info("Done Deleting {} ",childPath);
      }
    }
    internalImportContent(contentManager, json, path, replaceProperties, accessControlManager);
  }
  public void internalImportContent(ContentManager contentManager, JSONObject json,
      String path, boolean replaceProperties, AccessControlManager accessControlManager) throws JSONException, StorageClientException, AccessDeniedException {
    Iterator<String> keys = json.keys();
    Map<String, Object> properties = new HashMap<String, Object>();
    List<AclModification> modifications = Lists.newArrayList();
    while (keys.hasNext()) {

      String key = keys.next();
      if (!JcrUtils.isJCRProperty(key)) {
        Object obj = json.get(key);

        String pathKey = getPathElement(key);
        Class<?> typeHint = getElementType(key);

        if (obj instanceof JSONObject) {
          if ( key.endsWith("@grant")) {
            JSONObject acl = (JSONObject) obj;
            int bitmap = getPermissionBitMap(acl.getJSONArray("permission"));
            Operation op = getOperation(acl.getString("operation"));
            modifications.add(new AclModification(AclModification.grantKey(pathKey), bitmap, op));
          } else if ( key.endsWith("@deny")) {
            JSONObject acl = (JSONObject) obj;
            int bitmap = getPermissionBitMap(acl.getJSONArray("permission"));
            Operation op = getOperation(acl.getString("operation"));
            modifications.add(new AclModification(AclModification.denyKey(pathKey), bitmap, op));
          } else if ( key.endsWith("@Delete") ) {
            StorageClientUtils.deleteTree(contentManager, path + "/" + pathKey);
          } else {
            // need to do somethingwith delete here
            internalImportContent(contentManager, (JSONObject) obj, path + "/" + pathKey, replaceProperties, accessControlManager);
          }
        } else if (obj instanceof JSONArray) {
          if ( key.endsWith("@Delete") ) {
            properties.put(pathKey, new RemoveProperty());
          } else {
            // This represents a multivalued property
            JSONArray arr = (JSONArray) obj;
            properties.put(pathKey, getArray(arr, typeHint));
          }
        } else {
          if ( key.endsWith("@Delete") ) {
            properties.put(pathKey, new RemoveProperty());
          } else {
            properties.put(pathKey, getObject(obj, typeHint));
          }
        }
      }
    }
    Content content = contentManager.get(path);
    if (content == null) {
      contentManager.update(new Content(path, properties));
      LOGGER.info("Created Node {} {}",path,properties);
    } else {
      for (Entry<String, Object> e : properties.entrySet()) {
        if ( replaceProperties || !content.hasProperty(e.getKey())) {
          LOGGER.info("Updated Node {} {} {} ",new Object[]{path,e.getKey(), e.getValue()});
          content.setProperty(e.getKey(), e.getValue());
        }
      }
      contentManager.update(content);
    }
    if ( modifications.size() > 0 ) {
      accessControlManager.setAcl(Security.ZONE_CONTENT, path, modifications.toArray(new AclModification[modifications.size()]));
    }
  }
  protected Operation getOperation(String op) {
    op = op.toLowerCase();
    if ( op.equals("replace")) {
      return Operation.OP_REPLACE;
    } else if ( op.equals("and")) {
      return Operation.OP_AND;
    } else if ( op.equals("or")) {
      return Operation.OP_OR;
    } else if ( op.equals("xor")) {
      return Operation.OP_XOR;
    } else if ( op.equals("not")) {
      return Operation.OP_NOT;
    } else if ( op.equals("del")) {
      return Operation.OP_DEL;
    }
    return Operation.OP_REPLACE;
  }

  protected int getPermissionBitMap(JSONArray jsonArray) throws JSONException {
    int bitmap = 0;
    for ( int i = 0; i < jsonArray.length(); i++ ) {
      bitmap = bitmap | Permissions.parse(jsonArray.getString(i).toLowerCase()).getPermission();
    }
    return bitmap;
  }

  protected Class<?> getElementType(String key) {
    if ( key == null || key.length() == 0) {
      return Object.class;
    }
    String[] keyparts = StringUtils.split(key, "@",2);
    if ( keyparts.length < 2 ) {
      return Object.class;
    }
    Class<?> type = TYPES.get(keyparts[1]);
    if ( type == null ) {
      return Object.class;
    }
    return type;
  }

  protected String getPathElement(String key) {
    if ( key != null && key.length() > 0 && !"@".equals(key)) {
      if ( key.startsWith("@")) {
        return "";
      }
      return StringUtils.split(key, "@", 2)[0];
    }
    return null;
  }
  @SuppressWarnings("unchecked")
  protected <T> T[] getArray(JSONArray arr, Class<T> typeHint) throws JSONException {
    if ( arr.length() == 0 ) {
      if (typeHint.equals(Object.class)) {
        return (T[]) new String[0];
      } else {
        return (T[]) Array.newInstance(typeHint, 0);
      }
    }
    Object o = getObject(arr.get(0), typeHint);
    T[] array = (T[]) Array.newInstance(o.getClass(), arr.length());
    for ( int i = 0; i < arr.length(); i++ ) {
      array[i] = getObject(arr.get(i), typeHint);
    }
    return array;
  }
  @SuppressWarnings("unchecked")
  protected <T> T getObject(Object obj, Class<T> type) {
    if ( type.equals(Object.class)) {
      return (T) obj; // no type hint, just accept the json parser
    }else if ( type.equals(String.class)) {
      return (T) String.valueOf(obj);
    } else if ( type.equals(Integer.class) ) {
      if ( obj instanceof Integer ) {
        return (T) obj;
      } else {
        return (T) ((Integer)Integer.parseInt(String.valueOf(obj)));
      }
    } else if ( type.equals(Long.class)) {
      if ( obj instanceof Long) {
        return (T) obj;
      } else {
        return (T) ((Long)Long.parseLong(String.valueOf(obj)));
      }
    } else if ( type.equals(Double.class)) {
      if ( obj instanceof Double) {
        return (T) obj;
      } else {
        return (T) ((Double)Double.parseDouble(String.valueOf(obj)));
      }
    } else if ( type.equals(BigDecimal.class)) {
      if ( obj instanceof BigDecimal) {
        return (T) obj;
      } else {
        return (T) ( new BigDecimal(String.valueOf(obj)));
      }
    } else if ( type.equals(Calendar.class)) {
      if ( obj instanceof Calendar) {
        return (T) obj;
      } else {
        return (T) new ISO8601Date(String.valueOf(obj));
      }
    } else if ( type.equals(Boolean.class)) {
      if ( obj instanceof Boolean) {
        return (T) obj;
      } else {
        return (T) ((Boolean)Boolean.parseBoolean(String.valueOf(obj)));
      }
    }
    return null;
  }
}
