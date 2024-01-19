package org.integratedmodelling.klab.services.resources;

//import org.integratedmodelling.kactors.model.KActors;
//import org.integratedmodelling.kdl.model.Kdl;
//import org.integratedmodelling.kim.api.IKimObservable;
//import org.integratedmodelling.kim.api.IKimProject;
//import org.integratedmodelling.kim.model.Kim;
//import org.integratedmodelling.kim.model.KimLoader;
//import org.integratedmodelling.kim.model.KimLoader.NamespaceDescriptor;

import org.integratedmodelling.klab.api.authentication.CRUDPermission;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Project.Manifest;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.persistence.ModelKbox;
import org.integratedmodelling.klab.services.resources.persistence.ModelReference;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.serializer.GroupSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

@Service
public class ResourcesProvider extends BaseService implements ResourcesService, ResourcesService.Admin {

    private static final long serialVersionUID = 6589150530995037678L;

    private static boolean languagesInitialized;

    private String url;
    private Authentication authenticationService;
    private String hardwareSignature = Utils.Strings.hash(Utils.OS.getMACAddress());

    @Override
    public boolean isLocal() {
        String serverId = Utils.Strings.hash(Utils.OS.getMACAddress());
        return (capabilities().getServerId() == null && serverId == null) ||
                (capabilities().getServerId() != null && capabilities().getServerId().equals("RESOURCES_" + serverId));
    }

    /**
     * The projects are pre-sorted in order of dependency
     */
//    private Map<String, Project> localProjects = Collections.synchronizedMap(new LinkedHashMap<>());
//    private Map<String, WorkspaceImpl> localWorkspaces = Collections.synchronizedMap(new HashMap<>());
    private Map<String, KimNamespace> localNamespaces = Collections.synchronizedMap(new HashMap<>());
    // we need to be able to serve entire, valid worldviews, so if this is filled in it may contain both
    // local and remote ontologies. The worldview is always kept in dependency order.
    private Map<String, KimOntology> servedOntologies = Collections.synchronizedMap(new LinkedHashMap<>());
    private Map<String, KActorsBehavior> localBehaviors = Collections.synchronizedMap(new HashMap<>());

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

    /*
     * "fair" read/write lock to ensure no reading during updates
     */
    private ReadWriteLock updateLock = new ReentrantReadWriteLock(true);

    @SuppressWarnings("unchecked")
    public ResourcesProvider(ServiceScope scope, String localName,
                             BiConsumer<Scope, Message>... messageListeners) {
        super(scope, localName, messageListeners);
        if (scope instanceof LocalServiceScope localScope) {
            localScope.setService(this);
        }
//        initializeLanguageServices();

        this.db = DBMaker
                .fileDB(Configuration.INSTANCE.getDataPath("resources/catalog") + File.separator +
                        "gbif_ids.db")
                .transactionEnable().closeOnJvmShutdown().make();
        this.catalog =
                db.treeMap("resourcesCatalog", GroupSerializer.STRING, GroupSerializer.JAVA).createOrOpen();

        this.workspaceManager = new WorkspaceManager(scope, (projectId) -> resolveRemoteProject(projectId));
    }

    public Project resolveRemoteProject(String projectId) {
        // TODO
        System.out.println("TODO resolve external project " + projectId);
        return null;
    }

    @Autowired
    public ResourcesProvider(Authentication authenticationService, ServiceScope scope, String localName,
                             BiConsumer<Scope,
                                     Message>... messageListeners) {
        this(scope, localName, messageListeners);
        this.authenticationService = authenticationService;
    }

    @Override
    public void initializeService() {

        scope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing,
                capabilities());
        this.kbox = ModelKbox.create(localName, this.scope);
        this.workspaceManager.loadWorkspace();
        /*
         * TODO launch update service
         */
        scope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable,
                capabilities());
    }

    /**
     * Load projects in the order established by the workspace manager. We may be reloading after a change.
     * The resolver callback for external projects may have been called already by the workspace manager to
     * resolve project dependencies.
     */
//    private boolean loadWorkspaces() {
//        localProjects.clear();
//        for (var project : this.workspaceManager.getWorkspaces()) {
//            localProjects.put(projectData.getUrn(), projectData);
//        }
//        return true;
//    }


//    public ResourceSet loadWorkspaces() {
//        List<String> projects = new ArrayList<>();
//        for (String workspaceName : configuration.getWorkspaces().keySet()) {
//            Workspace workspace = getWorkspace(workspaceName);
//            for (String project : configuration.getWorkspaces().get(workspaceName)) {
//                workspace.getProjects().add(importProject(project,
//                        configuration.getProjectConfiguration().get(project)));
//                projects.add(project);
//            }
//        }
//        return projects(projects, scope);
//    }

//    private WorkspaceImpl getWorkspace(String workspaceName) {
//        var ret = localWorkspaces.get(workspaceName);
//        if (ret == null) {
//            ret = new WorkspaceImpl();
//            ((WorkspaceImpl) ret).setName(workspaceName);
//            // ((WorkspaceImpl)ret).setUrl(this.url + "/workspaces/" + workspaceName);
//            this.localWorkspaces.put(workspaceName, ret);
//        }
//        return ret;
//    }

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
//    private void updateProjects() {
//        for (String projectName : configuration.getProjectConfiguration().keySet()) {
//            ProjectConfiguration project = configuration.getProjectConfiguration().get(projectName);
//            Long lastUpdate = this.lastUpdate.get(projectName);
//            if (Git.isRemoteGitURL(project.getSourceUrl())) {
//                if (lastUpdate == null || (lastUpdate > 0
//                        && (System.currentTimeMillis() - lastUpdate) >= project.getSyncIntervalMs())) {
//                    Git.pull(project.getLocalPath());
//                    this.lastUpdate.put(projectName, System.currentTimeMillis());
//                }
//            }
//
//        }
//    }

//    /**
//     * TODO this must load to a staging area and commit the project after approval.
//     *
//     * @param projectName
//     * @param projectConfiguration
//     * @return
//     */
//    private synchronized Project importProject(String projectName,
//                                               final ProjectConfiguration projectConfiguration) {
//
//        /*
//         * this automatically loads namespaces and behaviors through callbacks.
//         */
//        ProjectImpl project = new ProjectImpl();
//        project.setName(projectName);
//        // project.setUrl(this.url + "/projects/" + kimProject.getName());
//        localProjects.put(projectName, project);
////        IKimProject kimProject = kimLoader.loadProject(projectConfiguration.getLocalPath());
////        File resourceDir = new File(kimProject.getRoot() + File.separator + "resources");
////        loadResources(resourceDir, project, 0, true);
////        project.setManifest(readManifest(kimProject));
////		project.getMetadata().putAll(readMetadata(kimProject, project.getManifest()));
//
//        /*
//         * TODO check notifications in namespaces and behaviors and ensure the project
//         * reports errors in its components. Resources are managed indepenendently of
//         * the projects they belong to. FIXME probably the resource permissions could be
//         * intersected, but it's probably confusing although restrictions in projects
//         * should be the default for resources..
//         */
//        /*
//         * TODO add a ResourceStatus record for the project
//         */
//
//        db.commit();
//
//        return project;
//
//    }

//    @SuppressWarnings("unchecked")
//    private Map<String, Object> readMetadata(IKimProject kimProject, Manifest manifest) {
//        File manifestFile = new File(
//                kimProject.getRoot() + File.separator + "META-INF" + File.separator + "metadata.json");
//        if (manifestFile.exists()) {
//            return Utils.Json.load(manifestFile, Map.class);
//        }
//        Map<String, Object> ret = new LinkedHashMap<>();
//        for (Object key : kimProject.getProperties().keySet()) {
//            ret.put(key.toString(), kimProject.getProperties().getProperty(key.toString()));
//        }
//        if (!ret.containsKey(Metadata.DC_COMMENT)) {
//            ret.put(Metadata.DC_COMMENT, manifest.getDescription());
//        }
//        return ret;
//    }
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
//                            .load(new File(subdir + File.separator + "resource.json"),
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
                                       ResourceStatus.Type.OFFLINE :
                                       ResourceStatus.Type.AVAILABLE);
                        status.setLegacy(legacy);
                        status.setKnowledgeClass(KnowledgeClass.RESOURCE);
                        // TODO fill in the rest
                        catalog.put(resource.getUrn(), status);
                    }
                }
            }
        }
    }


//    private void loadBehaviors(List<File> behaviors) {
//        for (File behaviorFile : behaviors) {
//            // projectLoader.execute(() -> {
//            KActorsBehavior behavior = KActorsAdapter.INSTANCE.readBehavior(behaviorFile);
//            if (behavior.getProjectId() != null) {
//                localProjects.get(behavior.getProjectId()).getBehaviors().add(behavior);
//            }
//            this.localBehaviors.put(behavior.getUrn(), behavior);
//            // });
//        }
//    }
//
//    private void loadNamespaces(List<NamespaceDescriptor> namespaces) {
//        for (NamespaceDescriptor ns : namespaces) {
//            // projectLoader.execute(() -> {
//            KimNamespaceImpl namespace = KimAdapter.adaptKimNamespace(ns);
//            Project project = localProjects.get(namespace.getProjectName());
//            project.getNamespaces().add(namespace);
//            this.localNamespaces.put(namespace.getUrn(), namespace);
//            this.kbox.store(namespace, this.scope);
//            for (KimStatement statement : namespace.getStatements()) {
//                if (statement instanceof KimModel && !Utils.Notifications.hasErrors(statement
//                .getNotifications())) {
//                    this.kbox.store(statement, scope);
//                }
//            }
//            // });
//        }
//    }

    @Override
    public KimNamespace resolveNamespace(String urn, Scope scope) {

        KimNamespace ret = localNamespaces.get(urn);
        if (ret != null && !(scope instanceof ServiceScope)) {
            /*
             * check permissions; if not allowed, log and set ret = null. If the scope is
             * the service itself, we can access everything.
             */
            ProjectConfiguration pconf = null; // TODO
//                    this.configuration.getProjectConfiguration().get(ret.getProjectName());
            if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
                scope.debug("trying to access unauthorized namespace " + urn);
                ret = null;
            }

        }

        return ret;
    }

    public KimOntology resolveOntology(String urn, Scope scope) {
        KimOntology ret = servedOntologies.get(urn);
        if (ret != null && !(scope instanceof ServiceScope)) {
            /*
             * check permissions; if not allowed, log and set ret = null. If the scope is
             * the service itself, we can access everything.
             */
            ProjectConfiguration pconf = null; // TODO
//                    this.configuration.getProjectConfiguration().get(ret.getProjectName());
            if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
                scope.debug("trying to access unauthorized namespace " + urn);
                ret = null;
            }

        }

        return ret;
    }

    @Override
    public KActorsBehavior resolveBehavior(String urn, Scope scope) {

        KActorsBehavior ret = localBehaviors.get(urn);
        if (ret != null && !(scope instanceof ServiceScope)) {
            if (ret.getProjectId() != null) {
                ProjectConfiguration pconf = null; // TODO
//                        this.configuration.getProjectConfiguration().get(ret.getProjectId());
                if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
                    // scope.info("");
                    ret = null;
                }
            }
            return ret;
        }

        return null;
    }

    @Override
    public Resource resolveResource(String urn, Scope scope) {
        if (localResources.contains(Urn.removeParameters(urn))) {
            // TODO
        }
        return null;
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
//
//    public ProjectStorage importProjectStorage(String projectUrl, String workspaceName,
//                                               boolean overwriteIfExisting) {
//
//        updateLock.writeLock().lock();
//        ProjectStorage ret = null;
//
//        try {
//
//            String projectName = Utils.URLs.getURLBaseName(projectUrl);
//            ProjectConfiguration config = this.configuration.getProjectConfiguration().get(projectName);
//            if (config != null) {
//                if (overwriteIfExisting) {
//                    removeProject(projectName);
//                }
//            }
//
//            File workspace = Configuration.INSTANCE.getDataPath(
//                    configuration.getServicePath() + File.separator + "workspaces" + File.separator +
//                    workspaceName);
//            workspace.mkdirs();
//
//            if (Utils.Git.isRemoteGitURL(projectUrl)) {
//
//                try {
//
//                    /**
//                     * This should be the main use of project resources. TODO handle credentials
//                     */
//
//                    projectName = Git.clone(projectUrl, workspace, false);
////                    ProjectConfiguration configuration = new ProjectConfiguration();
////                    configuration.setLocalPath(new File(workspace + File.separator + projectName));
////                    configuration.setSourceUrl(projectUrl);
////                    configuration.setWorkspaceName(workspaceName);
////                    configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);
////                    /*
////                     * Default privileges are exclusive to the service
////                     */
////                    configuration.setPrivileges(ResourcePrivileges.empty());
////                    // this must happen before loadProject is called.
////                    this.configuration.getProjectConfiguration().put(projectName, configuration);
////
////                    Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
////                    if (projects == null) {
////                        projects = new LinkedHashSet<>();
////                        this.configuration.getWorkspaces().put(workspaceName, projects);
////                    }
////                    projects.add(projectName);
////                    Project project = importProject(projectName, configuration);
////                    configuration.setWorldview(project.getManifest().getDefinedWorldview() != null);
////                    saveConfiguration();
////                    return true;
//                    File ws = new File(workspace + File.separator + projectName);
//                    if (ws.exists()) {
//                        ret = new FileProjectStorage(ws);
//                    }
//
//                } catch (Throwable t) {
//                    // just make the return value null
//                    File ws = new File(workspace + File.separator + projectName);
//                    if (ws.exists()) {
//                        Utils.Files.deleteQuietly(ws);
//                    }
//                }
//
//            } else if (projectUrl.startsWith("http")) {
//
//                /*
//                 * TODO
//                 *
//                 * Load from another service. These projects may be served as mirrors or just
//                 * kept to meet dependencies, according to the 'served' bit in the
//                 * configuration. The source of truth should remain the source code, hosted in a
//                 * single place (the remote service); mechanisms should be in place to store the
//                 * original server and check for changes and new versions.
//                 */
//
//            } else if (projectUrl.startsWith("file:") || new File(projectUrl).isFile()) {
//
//                var file = Utils.URLs.getFileForURL(projectUrl);
//                if (file.isDirectory()) {
//                    ret = new FileProjectStorage(file);
//                } else if (Utils.Files.JAVA_ARCHIVE_EXTENSIONS.contains(Utils.Files.getFileExtension
//                (file))) {
//                    // TODO ret = read from archive
//                }
//            }
//        } finally {
//            updateLock.writeLock().unlock();
//        }
//
//        return ret;
//    }

    /**
     * TODO improve logics: the main function should return the appropriate ProjectStorage for the URL in
     * all cases. Then call importProject (storage) when all the different storages are implemented.
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
    public synchronized boolean importProject(String workspaceName, String projectUrl,
                                              boolean overwriteIfExisting) {
        var storage = workspaceManager.importProject(projectUrl, workspaceName);
        return storage != null/* && loadWorkspaces()*/;
    }

    @Override
    public Project createProject(String workspaceName, String projectName) {
        return null;
    }

    @Override
    public Project updateProject(String projectName, Manifest manifest, Metadata metadata) {
        return null;
    }

    @Override
    public KimNamespace createNamespace(String projectName, String namespaceContent) {
        return null;
    }

    @Override
    public KimNamespace updateNamespace(String projectName, String namespaceContent) {
        return null;
    }

    @Override
    public KActorsBehavior createBehavior(String projectName, String behaviorContent) {
        return null;
    }

    @Override
    public KActorsBehavior updateBehavior(String projectName, String behaviorContent) {
        return null;
    }

    @Override
    public KimOntology createOntology(String projectName, String ontologyContent) {
        return null;
    }

    @Override
    public KimOntology updateOntology(String projectName, String ontologyContent) {
        return null;
    }

    @Override
    public void removeProject(String projectName) {

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
    }

//    private String getWorkspace(Project project) {
//        for (String ret : this.configuration.getWorkspaces().keySet()) {
//            if (this.configuration.getWorkspaces().get(ret).contains(project.getName())) {
//                return ret;
//            }
//        }
//        return null;
//    }

    @Override
    public void removeWorkspace(String workspaceName) {
        Workspace workspace = workspaceManager.getWorkspace(workspaceName);
        for (Project project : workspace.getProjects()) {
            removeProject(project.getUrn());
        }
//        try {
//            updateLock.writeLock().lock();
////            this.localWorkspaces.remove(workspaceName);
//        } finally {
//            updateLock.writeLock().unlock();
//        }
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

        scope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities());

        // try {
        // projectLoader.awaitTermination(secondsToWait, TimeUnit.SECONDS);
        return true;
        // } catch (InterruptedException e) {
        // Logging.INSTANCE.error("Error during thread termination", e);
        // }
        // return false;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities() {
            @Override
            public boolean isWorldviewProvider() {
                // TODO
                return !getWorldview().isEmpty();
            }

            @Override
            public String getAdoptedWorldview() {
                return getWorldview().isEmpty() ? null : getWorldview().getUrn();
            }

            @Override
            public Set<CRUDPermission> getPermissions() {
                // TODO depends on who asks and from where - check user scope
                return EnumSet.noneOf(CRUDPermission.class);
            }

            @Override
            public Type getType() {
                return Type.RESOURCES;
            }

            @Override
            public String getLocalName() {
                return localName;
            }

            @Override
            public String getServiceName() {
                return "Resources";
            }

            @Override
            public String getServerId() {
                return hardwareSignature == null ? null : ("RESOURCES_" + hardwareSignature);
            }
        };
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
            return errors ? null : LanguageAdapter.INSTANCE.adaptObservable(parsed);
        }
        return null;
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
            return errors ? null : LanguageAdapter.INSTANCE.adaptSemantics(parsed);
        }
        return null;
    }

    @Override
    public ResourceSet projects(Collection<String> projects, Scope scope) {

        ResourceSet ret = new ResourceSet();

        // TODO
//        for (String projectName : this.configuration.getProjectConfiguration().keySet()) {
//            if (projects.contains(projectName)) {
////                if (!localProjects.containsKey(projectName)) {
////                    importProject(projectName, this.configuration.getProjectConfiguration().get
// (projectName));
////                }
//                ret = Utils.Resources.merge(ret, collectProject(projectName, scope));
//            }
//        }

        return sort(ret, scope);
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
    private ResourceSet collectProject(String projectName, Scope scope) {
        List<KimNamespace> namespaces = new ArrayList<>();
        for (String namespace : this.localNamespaces.keySet()) {
            KimNamespace ns = this.localNamespaces.get(namespace);
            if (projectName.equals(ns.getProjectName())) {
                namespaces.add(ns);
            }
        }
        List<KActorsBehavior> behaviors = new ArrayList<>();
        for (KActorsBehavior behavior : localBehaviors.values()) {
            if (projectName.equals(behavior.getProjectId())) {
                behaviors.add(behavior);
            }
        }

        // Resources work independently and do not come with the project data.

        return Utils.Resources.create(this,
                Utils.Collections.shallowCollection(namespaces, behaviors).toArray(new KlabAsset[namespaces.size()]));
    }

    @Override
    public ResourceSet project(String projectName, Scope scope) {
        return sort(collectProject(projectName, scope), scope);
    }

    @Override
    public ResourceSet model(String modelName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean publishProject(String projectUrl, ResourcePrivileges permissions) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean unpublishProject(String projectUrl) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String createResource(Resource resource) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String createResource(File resourcePath) {
        // TODO Auto-generated method stub
        // Concept
        return null;
    }

    @Override
    public Resource createResource(String projectName, String urnId, String adapter,
                                   Parameters<String> resourceData) {
        return null;
    }

    @Override
    public boolean publishResource(String resourceUrn, ResourcePrivileges permissions) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean unpublishResource(String resourceUrn) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    @Override
    public ServiceScope scope() {
        return this.scope;
    }

    @Override
    public ResourceSet queryModels(Observable observable, ContextScope scope) {

        ResourceSet results = new ResourceSet();
        for (ModelReference model : this.kbox.query(observable, scope)) {
            results.getResults().add(new ResourceSet.Resource(this.url,
                    model.getNamespaceId() + "." + model.getName(),
                    model.getVersion(), KnowledgeClass.MODEL));
        }

        addDependencies(results, scope);

        return results;
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

        KimNamespace namespace = this.localNamespaces.get(ns);
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
        resource.setServiceId(getUrl());
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
        if (wanted.contains(KnowledgeClass.INSTANCE)) {

        }

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
        // TODO Auto-generated method stub
        return null;
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
    public Collection<Project> listProjects() {
        return workspaceManager.getProjects();
    }

    @Override
    public Collection<String> listResourceUrns() {
        return localResources;
    }

//    @Override
//    public ResourceSet loadWorldview() {
//        List<String> projects = new ArrayList<>();
//        for (String project : configuration.getProjectConfiguration().keySet()) {
//            if (configuration.getProjectConfiguration().get(project).isWorldview()) {
//                projects.add(project);
//            }
//        }
//        return projects(projects, scope);
//    }

    @Override
    public ResourceSet resolve(String urn, Scope scope) {

        ResourceSet ret = new ResourceSet();

        /*
         * TODO Check if it's a project
         */
        /*if (workspaceManager.getLocalProjectURNs().contains(urn)) {

        } else */if (localNamespaces.containsKey(urn)) {

            /*
             * If not, check for namespace
             */

        } else if (localBehaviors.containsKey(urn)) {

            /*
             * If not, check for behavior
             */

        } else if (urn.contains(".")) {

            /*
             * if not, extract namespace and check for that.
             */
            String ns = Utils.Paths.getLeading(urn, '.');
            String nm = Utils.Paths.getLast(urn, '.');
            KimNamespace namespace = localNamespaces.get(ns);
            /*
             * TODO check permissions!
             */
            if (namespace != null) {
                for (KlabStatement statement : namespace.getStatements()) {
                    if (statement instanceof KimModel && urn.equals(((KimModel) statement).getUrn())) {
                        ret.getResults().add(
                                new ResourceSet.Resource(getUrl(), urn, namespace.getVersion(),
                                        KnowledgeClass.MODEL));
                    } else if (statement instanceof KimInstance && nm.equals(((KimInstance) statement).getName())) {
                        ret.getResults().add(new ResourceSet.Resource(getUrl(), urn, namespace.getVersion(),
                                KnowledgeClass.INSTANCE));
                    }
                }

                if (ret.getResults().size() > 0) {
                    ret.getNamespaces().add(new ResourceSet.Resource(getUrl(), namespace.getUrn(),
                            namespace.getVersion(), KnowledgeClass.NAMESPACE));
                }

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

        Set<String> namespaces = new HashSet<>();
        for (ResourceSet.Resource result : resourceSet.getResults()) {
            if (Urn.classify(result.getResourceUrn()) == Urn.Type.KIM_OBJECT) {
                if (result.getKnowledgeClass() == KnowledgeClass.NAMESPACE) {
                    namespaces.add(result.getResourceUrn());
                } else if (result.getKnowledgeClass() == KnowledgeClass.MODEL
                        || result.getKnowledgeClass() == KnowledgeClass.INSTANCE
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

}
