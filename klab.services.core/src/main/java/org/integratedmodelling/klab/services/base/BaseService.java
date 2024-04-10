package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for service implementations. A BaseService implements all the {@link KlabService} functions but
 * does not create the {@link ServiceScope} it runs within, which is supplied from the outside. can be wrapped
 * within a {@link org.integratedmodelling.klab.services.ServiceInstance} to provide a {@link ServiceScope}
 * and become usable.
 */
public abstract class BaseService implements KlabService {

    private final Type type;
    private String serviceSecret;

    private URL url;
    protected AtomicBoolean online = new AtomicBoolean(false);
    protected AtomicBoolean available = new AtomicBoolean(false);

    protected ServiceScope scope;
    protected String localName = "Embedded";
    protected final ServiceStartupOptions startupOptions;

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
                Configuration.INSTANCE.getFileWithTemplate("services/" + type.name().toLowerCase() +
                        "/secret.key", Utils.Names.newName());
        try {
            this.serviceSecret = Files.readString(secretFile.toPath());
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
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
        ret.setAvailable(serviceScope().isAvailable());
        ret.setBusy(serviceScope().isBusy());
        ret.setLocality(serviceScope().getLocality());
        return ret;
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

    public static File getDataDir(ServiceStartupOptions startupOptions) {
        return startupOptions.getDataDir() == null ? Configuration.INSTANCE.getDataPath() :
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
}
