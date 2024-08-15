package org.integratedmodelling.klab.services.base;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Base class for service implementations. A BaseService implements all the {@link KlabService} functions but
 * does not create the {@link ServiceScope} it runs within, which is supplied from the outside. can be wrapped
 * within a {@link org.integratedmodelling.klab.services.ServiceInstance} to provide a {@link ServiceScope}
 * and become usable.
 */
public abstract class BaseService implements KlabService {

    private final Type type;
    protected EmbeddedBroker embeddedBroker;
    private String serviceSecret;

    private URL url;
    protected AtomicBoolean online = new AtomicBoolean(false);
    protected AtomicBoolean available = new AtomicBoolean(false);
    private final List<Notification> serviceNotifications = new ArrayList<>();
    protected ServiceScope scope;
    protected String localName = "Embedded";
    protected final ServiceStartupOptions startupOptions;
    private ScopeManager _scopeManager;
    private boolean initialized;

    protected BaseService(ServiceScope scope, KlabService.Type serviceType, ServiceStartupOptions options) {
        this.scope = scope;
        this.localName = localName;
        this.type = serviceType;
        this.startupOptions = options;
        try {
            this.url =
                    new URL(options.getServiceHostUrl() + ":" + options.getPort() + options.getContextPath());
        } catch (MalformedURLException e) {
            throw new KlabIllegalStateException(e);
        }
        createServiceSecret();
    }

    protected ServiceStartupOptions getStartupOptions() {
        return startupOptions;
    }

    /**
     * Create a unique ID that will be
     */
    private void createServiceSecret() {
        File secretFile =
                ServiceConfiguration.INSTANCE.getFileWithTemplate("services/" + type.name().toLowerCase() +
                        "/secret.key", Utils.Names.newName());
        try {
            this.serviceSecret = Files.readString(secretFile.toPath());
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public EmbeddedBroker getEmbeddedBroker() {
        return embeddedBroker;
    }

    /**
     * Set up the messaging queues according to configuration in case the user is local and privileged. TODO
     * this ignores the configuration for now.
     *
     * @param scope
     * @param capabilities
     */
    public void setupMessaging(UserScope scope, ServiceCapabilities capabilities) {
        if (scope instanceof ServiceUserScope serviceUserScope && serviceUserScope.isLocal()) {
            capabilities.getAvailableMessagingQueues().add(Message.Queue.Errors);
            capabilities.getAvailableMessagingQueues().add(Message.Queue.Warnings);
            capabilities.getAvailableMessagingQueues().add(Message.Queue.Info);
            // TODO configure debug
        }
    }

    /**
     * Use this broker in local configurations unless a broker URL is specified in configuration
     *
     * @return
     */
    protected EmbeddedBroker getLocalBroker() {
        return null;
    }

    /**
     * The scope manager is created on demand as not all services need it.
     *
     * @return
     */
    public ScopeManager getScopeManager() {
        if (_scopeManager == null) {
            _scopeManager = new ScopeManager(this);
        }
        return _scopeManager;
    }

    /**
     * The service secret is a legitimate API key for the service, only known to clients that can read it
     * because they are sharing the filesystem. These clients can access the service by just stating their
     * privileges, without authenticating through the hub.
     * <p>
     * The secret must NEVER be sent through the network - capabilities, status or anything.
     *
     * @return
     */
    public String getServiceSecret() {
        return this.serviceSecret;
    }

    /**
     * Override this to fill in the known parameters, i.e. everything except free/total memory.
     *
     * @return
     */
    public ServiceStatus status() {
        var ret = new ServiceStatusImpl();
        ret.setServiceId(serviceId());
        ret.setServiceType(serviceType());
        ret.setAvailable(initialized && serviceScope().isAvailable());
        ret.setBusy(serviceScope().isBusy());
        ret.setLocality(serviceScope().getLocality());
        return ret;
    }

    /**
     * Scan the passed packages for classes annotated with <code>annotationClass</code> and call the consumer
     * passing the annotation found and the class for each matching class..
     * <p>
     * This can be called with a pre-defined array of annotations using the similar method in
     * {@link ServiceConfiguration} for a quicker scan.
     *
     * @param annotationHandler
     * @param annotationClass
     * @param packages          if not passed, everything is scanned (highly NOT recommended).
     * @param <T>
     */
    protected <T extends Annotation> void scanPackages(BiConsumer<T, Class<?>> annotationHandler,
                                                       Class<T> annotationClass, String... packages) {

        if (packages == null) {
            packages = new String[]{"*"};
        }

        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAnnotationInfo()
                             .acceptPackages(packages)
                             .scan()) {
            for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(annotationClass)) {
                try {
                    Class<?> cls = Class.forName(routeClassInfo.getName());
                    T annotation = cls.getAnnotation(annotationClass);
                    if (annotation != null) {
                        annotationHandler.accept(annotation, cls);
                    }
                } catch (ClassNotFoundException e) {
                    Logging.INSTANCE.error(e);
                }
            }
        }
    }

    protected Collection<Notification> serviceNotifications() {
        return this.serviceNotifications;
    }

    public String getLocalName() {
        // TODO Auto-generated method stub
        return localName;
    }

    @Override
    public ServiceScope serviceScope() {
        return scope;
    }

    public abstract void initializeService();

    @Override
    public boolean shutdown() {
        _scopeManager.shutdown();
        return true;
    }

    public static File getDataDir(ServiceStartupOptions startupOptions) {
        return startupOptions.getDataDir() == null ? ServiceConfiguration.INSTANCE.getDataPath() :
               startupOptions.getDataDir();
    }

    public static File getConfigurationDirectory(ServiceStartupOptions startupOptions) {
        var ret =
                new File(getDataDir(startupOptions) + File.separator + "services" + File.separator + startupOptions.getServiceType().name().toLowerCase());
        ret.mkdirs();
        return ret;
    }

    public static File getConfigurationSubdirectory(ServiceStartupOptions startupOptions,
                                                    String relativePath) {
        var ret = new File(getConfigurationDirectory(startupOptions)
                + ((relativePath.startsWith("/") ? relativePath : (File.separator + relativePath))));
        ret.mkdirs();
        return ret;
    }

    public static File getFileInConfigurationDirectory(ServiceStartupOptions options,
                                                       String filename) {
        return new File(getConfigurationDirectory(options) + File.separator + filename);
    }

    public static File getFileInConfigurationSubdirectory(ServiceStartupOptions options,
                                                          String subdirectory, String filename) {
        return new File(getConfigurationSubdirectory(options, subdirectory) + File.separator + filename);
    }

    public ServiceStartupOptions startupOptions() {
        return startupOptions;
    }

    public KlabService.Type serviceType() {
        return type;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public boolean isExclusive() {
        // TODO
        // as a matter of principle, no service instance is exclusive by default. If a client is using the
        // secret token it can assume the service is exclusive, although there should be some type of locking
        // done to ensure that that is true. A way to do that could be to set busy status for any client
        // that isn't  using the secret token, and only allow these clients to lock the service that way.
        return false;
    }


    @Override
    public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
        return null;
    }

    @Override
    public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
        return false;
    }

    @Override
    public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
        return Authentication.INSTANCE.getCredentialInfo(scope);
    }

    @Override
    public ExternalAuthenticationCredentials.CredentialInfo addCredentials(String host,
                                                                           ExternalAuthenticationCredentials credentials, Scope scope) {
        return Authentication.INSTANCE.addExternalCredentials(host, credentials, scope);
    }

    /**
     * Called by ServiceInstance after initializeService was successful
     * @param b
     */
    public void setInitialized(boolean b) {
        this.initialized = true;
    }

    public boolean isInitialized() {
        return this.initialized;
    }
}
