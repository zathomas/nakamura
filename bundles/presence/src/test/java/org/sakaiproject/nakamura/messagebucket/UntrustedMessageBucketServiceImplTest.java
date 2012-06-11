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
package org.sakaiproject.nakamura.messagebucket;

import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucket;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucketException;

@RunWith(MockitoJUnitRunner.class)
public class UntrustedMessageBucketServiceImplTest {
  UntrustedMessageBucketServiceImpl untrustedMessageBucketServiceImpl;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    untrustedMessageBucketServiceImpl = new UntrustedMessageBucketServiceImpl();
  }

  /**
   * KERN-2904 Can trigger java.lang.ArrayIndexOutOfBoundsException anonymously for
   * /system/uievent/default
   * 
   * @throws MessageBucketException
   */
  @Test(expected = MessageBucketException.class)
  public void testGetBucketBadToken() throws MessageBucketException {
    untrustedMessageBucketServiceImpl.getBucket("badToken");
  }

  /**
   * KERN-2900 Able to create NullPointerException Anonymously by visiting
   * /system/uievent/default
   * 
   * @throws Exception
   */
  @Test
  public void testGetBucketNull() throws Exception {
    final MessageBucket mb = untrustedMessageBucketServiceImpl.getBucket(null);
    assertNull(mb);
  }

}
