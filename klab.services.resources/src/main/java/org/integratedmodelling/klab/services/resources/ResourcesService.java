package org.integratedmodelling.klab.services.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.xtext.testing.IInjectorProvider;
import org.integratedmodelling.contrib.jgrapht.Graph;
import org.integratedmodelling.contrib.jgrapht.graph.DefaultDirectedGraph;
import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.contrib.jgrapht.traverse.TopologicalOrderIterator;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kdl.model.Kdl;
import org.integratedmodelling.kim.api.IKimObservable;
import org.integratedmodelling.kim.model.Kim;
import org.integratedmodelling.kim.model.KimLoader;
import org.integratedmodelling.kim.model.KimLoader.NamespaceDescriptor;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.resources.lang.KactorsInjectorProvider;
import org.integratedmodelling.klab.services.resources.lang.KdlInjectorProvider;
import org.integratedmodelling.klab.services.resources.lang.KimAdapter;
import org.integratedmodelling.klab.services.resources.lang.KimInjectorProvider;
import org.integratedmodelling.klab.utils.Utils;
import org.integratedmodelling.klab.utils.Utils.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.inject.Injector;

@Service
public class ResourcesService implements ResourceProvider, ResourceProvider.Admin {

    private static final long serialVersionUID = 6589150530995037678L;

    private static boolean languagesInitialized;

    private String url;
    private String localName;

    transient private KimLoader kimLoader;
    transient private ResourcesConfiguration configuration = new ResourcesConfiguration();
    transient private Authentication authenticationService;
    transient private ServiceScope scope;

    transient Map<String, Project> localProjects = Collections.synchronizedMap(new HashMap<>());
    transient Map<String, Workspace> localWorkspaces = Collections.synchronizedMap(new HashMap<>());
    transient Map<String, KimNamespace> localNamespaces = Collections.synchronizedMap(new HashMap<>());
    transient Map<String, KActorsBehavior> localBehaviors = Collections.synchronizedMap(new HashMap<>());

    /*
     * Dirty namespaces are kept in order of dependency and reloaded sequentially.
     */
    transient Set<String> dirtyNamespaces = Collections.synchronizedSet(new LinkedHashSet<>());
    transient Set<String> dirtyProjects = Collections.synchronizedSet(new LinkedHashSet<>());
    transient Set<String> dirtyWorkspaces = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Always load projects and namespaces sequentially.
     */
    transient ExecutorService projectLoader = Executors.newSingleThreadExecutor();

    /**
     * Default interval to check for changes in Git (15 minutes in milliseconds)
     */
    private long DEFAULT_GIT_SYNC_INTERVAL = 15l * 60l * 60l * 1000l;

    public ResourcesService() {
        this.localName = "klab.services.resources." + UUID.randomUUID();
        Services.INSTANCE.setResources(this);
        initializeLanguageServices();
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
        if (config.exists()) {
            configuration = Utils.YAML.load(config, ResourcesConfiguration.class);
        }
        loadWorkspaces();
    }

    @Autowired
    public ResourcesService(Authentication authenticationService) {
        this();
        this.authenticationService = authenticationService;
        this.scope = authenticationService.authorizeService(this);
    }

    /**
     * A singleton in nature, this is only to fork the same service in a different exclusive or
     * dedicated mode.
     * 
     * @param self
     */
    public ResourcesService(ResourcesService self) {
        this.localName = self.localName;
        this.url = self.url;
        this.configuration = self.configuration;
        this.localNamespaces = self.localNamespaces;
        this.localWorkspaces = self.localWorkspaces;
        this.projectLoader = self.projectLoader;
        this.kimLoader = self.kimLoader;
        this.dirtyNamespaces = self.dirtyNamespaces;
        this.dirtyProjects = self.dirtyProjects;
        this.dirtyWorkspaces = self.dirtyWorkspaces;
    }

    private void saveConfiguration() {
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
        Utils.YAML.save(this.configuration, config);
    }

    private void loadWorkspaces() {
        for (String workspace : configuration.getWorkspaces().keySet()) {
            for (String project : configuration.getWorkspaces().get(workspace)) {
                loadProject(configuration.getProjectConfiguration().get(project));
            }
        }
    }

    private synchronized void loadProject(final ProjectConfiguration projectConfiguration) {
        kimLoader.loadProject(projectConfiguration.getLocalPath());
    }

    private void initializeLanguageServices() {

        if (!languagesInitialized) {

            /*
             * set up access to the k.IM grammar
             */
            IInjectorProvider kimInjectorProvider = new KimInjectorProvider();
            Injector kimInjector = kimInjectorProvider.getInjector();
            if (kimInjector != null) {
                Kim.INSTANCE.setup(kimInjector);
            }

            this.kimLoader = new KimLoader((nss) -> loadNamespaces(nss));

            /*
             * k.DL....
             */
            IInjectorProvider kdlInjectorProvider = new KdlInjectorProvider();
            Injector kdlInjector = kdlInjectorProvider.getInjector();
            if (kdlInjector != null) {
                Kdl.INSTANCE.setup(kdlInjector);
            }

            /*
             * ...and k.Actors
             */
            IInjectorProvider kActorsInjectorProvider = new KactorsInjectorProvider();
            Injector kActorsInjector = kActorsInjectorProvider.getInjector();
            if (kActorsInjector != null) {
                KActors.INSTANCE.setup(kActorsInjector);
            }

            languagesInitialized = true;
        }

    }

    private void loadNamespaces(List<NamespaceDescriptor> namespaces) {
        for (NamespaceDescriptor ns : namespaces) {
            projectLoader.execute(() -> {
                KimNamespaceImpl namespace = KimAdapter.adaptKimNamespace(ns);
                this.localNamespaces.put(namespace.getUrn(), namespace);
            });
        }

    }

    @Override
    public KimNamespace resolveNamespace(String urn, Scope scope) {

        KimNamespace ret = localNamespaces.get(urn);
        if (ret != null) {
            /*
             * check permissions; if not allowed, log and set ret = null
             */
            ProjectConfiguration pconf = this.configuration.getProjectConfiguration().get(ret.getProjectName());
            if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
                // scope.info("");
                ret = null;
            }

        } else if (!this.scope.isExclusive()) {

            for (ResourceProvider service : Services.INSTANCE.getFederatedResources()) {
                KimNamespace candidate = service.resolveNamespace(urn, scope);
                if (candidate != null) {
                    // FIXME this causes more traffic than we should cause - maybe limit full scan
                    // to option
                    if (ret == null || (ret != null && candidate.getVersion().greater(ret.getVersion()))) {
                        ret = candidate;
                    }
                }
            }
        }

        return ret;
    }

    @Override
    public KActorsBehavior resolveBehavior(String urn, Scope scope) {

        return null;
    }

    @Override
    public Resource resolveResource(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
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

    @Override
    public synchronized boolean addProjectToLocalWorkspace(String workspaceName, String projectUrl, boolean overwriteIfExisting) {

        String projectName = Utils.URLs.getURLBaseName(projectUrl);
        ProjectConfiguration config = this.configuration.getProjectConfiguration().get(projectName);
        if (config != null) {
            if (overwriteIfExisting) {
                removeProjectFromLocalWorkspace(config.getWorkspaceName(), projectName);
            } else {
                return false;
            }
        }

        if (Utils.Git.isRemoteGitURL(projectUrl)) {

            File workspace = Configuration.INSTANCE.getDataPath(configuration.getServicePath() + File.separator
                    + configuration.getLocalResourcePath() + File.separator + workspaceName);
            workspace.mkdirs();
            try {

                projectName = Git.clone(projectUrl, workspace, false);
                ProjectConfiguration configuration = new ProjectConfiguration();
                configuration.setLocalPath(new File(workspace + File.separator + projectName));
                configuration.setSourceUrl(projectUrl);
                configuration.setWorkspaceName(workspaceName);
                configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);

                Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
                if (projects == null) {
                    projects = new LinkedHashSet<>();
                    this.configuration.getWorkspaces().put(workspaceName, projects);
                }
                projects.add(projectName);
                this.configuration.getProjectConfiguration().put(projectName, configuration);
                saveConfiguration();

                dirtyWorkspaces.add(workspaceName);
                dirtyProjects.add(projectName);

                loadProject(configuration);

            } catch (Throwable t) {
                return false;
            }

        } else if (projectUrl.startsWith("http")) {

            /*
             * Load from another service. These projects may be served as mirrors or just kept to
             * meet dependencies, according to the 'served' bit in the configuration.
             */

        } else if (projectUrl.startsWith("file:") || new File(projectUrl).isFile()) {

            /*
             * import a zipped project (from a publish operation) or connect a directory
             */

        }

        return false;
    }

    @Override
    public void removeProjectFromLocalWorkspace(String workspaceName, String projectName) {
        // TODO Auto-generated method stub
        // ProjectConfiguration configuration =
    }

    @Override
    public void removeWorkspace(String workspaceName) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Workspace> getWorkspaces() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {
        return shutdown(30);
    }

    public boolean shutdown(int secondsToWait) {
        try {
            projectLoader.awaitTermination(secondsToWait, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Logging.INSTANCE.error("Error during thread termination", e);
        }
        return false;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimObservable resolveObservable(String definition) {
        IKimObservable parsed = Kim.INSTANCE.declare(definition);
        return parsed == null ? null : KimAdapter.adaptKimObservable(parsed);
    }

    @Override
    public KimConcept resolveConcept(String definition) {
        IKimObservable parsed = Kim.INSTANCE.declare(definition);
        if (parsed == null) {
            return null;
        }
        return KimAdapter.adaptKimObservable(parsed).getMain();

    }

    @Override
    public ResourceSet projects(Collection<String> projects, Scope scope) {

        ResourceSet ret = new ResourceSet();

        /*
         * find all the worldview projects
         */
        for (String projectName : this.configuration.getProjectConfiguration().keySet()) {
            ProjectConfiguration project = this.configuration.getProjectConfiguration().get(projectName);
            if (projects.contains(projectName)) {
                ret = Utils.Resources.merge(ret, collectProject(projectName, scope));
            }
        }

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
        while(order.hasNext()) {
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
    private ResourceSet collectProject(String projectName, Scope scope) {
        List<KimNamespace> namespaces = new ArrayList<>();
        for (String namespace : this.localNamespaces.keySet()) {
            KimNamespace ns = this.localNamespaces.get(namespace);
            if (projectName.equals(ns.getProjectName())) {
                namespaces.add(ns);
            }
        }
        return Utils.Resources.create(this, namespaces.toArray(new KlabAsset[namespaces.size()]));
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
    public String addResourceToLocalWorkspace(Resource resource) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addResourceToLocalWorkspace(File resourcePath) {
        // TODO Auto-generated method stub
        // Concept
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

    @Override
    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    @Override
    public ServiceScope scope() {
        return this.scope;
    }

    @Override
    public List<Pair<KimModelStatement, Double>> queryModels(Observable observable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

}
