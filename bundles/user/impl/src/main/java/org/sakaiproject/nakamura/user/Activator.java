package org.sakaiproject.nakamura.user;

import net.sourceforge.cobertura.coveragedata.ProjectData;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public void start(BundleContext bundleContext) throws Exception {
  }

  public void stop(BundleContext bundleContext) throws Exception {
    try {
      LOGGER.info("stopping bundle, writing code coverage data");
      ProjectData.saveGlobalProjectData();
    } catch (Exception e) {
      LOGGER.warn("problem writing code coverage data during bundle stop")
    }
  }
}