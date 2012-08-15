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
package org.sakaiproject.nakamura.basiclti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.basiclti.WimbaVirtualToolDataProvider.WIMBA_LTI_KEY;
import static org.sakaiproject.nakamura.basiclti.WimbaVirtualToolDataProvider.WIMBA_LTI_SECRET;
import static org.sakaiproject.nakamura.basiclti.WimbaVirtualToolDataProvider.WIMBA_LTI_URL;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class WimbaVirtualToolDataProviderTest {

  WimbaVirtualToolDataProvider wimbaVirtualToolDataProvider;
  WimbaVirtualToolDataProvider otherWimbaVirtualToolDataProvider;
  @Mock
  ComponentContext componentContext;
  @SuppressWarnings("rawtypes")
  @Mock
  Dictionary properties;

  protected String cleUrl;
  protected String ltiKey;
  protected String ltiSecret;
  protected Long frameHeight;
  protected boolean frameHeightLock;
  protected boolean urlLock;
  protected boolean keyLock;
  protected boolean secretLock;
  protected boolean releaseNames;
  protected boolean releaseNamesLock;
  protected boolean releaseEmail;
  protected boolean releaseEmailLock;
  protected boolean releasePrincipal;
  protected boolean releasePrincipalLock;
  protected boolean debug;
  protected boolean debugLock;
  protected List<String> toolList = Arrays
      .asList(new String[] { WimbaVirtualToolDataProvider.VTOOLID });

  @Before
  public void setUp() {
    wimbaVirtualToolDataProvider = new WimbaVirtualToolDataProvider();
    otherWimbaVirtualToolDataProvider = new WimbaVirtualToolDataProvider();
    cleUrl = wimbaVirtualToolDataProvider.ltiUrl;
    ltiKey = wimbaVirtualToolDataProvider.ltiKey;
    ltiSecret = wimbaVirtualToolDataProvider.ltiSecret;

    applyWhenConditions();
    wimbaVirtualToolDataProvider.activate(componentContext);
  }

  /**
   * Simple case where all configuration is same as defaults.
   */
  @Test
  public void testActivateUsingDefaultValues() {
    wimbaVirtualToolDataProvider.activate(componentContext);
    verifyConfiguration();
  }

  /**
   * Test all non-default values to make sure they stick.
   */
  @Test
  public void testActivateUsingNonDefaultValues() {
    setupNonDefaultValues();

    wimbaVirtualToolDataProvider.activate(componentContext);

    verifyConfiguration();
  }

  /**
   * Normal use case; return default tool list.
   */
  @Test
  public void testGetSupportedVirtualToolIds() {
    final List<String> supportedToolIds = wimbaVirtualToolDataProvider
        .getSupportedVirtualToolIds();
    assertNotNull(supportedToolIds);
    assertEquals(toolList, supportedToolIds);
  }

  /**
   * Defensive edge case where tool list might be null. Empty list should be returned
   * according to contract.
   */
  @Test
  public void testGetSupportedVirtualToolIdsNullToolList() {
    wimbaVirtualToolDataProvider.supportedVtoolIds = null;
    final List<String> supportedToolIds = wimbaVirtualToolDataProvider
        .getSupportedVirtualToolIds();
    assertNotNull(supportedToolIds);
    assertTrue(supportedToolIds.isEmpty());
  }

  /**
   * Test normal use case where we have a matching tool id.
   */
  @Test
  public void testGetKeySecret() {
    final Map<String, Object> keySecret = wimbaVirtualToolDataProvider
        .getKeySecret(toolList.get(0));
    assertNotNull(keySecret);
    assertEquals(ltiKey, keySecret.get(BasicLTIAppConstants.LTI_KEY));
    assertEquals(ltiSecret, keySecret.get(BasicLTIAppConstants.LTI_SECRET));
  }

  /**
   * Negative test case passing null argument to method.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetKeySecretNullToolId() {
    wimbaVirtualToolDataProvider.getKeySecret(null);
  }

  /**
   * Negative test case passing empty string to method.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetKeySecretEmptyToolId() {
    wimbaVirtualToolDataProvider.getKeySecret("");
  }

  /**
   * Test uncommon case where caller passes a toolId that cannot be matched.
   */
  @Test
  public void testGetKeySecretNoToolIdFound() {
    final Map<String, Object> keySecret = wimbaVirtualToolDataProvider
        .getKeySecret("/foo/bar/baz");
    assertNull(keySecret);
  }

  /**
   * Test normal use case and expected launch values.
   */
  @Test
  public void testGetLaunchValuesDefaultValues() {
    final Map<String, Object> launchValues = wimbaVirtualToolDataProvider
        .getLaunchValues(toolList.get(0));
    verifyLaunchValues(launchValues);
  }

  /**
   * Test launch values when using non-default configuration.
   */
  @Test
  public void testGetLaunchValuesNonDefaultValues() {
    setupNonDefaultValues();
    wimbaVirtualToolDataProvider.activate(componentContext);

    final Map<String, Object> launchValues = wimbaVirtualToolDataProvider
        .getLaunchValues(toolList.get(0));

    verifyLaunchValues(launchValues);
  }

  /**
   * Test edge case where null argument is passed.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetLaunchValuesNullArgument() {
    wimbaVirtualToolDataProvider.getLaunchValues(null);
  }

  /**
   * Test edge case where empty string is passed.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetLaunchValuesEmptyString() {
    wimbaVirtualToolDataProvider.getLaunchValues("");
  }

  /**
   * Test uncommmon case where caller asks for a toolId we cannot match.
   */
  @Test
  public void testGetLaunchValuesNoToolIdFound() {
    final Map<String, Object> launchValues = wimbaVirtualToolDataProvider
        .getLaunchValues("/foo/bar/bazooey");
    assertNull(launchValues);
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#hashCode()}
   */
  @Test
  public void testHashCode() {
    int hc = wimbaVirtualToolDataProvider.hashCode();
    assertTrue(hc != 0);

    otherWimbaVirtualToolDataProvider.ltiUrl = null;
    otherWimbaVirtualToolDataProvider.ltiKey = null;
    otherWimbaVirtualToolDataProvider.ltiSecret = null;
    otherWimbaVirtualToolDataProvider.supportedVtoolIds = null;
    assertTrue(wimbaVirtualToolDataProvider.hashCode() != otherWimbaVirtualToolDataProvider
        .hashCode());
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsSameObject() {
    assertTrue(wimbaVirtualToolDataProvider.equals(wimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullObject() {
    assertTrue(!wimbaVirtualToolDataProvider.equals(null));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsEqualObject() {
    assertTrue(wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsWrongObjectType() {
    assertTrue(!wimbaVirtualToolDataProvider.equals("foo"));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullUrl1() {
    wimbaVirtualToolDataProvider.ltiUrl = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullUrl2() {
    otherWimbaVirtualToolDataProvider.ltiUrl = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullUrl3() {
    wimbaVirtualToolDataProvider.ltiUrl = otherWimbaVirtualToolDataProvider.ltiUrl = null;
    assertTrue(wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullKey1() {
    wimbaVirtualToolDataProvider.ltiKey = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullKey2() {
    otherWimbaVirtualToolDataProvider.ltiKey = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullKey3() {
    wimbaVirtualToolDataProvider.ltiKey = otherWimbaVirtualToolDataProvider.ltiKey = null;
    assertTrue(wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullSecret1() {
    wimbaVirtualToolDataProvider.ltiSecret = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullSecret2() {
    otherWimbaVirtualToolDataProvider.ltiSecret = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullSecret3() {
    wimbaVirtualToolDataProvider.ltiSecret = otherWimbaVirtualToolDataProvider.ltiSecret = null;
    assertTrue(wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullToolList1() {
    wimbaVirtualToolDataProvider.supportedVtoolIds = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullToolList2() {
    otherWimbaVirtualToolDataProvider.supportedVtoolIds = null;
    assertTrue(!wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#equals(Object)}
   */
  @Test
  public void testEqualsNullToolList3() {
    wimbaVirtualToolDataProvider.supportedVtoolIds = otherWimbaVirtualToolDataProvider.supportedVtoolIds = null;
    assertTrue(wimbaVirtualToolDataProvider.equals(otherWimbaVirtualToolDataProvider));
  }

  /**
   * Code coverage for {@link CLEVirtualToolDataProvider#toString()}
   */
  @Test
  public void testToString() {
    assertTrue(wimbaVirtualToolDataProvider.toString() != null
        && wimbaVirtualToolDataProvider.toString().length() > 0);
  }

  // --------------------------------------------------------------------------

  private void setupNonDefaultValues() {
    cleUrl = "altCleUrl";
    assertNotSame(cleUrl, wimbaVirtualToolDataProvider.ltiUrl);
    ltiKey = "altLtiKey";
    assertNotSame(ltiKey, wimbaVirtualToolDataProvider.ltiKey);
    ltiSecret = "altLtiSecret";
    assertNotSame(ltiSecret, wimbaVirtualToolDataProvider.ltiSecret);
    frameHeight = 99999999999L;
    frameHeightLock = false;
    urlLock = false;
    keyLock = false;
    secretLock = false;
    releaseNames = false;
    releaseNamesLock = false;
    releaseEmail = false;
    releaseEmailLock = false;
    releasePrincipal = false;
    releasePrincipalLock = false;
    debug = true;
    debugLock = false;

    // re-apply when statements with new values
    applyWhenConditions();
  }

  private void verifyConfiguration() {
    assertEquals(cleUrl, wimbaVirtualToolDataProvider.ltiUrl);
    assertEquals(ltiKey, wimbaVirtualToolDataProvider.ltiKey);
    assertEquals(ltiSecret, wimbaVirtualToolDataProvider.ltiSecret);
    assertEquals(toolList, wimbaVirtualToolDataProvider.supportedVtoolIds);
  }

  private void applyWhenConditions() {
    when(componentContext.getProperties()).thenReturn(properties);
    when(properties.get(WIMBA_LTI_URL)).thenReturn(cleUrl);
    when(properties.get(WIMBA_LTI_KEY)).thenReturn(ltiKey);
    when(properties.get(WIMBA_LTI_SECRET)).thenReturn(ltiSecret);
  }

  private void verifyLaunchValues(final Map<String, Object> launchValues) {
    assertNotNull(launchValues);
    assertEquals(cleUrl, launchValues.get(BasicLTIAppConstants.LTI_URL));
  }
}
