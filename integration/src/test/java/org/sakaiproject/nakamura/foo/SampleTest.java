package org.sakaiproject.nakamura.foo;
 
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;
 
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.spi.reactors.AllConfinedStagedReactorFactory;
import org.ops4j.pax.exam.util.ServiceLookup;
import org.osgi.framework.BundleContext;
import javax.inject.Inject;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
 
@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(AllConfinedStagedReactorFactory.class)
public class SampleTest {
 
    @Inject
    private StorageClientPool helloService;
 
    @Configuration
    public Option[] config() {
 
        return options(
            mavenBundle("org.sakaiproject.nakamura", "org.sakaiproject.nakamura.core", "1.3"),
            mavenBundle("org.sakaiproject.nakamura", "org.sakaiproject.nakamura.woodstox", "4.0.7.1.2-SNAPSHOT"),
            mavenBundle("com.googlecode.guava-osgi", "guava-osgi", "10.0.1"),
            mavenBundle("org.apache.geronimo.specs", "geronimo-jta_1.1_spec", "1.1.1"),
            mavenBundle("org.apache.felix", "org.apache.felix.http.jetty", "2.2.0"),
            mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.2.8"),
            mavenBundle("org.apache.felix", "org.apache.felix.eventadmin", "1.2.14"),
            mavenBundle("org.apache.sling", "org.apache.sling.commons.log", "2.1.2"),
            mavenBundle("commons-codec", "commons-codec", "1.5"),
            mavenBundle("commons-io", "commons-io", "2.1"),
            mavenBundle("commons-lang", "commons-lang", "2.6"),
            mavenBundle("commons-collections", "commons-collections", "3.2.1"),
            mavenBundle("commons-fileupload", "commons-fileupload", "1.2.2"),
            mavenBundle("commons-logging", "commons-logging", "1.1.1"),
            mavenBundle("commons-pool", "commons-pool", "1.5.6"),
            junitBundles(),
            felix().version("3.2.2")
            );
    }
 
    @Test
    public void getHelloService() {
        assertNotNull(helloService);
        assertEquals("Hello Pax!", helloService.getStorageCacheManager());
    }
}