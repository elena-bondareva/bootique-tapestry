package io.bootique.tapestry;

import io.bootique.jetty.JettyModule;
import io.bootique.tapestry.testapp2.bq.TestApp2BootiqueModule;
import io.bootique.test.junit.BQTestFactory;
import org.apache.tapestry5.services.LibraryMapping;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TapestryModuleIT {

    private static WebTarget BASE_TARGET = ClientBuilder.newClient().target("http://127.0.0.1:8080/");

    @Rule
    public BQTestFactory app = new BQTestFactory();

    @Test
    public void testPageRender_Index() {
        app.app("-s")
                .module(new TapestryModuleProvider())
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp1")
                .run();

        assertHtml("/", "Index", "[xyz]");
    }

    @Test
    public void testPageRender_Page2() {
        app.app("-s")
                .module(new TapestryModuleProvider())
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp1")
                .run();

        assertHtml("/page2", "I am wrapped", "[I am page2 body]");
    }

    @Test
    public void testPageRender_T5_Injection() {
        app.app("-s")
                .module(new TapestryModuleProvider())
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp2")
                .property("bq.tapestry.name", "testapp2")
                .run();

        assertHtml("/", "Index", "[III]");
    }

    @Test
    public void testPageRender_T5_BQInjection() {
        app.app("-s")
                .module(new TapestryModuleProvider())
                .modules(TestApp2BootiqueModule.class)
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp2")
                .property("bq.tapestry.name", "testapp2")
                .run();

        assertHtml("/bqservices", "BQServices", "{III}");
    }

    @Test
    public void testPageRender_T5_BQInjection_Annotations() {
        app.app("-s", "testarg", "testarg2")
                .module(new TapestryModuleProvider())
                .modules(TestApp2BootiqueModule.class)
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp2")
                .property("bq.tapestry.name", "testapp2")
                .run();

        assertHtml("/bqannotatedservices", "BQAnnotatedServices", "-s_testarg_testarg2");
    }

    @Test
    public void testPageRender_LibComponent() {
        app.app("-s")
                .module(new TapestryModuleProvider())
                .module(b -> TapestryModule.extend(b)
                        .addLibraryMapping(new LibraryMapping("lib", "io.bootique.tapestry.testlib1")))
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp2")
                .run();

        assertHtml("/bqpagewithlibcomponent", "Index with Lib", "<b>__val__</b>");
    }

    @Test
    public void testIgnorePaths() {
        app.app("-s")
                .module(JettyModule.class)
                .module(TapestryModule.class)
                .module(b -> {
                    TapestryModule.extend(b).addIgnoredPath("/ignored_by_tapestry/*");
                    JettyModule.extend(b).useDefaultServlet();
                })
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp1")
                .property("bq.jetty.staticResourceBase", "classpath:docroot")
                .run();

        assertHtml("/", "Index", "[xyz]");
        assertHtml("/ignored_by_tapestry/static.html", "Static", "I am a static file");
    }

    @Test
    public void testPageRender_T5Modules() {
        app.app("-s")
                .module(JettyModule.class)
                .module(TapestryModule.class)
                .module(b -> TapestryModule.extend(b).addTapestryModule(TestApp3Module.class))
                .property("bq.tapestry.appPackage", "io.bootique.tapestry.testapp3")
                .run();

        assertHtml("/page1", "Testapp3 Page1", ":DeferredServiceImpl:");
    }

    private void assertHtml(String uri, String expectedTitle, String expectedBody) {
        Response r = BASE_TARGET.path(uri).request(MediaType.TEXT_HTML).get();
        assertEquals(200, r.getStatus());

        String html = r.readEntity(String.class);

        // adding a small delay after reading the response. Otherwise container may start shutdown
        // when T5 request is still in progress, resulting in stack traces in the logs.
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
        }

        assertTrue(html.startsWith("<!DOCTYPE html><html"));
        assertTrue(html.endsWith("</html>"));

        assertTrue("Expected: " + expectedTitle, html.contains("<title>" + expectedTitle + "</title>"));
        assertTrue("Unexpected html: " + html, html.contains(expectedBody));
    }
}
