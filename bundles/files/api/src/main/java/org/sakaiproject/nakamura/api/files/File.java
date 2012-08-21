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

/**
 * Represents a pooled content item that is in persistent storage.
 */
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

  /**
   * @return The user ID of the user who created this file.
   */
  public String getCreator() {
    return creator;
  }

  /**
   * @return The logical file name of the file.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * @return The content type (mimeType) of the file.
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * @return The internal storage ID of the file. Null when creating a new file.
   */
  public String getPoolID() {
    return poolID;
  }

  /**
   * @return A map of properties that are set on the file.
   */
  public Map<String, Object> getProperties() {
    return properties;
  }
}
