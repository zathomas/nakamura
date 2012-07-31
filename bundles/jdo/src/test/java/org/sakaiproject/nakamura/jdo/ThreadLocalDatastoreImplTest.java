package org.sakaiproject.nakamura.jdo;

import junit.framework.Assert;

import org.junit.Test;
import org.mockito.Mockito;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

/**
 * Verify a clean datastore lifecycle.
 */
public class ThreadLocalDatastoreImplTest {

  /**
   * Verify that an access to get() results in an IllegalStateException when the datastore was
   * never initialized. 
   */
  @Test(expected=IllegalStateException.class)
  public void testGetNotInitialized() {
    (new ThreadLocalDatastoreImpl()).get();
  }
  
  /**
   * Verify that an access to get() results in an IllegalStateException when the datastore was
   * destroyed since it was initialized.
   */
  @Test(expected=IllegalStateException.class)
  public void testGetDestroyed() {
    ThreadLocalDatastoreImpl datastore = new ThreadLocalDatastoreImpl();
    
    try {
      datastore.init();
      datastore.destroy();
    } catch (IllegalStateException e) {
      throw new RuntimeException(e);
    }
    
    datastore.get();
  }
  
  /**
   * Verify that the persistence manager is created lazily. That is, it will only be created when
   * an access to get() is performed within the lifecycle.
   */
  @Test
  public void testLazyInitialization() {
    ThreadLocalDatastoreImpl datastore = new ThreadLocalDatastoreImpl();
    datastore.init();
    Assert.assertFalse("Did not expect a persistence manager to be accessed eagerly by datastore init.",
        datastore.hasPersistenceManager());
    datastore.destroy();
  }
  
  /**
   * Verify several items about the regular datastore lifecycle:
   * 
   *    * A transaction is started when the persistence manager is accessed from the datastore
   *    * The persistence manager is managed as a singleton on the thread
   *    * A new persistence manager and transaction is created when the active singleton is closed and re-accessed
   *    * When the datastore is destroyed, if the existing transaction is active, it will be <strong>rolled back</strong>
   *
   */
  @Test
  public void testNormalLifecycle() {
    PersistenceManagerFactory pmf = Mockito.mock(PersistenceManagerFactory.class);
    PersistenceManager pm = Mockito.mock(PersistenceManager.class);
    Transaction tx = Mockito.mock(Transaction.class);
    
    Mockito.when(pmf.getPersistenceManager()).thenReturn(pm);
    Mockito.when(pm.isClosed()).thenReturn(Boolean.FALSE);
    Mockito.when(pm.currentTransaction()).thenReturn(tx);
    
    ThreadLocalDatastoreImpl datastore = new ThreadLocalDatastoreImpl(pmf);
    datastore.init();
    PersistenceManager pmGet = datastore.get();
    
    // verify we got our persistence manager and started a transaction
    Assert.assertTrue("Expected to get the persistence manager", pm == pmGet);
    Mockito.verify(tx, Mockito.times(1)).begin();
    
    // now slip in a new persistence manager, as pmf is a factory
    PersistenceManager pmNew = Mockito.mock(PersistenceManager.class);
    Transaction txNew = Mockito.mock(Transaction.class);
    Mockito.when(pmf.getPersistenceManager()).thenReturn(pmNew);
    Mockito.when(pmNew.isClosed()).thenReturn(Boolean.FALSE);
    Mockito.when(pmNew.currentTransaction()).thenReturn(txNew);
    Mockito.when(txNew.isActive()).thenReturn(Boolean.TRUE);
    
    // verify that when we get again, we don't get the new pmf (pmNew). we should get the original thread-managed one still.
    pmGet = datastore.get();
    Assert.assertTrue("Expected to get the original thread-managed persistence manager", pm == pmGet);
    // ensure we didn't try to begin a new transaction or anything
    Mockito.verify(tx, Mockito.times(1)).begin();
    Mockito.verify(txNew, Mockito.never()).begin();
    
    // now close the original pm
    Mockito.when(pm.isClosed()).thenReturn(Boolean.TRUE);
    
    // verify that we get the new persistence manager now if we ask again
    pmGet = datastore.get();
    Assert.assertTrue("Expected to get the new, unclosed, persistence manager", pmNew == pmGet);
    Mockito.verify(txNew, Mockito.times(1)).begin();
    
    // verify that the datastore reports that it has a persistence manager
    Assert.assertTrue("Expected the datastore to report having a persistence manager", datastore.hasPersistenceManager());
    
    datastore.destroy();
    
    // verify that the datastore correctly cleans up resources
    Mockito.verify(txNew, Mockito.times(1)).rollback();
    Mockito.verify(pmNew, Mockito.times(1)).close();
    
  }
  
  /**
   * Verify that if there is an error while closing the datastore transaction, resources are
   * still properly cleaned up.
   */
  @Test
  public void testErrorStillClosesPm() {
    PersistenceManagerFactory pmf = Mockito.mock(PersistenceManagerFactory.class);
    PersistenceManager pm = Mockito.mock(PersistenceManager.class);
    Transaction tx = Mockito.mock(Transaction.class);
    RuntimeException runtimeException = new RuntimeException();
    
    Mockito.when(pmf.getPersistenceManager()).thenReturn(pm);
    Mockito.when(pm.isClosed()).thenReturn(Boolean.FALSE);
    Mockito.when(pm.currentTransaction()).thenReturn(tx);
    Mockito.when(tx.isActive()).thenThrow(runtimeException);
    
    ThreadLocalDatastoreImpl datastore = new ThreadLocalDatastoreImpl(pmf);
    datastore.init();
    datastore.get();
    
    boolean exceptionThrown = false;
    
    try {
      datastore.destroy();
    } catch (RuntimeException e) {
      exceptionThrown = true;
      
      // sanity check the test
      Assert.assertTrue("Expected the isActive runtime exception to be thrown.", e == runtimeException);
      
      //verify that the persistence manager was still closed
      Mockito.verify(pm, Mockito.times(1)).close();
      
      // verify that the datastore is actually destroyed
      boolean isDestroyed = false;
      try {
        datastore.get();
      } catch (IllegalStateException ise) {
        isDestroyed = true;
      }
      
      Assert.assertTrue("Expected the datastore to be fully destroyed", isDestroyed);
      
      // verify that there is no persistence manager on the thread
      Assert.assertFalse("Expected the persistence manager to be cleaned from the thread.", datastore.hasPersistenceManager());
    }
    
    Assert.assertTrue("Expected a runtime exception to be thrown.", exceptionThrown);
  }
}
