package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for service implementations. A BaseService implements all the {@link KlabService} functions but
 * does not create the {@link ServiceScope} it runs within, which is supplied from the outside. can be wrapped
 * within a {@link org.integratedmodelling.klab.services.ServiceInstance} to provide a {@link ServiceScope}
 * and become usable.
 */
public abstract class BaseService implements KlabService {

    protected final Type type;
    private String serviceSecret;

    protected AtomicBoolean online = new AtomicBoolean(false);
    protected AtomicBoolean available = new AtomicBoolean(false);

    private static final long serialVersionUID = 1646569587945609013L;

    protected ServiceScope scope;
    protected String localName = "Embedded";
    private ServiceStartupOptions startupOptions;

    //    protected List<BiConsumer<Scope, Message>> eventListeners = new ArrayList<>();

    protected BaseService(ServiceScope scope, KlabService.Type serviceType, ServiceStartupOptions options) {
        this.scope = scope;
        this.localName = localName;
        this.type = serviceType;
        this.startupOptions = options;
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

    public static File getConfigurationDirectory(KlabService.Type serviceType,
                                                 ServiceStartupOptions startupOptions) {
        var ret =
                new File(startupOptions.fileFromPath(startupOptions.getConfigurationPath()) + File.separator + serviceType.name().toLowerCase());
        ret.mkdirs();
        return ret;
    }

    public static File getConfigurationSubdirectory(KlabService.Type serviceType,
                                                    ServiceStartupOptions startupOptions,
                                                    String relativePath) {
        var ret =
                new File(startupOptions.fileFromPath(startupOptions.getConfigurationPath()) + File.separator
                        + serviceType.name().toLowerCase() + (relativePath.startsWith("/") ? relativePath : ("/" + relativePath)));
        ret.mkdirs();
        return ret;
    }


    public static File getFileInConfigurationDirectory(Type type, ServiceStartupOptions options,
                                                       String filename) {
        return new File(getConfigurationDirectory(type, options) + File.separator + filename);
    }

    public static File getFileInConfigurationSubdirectory(Type type, ServiceStartupOptions options,
                                                          String subdirectory, String filename) {
        return new File(getConfigurationSubdirectory(type, options, subdirectory) + File.separator + filename);
    }
}
