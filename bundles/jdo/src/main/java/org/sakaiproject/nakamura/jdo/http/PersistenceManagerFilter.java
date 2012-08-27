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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.jdo.ThreadLocalDatastoreImpl;
import org.sakaiproject.nakamura.jdo.api.Datastore;

import java.io.IOException;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * A filter that instantiates and manages a JDO PersistenceManager session with the
 * database. All session state information is stored on the thread and terminated
 * afterward.
 */
@Service
@Component
@Properties(value = {
  @Property(name="pattern", value="/.*")
})
public class PersistenceManagerFilter implements Filter {

  @Reference
  protected Datastore datastore;
  
  public PersistenceManagerFilter() {
    
  }
  
  public PersistenceManagerFilter(Datastore datastore) {
    this.datastore = datastore;
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(FilterConfig arg0) throws ServletException {
    // nothing to do.
  }
  
  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#destroy()
   */
  public void destroy() {
    // nothing to do.
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    DatastoreHttpServletResponseWrapper wrappedResponse = new DatastoreHttpServletResponseWrapper(
        (HttpServletResponse) response);
    
    // initializes the management cycle for this request. enables accesses to Datastore.get()
    ((ThreadLocalDatastoreImpl) datastore).init();
    
    try {
      
      chain.doFilter(request, wrappedResponse);
      
      if (((ThreadLocalDatastoreImpl) datastore).hasPersistenceManager()) {
        PersistenceManager persistenceManager = datastore.get();
        Transaction tx = persistenceManager.currentTransaction();
        if (tx.isActive()) {
          // If the response is an error, roll back the transaction.
          // Including both 4xx and 5xx errors because it may be possible for an access error to happen
          // with some changes staged. The HTTP status codes are checked for the case where a conflicting
          // filter may catch and swallow an exception and spit out an HTTP error.
          if (wrappedResponse.getStatus() >= 400 && wrappedResponse.getStatus() < 600) {
            tx.rollback();
          } else {
            tx.commit();
          }
        }
      }
    } finally {
      ((ThreadLocalDatastoreImpl) datastore).destroy();
    }
  }
}
