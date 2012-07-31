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

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.osgi.framework.BundleContext;
import org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.PersistenceManagerFactory;
import javax.sql.DataSource;

/**
 * {@code PersistenceManagerFactoryConfiguration} is a configurable instance of a PersistenceManagerFactory.
 */
@Service(value=PersistenceManagerFactory.class)
@Component(metatype=true, immediate=true)
public class PersistenceManagerFactoryConfiguration extends DelegatingPersistenceManagerFactory {
  private static final long serialVersionUID = 1L;
  
  private static Logger LOGGER = LoggerFactory.getLogger(PersistenceManagerFactoryConfiguration.class);

  @Reference
  protected JDBCConnectionPoolConfiguration jdbcConfig;
  
  /**
   * The {@code DynamicClassLoaderManager} can provide a ClassLoader that is able to access
   * publicly-available components in the container without having to form a hard-wire bundle
   * package dependency to the providing bundle
   */
  @Reference
  protected DynamicClassLoaderManager dynamicClassLoaderManager;

  public PersistenceManagerFactoryConfiguration() {
    
  }

  public PersistenceManagerFactoryConfiguration(DynamicClassLoaderManager dynamicClassLoaderManager) {
    this.dynamicClassLoaderManager = dynamicClassLoaderManager;
  }
  
  @Activate
  void activate(Map<String, Object> properties) {
    super.pmf = JDOPersistenceManagerFactory.getPersistenceManagerFactory(getJdoProps(properties));
  }
  
  /**
   * Get all the JDO properties to be passed to the persistence manager factory, given the bundle configuration.
   * 
   * @param config The bundle configuration
   * @return
   */
  private Map<String, Object> getJdoProps(Map<String, Object> config) {
    
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("datanucleus.ConnectionFactory", createDataSource());
    props.put("datanucleus.storeManagerType", "rdbms");
    props.put("datanucleus.autoCreateSchema", "true");
    props.put("datanucleus.validateSchema", "true");
    props.put("datanucleus.rdbms.CheckExistTablesOrViews", "true");
    props.put("datanucleus.identifier.case", "LowerCase");
    props.put("datanucleus.Optimistic", "false");
    props.put("datanucleus.plugin.pluginRegistryClassName", "org.datanucleus.plugin.OSGiPluginRegistry");
    
    /*
     * We pass an "all-seeing" dynamic class loader to datanucleus for the following reasons:
     * 
     * First, it provides the ability for DataNucleus to resolve and build model objects from any publically-accessible
     * model object in the container.
     * 
     * Second, by using this dynamic class loader, this bundle makes no hard-wired dependency to the other bundles. If
     * hard-wired package imports were formed, then the result is that this bundle becomes forced to reload when other
     * model-providing bundles are reloaded, and that triggers a cascade that refreshes basically all application bundles
     * in the container. Bad.
     */
    props.put("datanucleus.primaryClassLoader", dynamicClassLoaderManager.getDynamicClassLoader());
    
    props.put("datanucleus.metadata.allowXML", "false");
    return props;
  }
  
  @Deactivate
  void deactivate(BundleContext context) {
    if (super.pmf != null) {
      super.pmf.close();
      super.pmf = null;
    }
  }
  
  private DataSource createDataSource() {
    LOGGER.info("Creating JDO DataSource with info:");
    LOGGER.info("\tDriver: {}", jdbcConfig.getDriverClassName());
    LOGGER.info("\tConnection String: {}", jdbcConfig.getConnectionUrl());
    LOGGER.info("\tUsername: {}", jdbcConfig.getUsername());
    
    BasicDataSource ds = new BasicDataSource();
    ds.setDriverClassName(jdbcConfig.getDriverClassName());
    ds.setUrl(jdbcConfig.getConnectionUrl());
    ds.setUsername(jdbcConfig.getUsername());
    ds.setPassword(jdbcConfig.getPassword());
    ds.setValidationQuery(jdbcConfig.getValidationQuery());
    ds.setValidationQueryTimeout(jdbcConfig.getValidationQueryTimeout());
    ds.setTestOnBorrow(jdbcConfig.isTestOnBorrow());
    ds.setTestOnReturn(jdbcConfig.isTestOnReturn());
    ds.setTestWhileIdle(jdbcConfig.isTestWhileIdle());
    ds.setMaxActive(jdbcConfig.getMaxActive());
    ds.setMaxIdle(jdbcConfig.getMaxIdle());
    ds.setMinIdle(jdbcConfig.getMinIdle());
    ds.setMaxWait(jdbcConfig.getMaxWait());
    ds.setMinEvictableIdleTimeMillis(jdbcConfig.getMinEvictableTimeMillis());
    ds.setNumTestsPerEvictionRun(jdbcConfig.getNumTestsPerEvictionRun());
    
    return ds;
        
  }
}
