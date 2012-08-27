package org.sakaiproject.nakamura.jdo.http;

import junit.framework.Assert;

import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.jdo.ThreadLocalDatastoreImpl;

import java.io.IOException;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Verifies a clean persistence manager lifecycle in the servlet request filter chain.
 */
public class PersistenceManagerFilterTest {

  /**
   * Verify that there are no unnecessary accesses to datastore when passed through the filter.
   * 
   * @throws Exception
   */
  @Test
  public void testNoAccess() throws Exception {
    FilterChain chain = Mockito.mock(FilterChain.class);
    ThreadLocalDatastoreImpl datastore = Mockito.mock(ThreadLocalDatastoreImpl.class);
    Mockito.when(datastore.hasPersistenceManager()).thenReturn(Boolean.FALSE);
    
    PersistenceManagerFilter filter = new PersistenceManagerFilter(datastore);
    filter.doFilter(Mockito.mock(HttpServletRequest.class), Mockito.mock(HttpServletResponse.class), chain);
    
    // verify that the datastore was initialized
    Mockito.verify(datastore, Mockito.times(1)).init();
    
    // verify that chain execution continued
    Mockito.verify(chain, Mockito.times(1)).doFilter(Mockito.any(HttpServletRequest.class),
        Mockito.any(HttpServletResponse.class));
    
    // verify that the datastore was not accessed, as it was not used.
    Mockito.verify(datastore, Mockito.never()).get();
    
    // verify that the datastore was destroyed
    Mockito.verify(datastore, Mockito.times(1)).destroy();
  }
  
  /**
   * Verify that when a request is passed through the filter, the datastore transaction is commit at
   * the end.
   * 
   * @throws Exception
   */
  @Test
  public void testCommitTransaction() throws Exception {
    FilterChain chain = new FilterChain() {
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
        // when the filter chain is continued, send a 400 code in order to invoke an HTTP error
        ((HttpServletResponse)response).sendError(201); 
      }
    };
    
    ThreadLocalDatastoreImpl datastore = Mockito.mock(ThreadLocalDatastoreImpl.class);
    PersistenceManager pm = Mockito.mock(PersistenceManager.class);
    Transaction tx = Mockito.mock(Transaction.class);
    Mockito.when(datastore.hasPersistenceManager()).thenReturn(Boolean.TRUE);
    Mockito.when(datastore.get()).thenReturn(pm);
    Mockito.when(pm.currentTransaction()).thenReturn(tx);
    Mockito.when(tx.isActive()).thenReturn(Boolean.TRUE);
    
    PersistenceManagerFilter filter = new PersistenceManagerFilter(datastore);
    filter.doFilter(Mockito.mock(HttpServletRequest.class), Mockito.mock(HttpServletResponse.class), chain);
    
    // verify that the datastore was initialized
    Mockito.verify(datastore, Mockito.times(1)).init();
    
    // verify that the transaction was rolled back
    Mockito.verify(tx, Mockito.times(1)).commit();
    
    // verify that the datastore was torn down
    Mockito.verify(datastore, Mockito.times(1)).destroy();
  }
  
  /**
   * Verify that if a request completes with an error status code, the datastore transaction
   * is rolled back.
   * 
   * @throws Exception
   */
  @Test
  public void testRollbackTransactionOnHttpErrorCode() throws Exception {
    FilterChain chain = new FilterChain() {
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
        // when the filter chain is continued, send a 400 code in order to invoke an HTTP error
        ((HttpServletResponse)response).sendError(400); 
      }
    };
    
    ThreadLocalDatastoreImpl datastore = Mockito.mock(ThreadLocalDatastoreImpl.class);
    PersistenceManager pm = Mockito.mock(PersistenceManager.class);
    Transaction tx = Mockito.mock(Transaction.class);
    Mockito.when(datastore.hasPersistenceManager()).thenReturn(Boolean.TRUE);
    Mockito.when(datastore.get()).thenReturn(pm);
    Mockito.when(pm.currentTransaction()).thenReturn(tx);
    Mockito.when(tx.isActive()).thenReturn(Boolean.TRUE);
    
    PersistenceManagerFilter filter = new PersistenceManagerFilter(datastore);
    filter.doFilter(Mockito.mock(HttpServletRequest.class), Mockito.mock(HttpServletResponse.class), chain);
    
    // verify that the datastore was initialized
    Mockito.verify(datastore, Mockito.times(1)).init();
    
    // verify that the transaction was rolled back
    Mockito.verify(tx, Mockito.times(1)).rollback();
    
    // verify that the datastore was torn down
    Mockito.verify(datastore, Mockito.times(1)).destroy();
  }
  
  
  /**
   * Verify that when an exception is thrown from the request cycle, the datastore transaction is
   * rolled back.
   * 
   * @throws Exception
   */
  @Test
  public void testNoCommitOnException() throws Exception {
    
    final IOException ioe = new IOException("Psyche!");
    
    FilterChain chain = new FilterChain() {
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
        throw ioe;
      }
    };
    
    ThreadLocalDatastoreImpl datastore = Mockito.mock(ThreadLocalDatastoreImpl.class);
    PersistenceManager pm = Mockito.mock(PersistenceManager.class);
    Transaction tx = Mockito.mock(Transaction.class);
    Mockito.when(datastore.hasPersistenceManager()).thenReturn(Boolean.TRUE);
    Mockito.when(datastore.get()).thenReturn(pm);
    Mockito.when(pm.currentTransaction()).thenReturn(tx);
    Mockito.when(tx.isActive()).thenReturn(Boolean.TRUE);
    
    PersistenceManagerFilter filter = new PersistenceManagerFilter(datastore);
    
    boolean exceptionThrown = false;
    
    try {
      filter.doFilter(Mockito.mock(HttpServletRequest.class), Mockito.mock(HttpServletResponse.class), chain);
    } catch (IOException e) {
      exceptionThrown = true;
      
      Assert.assertTrue("Expected exception from filter chain to be thrown", e == ioe);

      // verify that the datastore was initialized
      Mockito.verify(datastore, Mockito.times(1)).init();
      
      // verify that commit was not executed on the transaction
      Mockito.verify(tx, Mockito.never()).commit();
      
      // verify that the datastore was torn down
      Mockito.verify(datastore, Mockito.times(1)).destroy();
    }
    
    Assert.assertTrue("Expected an IOException to be thrown.", exceptionThrown);
    
  }

}
