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

import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration;

import java.util.Map;

/**
 * A configurable instance of a reusable JDBC connection pooling configuration.
 */
@Service
@Component(metatype=true)
public class JDBCConnectionPoolConfigurationImpl implements JDBCConnectionPoolConfiguration {

  public final static String DEFAULT_CONNECTION_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
  public final static String DEFAULT_CONNECTION_URL = "jdbc:derby:directory:sling/db;create=true";
  public final static String DEFAULT_USERNAME = "sa";
  public final static String DEFAULT_PASSWORD = "";
  public final static int DEFAULT_VALIDATION_QUERY_TIMEOUT = -1;
  public final static boolean DEFAULT_TEST_ON_BORROW = GenericObjectPool.DEFAULT_TEST_ON_BORROW;
  public final static boolean DEFAULT_TEST_ON_RETURN = GenericObjectPool.DEFAULT_TEST_ON_RETURN;
  public final static boolean DEFAULT_TEST_WHILE_IDLE = GenericObjectPool.DEFAULT_TEST_WHILE_IDLE;
  public final static int DEFAULT_MAX_ACTIVE = GenericObjectPool.DEFAULT_MAX_ACTIVE;
  public final static int DEFAULT_MAX_IDLE = GenericObjectPool.DEFAULT_MAX_IDLE;
  public final static int DEFAULT_MIN_IDLE = GenericObjectPool.DEFAULT_MIN_IDLE;
  public final static long DEFAULT_MAX_WAIT = GenericObjectPool.DEFAULT_MAX_WAIT;
  public final static long DEFAULT_MIN_EVICTABLE_TIME_MILLIS = GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
  public final static int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = GenericObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
  
  @Property(label="JDBC Driver Class", description="e.g., org.postgresql.Driver", value=DEFAULT_CONNECTION_DRIVER_NAME)
  private final static String PROP_CONNECTION_DRIVER_NAME = "org.sakaiproject.nakamura.jdo.PROP_CONNECTION_DRIVER_NAME";

  @Property(label="Connection URL", description="e.g., jdbc:postgresql://localhost/nakamura", value=DEFAULT_CONNECTION_URL)
  private final static String PROP_CONNECTION_URL = "org.sakaiproject.nakamura.jdo.PROP_CONNECTION_URL";

  @Property(label="DB Username", value=DEFAULT_USERNAME)
  private final static String PROP_USERNAME = "org.sakaiproject.nakamura.jdo.PROP_USERNAME";

  @Property(label="DB Password", value=DEFAULT_PASSWORD)
  private final static String PROP_PASSWORD = "org.sakaiproject.nakamura.jdo.PROP_PASSWORD";
  
  @Property(label="Validation Query")
  private final static String PROP_VALIDATION_QUERY = "org.sakaiproject.nakamura.jdo.PROP_VALIDATION_QUERY";

  @Property(label="Validation Query Timeout", intValue=DEFAULT_VALIDATION_QUERY_TIMEOUT)
  private final static String PROP_VALIDATION_QUERY_TIMEOUT = "org.sakaiproject.nakamura.jdo.PROP_VALIDATION_QUERY_TIMEOUT";
  
  @Property(label="Test on borrow", boolValue=DEFAULT_TEST_ON_BORROW)
  private final static String PROP_TEST_ON_BORROW = "org.sakaiproject.nakamura.jdo.PROP_TEST_ON_BORROW";
  
  @Property(label="Test on return", boolValue=DEFAULT_TEST_ON_RETURN)
  private final static String PROP_TEST_ON_RETURN = "org.sakaiproject.nakamura.jdo.PROP_TEST_ON_RETURN";
  
  @Property(label="Test while idle", boolValue=DEFAULT_TEST_WHILE_IDLE)
  private final static String PROP_TEST_WHILE_IDLE = "org.sakaiproject.nakamura.jdo.PROP_TEST_WHILE_IDLE";
  
  @Property(label="Max Active Conns.", intValue=DEFAULT_MAX_ACTIVE)
  private final static String PROP_MAX_ACTIVE = "org.sakaiproject.nakamura.jdo.PROP_MAX_ACTIVE";
  
  @Property(label="Max Idle Conns.", intValue=DEFAULT_MAX_IDLE)
  private final static String PROP_MAX_IDLE = "org.sakaiproject.nakamura.jdo.PROP_MAX_IDLE";
  
  @Property(label="Min Idle Conns.", intValue=DEFAULT_MIN_IDLE)
  private final static String PROP_MIN_IDLE = "org.sakaiproject.nakamura.jdo.PROP_MIN_IDLE";
  
  @Property(label="Max Wait", longValue=DEFAULT_MAX_WAIT)
  private final static String PROP_MAX_WAIT = "org.sakaiproject.nakamura.jdo.PROP_MAX_WAIT";
  
  @Property(label="Min Evictable Time in Millis", longValue=DEFAULT_MIN_EVICTABLE_TIME_MILLIS)
  private final static String PROP_MIN_EVICTABLE_TIME_MILLIS = "org.sakaiproject.nakamura.jdo.PROP_MIN_EVICTABLE_TIME_MILLIS";
  
  @Property(label="Num Tests per Eviction Run", intValue=DEFAULT_NUM_TESTS_PER_EVICTION_RUN)
  private final static String PROP_NUM_TESTS_PER_EVICTION_RUN = "org.sakaiproject.nakamura.jdo.PROP_NUM_TESTS_PER_EVICTION_RUN";
  
  private String driverClassName = DEFAULT_CONNECTION_DRIVER_NAME;
  private String connectionUrl = DEFAULT_CONNECTION_URL;
  private String username = DEFAULT_USERNAME;
  private String password = DEFAULT_PASSWORD;
  private String validationQuery = null;
  private int validationQueryTimeout = DEFAULT_VALIDATION_QUERY_TIMEOUT;
  private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;
  private boolean testOnReturn = DEFAULT_TEST_ON_RETURN;
  private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;
  private int maxActive = DEFAULT_MAX_ACTIVE;
  private int maxIdle = DEFAULT_MAX_IDLE;
  private int minIdle = DEFAULT_MIN_IDLE;
  private long maxWait = DEFAULT_MAX_WAIT;
  private long minEvictableTimeMillis = DEFAULT_MIN_EVICTABLE_TIME_MILLIS;
  private int numTestsPerEvictionRun = DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
  
  public void activate(Map<String, Object> cfg) {
    driverClassName = PropertiesUtil.toString(cfg.get(PROP_CONNECTION_DRIVER_NAME), DEFAULT_CONNECTION_DRIVER_NAME);
    connectionUrl = PropertiesUtil.toString(cfg.get(PROP_CONNECTION_URL), DEFAULT_CONNECTION_URL);
    username = PropertiesUtil.toString(cfg.get(PROP_USERNAME), DEFAULT_USERNAME);
    password = PropertiesUtil.toString(cfg.get(PROP_PASSWORD), DEFAULT_PASSWORD);
    validationQuery = PropertiesUtil.toString(cfg.get(PROP_VALIDATION_QUERY), null);
    validationQueryTimeout = PropertiesUtil.toInteger(cfg.get(PROP_VALIDATION_QUERY_TIMEOUT), DEFAULT_VALIDATION_QUERY_TIMEOUT);
    testOnBorrow = PropertiesUtil.toBoolean(cfg.get(PROP_TEST_ON_BORROW), DEFAULT_TEST_ON_BORROW);
    testOnReturn = PropertiesUtil.toBoolean(cfg.get(PROP_TEST_ON_RETURN), DEFAULT_TEST_ON_RETURN);
    testWhileIdle = PropertiesUtil.toBoolean(cfg.get(PROP_TEST_WHILE_IDLE), DEFAULT_TEST_WHILE_IDLE);
    maxActive = PropertiesUtil.toInteger(cfg.get(PROP_MAX_ACTIVE), DEFAULT_MAX_ACTIVE);
    maxIdle = PropertiesUtil.toInteger(cfg.get(PROP_MAX_IDLE), DEFAULT_MAX_IDLE);
    minIdle = PropertiesUtil.toInteger(cfg.get(PROP_MIN_IDLE), DEFAULT_MIN_IDLE);
    maxWait = PropertiesUtil.toLong(cfg.get(PROP_MAX_WAIT), DEFAULT_MAX_WAIT);
    minEvictableTimeMillis = PropertiesUtil.toLong(cfg.get(PROP_MIN_EVICTABLE_TIME_MILLIS), DEFAULT_MIN_EVICTABLE_TIME_MILLIS);
    numTestsPerEvictionRun = PropertiesUtil.toInteger(cfg.get(PROP_NUM_TESTS_PER_EVICTION_RUN), DEFAULT_NUM_TESTS_PER_EVICTION_RUN);
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getDriverClassName()
   */
  public String getDriverClassName() {
    return driverClassName;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getConnectionUrl()
   */
  public String getConnectionUrl() {
    return connectionUrl;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getUsername()
   */
  public String getUsername() {
    return username;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getPassword()
   */
  public String getPassword() {
    return password;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getValidationQuery()
   */
  public String getValidationQuery() {
    return validationQuery;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getValidationQueryTimeout()
   */
  public int getValidationQueryTimeout() {
    return validationQueryTimeout;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#isTestOnBorrow()
   */
  public boolean isTestOnBorrow() {
    return testOnBorrow;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#isTestOnReturn()
   */
  public boolean isTestOnReturn() {
    return testOnReturn;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#isTestWhileIdle()
   */
  public boolean isTestWhileIdle() {
    return testWhileIdle;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getMaxActive()
   */
  public int getMaxActive() {
    return maxActive;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getMaxIdle()
   */
  public int getMaxIdle() {
    return maxIdle;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getMinIdle()
   */
  public int getMinIdle() {
    return minIdle;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getMaxWait()
   */
  public long getMaxWait() {
    return maxWait;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getMinEvictableTimeMillis()
   */
  public long getMinEvictableTimeMillis() {
    return minEvictableTimeMillis;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.jdo.api.JDBCConnectionPoolConfiguration#getNumTestsPerEvictionRun()
   */
  public int getNumTestsPerEvictionRun() {
    return numTestsPerEvictionRun;
  }
}
