package org.integratedmodelling.klab.services.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.knowledge.ProjectImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
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
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Project.Manifest;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Instance;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.persistence.ModelKbox;
import org.integratedmodelling.klab.services.resources.persistence.ModelReference;
import org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.serializer.GroupSerializer;
import org.springframework.stereotype.Service;

@Service
public class ResourcesProvider extends BaseService
    implements ResourcesService, ResourcesService.Admin {

  private final String hardwareSignature = Utils.Names.getHardwareId();
  private final WorkspaceManager workspaceManager;
  private final ResourcesKBox resourcesKbox;

  /**
   * We keep a hash of all the resource URNs we serve for quick reference and search
   *
   * @deprecated use {@link
   *     org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox}
   */
  private Set<String> localResources = new HashSet<>();

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

  //  /**
  //   * the only persistent info in this implementation is the catalog of resource status info.
  // This is
  //   * used for individual resources and whole projects. It also holds and maintains the review
  //   * status, which in the case of projects propagates to the namespaces and models. Reviews and
  // the
  //   * rest of the editorial material should be part of the provenance info associated to the
  // items.
  //   * The review process is organized and maintained in the community service; only its
  // initiation
  //   * and the storage of the review status is the job of the resources service.
  //   *
  //   * @deprecated use {@link
  //   *     org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox}
  //   */
  //  private DB db = null;
  //
  //  private ConcurrentNavigableMap<String, ResourceInfo> catalog = null;

  /**
   * @deprecated use {@link
   *     org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox}
   */
  private ModelKbox kbox;

  // set to true when the connected reasoner becomes operational
  private boolean semanticSearchAvailable = false;
  /*
   * "fair" read/write lock to ensure no reading during updates
   */
  private final ReadWriteLock updateLock = new ReentrantReadWriteLock(true);
  private Thread lspThread;

  @SuppressWarnings("unchecked")
  public ResourcesProvider(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {

    super(scope, Type.RESOURCES, options);

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
    this.resourcesKbox = new ResourcesKBox(scope, options, this);
    this.workspaceManager =
        new WorkspaceManager(scope, getStartupOptions(), this, this::resolveRemoteProject);

    //    // FIXME remove along with MapDB and catalog, use Nitrite instead
    //    this.db =
    //        DBMaker.fileDB(
    //                getConfigurationSubdirectory(options, "catalog") + File.separator +
    // "resources.db")
    //            .transactionEnable()
    //            .closeOnJvmShutdown()
    //            .make();
    //    this.catalog =
    //        db.treeMap("resourcesCatalog", GroupSerializer.STRING,
    // GroupSerializer.JAVA).createOrOpen();

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
  public void initializeService() {

    Logging.INSTANCE.setSystemIdentifier("Resources service: ");

    serviceScope()
        .send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceInitializing,
            capabilities(serviceScope()).toString());

    //        this.workspaceManager.loadWorkspace();
    /*
     * TODO launch update service
     */

    /**
     * Setup an embedded broker, possibly to be shared with other services, if we're local and there
     * is no configured broker.
     */
    if (Utils.URLs.isLocalHost(this.getUrl())
        && workspaceManager.getConfiguration().getBrokerURI() == null) {
      this.embeddedBroker = new EmbeddedBroker();
    }

    /*
     * If we want the local resources service to provide LSP functionalities, uncomment this. For now it is
     * in the Java-based modeler, but if the modeler moves to a web version this can be added.
     */
    //    if (Utils.URLs.isLocalHost(this.getUrl())) {
    //      /*
    //       *  org.eclipse.xtext.ide.server.ServerLauncher to start the LSP server for all
    // languages on the
    //       *  classpath.
    //       */
    //      Logging.INSTANCE.info("Starting language services for k.LAB language editors");
    //      this.lspThread =
    //          new Thread(
    //              () -> {
    //                try {
    //                  ServerLauncher.main(new String[0]);
    //                } catch (Throwable t) {
    //                  Logging.INSTANCE.error(
    //                      "Error launching LSP server: language services not available", t);
    //                }
    //              });
    //
    //      this.lspThread.start();
    //    }

    serviceScope()
        .send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceAvailable,
            capabilities(serviceScope()));
  }

  @Override
  public boolean operationalizeService() {
    var reasoner = serviceScope().getService(Reasoner.class);
    if (reasoner.status().isOperational()) {
      Logging.INSTANCE.info("Reasoner is available: indexing semantic assets");
      indexKnowledge();
      this.semanticSearchAvailable = true;
    } else {
      Logging.INSTANCE.warn("reasoner is inoperative: cannot index semantic content");
      this.semanticSearchAvailable = false;
    }
    return true;
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
          localResources.add(resource.getUrn());
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

  private void indexKnowledge() {

    // TODO index ontologies

    for (var namespace : workspaceManager.getNamespaces()) {
      kbox.remove(namespace.getUrn(), scope);
      for (var statement : namespace.getStatements()) {
        if (statement instanceof KimModel model) {
          kbox.store(model, scope);
        }
      }
    }
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
    }

    // TODO use kbox
    return null;
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
                    KnowledgeClass.COMPONENT));
      }
    }

    if (!empty) {
      ret.getServices().put(this.serviceId(), this.getUrl());
    }

    ret.setEmpty(empty);

    return ret;
  }

  @Override
  public ResourceSet resolveResource(List<String> urnIds, Scope scope) {

    if (urnIds.size() == 1) {
      return resolveResourceUrn(urnIds.getFirst(), scope);
    }

    return ResourceSet.empty(Notification.error("UNIMPLEMENTED"));
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
    if (urn.isUniversal()) {

      var adapter = getComponentRegistry().getAdapter(urn.getCatalog(), Version.ANY_VERSION, scope);
      if (adapter == null) {
        return ResourceSet.empty(
            Notification.error("No adapter available for " + urn.getCatalog()));
      }

      var info = adapter.getAdapterInfo();
      if (info.validatedPhases().contains(ResourceAdapter.Validator.LifecyclePhase.UrnSyntax)) {
        // TODO validate the URN before returning
      }

      ret.getResults()
          .add(
              new ResourceSet.Resource(
                  this.serviceId(),
                  urn.getUrn(),
                  null,
                  adapter.getVersion(),
                  KnowledgeClass.RESOURCE));

      return ret;

    } else if (urn.isLocal()) {

      // must have project and be same user. Staging area is accessible.

    } else {

      // use the resource
    }

    return ResourceSet.empty(Notification.error("UNIMPLEMENTED"));
  }

  @Override
  public Data contextualize(
      Resource resource,
      Observation observation,
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
    var builder = Data.builder(name, observation.getObservable(), observation.getGeometry());
    Urn urn = Urn.of(resource.getUrn());
    if (!adapter.encode(
        resource,
        observation.getGeometry(),
        event,
        builder,
        observation,
        observation.getObservable(),
        urn,
        Parameters.create(urn.getParameters()),
        input,
        scope)) {
      return Data.empty(Notification.error("Resource encoding failed"));
    }
    return builder.build();
  }

  @Override
  public KimObservationStrategyDocument retrieveDataflow(String urn, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<KimNamespace> dependents(String namespaceId) {
    return null;
  }

  @Override
  public List<KimNamespace> precursors(String namespaceId) {
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
   * @deprecated use project import schema + register resource
   */
  //    @Override
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
    status.setUrn(project.getUrn());
    resourcesKbox.putStatus(status);
    //    db.commit();

    return collectProject(project.getUrn(), CRUDOperation.CREATE, workspaceName, scope);
  }

  @Override
  public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
    return null;
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

    updateLock.writeLock().lock();
    //
    //        try {
    //            // remove namespaces, behaviors and resources
    //            var project = localProjects.get(projectName);
    //            if (project != null) {
    //                for (var namespace : project.getNamespaces()) {
    //                    this.localNamespaces.remove(namespace.getUrn());
    //                }
    //                for (var ontology : project.getOntologies()) {
    //                    this.servedOntologies.remove(ontology.getUrn());
    //                }
    //                for (KActorsBehavior behavior : project.getBehaviors()) {
    //                    this.localBehaviors.remove(behavior.getUrn());
    //                }
    //                for (String resource : project.getResourceUrns()) {
    //                    localResources.remove(resource);
    //                    catalog.remove(resource);
    //                }
    //                this.localProjects.remove(projectName);
    //            }
    workspaceManager.removeProject(projectName);
    invalidateCaches();
    //    db.commit();

    //        }/* finally {*/
    updateLock.writeLock().unlock();
    /*}*/

    return null;
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

  @Override
  public boolean scopesAreReactive() {
    return false;
  }

  public boolean shutdown(int secondsToWait) {

    serviceScope()
        .send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceUnavailable,
            capabilities(serviceScope()));

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
    ret.setType(Type.RESOURCES);
    ret.setUrl(getUrl());
    ret.setServiceName("Resources");
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
    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());
    ret.setBrokerURI(
        embeddedBroker != null
            ? embeddedBroker.getURI()
            : workspaceManager.getConfiguration().getBrokerURI());
    ret.setAvailableMessagingQueues(
        Utils.URLs.isLocalHost(getUrl())
            ? EnumSet.of(
                Message.Queue.Info,
                Message.Queue.Errors,
                Message.Queue.Warnings,
                Message.Queue.Events)
            : EnumSet.noneOf(Message.Queue.class));

    return ret;
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
  public KimConcept retrieveConcept(String definition) {
    try {
      return concepts.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      scope.warn("invalid concept definition: " + definition);
    }
    return null;
  }

  @Override
  public KimObservable retrieveObservable(String definition) {
    try {
      return observables.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      scope.warn("invalid observable definition: " + definition);
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
          scope.error(notification.message().message());
        } else if (notification.message().level() == LanguageValidationScope.Level.WARNING) {
          scope.error(notification.message().message());
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
          scope.error(notification.message().message());
        } else if (notification.message().level() == LanguageValidationScope.Level.WARNING) {
          scope.error(notification.message().message());
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
   * @param projectName
   * @param scope
   * @return
   */
  private List<ResourceSet> collectProject(
      String projectName, CRUDOperation operation, String workspace, Scope scope) {

    List<ResourceSet> ret = new ArrayList<>();

    List<KimOntology> ontologies =
        this.workspaceManager.getOntologies(false).stream()
            .filter(o -> projectName.equals(o.getProjectName()))
            .toList();
    List<KimNamespace> namespaces =
        this.workspaceManager.getNamespaces().stream()
            .filter(o -> projectName.equals(o.getProjectName()))
            .toList();
    List<KimObservationStrategyDocument> strategies =
        this.workspaceManager.getStrategyDocuments().stream()
            .filter(o -> projectName.equals(o.getProjectName()))
            .toList();
    List<KActorsBehavior> behaviors =
        this.workspaceManager.getBehaviors().stream()
            .filter(o -> projectName.equals(o.getProjectName()))
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

  //    @Override
  //    public ResourceSet createResource(Resource resource, UserScope scope) {
  //        // TODO Auto-generated method stub
  //        return null;
  //    }
  //
  //    @Override
  //    public ResourceSet createResource(Dataflow<Observation> dataflow, UserScope scope) {
  //        return null;
  //    }

  //    @Override
  @Deprecated // remove when the import mechanism can do this
  public ResourceSet createResource(File resourcePath, UserScope scope) {

    KnowledgeClass knowledgeClass = null;
    File sourceFile = null;
    String urn = null;
    ResourceSet ret = null;

    if ("jar".equals(Utils.Files.getFileExtension(resourcePath))) {
      var imported = getComponentRegistry().installComponent(resourcePath, null, scope);
      knowledgeClass = KnowledgeClass.COMPONENT;
      sourceFile = imported.getFirst().sourceArchive();
      urn = imported.getFirst().id();
      ret = imported.getSecond();
    } else {
      // TODO resource, mirror archive
    }

    if (urn != null) {
      // initial resource permissions
      var status = new ResourceInfo();
      if (scope.getIdentity() instanceof UserIdentity user) {
        status.getRights().getAllowedUsers().add(user.getUsername());
        status.setOwner(user.getUsername());
      }
      status.setFileLocation(sourceFile);
      status.setKnowledgeClass(knowledgeClass);
      status.setReviewStatus(0);
      status.setType(ResourceInfo.Type.AVAILABLE);
      status.setLegacy(false);
      status.setUrn(urn);
      resourcesKbox.putStatus(status);
      //      db.commit();
    }

    return ret;
  }

  //    @Override
  //    public Resource createResource(String projectName, String urnId, String adapter,
  //                                   Parameters<String> resourceData, UserScope scope) {
  //        return null;
  //    }

  @Override
  public ResourceInfo registerResource(
      String urn, KnowledgeClass knowledgeClass, File fileLocation, Scope submittingScope) {

    if (urn != null) {
      // initial resource permissions
      var status = new ResourceInfo();
      if (scope.getIdentity() instanceof UserIdentity user) {
        status.getRights().getAllowedUsers().add(user.getUsername());
        status.setOwner(user.getUsername());
      }
      status.setFileLocation(fileLocation);
      status.setKnowledgeClass(knowledgeClass);
      status.setReviewStatus(0);
      status.setType(ResourceInfo.Type.AVAILABLE);
      status.setLegacy(false);
      status.setUrn(urn);
      resourcesKbox.putStatus(status);
      //      db.commit();
      return status;
    }

    return ResourceInfo.offline();
  }

  @Override
  public List<ResourceSet> deleteDocument(String projectName, String assetUrn, UserScope scope) {
    return null;
  }

  //    public void setLocalName(String localName) {
  //        this.localName = localName;
  //    }

  @Override
  public ResourceSet resolveModels(Observable observable, ContextScope scope) {

    if (!semanticSearchAvailable) {
      Logging.INSTANCE.warn(
          "Semantic search is not available: client should not make this request");
      return ResourceSet.empty();
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
                  KnowledgeClass.MODEL));
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
    storage.put(ns, resource);

    return true;
  }

  @Override
  public List<String> queryResources(String urnPattern, KnowledgeClass... resourceTypes) {

    List<String> ret = new ArrayList<>();
    Set<KnowledgeClass> wanted = EnumSet.noneOf(KnowledgeClass.class);
    if (resourceTypes != null && resourceTypes.length > 0) {
      wanted.addAll(Arrays.asList(resourceTypes));
    } else {
      // we want them all
      wanted.addAll(Arrays.asList(KnowledgeClass.values()));
    }

    if (wanted.contains(KnowledgeClass.RESOURCE)) {}

    if (wanted.contains(KnowledgeClass.MODEL)) {}

    if (wanted.contains(KnowledgeClass.SCRIPT)) {}

    if (wanted.contains(KnowledgeClass.APPLICATION)) {}

    if (wanted.contains(KnowledgeClass.BEHAVIOR)) {}

    if (wanted.contains(KnowledgeClass.COMPONENT)) {}

    if (wanted.contains(KnowledgeClass.NAMESPACE)) {}

    if (wanted.contains(KnowledgeClass.PROJECT)) {}

    //        if (wanted.contains(KnowledgeClass.INSTANCE)) {
    //
    //        }

    return ret;
  }

  @Override
  public ResourceInfo resourceInfo(String urn, Scope scope) {
    ResourceInfo ret = resourcesKbox.getStatus(urn, null);
    if (ret != null && (ret.getType().isUsable())) {
      /*
       * TODO check the resource status at this time and in this scope
       */
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
  public KActorsBehavior readBehavior(URL url) {
    return null;
    //        return KActorsAdapter.INSTANCE.readBehavior(url);
  }

  @Override
  public Collection<Project> listProjects(Scope scope) {
    // FIXME filter by scope access
    return workspaceManager.getProjects();
  }

  @Override
  public Collection<String> listResourceUrns(Scope scope) {
    return localResources;
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
  public URL lockProject(String urn, UserScope scope) {
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
                      KnowledgeClass.NAMESPACE));

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
                            KlabAsset.classify(statement)));
                break;
              }
            }
          }
        }
      }
      case OBSERVABLE -> {
        var observable = retrieveObservable(urn);
        if (observable != null) {
          ret.getResults()
              .add(
                  new ResourceSet.Resource(
                      serviceId(), urn, null, null, KnowledgeClass.OBSERVABLE));
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

  /**
   * Replicate a remote scope in the scope manager. This should be called by the runtime service
   * after creating it so if the scope has no ID we issue an error, as we do not create independent
   * scopes.
   *
   * @param sessionScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @return
   */
  @Override
  public String registerSession(SessionScope sessionScope) {

    if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

      if (sessionScope.getId() == null) {
        throw new KlabIllegalArgumentException(
            "resolver: session scope has no ID, cannot register " + "a scope autonomously");
      }

      getScopeManager()
          .registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());
      return serviceSessionScope.getId();
    }

    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  /**
   * Replicate a remote scope in the scope manager. This should be called by the runtime service
   * after creating it so if the scope has no ID we issue an error, as we do not create independent
   * scopes.
   *
   * @param contextScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @return
   */
  @Override
  public String registerContext(ContextScope contextScope) {

    if (contextScope instanceof ServiceContextScope serviceContextScope) {

      if (contextScope.getId() == null) {
        throw new KlabIllegalArgumentException(
            "resolver: context scope has no ID, cannot register " + "a scope autonomously");
      }

      /*
       * The resolver needs a digital twin client installed to find existing observations through the
       * service-level context scope.
       */
      if (contextScope.getHostServiceId() != null) {
        serviceContextScope.setDigitalTwin(
            new ClientDigitalTwin(contextScope, serviceContextScope.getId()));
      } else {
        scope.warn(
            "Registering context scope without service ID: digital twin will be inoperative");
      }

      getScopeManager()
          .registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());
      return serviceContextScope.getId();
    }

    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  public ResourcesKBox getResourcesKbox() {
    return this.resourcesKbox;
  }
}
