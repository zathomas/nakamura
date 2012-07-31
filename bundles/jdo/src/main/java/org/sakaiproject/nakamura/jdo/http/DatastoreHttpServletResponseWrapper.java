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
package org.sakaiproject.nakamura.jdo.http;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 *
 */
public class DatastoreHttpServletResponseWrapper extends HttpServletResponseWrapper {

  private int sc;
  
  /**
   * @param response
   */
  public DatastoreHttpServletResponseWrapper(HttpServletResponse response) {
    super(response);
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.http.HttpServletResponseWrapper#sendError(int, java.lang.String)
   */
  @Override
  public void sendError(int sc, String msg) throws IOException {
    this.sc = sc;
    super.sendError(sc, msg);
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.http.HttpServletResponseWrapper#sendError(int)
   */
  @Override
  public void sendError(int sc) throws IOException {
    this.sc = sc;
    super.sendError(sc);
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.http.HttpServletResponseWrapper#setStatus(int, java.lang.String)
   */
  @Override
  public void setStatus(int sc, String sm) {
    this.sc = sc;
    super.setStatus(sc, sm);
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.http.HttpServletResponseWrapper#setStatus(int)
   */
  @Override
  public void setStatus(int sc) {
    this.sc = sc;
    super.setStatus(sc);
  }
  
  /**
   * @return The status code of the response.
   */
  public int getStatus() {
    return sc;
  }

}
