package org.integratedmodelling.klab.services.resources;

import com.google.common.collect.Sets;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.knowledge.ProjectImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Project.Manifest;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
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

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class ResourcesProvider extends BaseService implements ResourcesService, ResourcesService.Admin {


    private String hardwareSignature = Utils.Names.getHardwareId();

    private WorkspaceManager workspaceManager;
    /**
     * We keep a hash of all the resource URNs we serve for quick reference and search
     */
    private Set<String> localResources = new HashSet<>();

    /**
     * record the time of last update of each project
     */
    private Map<String, Long> lastUpdate = new HashMap<>();

    /**
     * the only persistent info in this implementation is the catalog of resource status info. This is used
     * for individual resources and whole projects. It also holds and maintains the review status, which in
     * the case of projects propagates to the namespaces and models. Reviews and the rest of the editorial
     * material should be part of the provenance info associated to the items. The review process is organized
     * and maintained in the community service; only its initiation and the storage of the review status is
     * the job of the resources service.
     */
    private DB db = null;
    private ConcurrentNavigableMap<String, ResourceStatus> catalog = null;
    private ModelKbox kbox;
    // set to true when the connected reasoner becomes operational
    private boolean semanticSearchAvailable = false;

    /*
     * "fair" read/write lock to ensure no reading during updates
     */
    private ReadWriteLock updateLock = new ReentrantReadWriteLock(true);

    @SuppressWarnings("unchecked")
    public ResourcesProvider(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {

        super(scope, Type.RESOURCES, options);

        ServiceConfiguration.INSTANCE.setMainService(this);

        /*
        Find out any Instance-annotated classes before we read anything
         */
        scanPackages((annotation, annotated) -> {
            if (!LanguageAdapter.INSTANCE.registerInstanceClass(annotation, annotated)) {
                Logging.INSTANCE.error("Configuration error: multiple definitions, cannot redefine instance" +
                        " implementation " + annotation.value());
                serviceNotifications().add(Notification.create("Configuration error: multiple definitions, " +
                        "cannot redefine instance" +
                        " implementation " + annotation.value(), Notification.Level.Error));
            }
        }, Instance.class);

        this.kbox = ModelKbox.create(this);
        this.workspaceManager = new WorkspaceManager(scope, getStartupOptions(), this,
                this::resolveRemoteProject);

        this.db = DBMaker.fileDB(getConfigurationSubdirectory(options, "catalog") + File.separator +
                "resources.db").transactionEnable().closeOnJvmShutdown().make();
        this.catalog =
                db.treeMap("resourcesCatalog", GroupSerializer.STRING, GroupSerializer.JAVA).createOrOpen();
    }

    public Project resolveRemoteProject(String projectId) {
        // TODO
        System.out.println("TODO resolve external project " + projectId);
        return null;
    }

    @Override
    public void initializeService() {

        Logging.INSTANCE.setSystemIdentifier("Resources service: ");

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing,
                capabilities(serviceScope()).toString());


        //        this.workspaceManager.loadWorkspace();
        /*
         * TODO launch update service
         */

        /**
         * Setup an embedded broker, possibly to be shared with other services, if we're local and there
         * is no configured broker.
         */
        if (Utils.URLs.isLocalHost(this.getUrl()) && workspaceManager.getConfiguration().getBrokerURI() == null) {
            this.embeddedBroker = new EmbeddedBroker();
        }

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable,
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
     * Return whatever worldview is defined in this service, using any other services necessary, or an empty
     * set if none is available.
     * <p>
     * TODO we may support >1 worldviews at this level and pass the worldview name.
     *
     * @return
     */
    public Worldview getWorldview() {
        return this.workspaceManager.getWorldview();
    }

    /**
     * Called after startup and by the update timer at regular intervals. TODO must check if changes were made
     * and reload the affected workspaces if so.
     * <p>
     * Projects with update frequency == 0 do not get updated.
     */
    private void loadResources(File resourceDir, ProjectImpl project, int level, boolean legacy) {

        /*
         * load new and legacy resources. This thing returns null if the dir does not
         * exist.
         */
        File[] files = resourceDir.listFiles(new FileFilter() {
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
                    ResourceStatus status = catalog.get(resource.getUrn());
                    if (status == null) {
                        status = new ResourceStatus();
                        status.setReviewStatus(level);
                        status.setFileLocation(subdir);
                        status.setType(Utils.Notifications.hasErrors(resource.getNotifications()) ?
                                       ResourceStatus.Type.OFFLINE : ResourceStatus.Type.AVAILABLE);
                        status.setLegacy(legacy);
                        status.setKnowledgeClass(KnowledgeClass.RESOURCE);
                        // TODO fill in the rest
                        catalog.put(resource.getUrn(), status);
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
    public KimNamespace resolveNamespace(String urn, Scope scope) {
        return this.workspaceManager.getNamespace(urn);
        // TODO check scope for authorization
    }

    @Override
    public KimOntology resolveOntology(String urn, Scope scope) {
        return this.workspaceManager.getOntology(urn);
        // TODO check scope for authorization
    }

    @Override
    public KActorsBehavior resolveBehavior(String urn, Scope scope) {
        return this.workspaceManager.getBehavior(urn);
        // TODO check scope for authorization
    }

    public KimObservationStrategyDocument resolveObservationStrategyDocument(String urn, Scope scope) {
        return this.workspaceManager.getStrategyDocument(urn);
        // TODO check scope for authorization
    }

    @Override
    public Resource resolveResource(String urn, Scope scope) {
        if (localResources.contains(Urn.removeParameters(urn))) {
            // TODO
        }
        return null;
    }

    @Override
    public Workspace resolveWorkspace(String urn, Scope scope) {
        // TODO check permissions in scope, possibly filter the workspace's projects
        return this.workspaceManager.getWorkspace(urn);
    }

    @Override
    public ResourceSet resolveServiceCall(String name, Scope scope) {
        // TODO
        var ret = new ResourceSet();
        ret.setEmpty(true);
        return ret;
    }

    @Override
    public Resource contextualizeResource(Resource originalResource, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KlabData contextualize(Resource contextualizedResource, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KdlDataflow resolveDataflow(String urn, Scope scope) {
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
     * TODO improve logics: the main function should return the appropriate ProjectStorage for the URL in
     * all cases. Then call importProject (storage) when all the different storages are implemented.
     * <p>
     * TODO add scope so we can record the owner/importer in the project rights
     *
     * @param workspaceName
     * @param projectUrl          can be a file (zip, jar, existing folder, or anything supported by
     *                            extensions), a git URL (with a potential branch name after a # sign) or a
     *                            http URL from another resource manager. Could also be a service URL for
     *                            mirroring.
     * @param overwriteIfExisting self-explanatory. If the project is remote, reload if true.
     * @return
     */
    @Override
    public synchronized List<ResourceSet> importProject(String workspaceName, String projectUrl,
                                                        boolean overwriteIfExisting, UserScope scope) {

        var storage = workspaceManager.importProject(projectUrl, workspaceName);
        if (storage == null) {
            return List.of(Utils.Resources.createEmpty(Notification.create("Import failed for " + projectUrl,
                    Notification.Level.Error)));
        }

        var project = workspaceManager.loadProject(storage, workspaceName);

        // initial resource permissions
        var status = new ResourceStatus();
        if (scope.getIdentity() instanceof UserIdentity user) {
            status.getPrivileges().getAllowedUsers().add(user.getUsername());
            status.setOwner(user.getUsername());
        }
        status.setFileLocation(storage instanceof FileProjectStorage fps ? fps.getRootFolder() : null);
        status.setKnowledgeClass(KnowledgeClass.PROJECT);
        status.setReviewStatus(0);
        status.setType(ResourceStatus.Type.AVAILABLE);
        status.setLegacy(false);
        catalog.put(project.getUrn(), status);
        db.commit();

        return collectProject(project.getUrn(), CRUDOperation.CREATE, workspaceName, scope);
    }

    @Override
    public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
        return null;
    }

    @Override
    public ResourceSet updateProject(String projectName, Manifest manifest, Metadata metadata,
                                     UserScope scope) {
        return null;
    }


    @Override
    public List<ResourceSet> createDocument(String projectName, String documentUrn,
                                            ProjectStorage.ResourceType documentType,
                                            UserScope scope) {
        return this.workspaceManager.createDocument(projectName, documentType, documentUrn,
                scope);
    }

    @Override
    public List<ResourceSet> updateDocument(String projectName, ProjectStorage.ResourceType documentType,
                                            String content, UserScope scope) {
        return this.workspaceManager.updateDocument(projectName, documentType, content, scope);
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
        db.commit();

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

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities(serviceScope()));

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
        ret.setServiceName("Resources");
        ret.setServerId(hardwareSignature == null ? null : ("RESOURCES_" + hardwareSignature));
        ret.setServiceId(workspaceManager.getConfiguration().getServiceId());
        ret.getServiceNotifications().addAll(serviceNotifications());
        // TODO capabilities are being asked from same machine as the one that runs the server. This call
        //  should have a @Nullable scope. The condition here is silly.
        ret.getPermissions().add(CRUDOperation.CREATE);
        ret.getPermissions().add(CRUDOperation.DELETE);
        ret.getPermissions().add(CRUDOperation.UPDATE);

        ret.setBrokerURI(embeddedBroker != null ? embeddedBroker.getURI() :
                         workspaceManager.getConfiguration().getBrokerURI());
        ret.setAvailableMessagingQueues(Utils.URLs.isLocalHost(getUrl()) ?
                                        EnumSet.of(Message.Queue.Info, Message.Queue.Errors,
                                                Message.Queue.Warnings, Message.Queue.Events) :
                                        EnumSet.noneOf(Message.Queue.class));

        return ret;
    }

    @Override
    public String serviceId() {
        return workspaceManager.getConfiguration().getServiceId();
    }

    @Override
    public KimObservable resolveObservable(String definition) {
        var parsed = this.workspaceManager.resolveObservable(definition);
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

    @Override
    public KimConcept.Descriptor describeConcept(String conceptUrn) {
        return workspaceManager.describeConcept(conceptUrn);
    }

    @Override
    public KimConcept resolveConcept(String definition) {
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

    @Override
    public List<ResourceSet> projects(Collection<String> projects, Scope scope) {

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
            KimNamespace namespace = resolveNamespace(ns.getResourceUrn(), scope);
            if (namespace == null) {
                ret.setEmpty(true);
                return ret;
            }
            for (String imp : namespace.getImports().keySet()) {
                KimNamespace imported = resolveNamespace(imp, scope);
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
     * Collect all known project data, fulfilling any missing external dependencies but not sorting the
     * results by dependency as this could be one step in a multiple-project setup. If external dependencies
     * are needed and unsatisfied, return an empty resourceset.
     *
     * @param projectName
     * @param scope
     * @return
     */
    private List<ResourceSet> collectProject(String projectName, CRUDOperation operation, String
            workspace,
                                             Scope scope) {

        List<ResourceSet> ret = new ArrayList<>();

        List<KimOntology> ontologies =
                this.workspaceManager.getOntologies(false).stream().filter(o -> projectName.equals(o.getProjectName())).toList();
        List<KimNamespace> namespaces =
                this.workspaceManager.getNamespaces().stream().filter(o -> projectName.equals(o.getProjectName())).toList();
        List<KimObservationStrategyDocument> strategies =
                this.workspaceManager.getStrategyDocuments().stream().filter(o -> projectName.equals(o.getProjectName())).toList();
        List<KActorsBehavior> behaviors =
                this.workspaceManager.getBehaviors().stream().filter(o -> projectName.equals(o.getProjectName())).toList();

        // Resources work independently and do not come with the project data.

        // check if the worldview is impacted, too
        var worldviewOntologies =
                getWorldview().getOntologies().stream().map(KlabAsset::getUrn).collect(Collectors.toSet());
        var worldviewStrategies =
                getWorldview().getObservationStrategies().stream().map(KlabAsset::getUrn).collect(Collectors.toSet());

        var conts = Sets.intersection(worldviewOntologies,
                ontologies.stream().map(KlabAsset::getUrn).collect(Collectors.toSet()));
        var cstra = Sets.intersection(worldviewStrategies,
                strategies.stream().map(KlabAsset::getUrn).collect(Collectors.toSet()));

        if (!conts.isEmpty() || !cstra.isEmpty()) {
            ret.add(Utils.Resources.create(this, Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER, operation,
                    Utils.Collections.shallowCollection(
                            ontologies.stream().filter(o -> conts.contains(o.getUrn())).toList(),
                            strategies.stream().filter(o -> conts.contains(o.getUrn())).toList()).toArray(new KlabAsset[0])));
        }

        ret.add(Utils.Resources.create(this, workspace, operation,
                Utils.Collections.shallowCollection(ontologies,
                        strategies,
                        namespaces, behaviors).toArray(new KlabAsset[0])));

        return ret;
    }

    @Override
    public ResourceSet model(String modelName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    //    @Override
    //    public boolean publishProject(String projectUrl, ResourcePrivileges permissions) {
    //        // TODO Auto-generated method stub
    //        return false;
    //    }
    //
    //    @Override
    //    public boolean unpublishProject(String projectUrl) {
    //        // TODO Auto-generated method stub
    //        return false;
    //    }

    @Override
    public List<ResourceSet> manageRepository(String projectName, RepositoryState.Operation operation,
                                              String... arguments) {
        return workspaceManager.manageRepository(projectName, operation, arguments);
    }

    @Override
    public ResourceSet createResource(Resource resource, UserScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet createResource(File resourcePath, UserScope scope) {
        // TODO Auto-generated method stub
        // Concept
        return null;
    }

    @Override
    public Resource createResource(String projectName, String urnId, String adapter,
                                   Parameters<String> resourceData, UserScope scope) {
        return null;
    }

    @Override
    public List<ResourceSet> deleteDocument(String projectName, String assetUrn,
                                            UserScope scope) {
        return null;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }


    @Override
    public ResourceSet queryModels(Observable observable, ContextScope scope) {

        if (!semanticSearchAvailable) {
            Logging.INSTANCE.warn("Semantic search is not available: client should not make this request");
            return ResourceSet.empty();
        }

        ResourceSet results = new ResourceSet();
        // FIXME use the observation's scale (pass the observation)
        for (ModelReference model : this.kbox.query(observable, scope)) {
            results.getResults().add(new ResourceSet.Resource(getUrl().toString(),
                    model.getName(), model.getProjectUrn(),
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
     * <p>
     * TODO/FIXME involve other services from the scope if a namespace is not
     * available locally.
     *
     * @param namespaces
     * @param results
     */
    private boolean addNamespacesToResultSet(Set<String> namespaces, ResourceSet results, Scope scope) {

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

    private boolean addNamespaceToResultSet(String ns, DefaultDirectedGraph<String, DefaultEdge> nss,
                                            Map<String, ResourceSet.Resource> storage, Scope scope) {

        if (nss.containsVertex(ns)) {
            return true;
        }

        KimNamespace namespace = resolveNamespace(ns, scope);
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
            for (KnowledgeClass k : resourceTypes) {
                wanted.add(k);
            }
        } else {
            // we want them all
            for (KnowledgeClass k : KnowledgeClass.values()) {
                wanted.add(k);
            }
        }

        if (wanted.contains(KnowledgeClass.RESOURCE)) {

        }
        if (wanted.contains(KnowledgeClass.MODEL)) {

        }
        if (wanted.contains(KnowledgeClass.SCRIPT)) {

        }
        if (wanted.contains(KnowledgeClass.APPLICATION)) {

        }
        if (wanted.contains(KnowledgeClass.BEHAVIOR)) {

        }
        if (wanted.contains(KnowledgeClass.COMPONENT)) {

        }
        if (wanted.contains(KnowledgeClass.NAMESPACE)) {

        }
        if (wanted.contains(KnowledgeClass.PROJECT)) {

        }
        //        if (wanted.contains(KnowledgeClass.INSTANCE)) {
        //
        //        }

        return ret;
    }

    @Override
    public ResourceStatus resourceStatus(String urn, Scope scope) {
        ResourceStatus ret = catalog.get(urn);
        if (ret != null && (ret.getType().isUsable())) {
            /*
             * TODO check the resource status at this time and in this scope
             */
        }
        return ret;
    }

    @Override
    public Project resolveProject(String projectName, Scope scope) {
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

        var status = catalog.get(resourceUrn);
        if (status != null) {
            return status.getPrivileges().asSeenByScope(scope);
        }
        return ResourcePrivileges.empty();
    }

    @Override
    public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
        var status = catalog.get(resourceUrn);
        if (status != null) {
            status.setPrivileges(resourcePrivileges);
            catalog.put(resourceUrn, status);
            db.commit();
            return true;
        }
        return false;
    }

    @Override
    public URL lockProject(String urn, UserScope scope) {
        String token = scope.getIdentity().getId();
        boolean local =
                scope instanceof ServiceScope || (scope instanceof ServiceUserScope userScope && userScope.isLocal());
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
            case RESOURCE -> {
            }
            case KIM_OBJECT -> {

                /**
                 * TODO may be a project or even a workspace
                 */

                KimNamespace namespace = resolveNamespace(urn, scope);
                if (namespace != null) {

                    ret.getResults().add(new ResourceSet.Resource(getUrl().toString(), urn,
                            namespace.getProjectName(),
                            namespace.getVersion(), KnowledgeClass.NAMESPACE));

                } else {


                    /*
                     * extract namespace and check for that.
                     */
                    String ns = Utils.Paths.getLeading(urn, '.');
                    String nm = Utils.Paths.getLast(urn, '.');
                    namespace = resolveNamespace(ns, scope);
                    /*
                     * TODO check permissions!
                     */
                    if (namespace != null) {
                        for (KlabStatement statement : namespace.getStatements()) {
                            if (urn.equals(statement.getUrn())) {
                                ret.getResults().add(new ResourceSet.Resource(getUrl().toString(), urn,
                                        namespace.getProjectName(),
                                        namespace.getVersion(), KlabAsset.classify(statement)));
                                break;
                            }
                        }
                    }
                }
            }
            case OBSERVABLE -> {
            }
            case REMOTE_URL -> {
            }
            case UNKNOWN -> {
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
                } else if (result.getKnowledgeClass() == KnowledgeClass.MODEL || result.getKnowledgeClass() == KnowledgeClass.DEFINITION) {
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
     * Replicate a remote scope in the scope manager. This should be called by the runtime service after
     * creating it so if the scope has no ID we issue an error, as we do not create independent scopes.
     *
     * @param sessionScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return
     */
    @Override
    public String registerSession(SessionScope sessionScope) {

        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

            if (sessionScope.getId() == null) {
                throw new KlabIllegalArgumentException("resolver: session scope has no ID, cannot register " +
                        "a scope autonomously");
            }

            getScopeManager().registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());
            return serviceSessionScope.getId();
        }

        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    /**
     * Replicate a remote scope in the scope manager. This should be called by the runtime service after
     * creating it so if the scope has no ID we issue an error, as we do not create independent scopes.
     *
     * @param contextScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return
     */
    @Override
    public String registerContext(ContextScope contextScope) {

        if (contextScope instanceof ServiceContextScope serviceContextScope) {

            if (contextScope.getId() == null) {
                throw new KlabIllegalArgumentException("resolver: context scope has no ID, cannot register " +
                        "a scope autonomously");
            }

            getScopeManager().registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());
            return serviceContextScope.getId();
        }

        throw new KlabIllegalArgumentException("unexpected scope class");

    }

}
