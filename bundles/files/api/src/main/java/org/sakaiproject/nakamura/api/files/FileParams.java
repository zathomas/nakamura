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

public class FileParams {

  private String creator;

  private String filename;

  private String contentType;

  private String poolID;

  private InputStream inputStream;

  private String alternativeStream;

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getPoolID() {
    return poolID;
  }

  public void setPoolID(String poolID) {
    this.poolID = poolID;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  public String getAlternativeStream() {
    return alternativeStream;
  }

  public void setAlternativeStream(String alternativeStream) {
    this.alternativeStream = alternativeStream;
  }
}
