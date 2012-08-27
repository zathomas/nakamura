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
package org.sakaiproject.nakamura.jdo;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.jdo.api.Datastore;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

/**
 * Manages a PersistenceManager instance on the thread for the processing context.
 */
@Service
@Component
public class ThreadLocalDatastoreImpl implements Datastore {

  /**
   * Thread-local marker to indicate whether or not the thread is in an acceptable datastore
   * management cycle. When false (or null), no accesses to {@link #get()} will be permitted.
   */
  private static final ThreadLocal<Boolean> THREAD_LOCAL_MANAGED = new ThreadLocal<Boolean>();
  
  /**
   * Holds the current active persistence manager for the processing context.
   */
  private static final ThreadLocal<PersistenceManager> THREAD_LOCAL_PM = new ThreadLocal<PersistenceManager>();
  
  @Reference
  protected PersistenceManagerFactory persistenceManagerFactory;
  
  public ThreadLocalDatastoreImpl() {
    
  }
  
  public ThreadLocalDatastoreImpl(PersistenceManagerFactory persistenceManagerFactory) {
    this.persistenceManagerFactory = persistenceManagerFactory;
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.Datastore#get()
   */
  public PersistenceManager get() {
    assertManaged();
    PersistenceManager persistenceManager = THREAD_LOCAL_PM.get();
    if (persistenceManager == null || persistenceManager.isClosed()) {
      persistenceManager = persistenceManagerFactory.getPersistenceManager();
      persistenceManager.currentTransaction().begin();
      THREAD_LOCAL_PM.set(persistenceManager);
    }
    
    return persistenceManager;
  }

  /**
   * Initialize the Datastore management for this thread, allowing calls to {@link #get()}. After
   * {@link #init()} has been called, {@link #destroy()} <strong>MUST</strong> be called at some
   * point after processing to ensure all resources are properly cleaned up, and transactions are
   * appropriately terminated.
   */
  public void init() {
    // mark the beginning of datastore management. persistence management may now be accessed.
    THREAD_LOCAL_MANAGED.set(Boolean.TRUE);
  }
  
  /**
   * Destroy the PersistenceManager on this thread, if necessary. If there is an active transaction,
   * it will be rolled back. After {@link #destroy()} is called, accesses to {@link #get()} will no
   * longer be permitted until {@link #init()} has been called again. This is to avoid resource leaks
   * due to calls to {@link #get()} without {@link #destroy()} being called sometime after.
   */
  public void destroy() {
    PersistenceManager persistenceManager = THREAD_LOCAL_PM.get();
    
    // mark the end of datastore management. no more access to persistence management is allowed
    // until management is re-initialized through init()
    THREAD_LOCAL_MANAGED.remove();
    
    if (persistenceManager != null) {
      THREAD_LOCAL_PM.remove();
      try {
        Transaction tx = persistenceManager.currentTransaction();
        if (tx.isActive()) {
          tx.rollback();
        }
      } finally {
        persistenceManager.close();
      }
    }
  }
  
  /**
   * @return Whether or not there is a persistence manager open and associated to this thread.
   */
  public boolean hasPersistenceManager() {
    return THREAD_LOCAL_PM.get() != null;
  }
  
  /**
   * Asserts that the thread is in a managed state (i.e., init() has been called since the last
   * destroy()). This check is in place to ensure that the JDO PersistenceManager is not opened
   * on the thread VIA get() with the assumption that it will be automatically closed.
   * <p>
   * Any component that takes responsibility of datastore management should know to always call
   * destroy() to safely terminate the management cycle.
   */
  private void assertManaged() {
    if (THREAD_LOCAL_MANAGED.get() == null || !THREAD_LOCAL_MANAGED.get()) {
      throw new IllegalStateException("Attempted to access managed Datastore out of managed scope.");
    }
  }
}
