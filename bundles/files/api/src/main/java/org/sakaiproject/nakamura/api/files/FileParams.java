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

import java.io.InputStream;
import java.util.Map;

/**
 * A data container for data passed to the various methods of {@link FileService}. Once the
 * {@link FileService} stores the pooled content item in persistent storage, it becomes a {@link File}.
 */
public class FileParams {

  private String creator;

  private String filename;

  private String contentType;

  private String poolID;

  private InputStream inputStream;

  private String alternativeStream;

  private Map<String, Object> properties;

  /**
   * @return The user ID of the user that created the file.
   */
  public String getCreator() {
    return creator;
  }

  /**
   * @param creator The user ID of the user that created the file.
   */
  public void setCreator(String creator) {
    this.creator = creator;
  }

  /**
   * @return The logical file name of the file.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * @param filename The logical file name of the file.
   */
  public void setFilename(String filename) {
    this.filename = filename;
  }

  /**
   * @return The content type (mimeType) of the file.
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * @param contentType The content type (mimeType) of the file.
   */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  /**
   * @return The internal storage ID of the file. Null when creating a new file.
   */
  public String getPoolID() {
    return poolID;
  }

  /**
   * @param poolID The internal storage ID of the file. Null when creating a new file.
   */
  public void setPoolID(String poolID) {
    this.poolID = poolID;
  }

  /**
   * @return The inputstream from which to create the file's body. May be null if the file has no body.
   */
  public InputStream getInputStream() {
    return inputStream;
  }

  /**
   * @param inputStream The inputstream from which to create the file's body. May be null if the file has no body.
   */
  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  /**
   * @return The name of an alternative stream to create for an already-existing file.
   */
  public String getAlternativeStream() {
    return alternativeStream;
  }

  /**
   * @param alternativeStream The name of an alternative stream to create for an already-existing
   *                          file. This takes the format "page-previewsize".
   */
  public void setAlternativeStream(String alternativeStream) {
    this.alternativeStream = alternativeStream;
  }

  /**
   * @return A map of properties that will be set on the file.
   */
  public Map<String, Object> getProperties() {
    return properties;
  }

  /**
   * @param properties A map of properties that will be set on the file.
   */
  public void setProperties(Map<String, Object> properties) {
    this.properties = properties;
  }

  /**
   * @return True if this file has a streamed body.
   */
  public boolean hasStream() {
    return getInputStream() != null;
  }
}
