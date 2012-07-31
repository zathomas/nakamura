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
package org.sakaiproject.nakamura.todo;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.jaxrs.api.JaxrsService;
import org.sakaiproject.nakamura.jdo.api.Datastore;
import org.sakaiproject.nakamura.todo.api.Todo;
import org.sakaiproject.nakamura.todo.api.TodoService;

import java.util.LinkedList;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@Service
@Component
@Path("/todo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TodoServiceImpl implements TodoService, JaxrsService {

  @Reference
  protected Datastore datastore;
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.todo.api.TodoService#findById(java.lang.Long)
   */
  @GET
  @Path("/{id}")
  public Todo findById(@PathParam("id") Long id) {
    PersistenceManager pm = datastore.get();
    Todo todo = pm.getObjectById(Todo.class, id);
    if (todo != null) {
      todo = pm.detachCopy(todo);
    }    
    return todo;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.todo.api.TodoService#findByCreatorId(java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @GET
  @Path("/creator/{userId}")
  public List<Todo> findByCreatorId(@PathParam("userId") String creatorUserId) {
    PersistenceManager pm = datastore.get();
    List<Todo> todos = (List<Todo>) pm.newNamedQuery(Todo.class, Todo.QUERY_NAME_CREATED_BY).execute(creatorUserId);
    if (todos != null) {
      todos = new LinkedList<Todo>(pm.detachCopyAll(todos));
    }
    return todos;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.todo.api.TodoService#findByUserId(java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @GET
  @Path("/actor/{userId}")
  public List<Todo> findByUserId(@PathParam("userId") String whoUserId) {
    PersistenceManager pm = datastore.get();
    List<Todo> todos = (List<Todo>) pm.newNamedQuery(Todo.class, Todo.QUERY_NAME_WHO).execute(whoUserId);
    if (todos != null) {
      todos = new LinkedList<Todo>(pm.detachCopyAll(todos));
    }
    return todos;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.todo.api.TodoService#createOrUpdate(org.sakaiproject.nakamura.todo.api.Todo)
   */
  @POST
  public Todo createOrUpdate(Todo todo) {
    if (todo == null)
      throw new IllegalArgumentException("Cannot create a null todo entity");
    PersistenceManager pm = datastore.get();
    pm.makePersistent(todo);
    todo = pm.detachCopy(todo);
    return todo;
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.todo.api.TodoService#deleteById(java.lang.Long)
   */
  @DELETE
  @Path("/{id}")
  public Todo deleteById(@PathParam("id") Long id) {
    if (id == null)
      throw new IllegalArgumentException("Cannot delete a todo with a null ID");
    PersistenceManager pm = datastore.get();
    Todo todo = pm.getObjectById(Todo.class, id);
    Todo detachedTodo = pm.detachCopy(todo);
    pm.deletePersistent(todo);
    return detachedTodo;
  }

  
  
}
