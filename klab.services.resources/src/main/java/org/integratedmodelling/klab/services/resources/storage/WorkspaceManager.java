package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.ImmutableList;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabConfigurationException;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.assets.WorkspaceImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.loader.ResourcesLoader;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.ObservationStrategySyntax;
import org.integratedmodelling.languages.api.NamespaceSyntax;
import org.integratedmodelling.languages.api.OntologySyntax;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.security.core.parameters.P;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the loading of
 * the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {


    private class ProjectDescriptor {
        String name;
        String workspace;
        ProjectStorage storage;
        Project.Manifest manifest;
        // TODO permissions

    }

    private final Function<String, Project> externalProjectResolver;
    private Map<String, ProjectDescriptor> projects = new HashMap<>();
    // these MAY be resolved through the network via callback. If unresolved, the corresponding Project
    // will be null.
    private Map<String, Project> externalReferences = new HashMap<>();
    // all logging goes through here
    private Channel monitor;
    private final ResourcesConfiguration configuration;

    private List<Pair<String, Version>> unresolvedProjects = new ArrayList<>();

    public WorkspaceManager(ResourcesConfiguration configuration, Channel monitor,
                            Function<String, Project> externalProjectResolver) {
        this.externalProjectResolver = externalProjectResolver;
        this.configuration = configuration;
        this.monitor = monitor;
        readConfiguration();
    }

    private void readConfiguration() {

        // clear existing caches (this must be reentrant and be callable again at any new import)
        projects.clear();
        externalReferences.clear();

        // build descriptors for all locally configured projects and workspaces

        for (var workspace : configuration.getWorkspaces().keySet()) {
            for (var project : configuration.getWorkspaces().get(workspace)) {
                var projectConfiguration = configuration.getProjectConfiguration().get(project);
                ProjectStorage projectStorage = importProject(projectConfiguration.getSourceUrl(), workspace);
                if (projectStorage != null) {

                    // create descriptor
                    ProjectDescriptor descriptor = new ProjectDescriptor();
                    descriptor.storage = projectStorage;
                    descriptor.manifest = readManifest(projectStorage);
                    descriptor.workspace = workspace;
                    descriptor.name = project;
                    projects.put(project, descriptor);

                } else {
                    // whine plaintively; the monitor will contain errors
                    monitor.error("Project " + project + " cannot be loaded. Configuration is invalid.");
                }
            }
        }

    }

    public ProjectStorage getProject(String projectName) {
        return null;
    }

    /**
     * Return all the projects in all workspaces in order of dependency. If projects refer to others that are
     * unavailable locally, those dependencies remain unresolved.
     *
     * @return
     */
    public List<ProjectStorage> getProjects() {
        return null;
    }

    /**
     * Return all known ontologies in order of dependency, ready for loading. Their requirements will only be
     * resolved internally, i.e. some may remain unsatisfied and will need to be resolved from other resource
     * services.
     *
     * @return
     */
    List<OntologySyntax> getOntologies() {
        return null;
    }

    /**
     * Return all the namespaces in order of dependency. Resolution is internal like in
     * {@link #getOntologies()}.
     *
     * @return
     */
    List<NamespaceSyntax> getNamespaces() {
        return null;
    }

    List<ObservationStrategySyntax> getStrategies() {
        return null;
    }

    /**
     * Import a project from a URL into the given workspace and return the associated storage. Project must
     * not exist already.
     *
     * @param projectUrl
     * @param workspaceName
     * @return
     */
    public ProjectStorage importProject(String projectUrl, String workspaceName) {

//        updateLock.writeLock().lock();
        ProjectStorage ret = null;

        try {

            String projectName = Utils.URLs.getURLBaseName(projectUrl);
            ResourcesConfiguration.ProjectConfiguration config =
                    this.configuration.getProjectConfiguration().get(projectName);
            if (config != null) {
                // throw exception
            }

            File workspace = Configuration.INSTANCE.getDataPath(
                    configuration.getServicePath() + File.separator + "workspaces" + File.separator + workspaceName);
            workspace.mkdirs();

            if (Utils.Git.isRemoteGitURL(projectUrl)) {

                try {

                    /**
                     * This should be the main use of project resources. TODO handle credentials
                     */

                    projectName = Utils.Git.clone(projectUrl, workspace, false);
//                    ProjectConfiguration configuration = new ProjectConfiguration();
//                    configuration.setLocalPath(new File(workspace + File.separator + projectName));
//                    configuration.setSourceUrl(projectUrl);
//                    configuration.setWorkspaceName(workspaceName);
//                    configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);
//                    /*
//                     * Default privileges are exclusive to the service
//                     */
//                    configuration.setPrivileges(ResourcePrivileges.empty());
//                    // this must happen before loadProject is called.
//                    this.configuration.getProjectConfiguration().put(projectName, configuration);
//
//                    Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
//                    if (projects == null) {
//                        projects = new LinkedHashSet<>();
//                        this.configuration.getWorkspaces().put(workspaceName, projects);
//                    }
//                    projects.add(projectName);
//                    Project project = importProject(projectName, configuration);
//                    configuration.setWorldview(project.getManifest().getDefinedWorldview() != null);
//                    saveConfiguration();
//                    return true;
                    File ws = new File(workspace + File.separator + projectName);
                    if (ws.exists()) {
                        ret = new FileProjectStorage(ws);
                    }

                } catch (Throwable t) {
                    // just make the return value null
                    File ws = new File(workspace + File.separator + projectName);
                    if (ws.exists()) {
                        Utils.Files.deleteQuietly(ws);
                    }
                }

            } else if (projectUrl.startsWith("http")) {

                /*
                 * TODO
                 *
                 * Load from another service. These projects may be served as mirrors or just
                 * kept to meet dependencies, according to the 'served' bit in the
                 * configuration. The source of truth should remain the source code, hosted in a
                 * single place (the remote service); mechanisms should be in place to store the
                 * original server and check for changes and new versions.
                 */

            } else if (projectUrl.startsWith("file:") || new File(projectUrl).isFile()) {

                var file = Utils.URLs.getFileForURL(projectUrl);
                if (file.isDirectory()) {
                    ret = new FileProjectStorage(file);
                } else if (Utils.Files.JAVA_ARCHIVE_EXTENSIONS.contains(Utils.Files.getFileExtension(file))) {
                    // TODO ret = read from archive
                }
            }
        } finally {
//            updateLock.writeLock().unlock();
        }

        return ret;
    }

    /**
     * Read, validate, resolve and sorts projects locally (all workspaces) and from the network, returning the
     * load order for all projects, including local and externally resolved ones. Check errors (reported in
     * the configured monitor) and unresolved projects after calling. Does not throw exceptions.
     * <p>
     * While loading the workspaces, (re)build the workspace list so that {@link #getWorkspaces()} can work.
     * The workspaces are also listed in order of first-contact dependency although circular deps between
     * workspaces are permitted.
     *
     * @return the load order or an empty collection in case of circular dependencies or no configuration. If
     * errors happened they will be notified through the monitor and {@link #getUnresolvedProjects()} will
     * return the list of projects that have not resolved properly (including resource not found and version
     * mismatch errors). Only one of the elements in each returned pair will be non-null.
     */
    public List<Pair<ProjectStorage, Project>> loadWorkspace() {

        List<Pair<ProjectStorage, Project>> loadOrder = new ArrayList<>();

        // build a version-aware dependency tree
        Graph<Pair<String, Version>, DefaultEdge> dependencyGraph =
                new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String s : projects.keySet()) {
            var snode = Pair.of(s, projects.get(s).manifest.getVersion());
            dependencyGraph.addVertex(snode);
            for (var dep : projects.get(s).manifest.getPrerequisiteProjects()) {
                var pnode = Pair.of(dep.getFirst(), dep.getSecond());
                dependencyGraph.addVertex(pnode);
                dependencyGraph.addEdge(pnode, snode);
            }
        }

        CycleDetector<Pair<String, Version>, DefaultEdge> cycleDetector =
                new CycleDetector<>(dependencyGraph);
        if (cycleDetector.detectCycles()) {
            monitor.error(Klab.ErrorCode.CIRCULAR_REFERENCES, Klab.ErrorContext.PROJECT, "Projects in " +
                    "configuration have cyclic dependencies on each other: " +
                    "will not " +
                    "proceed. Review configuration");
            return Collections.emptyList();
        } else {

            // establish load order: a list of either ProjectStorage or external Project

            TopologicalOrderIterator<Pair<String, Version>, DefaultEdge> sort =
                    new TopologicalOrderIterator(dependencyGraph);
            while (sort.hasNext()) {
                var proj = sort.next();
                // verify availability
                if (projects.get(proj.getFirst()) != null) {
                    // local dependency: check version
                    var pd = projects.get(proj.getFirst());
                    if (!pd.manifest.getVersion().compatible(proj.getSecond())) {
                        loadOrder.add(Pair.of(pd.storage, null));
                    } else {
                        monitor.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.MISMATCHED_VERSION,
                                "Project " + proj.getFirst() + "@" + proj.getSecond() + " is required" +
                                        " by other projects in workspace but incompatible version " + pd.manifest.getVersion()
                                        + " is available in local workspace");
                        unresolvedProjects.add(proj);
                    }
                } else {
                    var externalProject = externalProjectResolver.apply(proj.getFirst());
                    if (externalProject != null) {
                        // check version
                        if (externalProject.getManifest().getVersion().compatible(proj.getSecond())) {
                            externalReferences.put(proj.getFirst(), externalProject);
                            loadOrder.add(Pair.of(null, externalProject));
                        } else {
                            monitor.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.MISMATCHED_VERSION,
                                    "Project " + proj.getFirst() + "@" + proj.getSecond() + " is " +
                                            "required by other projects in workspace but incompatible " +
                                            "version " +
                                            externalProject.getManifest().getVersion() + " is available " +
                                            "externally");
                            unresolvedProjects.add(proj);
                        }
                    } else {
                        monitor.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.UNRESOLVED_REFERENCE,
                                "Project " + proj.getFirst() + "@" + proj.getSecond() + " is required" +
                                " by other projects in workspace but cannot be resolved from the network");
                        unresolvedProjects.add(proj);
                    }
                }
            }
        }

        return loadOrder;
    }

    private ProjectStorage newProject(String projectName, String workspaceName) {
        return null;
    }

    private boolean removeProject(String projectName) {
        return false;
    }

    private Project.Manifest readManifest(ProjectStorage project) {
        return Utils.Json.load(project.listResources(ProjectStorage.ResourceType.MANIFEST).getFirst(),
                ProjectImpl.ManifestImpl.class);
    }

    public Collection<Workspace> getWorkspaces() {
        List<Workspace> ret = new ArrayList<>();
        // TODO
        return ret;
    }

    public List<Pair<String, Version>> getUnresolvedProjects() {
        return unresolvedProjects;
    }

}
