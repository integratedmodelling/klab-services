package org.integratedmodelling.klab.services.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.authentication.KlabCertificateImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.branding.Branding;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
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
import java.io.File;
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
@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security", "org" +
        ".integratedmodelling.klab.services.application.controllers"})
public abstract class ServiceNetworkedInstance<T extends BaseService> extends ServiceInstance<T> implements WebMvcConfigurer {

    private long bootTime;
    private ConfigurableApplicationContext context;

    public ServiceNetworkedInstance(ServiceStartupOptions options) {
        super(options);
    }

    //    private AtomicBoolean maintenanceMode = new AtomicBoolean(false);
    //    private AtomicBoolean atomicOperationMode = new AtomicBoolean(false);

    //    public void run(Service<?> klabService, String[] args) {
    //        this.service = klabService;
    //        ServiceStartupOptions options = new ServiceStartupOptions();
    //        options.initialize(args);
    //        klabService.start();
    //    }


    public void start(ServiceStartupOptions startupOptions) {

        if (!startupOptions.isCloudConfig()) {

            KlabCertificate certificate = null;

            if (startupOptions.getCertificateResource() != null) {
                certificate =
                        KlabCertificateImpl.createFromClasspath(startupOptions.getCertificateResource());
            } else {
                File certFile = startupOptions.getCertificateFile();
                certificate = certFile.exists() ? KlabCertificateImpl.createFromFile(certFile) :
                              KlabCertificateImpl.createDefault();
            }

            if (!certificate.isValid()) {
                throw new KlabAuthorizationException("certificate is invalid: " + certificate.getInvalidityCause());
            }

            //            /*
            //             * This authenticates with the hub
            //             *
            //             */
            //            Service ret = new Service(service, options, certificate);

            if (!boot(startupOptions)) {
                throw new KlabServiceAccessException("service failed to start");
            }

            //            return ret;

        } else {
            if (!boot()) {
                throw new KlabServiceAccessException("service failed to start");
            }

            //            return ret;
        }
    }

    /**
     * The initialize() method of the service should be called in a thread and wait for the configured
     * services needed, setting the available flag as soon as boot is complete.
     *
     * @return
     */
    private boolean boot(StartupOptions startupOptions) {
        try {

            bootTime = System.currentTimeMillis();

            SpringApplication app = new SpringApplication(ServiceNetworkedInstance.class);
            this.context = app.run(startupOptions.getArguments());
            Environment environment = this.context.getEnvironment();
            setPropertiesFromEnvironment(environment);
            var port = startupOptions.getPort();
            Map<String, Object> props = new HashMap<>();
            props.put("server.port", "" + startupOptions.getPort());
            props.put("spring.main.banner-mode", "off");
            props.put("server.servlet.contextPath", startupOptions);
            app.setDefaultProperties(props);
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println("\nStartup successful: " + "k.LAB service " + klabService().getLocalName() + " " + "v" + Version.CURRENT + " on " + new Date());

            // TODO call initialize in a thread; inform the application

        } catch (Throwable e) {
            Logging.INSTANCE.error(e);
            return false;
        }

        bootTime = System.currentTimeMillis();

        return true;
    }

    private boolean boot() {
        try {

            SpringApplication app = new SpringApplication(ServiceNetworkedInstance.class);
            this.context = app.run();
            Environment environment = this.context.getEnvironment();
            String certString = environment.getProperty("klab.certificate");
            //            this.certificate = KlabCertificateImpl.createFromString(certString);
            setPropertiesFromEnvironment(environment);
            //            this.owner = JWTAuthenticationManager.INSTANCE.authenticateService(certificate,
            //                    new ServiceStartupOptions());
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println("\nStartup successful: " + "k.LAB node server" + " v" + Version.CURRENT + " "
                    + "on " + new Date());

        } catch (Throwable e) {
            Logging.INSTANCE.error(e);
            return false;
        }

        bootTime = System.currentTimeMillis();
        //		Klab.INSTANCE.setRootIdentity(owner);

        return true;
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

    @PreDestroy
    public void shutdown() {
        // TODO engine shutdown if needed
    }

    @Bean
    public ProtobufJsonFormatHttpMessageConverter ProtobufJsonFormatHttpMessageConverter() {
        return new ProtobufJsonFormatHttpMessageConverter();
    }

    @Bean
    public RestTemplate restTemplate(ProtobufHttpMessageConverter hmc) {
        return new RestTemplate(Arrays.asList(hmc));
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
                // response.sendRedirect(maintenanceMapping); return false;
                return HandlerInterceptor.super.preHandle(request, response, handler);
            }

        });
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //    public static void main(String args[]) {
    //        new ServiceApplication().run(args);
    //    }

}
