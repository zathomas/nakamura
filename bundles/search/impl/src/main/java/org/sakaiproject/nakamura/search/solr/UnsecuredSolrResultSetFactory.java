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

package org.sakaiproject.nakamura.search.solr;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.search.solr.ResultSetFactory;

import java.util.Map;

@Component(metatype = true)
@Service(value = ResultSetFactory.class)
@Property(name = "type", value = "solr-unsecured")
/**
 * Use this result set factory if you want your search results without the "readers" clause.
 * Beware, this may expose users to content on which they do not have permission.
 */
public class UnsecuredSolrResultSetFactory extends SolrResultSetFactory {

  @Override
  protected void applyReadersRestrictions(String searchUserId, boolean asAnon,
                                          Map<String, Object> queryOptions) throws StorageClientException, AccessDeniedException {
    // no-op (there are no reader restrictions in this result set factory)
  }
}
