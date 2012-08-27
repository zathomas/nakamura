/**
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
package org.sakaiproject.nakamura.files.jaxrs;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.files.File;
import org.sakaiproject.nakamura.api.files.FileParams;
import org.sakaiproject.nakamura.api.files.FileService;
import org.sakaiproject.nakamura.jaxrs.api.NakamuraWebContext;
import org.sakaiproject.nakamura.jaxrs.api.JaxrsService;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Service
@Component
@Path("/content")
@Produces(MediaType.APPLICATION_JSON)
public class FileServiceRest implements JaxrsService {

  @Reference
  FileService fileService;

  @Reference
  NakamuraWebContext nakamuraContext;

  @GET @Path("/id/{id}")
  public File getFile(@PathParam("id") String id) {
    String currentUserId = nakamuraContext.getCurrentUserId();
    return new File(currentUserId, null, null, null, null);
  }

  @POST @Path("/id/{id}")
  public void updateFile(FileParams fileParams) {
    try {
      fileService.updateFile(fileParams);
    } catch (Exception e) {
      throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @PUT @Path("/")
  public File createFile(FileParams fileParams) {
    try {
      return fileService.createFile(fileParams);
    } catch (Exception e) {
      throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @DELETE @Path("/id/{id}")
  public void deleteFile() {
    // TODO do something here
  }

}
