package org.integratedmodelling.klab.services.application;

import java.io.File;
import java.util.*;
import java.util.stream.StreamSupport;

import org.integratedmodelling.common.authentication.KlabCertificateImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.branding.Branding;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.security.JWTAuthenticationManager;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.base.BaseService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

/**
 * Service wrapper to turn a service base implementation into a Spring-enabled online service. Must supply
 * controllers in the corresponding {@link ServiceApplication}.
 *
 * @author ferdinando.villa
 */
public abstract class Service<T extends BaseService> {

    int port = /*IConfigurationService.DEFAULT_NODE_PORT*/ -1; // TODO
    private ConfigurableApplicationContext context;
    private String contextPath = "/node";
    private Identity owner;
    private KlabCertificate certificate;

    private BaseService service;

    private static long bootTime;

    public Service(T service) {
        this.service = service;
        this.port = service.capabilities().getType().defaultPort;
    }

    /**
     * Return the type of the other services required for this service to be online.
     *
     * @return
     */
    protected abstract List<KlabService.Type> getEssentialServices();

    public Service(T service, StartupOptions options, KlabCertificate certificate) {
        this(service);
        this.certificate = certificate;
        this.owner = JWTAuthenticationManager.INSTANCE.authenticateService(certificate, options);
    }

    public String getLocalAddress() {
        return "http://127.0.0.1:" + port + contextPath;
    }

    public void run(String[] args) {
        ServiceStartupOptions options = new ServiceStartupOptions();
        options.initialize(args);
    }

//    public void start(T service) {
//        start(service, new ServiceStartupOptions());
//    }

    public void start(StartupOptions options) {

        if (!options.isCloudConfig()) {

            KlabCertificate certificate = null;

            if (options.getCertificateResource() != null) {
                certificate = KlabCertificateImpl.createFromClasspath(options.getCertificateResource());
            } else {
                File certFile = options.getCertificateFile();
                certificate = certFile.exists() ? KlabCertificateImpl.createFromFile(certFile)
                                                : KlabCertificateImpl.createDefault();
            }

            if (!certificate.isValid()) {
                throw new KlabAuthorizationException("certificate is invalid: " + certificate.getInvalidityCause());
            }

//            /*
//             * This authenticates with the hub
//             *
//             */
//            Service ret = new Service(service, options, certificate);

            if (!boot(options)) {
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
     * @param options
     * @return
     */
    private boolean boot(StartupOptions options) {
        try {
            SpringApplication app = new SpringApplication(ServiceApplication.class);
            this.context = app.run(options.getArguments());
            Environment environment = this.context.getEnvironment();
            setPropertiesFromEnvironment(environment);
            this.port = options.getPort();
            Map<String, Object> props = new HashMap<>();
            props.put("server.port", "" + options.getPort());
            props.put("spring.main.banner-mode", "off");
            props.put("server.servlet.contextPath", contextPath);
            app.setDefaultProperties(props);
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println(
                    "\nStartup successful: " + "k.LAB service " + service.getLocalName() + " v" + Version.CURRENT + " on " + new Date());

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
            SpringApplication app = new SpringApplication(ServiceApplication.class);
            this.context = app.run();
            Environment environment = this.context.getEnvironment();
            String certString = environment.getProperty("klab.certificate");
            this.certificate = KlabCertificateImpl.createFromString(certString);
            setPropertiesFromEnvironment(environment);
            this.owner = JWTAuthenticationManager.INSTANCE.authenticateService(certificate,
                    new ServiceStartupOptions());
            System.out.println("\n" + Branding.NODE_BANNER);
            System.out.println(
                    "\nStartup successful: " + "k.LAB node server" + " v" + Version.CURRENT + " on " + new Date());

        } catch (Throwable e) {
            Logging.INSTANCE.error(e);
            return false;
        }

        bootTime = System.currentTimeMillis();
        //		Klab.INSTANCE.setRootIdentity(owner);

        return true;
    }

    public void stop() {
        // // shutdown all components
        // if (this.sessionClosingTask != null) {
        // this.sessionClosingTask.cancel(true);
        // }
        //
        // // shutdown the task executor
        // if (taskExecutor != null) {
        // taskExecutor.shutdown();
        // try {
        // if (!taskExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // taskExecutor.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // taskExecutor.shutdownNow();
        // }
        // }
        //
        // // shutdown the script executor
        // if (scriptExecutor != null) {
        // scriptExecutor.shutdown();
        // try {
        // if (!scriptExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // scriptExecutor.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // scriptExecutor.shutdownNow();
        // }
        // }
        //
        // // and the session scheduler
        // if (scheduler != null) {
        // scheduler.shutdown();
        // try {
        // if (!scheduler.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // scheduler.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // scheduler.shutdownNow();
        // }
        // }
        //
        // // shutdown the runtime
        // Klab.INSTANCE.getRuntimeProvider().shutdown();

        context.close();
    }

    public Identity getOwner() {
        return owner;
    }

    public KlabCertificate getCertificate() {
        return certificate;
    }

    //	public Engine getEngine() {
    //		return engine;
    //	}


    private static void setPropertiesFromEnvironment(Environment environment) {
        MutablePropertySources propSrcs = ((ConfigurableEnvironment) environment).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                     .filter(ps -> ps instanceof EnumerablePropertySource)
                     .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                     .flatMap(Arrays::<String>stream)
                     .forEach(propName -> {
                         if (propName.contains("klab.")) {
                             Configuration.INSTANCE.getProperties().setProperty(propName,
                                     environment.getProperty(propName));
                         }
                     });
        return;
    }

    public static long getBootTime() {
        return bootTime;
    }


}
