package org.sakaiproject.nakamura.api.user;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

  public enum SortOrder {
    ASCENDING, DESCENDING
  }

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
                  Map<String, Object> properties);

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
   * Add the set of specified {@code String} tags to the specified {@link SakaiPerson}
   * If any of the specified tags is on that person already, it will be ignored.
   * @param personId
   * @param tags
   */
  void tagPerson(String personId, Set<String> tags);

  /**
   * Remove the set of specified {@code String} tags from the specified {@link SakaiPerson}
   * If any of specified tags is not on that person, it will be ignored.
   * @param personId
   * @param tags
   */
  void untagPerson(String personId, Set<String> tags);


  /**
   * Change the password for the specified {@link SakaiPerson}
   * @param userId
   * @param oldPwd
   * @param newPwd
   * @param newPwdConfirm
   */
  void changePersonAccountPassword(String userId, String oldPwd, String newPwd, String newPwdConfirm);

  /**
   * Perform a full-text search for {@link SakaiPerson}s matching the given criteria
   * @param query
   * @param tags
   * @param alsoSearchProfile a flag for searching in the person profile information
   * @param sortOn the name of a {@link SakaiPerson} property to sort by
   * @param sortOrder
   * @param limit the maximum number of results to return
   * @param offset the number of results to skip over before returning
   * @return
   */
  List<SakaiPerson> searchPeople(String query, Set<String> tags,
                                 boolean alsoSearchProfile,
                                 String sortOn, SortOrder sortOrder,
                                 int limit, int offset);


}
