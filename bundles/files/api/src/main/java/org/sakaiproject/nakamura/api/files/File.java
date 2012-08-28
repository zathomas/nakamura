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

import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import java.util.Map;

/**
 * Represents a pooled content item that is in persistent storage.
 */
@PersistenceCapable
public class File {

  @Persistent
  private String creator;

  @Persistent
  private String filename;

  @Persistent
  private String contentType;

  @Persistent @PrimaryKey
  private String poolID;

  @NotPersistent
  private Map<String, String> properties;

  public File(String creator, String filename, String contentType, String poolID, Map<String, String> properties) {
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
  public Map<String, String> getProperties() {
    return properties;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public void setPoolID(String poolID) {
    this.poolID = poolID;
  }

  public void setProperties(Map<String, String> properties) {
    this.properties = properties;
  }

  public void updateFromParams(FileParams fileParams) {
    this.filename = fileParams.getFilename();
    this.creator = fileParams.getCreator();
    this.poolID = fileParams.getPoolID();
    this.contentType = fileParams.getContentType();
    this.properties = fileParams.getProperties();
  }
}
