package org.integratedmodelling.klab.services.resources.storage;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.NamespaceSyntax;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.observable.ObservableSequence;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.worldview.Ontology;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the loading of
 * the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {


    private List<Pair<ProjectStorage, Project>> _projectLoadOrder;
    private List<KimOntology> _ontologyOrder;
    private List<KimOntology> _worldview;
    private Parser<ObservableSequence> observableParser = new Parser<ObservableSequence>() {
        @Override
        protected Injector createInjector() {
            return new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    private Parser<Ontology> ontologyParser = new Parser<Ontology>() {
        @Override
        protected Injector createInjector() {
            return new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    private Parser<Model> namespaceParser = new Parser<Model>() {
        @Override
        protected Injector createInjector() {
            return new KimStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    private class ProjectDescriptor {
        String name;
        String workspace;
        // one of the next two is filled in
        ProjectStorage storage;
        Project externalProject;
        Project.Manifest manifest;
        // these are loaded on demand, use only through their accessors
        private Set<String> _namespaceIds;
        private Set<String> _ontologyIds;
        // TODO permissions


        public Set<String> getNamespaceIds() {
            if (_namespaceIds == null) {

            }
            return _namespaceIds;
        }

        public Set<String> getOntologyIds() {
            if (_ontologyIds == null) {

            }
            return _ontologyIds;
        }
    }

    private final Function<String, Project> externalProjectResolver;
    private Map<String, ProjectDescriptor> projects = new HashMap<>();
    // these MAY be resolved through the network via callback. If unresolved, the corresponding Project
    // will be null.
//    private Map<String, Project> externalReferences = new HashMap<>();
    // all logging goes through here
    private Scope scope;
    private final ResourcesConfiguration configuration;

    private List<Pair<String, Version>> unresolvedProjects = new ArrayList<>();

    public WorkspaceManager(ResourcesConfiguration configuration, Scope scope,
                            Function<String, Project> externalProjectResolver) {
        this.externalProjectResolver = externalProjectResolver;
        this.configuration = configuration;
        this.scope = scope;
        readConfiguration();
    }

    private void readConfiguration() {

        // clear existing caches (this must be reentrant and be callable again at any new import)
        projects.clear();

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
                    scope.error("Project " + project + " cannot be loaded. Configuration is invalid.");
                }
            }
        }
    }

    /**
     * Return all ontologies sorted in order of dependency. Automatically adapt the local ones from their
     * syntactic form. Project dependencies will ensure the consistency of the result; if any of the
     * ontologies is part of a missing project, return an empty list.
     *
     * @param worldviewOnly if true, only ontologies that are part of a project tagged as worldview will be
     *                      returned
     * @return the fully consistent known worldview or an empty list
     */
    public List<KimOntology> getOntologies(boolean worldviewOnly) {

        if (_ontologyOrder == null) {
            _worldview = new ArrayList<>();
            _ontologyOrder = new ArrayList<>();
            Map<String, Triple<Ontology, KimOntology, Boolean>> cache = new HashMap<>();
            for (var pd : projects.values()) {
                var isWorldview = pd.manifest.getDefinedWorldview() != null;
                if (pd.externalProject != null) {
                    for (var ontology : pd.externalProject.getOntologies()) {
                        cache.put(ontology.getUrn(), Triple.of(null, ontology, isWorldview));
                    }
                } else {
                    for (var ontologyUrl : pd.storage.listResources(ProjectStorage.ResourceType.ONTOLOGY)) {
                        try (var input = ontologyUrl.openStream()) {
                            var errors = new ArrayList<Notification>();
                            var parsed = ontologyParser.parse(input, errors);
                            if (!errors.isEmpty()) {
                                scope.error("Ontology resource has errors: " + ontologyUrl,
                                        Klab.ErrorCode.RESOURCE_VALIDATION, Klab.ErrorContext.ONTOLOGY);
                                return Collections.emptyList();
                            }
                            cache.put(parsed.getNamespace().getName(), Triple.of(parsed, null, isWorldview));
                        } catch (IOException e) {
                            // log error and return failure
                            scope.error("Error loading ontology " + ontologyUrl,
                                    Klab.ErrorCode.READ_FAILED, Klab.ErrorContext.ONTOLOGY);
                            return Collections.emptyList();
                        }
                    }
                }
            }

            // we have the ontologies and there are no errors this far: now build the order and if
            // something is unresolved, log error and say goodbye
            Graph<String, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
            Map<String, KimOntology> ontologies = new HashMap<>();
            for (String ontologyId : cache.keySet()) {
                var od = cache.get(ontologyId);
                dependencyGraph.addVertex(ontologyId);
                if (od.getFirst() != null) {
                    for (var imported : od.getFirst().getNamespace().getImported()) {
                        dependencyGraph.addVertex(imported);
                        dependencyGraph.addEdge(imported, ontologyId);
                    }
                } else {
                    for (var imported : od.getSecond().getImportedOntologies()) {
                        dependencyGraph.addVertex(imported);
                        dependencyGraph.addEdge(imported, ontologyId);
                    }
                }
            }

            CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(dependencyGraph);
            if (cycleDetector.detectCycles()) {
                scope.error("Circular dependencies in ontology graph: cannot continue",
                        Klab.ErrorCode.CIRCULAR_REFERENCES, Klab.ErrorContext.ONTOLOGY);
                return Collections.emptyList();
            }

            var languageValidationScope = new BasicObservableValidationScope() {
                @Override
                public ConceptDescriptor getConceptDescriptor(String name) {
                    return null; // conceptDescriptors.get(name);
                }

                @Override
                public boolean hasReasoner() {
                    return false; // TODO
                }
            };

            // finish building the ontologies in the given order using a new language validator
            TopologicalOrderIterator<String, DefaultEdge> sort =
                    new TopologicalOrderIterator<>(dependencyGraph);
            while (sort.hasNext()) {
                var ontologyId = sort.next();
                var od = cache.get(ontologyId);
                if (od == null) {
                    scope.error("Ontology " + ontologyId + " cannot be resolved either locally or through" +
                                    " the network",
                            Klab.ErrorCode.UNRESOLVED_REFERENCE, Klab.ErrorContext.ONTOLOGY);
                    return Collections.emptyList();
                }
                AtomicBoolean errors = new AtomicBoolean(false);
                var ontology = od.getSecond();
                if (ontology == null) {
                    var syntax = new OntologySyntaxImpl(od.getFirst(), languageValidationScope) {

                        @Override
                        protected void logWarning(ParsedObject target, EObject object,
                                                  EStructuralFeature feature, String message) {

                        }

                        @Override
                        protected void logError(ParsedObject target, EObject object,
                                                EStructuralFeature feature, String message) {
                            errors.set(true);
                        }
                    };
                    ontology = LanguageAdapter.INSTANCE.adaptOntology(syntax);
                }

                if (errors.get()) {
                    scope.error("Logical errors in ontology " + ontologyId + ": cannot continue",
                            Klab.ErrorCode.RESOURCE_VALIDATION, Klab.ErrorContext.ONTOLOGY);
                    return Collections.emptyList();
                }

                this._ontologyOrder.add(ontology);
                if (od.getThird()) {
                    this._worldview.add(ontology);
                }
            }
        }

        return worldviewOnly ? _worldview : _ontologyOrder;
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
    public List<ProjectStorage> getLocalProjects() {
        return null;
    }

    /**
     * Return all the namespaces in order of dependency. Resolution is internal like in
     * {@link #getOntologies(boolean)}.
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
     * not exist already. Removes any cached load order so that it can be computed again when requested.
     *
     * @param projectUrl
     * @param workspaceName
     * @return
     */
    public ProjectStorage importProject(String projectUrl, String workspaceName) {

//        updateLock.writeLock().lock();
        ProjectStorage ret = null;
        this._projectLoadOrder = null;
        this._ontologyOrder = null;

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

    public List<Pair<ProjectStorage, Project>> getProjectLoadOrder() {
        if (this._projectLoadOrder == null) {
            this._projectLoadOrder = loadWorkspace();
        }
        return this._projectLoadOrder;
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
            scope.error(Klab.ErrorCode.CIRCULAR_REFERENCES, Klab.ErrorContext.PROJECT, "Projects in " +
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
                        scope.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.MISMATCHED_VERSION,
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
                            ProjectDescriptor descriptor = new ProjectDescriptor();
                            descriptor.externalProject = externalProject;
                            descriptor.manifest = externalProject.getManifest();
                            descriptor.workspace = null;
                            descriptor.name = proj.getFirst();
                            projects.put(proj.getFirst(), descriptor);
                            loadOrder.add(Pair.of(null, externalProject));
                        } else {
                            scope.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.MISMATCHED_VERSION,
                                    "Project " + proj.getFirst() + "@" + proj.getSecond() + " is " +
                                            "required by other projects in workspace but incompatible " +
                                            "version " +
                                            externalProject.getManifest().getVersion() + " is available " +
                                            "externally");
                            unresolvedProjects.add(proj);
                        }
                    } else {
                        scope.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.UNRESOLVED_REFERENCE,
                                "Project " + proj.getFirst() + "@" + proj.getSecond() + " is required" +
                                        " by other projects in workspace but cannot be resolved from the " +
                                        "network");
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


    private abstract class Parser<T extends EObject> {

        @Inject
        private IParser parser;

        public Parser() {
            createInjector().injectMembers(this);
        }

        protected abstract Injector createInjector();

        public T parse(InputStream input, List<Notification> errors) {
            return parse(new InputStreamReader(input, StandardCharsets.UTF_8), errors);
        }

        /**
         * Parses data provided by an input reader using Xtext and returns the root node of the resulting
         * object tree.
         *
         * @param reader Input reader
         * @return root object node
         * @throws IOException when errors occur during the parsing process
         */
        public T parse(Reader reader, List<Notification> errors) {
            IParseResult result = parser.parse(reader);
            for (var error : result.getSyntaxErrors()) {

            }
            return (T) result.getRootASTElement();
        }
    }

}
