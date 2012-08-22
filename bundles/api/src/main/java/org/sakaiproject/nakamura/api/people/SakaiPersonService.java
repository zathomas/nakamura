package org.sakaiproject.nakamura.api.people;

import java.util.Map;

/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
public interface SakaiPersonService {

  /**
   *
   * @param personId
   * @param firstName
   * @param lastName
   * @param email
   * @param password
   * @param passwordConfirm
   * @param properties
   * @return a newly created {@link SakaiPerson}
   */
  SakaiPerson createPerson(String personId, String firstName, String lastName, String email,
                           String password, String passwordConfirm,
                           Map<String, Object[]> properties);

  /**
   * Update th
   * @param personId
   * @param firstName
   * @param lastName
   * @param email
   * @param properties
   */
  void updatePerson(String personId, String firstName, String lastName, String email,
                  Map<String, Object[]> properties);

  /**
   * Returns the {@link SakaiPerson} specified by {@code personId}
   * @param personId The unique system id for this person
   */
  SakaiPerson getPerson(String personId);

  /**
   * Removes the specified person from the system.
   * Also:
   *   removes their messages.
   *   removes the person from anyone's contacts.
   *   removes the person from any world or content membership.
   *
   * @param personId The unique system id for the {@link SakaiPerson} to delete
   */
  void deletePerson(String personId);

  /**
   *
   * @param personId
   * @return whether or not the specified ID is already in use by a @{link SakaiPerson} in the system.
   */
  boolean isPersonIdInUse(String personId);

  /**
   * Change the password for the specified {@link SakaiPerson}
   * @param userId
   * @param oldPwd
   * @param newPwd
   * @param newPwdConfirm
   */
  void changePersonAccountPassword(String userId, String oldPwd, String newPwd, String newPwdConfirm);


}
