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
package org.sakaiproject.nakamura.basiclti;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants;
import org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple little hacky class to supply LTI launch info for a Wimba LTI tool.
 */
@Component
@Service
public class WimbaVirtualToolDataProvider implements VirtualToolDataProvider {
  private static final Logger LOG = LoggerFactory
      .getLogger(WimbaVirtualToolDataProvider.class);
  protected static final String VTOOLID = "wimba.lti.vtool";
  transient protected List<String> supportedVtoolIds = Collections.emptyList();
  transient protected String ltiUrl = "http://www.imsglobal.org/developers/LTI/test/v1p1/tool.php";
  transient protected String ltiKey = "12345";
  transient protected String ltiSecret = "secret";

  @Property(value = "http://www.imsglobal.org/developers/LTI/test/v1p1/tool.php", name = "wimba.lti.url", description = "LTI URL for Wimba Server")
  protected static final String WIMBA_LTI_URL = "wimba.lti.url";
  @Property(value = "12345", name = "wimba.lti.key", description = "LTI Key for Wimba Server")
  protected static final String WIMBA_LTI_KEY = "wimba.lti.key";
  @Property(value = "secret", name = "wimba.lti.secret", description = "LTI Secret for Wimba Server")
  protected static final String WIMBA_LTI_SECRET = "wimba.lti.secret";

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider#getLaunchValues(java.lang.String)
   */
  @Override
  public Map<String, Object> getLaunchValues(String virtualToolId) {
    LOG.debug("getLaunchValues(String {})", virtualToolId);
    if (virtualToolId == null || "".equals(virtualToolId)) {
      throw new IllegalArgumentException("Illegal String virtualToolId");
    }
    Map<String, Object> launchValues = null;
    if (supportedVtoolIds.contains(virtualToolId)) {
      launchValues = new HashMap<String, Object>();
      launchValues.put(BasicLTIAppConstants.LTI_URL, ltiUrl);
      launchValues.put(BasicLTIAppConstants.FRAME_HEIGHT, 700);
      launchValues.put(BasicLTIAppConstants.FRAME_HEIGHT_LOCK, false);
      launchValues.put(BasicLTIAppConstants.LTI_URL_LOCK, true);
      launchValues.put(BasicLTIAppConstants.LTI_KEY_LOCK, true);
      launchValues.put(BasicLTIAppConstants.LTI_SECRET_LOCK, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_NAMES, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_NAMES_LOCK, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_EMAIL, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_EMAIL_LOCK, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_PRINCIPAL_NAME, true);
      launchValues.put(BasicLTIAppConstants.RELEASE_PRINCIPAL_NAME_LOCK, true);
      launchValues.put(BasicLTIAppConstants.DEBUG, false);
      launchValues.put(BasicLTIAppConstants.DEBUG_LOCK, false);
    }
    return launchValues;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider#getKeySecret(java.lang.String)
   */
  @Override
  public Map<String, Object> getKeySecret(String virtualToolId) {
    LOG.debug("getKeySecret(String {})", virtualToolId);
    if (virtualToolId == null || "".equals(virtualToolId)) {
      throw new IllegalArgumentException("Illegal String virtualToolId");
    }
    Map<String, Object> adminValues = null;
    if (supportedVtoolIds.contains(virtualToolId)) {
      adminValues = new HashMap<String, Object>(2);
      adminValues.put(BasicLTIAppConstants.LTI_KEY, ltiKey);
      adminValues.put(BasicLTIAppConstants.LTI_SECRET, ltiSecret);
    }
    return adminValues;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider#getSupportedVirtualToolIds()
   */
  @Override
  public List<String> getSupportedVirtualToolIds() {
    if (supportedVtoolIds == null) {
      return Collections.emptyList();
    } else {
      return supportedVtoolIds;
    }
  }

  @Activate
  protected void activate(ComponentContext componentContext) {
    LOG.debug("activate(ComponentContext componentContext)");
    supportedVtoolIds = new ArrayList<String>(1);
    supportedVtoolIds.add(VTOOLID);
    supportedVtoolIds = Collections.unmodifiableList(supportedVtoolIds);

    final Dictionary<?, ?> properties = componentContext.getProperties();
    ltiUrl = PropertiesUtil.toString(properties.get(WIMBA_LTI_URL), ltiUrl);
    ltiKey = PropertiesUtil.toString(properties.get(WIMBA_LTI_KEY), ltiKey);
    ltiSecret = PropertiesUtil.toString(properties.get(WIMBA_LTI_SECRET), ltiSecret);
  }

  /**
   * Default constructor
   */
  public WimbaVirtualToolDataProvider() {
    LOG.debug("new WimbaVirtualToolDataProvider()");
    supportedVtoolIds = new ArrayList<String>(1);
    supportedVtoolIds.add(VTOOLID);
    supportedVtoolIds = Collections.unmodifiableList(supportedVtoolIds);
  }

  /**
   * {@inheritDoc}
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ltiKey == null) ? 0 : ltiKey.hashCode());
    result = prime * result + ((ltiSecret == null) ? 0 : ltiSecret.hashCode());
    result = prime * result + ((ltiUrl == null) ? 0 : ltiUrl.hashCode());
    result = prime * result
        + ((supportedVtoolIds == null) ? 0 : supportedVtoolIds.hashCode());
    return result;
  }

  /**
   * {@inheritDoc}
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof WimbaVirtualToolDataProvider)) {
      return false;
    }
    WimbaVirtualToolDataProvider other = (WimbaVirtualToolDataProvider) obj;
    if (ltiKey == null) {
      if (other.ltiKey != null) {
        return false;
      }
    } else if (!ltiKey.equals(other.ltiKey)) {
      return false;
    }
    if (ltiSecret == null) {
      if (other.ltiSecret != null) {
        return false;
      }
    } else if (!ltiSecret.equals(other.ltiSecret)) {
      return false;
    }
    if (ltiUrl == null) {
      if (other.ltiUrl != null) {
        return false;
      }
    } else if (!ltiUrl.equals(other.ltiUrl)) {
      return false;
    }
    if (supportedVtoolIds == null) {
      if (other.supportedVtoolIds != null) {
        return false;
      }
    } else if (!supportedVtoolIds.equals(other.supportedVtoolIds)) {
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "WimbaVirtualToolDataProvider [supportedVtoolIds=" + supportedVtoolIds
        + ", ltiUrl=" + ltiUrl + "]";
  }
}
