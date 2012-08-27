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
package org.sakaiproject.nakamura.jdo.api;

/**
 *
 */
public interface JDBCConnectionPoolConfiguration {

  /**
   * @return the driverClassName
   */
  public abstract String getDriverClassName();

  /**
   * @return the connectionUrl
   */
  public abstract String getConnectionUrl();

  /**
   * @return the username
   */
  public abstract String getUsername();

  /**
   * @return the password
   */
  public abstract String getPassword();

  /**
   * @return the validationQuery
   */
  public abstract String getValidationQuery();

  /**
   * @return the validationQueryTimeout
   */
  public abstract int getValidationQueryTimeout();

  /**
   * @return the testOnBorrow
   */
  public abstract boolean isTestOnBorrow();

  /**
   * @return the testOnReturn
   */
  public abstract boolean isTestOnReturn();

  /**
   * @return the testWhileIdle
   */
  public abstract boolean isTestWhileIdle();

  /**
   * @return the maxActive
   */
  public abstract int getMaxActive();

  /**
   * @return the maxIdle
   */
  public abstract int getMaxIdle();

  /**
   * @return the minIdle
   */
  public abstract int getMinIdle();

  /**
   * @return the maxWait
   */
  public abstract long getMaxWait();

  /**
   * @return the minEvictableTimeMillis
   */
  public abstract long getMinEvictableTimeMillis();

  /**
   * @return the numTestsPerEvictionRun
   */
  public abstract int getNumTestsPerEvictionRun();

}