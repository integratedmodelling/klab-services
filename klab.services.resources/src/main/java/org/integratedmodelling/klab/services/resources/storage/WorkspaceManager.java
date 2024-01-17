package org.integratedmodelling.klab.services.resources.storage;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.knowledge.WorldviewImpl;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.assets.WorkspaceImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.observable.ConceptExpression;
import org.integratedmodelling.languages.observable.ObservableSemantics;
import org.integratedmodelling.languages.observable.ObservableSequence;
import org.integratedmodelling.languages.observation.Strategies;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.languages.worldview.Ontology;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the loading of
 * the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {

    /**
     * Default interval to check for changes in Git (15 minutes in milliseconds)
     */
    private long DEFAULT_GIT_SYNC_INTERVAL = 15L * 60L * 60L * 1000L;

    private List<Pair<ProjectStorage, Project>> _projectLoadOrder;
    private List<KimOntology> _ontologyOrder;
    private List<KimNamespace> _namespaceOrder;
    private List<KimOntology> _worldviewOntologies;
    private WorldviewImpl _worldview;

    private LanguageValidationScope languageValidationScope = new BasicObservableValidationScope() {

        @Override
        public boolean hasReasoner() {
            return scope.getService(Reasoner.class) != null;
        }

        @Override
        public ConceptDescriptor createConceptDescriptor(ConceptDeclarationSyntax declaration) {
            // trust the "is core" to define the type for all core ontology concepts
            if (declaration.isCoreDeclaration()) {
                SemanticSyntax coreConcept = declaration.getDeclaredParent();
                var coreId = coreConcept.encode();
                var cname = coreConcept.encode().split(":");
                this.conceptTypes.put(coreConcept.encode(),
                        new ConceptDescriptor(cname[0], cname[1],
                                declaration.getDeclaredType(), coreConcept.encode(), "Core concept "
                                + coreConcept.encode() + " for type " + declaration.getDeclaredType(), true));
            }
            return super.createConceptDescriptor(declaration);
        }
    };

    /**
     * This includes the non-local projects, in load order
     *
     * @return
     */
    public List<Project> getProjects() {
        return new ArrayList<>(projects.values());
    }

    class StrategyParser extends Parser<Strategies> {

        @Override
        protected Injector createInjector() {
            return new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration();
        }

        /**
         * Parse a concept definition into its syntactic peer, which should be inspected for errors before
         * turning into semantics.
         *
         * @param strategyUrl
         * @return the parsed semantic expression, or null if the parser cannot make sense of it.
         */
        public ObservationStrategiesSyntax parseStrategies(URL strategyUrl) {

            List<Notification> errors = new ArrayList<>();

            try (var input = strategyUrl.openStream()) {
                var result = parse(input, errors);

                if (!errors.isEmpty()) {
                    for (var error : errors) {
                        scope.error("Observation strategy resource has errors: " + strategyUrl,
                                Klab.ErrorCode.RESOURCE_VALIDATION,
                                Klab.ErrorContext.OBSERVATION_STRATEGY);
                    }
                    return null;
                }

                if (result instanceof Strategies) {
                    return new ObservationStrategiesSyntaxImpl((Strategies) result, languageValidationScope) {

                        @Override
                        protected void logWarning(ParsedObject target, EObject object,
                                                  EStructuralFeature feature, String message) {
                            getNotifications().add(new Notification(object,
                                    new LanguageValidationScope.ValidationMessage(message, -1,
                                            LanguageValidationScope.Level.WARNING)));
                        }

                        @Override
                        protected void logError(ParsedObject target, EObject object,
                                                EStructuralFeature feature
                                , String message) {
                            getNotifications().add(new Notification(object,
                                    new LanguageValidationScope.ValidationMessage(message, -1,
                                            LanguageValidationScope.Level.ERROR)));
                        }
                    };
                }
            } catch (IOException e) {
                scope.error("Error loading observation strategy " + strategyUrl,
                        Klab.ErrorCode.READ_FAILED, Klab.ErrorContext.OBSERVATION_STRATEGY);
            }
            return null;
        }
    }

    class ObservableParser extends Parser<ObservableSequence> {

        @Inject
        ObservableGrammarAccess grammarAccess;

        @Override
        protected Injector createInjector() {
            return new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
        }

        /**
         * Parse a concept definition into its syntactic peer, which should be inspected for errors before
         * turning into semantics.
         *
         * @param conceptDefinition
         * @return the parsed semantic expression, or null if the parser cannot make sense of it.
         */
        public SemanticSyntax parseConcept(String conceptDefinition) {
            var result = parser.parse(grammarAccess.getConceptExpressionRule(),
                    new StringReader(conceptDefinition));
            var ret = result.getRootASTElement();
            if (ret instanceof ConceptExpression) {
                return new SemanticSyntaxImpl((ConceptExpression) ret, languageValidationScope) {

                    List<String> errors = new ArrayList<>();

                    @Override
                    protected void logWarning(ParsedObject target, EObject object,
                                              EStructuralFeature feature, String message) {
                        getNotifications().add(new Notification(object,
                                new LanguageValidationScope.ValidationMessage(message, -1,
                                        LanguageValidationScope.Level.WARNING)));
                    }

                    @Override
                    protected void logError(ParsedObject target, EObject object, EStructuralFeature feature
                            , String message) {
                        getNotifications().add(new Notification(object,
                                new LanguageValidationScope.ValidationMessage(message, -1,
                                        LanguageValidationScope.Level.ERROR)));
                    }
                };
            }
            return null;
        }

        /**
         * Parse an observable definition into its syntactic peer, which should be inspected for errors before
         * turning into semantics.
         *
         * @param observableDefinition
         * @return the parsed semantic expression, or null if the parser cannot make sense of it.
         */
        public ObservableSyntax parseObservable(String observableDefinition) {
            var result = parser.parse(grammarAccess.getObservableSemanticsRule(),
                    new StringReader(observableDefinition));
            var ret = result.getRootASTElement();
            if (ret instanceof ObservableSemantics) {
                return new ObservableSyntaxImpl((ObservableSemantics) ret, languageValidationScope) {

                    List<String> errors = new ArrayList<>();

                    @Override
                    protected void logWarning(ParsedObject target, EObject object,
                                              EStructuralFeature feature, String message) {
                        getNotifications().add(new Notification(object,
                                new LanguageValidationScope.ValidationMessage(message, -1,
                                        LanguageValidationScope.Level.WARNING)));
                    }

                    @Override
                    protected void logError(ParsedObject target, EObject object, EStructuralFeature feature
                            , String message) {
                        getNotifications().add(new Notification(object,
                                new LanguageValidationScope.ValidationMessage(message, -1,
                                        LanguageValidationScope.Level.ERROR)));
                    }
                };
            }
            return null;
        }

    }

    private ObservableParser observableParser = new ObservableParser();
    private StrategyParser strategyParser = new StrategyParser();

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
//        // these are loaded on demand, use only through their accessors
//        private Set<String> _namespaceIds;
//        private Set<String> _ontologyIds;
        // TODO permissions

//
//        public Set<String> getNamespaceIds() {
//            if (_namespaceIds == null) {
//
//            }
//            return _namespaceIds;
//        }
//
//        public Set<String> getOntologyIds() {
//            if (_ontologyIds == null) {
//
//            }
//            return _ontologyIds;
//        }
    }

    private Map<String, WorkspaceImpl> workspaces = new LinkedHashMap<>();
    private final Function<String, Project> externalProjectResolver;
    private Map<String, ProjectDescriptor> projectDescriptors = new HashMap<>();
    private Map<String, Project> projects = new LinkedHashMap<>();
    // all logging goes through here
    private Scope scope;
    private ResourcesConfiguration configuration;

    private List<Pair<String, Version>> unresolvedProjects = new ArrayList<>();

    public WorkspaceManager(Scope scope, Function<String, Project> externalProjectResolver) {
        this.externalProjectResolver = externalProjectResolver;
        this.scope = scope;
        readConfiguration();
    }

    private void readConfiguration() {

        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
        if (config.exists() && config.length() > 0) {
            this.configuration = Utils.YAML.load(config, ResourcesConfiguration.class);
        } else {
            // make an empty config
            this.configuration = new ResourcesConfiguration();
            this.configuration.setServicePath("resources");
            this.configuration.setLocalResourcePath("local");
            this.configuration.setPublicResourcePath("public");
            saveConfiguration();
        }

        // clear existing caches (this must be reentrant and be callable again at any new import)
        projectDescriptors.clear();

        // build descriptors for all locally configured projects and workspaces

        for (var workspace : configuration.getWorkspaces().keySet()) {

            // ensure existing
            getWorkspace(workspace);

            for (var project : configuration.getWorkspaces().get(workspace)) {
                var projectConfiguration = configuration.getProjectConfiguration().get(project);
                ProjectStorage projectStorage = loadProject(projectConfiguration.getSourceUrl(), workspace);
                if (projectStorage != null) {

                    // create descriptor
                    ProjectDescriptor descriptor = new ProjectDescriptor();
                    descriptor.storage = projectStorage;
                    descriptor.manifest = readManifest(projectStorage);
                    descriptor.workspace = workspace;
                    descriptor.name = project;
                    projectDescriptors.put(project, descriptor);


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

            _worldviewOntologies = new ArrayList<>();
            _ontologyOrder = new ArrayList<>();

            Map<String, String> ontologyProjects = new HashMap<>();
            Map<String, Triple<Ontology, KimOntology, Boolean>> cache = new HashMap<>();
            for (var pd : projectDescriptors.values()) {
                var isWorldview = pd.manifest.getDefinedWorldview() != null;
                if (pd.externalProject != null) {
                    for (var ontology : pd.externalProject.getOntologies()) {
                        cache.put(ontology.getUrn(), Triple.of(null, ontology, isWorldview));
                        // TODO add metadata to the ontology to signify it's remote, probably a URL
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
                            ontologyProjects.put(parsed.getNamespace().getName(), pd.name);
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
                    ontology = LanguageAdapter.INSTANCE.adaptOntology(syntax,
                            ontologyProjects.get(syntax.getName()));
                    // TODO add ontology local URL to metadata based on service URL
                }

                if (errors.get()) {
                    scope.error("Logical errors in ontology " + ontologyId + ": cannot continue",
                            Klab.ErrorCode.RESOURCE_VALIDATION, Klab.ErrorContext.ONTOLOGY);
                    return Collections.emptyList();
                }

                this._ontologyOrder.add(ontology);
                if (od.getThird()) {
                    this._worldviewOntologies.add(ontology);
                }
            }
        }

        return worldviewOnly ? _worldviewOntologies : _ontologyOrder;
    }

    public ProjectStorage getProject(String projectName) {
        return null;
    }

    /**
     * Return all the namespaces in order of dependency. Resolution is internal like in
     * {@link #getOntologies(boolean)}.
     *
     * @return
     */
    List<KimNamespace> getNamespaces() {
        if (_namespaceOrder == null) {
            _namespaceOrder = new ArrayList<>();
            // TODO load them from all projects in dependency order, same as ontologies
        }
        return _namespaceOrder;
    }

    public List<String> getWorkspaceURNs() {
        return new ArrayList<>(workspaces.keySet());
    }

//    public List<String> getLocalProjectURNs(String workspace) {
//        var ret = new ArrayList<String>();
//        for (var pd : getProjectLoadOrder()) {
//            if (pd.getFirst() != null) {
//                var pdesc = projectDescriptors.get(pd.getFirst().getProjectName());
//                if (pdesc != null /* shouldn't happen */ && pdesc.workspace.equals(workspace)) {
//                    ret.add(pdesc.name);
//                }
//            }
//        }
//        return ret;
//    }

    /**
     * Create the project implementation with every namespace and manifest filled in. CAUTION this can be a
     * large object. The project must exist in a local workspace; if not, null will be returned without
     * error.
     *
     * @param projectId
     * @return the filled in project or null
     */
    public Project createProjectData(String projectId) {

        ProjectImpl ret = null;
        var pdesc = projectDescriptors.get(projectId);
        if (pdesc != null && pdesc.storage != null) {

            ret = new ProjectImpl();
            ret.setUrn(projectId);

            // TODO improve metadata with service IDs, load time, stats, any info etc.
            // TODO should only add a file:/ URL if the project is local to the requester (check scope)
            ret.getMetadata().put(Metadata.RESOURCES_STORAGE_URL, pdesc.storage.getUrl());
            ret.setManifest(pdesc.manifest);

            for (KimOntology ontology : getOntologies(false)) {
                if (projectId.equals(ontology.getProjectName())) {
                    ret.getOntologies().add(ontology);
                }
            }

            for (KimNamespace namespace : getNamespaces()) {
                if (projectId.equals(namespace.getProjectName())) {
                    ret.getNamespaces().add(namespace);
                }
            }

        }
        return ret;
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
        var ret = loadProject(projectUrl, workspaceName);

        if (ret != null) {
            ResourcesConfiguration.ProjectConfiguration configuration =
                    new ResourcesConfiguration.ProjectConfiguration();
//            configuration.setLocalPath(new File(workspaceName + File.separator + projectName));
            configuration.setSourceUrl(projectUrl);
            configuration.setWorkspaceName(workspaceName);
            // FIXME only if git
            configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);
            /*
             * Default privileges are exclusive to the service
             */
            configuration.setPrivileges(ResourcePrivileges.empty());
            // this must happen before loadProject is called.
            this.configuration.getProjectConfiguration().put(ret.getProjectName(), configuration);

            Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
            if (projects == null) {
                projects = new LinkedHashSet<>();
                this.configuration.getWorkspaces().put(workspaceName, projects);
            }
            configuration.setWorldview(readManifest(ret).getDefinedWorldview() != null);
            projects.add(ret.getProjectName());
            saveConfiguration();
        }
        return ret;
    }

    private ProjectStorage loadProject(String projectUrl, String workspaceName) {

        ProjectStorage ret = null;
        this._projectLoadOrder = null;
        this._ontologyOrder = null;
        this._namespaceOrder = null;
        this._worldview = null;

        try {

            String projectName = Utils.URLs.getURLBaseName(projectUrl);
            ResourcesConfiguration.ProjectConfiguration config =
                    this.configuration.getProjectConfiguration().get(projectName);
            if (config != null) {
                // throw exception
            }

            if (Utils.Git.isRemoteGitURL(projectUrl)) {

                File workspace = Configuration.INSTANCE.getDataPath(
                        configuration.getServicePath() + File.separator + "workspaces" + File.separator + workspaceName);
                workspace.mkdirs();

                try {

                    /**
                     * This should be the main use of project resources. TODO handle credentials
                     */

                    projectName = Utils.Git.clone(projectUrl, workspace, false);

                    ProjectStorage project = importProject(projectName, workspaceName);
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
    public boolean loadWorkspace() {

        this._projectLoadOrder = new ArrayList<>();
        this.workspaces.clear();
        this.projects.clear();

        // build a version-aware dependency tree
        Graph<Pair<String, Version>, DefaultEdge> dependencyGraph =
                new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String s : projectDescriptors.keySet()) {
            var snode = Pair.of(s, projectDescriptors.get(s).manifest.getVersion());
            dependencyGraph.addVertex(snode);
            for (var dep : projectDescriptors.get(s).manifest.getPrerequisiteProjects()) {
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
            return false;
        } else {

            // establish load order: a list of either ProjectStorage or external Project

            TopologicalOrderIterator<Pair<String, Version>, DefaultEdge> sort =
                    new TopologicalOrderIterator(dependencyGraph);
            while (sort.hasNext()) {
                var proj = sort.next();
                // verify availability
                if (projectDescriptors.get(proj.getFirst()) != null) {
                    // local dependency: check version
                    var pd = projectDescriptors.get(proj.getFirst());
                    if (pd.manifest.getVersion().compatible(proj.getSecond())) {
                        this._projectLoadOrder.add(Pair.of(pd.storage, null));
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
                            projectDescriptors.put(proj.getFirst(), descriptor);
                            this._projectLoadOrder.add(Pair.of(null, externalProject));
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

        /*
        we have workspaces and project descriptors; load ontologies and namespaces
         */

        getOntologies(false);
        getNamespaces();

        // build workspace and project descriptors and attribute all namespaces
        for (var proj : this._projectLoadOrder) {
            if (proj.getFirst() != null) {
                var pdesc = projectDescriptors.get(proj.getFirst().getProjectName());
                if (pdesc != null && pdesc.storage != null) {
                    WorkspaceImpl ws = null;
                    if (pdesc.workspace != null) {
                        ws = this.workspaces.get(pdesc.workspace);
                        if (ws == null) {
                            ws = new WorkspaceImpl();
                            ws.setUrn(pdesc.workspace);
                            this.workspaces.put(pdesc.workspace, ws);
                        }
                    }
                    var project = createProjectData(pdesc.name);
                    this.projects.put(pdesc.name, project);
                    if (ws != null) {
                        ws.getProjects().add(project);
                    }
                }
            }
        }

        return true;
    }

    private ProjectStorage newProject(String projectName, String workspaceName) {
        return null;
    }

    public SemanticSyntax resolveConcept(String conceptDefinition) {
        return this.observableParser.parseConcept(conceptDefinition);
    }

    public ObservableSyntax resolveObservable(String observableDefinition) {
        return this.observableParser.parseObservable(observableDefinition);
    }

    public boolean removeProject(String projectName) {
        ResourcesConfiguration.ProjectConfiguration configuration =
                this.configuration.getProjectConfiguration().get(projectName);
        var project = this.projectDescriptors.get(projectName);
        if (project != null && project.storage != null) {
            Workspace workspace = getWorkspace(project.workspace);
            Utils.Files.deleteQuietly(configuration.getLocalPath());
            if (this.configuration.getWorkspaces().get(project.workspace) != null) {
                this.configuration.getWorkspaces().get(project.workspace).remove(projectName);
            }
//        // remove namespaces, behaviors and resources
//        for (KimNamespace namespace : project.getNamespaces()) {
//            this.localNamespaces.remove(namespace.getUrn());
//        }
//        for (KActorsBehavior behavior : project.getBehaviors()) {
//            this.localBehaviors.remove(behavior.getUrn());
//        }
//        for (String resource : project.getResourceUrns()) {
//            localResources.remove(resource);
//            catalog.remove(resource);
//        }
//        this.localProjects.remove(projectName);
            workspace.getProjects().remove(project);
            saveConfiguration();
        }
//        db.commit();
        return true;
    }

    private Project.Manifest readManifest(ProjectStorage project) {
        return Utils.Json.load(project.listResources(ProjectStorage.ResourceType.MANIFEST).getFirst(),
                ProjectImpl.ManifestImpl.class);
    }

    public WorkspaceImpl getWorkspace(String workspaceName) {
        return this.workspaces.get(workspaceName);
    }

    public Collection<Workspace> getWorkspaces() {
        List<Workspace> ret = new ArrayList<>();
        for (var wsId : configuration.getWorkspaces().keySet()) {
            var workspace = getWorkspace(wsId);
            ret.add(workspace);
        }
        return ret;
    }

    public List<Pair<String, Version>> getUnresolvedProjects() {
        return unresolvedProjects;
    }


    private abstract class Parser<T extends EObject> {

        @Inject
        protected IParser parser;

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

    public Worldview getWorldview() {

        if (_worldview == null) {

            _worldview = new WorldviewImpl();
            _worldview.getOntologies().addAll(getOntologies(true));
            // basic validations: non-empty, first must be root, take the worldview name from it
            // go back to the projects and load all observation strategies, adding project metadata
            for (var pd : projectDescriptors.values()) {
                if (pd.manifest.getDefinedWorldview() == null) {
                    continue;
                }
                if (pd.externalProject != null) {
                    for (var strategy : pd.externalProject.getObservationStrategies()) {
                        _worldview.getObservationStrategies().add(strategy);
                    }
                } else {
                    for (var strategyUrl : pd.storage.listResources(ProjectStorage.ResourceType.STRATEGY)) {
                        var parsed = strategyParser.parseStrategies(strategyUrl);
                        if (parsed == null) {
                            _worldview.setEmpty(true);
                            return _worldview;
                        }
                        _worldview.getObservationStrategies().addAll(LanguageAdapter.INSTANCE.adaptStrategies(parsed).getStatements());
                    }
                }
            }
        }

            /*
            Validate the first ontology as the root ontology and set the worldview name from it
             */
        if (_worldview.getOntologies().size() > 0) {
            KimOntology root = _worldview.getOntologies().get(0);
            if (!(root.getDomain() == KimOntology.rootDomain)) {
                _worldview.setEmpty(true);
                scope.error("The first namespace in the worldview is not the root namespace: worldview " +
                        "is inconsistent");
            } else {
                _worldview.setUrn(root.getUrn());
            }
        } else {
            _worldview.setEmpty(true);
        }

        return _worldview;
    }

    private void saveConfiguration() {
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
        Utils.YAML.save(this.configuration, config);
    }

}
