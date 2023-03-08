package org.integratedmodelling.klab.services.resources;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.xtext.testing.IInjectorProvider;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kdl.model.Kdl;
import org.integratedmodelling.kim.api.IKimObservable;
import org.integratedmodelling.kim.model.Kim;
import org.integratedmodelling.kim.model.KimLoader;
import org.integratedmodelling.kim.model.KimLoader.NamespaceDescriptor;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.organization.KProject;
import org.integratedmodelling.klab.api.knowledge.organization.KWorkspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.services.Resources;
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
import org.springframework.stereotype.Service;

import com.google.inject.Injector;

@Service
public class ResourcesService implements Resources, Resources.Admin {

    private static final long serialVersionUID = 6589150530995037678L;

    private static boolean languagesInitialized;

    private String url;
    
    transient private KimLoader kimLoader;
    transient private ResourcesConfiguration configuration = new ResourcesConfiguration();

    transient Map<String, KProject> localProjects = Collections.synchronizedMap(new HashMap<>());
    transient Map<String, KWorkspace> localWorkspaces = Collections.synchronizedMap(new HashMap<>());
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
        Services.INSTANCE.setResources(this);
        initializeLanguageServices();
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
        if (config.exists()) {
            configuration = Utils.YAML.load(config, ResourcesConfiguration.class);
        }
        loadWorkspaces();
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
                this.localNamespaces.put(namespace.getName(), namespace);
            });
        }

    }

    @Override
    public KimNamespace resolveNamespace(String urn, Scope scope) {

        /*
         * must be a known project, either here or on a federated service.
         */

        return null;
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
    public List<KWorkspace> getWorkspaces() {
        // TODO Auto-generated method stub
        return null;
    }

    public void shutdown() {
        // TODO Auto-generated method stub
        try {
            projectLoader.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Logging.INSTANCE.error("Error during thread termination", e);
        }
    }

    public void shutdown(int secondsToWait) {
        // TODO Auto-generated method stub
        try {
            projectLoader.awaitTermination(secondsToWait, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Logging.INSTANCE.error("Error during thread termination", e);
        }
    }

    @Override
    public Capabilities capabilities() {
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
    public ResourceSet requestWorldview() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet requestProject(String projectName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet requestModel(String modelName, Scope scope) {
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
//        Concept
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
}
