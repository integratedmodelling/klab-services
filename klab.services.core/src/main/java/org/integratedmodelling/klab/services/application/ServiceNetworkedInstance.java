package org.integratedmodelling.klab.services.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.branding.Branding;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.messaging.WebsocketsServerMessageBus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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

import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security", "org" +
        ".integratedmodelling.klab.services.application.controllers"})
public abstract class ServiceNetworkedInstance<T extends BaseService> extends ServiceInstance<T> implements WebMvcConfigurer {

    private long bootTime;
    private ConfigurableApplicationContext context;
    private T klabService;

    @Override
    public boolean start(ServiceStartupOptions startupOptions) {

        try {
            super.start(startupOptions);
            this.klabService = super.klabService();
            SpringApplication app = new SpringApplication(this.getClass());
            this.context = app.run(startupOptions.getArguments());
            Environment environment = this.context.getEnvironment();
            setPropertiesFromEnvironment(environment);
            Map<String, Object> props = new HashMap<>();
            props.put("server.port", "" + startupOptions.getPort());
            props.put("spring.main.banner-mode", "off");
            props.put("server.servlet.contextPath", startupOptions.getContextPath());

            app.setDefaultProperties(props);
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println("\nStartup successful: " + "k.LAB service " + startupOptions.getContextPath().toUpperCase() + " v" + Version.CURRENT + " on " + new Date());
        } catch (Throwable e) {
            Logging.INSTANCE.error(e);
            return false;
        }
        return true;
    }

    @Bean
    public T klabService() {
        return klabService;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                        .enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        // objectMapper.getSerializerProvider().setNullKeySerializer(new Jsr310NullKeySerializer());
        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);
        return objectMapper;
    }

    protected Channel createChannel(UserIdentity identity) {
        return new MessagingChannelImpl(identity, new WebsocketsServerMessageBus());
    }

    private static void setPropertiesFromEnvironment(Environment environment) {
        MutablePropertySources propSrcs = ((ConfigurableEnvironment) environment).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false).filter(ps -> ps instanceof EnumerablePropertySource).map(ps -> ((EnumerablePropertySource) ps).getPropertyNames()).flatMap(Arrays::<String>stream).forEach(propName -> {
            if (propName.contains("klab.")) {
                Configuration.INSTANCE.getProperties().setProperty(propName,
                        environment.getProperty(propName));
            }
        });
        return;
    }

    //    @PreDestroy
    public void shutdown() {
        super.stop();
        this.context.stop();
    }

//    @Bean
//    public ProtobufJsonFormatHttpMessageConverter ProtobufJsonFormatHttpMessageConverter() {
//        return new ProtobufJsonFormatHttpMessageConverter();
//    }

    //    @Bean
    //    public RestTemplate restTemplate(ProtobufHttpMessageConverter hmc) {
    //        return new RestTemplate(Arrays.asList(hmc));
    //    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        /**
         * Handle maintenance mode and wait mode, defaulting to maintenance mode after configurable timeout
         */
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) throws Exception {
                if (!klabService().isOnline()) {
                    // response.sendRedirect(maintenanceMapping); return false;
                } else if (!klabService().isAvailable()) {
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
