package org.integratedmodelling.klab.services.base;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.utilities.Utils;

import javax.print.DocFlavor;

/**
 * Base class for service implementations. A BaseService implements all the {@link KlabService}
 * functions but does not create the {@link ServiceScope} it runs within, which is supplied from the
 * outside. can be wrapped within a {@link org.integratedmodelling.klab.services.ServiceInstance} to
 * provide a {@link ServiceScope} and become usable.
 */
public abstract class BaseService implements KlabService {

  private final Type type;
  //  protected EmbeddedBroker embeddedBroker;
  private String serviceSecret;
  private URL url;
  protected AtomicBoolean available = new AtomicBoolean(false);
  protected AtomicBoolean atomicOperationMode = new AtomicBoolean(false);
  private final List<Notification> serviceNotifications = new ArrayList<>();
  protected ServiceScope serviceScope;
  protected String serviceName = "Unassigned";
  protected final ServiceStartupOptions startupOptions;
  private ScopeManager _scopeManager;
  private boolean initialized;
  private boolean operational;
  private ComponentRegistry componentRegistry;
  private String instanceKey = Utils.Names.newName();
  private long bootTime = System.currentTimeMillis();
  private AtomicInteger cachedLoadPercentage = new AtomicInteger(-1);
  private static final com.sun.management.OperatingSystemMXBean OS_MX_BEAN;
  private ServiceScope.Locality locality;

  static {
    var mxBean = ManagementFactory.getOperatingSystemMXBean();
    // TODO/FIXME: on modular JDK configurations this import may require an explicit --add-exports
    //  flag
    OS_MX_BEAN =
        mxBean instanceof com.sun.management.OperatingSystemMXBean sunBean ? sunBean : null;
  }

  protected Settings settings;
  protected Settings settingsForSlaveServices;
  private Identity identity;
  private ScheduledExecutorService schedule = Executors.newScheduledThreadPool(1);

  protected BaseService(
      ServiceScope scope, KlabService.Type serviceType, ServiceStartupOptions options) {

    this.type = serviceType;
    settings = SettingsImpl.forService(this, serviceType);

    settingsForSlaveServices = SettingsImpl.forSlaveServices(serviceType, settings);

    settingsForSlaveServices.setIfUnset(Setting.POLLING, true);
    settingsForSlaveServices.setIfUnset(Setting.POLLING_INTERVAL_REMOTE, 15);
    settingsForSlaveServices.setIfUnset(Setting.LOG_EVENTS, true);
    settingsForSlaveServices.setIfUnset(Setting.LAUNCH_PRODUCT, false);

    this.serviceScope = scope;
    this.startupOptions = options;
    try {
      URL serviceHostUrl = (new URI(options.getServiceHostUrl())).toURL();
      if (Utils.URLs.isLocalHost(serviceHostUrl)) {
        this.url =
            (new URI(
                    options.getServiceHostUrl()
                        + ":"
                        + options.getPort()
                        + options.getContextPath()))
                .toURL();
      } else {
        this.url = serviceHostUrl;
      }
    } catch (MalformedURLException | URISyntaxException e) {
      throw new KlabIllegalStateException(e);
    }
    createServiceSecret();
    Klab.INSTANCE.setExecutionContext(
        new Klab.ExecutionContext(type, url) {
          @Override
          public String uptime() {
            return Utils.Time.formatDuration(getBootTime(), System.currentTimeMillis());
          }
        });
  }

  protected void setComponentRegistry() {
    this.componentRegistry = new ComponentRegistry(this, this.startupOptions);
  }

  public ComponentRegistry getComponentRegistry() {
    return componentRegistry;
  }

  protected ServiceStartupOptions getStartupOptions() {
    return startupOptions;
  }

  public Settings settings() {
    return settings;
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
    ret.getAdvisories().addAll(serviceNotifications());
    ret.setConnected(true); // obviously
    ret.setUptimeMs(System.currentTimeMillis() - this.bootTime);
    ret.setLoadPercentage(cachedLoadPercentage.get());
    return ret;
  }

  @Override
  public <T> T info(
      String urn, KlabAsset.KnowledgeClass objectClass, Class<T> infoClass, UserScope scope) {
    Objects.requireNonNull(infoClass, "The requested info class cannot be null");

    if (ServiceCapabilities.class.isAssignableFrom(infoClass) && identifiesThisService(urn)) {
      return infoClass.cast(capabilities(scope));
    }
    if (ServiceStatus.class.isAssignableFrom(infoClass) && identifiesThisService(urn)) {
      return infoClass.cast(status());
    }

    var object =
        commonInformationObjects(objectClass, scope).stream()
            .filter(candidate -> Objects.equals(urn, informationIdentifier(candidate)))
            .findFirst()
            .orElse(null);
    if (object != null && infoClass.isInstance(object)) {
      return infoClass.cast(object);
    }
    if (DomainObject.class.isAssignableFrom(infoClass)) {
      return infoClass.cast(asDomainObject(urn, objectClass, object));
    }
    if (object == null) {
      return null;
    }
    throw new KlabIllegalArgumentException(
        "Cannot project " + objectClass + " " + urn + " as " + infoClass.getCanonicalName());
  }

  @Override
  public <T> List<T> query(
      Parameters<String> query,
      KlabAsset.KnowledgeClass objectClass,
      Class<T> infoClass,
      UserScope scope) {
    var parameters = query == null ? Map.<String, Object>of() : query;
    var unsupported =
        parameters.keySet().stream().filter(key -> !Set.of("urn", "query").contains(key)).toList();
    if (!unsupported.isEmpty()) {
      throw new KlabIllegalArgumentException(
          "Unsupported service query parameters " + unsupported + " for " + objectClass);
    }
    var pattern = Objects.toString(parameters.getOrDefault("urn", parameters.get("query")), ".*");
    if (ServiceCapabilities.class.isAssignableFrom(infoClass)) {
      var capabilities = capabilities(scope);
      return informationIdentifier(capabilities).matches(pattern)
          ? List.of(infoClass.cast(capabilities))
          : List.of();
    }
    if (ServiceStatus.class.isAssignableFrom(infoClass)) {
      var status = status();
      return informationIdentifier(status).matches(pattern)
          ? List.of(infoClass.cast(status))
          : List.of();
    }
    return commonInformationObjects(objectClass, scope).stream()
        .filter(object -> informationIdentifier(object).matches(pattern))
        .map(
            object -> {
              if (infoClass.isInstance(object)) {
                return infoClass.cast(object);
              }
              if (DomainObject.class.isAssignableFrom(infoClass)) {
                return infoClass.cast(
                    asDomainObject(informationIdentifier(object), objectClass, object));
              }
              throw new KlabIllegalArgumentException(
                  "Cannot project " + objectClass + " as " + infoClass.getCanonicalName());
            })
        .toList();
  }

  protected boolean isCommonInformationClass(
      KlabAsset.KnowledgeClass objectClass, Class<?> infoClass) {
    return DomainObject.class.isAssignableFrom(infoClass)
        || AdapterDescriptor.class.isAssignableFrom(infoClass)
        || Extensions.ComponentDescriptor.class.isAssignableFrom(infoClass)
        || Extensions.FunctionDescriptor.class.isAssignableFrom(infoClass)
        || ServiceCapabilities.class.isAssignableFrom(infoClass)
        || ServiceStatus.class.isAssignableFrom(infoClass);
  }

  private List<?> commonInformationObjects(KlabAsset.KnowledgeClass objectClass, UserScope scope) {
    if (getComponentRegistry() == null) {
      return List.of();
    }
    return switch (objectClass) {
      case COMPONENT -> List.copyOf(getComponentRegistry().getComponents(scope));
      case INFORMATION ->
          getComponentRegistry().getComponents(scope).stream()
              .flatMap(component -> component.adapters().stream())
              .distinct()
              .toList();
      case SERVICE_IMPLEMENTATION ->
          getComponentRegistry().getComponents(scope).stream()
              .flatMap(component -> component.services().values().stream())
              .flatMap(Collection::stream)
              .distinct()
              .toList();
      default -> List.of();
    };
  }

  private boolean identifiesThisService(String urn) {
    return urn == null
        || urn.isBlank()
        || Objects.equals(urn, serviceId())
        || Objects.equals(urn, serviceName());
  }

  private String informationIdentifier(Object object) {
    return switch (object) {
      case Extensions.ComponentDescriptor component -> component.id();
      case AdapterDescriptor adapter -> adapter.getName();
      case Extensions.FunctionDescriptor function -> function.serviceInfo.getName();
      case ServiceCapabilities ignored -> serviceId();
      case ServiceStatus ignored -> serviceId();
      default -> Objects.toString(object, "");
    };
  }

  private DomainObject asDomainObject(
      String urn, KlabAsset.KnowledgeClass objectClass, Object object) {
    var ret =
        DomainObject.create(
            DomainObject.TYPE,
            objectClass.name(),
            DomainObject.URN,
            urn,
            DomainObject.NAME,
            urn,
            "serviceId",
            serviceId());
    if (object instanceof Extensions.ComponentDescriptor component) {
      ret.put(DomainObject.VERSION, component.version());
      ret.put(DomainObject.DESCRIPTION, component.description());
    } else if (object instanceof AdapterDescriptor adapter) {
      ret.put(DomainObject.VERSION, adapter.getVersion());
    } else if (object != null) {
      ret.put("value", object.toString());
    }
    return ret;
  }

  /**
   * Samples the current JVM process CPU load and caches it as a 0–1000 integer for the next {@link
   * #status()} call. Intended to be called periodically from an external scheduler (e.g. every 5 s)
   * so that {@code getProcessCpuLoad()} always has a meaningful elapsed-time window to measure
   * against. Returns immediately if the platform does not support the measurement.
   */
  public void sampleLoad() {
    if (OS_MX_BEAN != null) {
      double cpu = OS_MX_BEAN.getProcessCpuLoad();
      if (cpu >= 0) {
        cachedLoadPercentage.set((int) Math.round(cpu * 1000.0));
      }
    }
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

  public String serviceName() {
    return serviceName;
  }

  @Override
  public ServiceScope serviceScope() {
    return serviceScope;
  }

  /**
   * Called when all the essential services are available. The non-essential "operational" services
   * will not necessarily be available yet.
   */
  public abstract boolean initializeService();

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

  protected abstract org.integratedmodelling.klab.services.configuration.ServiceConfiguration
      getServiceConfiguration();

  /**
   * Called by ServiceInstance after initializeService was successful
   *
   * @param b
   */
  public void setOperational(boolean b, Notification... notifications) {
    this.operational = true;
    if (notifications != null) {
      this.serviceNotifications.addAll(Arrays.asList(notifications));
    }
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
   * Calls {@link #ingestResources(ResourceSet, Scope, boolean)} ignoring the class and only
   * returning true or false if the resource set has errors.
   *
   * @param resourceSet
   * @param scope
   * @return
   */
  protected boolean ingestResources(ResourceSet resourceSet, Scope scope, boolean loadComponents) {
    if (Utils.Notifications.hasErrors(resourceSet.getNotifications())) {
      return false;
    }
    ingestResources(resourceSet, scope, KlabAsset.class, loadComponents);
    return true;
  }

  @Override
  public boolean loadResources(ResourceSet resourceSet, Scope scope) {
    return ingestResources(resourceSet, scope, true);
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
      ResourceSet resourceSet, Scope scope, Class<T> resultClass, boolean loadComponents) {

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

    if (hasComponents && loadComponents) {
      getComponentRegistry().loadComponents(resourceSet, scope);
    }

    return ret;
  }

  protected boolean isAllowed(CRUDOperation operation, UserScope scope) {
    if (scope instanceof org.integratedmodelling.klab.services.scopes.ServiceUserScope userScope
        && !userScope.isAuthorized(operation)) {
      return false;
    }
    var rights = getServiceConfiguration().getPermissions().get(operation);
    if (rights == null) {
      return scope instanceof org.integratedmodelling.klab.services.scopes.ServiceUserScope
          || isLocal();
    }
    return rights.checkAuthorization(scope);
  }

  /** Resolve the service-wide CRUD mask established for the authenticated user scope. */
  protected Set<CRUDOperation> permissions(Scope scope) {
    if (scope instanceof org.integratedmodelling.klab.services.scopes.ServiceUserScope userScope) {
      return userScope.getPermissions();
    }
    return EnumSet.of(CRUDOperation.READ);
  }

  @Override
  public InputStream exportAsset(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      String mediaType,
      Parameters<String> parameters,
      Scope scope) {

    // First retrieve the asset, then if the metadata contain an adapter and the adapter is local,
    // use that to prioritize before warning.
    Geometry geometry = null;
    var asset = resolveUrn(urn, knowledgeClass, scope);
    if (asset instanceof Observation observation) {
      geometry = observation.getGeometry().dimensionsOnly();
      var adapterId = observation.getContextualizationData().getAdapterId();
      if (adapterId != null) {
        var adapter = getComponentRegistry().getAdapter(adapterId, Version.ANY_VERSION, scope);
        if (adapter != null) {
          // TODO
        }
      }
    }

    var schemata =
        ResourceTransport.INSTANCE.findExportSchemata(
            knowledgeClass, mediaType, geometry, this, scope);

    if (schemata.isEmpty()) {
      throw new KlabAuthorizationException(
          "No authorized export schema with media type " + mediaType + " is available");
    } else if (schemata.size() > 1) {
      scope.warn(
          "Ambiguous request: more than one export schema with "
              + "media type "
              + mediaType
              + " is available");
    }
    var exportSchema = schemata.getFirst();
    // TODO if the schema is in an adapter, we must either ensure that we have the data (i.e., scope
    // is
    //  a context scope and the DT is local) or call the exported parametrically from a resources
    // service.

    ServiceCall serviceCall =
        ServiceCallImpl.create(exportSchema.getSchemaId(), "MEDIA_TYPE", mediaType);
    serviceCall.getParameters().putUnnamed(urn);
    serviceCall.getParameters().putUnnamed(this);
    var languageService = ServiceConfiguration.INSTANCE.getService(Language.class);
    return languageService.execute(serviceCall, scope, InputStream.class, asset, this, parameters);
  }

  public RuntimeAsset resolveUrn(String urn, KlabAsset.KnowledgeClass knowledgeClass, Scope scope) {
    if (knowledgeClass == KlabAsset.KnowledgeClass.OBSERVATION) {
      if (scope instanceof ServiceContextScope serviceContextScope) {
        var numericId = ObservationImpl.idFromUrn(serviceContextScope.getId(), urn);
        if (numericId.isPresent()) {
          return serviceContextScope.getObservation(numericId.getAsLong());
        }
        var canonicalUrn = ObservationImpl.catalogUrn(serviceContextScope.getId(), urn);
        return serviceContextScope
            .getDigitalTwin()
            .getKnowledgeGraph()
            .getAsset(canonicalUrn, scope, Observation.class);
      }
    }
    return null;
  }

  @Override
  public CompletableFuture<ResourceSet> importAsset(
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

    if (suggestedUrn != null && !Urn.UNDEFINED_URN.equals(suggestedUrn)) {
      serviceCall.getParameters().put("URN", suggestedUrn);
    }

    final var call = serviceCall;
    var languageService = ServiceConfiguration.INSTANCE.getService(Language.class);
    return CompletableFuture.supplyAsync(
        () -> languageService.execute(call, scope, ResourceSet.class));
  }

  public void setRuntimeLockfile(String serviceId) {
    var file = getFileInConfigurationDirectory(startupOptions(), serviceId + ".lock");
    Utils.Files.touch(file);
    file.deleteOnExit();
  }

  //  public void setServiceName(String username) {
  //    this.serviceName = username;
  //  }

  public void setIdentity(Identity identity) {
    this.identity = identity;
    this.serviceName =
        switch (identity) {
          case UserIdentity user -> serviceType().name().toLowerCase() + "." + user.getUsername();
          case PartnerIdentity partner -> partner.getId();
          default -> throw new KlabIllegalStateException("Unknown identity type: " + identity);
        };
  }

  @Override
  public String declareSessionScope(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior) {

    if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

      if (sessionScope.getId() == null) {
        throw new KlabIllegalArgumentException(
            "resolver: session scope has no ID, cannot register " + "a scope autonomously");
      }
      getScopeManager().registerScope(serviceSessionScope);
      return serviceSessionScope.getId();
    }

    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  /**
   * As a default, the service will create a client configuration for the digital twin. In the
   * runtime, this will be overridden to create the actual DT.
   *
   * @param contextScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @param sessionScope used to set up federated behavior
   * @return
   */
  @Override
  public DigitalTwin.Configuration declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope) {

    if (contextScope instanceof ServiceContextScope serviceContextScope) {

      if (contextScope.getId() == null) {
        throw new KlabIllegalArgumentException(
            "resolver: context scope has no ID, cannot register " + "a scope autonomously");
      }

      // Create a client digital twin. This is overridden in the runtime to create the actual DT.
      if (contextScope.getHostServiceId() != null) {
        serviceContextScope.setDigitalTwin(
            new ClientDigitalTwin(contextScope, serviceContextScope.getId()));
      } else {
        serviceScope.warn(
            "Registering context scope without service ID: digital twin will be inoperative");
      }

      getScopeManager().registerScope(serviceContextScope);
      return serviceContextScope.getConfiguration();
    }

    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  public void setMaintenanceMode(boolean maintenanceMode) {
    this.available.set(!maintenanceMode);
  }

  public void setAtomicOperationMode(boolean atomicOperationMode) {
    this.atomicOperationMode.set(atomicOperationMode);
  }

  public ServiceScope.Locality getLocality() {
    return locality;
  }

  public void setLocality(ServiceScope.Locality locality) {
    this.locality = locality;
  }

  /**
   * Called by the service instance to run additional timed tasks. Override as needed. TODO may be a
   * boolean
   */
  public void runAdditionalTimedTasks() {}
}
