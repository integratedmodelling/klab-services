package org.integratedmodelling.klab.services.base;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.*;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.engine.ServiceMonitor;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;

/**
 * Base class for service implementations. A BaseService implements all the {@link KlabService}
 * functions but does not create the {@link ServiceScope} it runs within, which is supplied from the
 * outside. can be wrapped within a {@link org.integratedmodelling.klab.services.ServiceInstance} to
 * provide a {@link ServiceScope} and become usable.
 */
public abstract class BaseService implements KlabService {

  private final Type type;
  protected EmbeddedBroker embeddedBroker;
  private String serviceSecret;
//  private boolean provideScopesAutomatically = false;
  private URL url;
  //    protected AtomicBoolean online = new AtomicBoolean(false);
  protected AtomicBoolean available = new AtomicBoolean(false);
  private final List<Notification> serviceNotifications = new ArrayList<>();
  protected AbstractServiceDelegatingScope scope;
  protected String localName = "Embedded";
  protected final ServiceStartupOptions startupOptions;
  private ScopeManager _scopeManager;
  private boolean initialized;
  private boolean operational;
  private ComponentRegistry componentRegister;
  private String instanceKey = Utils.Names.newName();
  private long bootTime = System.currentTimeMillis();
  private ServiceMonitor serviceMonitor;

  protected Parameters<Engine.Setting> settingsForSlaveServices = Parameters.createSynchronized();

  protected BaseService(
      AbstractServiceDelegatingScope scope,
      KlabService.Type serviceType,
      ServiceStartupOptions options) {

    settingsForSlaveServices.put(Engine.Setting.POLLING, "on");
    settingsForSlaveServices.put(Engine.Setting.POLLING_INTERVAL, 15);
    settingsForSlaveServices.put(Engine.Setting.LOG_EVENTS, true);
    settingsForSlaveServices.put(Engine.Setting.LAUNCH_PRODUCT, false);

    this.scope = scope;
    this.type = serviceType;
    this.startupOptions = options;
    try {
      this.url =
          new URL(options.getServiceHostUrl() + ":" + options.getPort() + options.getContextPath());
    } catch (MalformedURLException e) {
      throw new KlabIllegalStateException(e);
    }
    createServiceSecret();
    componentRegister = new ComponentRegistry(this, options);
    
    serviceMonitor =
        new ServiceMonitor(
            scope.getIdentity(),
            settingsForSlaveServices,
            Utils.URLs.isLocalHost(this.url),
            List.of(),
            this::notifyLocalService,
            this::notifyLocalEngine);
  }

  private void notifyLocalEngine(Engine.Status status) {}

  private void notifyLocalService(KlabService service, ServiceStatus status) {}

  public ComponentRegistry getComponentRegistry() {
    return componentRegister;
  }

  protected ServiceStartupOptions getStartupOptions() {
    return startupOptions;
  }

  /**
   * Each service creates a secret key and stores in a text file in its work directory. The service
   * key is created only if absent and remains the same across boot cycles. It is used to grant
   * "local" admin privileges to any client that can find it and read it from the filesystem. The
   * secret key can be added to requests in the {@link
   * org.integratedmodelling.klab.api.ServicesAPI#SERVER_KEY_HEADER} and if it matches the one known
   * to the service admin access is granted independent of authentication.
   *
   * <p>The service also exposes an instance key that is different at each boot cycle and is used to
   * distinguish artifacts or state persisted by a previous instance that may be left over after
   * irregular termination, so they can be cleaned up.
   */
  private void createServiceSecret() {

    File secretFile =
        ServiceConfiguration.INSTANCE.getFileWithTemplate(
            "services/" + type.name().toLowerCase() + "/secret.key", Utils.Names.newName());
    try {
      this.serviceSecret = Files.readString(secretFile.toPath());
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  public EmbeddedBroker getEmbeddedBroker() {
    return embeddedBroker;
  }

  //  /**
  //   * Set up the messaging queues according to configuration in case the user is local and
  //   * privileged. TODO this ignores the configuration for now.
  //   *
  //   * @param scope
  //   * @param capabilities
  //   */
  //  public void setupMessaging(UserScope scope, ServiceCapabilities capabilities) {
  //    if (scope instanceof ServiceUserScope serviceUserScope && serviceUserScope.isLocal()) {
  //      capabilities.getAvailableMessagingQueues().add(Message.Queue.Errors);
  //      capabilities.getAvailableMessagingQueues().add(Message.Queue.Warnings);
  //      capabilities.getAvailableMessagingQueues().add(Message.Queue.Info);
  //      // TODO configure debug
  //    }
  //  }

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
   * The service secret is a legitimate API key for the service, only known to clients that can read
   * it because they are sharing the filesystem. These clients can access the service by just
   * stating their privileges, without authenticating through the hub.
   *
   * <p>The secret must NEVER be sent through the network - capabilities, status or anything.
   *
   * @return
   */
  public String getServiceSecret() {
    return this.serviceSecret;
  }

  /**
   * The instance key can be added to any artifact that must be cleaned up after irregular
   * termination to distinguish anything that may have been left over.
   *
   * @return
   */
  public String getInstanceKey() {
    return this.instanceKey;
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
    ret.setOperational(operational);
    ret.setUptimeMs(System.currentTimeMillis() - this.bootTime);
    return ret;
  }

  /**
   * Scan the passed packages for classes annotated with <code>annotationClass</code> and call the
   * consumer passing the annotation found and the class for each matching class..
   *
   * <p>This can be called with a pre-defined array of annotations using the similar method in
   * {@link ServiceConfiguration} for a quicker scan.
   *
   * @param annotationHandler
   * @param annotationClass
   * @param packages if not passed, everything is scanned (highly NOT recommended).
   * @param <T>
   */
  protected <T extends Annotation> void scanPackages(
      BiConsumer<T, Class<?>> annotationHandler, Class<T> annotationClass, String... packages) {

    if (packages == null) {
      packages = new String[] {"*"};
    }

    try (ScanResult scanResult =
        new ClassGraph().enableAnnotationInfo().acceptPackages(packages).scan()) {
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
  public AbstractServiceDelegatingScope serviceScope() {
    return scope;
  }

  /**
   * Called when all the essential services are available. The non-essential "operational" services
   * will not necessarily be available yet.
   */
  public abstract void initializeService();

  /**
   * Called when all non-essential operational services become available. The return value will be
   * the operational status returned in {@link #status()}. Operational means that the API is usable
   * as advertised in {@link #capabilities(Scope)}.
   */
  public abstract boolean operationalizeService();

  @Override
  public boolean shutdown() {
    _scopeManager.shutdown();
    return true;
  }

  public static File getDataDir(ServiceStartupOptions startupOptions) {
    return startupOptions.getDataDir() == null
        ? ServiceConfiguration.INSTANCE.getDataPath()
        : startupOptions.getDataDir();
  }

  public static File getConfigurationDirectory(ServiceStartupOptions startupOptions) {
    var ret =
        new File(
            getDataDir(startupOptions)
                + File.separator
                + "services"
                + File.separator
                + startupOptions.getServiceType().name().toLowerCase());
    ret.mkdirs();
    return ret;
  }

  public static File getConfigurationSubdirectory(
      ServiceStartupOptions startupOptions, String relativePath) {
    var ret =
        new File(
            getConfigurationDirectory(startupOptions)
                + ((relativePath.startsWith("/")
                    ? relativePath
                    : (File.separator + relativePath))));
    ret.mkdirs();
    return ret;
  }

  public static File getFileInConfigurationDirectory(
      ServiceStartupOptions options, String filename) {
    return new File(getConfigurationDirectory(options) + File.separator + filename);
  }

  public static File getFileInConfigurationSubdirectory(
      ServiceStartupOptions options, String subdirectory, String filename) {
    return new File(
        getConfigurationSubdirectory(options, subdirectory) + File.separator + filename);
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

  protected boolean isOperational() {
    return operational;
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
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    return Authentication.INSTANCE.addExternalCredentials(host, credentials, scope);
  }

  /**
   * Register a session created by the scope manager after receving a CREATE_SESSION request. Return
   * a unique session ID that may be requested with the session or generated within the service.
   *
   * @param sessionScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @return the ID of the new session created at server side, or null in case of failure.
   */
  public String registerSession(
      SessionScope sessionScope, Federation federation) {
    return sessionScope instanceof ServiceSessionScope serviceSessionScope
        ? serviceSessionScope.getId()
        : null;
  }

  /**
   * Register a context scope created by the scope manager Return a * unique session ID that may be
   * requested with the session or generated within the service. Context starts empty with the
   * default observer for the worldview, using the services available to the user and passed as
   * parameters. The same runtime that hosts the context must become the one and only runtime
   * accessible to the resulting scope. If the service is not a runtime, the request must come from
   * another service and the scope should be instrumented as necessary for its purposes.
   *
   * @param contextScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @return the ID of the new context scope created at server side, or null in case of failure.
   */
  public String registerContext(
      ContextScope contextScope, Federation federation) {
    return contextScope instanceof ServiceContextScope serviceSessionScope
        ? serviceSessionScope.getId()
        : null;
  }

  /**
   * Called by ServiceInstance after initializeService was successful
   *
   * @param b
   */
  public void setOperational(boolean b) {
    this.operational = true;
  }

  /**
   * Called by ServiceInstance after initializeService was successful
   *
   * @param b
   */
  public void setInitialized(boolean b) {
    this.initialized = true;
  }

  public boolean isInitialized() {
    return this.initialized;
  }

  /**
   * Calls {@link #ingestResources(ResourceSet, Scope, Class)} ignoring the class and only returning
   * true or false if the resource set has errors.
   *
   * @param resourceSet
   * @param scope
   * @return
   */
  protected boolean ingestResources(ResourceSet resourceSet, Scope scope) {
    if (Utils.Notifications.hasErrors(resourceSet.getNotifications())) {
      return false;
    }
    ingestResources(resourceSet, scope, KlabAsset.class);
    return true;
  }

  /**
   * Can be overridden in each service to take what the service can handle from a ResourceSet. The
   * default ingests all documents into the {@link KnowledgeRepository} and loads any components in
   * results.
   *
   * @param resourceSet
   * @param scope
   * @return
   */
  protected synchronized <T extends KlabAsset> List<T> ingestResources(
      ResourceSet resourceSet, Scope scope, Class<T> resultClass) {
    List<T> ret = new ArrayList<>();
    for (var doc : KnowledgeRepository.INSTANCE.ingest(resourceSet, scope, Knowledge.class)) {
      if (resultClass.isAssignableFrom(doc.getClass())) {
        ret.add((T) doc);
      }
    }

    boolean hasComponents = false;
    for (var result : resourceSet.getResults()) {
      switch (result.getKnowledgeClass()) {
        case COMPONENT -> hasComponents = true;
        default -> {}
      }
    }

    if (hasComponents) {
      getComponentRegistry().loadComponents(resourceSet, scope);
    }

    return ret;
  }

  @Override
  public InputStream exportAsset(
      String urn, ResourceTransport.Schema exportSchema, String mediaType, Scope scope) {
    ServiceCall serviceCall =
        ServiceCallImpl.create(exportSchema.getSchemaId(), "MEDIA_TYPE", mediaType);
    serviceCall.getParameters().putUnnamed(urn);
    serviceCall.getParameters().putUnnamed(this);
    var languageService = ServiceConfiguration.INSTANCE.getService(Language.class);
    return languageService.execute(serviceCall, scope, InputStream.class);
  }

  @Override
  public String importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {

    ServiceCall serviceCall = null;
    if (assetCoordinates.getUrl() != null) {
      serviceCall = ServiceCallImpl.create(schema.getSchemaId());
      serviceCall.getParameters().putUnnamed(assetCoordinates.getUrl());
    } else if (assetCoordinates.getFile() != null) {
      serviceCall = ServiceCallImpl.create(schema.getSchemaId());
      serviceCall.getParameters().putUnnamed(assetCoordinates.getFile());
    } else {
      serviceCall = ServiceCallImpl.create(schema.getSchemaId(), assetCoordinates.getProperties());
    }

    if (suggestedUrn != null && !"X:X:X:X".equals(suggestedUrn)) {
      serviceCall.getParameters().put("URN", suggestedUrn);
    }

    var languageService = ServiceConfiguration.INSTANCE.getService(Language.class);
    return languageService.execute(serviceCall, scope, String.class);
  }
}
