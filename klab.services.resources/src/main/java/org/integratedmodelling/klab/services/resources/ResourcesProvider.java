package org.integratedmodelling.klab.services.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.integratedmodelling.common.data.SerializingDataBuilder;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Project.Manifest;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.lang.LanguageDescriptor;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.Instance;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.persistence.ModelKbox;
import org.integratedmodelling.klab.services.resources.persistence.ModelReference;
import org.integratedmodelling.klab.services.resources.storage.ResourceManager;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.stereotype.Service;

@Service
public class ResourcesProvider extends BaseService implements ResourcesService {

  // this is for URNs that represent inline resources from namespace defines
  private static final String TEMPORARY_NODE_NAME = "temporary";
  // this for URNs that identify resources. Leaving space for other URN catalogs in the future.
  private static final String TEMPORARY_CATALOG_NAME = "resources";

  private final String hardwareSignature = Utils.Names.getHardwareId();
  private final WorkspaceManager workspaceManager;
  private final ResourcesKBox resourcesKbox;
  private final ResourceManager resourceManager;
  private AtomicBoolean semanticSearchAvailable = new AtomicBoolean(false);

  /** Caches for concepts and observables. */
  private LoadingCache<String, KimConcept> concepts =
      CacheBuilder.newBuilder()
          .maximumSize(500)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, KimConcept>() {
                public KimConcept load(String key) {
                  return resolveConceptInternal(key);
                }
              });

  /** Caches for concepts and observables. */
  private LoadingCache<String, KimObservable> observables =
      CacheBuilder.newBuilder()
          .maximumSize(500)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, KimObservable>() {
                public KimObservable load(String key) {
                  return resolveObservableInternal(key);
                }
              });

  /**
   * @deprecated use {@link ResourcesKBox}
   */
  private ModelKbox kbox;

  // set to true when the connected reasoner becomes operational
  //  private boolean semanticSearchAvailable = false;
  /*
   * "fair" read/write lock to ensure no reading during updates
   */
  private final ReadWriteLock updateLock = new ReentrantReadWriteLock(true);
  private Thread lspThread;

  @SuppressWarnings("unchecked")
  public ResourcesProvider(ServiceScope scope, ServiceStartupOptions options) {

    super(scope, Type.RESOURCES, options);
    this.resourcesKbox = new ResourcesKBox(scope, options, this);
    this.workspaceManager =
        new WorkspaceManager(
            scope, getStartupOptions(), this, this.resourcesKbox, this::resolveRemoteProject);
    this.resourceManager = new ResourceManager(this.resourcesKbox, this);

    setComponentRegistry();

    ServiceConfiguration.INSTANCE.setMainService(this);

    /*
    Find out any Instance-annotated classes before we read anything
     */
    scanPackages(
        (annotation, annotated) -> {
          if (!LanguageAdapter.INSTANCE.registerInstanceClass(annotation, annotated)) {
            Logging.INSTANCE.error(
                "Configuration error: multiple definitions, cannot redefine instance"
                    + " "
                    + "implementation "
                    + annotation.value());
            serviceNotifications()
                .add(
                    Notification.create(
                        "Configuration error: multiple definitions, "
                            + "cannot redefine instance"
                            + " "
                            + "implementation "
                            + annotation.value(),
                        Notification.Level.Error));
          }
        },
        Instance.class);

    this.kbox = ModelKbox.create(this);

    /*
    initialize the plugin system to handle components
     */
    getComponentRegistry()
        .initializeComponents(
            this.workspaceManager.getConfiguration(),
            getConfigurationSubdirectory(options, "components"));

    // load predefined runtime libraries
    getComponentRegistry()
        .loadExtensions(
            "org.integratedmodelling.klab.runtime.libraries",
            "org.integratedmodelling.klab.services.resources.library");
  }

  public Project resolveRemoteProject(String projectId) {
    // TODO
    System.out.println("TODO resolve external project " + projectId);
    return null;
  }

  @Override
  public boolean initializeService() {
    Logging.INSTANCE.setSystemIdentifier("Resources service: ");
    return true;
  }

  @Override
  public boolean operationalizeService() {
    // do a first check for an appropriate reasoner. Does not prevent operationalization.
    checkSemanticServices(serviceScope());
    return true;
  }

  public synchronized boolean checkSemanticServices(Scope scope) {
    // TODO this should be called repeatedly and should be able to make incremental changes
    if (!this.semanticSearchAvailable.get()) {
      var iAmLocal = this.serviceScope().getIdentity() instanceof UserIdentity;

      /** Only local reasoners can index semantic content of a local resources service. */
      var reasoner =
          scope.getServices(Reasoner.class).stream()
              .filter(
                  r ->
                      !iAmLocal
                          || org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(
                              r.getUrl()))
              .findAny()
              .orElse(null);

      if (reasoner != null && reasoner.status().isOperational()) {
        Logging.INSTANCE.info("Reasoner is available: indexing semantic assets");
        if (indexKnowledge(scope)) {
          this.semanticSearchAvailable.set(true);
        }
        return true;
      } else {
        Logging.INSTANCE.warn("reasoner is inoperative: cannot index semantic content");
        this.semanticSearchAvailable.set(false);
      }
    }
    return this.semanticSearchAvailable.get();
  }

  /**
   * Return whatever worldview is defined in this service, using any other services necessary, or an
   * empty set if none is available.
   *
   * <p>TODO we may support >1 worldviews at this level and pass the worldview name.
   *
   * @return
   */
  public Worldview retrieveWorldview() {
    return this.workspaceManager.getWorldview();
  }

  /**
   * Called after startup and by the update timer at regular intervals. TODO must check if changes
   * were made and reload the affected workspaces if so.
   *
   * <p>Projects with update frequency == 0 do not get updated.
   */
  private void loadResources(File resourceDir, ProjectImpl project, int level, boolean legacy) {

    /*
     * load new and legacy resources. This thing returns null if the dir does not
     * exist.
     */
    File[] files =
        resourceDir.listFiles(
            new FileFilter() {
              @Override
              public boolean accept(File pathname) {
                return pathname.isDirectory() && pathname.canRead();
              }
            });
    if (files != null) {
      for (File subdir : files) {
        Resource resource = null;
        if ("unreviewed".equals(Utils.Files.getFileBaseName(subdir))) {
          loadResources(subdir, project, 0, false);
        } else if ("staging".equals(Utils.Files.getFileBaseName(subdir))) {
          loadResources(subdir, project, 1, false);
        } else {
          // CHUPA CHUPA
          //                    resource = KimAdapter.adaptResource(Utils.Json
          //                            .load(new File(subdir + File.separator + "resource
          //                            .json"),
          //                                    ResourceReference.class));
        }
        if (resource != null) {
          ResourceInfo status = resourcesKbox.getStatus(resource.getUrn(), null);
          if (status == null) {
            status = new ResourceInfo();
            status.setReviewStatus(level);
            status.setFileLocation(subdir);
            status.setUrn(resource.getUrn());
            status.setType(
                Utils.Notifications.hasErrors(resource.getNotifications())
                    ? ResourceInfo.Type.OFFLINE
                    : ResourceInfo.Type.AVAILABLE);
            status.setLegacy(legacy);
            status.setKnowledgeClass(KnowledgeClass.RESOURCE);
            // TODO fill in the rest
            resourcesKbox.putStatus(status);
          }
        }
      }
    }
  }

  private boolean indexKnowledge(Scope scope) {

    // TODO index ontologies
    try {
      for (var namespace : workspaceManager.getNamespaces()) {
        kbox.remove(namespace.getUrn(), scope);
        for (var statement : namespace.getStatements()) {
          if (statement instanceof KimModel model) {
            kbox.store(model, scope);
          }
        }
      }
    } catch (Throwable t) {
      Logging.INSTANCE.error("Error indexing semantic content", t);
      return false;
    }
    return true;
  }

  @Override
  public KimNamespace retrieveNamespace(String urn, Scope scope) {
    return this.workspaceManager.getNamespace(urn);
    // TODO check scope for authorization
  }

  @Override
  public KimOntology retrieveOntology(String urn, Scope scope) {
    return this.workspaceManager.getOntology(urn);
    // TODO check scope for authorization
  }

  @Override
  public KActorsBehavior retrieveBehavior(String urn, Scope scope) {
    return this.workspaceManager.getBehavior(urn);
    // TODO check scope for authorization
  }

  public KimObservationStrategyDocument retrieveObservationStrategyDocument(
      String urn, Scope scope) {
    return this.workspaceManager.getStrategyDocument(urn);
    // TODO check scope for authorization
  }

  @Override
  public Resource retrieveResource(List<String> urns, Scope scope) {
    if (urns.size() > 1) {
      // TODO find or cache a merged resource for these URNs with validation and shit
      throw new KlabUnimplementedException("Multiple URNs in retrieveResource");
    }
    return retrieveResource(urns.getFirst(), scope);
  }

  private Resource retrieveResource(String urnId, Scope scope) {

    var urn = Urn.of(urnId);

    if (urn.isUniversal()) {
      return createUniversalResource(urn, scope);
    } else if (urn.getNodeName().equals(TEMPORARY_NODE_NAME)
        && urn.getCatalog().equals(TEMPORARY_CATALOG_NAME)) {
      return createResourceFromDefinition(urn.getNamespace(), urn.getResourceId());
    }

    return resourcesKbox.getResource(urnId, urn.getVersion());
  }

  private Resource createResourceFromDefinition(String namespace, String resourceId) {
    throw new KlabUnimplementedException("createResourceFromDefinition");
  }

  private Resource createUniversalResource(Urn urn, Scope scope) {
    var adapter = getComponentRegistry().getAdapter(urn.getCatalog(), urn.getVersion(), scope);
    if (adapter == null) {
      return null;
    }
    // TODO see if we need a resource builder within the adapter.
    var ret = new ResourceImpl();
    ret.setUrn(urn.getUrn());
    ret.setAdapterType(urn.getCatalog());
    ret.setVersion(adapter.getVersion());
    ret.setServiceId(serviceId());
    ret.setType(adapter.resourceType(urn));
    // TODO adapter must report the overall geometry, generally or on a URN basis
    ret.setGeometry(Geometry.create("S2"));
    return ret;
  }

  @Override
  public Workspace retrieveWorkspace(String urn, Scope scope) {
    // TODO check permissions in scope, possibly filter the workspace's projects
    return this.workspaceManager.getWorkspace(urn);
  }

  @Override
  public ResourceSet resolveResourceAdapter(String urn, Scope scope) {
    var version = Version.splitVersion(urn);
    var adapter = getComponentRegistry().getAdapter(urn, version.getSecond(), scope);
    if (adapter == null) {
      return ResourceSet.empty(Notification.error("No adapter available for " + urn));
    }
    ResourceSet ret = new ResourceSet();
    ret.getResults()
        .add(
            new ResourceSet.Resource(
                this.serviceId(),
                adapter.getComponentUrn(),
                null,
                adapter.getComponentVersion(),
                KnowledgeClass.COMPONENT,
                adapter.getAdapterInfo().getTimestamp(),
                false));
    return ret;
  }

  @Override
  public ResourceSet resolveImportSchema(String mediaType, Geometry geometry, Scope scope) {
    // TODO
    return null;
  }

  @Override
  public ResourceSet resolveExportSchema(String mediaType, Geometry geometry, Scope scope) {
    ResourceSet ret = new ResourceSet();
    boolean empty = true;
    for (var component : getComponentRegistry().resolveExportSchemata(mediaType, geometry)) {
      if (
      /*component.permissions().checkAuthorization(scope)*/ true /* TODO check permissions */) {
        empty = false;
        ret.getResults()
            .add(
                new ResourceSet.Resource(
                    this.serviceId(),
                    component.id(),
                    null,
                    component.version(),
                    KnowledgeClass.COMPONENT,
                    component.timestamp(),
                    false));
      }
    }

    if (!empty) {
      ret.getServices().put(this.serviceId(), this.getUrl());
    }

    ret.setEmpty(empty);

    return ret;
  }

  @Override
  public ResourceSet resolveServiceCall(String name, Version version, Scope scope) {

    ResourceSet ret = new ResourceSet();
    boolean empty = true;
    for (var component : getComponentRegistry().resolveServiceCall(name, version)) {
      if (
      /*component.permissions().checkAuthorization(scope)*/ true /* TODO check permissions */) {
        empty = false;
        ret.getResults()
            .add(
                new ResourceSet.Resource(
                    this.serviceId(),
                    component.id(),
                    null,
                    component.version(),
                    KnowledgeClass.COMPONENT,
                    component.timestamp(),
                    false));
      }
    }

    if (!empty) {
      ret.getServices().put(this.serviceId(), this.getUrl());
    }

    ret.setEmpty(empty);

    return ret;
  }

  @Override
  public ResourceSet resolveResource(String urn, Scope scope) {
    return resolveResourceUrn(urn, scope);
  }

  @Override
  public Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope) {
    var adapter =
        getComponentRegistry()
            .getAdapter(
                resource.getAdapterType(), /* TODO needs adapter version */
                Version.ANY_VERSION,
                scope);
    if (adapter == null) {
      throw new KlabIllegalStateException(
          "Cannot contextualize resource "
              + resource.getUrn()
              + ": unknown adapter "
              + resource.getAdapterType());
    }
    return adapter.hasContextualizer()
        ? adapter.contextualize(resource, geometry, scope)
        : resource;
  }

  private ResourceSet resolveResourceUrn(String urnId, Scope scope) {

    var urn = Urn.of(urnId);
    ResourceSet ret = new ResourceSet();
    Resource resource = null;
    var adapterId = urn.getCatalog();
    var adapterVersion = urn.getVersion();
    if (!urn.isUniversal()) {
      resource = resourcesKbox.getResource(urnId, urn.getVersion());
      if (resource == null) {
        return ResourceSet.empty(Notification.error("No resource found for URN " + urnId));
      }
      var split = Version.splitVersion(resource.getAdapterType());
      adapterId = split.getFirst();
      adapterVersion = split.getSecond();
    }

    var adapter = getComponentRegistry().getAdapter(adapterId, adapterVersion, scope);
    if (adapter == null) {
      return ResourceSet.empty(
          Notification.error(
              "Adapter " + adapterId + "  is unavailable to this scope for resource " + urnId));
    }

    var info = adapter.getAdapterInfo();
    if (info.getValidatedPhases().contains(ResourceAdapter.Validator.LifecyclePhase.UrnSyntax)) {
      // TODO validate the URN before returning
    }

    if (adapter.isEmbeddable()) {
      // runtime will decide what to do, but we can embed the adapter so we can add an optional
      // dependency on the component that provides it.
      ret.getResults()
          .add(
              new ResourceSet.Resource(
                  this.serviceId(),
                  adapter.getComponentUrn(),
                  null,
                  adapter.getComponentVersion(),
                  KnowledgeClass.COMPONENT,
                  adapter.getAdapterInfo().getTimestamp(),
                  true));
    }

    if (urn.isUniversal()) {
      ret.getResults()
          .add(
              new ResourceSet.Resource(
                  this.serviceId(),
                  urn.getUrn(),
                  null,
                  adapter.getVersion(),
                  KnowledgeClass.RESOURCE,
                  adapter.getAdapterInfo().getTimestamp(),
                  false));

      return ret;
    }

    // TODO figure out what kind of dependencies may be needed by a resource - possibly
    //  other resources, e.g. for codelists

    ret.getResults()
        .add(
            new ResourceSet.Resource(
                this.serviceId(),
                resource.getUrn(),
                null,
                resource.getVersion(),
                KnowledgeClass.RESOURCE,
                adapter.getAdapterInfo().getTimestamp(),
                false));

    return ret;
  }

  @Override
  public CompletableFuture<Data> contextualize(
      Resource resource,
      // FIXME needs to pass a geometry explicitly
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      @Nullable Data input,
      Scope scope) {
    return CompletableFuture.supplyAsync(
        () -> contextualizeSynchronous(resource, observation, geometry, event, input, scope));
  }

  public Data contextualizeSynchronous(
      Resource resource,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      @Nullable Data input,
      Scope scope) {
    var adapter =
        getComponentRegistry().getAdapter(resource.getAdapterType(), resource.getVersion(), scope);

    if (adapter == null) {
      return Data.empty(
          Notification.error("Adapter " + resource.getAdapterType() + " not available"));
    }
    var name =
        observation.getObservable().getStatedName() == null
            ? observation.getObservable().getUrn()
            : observation.getObservable().getStatedName();

    if (!adapter.validate(
        resource, scope, ResourceAdapter.Validator.LifecyclePhase.PreContextualization)) {
      return Data.empty(
          Notification.error(
              "Resource " + resource.getUrn() + " failed remote pre-contextualization validation"));
    }

    var builder = new SerializingDataBuilder(name, input, observation.getGeometry(), null);
    Urn urn = Urn.of(resource.getUrn());

    if (adapter.encode(
        resource,
        geometry,
        event,
        builder,
        null, // FIXME we need an output scanner to pair for qualities
        observation,
        observation.getObservable(),
        urn,
        Parameters.create(urn.getParameters()),
        scope)) {
      return builder.build();
    }
    return Data.empty(Notification.error("Encoding failed"));
  }

  @Override
  public KimObservationStrategyDocument retrieveDataflow(String urn, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<String> dependents(String namespaceId) {
    return workspaceManager.dependents(namespaceId);
  }

  @Override
  public AdapterDescriptor retrieveAdapterInfo(String adapterType, Scope scope) {
    var adapter = Version.splitVersion(adapterType);
    var ad = getComponentRegistry().getAdapter(adapter.getFirst(), adapter.getSecond(), scope);
    return ad == null ? null : ad.getAdapterInfo();
  }

  @Override
  public List<String> precursors(String namespaceId) {
    return null;
  }

  /**
   * TODO improve logics: the main function should return the appropriate ProjectStorage for the URL
   * in all cases. Then call importProject (storage) when all the different storages are
   * implemented.
   *
   * <p>TODO add scope so we can record the owner/importer in the project rights
   *
   * @param workspaceName
   * @param projectUrl can be a file (zip, jar, existing folder, or anything supported by
   *     extensions), a git URL (with a potential branch name after a # sign) or a http URL from
   *     another resource manager. Could also be a service URL for mirroring.
   * @param overwriteIfExisting self-explanatory. If the project is remote, reload if true.
   * @return
   */
  public synchronized List<ResourceSet> importProject(
      String workspaceName, String projectUrl, boolean overwriteIfExisting, UserScope scope) {

    var storage = workspaceManager.importProject(projectUrl, workspaceName);
    if (storage == null) {
      return List.of(
          Utils.Resources.createEmpty(
              Notification.create("Import failed for " + projectUrl, Notification.Level.Error)));
    }

    var project = workspaceManager.loadProject(storage, workspaceName);

    // initial resource permissions
    var status = new ResourceInfo();
    if (scope.getIdentity() instanceof UserIdentity user) {
      status.getRights().getAllowedUsers().add(user.getUsername());
      status.setOwner(user.getUsername());
    }
    status.setFileLocation(storage instanceof FileProjectStorage fps ? fps.getRootFolder() : null);
    status.setKnowledgeClass(KnowledgeClass.PROJECT);
    status.setReviewStatus(0);
    status.setType(ResourceInfo.Type.AVAILABLE);
    status.setLegacy(false);
    status.setServiceId(serviceId());
    status.setUrn(project.getUrn());
    resourcesKbox.putStatus(status);
    //    db.commit();

    return collectProject(project, CRUDOperation.CREATE, workspaceName, scope);
  }

  @Override
  public boolean createWorkspace(String workspace, Metadata metadata, UserScope scope) {
    /*
     * We just create the descriptor. Project URNs are unique anyway, so the workspace is a purely
     * logical entity for now.
     */
    var existing = resourcesKbox.getStatus(workspace, null);
    if (existing != null) {
      return false;
    }

    var rights = ResourcePrivileges.create(scope);

    if (rights.invalid()) {
      return false;
    }

    ResourceInfo resourceInfo = new ResourceInfo();
    resourceInfo.setType(ResourceInfo.Type.AVAILABLE);
    resourceInfo.setRights(rights);
    resourceInfo.setKnowledgeClass(KnowledgeClass.WORKSPACE);
    resourceInfo.setUrn(workspace);
    resourceInfo.getMetadata().putAll(metadata);
    resourcesKbox.putStatus(resourceInfo);

    workspaceManager.notifyNewWorkspace(resourceInfo);

    return true;
  }

  @Override
  public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {

    var workspaceInfo = resourcesKbox.getStatus(workspaceName, null);

    if (workspaceInfo == null) {
      if (!createWorkspace(workspaceName, Metadata.create(), scope)) {
        return ResourceSet.empty(
            Notification.error(
                "Cannot create workspace "
                    + workspaceName
                    + " when attempting to create project "
                    + projectName
                    + " in it"));
      }
    }

    var projectInfo = resourcesKbox.getStatus(projectName, null);
    if (projectInfo != null) {
      return ResourceSet.empty(
          Notification.error(
              "Cannot create project "
                  + projectName
                  + " as asset of type "
                  + projectInfo.getKnowledgeClass()
                  + " already exists"));
    }

    var rights = ResourcePrivileges.create(scope);
    if (rights.invalid()) {
      return ResourceSet.empty(
          Notification.error(
              "Cannot create project " + projectName + ": requesting scope is not authorized"));
    }

    var ret = workspaceManager.createProject(projectName, workspaceName, scope);

    if (ret != null) {

      ResourceInfo resourceInfo = new ResourceInfo();
      resourceInfo.setType(ResourceInfo.Type.AVAILABLE);
      resourceInfo.setRights(rights);
      resourceInfo.setKnowledgeClass(KnowledgeClass.PROJECT);
      resourceInfo.setUrn(projectName);
      resourceInfo.setServiceId(serviceId());
      resourceInfo.setOwner(scope.getUser().getUsername());
      resourceInfo.setServiceId(serviceId());
      resourceInfo
          .getMetadata()
          .putAll(
              Metadata.create(
                  Metadata.DC_DATE_CREATED,
                  TimeInstant.create().toRFC3339String(),
                  "klab:serviceId",
                  serviceId(),
                  Metadata.DC_CONTRIBUTOR,
                  scope.getUser().getUsername()));

      resourcesKbox.putStatus(resourceInfo);

    } else {
      return ResourceSet.empty(Notification.error("Cannot create project " + projectName));
    }

    return ret;
  }

  @Override
  public ResourceSet updateProject(
      String projectName, Manifest manifest, Metadata metadata, UserScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> createDocument(
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType,
      UserScope scope) {
    return this.workspaceManager.createDocument(projectName, documentType, documentUrn, scope);
  }

  @Override
  public List<ResourceSet> updateDocument(
      String projectName,
      ProjectStorage.ResourceType documentType,
      String content,
      UserScope scope) {
    var ret = this.workspaceManager.updateDocument(projectName, documentType, content, scope);
    invalidateCaches();
    return ret;
  }

  private void invalidateCaches() {
    concepts.invalidateAll();
    observables.invalidateAll();
  }

  @Override
  public List<ResourceSet> deleteProject(String projectName, UserScope scope) {

    //    updateLock.writeLock().lock();
    var workspaceName = workspaceManager.getWorkspaceForProject(projectName);
    if (workspaceName != null && workspaceManager.removeProject(projectName)) {
      invalidateCaches();
      // a project deletion can have deep consequences, so the client should rebuild the
      // entire workspace
      ResourceSet resourceSet = new ResourceSet();
      resourceSet
          .getNotifications()
          .add(
              Notification.info(
                  "Project "
                      + projectName
                      + " was deleted, invalidating caches and rebuilding workspace"));

      resourceSet.setWorkspace(workspaceName);

      var change = new ResourceSet.Resource();
      change.setOperation(CRUDOperation.DELETE);
      change.setResourceUrn(projectName);
      change.setKnowledgeClass(KnowledgeClass.PROJECT);
      change.setServiceId(serviceId());

      resourceSet.getProjects().add(change);

      return List.of(resourceSet);
    }
    //    updateLock.writeLock().unlock();
    return List.of(
        ResourceSet.empty(Notification.info("Project " + projectName + " was not deleted")));
  }

  @Override
  public List<ResourceSet> deleteWorkspace(String workspaceName, UserScope scope) {
    Workspace workspace = workspaceManager.getWorkspace(workspaceName);
    for (Project project : workspace.getProjects()) {
      deleteProject(project.getUrn(), scope);
    }
    invalidateCaches();
    //        try {
    //            updateLock.writeLock().lock();
    ////            this.localWorkspaces.remove(workspaceName);
    //        } finally {
    //            updateLock.writeLock().unlock();
    //        }\
    return null;
  }

  @Override
  public Collection<Workspace> listWorkspaces() {
    return this.workspaceManager.getWorkspaces();
  }

  @Override
  public boolean shutdown() {
    return shutdown(30);
  }

  public boolean shutdown(int secondsToWait) {

    //    serviceScope()
    //        .send(
    //            Message.MessageClass.ServiceLifecycle,
    //            Message.MessageType.ServiceUnavailable,
    //            capabilities(serviceScope()));
    //
    if (this.lspThread != null) {
      this.lspThread.interrupt();
    }

    // try {
    // projectLoader.awaitTermination(secondsToWait, TimeUnit.SECONDS);
    return super.shutdown();
    // } catch (InterruptedException e) {
    // Logging.INSTANCE.error("Error during thread termination", e);
    // }
    // return false;
  }

  @Override
  public Capabilities capabilities(Scope scope) {

    var ret = new ResourcesCapabilitiesImpl();
    ret.setWorldviewProvider(workspaceManager.isWorldviewProvider());
    ret.setAdoptedWorldview(workspaceManager.getAdoptedWorldview());
    ret.setWorkspaceNames(workspaceManager.getWorkspaceURNs());
    ret.setServiceName(serviceName);
    ret.setType(Type.RESOURCES);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("RESOURCES_" + hardwareSignature));
    ret.setServiceId(workspaceManager.getConfiguration().getServiceId());
    ret.getServiceNotifications().addAll(serviceNotifications());
    ret.getComponents().addAll(getComponentRegistry().getComponents(scope));
    // TODO capabilities are being asked from same machine as the one that runs the server. This
    // call
    //  should have a @Nullable scope. The condition here is silly.
    ret.getPermissions().add(CRUDOperation.CREATE);
    ret.getPermissions().add(CRUDOperation.DELETE);
    ret.getPermissions().add(CRUDOperation.UPDATE);
    ret.setSemanticSearchCapable(semanticSearchAvailable.get());
    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());

    return ret;
  }

  @Override
  public <T extends KlabAsset> T retrieve(String urn, Class<T> assetClass, UserScope scope) {
    if (Project.class.isAssignableFrom(assetClass)) {
      return (T) retrieveProject(urn, scope);
    } else if (Workspace.class.isAssignableFrom(assetClass)) {
      return (T) retrieveWorkspace(urn, scope);
    } else if (Resource.class.isAssignableFrom(assetClass)) {
      return (T) retrieveResource(List.of(urn), scope);
    } else if (KimObservable.class.isAssignableFrom(assetClass)) {
      return (T) resolveObservableInternal(urn);
    } else if (KimConcept.class.isAssignableFrom(assetClass)) {
      return (T) resolveConceptInternal(urn);
    } /*else if (AdapterDescriptor.class.isAssignableFrom(assetClass)) {
        return (T) retrieveAdapterInfo(urn);
      }*/ else if (KActorsBehavior.class.isAssignableFrom(assetClass)) {
      return (T) retrieveBehavior(urn, scope);
    } else if (KActorsBehavior.class.isAssignableFrom(assetClass)) {
      return (T) retrieveBehavior(urn, scope);
    } else if (KimModel.class.isAssignableFrom(assetClass)) {
      var namespace = retrieveNamespace(Utils.Paths.getLeading(urn, '.'), scope);
      return namespace == null
          ? null
          : (T)
              namespace.getStatements().stream()
                  .filter(s -> s instanceof KimModel && s.getUrn().equals(urn))
                  .map(s -> (KimModel) s)
                  .findFirst()
                  .orElse(null);
    } else if (KimNamespace.class.isAssignableFrom(assetClass)) {
      return (T) retrieveNamespace(urn, scope);
    } else if (KimOntology.class.isAssignableFrom(assetClass)) {
      return (T) retrieveOntology(urn, scope);
    } else if (KimObservationStrategyDocument.class.isAssignableFrom(assetClass)) {
      return (T) retrieveObservationStrategyDocument(urn, scope);
    } else if (KimSymbolDefinition.class.isAssignableFrom(assetClass)) {
      var namespace = retrieveNamespace(Utils.Paths.getLeading(urn, '.'), scope);
      return namespace == null
          ? null
          : (T)
              namespace.getStatements().stream()
                  .filter(s -> s instanceof KimSymbolDefinition && s.getUrn().equals(urn))
                  .map(s -> (KimModel) s)
                  .findFirst()
                  .orElse(null);
    } else if (Worldview.class.isAssignableFrom(assetClass)) {
      var ret = retrieveWorldview();
      if (ret.getUrn().equals(urn)) {
        return (T) ret;
      }
    }
    // TODO continue
    throw new KlabIllegalStateException(
        "Cannot retrieve " + assetClass.getSimpleName() + " " + urn);
  }

  @Override
  public <T extends KlabAsset> List<T> list(Class<T> assetClass, UserScope scope) {
    return List.of();
  }

  @Override
  public List<ResourceSet> delete(String urn, KnowledgeClass knowledgeClass, UserScope scope) {
    switch (knowledgeClass) {
      case PROJECT:
        return deleteProject(urn, scope);
      case WORKSPACE:
        return deleteWorkspace(urn, scope);
      case NAMESPACE, BEHAVIOR, APPLICATION, SCRIPT, OBSERVATION_STRATEGY_DOCUMENT, ONTOLOGY:
        String[] urns = urn.split("/");
        if (urns.length < 2) {
          throw new KlabIllegalArgumentException(
              "Invalid URN " + urn + " for deletion: must contain at least the project name");
        }
        return deleteDocument(
            urns[urns.length - 2], urns[urns.length - 1], knowledgeClass.getResourceType(), scope);
      case COMPONENT:
        getComponentRegistry().unloadComponent(urn, Urn.of(urn).getVersion());
        // TODO delete from registry!
        // TODO RESOURCE
    }

    return List.of(ResourceSet.empty(Notification.error("Cannot delete " + urn)));
  }

  // TODO see logic in the aspecific resolve() and revise API to use this after classification of
  // the urn
  @Override
  public ResourceSet resolve(String urn, KnowledgeClass assetClass, UserScope scope) {

    ResourceSet.Resource desiredResource = null;

    var ret =
        switch (assetClass) {
          case NAMESPACE -> {
            var namespace = workspaceManager.getNamespace(urn);
            if (namespace != null) {
              desiredResource =
                  new ResourceSet.Resource(
                      serviceId(),
                      namespace.getUrn(),
                      namespace.getProjectName(),
                      namespace.getVersion(),
                      KnowledgeClass.NAMESPACE,
                      namespace.getLastUpdateTimestamp(),
                      false);
            }
            yield desiredResource == null ? null : ResourceSet.of(desiredResource);
          }
          case ONTOLOGY -> {
            var ontology = workspaceManager.getOntology(urn);
            if (ontology != null) {
              desiredResource =
                  new ResourceSet.Resource(
                      serviceId(),
                      ontology.getUrn(),
                      ontology.getProjectName(),
                      ontology.getVersion(),
                      KnowledgeClass.ONTOLOGY,
                      ontology.getLastUpdateTimestamp(),
                      false);
            }
            yield desiredResource == null ? null : ResourceSet.of(desiredResource);
          }
          case BEHAVIOR, TESTCASE, APPLICATION, SCRIPT -> {
            var behavior = workspaceManager.getBehavior(urn);
            if (behavior != null) {
              desiredResource =
                  new ResourceSet.Resource(
                      serviceId(),
                      behavior.getUrn(),
                      behavior.getProjectName(),
                      behavior.getVersion(),
                      assetClass,
                      behavior.getLastUpdateTimestamp(),
                      false);
            }
            yield desiredResource == null ? null : ResourceSet.of(desiredResource);
          }
          case OBSERVATION_STRATEGY_DOCUMENT -> null;
          case COMPONENT -> null;
          case MODEL -> resolveModel(urn, scope);
          case RESOURCE -> resolveResourceUrn(urn, scope);
          case WORKSPACE -> null;
          case PROJECT -> null;
          case OBSERVATION_STRATEGY -> null;
          case CONCEPT_STATEMENT -> null;
          case WORLDVIEW -> resolveWorldview(urn, scope);
          default -> null;
        };

    if (ret != null) {
      return addDependencies(ret, scope);
    }

    return ResourceSet.empty(
        Notification.error("Cannot resolve " + assetClass.name().toLowerCase() + " " + urn));
  }

  /**
   * Get all the worldview resources for the named worldview available to the scope. These will be
   * all the ontology and observation strategy documents participating to the worldview, managed by
   * this service and allowed for the scope, plus any metadata from the correspondent projects.
   *
   * @param scope
   * @return
   */
  private ResourceSet resolveWorldview(String urn, Scope scope) {
    if (workspaceManager.isWorldviewProvider()) {
      return workspaceManager.resolveWorldview(urn, scope);
    }
    return ResourceSet.empty(Notification.error("Worldview resolution not implemented"));
  }

  @Override
  public <T extends KlabAsset> List<ResourceSet> submit(
      T asset, SubmissionMode submissionMode, UserScope scope) {
    switch (asset) {
      case Resource resource:
        return List.of(ingestResource(resource, scope));
      //      case Workspace workspace:
      //        return List.of(ingestProject(project, scope));
      //      case Project project:
      //        return List.of(ingestProject(project, scope));
      //      case KlabDocument<?> document:
      //        return List.of(ingestDocument(document, scope));
      default:
        return List.of(ResourceSet.empty(Notification.error("Cannot submit " + asset)));
    }
    //    return List.of();
  }

  @Override
  public <T> T info(String urn, KnowledgeClass assetClass, Class<T> infoClass, UserScope scope) {

    if (infoClass.isAssignableFrom(KlabAsset.class)
        && KnowledgeClass.classify((Class<? extends KlabAsset>) infoClass) == assetClass) {
      return (T) retrieve(urn, (Class<? extends KlabAsset>) infoClass, scope);
    } else if (assetClass == KnowledgeClass.INFORMATION) {
      if (infoClass.isAssignableFrom(LanguageDescriptor.class)) {
        return (T) workspaceManager.getLanguageDescriptor();
      } else if (infoClass.isAssignableFrom(AdapterDescriptor.class)) {
        // TODO
        var adapter = getComponentRegistry().getAdapter(urn, Version.ANY_VERSION, scope);
        if (adapter != null) {
          return (T) retrieveAdapterInfo(urn, scope);
        }
      } // TODO more info objects
    }
    throw new KlabIllegalArgumentException("Cannot retrieve info for " + assetClass + " " + urn);
  }

  @Override
  public String serviceId() {
    return workspaceManager.getConfiguration().getServiceId();
  }

  @Override
  public KimConcept.Descriptor describeConcept(String conceptUrn) {
    return workspaceManager.describeConcept(conceptUrn);
  }

  @Override
  public KimConcept declareConcept(String definition) {
    try {
      return concepts.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      serviceScope().warn("invalid concept definition: " + definition);
    }
    return null;
  }

  @Override
  public KimObservable declareObservable(String definition) {
    try {
      return observables.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      serviceScope().warn("invalid observable definition: " + definition);
    }
    return null;
  }

  public KimConcept resolveConceptInternal(String definition) {
    var parsed = this.workspaceManager.resolveConcept(definition);
    if (parsed != null) {
      boolean errors = false;
      for (var notification : parsed.getNotifications()) {
        if (notification.message().level() == LanguageValidationScope.Level.ERROR) {
          errors = true;
          serviceScope().error(notification.message().message());
        } else if (notification.message().level() == LanguageValidationScope.Level.WARNING) {
          serviceScope().error(notification.message().message());
        }
      }
      return errors ? null : LanguageAdapter.INSTANCE.adaptSemantics(parsed, null, null, null);
    }
    return null;
  }

  public KimObservable resolveObservableInternal(String definition) {
    var parsed = this.workspaceManager.resolveObservable(removeExcessParentheses(definition));
    if (parsed != null) {
      boolean errors = false;
      for (var notification : parsed.getNotifications()) {
        if (notification.message().level() == LanguageValidationScope.Level.ERROR) {
          errors = true;
          serviceScope().error(notification.message().message());
        } else if (notification.message().level() == LanguageValidationScope.Level.WARNING) {
          serviceScope().error(notification.message().message());
        }
      }
      return errors ? null : LanguageAdapter.INSTANCE.adaptObservable(parsed, null, null, null);
    }
    return null;
  }

  private String removeExcessParentheses(String definition) {
    definition = definition.trim();
    while (definition.startsWith("(") && definition.endsWith(")")) {
      definition = definition.substring(1, definition.length() - 1);
    }
    return definition;
  }

  @Override
  public List<ResourceSet> resolveProjects(Collection<String> projects, Scope scope) {

    ResourceSet ret = new ResourceSet();

    // TODO
    //        for (String projectName : this.configuration.getProjectConfiguration().keySet()) {
    //            if (projects.contains(projectName)) {
    ////                if (!localProjects.containsKey(projectName)) {
    ////                    importProject(projectName, this.configuration.getProjectConfiguration()
    // .get
    // (projectName));
    ////                }
    //                ret = Utils.Resources.merge(ret, collectProject(projectName, scope));
    //            }
    //        }

    return List.of(); // sort(ret, scope);
  }

  private ResourceSet sort(ResourceSet ret, Scope scope) {

    Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    for (ResourceSet.Resource ns : ret.getNamespaces()) {

      // TODO use a recursive function to capture n-th level deps that aren't resolved
      // directly, although this doesn't apply if we have the whole workspace

      graph.addVertex(ns.getResourceUrn());
      KimNamespace namespace = retrieveNamespace(ns.getResourceUrn(), scope);
      if (namespace == null) {
        ret.setEmpty(true);
        return ret;
      }
      for (String imp : namespace.getImports().keySet()) {
        KimNamespace imported = retrieveNamespace(imp, scope);
        if (imported == null) {
          ret.setEmpty(true);
          return ret;
        }
        graph.addVertex(imported.getUrn());
        if (imported.getUrn().equals(namespace.getUrn())) {
          System.out.println("DIO ZAPPA");
        }
        graph.addEdge(imported.getUrn(), namespace.getUrn());
      }
    }

    TopologicalOrderIterator<String, DefaultEdge> order = new TopologicalOrderIterator<>(graph);
    Map<String, ResourceSet.Resource> toSort = new HashMap<>();
    ret.getNamespaces().forEach((ns) -> toSort.put(ns.getResourceUrn(), ns));
    ret.getNamespaces().clear();
    while (order.hasNext()) {
      ret.getNamespaces().add(toSort.get(order.next()));
    }

    return ret;
  }

  /**
   * Collect all known project data, fulfilling any missing external dependencies but not sorting
   * the results by dependency as this could be one step in a multiple-project setup. If external
   * dependencies are needed and unsatisfied, return an empty resourceset.
   *
   * @param project
   * @param scope
   * @return
   */
  private List<ResourceSet> collectProject(
      Project project, CRUDOperation operation, String workspace, Scope scope) {

    List<ResourceSet> ret = new ArrayList<>();

    List<KimOntology> ontologies =
        this.workspaceManager.getOntologies(false).stream()
            .filter(o -> project.getUrn().equals(o.getProjectName()))
            .toList();
    List<KimNamespace> namespaces =
        this.workspaceManager.getNamespaces().stream()
            .filter(o -> project.getUrn().equals(o.getProjectName()))
            .toList();
    List<KimObservationStrategyDocument> strategies =
        this.workspaceManager.getStrategyDocuments().stream()
            .filter(o -> project.getUrn().equals(o.getProjectName()))
            .toList();
    List<KActorsBehavior> behaviors =
        this.workspaceManager.getBehaviors().stream()
            .filter(o -> project.getUrn().equals(o.getProjectName()))
            .toList();

    // Resources work independently and do not come with the project data.

    // check if the worldview is impacted, too
    var worldviewOntologies =
        retrieveWorldview().getOntologies().stream()
            .map(KlabAsset::getUrn)
            .collect(Collectors.toSet());
    var worldviewStrategies =
        retrieveWorldview().getObservationStrategies().stream()
            .map(KlabAsset::getUrn)
            .collect(Collectors.toSet());

    var conts =
        Sets.intersection(
            worldviewOntologies,
            ontologies.stream().map(KlabAsset::getUrn).collect(Collectors.toSet()));
    var cstra =
        Sets.intersection(
            worldviewStrategies,
            strategies.stream().map(KlabAsset::getUrn).collect(Collectors.toSet()));

    if (!conts.isEmpty() || !cstra.isEmpty()) {
      ret.add(
          Utils.Resources.create(
              this,
              Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER,
              operation,
              Utils.Collections.shallowCollection(
                      ontologies.stream().filter(o -> conts.contains(o.getUrn())).toList(),
                      strategies.stream().filter(o -> conts.contains(o.getUrn())).toList())
                  .toArray(new KlabAsset[0])));
    }

    ret.add(
        Utils.Resources.create(
            this,
            workspace,
            operation,
            Utils.Collections.shallowCollection(ontologies, strategies, namespaces, behaviors)
                .toArray(new KlabAsset[0])));

    var timestamp = project.computeTimestamp();

    /*
    Add project info as result to everything that has changed
     */
    ret.forEach(
        r ->
            r.getResults()
                .add(
                    new ResourceSet.Resource(
                        serviceId(),
                        project.getUrn(),
                        project.getUrn(),
                        project.getManifest().getVersion(),
                        KnowledgeClass.PROJECT,
                        timestamp,
                        false)));

    return ret;
  }

  @Override
  public ResourceSet resolveModel(String modelName, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String... arguments) {
    return workspaceManager.manageRepository(projectName, operation, arguments);
  }

  @Override
  public ResourceInfo registerResource(
      String urn,
      KnowledgeClass knowledgeClass,
      File fileLocation,
      ResourcePrivileges rights,
      Scope submittingScope) {

    if (urn != null) {
      // initial resource permissions
      var status = new ResourceInfo();
      status.setRights(rights);
      status.setFileLocation(fileLocation);
      status.setKnowledgeClass(knowledgeClass);
      status.setReviewStatus(0);
      status.setType(ResourceInfo.Type.AVAILABLE);
      status.setLegacy(false);
      status.setUrn(urn);
      status.setServiceId(serviceId());
      resourcesKbox.putStatus(status);
      return status;
    }

    return ResourceInfo.offline();
  }

  @Override
  public List<ResourceSet> deleteDocument(
      String projectName,
      String assetUrn,
      ProjectStorage.ResourceType resourceType,
      UserScope scope) {
    return this.workspaceManager.deleteDocument(projectName, resourceType, assetUrn, scope);
  }

  @Override
  public CompletableFuture<Resource> publishObservation(
      Observation observation, ContextScope scope) {
    // TODO
    return null;
  }

  @Override
  public ResourceSet resolveModels(Observable observable, ContextScope scope) {

    if (!checkSemanticServices(scope)) {
      return ResourceSet.empty(Notification.warning("Semantic search is not available"));
    }

    ResourceSet results = new ResourceSet();
    // FIXME use the observation's scale (pass the observation)
    for (ModelReference model : this.kbox.query(observable, scope)) {
      results
          .getResults()
          .add(
              new ResourceSet.Resource(
                  getUrl().toString(),
                  model.getName(),
                  model.getProjectUrn(),
                  model.getVersion(),
                  KnowledgeClass.MODEL,
                  System.currentTimeMillis(),
                  false));
    }

    addDependencies(results, scope);

    return results;
  }

  /**
   * The workspace manager calls the kbox directly
   *
   * @return
   */
  public ModelKbox modelKbox() {
    return this.kbox;
  }

  /**
   * Add a collection of namespaces to a result set, including their dependencies and listing the
   * correspondent resources in dependency order. If any namespace isn't available, return false;
   *
   * <p>TODO/FIXME involve other services from the scope if a namespace is not available locally.
   *
   * @param namespaces
   * @param results
   */
  private boolean addNamespacesToResultSet(
      Set<String> namespaces, ResourceSet results, Scope scope) {

    DefaultDirectedGraph<String, DefaultEdge> nss = new DefaultDirectedGraph<>(DefaultEdge.class);
    Map<String, ResourceSet.Resource> storage = new HashMap<>();
    for (String ns : namespaces) {
      if (!addNamespaceToResultSet(ns, nss, storage, scope)) {
        return false;
      }
    }

    TopologicalOrderIterator<String, DefaultEdge> order = new TopologicalOrderIterator<>(nss);
    while (order.hasNext()) {
      results.getNamespaces().add(storage.get(order.next()));
    }

    return true;
  }

  private boolean addNamespaceToResultSet(
      String ns,
      DefaultDirectedGraph<String, DefaultEdge> nss,
      Map<String, ResourceSet.Resource> storage,
      Scope scope) {

    if (nss.containsVertex(ns)) {
      return true;
    }

    KimNamespace namespace = retrieveNamespace(ns, scope);
    if (namespace == null) {
      // TODO use services in scope
      return false;
    }

    nss.addVertex(ns);

    var dependency = namespace.getImports();
    for (String dependent : dependency.keySet()) {
      if (!nss.containsVertex(dependent)) {
        addNamespaceToResultSet(dependent, nss, storage, scope);
      }
      nss.addEdge(dependent, ns);
    }

    var resource = new ResourceSet.Resource();
    resource.setKnowledgeClass(KnowledgeClass.NAMESPACE);
    resource.setResourceUrn(ns);
    resource.setResourceVersion(namespace.getVersion());
    resource.setServiceId(serviceId());
    resource.setLocal(isLocal());
    storage.put(ns, resource);

    return true;
  }

  @Override
  public List<ResourceInfo> queryResources(
      String queryPattern, Scope scope, KnowledgeClass... resourceTypes) {

    List<ResourceInfo> ret = new ArrayList<>();
    Set<KnowledgeClass> wanted = EnumSet.noneOf(KnowledgeClass.class);
    if (resourceTypes != null && resourceTypes.length > 0) {
      wanted.addAll(Arrays.asList(resourceTypes));
    } else {
      // we want them all
      wanted.addAll(Arrays.asList(KnowledgeClass.values()));
    }

    if (wanted.contains(KnowledgeClass.RESOURCE)) {
      ret.addAll(getResourcesKbox().queryResources(queryPattern));
    }

    if (wanted.contains(KnowledgeClass.MODEL)) {}

    if (wanted.contains(KnowledgeClass.SCRIPT)) {}

    if (wanted.contains(KnowledgeClass.APPLICATION)) {}

    if (wanted.contains(KnowledgeClass.BEHAVIOR)) {}

    if (wanted.contains(KnowledgeClass.COMPONENT)) {}

    if (wanted.contains(KnowledgeClass.NAMESPACE)) {}

    if (wanted.contains(KnowledgeClass.PROJECT)) {}

    return ret;
  }

  @Override
  public ResourceInfo resourceInfo(String urn, Scope scope) {

    ResourceInfo ret = resourcesKbox.getStatus(urn, null);
    if (ret == null) {
      ret = ResourceInfo.offline(urn);
    }
    ret.setServiceId(serviceId());
    if (ret.getType().isUsable()) {
      if (!ret.getRights().checkAuthorization(scope)) {
        ret.setType(ResourceInfo.Type.UNAUTHORIZED);
      }
    }
    return ret;
  }

  @Override
  public boolean setResourceInfo(String urn, ResourceInfo info, Scope scope) {
    // TODO check access permissions etc
    return resourcesKbox.putStatus(info);
  }

  @Override
  public Project retrieveProject(String projectName, Scope scope) {
    // TODO check scope
    return workspaceManager.getProject(projectName);
  }

  @Override
  public Coverage modelGeometry(String modelUrn) throws KlabIllegalArgumentException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public KActorsBehavior readBehavior(URL url, UserScope scope) {
    var content = Utils.URLs.readUrlContents(url);
    return workspaceManager.readBehavior(content);
  }

  @Override
  public Collection<Project> listProjects(Scope scope) {
    // FIXME filter by scope access
    return workspaceManager.getProjects();
  }

  @Override
  public Collection<String> listResourceUrns(Scope scope) {
    return resourcesKbox.listResourcesUrns();
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {

    var status = resourcesKbox.getStatus(resourceUrn, null);
    if (status != null) {
      return status.getRights().asSeenByScope(scope);
    }
    return ResourcePrivileges.empty();
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    var status = resourcesKbox.getStatus(resourceUrn, null);
    if (status != null) {
      status.setRights(resourcePrivileges);
      return resourcesKbox.putStatus(status);
    }
    return false;
  }

  @Override
  protected org.integratedmodelling.klab.services.configuration.ServiceConfiguration
      getServiceConfiguration() {
    return this.workspaceManager.getConfiguration();
  }

  @Override
  public boolean lockProject(String urn, UserScope scope) {
    String token = scope.getIdentity().getId();
    boolean local =
        scope instanceof ServiceScope
            || (scope instanceof ServiceUserScope userScope && userScope.isLocal());
    return workspaceManager.lockProject(urn, token, local);
  }

  @Override
  public boolean unlockProject(String urn, UserScope scope) {
    String token = scope.getIdentity().getId();
    return workspaceManager.unlockProject(urn, token);
  }

  @Override
  public ResourceSet resolve(String urn, Scope scope) {

    ResourceSet ret = new ResourceSet();

    switch (Urn.classify(urn)) {
      case RESOURCE -> {}
      case KIM_OBJECT -> {

        /** TODO may be a project or even a workspace */
        KimNamespace namespace = retrieveNamespace(urn, scope);
        if (namespace != null) {

          ret.getResults()
              .add(
                  new ResourceSet.Resource(
                      getUrl().toString(),
                      urn,
                      namespace.getProjectName(),
                      namespace.getVersion(),
                      KnowledgeClass.NAMESPACE,
                      namespace.getLastUpdateTimestamp(),
                      false));

        } else {

          /*
           * extract namespace and check for that.
           */
          String ns = Utils.Paths.getLeading(urn, '.');
          String nm = Utils.Paths.getLast(urn, '.');
          namespace = retrieveNamespace(ns, scope);
          /*
           * TODO check permissions!
           */
          if (namespace != null) {
            for (KlabStatement statement : namespace.getStatements()) {
              if (urn.equals(statement.getUrn())) {
                ret.getResults()
                    .add(
                        new ResourceSet.Resource(
                            serviceId(),
                            urn,
                            namespace.getProjectName(),
                            namespace.getVersion(),
                            KlabAsset.classify(statement),
                            namespace.getLastUpdateTimestamp(),
                            false));
                break;
              }
            }
          }
        }
      }
      case OBSERVABLE -> {
        var observable = declareObservable(urn);
        if (observable != null) {
          ret.getResults()
              .add(
                  new ResourceSet.Resource(
                      serviceId(),
                      urn,
                      null,
                      null,
                      KnowledgeClass.OBSERVABLE,
                      System.currentTimeMillis(),
                      false));
        }
      }
      case REMOTE_URL -> {
        // TODO
      }
      case UNKNOWN -> {
        ret.setEmpty(true);
        ret.getNotifications()
            .add(Notification.error("Resource service cannot resolve URN " + urn));
      }
    }

    return addDependencies(ret, scope);
  }

  /*
   * TODO add dependencies to resource set containing only local resources,
   * including merging any remote resources in view of the passed scope. SET TO
   * EMPTY if dependencies cannot be resolved in this scope.
   */
  private ResourceSet addDependencies(ResourceSet resourceSet, Scope scope) {

    if (resourceSet.getResults().isEmpty()) {
      resourceSet.setEmpty(true);
      return resourceSet;
    }

    Set<String> namespaces = new HashSet<>();
    for (ResourceSet.Resource result : resourceSet.getResults()) {
      if (Urn.classify(result.getResourceUrn()) == Urn.Type.KIM_OBJECT) {
        if (result.getKnowledgeClass() == KnowledgeClass.NAMESPACE) {
          namespaces.add(result.getResourceUrn());
        } else if (result.getKnowledgeClass() == KnowledgeClass.MODEL
            || result.getKnowledgeClass() == KnowledgeClass.DEFINITION) {
          namespaces.add(Utils.Paths.getLeading(result.getResourceUrn(), '.'));
        }
      }
    }

    addNamespacesToResultSet(namespaces, resourceSet, scope);

    /*
     * add components and action libraries to behaviors
     *
     * add loaded namespaces and the deps (projects, components) of all projects
     * that are required by their projects. Function calls may reference local
     * resources.
     *
     * Resources may be using other resources
     */

    return resourceSet;
  }

  public ResourcesKBox getResourcesKbox() {
    return this.resourcesKbox;
  }

  @Override
  public Future<ResourceSet> importResource(Resource resource, UserScope scope) {
    return CompletableFuture.supplyAsync(() -> ingestResource(resource, scope));
  }

  /**
   * The synchronous job started by {@link #importResource(Resource, UserScope)}. This may be
   * long-running.
   *
   * @param resource
   * @param scope
   * @return
   */
  public ResourceSet ingestResource(Resource resource, UserScope scope) {

    var operation = CRUDOperation.CREATE;

    var existingSame = resourcesKbox.getResource(resource.getUrn(), resource.getVersion());
    if (existingSame != null) {
      return ResourceSet.empty(
          Notification.error(
              "Resource already exists in version " + resource.getVersion() + " or higher"));
    }
    var existingPrev = resourcesKbox.getResource(resource.getUrn(), Version.ANY_VERSION);
    if (existingPrev != null) {
      operation = CRUDOperation.UPDATE;
    }

    // establish rights
    if (!isAllowed(operation, scope)) {
      return ResourceSet.empty(
          Notification.error(
              "User "
                  + scope.getUser().getUsername()
                  + " is not authorized to "
                  + operation.name().toLowerCase()
                  + " resources"));
    }
    // check if we're updating and, if so, whether we have the right to modify

    // find adapter
    var adapterType = Version.splitVersion(resource.getAdapterType());
    var adapter =
        getComponentRegistry().getAdapter(adapterType.getFirst(), adapterType.getSecond(), scope);
    if (adapter == null) {

      // TODO this must use the remaining services
      var adapterResult = resolveResourceAdapter(resource.getAdapterType(), scope);
      if (adapterResult.isEmpty()) {
        // resolve using the remaining services in the scope
        var otherServices =
            scope.getServices(ResourcesService.class).stream()
                .filter(s -> !serviceId().equals(s.serviceId()))
                .toList();
        if (!otherServices.isEmpty()) {
          // Utils.Resources.queryResources(us)
        }
      }

      if (adapterResult.isEmpty()) {
        return ResourceSet.empty(
            Notification.error(
                "Cannot find or load adapter "
                    + resource.getAdapterType()
                    + " to handle resource "
                    + resource.getUrn()));
      }

      ingestResources(adapterResult, scope, true);
      adapter =
          getComponentRegistry().getAdapter(adapterType.getFirst(), adapterType.getSecond(), scope);

      if (adapter == null) {
        return ResourceSet.empty(
            Notification.error(
                "Cannot find or load adapter "
                    + resource.getAdapterType()
                    + " to handle resource "
                    + resource.getUrn()));
      }
    }

    return resourceManager.ingestResource(resource, adapter, scope);
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    // TODO
    return null;
  }

  /**
   * Used by controllers
   *
   * @return
   */
  public WorkspaceManager getWorkspaceManager() {
    return workspaceManager;
  }
}
