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

package org.sakaiproject.nakamura.api.files;

import java.io.IOException;

/**
 * A service for creating and modifying {@link File} pooled content items in persistent storage.
 */
public interface FileService {

  /**
   * Create a {@link File} with the given parameters.
   *
   * @param params Parameters for the new file.
   * @return A file that has been stored persistently.
   * @throws StorageException
   * @throws IOException
   */
  File createFile(FileParams params)
      throws StorageException, IOException;

  /**
   * Create an alternative stream for a {@link File} that already exists.
   *
   * @param params Parameters for the new alternative stream.
   * @return A file that has been stored persistently.
   * @throws StorageException
   * @throws IOException
   */
  File createAlternativeStream(FileParams params)
      throws StorageException, IOException;

  /**
   * Update a {@link File} that already exists.
   *
   * @param params Parameters for the file.
   * @return A file that has been stored persistently.
   * @throws StorageException
   * @throws IOException
   */
  File updateFile(FileParams params)
      throws StorageException, IOException;
}
