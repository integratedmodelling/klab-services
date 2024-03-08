package org.integratedmodelling.klab.services.resources.storage;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.knowledge.WorldviewImpl;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.assets.WorkspaceImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.lang.WorldviewValidationScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.observable.ConceptExpression;
import org.integratedmodelling.languages.observable.ObservableSemantics;
import org.integratedmodelling.languages.observable.ObservableSequence;
import org.integratedmodelling.languages.observation.Strategies;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the loading of
 * the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {

    private final ServiceStartupOptions startupOptions;
    /**
     * Default interval to check for changes in Git (15 minutes in milliseconds)
     */
    private int DEFAULT_GIT_SYNC_INTERVAL_MINUTES = 15;

    private AtomicBoolean loading = new AtomicBoolean(false);
    private List<Pair<ProjectStorage, Project>> _projectLoadOrder;
    private List<KimOntology> _ontologyOrder;
    private Map<String, KimOntology> _ontologyMap;
    private List<KimNamespace> _namespaceOrder;
    private Map<String, KimNamespace> _namespaceMap;
    private List<KActorsBehavior> _behaviorOrder;
    private Map<String, KActorsBehavior> _behaviorMap;
    private List<KimOntology> _worldviewOntologies;
    private List<KimObservationStrategy> _observationStrategies;
    private List<KimObservationStrategyDocument> _observationStrategyDocuments;
    private Map<String, KimObservationStrategyDocument> _observationStrategyDocumentMap;
    // all docs that have been loaded through a URL remember the URL keyed by the document URN. No
    // guarantee that all URLs correspond to a document in the current catalogs.
    private Map<String, URL> documentURLs = new HashMap<>();
    private WorldviewImpl _worldview;
    //
    private AtomicBoolean consistent = new AtomicBoolean(true);

    // filled in at boot and maintained when changes happen
    private WorldviewValidationScope languageValidationScope;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * This includes the non-local projects, in load order
     *
     * @return
     */
    public List<Project> getProjects() {
        return new ArrayList<>(projects.values());
    }

    public KimConcept.Descriptor describeConcept(String conceptUrn) {
        try {
            String[] split = conceptUrn.split(":");
            var ontology = getOntology(split[0]);
            if (ontology != null) {
                // we don't cache the concept map, so this is a potentially expensive operation and its
                // results should be cached.
                var declaration = conceptMap(ontology).get(split[1]);
                if (declaration != null) {
                    var type = EnumSet.copyOf(declaration.getType());
                    type.retainAll(SemanticType.DECLARABLE_TYPES);
                    return new KimConcept.Descriptor(declaration.getNamespace(), declaration.getUrn(),
                            type.size() == 1 ? type.iterator().next() : SemanticType.NOTHING,
                            declaration.getMetadata().get(Metadata.DC_COMMENT, "No description provided"),
                            declaration.getMetadata().get(Metadata.DC_LABEL,
                                    ontology.getUrn() + ":" + declaration.getUrn()),
                            declaration.isAbstract());
                }
            }
        } catch (Throwable throwable) {
            // just return null
            scope.error(throwable);
        }
        return null;
    }

    private Map<String, KimConceptStatement> conceptMap(KimOntology ontology) {
        Map<String, KimConceptStatement> ret = new HashMap<>();
        for (var conceptStatement : ontology.getStatements()) {
            collectConcepts(conceptStatement, ret);
        }
        return ret;
    }

    private void collectConcepts(KimConceptStatement conceptStatement, Map<String, KimConceptStatement> ret) {
        ret.put(conceptStatement.getUrn(), conceptStatement);
        for (var child : conceptStatement.getChildren()) {
            collectConcepts(child, ret);
        }
    }

    /**
     * Execute the passed operation as an atomic unit, handling any issue. All workspace-modifying operations
     * called after initialization should be wrapped in this.
     *
     * @param runnable
     */
    private synchronized void atomicOperation(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            scope.error(throwable, Klab.ErrorContext.RESOURCES_SERVICE, Klab.ErrorCode.INTERNAL_ERROR);
        }
    }


    public Collection<String> getNamespaceUrns() {
        return _namespaceMap.keySet();
    }

    public Collection<String> getBehaviorUrns() {
        return _behaviorMap.keySet();
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
        public ObservationStrategiesSyntax parseStrategies(URL strategyUrl, String projectName) {

            List<Notification> errors = new ArrayList<>();

            try (var input = strategyUrl.openStream()) {
                var result = parse(input, errors);

                if (!errors.isEmpty()) {
                    for (var error : errors) {
                        scope.error("Observation strategy resource has errors: " + strategyUrl,
                                Klab.ErrorCode.RESOURCE_VALIDATION, Klab.ErrorContext.OBSERVATION_STRATEGY);
                    }
                    return null;
                }

                if (result instanceof Strategies strategies) {
                    String strategyUrn = projectName + "." + Utils.URLs.getURLBaseName(strategyUrl) +
                            ".strategies";
                    return new ObservationStrategiesSyntaxImpl(strategyUrn, strategies,
                            languageValidationScope) {

                        @Override
                        protected void logWarning(ParsedObject target, EObject object,
                                                  EStructuralFeature feature, String message) {
                            getNotifications().add(new Notification(object,
                                    new LanguageValidationScope.ValidationMessage(message, -1,
                                            LanguageValidationScope.Level.WARNING)));
                        }

                        @Override
                        protected void logError(ParsedObject target, EObject object,
                                                EStructuralFeature feature, String message) {
                            getNotifications().add(new Notification(object,
                                    new LanguageValidationScope.ValidationMessage(message, -1,
                                            LanguageValidationScope.Level.ERROR)));
                        }
                    };
                }
            } catch (IOException e) {
                scope.error("Error loading observation strategy " + strategyUrl, Klab.ErrorCode.READ_FAILED
                        , Klab.ErrorContext.OBSERVATION_STRATEGY);
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
        // if not external, this will be not null iif the project is a git repository
        Git repository;
        // Update interval in minutes, from configuration
        int updateInterval;
        // TODO permissions

    }

    private Map<String, WorkspaceImpl> workspaces = new LinkedHashMap<>();
    private final Function<String, Project> externalProjectResolver;
    private Map<String, ProjectDescriptor> projectDescriptors = new HashMap<>();
    private Map<String, Project> projects = new LinkedHashMap<>();
    // all logging goes through here
    private Scope scope;
    private ResourcesConfiguration configuration;
    private Map<String, Long> lastProjectUpdates = new HashMap<>();
    private List<Pair<String, Version>> unresolvedProjects = new ArrayList<>();

    public WorkspaceManager(Scope scope, ServiceStartupOptions options,
                            Function<String, Project> externalProjectResolver) {
        this.externalProjectResolver = externalProjectResolver;
        this.scope = scope;
        this.startupOptions = options;
        readConfiguration(options);
        scheduler.scheduleAtFixedRate(() -> checkForProjectUpdates(), 1, 1, TimeUnit.MINUTES);
    }

    private void checkForProjectUpdates() {

        synchronized (projectDescriptors) {
            for (var pd : projectDescriptors.values()) {
                // configured interval == 0 disables update
                if (pd.repository != null && pd.updateInterval > 0) {
                    var now = System.currentTimeMillis();
                    var timeToUpdate = lastProjectUpdates.containsKey(pd.name) ?
                                       lastProjectUpdates.get(pd.name) + (pd.updateInterval * 1000 * 60) :
                                       now;
                    if (timeToUpdate <= now) {
                        Thread.ofVirtual().start(() -> checkForProjectUpdates(pd));
                        lastProjectUpdates.put(pd.name, now);
                    }
                }
            }
        }
    }

    private void checkForProjectUpdates(ProjectDescriptor projectDescriptor) {
        // TODO fetch changes and react as configured; if anything must be reloaded, lock the workspace
        scope.info("Checking for updates in " + projectDescriptor.name + ", scheduled each " + projectDescriptor.updateInterval + " minutes");
    }

    private void readConfiguration(ServiceStartupOptions options) {
        File config = BaseService.getFileInConfigurationDirectory(KlabService.Type.RESOURCES, options,
                "resources.yaml");
        if (config.exists() && config.length() > 0 && !options.isClean()) {
            this.configuration = org.integratedmodelling.common.utils.Utils.YAML.load(config,
                    ResourcesConfiguration.class);
        } else {
            // make an empty config
            this.configuration = new ResourcesConfiguration();
            this.configuration.setServicePath("resources");
            this.configuration.setLocalResourcePath("local");
            this.configuration.setPublicResourcePath("public");
            this.configuration.setServiceId(UUID.randomUUID().toString());
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
                    descriptor.updateInterval = projectConfiguration.getSyncIntervalMinutes();
                    if (projectStorage instanceof FileProjectStorage fileProjectStorage) {
                        var repository = fileProjectStorage.getRepository();
                        if (repository != null) {
                            descriptor.repository = new Git(repository);
                        }
                    }
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
            _ontologyMap = new HashMap<>();

            this.languageValidationScope = new WorldviewValidationScope();

            Map<String, String> ontologyProjects = new HashMap<>();
            Map<String, Triple<Ontology, KimOntology, Boolean>> cache = new HashMap<>();
            Map<String, URL> urlCache = new HashMap<>();
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
                                //                                return Collections.emptyList();
                            }
                            urlCache.put(parsed.getNamespace().getName(), ontologyUrl);
                            ontologyProjects.put(parsed.getNamespace().getName(), pd.name);
                            cache.put(parsed.getNamespace().getName(), Triple.of(parsed, null, isWorldview));
                        } catch (IOException e) {
                            // log error and return failure
                            scope.error("Error loading ontology " + ontologyUrl, Klab.ErrorCode.READ_FAILED
                                    , Klab.ErrorContext.ONTOLOGY);
                            //                            return Collections.emptyList();
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
                    scope.error("Ontology " + ontologyId + " cannot be resolved either locally or through" + " the network", Klab.ErrorCode.UNRESOLVED_REFERENCE, Klab.ErrorContext.ONTOLOGY);
                    return Collections.emptyList();
                }
                AtomicBoolean errors = new AtomicBoolean(false);
                List<Notification> notifications = new ArrayList<>();
                var ontology = od.getSecond();
                if (ontology == null) {
                    var syntax = new OntologySyntaxImpl(od.getFirst(), languageValidationScope) {

                        @Override
                        protected void logWarning(ParsedObject target, EObject object,
                                                  EStructuralFeature feature, String message) {
                            notifications.add(makeNotification(target, object, feature, message,
                                    org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning));

                        }

                        @Override
                        protected void logError(ParsedObject target, EObject object,
                                                EStructuralFeature feature, String message) {
                            notifications.add(makeNotification(target, object, feature, message,
                                    org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
                            errors.set(true);
                        }
                    };
                    ontology = LanguageAdapter.INSTANCE.adaptOntology(syntax,
                            ontologyProjects.get(syntax.getName()), notifications);
                    documentURLs.put(ontology.getUrn(), urlCache.get(ontology.getUrn()));
                }

                if (errors.get()) {
                    scope.error("Logical errors in ontology " + ontologyId + ": cannot continue",
                            Klab.ErrorCode.RESOURCE_VALIDATION, Klab.ErrorContext.ONTOLOGY);
                    //                    return Collections.emptyList();
                }

                languageValidationScope.addNamespace(ontology);

                this._ontologyOrder.add(ontology);
                this._ontologyMap.put(ontology.getUrn(), ontology);
                if (od.getThird()) {
                    this._worldviewOntologies.add(ontology);
                }
            }
        }

        return worldviewOnly ? _worldviewOntologies : _ontologyOrder;
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
            _namespaceMap = new HashMap<>();
            // TODO load them from all projects in dependency order, same as ontologies; fill in the URL
            //  cache and everything
        }
        return _namespaceOrder;
    }

    public ResourcesConfiguration getConfiguration() {
        return this.configuration;
    }

    List<KActorsBehavior> getBehaviors() {
        if (_behaviorOrder == null) {
            _behaviorOrder = new ArrayList<>();
            _behaviorMap = new HashMap<>();
            // TODO load them from all projects in dependency order, same as ontologies; fill in the URL
            //  cache and everything
        }
        return _behaviorOrder;
    }

    List<KimObservationStrategyDocument> getStrategyDocuments() {
        if (_observationStrategyDocuments == null) {
            _observationStrategyDocuments = new ArrayList<>();
            _observationStrategyDocumentMap = new HashMap<>();
            // TODO load them from all projects in dependency order, same as ontologies; fill in the URL
            //  cache and everything
        }
        return _observationStrategyDocuments;
    }

    public List<String> getWorkspaceURNs() {
        return new ArrayList<>(workspaces.keySet());
    }

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
            configuration.setSourceUrl(projectUrl);
            configuration.setWorkspaceName(workspaceName);
            configuration.setSyncIntervalMinutes(DEFAULT_GIT_SYNC_INTERVAL_MINUTES);
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
        this._ontologyMap = null;
        this._namespaceMap = null;
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

                File workspace = BaseService.getConfigurationSubdirectory(KlabService.Type.RESOURCES,
                        startupOptions, "workspaces");

                try {

                    /**
                     * This should be the main use of project resources. TODO handle credentials
                     */

                    projectName = Utils.Git.clone(projectUrl, workspace, false);

                    ProjectStorage project = importProject(projectName, workspaceName);
                    File ws = new File(workspace + File.separator + projectName);
                    if (ws.exists()) {
                        ret = new FileProjectStorage(ws, this::handleFileChange);
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
                    ret = new FileProjectStorage(file, this::handleFileChange);
                } else if (Utils.Files.JAVA_ARCHIVE_EXTENSIONS.contains(Utils.Files.getFileExtension(file))) {
                    // TODO ret = read from archive
                }
            }
        } finally {
            //            updateLock.writeLock().unlock();
        }
        return ret;
    }

    private void handleFileChange(String project, ProjectStorage.ResourceType type, CRUDOperation change,
                                  URL url) {

        if (loading.get()) {
            return;
        }

        /*
        populate the resource set changes in order of workspace affected
         */
        Map<String, ResourceSet> result = new LinkedHashMap<>();

        if (change == CRUDOperation.DELETE) {
            // TODO
        } else if (change == CRUDOperation.CREATE) {
            // TODO
        } else {

            var projectDescriptor = projectDescriptors.get(project);

            /*
            figure out which asset is affected and load it
             */
            KlabDocument<?> newAsset = switch (type) {
                case ONTOLOGY -> loadOntology(url, project);
                case MODEL_NAMESPACE -> loadNamespace(url, project);
                case BEHAVIOR -> loadBehavior(url, project);
                case STRATEGY -> loadStrategy(url, project);
                default -> null;
            };


            if (newAsset != null) {

                KlabDocument<?> oldAsset = switch (newAsset) {
                    case KimOntology ontology -> getOntology(ontology.getUrn());
                    case KimNamespace namespace -> getNamespace(namespace.getUrn());
                    case KActorsBehavior behavior -> getBehavior(behavior.getUrn());
                    case KimObservationStrategyDocument strategy -> getStrategyDocument(strategy.getUrn());
                    default -> null;
                };

                if (oldAsset == null) {
                    scope.error("Internal: cannot update a non-existing document: " + url);
                    return;
                }

                /*
                if the implicit or explicit import statements have changed, the full order of loading must be
                recomputed.
                 */
                var mustRecomputeOrder =
                        !newAsset.importedNamespaces(false).equals(oldAsset.importedNamespaces(false));

                /*
                establish what needs to be reloaded and which workspaces are affected: dry run across
                ontologies (if the asset is an ontology), then strategies, namespaces and behaviors. First
                establish the affected ones and compile the result sets per workspace. Then send those and
                start the loading based on the collected metadata in the sets.
                */
                Set<String> affectedOntologies = new HashSet<>();
                affectedOntologies.add(oldAsset.getUrn());
                for (var ontology : getOntologies(false)) {
                    if (Sets.intersection(affectedOntologies, ontology.importedNamespaces(false)).size() > 0) {
                        affectedOntologies.add(ontology.getUrn());
                    }
                }

                Set<String> affectedNamespaces = new HashSet<>(affectedOntologies);
                for (var namespace : getNamespaces()) {
                    if (Sets.intersection(affectedNamespaces, namespace.importedNamespaces(false)).size() > 0) {
                        affectedOntologies.add(namespace.getUrn());
                    }
                }
                affectedNamespaces.removeAll(affectedOntologies);

                // same for strategies and behaviors
                Set<String> affectedBehaviors = new HashSet<>(affectedOntologies);
                for (var behavior : getBehaviors()) {
                    if (Sets.intersection(affectedBehaviors, behavior.importedNamespaces(false)).size() > 0) {
                        affectedBehaviors.add(behavior.getUrn());
                    }
                }
                affectedBehaviors.removeAll(affectedOntologies);

                Set<String> affectedStrategies = new HashSet<>(affectedOntologies);
                for (var strategies : getStrategyDocuments()) {
                    if (!Sets.intersection(affectedStrategies, strategies.importedNamespaces(false)).isEmpty()) {
                        affectedStrategies.add(strategies.getUrn());
                    }
                }
                affectedStrategies.removeAll(affectedOntologies);

                this.loading.set(true);

                /*
                make the actual change. For each modification: if
                it's the modified object, reset the corresponding concept descriptors in the language
                validator (if an ontology) or the kbox for the namespace. Then reload and substitute in the
                ontology, worldview and namespace arrays for the modified and the affected in the order
                specified by the resourcesets.
                 */
                var worldviewChange = checkForWorldviewChanges(newAsset, type);
                if (worldviewChange != null) {
                    result.put("Worldview", worldviewChange);
                }

                /*
                 If dependency statements have changed in the modified file, a NEW order of everything is
                 computed. The result set contains all the affected files IN THE NEW ORDER. The order
                 can stay the same if the dependency statements haven't changed between the old and the new
                 version.
                 */
                if (mustRecomputeOrder) {
                    computeLoadOrder();
                }


                /*
                compile the ResourceSets based on the (possibly new) order
                 */
                for (var ontology : getOntologies(false)) {
                    if (affectedOntologies.contains(ontology.getUrn())) {
                        addToResultSet(ontology, Workspace.EXTERNAL_WORKSPACE_URN, result);
                    }
                }
                for (var namespace : getNamespaces()) {
                    if (affectedNamespaces.contains(namespace.getUrn())) {
                        addToResultSet(namespace, Workspace.EXTERNAL_WORKSPACE_URN, result);
                    }
                }
                for (var behavior : getBehaviors()) {
                    if (affectedBehaviors.contains(behavior.getUrn())) {
                        addToResultSet(behavior, Workspace.EXTERNAL_WORKSPACE_URN, result);
                    }
                }
                for (var strategies : getStrategyDocuments()) {
                    if (affectedStrategies.contains(strategies.getUrn())) {
                        addToResultSet(strategies, Workspace.EXTERNAL_WORKSPACE_URN, result);
                    }
                }

                /*
                TODO reload all the affected namespaces from their source, including the language validator
                  and kbox, using the
                 possibly new order. External namespaces that depend on anything that has changed should
                 probably cause a warning.
                 */
                List<KlabDocument<?>> newDocuments = new ArrayList<>();

                for (KimOntology oldOntology : _ontologyOrder) {
                    if (affectedOntologies.contains(oldOntology.getUrn())) {

                        boolean isWorldview =
                                _worldviewOntologies.stream().anyMatch(o -> newAsset.getUrn().equals(o.getUrn()));

                        this.languageValidationScope.clearNamespace(newAsset.getUrn());
                        var newOntology = oldOntology.getUrn().equals(newAsset.getUrn()) ? newAsset :
                                          loadOntology(documentURLs.get(oldOntology.getUrn()),
                                                  oldOntology.getProjectName());
                        this.languageValidationScope.addNamespace((KimOntology) newAsset);
                        newDocuments.add(newOntology);
                    }
                }
                for (var oldNamespace : _namespaceOrder) {
                    if (affectedNamespaces.contains(oldNamespace.getUrn())) {
                        newDocuments.add(oldNamespace.getUrn().equals(newAsset.getUrn()) ? newAsset :
                                         loadNamespace(documentURLs.get(oldNamespace.getUrn()),
                                                 oldNamespace.getProjectName()));
                    }
                }
                for (var oldBehavior : _behaviorOrder) {
                    if (affectedBehaviors.contains(oldBehavior.getUrn())) {
                        newDocuments.add(oldBehavior.getUrn().equals(newAsset.getUrn()) ? newAsset :
                                         loadBehavior(documentURLs.get(oldBehavior.getUrn()),
                                                 oldBehavior.getProjectName()));
                    }
                }
                for (var oldStrategy : _observationStrategyDocuments) {
                    if (affectedStrategies.contains(oldStrategy.getUrn())) {
                        newDocuments.add(oldStrategy.getUrn().equals(newAsset.getUrn()) ? newAsset :
                                         loadStrategy(documentURLs.get(oldStrategy.getUrn()),
                                                 oldStrategy.getProjectName()));
                    }
                }

                for (var document : newDocuments) {
                    switch (document) {
                        case KimOntology ontology -> {
                            if (_worldviewOntologies.stream().anyMatch(o -> newAsset.getUrn().equals(o.getUrn()))) {
                                _worldviewOntologies =
                                        _worldviewOntologies.stream().map(o -> o.getUrn().equals(document.getUrn()) ?
                                                                               ontology : o).collect(toList());
                                _worldview.setOntologies(_worldviewOntologies);
                            }
                            _ontologyOrder =
                                    _ontologyOrder.stream().map(o -> o.getUrn().equals(document.getUrn()) ?
                                                                     ontology : o).collect(toList());
                            _ontologyMap.put(ontology.getUrn(), ontology);

                        }
                        case KimNamespace namespace -> {
                            _namespaceOrder =
                                    _namespaceOrder.stream().map(o -> o.getUrn().equals(document.getUrn()) ?
                                                                      namespace : o).collect(toList());
                            _namespaceMap.put(namespace.getUrn(), namespace);
                        }
                        case KActorsBehavior behavior -> {
                            _behaviorOrder =
                                    _behaviorOrder.stream().map(o -> o.getUrn().equals(document.getUrn()) ?
                                                                     behavior : o).collect(toList());
                            _behaviorMap.put(behavior.getUrn(), behavior);
                        }
                        case KimObservationStrategyDocument strategies -> {
                            _observationStrategyDocuments =
                                    _observationStrategyDocuments.stream().map(o -> o.getUrn().equals(document.getUrn()) ?
                                                                                    strategies :
                                                                                    o).collect(toList());
                            _observationStrategyDocumentMap.put(strategies.getUrn(), strategies);
                            _worldview.setObservationStrategies(_observationStrategyDocuments);
                        }
                        default -> throw new KlabIllegalStateException("can't deal with " + document);
                    }
                }

                this.loading.set(false);

            } else {
                // TODO report failure notification
            }

            /**
             * Report any changes in the project repository if there is one
             */
            var pd = projectDescriptors.get(project);
            if (pd != null && pd.repository != null) {
                try {
                    var status = pd.repository.status().call();
                    for (var changed : status.getModified()) {
                        System.out.println("Modified " + changed);
                    }
                } catch (GitAPIException e) {
                    scope.error(e);
                }
            }

            /**
             Report a ResourceSet per workspace affected. The listening end(s) will have to request the
             contents.
             */
            for (var resourceSet : result.values()) {
                scope.send(Message.MessageClass.ResourceLifecycle, Message.MessageType.WorkspaceChanged
                        , resourceSet);
            }

        }
    }

    /**
     * Substitute the document in the respective lists and containers. If this is an ontology and the
     * worldview contains it, substitute it in the worldview only if there are no errors, otherwise remove it.
     * In all other cases it goes in with any errors it may contain.
     * <p>
     * If the document is an ontology, recompute all concept descriptors in the language validator.
     * <p>
     * if it's an ontology and it's part of the worldview, the worldview must become empty if it has errors.
     * In all situations the worldview resource must be in the ResourceSet to message that it must be
     * reloaded.
     *
     * @param newAsset
     * @param type
     * @return
     */
    private ResourceSet checkForWorldviewChanges(KlabDocument<?> newAsset, ProjectStorage.ResourceType type) {

        ResourceSet ret = null;

        boolean isWorldview = type == ProjectStorage.ResourceType.ONTOLOGY &&
                _worldviewOntologies.stream().anyMatch(ontology -> newAsset.getUrn().equals(ontology.getUrn()));

        if (isWorldview) {
            ret = new ResourceSet();
            ret.setWorkspace(Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER);
            var resource = new ResourceSet.Resource();
            resource.setKnowledgeClass(KlabAsset.KnowledgeClass.WORLDVIEW);
            ret.getResources().add(resource);
            if (Utils.Notifications.hasErrors(newAsset.getNotifications())) {
                ret.setEmpty(true);
                ret.getNotifications().addAll(newAsset.getNotifications());
            }
        }

        return ret;
    }

    /**
     * Add the document info to the result set that corresponds to the passed workspace in the passed result
     * map, creating whatever is needed. If the external workspace name is given, use that for an external
     * document, otherwise skip it.
     *
     * @param ontology
     * @param result
     */
    private void addToResultSet(KlabDocument<?> ontology, String externalWorkspaceId, Map<String,
            ResourceSet> result) {

        String workspace = getWorkspaceForProject(ontology.getProjectName());
        if (workspace == null) workspace = externalWorkspaceId;

        if (workspace != null) {

            ResourceSet resourceSet = result.get(workspace);
            if (resourceSet == null) {
                resourceSet = new ResourceSet();
                resourceSet.setWorkspace(workspace);
                result.put(workspace, resourceSet);
            }

            ResourceSet.Resource resource = new ResourceSet.Resource();
            resource.setResourceUrn(ontology.getUrn());
            resource.setResourceVersion(ontology.getVersion());
            resource.setServiceId(configuration.getServicePath());
            resource.setKnowledgeClass(KlabAsset.classify(ontology));

            switch (resource.getKnowledgeClass()) {
                case RESOURCE -> {
                    // TODO
                }
                case NAMESPACE -> {
                    resourceSet.getNamespaces().add(resource);
                }
                case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> {
                    resourceSet.getBehaviors().add(resource);
                }
                case ONTOLOGY -> {
                    resourceSet.getOntologies().add(resource);
                }
                case OBSERVATION_STRATEGY_DOCUMENT -> {
                    resourceSet.getObservationStrategies().add(resource);
                }
            }

            resourceSet.getResources().add(resource);

        }
    }

    /**
     * Recompute from scratch the order of all known ontologies, namespaces, behaviors, strategies and
     * projects
     */
    private void computeLoadOrder() {
        sortDocuments(_ontologyOrder, Klab.ErrorContext.ONTOLOGY);
        sortDocuments(_namespaceOrder, Klab.ErrorContext.NAMESPACE);
        sortDocuments(_behaviorOrder, Klab.ErrorContext.BEHAVIOR);
    }

    private <T extends KlabDocument<?>> void sortDocuments(List<T> documents,
                                                           Klab.ErrorContext errorContext) {

        Graph<String, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<String, T> documentMap = new HashMap<>();
        for (var document : documents) {
            documentMap.put(document.getUrn(), document);
            dependencyGraph.addVertex(document.getUrn());
            for (var imported : document.importedNamespaces(true)) {
                dependencyGraph.addVertex(imported);
                dependencyGraph.addEdge(imported, document.getUrn());
            }
        }

        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(dependencyGraph);
        if (cycleDetector.detectCycles()) {
            scope.error("Circular dependencies in workspace: cannot continue. Cyclic dependencies affect " + cycleDetector.findCycles(),
                    Klab.ErrorCode.CIRCULAR_REFERENCES, errorContext);
            return;
        }

        // finish building the ontologies in the given order using a new language validator
        documents.clear();
        TopologicalOrderIterator<String, DefaultEdge> sort =
                new TopologicalOrderIterator<>(dependencyGraph);
        while (sort.hasNext()) {
            documents.add(documentMap.get(sort.next()));
        }
    }

    /**
     * Return the nzme of the local workspace that hosts the passed project, or null.
     *
     * @param projectName
     * @return
     */
    public String getWorkspaceForProject(String projectName) {
        var pd = projectDescriptors.get(projectName);
        return pd == null ? null : pd.workspace;
    }

    public KimOntology getOntology(String urn) {
        return _ontologyMap.get(urn);
    }

    public KimNamespace getNamespace(String urn) {
        return _namespaceMap.get(urn);
    }

    public KActorsBehavior getBehavior(String urn) {
        return null; // TODO _ontologyMap.get(urn);
    }

    public KimObservationStrategyDocument getStrategyDocument(String urn) {
        return null; // _ontologyMap.get(urn);
    }

    private KimOntology loadOntology(URL url, String project) {
        try (var input = url.openStream()) {
            List<Notification> notifications = new ArrayList<>();
            var parsed = ontologyParser.parse(input, notifications);
            var syntax = new OntologySyntaxImpl(parsed, languageValidationScope) {

                @Override
                protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature,
                                          String message) {
                    notifications.add(makeNotification(target, object, feature, message,
                            org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning));
                }

                @Override
                protected void logError(ParsedObject target, EObject object, EStructuralFeature feature,
                                        String message) {
                    notifications.add(makeNotification(target, object, feature, message,
                            org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
                }
            };
            return LanguageAdapter.INSTANCE.adaptOntology(syntax, project, notifications);
        } catch (IOException e) {
            scope.error(e);
            return null;
        }
    }

    private KimNamespace loadNamespace(URL url, String project) {
        //        try (var input = url.openStream()) {
        //            List<Notification> notifications = new ArrayList<>();
        //            var parsed = namespaceParser.parse(input, notifications);
        //            var syntax = new NamespaceSyntaxImpl(parsed, languageValidationScope) {
        //
        //                @Override
        //                protected void logWarning(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                          String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Warning));
        //                }
        //
        //                @Override
        //                protected void logError(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                        String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Error));
        //                }
        //            };
        //            return LanguageAdapter.INSTANCE.adaptNamespace(syntax, project, notifications);
        //        } catch (IOException e) {
        //            scope.error(e);
        return null;
        //        }
    }

    private KActorsBehavior loadBehavior(URL url, String project) {
        //        try (var input = url.openStream()) {
        //            List<Notification> notifications = new ArrayList<>();
        //            var parsed = behaviorParser.parse(input, notifications);
        //            var syntax = new KActorsBehaviorImpl(parsed, languageValidationScope) {
        //
        //                @Override
        //                protected void logWarning(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                          String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Warning));
        //                }
        //
        //                @Override
        //                protected void logError(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                        String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Error));
        //                }
        //            };
        //            return LanguageAdapter.INSTANCE.adaptBehavior(syntax, project, notifications);
        //        } catch (IOException e) {
        //            scope.error(e);
        return null;
        //        }
    }


    private KimObservationStrategyDocument loadStrategy(URL url, String project) {
        //        try (var input = url.openStream()) {
        //            List<Notification> notifications = new ArrayList<>();
        //            var parsed = behaviorParser.parse(input, notifications);
        //            var syntax = new KActorsBehaviorImpl(parsed, languageValidationScope) {
        //
        //                @Override
        //                protected void logWarning(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                          String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Warning));
        //                }
        //
        //                @Override
        //                protected void logError(ParsedObject target, EObject object, EStructuralFeature
        //                feature,
        //                                        String message) {
        //                    notifications.add(makeNotification(target, object, feature, message,
        //                            org.integratedmodelling.klab.api.services.runtime.Notification.Level
        //                            .Error));
        //                }
        //            };
        //            return LanguageAdapter.INSTANCE.adaptBehavior(syntax, project, notifications);
        //        } catch (IOException e) {
        //            scope.error(e);
        return null;
        //        }
    }

    private Notification makeNotification(ParsedObject target, EObject object, EStructuralFeature feature,
                                          String message, Notification.Level level) {
        var ret = new NotificationImpl();
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
    public synchronized boolean loadWorkspace() {

        /*
        TODO wait until this.loading.get() is false! Could be straight in here or we could just use this
         from an operation queue. API admin ops and retrievals should also ensure that they only return
         when not loading.

         Use this pattern

        if(lock.compareAndSet(false, true)){
        try {
            //do
           } catch(Exception e){
            //error handling
           } finally {
             lock.set(false);
          }
        }
         */

        this.loading.set(true);

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
                    "configuration have cyclic dependencies on each other: " + "will not " + "proceed. " +
                    "Review configuration");
            this.loading.set(false);
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
                        scope.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.MISMATCHED_VERSION, "Project "
                                + proj.getFirst() + "@" + proj.getSecond() + " is required" + " by other " +
                                "projects in workspace but incompatible version " + pd.manifest.getVersion() +
                                " is available in local workspace");
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
                                            "version " + externalProject.getManifest().getVersion() + " is "
                                            + "available " + "externally");
                            unresolvedProjects.add(proj);
                        }
                    } else {
                        scope.error(Klab.ErrorContext.PROJECT, Klab.ErrorCode.UNRESOLVED_REFERENCE,
                                "Project " + proj.getFirst() + "@" + proj.getSecond() + " is required" + " "
                                        + "by other projects in workspace but cannot be resolved from the " +
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

        this.loading.set(false);

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
                        var parsed = strategyParser.parseStrategies(strategyUrl, pd.name);
                        if (parsed == null) {
                            _worldview.setEmpty(true);
                            return _worldview;
                        }
                        _worldview.getObservationStrategies().add(LanguageAdapter.INSTANCE.adaptStrategies(parsed));
                    }
                }
            }
        }

            /*
            Validate the first ontology as the root ontology and set the worldview name from it
             */
        if (_worldview.getOntologies().size() > 0) {

            for (var ontology : _worldview.getOntologies()) {
                if (Utils.Notifications.hasErrors(ontology.getNotifications())) {
                    _worldview.setEmpty(true);
                    scope.error("Namespace " + ontology.getUrn() + " has fatal errors: worldview " +
                            "is inconsistent");
                }
            }

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
        File config = BaseService.getFileInConfigurationDirectory(KlabService.Type.RESOURCES,
                startupOptions, "resources.yaml");
        org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
    }


    public void updateOntology(String projectName, String ontologyContent) {

        var pd = projectDescriptors.get(projectName);
        if (pd == null || !(pd.storage instanceof FileProjectStorage)) {
            throw new KlabIllegalStateException("Cannot update an ontology that is not stored on the " +
                    "service's filesystem");
        }
        /*
        file storage: modify as specified
         */
        List<Notification> notifications = new ArrayList<>();
        var parsed = ontologyParser.parse(new StringReader(ontologyContent), notifications);

        // do the update in the stored project and screw it
        ((FileProjectStorage) pd.storage).update(ProjectStorage.ResourceType.ONTOLOGY,
                parsed.getNamespace().getName(), ontologyContent);

    }

    public void updateNamespace(String projectName, String ontologyContent) {

        var pd = projectDescriptors.get(projectName);
        if (pd == null || !(pd.storage instanceof FileProjectStorage)) {
            throw new KlabIllegalStateException("Cannot update an ontology that is not stored on the " +
                    "service's filesystem");
        }
        /*
        file storage: modify as specified
         */
        List<Notification> notifications = new ArrayList<>();
        var parsed = namespaceParser.parse(new StringReader(ontologyContent), notifications);

        // do the update in the stored project and screw it
        ((FileProjectStorage) pd.storage).update(ProjectStorage.ResourceType.MODEL_NAMESPACE,
                parsed.getNamespace().getName(), ontologyContent);

    }

    public void updateBehavior(String projectName, String ontologyContent) {

        var pd = projectDescriptors.get(projectName);
        if (pd == null || !(pd.storage instanceof FileProjectStorage)) {
            throw new KlabIllegalStateException("Cannot update an ontology that is not stored on the " +
                    "service's filesystem");
        }
        /*
        file storage: modify as specified
         */
        List<Notification> notifications = new ArrayList<>();
        //        var parsed = behaviorParser.parse(new StringReader(ontologyContent), notifications);
        //
        //        // do the update in the stored project and screw it
        //        ((FileProjectStorage) pd.storage).update(ProjectStorage.ResourceType.BEHAVIOR,
        //                parsed.getNamespace().getName(), ontologyContent);

    }

    /**
     * Consistent status means that the contents can be trusted 100%. For now, this means that:
     * <p>
     * 1. Changes happened in the workspace did not affect namespaces that the workspace has no control on,
     * like externally imported namespaces.
     * <p>
     * (list will expand)
     *
     * @return
     */
    public boolean isConsistent() {
        return consistent.get();
    }


}
