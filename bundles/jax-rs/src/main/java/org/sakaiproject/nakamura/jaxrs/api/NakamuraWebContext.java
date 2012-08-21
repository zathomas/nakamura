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
package org.sakaiproject.nakamura.jaxrs.api;

import org.sakaiproject.nakamura.api.lite.Session;

/**
 * A bean that represents the web execution context of a request to Nakamura.
 */
public interface NakamuraWebContext {

  /**
   * @return The user id of the current authenticated user. If no user is authenticated, the result will be
   * {@link org.sakaiproject.nakamura.api.lite.authorizable.User#ANON_USER}. This should never return null.
   * 
   * @throws IllegalStateException If this method is accessed without the web context being initialized.
   */
  String getCurrentUserId() throws IllegalStateException;
  
  /**
   * @return The sparse session that belongs to the current user, as determined by {@link #getCurrentUserId()}. If the
   * current user is anonymous, this will be anonymous session. This should never return null.
   * 
   * @throws IllegalStateException If this method is accessed without the web context being initialized.
   */
  Session getCurrentSession() throws IllegalStateException;
  
}
