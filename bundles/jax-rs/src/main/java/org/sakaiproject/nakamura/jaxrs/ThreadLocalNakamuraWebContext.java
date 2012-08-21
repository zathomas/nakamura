package org.sakaiproject.nakamura.jaxrs;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.osgi.service.http.HttpContext;
import org.perf4j.aop.Profiled;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.jaxrs.api.NakamuraWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Service({NakamuraWebContext.class, Filter.class})
@Property(name="pattern", value=".*")
public class ThreadLocalNakamuraWebContext implements NakamuraWebContext, Filter {

  private final static Logger LOGGER = LoggerFactory.getLogger(ThreadLocalNakamuraWebContext.class);
  
  private final static ThreadLocal<BeanNakamuraWebContextImpl> webContextThreadLocal =
      new ThreadLocal<BeanNakamuraWebContextImpl>();

  @Reference
  protected Repository repository;

  @Reference
  protected AuthenticationSupport authenticationSupport;
  
  public ThreadLocalNakamuraWebContext() {
    
  }
  
  public ThreadLocalNakamuraWebContext(Repository repository, AuthenticationSupport authenticationSupport) {
    this.repository = repository;
    this.authenticationSupport = authenticationSupport;
  }
  
  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    LOGGER.info("init()");
  }

  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    initWebContext(request, response);
    try {
      chain.doFilter(request, response);
    } finally {
      destroyWebContext();
    }
  }
  
  /**
   * {@inheritDoc}
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
    LOGGER.info("destroy()");
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jaxrs.api.NakamuraWebContext#getCurrentUserId()
   */
  @Override
  public String getCurrentUserId() {
    validateInitialized();
    return webContextThreadLocal.get().getCurrentUserId();
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jaxrs.api.NakamuraWebContext#getCurrentSession()
   */
  @Override
  public Session getCurrentSession() {
    validateInitialized();
    // we lazily create the session since the session uses up sweet, sweet pooled resources.
    if (webContextThreadLocal.get().getCurrentSession() == null) {
      createSession(webContextThreadLocal.get());
    }
    return webContextThreadLocal.get().getCurrentSession();
  }

  /**
   * Initialize the web context for the given request.
   * 
   * @param request
   * @param response
   */
  void initWebContext(ServletRequest request, ServletResponse response) {
    if (webContextThreadLocal.get() != null) {
      LOGGER.warn("A web context was initialized when one alread existed. This could indicate that " +
          "a web context was left lingering on a thread, which is dangerous.");
      destroyWebContext();
    }
    BeanNakamuraWebContextImpl webContext = new BeanNakamuraWebContextImpl();
    resolveCurrentUserId(request, response, webContext);
    webContextThreadLocal.set(webContext);
  }
  
  /**
   * Destroy the thread web context. This will release all resources and completely unbind the context
   * from the thread.
   */
  void destroyWebContext() {
    BeanNakamuraWebContextImpl webContext = webContextThreadLocal.get();
    
    if (webContext == null)
      return;

    try {
      webContext.setCurrentUserId(null);
      try {
        Session session = webContext.getCurrentSession();
        webContext.setSession(null);
        if (session != null) {
          session.logout();
        }
      } catch (ClientPoolException e) {
        LOGGER.error("Error logging out of sparse session", e);
      }
    } finally {
      webContextThreadLocal.set(null);
    }
  }
  
  /**
   * Verify that the web context is initialized and in a state where it can be accessed for context data
   */
  void validateInitialized() {
    if (webContextThreadLocal.get() == null)
      throw new IllegalStateException("Attempted to access null web context.");
    
    // we can determine if this was initialized based on whether or not the user id was set. If it is an anonymous request,
    // then the user id will be User.ANON_USER, not null.
    if (webContextThreadLocal.get().getCurrentUserId() == null)
      throw new IllegalStateException("Attempted to access uninitialized web context");
  }
  
  /**
   * Create a sparse session from the current request context and pin it to the given {@code webContext}
   * @param request
   * @param webContext
   */
  @Profiled(tag="jaxrs:createSession:slow", timeThreshold=10)
  private void createSession(BeanNakamuraWebContextImpl webContext) {
    try {
      String userId = webContext.getCurrentUserId();
      if (userId != null && !User.ANON_USER.equals(userId)) {
        webContext.setSession(repository.loginAdministrative(userId));
      } else {
        webContext.setSession(repository.login());
      }
    } catch (ClientPoolException e) {
      LOGGER.error("Error establishing sparse session.", e);
    } catch (StorageClientException e) {
      LOGGER.error("Error establishing sparse session.", e);
    } catch (AccessDeniedException e) {
      LOGGER.error("Error establishing sparse session.", e);
    }
  }
  
  /**
   * Determine the current user id and set it onto the {@code webContext}.
   * 
   * @param request
   * @param response
   * @param webContext
   */
  @Profiled(tag="jaxrs:authenticateUser:slow:{$return}", timeThreshold=10, el=true)
  private String resolveCurrentUserId(ServletRequest request, ServletResponse response,
      BeanNakamuraWebContextImpl webContext) {
    String userId = null;
    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      userId = ((HttpServletRequest) request).getRemoteUser();
      if (userId == null) {
        authenticationSupport.handleSecurity((HttpServletRequest) request,
            (HttpServletResponse) response);
        userId = (String) request.getAttribute(HttpContext.REMOTE_USER);
      }
    }

    if (userId == null) {
      userId = User.ANON_USER;
    }
    
    webContext.setCurrentUserId(userId);
    return userId;
  }

}
