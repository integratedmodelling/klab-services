package org.integratedmodelling.resources.server;

import org.apache.catalina.startup.Tomcat;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security",
                               "org.integratedmodelling.klab.services.messaging",
                               "org.integratedmodelling.klab.services.application.controllers",
                               "org.integratedmodelling.resources.server.controllers"})
public class ResourcesServer extends ServiceNetworkedInstance<ResourcesProvider> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return List.of();
    }

    @Override
    protected List<KlabService.Type> getOperationalServices() {
        return List.of(KlabService.Type.REASONER);
    }

    @Override
    protected ResourcesProvider createPrimaryService(AbstractServiceDelegatingScope serviceScope,
                                                     ServiceStartupOptions options) {
        return new ResourcesProvider(serviceScope, options);
    }

    @Bean
    public TomcatServletWebServerFactory servletContainerFactory() {
        return new TomcatServletWebServerFactory() {

            @Override
            protected TomcatWebServer getTomcatWebServer(Tomcat tomcat) {

                // TODO add the WARs for the language servers at appropriate context paths

//                // webapps directory does not exist by default, needs to be created
//                new File(tomcat.getServer().getCatalinaBase(), "webapps").mkdirs();
//
//                // Add a war with given context path
//                // Can add multiple wars this way with different context paths
//                tomcat.addWebapp("context-path", "path-to-your-war.war");
//
                return super.getTomcatWebServer(tomcat);
            }

        };
    }
    public static void main(String[] args) {
        ServiceNetworkedInstance.start(ResourcesServer.class,
                ServiceStartupOptions.create(KlabService.Type.RESOURCES, args));
    }
}
