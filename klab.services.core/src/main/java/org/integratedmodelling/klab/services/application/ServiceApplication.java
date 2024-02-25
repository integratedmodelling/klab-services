package org.integratedmodelling.klab.services.application;

import java.util.Arrays;

import javax.annotation.PreDestroy;

import org.integratedmodelling.klab.api.services.KlabService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.security",
                               "org.integratedmodelling.klab.services.resources",
                               "org.integratedmodelling.klab.services.controllers"})
public class ServiceApplication<T extends KlabService> {

    private static Service service;

    public void run(KlabService klabService, String[] args) {
        ServiceStartupOptions options = new ServiceStartupOptions();
        options.initialize(args);
        service = Service.start(klabService, options);
    }

    @PreDestroy
    public void shutdown() {
        // TODO engine shutdown if needed
    }

    /**
     * TODO add maintenance mode filter/interceptor
     * @return
     */

    @Bean
    public ProtobufJsonFormatHttpMessageConverter ProtobufJsonFormatHttpMessageConverter() {
        return new ProtobufJsonFormatHttpMessageConverter();
    }

    @Bean
    public RestTemplate restTemplate(ProtobufHttpMessageConverter hmc) {
        return new RestTemplate(Arrays.asList(hmc));
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //	@Bean
    //	public ServletWebServerFactory servletContainer() {
    //		TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
    //			@Override
    //			protected TomcatWebServer getTomcatWebServer(Tomcat tomcat) {
    ////				try {
    ////					Context context = tomcat.addWebapp("/kgit", Node.gitWarPath.getAbsolutePath());
    ////					WebappLoader loader = new WebappLoader(Thread.currentThread()
    // .getContextClassLoader());
    ////					context.setLoader(loader);
    ////				} catch (ServletException e) {
    ////					throw new IllegalStateException("could not deploy the Git server from the
    // embedded war");
    ////				}
    //				return super.getTomcatWebServer(tomcat);
    //			}
    //		};
    //		return tomcat;
    //	}

//    public static void main(String args[]) {
//        new ServiceApplication().run(args);
//    }

}
