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
package org.sakaiproject.nakamura.resource.lite;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.resource.ResourceService;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.resource.lite.servlet.post.operations.ResourceModifyOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

@Component(immediate = true)
@Service
public class ResourceServiceImpl implements ResourceService {

  public static final Logger LOG = LoggerFactory.getLogger(ResourceServiceImpl.class);

  @Override
  public SparsePostOperation getDefaultSparsePostOperation(ServletContext servletContext) {
    LOG.info("Returning default sparse post operation on behalf of servlet context [{}]", servletContext);
    return new ResourceModifyOperation(servletContext);
  }
}
