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

package org.sakaiproject.nakamura.api.files;

import java.util.Map;

public class File {

  private String creator;

  private String filename;

  private String contentType;

  private String poolID;

  private Map<String, Object> properties;

  public File(String creator, String filename, String contentType, String poolID, Map<String, Object> properties) {
    this.creator = creator;
    this.filename = filename;
    this.contentType = contentType;
    this.poolID = poolID;
    this.properties = properties;
  }

  public String getCreator() {
    return creator;
  }

  public String getFilename() {
    return filename;
  }

  public String getContentType() {
    return contentType;
  }

  public String getPoolID() {
    return poolID;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }
}
