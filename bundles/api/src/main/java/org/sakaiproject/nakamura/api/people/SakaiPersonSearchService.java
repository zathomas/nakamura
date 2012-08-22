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
package org.sakaiproject.nakamura.api.people;

import java.util.List;
import java.util.Set;

public interface SakaiPersonSearchService {
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
  List<SakaiPerson> searchPeople(String searchUserId, String query, Set<String> tags,
                                 boolean alsoSearchProfile,
                                 String sortOn, SortOrder sortOrder,
                                 int limit, int offset);

  enum SortOrder {
    asc, desc
  }
}
