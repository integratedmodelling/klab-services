package org.integratedmodelling.klab.services.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.KlabCertificateImpl;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.branding.Branding;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.messaging.WebsocketsServerMessageBus;
import org.mapdb.elsa.ElsaSerializerBase;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import javax.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Abstract wrapper for a {@link org.integratedmodelling.klab.api.services.KlabService} that is served as an
 * online service through REST endpoints handled by Spring controllers. The base packages scanned below should
 * be ALSO present in the final derived class, which should add its own. That way all services can use the
 * same security model and present the same common API.
 * <p>
 * The {@link org.integratedmodelling.klab.api.scope.ServiceScope} is produced using a service certificate
 * located in the configured service dataspace. If that isn't available, the service will use the strategy
 * from the superclass and will only be available within the local network if the user is authenticated, or
 * the local machine if anonymous.
 */
@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security", "org" +
        ".integratedmodelling.klab.services.application.controllers"})
public abstract class ServiceNetworkedInstance<T extends BaseService> extends ServiceInstance<T> implements WebMvcConfigurer, InitializingBean {

    /*
     * overridden through properties in application.yml, if only it worked.
     * TODO take from certificate
     */
    private String version = Version.CURRENT;
    private String basePackage = "org.integratedmodelling.klab.services.reasoner.controllers";
    private String title = "k.LAB Reasoner API";
    private String description = "API documentation for the k.LAB reasoner service. POST methods use valid " +
            "concepts obtained through the resolve endpoints.";
    private String contactName = "Integrated Modelling Partnership";
    private String contactEmail = "info@integratedmodelling.org";

    @Autowired
    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private Environment environment;
    @Autowired
    ServiceAuthorizationManager authorizationManager;

    @Override
    protected Pair<Identity, List<ServiceReference>> authenticateService() {
        /**
         * TODO lookup service certificate in configuration path; if found, use that to build the
         *  identity. Otherwise proceed as per default. If service is certified, record the
         *  privileges and adjust the service scope.
         */
        File config = getStartupOptions().fileFromPath(getStartupOptions().getConfigurationPath());
        config = new File(config + File.separator + "klab.cert");
        if (config.isFile()) {
            return authorizationManager.authenticateService(KlabCertificateImpl.createFromFile(config),
                    getStartupOptions());
        }
        return super.authenticateService();
    }

    @Override
    protected AbstractServiceDelegatingScope createServiceScope() {
        var ret = super.createServiceScope();
        // TODO if we're certified, adjust the scope's locality and service discovery capabilities
        ret.setLocality(ServiceScope.Locality.LOCALHOST);
        if (ret.getIdentity() instanceof UserIdentity user && !user.isAnonymous()) {
            ret.setLocality(ServiceScope.Locality.LAN);
        } /* else if certified by partner/institution and configured for cloud, set to WAN */
        authorizationManager.setKlabService(klabService());
        return ret;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.start(environment.getRequiredProperty("klab.service.options", ServiceStartupOptions.class));
    }

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2).select().apis(RequestHandlerSelectors.basePackage(basePackage))
                                                      .paths(PathSelectors.any()).build().directModelSubstitute(LocalDate.class, java.sql.Date.class)
                                                      .directModelSubstitute(LocalDateTime.class,
                                                              java.util.Date.class).apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder().title(title).description(description).version(version)
                                   .contact(new Contact(contactName, null, contactEmail)).build();
    }

    /**
     * Call this in main() using the concrete subclass of ServiceNetworkedInstance and the desired startup
     * options.
     *
     * @param cls
     * @param options
     * @return
     */
    public static boolean start(Class<? extends ServiceNetworkedInstance> cls,
                                ServiceStartupOptions options) {

        try {
            SpringApplication app = new SpringApplication(cls);
            Map<String, Object> props = new HashMap<>();
            props.put("klab.service.options", options);
            props.put("server.port", "" + options.getPort());
            props.put("spring.main.banner-mode", "off");
            props.put("server.servlet.contextPath", options.getContextPath());
            app.setDefaultProperties(props);
            app.run(options.getArguments());
            //            Environment environment = this.context.getEnvironment();
            //            setPropertiesFromEnvironment(environment);
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println("\nStartup successful: " + "k.LAB service " + options.getContextPath().toUpperCase() + " v" + Version.CURRENT + " on " + new Date());
            System.out.println("Capabilities: http://127.0.0.1:" + options.getPort() + options.getContextPath() + ServicesAPI.CAPABILITIES);
        } catch (Throwable e) {
            Logging.INSTANCE.error(e);
            return false;
        }
        return true;
    }


    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper =
                new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        // objectMapper.getSerializerProvider().setNullKeySerializer(new Jsr310NullKeySerializer());
        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);
        return objectMapper;
    }

    protected Channel createChannel(UserIdentity identity) {
        return new MessagingChannelImpl(identity, new WebsocketsServerMessageBus());
    }

    private void setPropertiesFromEnvironment(Environment environment) {
        MutablePropertySources propSrcs = ((ConfigurableEnvironment) environment).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false).filter(ps -> ps instanceof EnumerablePropertySource).map(ps -> ((EnumerablePropertySource) ps).getPropertyNames()).flatMap(Arrays::<String>stream).forEach(propName -> {
            if (propName.contains("klab.")) {
                Configuration.INSTANCE.getProperties().setProperty(propName,
                        environment.getProperty(propName));
            }
        });
    }

    @PreDestroy
    public void stopService() {
        Logging.INSTANCE.info("Stopping service...");
        super.stop();
    }

    public void shutdown() {
        this.applicationContext.stop();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        /**
         * Handle maintenance mode and wait mode, defaulting to maintenance mode after configurable timeout
         */
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) throws Exception {
                if (!klabService().scope().isAvailable()) {
                    // response.sendRedirect(maintenanceMapping); return false;
                } else if (!klabService().scope().isBusy()) {
                    // TODO wait a configurable interval; if it's still not available, redirect, otherwise
                }
                return HandlerInterceptor.super.preHandle(request, response, handler);
            }
        });
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
