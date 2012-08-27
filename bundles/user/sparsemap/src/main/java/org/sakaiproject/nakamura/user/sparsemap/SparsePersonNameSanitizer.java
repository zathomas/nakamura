/**
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
package org.sakaiproject.nakamura.user.sparsemap;

public class SparsePersonNameSanitizer {


  private String name;

  public SparsePersonNameSanitizer(String name) {
    this.name = name;
  }

  public void validate()  {
    String name = this.name;

    // At least 3 chars.
    if (name.length() < 3) {
      throw new IllegalArgumentException("Name must be bigger than 3 chars.");
    }

    // KERN-763 - UserIDs starting with g-contacts- are reserved for the contact groups.
    if (name.startsWith("g-contacts-")) {
      throw new IllegalArgumentException("'g-contacts-' is a reserved prefix.");
    }
  }
}
