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
package org.sakaiproject.nakamura.todo.api;

import java.util.Date;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;
import javax.jdo.annotations.Version;
import javax.jdo.annotations.VersionStrategy;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 */
@PersistenceCapable(detachable="true", table="todo_todo")
@Version(strategy=VersionStrategy.DATE_TIME, column="version")
@Queries({
  @Query(name=Todo.QUERY_NAME_CREATED_BY, value=Todo.QUERY_CREATED_BY),
  @Query(name=Todo.QUERY_NAME_WHO, value=Todo.QUERY_WHO)})
@XmlRootElement
public class Todo {

  public static final String QUERY_NAME_CREATED_BY = "CreatedBy";
  static final String QUERY_CREATED_BY = "select from org.sakaiproject.nakamura.todo.api.Todo where createdBy == :createdBy";
  
  public static final String QUERY_NAME_WHO = "Who";
  static final String QUERY_WHO = "select from org.sakaiproject.nakamura.todo.api.Todo where who == :who";
  
  @PrimaryKey
  @Persistent(valueStrategy=IdGeneratorStrategy.INCREMENT)
  @XmlElement
  private Long id;
  
  @Persistent
  @XmlElement
  private String description;
  
  @Persistent
  @XmlElement
  private Date dateCreated;
  
  @Persistent
  @XmlElement
  private Date dueDate;
  
  @Persistent
  @Index
  @XmlElement
  private String who;
  
  @Persistent
  @Index
  @XmlElement
  private String createdBy;

  /**
   * @return the id
   */
  public Long getId() {
    return id;
  }

  /**
   * @param id the id to set
   */
  public void setId(Long id) {
    this.id = id;
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * @return the dateCreated
   */
  public Date getDateCreated() {
    return dateCreated;
  }

  /**
   * @param dateCreated the dateCreated to set
   */
  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  /**
   * @return the dueDate
   */
  public Date getDueDate() {
    return dueDate;
  }

  /**
   * @param dueDate the dueDate to set
   */
  public void setDueDate(Date dueDate) {
    this.dueDate = dueDate;
  }

  /**
   * @return the who
   */
  public String getWho() {
    return who;
  }

  /**
   * @param who the who to set
   */
  public void setWho(String who) {
    this.who = who;
  }

  /**
   * @return the createdBy
   */
  public String getCreatedBy() {
    return createdBy;
  }

  /**
   * @param createdBy the createdBy to set
   */
  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }
  
  @Override
  public String toString() {
    return String.format("{ id=%s; desc=%s; created=%s; dueDate=%s; who=%s; createdBy=%s }",
        String.valueOf(getId()), getDescription(), String.valueOf(getDateCreated()), String.valueOf(getDueDate()), getWho(), getCreatedBy());
  }
}
