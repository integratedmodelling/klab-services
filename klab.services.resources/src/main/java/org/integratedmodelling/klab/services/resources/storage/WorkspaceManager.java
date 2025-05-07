package org.integratedmodelling.klab.services.resources.storage;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.knowledge.ProjectImpl;
import org.integratedmodelling.common.knowledge.WorkspaceImpl;
import org.integratedmodelling.common.knowledge.WorldviewImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.view.UIView;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.lang.WorldviewValidationScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.kActors.Behavior;
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

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the
 * loading of the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {

  private final ServiceStartupOptions startupOptions;
  private final ResourcesProvider service;

  /** Default interval to check for changes in Git (15 minutes in milliseconds) */
  private int DEFAULT_GIT_SYNC_INTERVAL_MINUTES = 15;

  // project locks are mappings usertoken->projectName and enable remote updating of projects for
  // one
  // user at
  // a time, while inhibiting file change logging in project storage
  private Map<String, String> projectLocks = Collections.synchronizedMap(new HashMap<>());
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
  private boolean worldviewProvider = false;
  private String adoptedWorldview;

  /**
   * This includes the non-local projects, in load order
   *
   * @return
   */
  public List<Project> getProjects() {
    var ret = new ArrayList<Project>();
    for (var project : projects.values()) {
      ret.add(updateStatus(project));
    }
    return ret;
  }

  public Project getProject(String projectName) {
    return projects.get(projectName);
  }

  private <T extends KlabAsset> T updateStatus(T container) {

    if (container instanceof Workspace workspace) {
      for (var project : workspace.getProjects()) {
        updateProjectStatus(project.getUrn(), null);
      }
    } else if (container instanceof Project) {
      updateProjectStatus(container.getUrn(), null);
    } else if (container instanceof KlabDocument<?> document) {
      updateProjectStatus(document.getProjectName(), document);
    }

    return container;
  }

  private void updateProjectStatus(String projectId, KlabDocument<?> resource) {
    var pd = projectDescriptors.get(projectId);
    var prj = projects.get(projectId);
    if (pd.storage instanceof FileProjectStorage fps && prj instanceof ProjectImpl pimpl) {
      pimpl.setRepositoryState(fps.getRepositoryState());
      //            fps.updateMetadata(prj, resource, scope);
    }
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
          return new KimConcept.Descriptor(
              declaration.getNamespace(),
              declaration.getUrn(),
              type.size() == 1 ? type.iterator().next() : SemanticType.NOTHING,
              declaration.getMetadata().get(Metadata.DC_COMMENT, "No description provided"),
              declaration
                  .getMetadata()
                  .get(Metadata.DC_LABEL, ontology.getUrn() + ":" + declaration.getUrn()),
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

  private void collectConcepts(
      KimConceptStatement conceptStatement, Map<String, KimConceptStatement> ret) {
    ret.put(conceptStatement.getUrn(), conceptStatement);
    for (var child : conceptStatement.getChildren()) {
      collectConcepts(child, ret);
    }
  }

  /**
   * Execute the passed operation as an atomic unit, handling any issue. All workspace-modifying
   * operations called after initialization should be wrapped in this.
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
    return _namespaceMap == null ? Collections.emptySet() : _namespaceMap.keySet();
  }

  public Collection<String> getBehaviorUrns() {
    return _behaviorMap == null ? Collections.emptySet() : _behaviorMap.keySet();
  }

  public boolean lockProject(String urn, String token, boolean isLocal) {

    var descriptor = projectDescriptors.get(urn);
    if (descriptor == null || !(descriptor.storage instanceof FileProjectStorage)) {
      return false;
    }

    // check and record lock
    if (projectLocks.containsKey(urn) && !projectLocks.get(urn).equals(token)) {
      scope.info("Lock attempt failed: project " + urn + " is already locked");
      return false;
    }

    projectLocks.put(urn, token);
    ((FileProjectStorage) descriptor.storage).lock(true);
    scope.info("Project " + urn + " is locked");

    return true;
  }

  public boolean unlockProject(String urn, String token) {
    if (projectLocks.containsKey(urn)) {

      if (projectLocks.get(urn).equals(token)) {
        var descriptor = projectDescriptors.get(urn);
        ((FileProjectStorage) descriptor.storage).lock(false);
        projectLocks.remove(urn);
        scope.info("Project " + urn + " unlocked");

        return true;
      }
    }
    return false;
  }

  public boolean isWorldviewProvider() {
    return this.worldviewProvider;
  }

  public String getAdoptedWorldview() {
    return this.adoptedWorldview;
  }

  public List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String[] arguments) {

    List<ResourceSet> ret = new ArrayList<>();
    List<Notification> notifications = new ArrayList<>();

    var pd = projectDescriptors.get(projectName);

    if (pd != null && pd.storage instanceof FileProjectStorage fileProjectStorage) {

      var mods =
          switch (operation) {
            case FETCH_COMMIT_AND_PUSH ->
                Utils.Git.fetchCommitAndPush(
                    fileProjectStorage.getRootFolder(),
                    arguments == null || arguments.length == 0
                        ? "Committed by k.LAB resources " + "service"
                        : arguments[0],
                    scope);
            case FETCH_AND_MERGE ->
                Utils.Git.fetchAndMerge(fileProjectStorage.getRootFolder(), scope);
            case COMMIT_AND_SWITCH ->
                Utils.Git.commitAndSwitch(fileProjectStorage.getRootFolder(), arguments[0]);
            case HARD_RESET -> Utils.Git.hardReset(fileProjectStorage.getRootFolder());
            case MERGE_CHANGES_FROM ->
                Utils.Git.mergeChangesFrom(fileProjectStorage.getRootFolder(), arguments[0]);
          };

      List<Triple<ProjectStorage.ResourceType, CRUDOperation, URL>> changes = new ArrayList<>();
      if (mods != null) {

        notifications.addAll(mods.getNotifications());

        if (!Utils.Notifications.hasErrors(mods.getNotifications())) {

          for (var path : mods.getRemovedPaths()) {
            var ddata = ProjectStorage.getDocumentData(path, "/");
            if (ddata != null) {
              changes.add(
                  Triple.of(
                      ddata.getFirst(),
                      CRUDOperation.DELETE,
                      fileProjectStorage.getDocumentUrl(path, "/")));
            }
          }
          for (var path : mods.getAddedPaths()) {
            var ddata = ProjectStorage.getDocumentData(path, "/");
            if (ddata != null) {
              changes.add(
                  Triple.of(
                      ddata.getFirst(),
                      CRUDOperation.CREATE,
                      fileProjectStorage.getDocumentUrl(path, "/")));
            }
          }
          for (var path : mods.getModifiedPaths()) {
            var ddata = ProjectStorage.getDocumentData(path, "/");
            if (ddata != null) {
              changes.add(
                  Triple.of(
                      ddata.getFirst(),
                      CRUDOperation.UPDATE,
                      fileProjectStorage.getDocumentUrl(path, "/")));
            }
          }
        }
      }

      var repositoryState = fileProjectStorage.getRepositoryState();

      if (!changes.isEmpty()) {
        for (var result : handleFileChange(projectName, changes)) {
          result.getNotifications().addAll(notifications);
          ret.add(result);
        }
      } else {
        if (notifications.isEmpty()) {
          notifications.add(
              Notification.info("No repository changes", UIView.Interactivity.DISPLAY));
        }
        var result = ResourceSet.empty();
        result.getNotifications().addAll(notifications);
        ret.add(result);
      }

      for (var rset : ret) {
        var projectResource = new ResourceSet.Resource();
        projectResource.setResourceVersion(pd.manifest.getVersion());
        projectResource.setProjectUrn(pd.name);
        projectResource.setResourceUrn(pd.name);
        projectResource.setRepositoryState(repositoryState);
        projectResource.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT);
        rset.getProjects().add(projectResource);
      }

      return ret;
    }

    return List.of(
        ResourceSet.empty(
            Notification.create(
                "Project" + projectName + " not found or not " + "accessible",
                Notification.Level.Error)));
  }

  /**
   * Called by the service after checking that rights are OK, workspace exists and the project is
   * not already there.
   *
   * @param projectName
   * @return
   */
  public ResourceSet createProject(String projectName, String workspaceName) {

    File workspace = BaseService.getConfigurationSubdirectory(startupOptions, "workspaces");
    File projectHome = new File(workspace + File.separator + projectName);

    if (projectHome.exists()) {
      // shouldn't happen, but in case
      return null;
    }

    var manifest = new ProjectImpl.ManifestImpl();
    var result =
        Utils.Templates.builder(projectHome)
            // TODO other support files?
            .file("META-INF/klab.yaml", Utils.YAML.asString(manifest))
            .build();

    if (result != null) {
      var ret = new ResourceSet();
      var prj = new ResourceSet.Resource();
      prj.setResourceUrn(projectName);
      prj.setResourceVersion(manifest.getVersion());
      ret.setWorkspace(workspaceName);
      ret.getProjects().add(prj);

      var configuration = new ResourcesConfiguration.ProjectConfiguration();
      configuration.setSourceUrl(projectName);
      configuration.setWorkspaceName(workspaceName);
      configuration.setSyncIntervalMinutes(DEFAULT_GIT_SYNC_INTERVAL_MINUTES);
      configuration.setStorageType(ProjectStorage.Type.FILE);
      configuration.setLocalPath(projectHome);
      this.configuration.getProjectConfiguration().put(projectName, configuration);
      // TODO   configuration.setWorldview(readManifest(ret).getDefinedWorldview() != null);

      this.configuration
          .getWorkspaces()
          .computeIfAbsent(workspaceName, k -> new LinkedHashSet<>())
          .add(projectName);

      var storage = new FileProjectStorage(projectHome, projectName, this::handleFileChange);

      ProjectDescriptor descriptor = new ProjectDescriptor();
      descriptor.storage = storage;
      descriptor.manifest = readManifest(storage);
      descriptor.workspace = workspaceName;
      descriptor.name = projectName;
      descriptor.updateInterval = configuration.getSyncIntervalMinutes();
      projectDescriptors.put(projectName, descriptor);

      this.lastProjectUpdates.put(projectName, System.currentTimeMillis());

      saveConfiguration();

      return ret;
    }

    return null;
  }

  public void notifyNewWorkspace(ResourceInfo resourceInfo) {
    var workspace = new WorkspaceImpl();
    workspace.setUrn(resourceInfo.getUrn());
    workspace.setPrivileges(resourceInfo.getRights());
    workspace.setMetadata(resourceInfo.getMetadata());
    this.workspaces.put(resourceInfo.getUrn(), workspace);
    this.configuration.getWorkspaces().put(resourceInfo.getUrn(), new LinkedHashSet<>());
    this.saveConfiguration();
  }

  class StrategyParser extends Parser<Strategies> {

    @Override
    protected Injector createInjector() {
      return new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

    /**
     * Parse a concept definition into its syntactic peer, which should be inspected for errors
     * before turning into semantics.
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
            scope.error(
                "Observation strategy resource has errors: "
                    + strategyUrl
                    + " "
                    + error.getMessage()
                    + "@"
                    + error.getLexicalContext(),
                Klab.ErrorCode.RESOURCE_VALIDATION,
                Klab.ErrorContext.OBSERVATION_STRATEGY);
          }
          return null;
        }

        if (result instanceof Strategies strategies) {
          return new ObservationStrategiesSyntaxImpl(strategies, languageValidationScope) {

            @Override
            protected void logWarning(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              getNotifications()
                  .add(
                      new Notification(
                          object,
                          new LanguageValidationScope.ValidationMessage(
                              message, -1, LanguageValidationScope.Level.WARNING)));
            }

            @Override
            protected void logError(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              getNotifications()
                  .add(
                      new Notification(
                          object,
                          new LanguageValidationScope.ValidationMessage(
                              message, -1, LanguageValidationScope.Level.ERROR)));
            }
          };
        }
      } catch (IOException e) {
        scope.error(
            "Error loading observation strategy " + strategyUrl,
            Klab.ErrorCode.READ_FAILED,
            Klab.ErrorContext.OBSERVATION_STRATEGY);
      }
      return null;
    }
  }

  class BehaviorParser extends Parser<Behavior> {

    @Inject ObservableGrammarAccess grammarAccess;

    @Override
    protected Injector createInjector() {
      return new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

    /**
     * Parse a concept definition into its syntactic peer, which should be inspected for errors
     * before turning into semantics.
     *
     * @param conceptDefinition
     * @return the parsed semantic expression, or null if the parser cannot make sense of it.
     */
    public BehaviorSyntax parseBehavior(String conceptDefinition) {
      var result =
          parser.parse(
              grammarAccess.getConceptExpressionRule(), new StringReader(conceptDefinition));
      var ret = result.getRootASTElement();
      if (ret instanceof Behavior parsed) {
        return new BehaviorSyntaxImpl(parsed, languageValidationScope) {

          List<String> errors = new ArrayList<>();

          @Override
          protected void logWarning(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.WARNING)));
          }

          @Override
          protected void logError(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.ERROR)));
          }
        };
      }
      return null;
    }
  }

  class ObservableParser extends Parser<ObservableSequence> {

    @Inject ObservableGrammarAccess grammarAccess;

    @Override
    protected Injector createInjector() {
      return new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

    /**
     * Parse a concept definition into its syntactic peer, which should be inspected for errors
     * before turning into semantics.
     *
     * @param conceptDefinition
     * @return the parsed semantic expression, or null if the parser cannot make sense of it.
     */
    public SemanticSyntax parseConcept(String conceptDefinition) {
      var result =
          parser.parse(
              grammarAccess.getConceptExpressionRule(), new StringReader(conceptDefinition));
      var ret = result.getRootASTElement();
      if (ret instanceof ConceptExpression) {
        return new SemanticSyntaxImpl(
            (ConceptExpression) ret, false, null, languageValidationScope) {

          List<String> errors = new ArrayList<>();

          @Override
          protected void logWarning(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.WARNING)));
          }

          @Override
          protected void logError(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.ERROR)));
          }
        };
      }
      return null;
    }

    /**
     * Parse an observable definition into its syntactic peer, which should be inspected for errors
     * before turning into semantics.
     *
     * @param observableDefinition
     * @return the parsed semantic expression, or null if the parser cannot make sense of it.
     */
    public ObservableSyntax parseObservable(String observableDefinition) {
      var result =
          parser.parse(
              grammarAccess.getObservableSemanticsRule(), new StringReader(observableDefinition));
      var ret = result.getRootASTElement();
      if (ret instanceof ObservableSemantics) {
        return new ObservableSyntaxImpl((ObservableSemantics) ret, languageValidationScope) {

          List<String> errors = new ArrayList<>();

          @Override
          protected void logWarning(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.WARNING)));
          }

          @Override
          protected void logError(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.ERROR)));
          }
        };
      }
      return null;
    }
  }

  private ObservableParser observableParser = new ObservableParser();
  private StrategyParser strategyParser = new StrategyParser();
  private BehaviorParser behaviorParser = new BehaviorParser();

  private Parser<Ontology> ontologyParser =
      new Parser<>() {
        @Override
        protected Injector createInjector() {
          return new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
      };

  private Parser<Model> namespaceParser =
      new Parser<>() {
        @Override
        protected Injector createInjector() {
          return new KimStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
      };

  private class ProjectDescriptor {
    String name;
    String workspace;
    ProjectStorage storage;
    Project externalProject;
    Project.Manifest manifest;
    int updateInterval;
  }

  private final Map<String, WorkspaceImpl> workspaces = new LinkedHashMap<>();
  private final Function<String, Project> externalProjectResolver;
  private final Map<String, ProjectDescriptor> projectDescriptors = new HashMap<>();
  private final Map<String, Project> projects = new LinkedHashMap<>();
  // all logging goes through here
  private final Scope scope;
  private ResourcesConfiguration configuration;
  private final Map<String, Long> lastProjectUpdates = new HashMap<>();
  private final List<Pair<String, Version>> unresolvedProjects = new ArrayList<>();

  // TODO fix the API - just pass the service, get options and scope from it like the kbox
  public WorkspaceManager(
      Scope scope,
      ServiceStartupOptions options,
      ResourcesProvider service,
      Function<String, Project> externalProjectResolver) {
    this.service = service;
    this.externalProjectResolver = externalProjectResolver;
    this.scope = scope;
    this.startupOptions = options;
    readConfiguration(options);
    loadWorkspace();
    scheduler.scheduleAtFixedRate(this::checkForProjectUpdates, 1, 1, TimeUnit.MINUTES);
  }

  private void checkForProjectUpdates() {

    synchronized (projectDescriptors) {
      for (var pd : projectDescriptors.values()) {
        // configured interval == 0 disables update
        if (pd.storage instanceof FileProjectStorage fpd
            && !fpd.isLocked()
            && pd.updateInterval > 0) {
          var now = System.currentTimeMillis();
          var timeToUpdate =
              lastProjectUpdates.containsKey(pd.name)
                  ? lastProjectUpdates.get(pd.name) + ((long) pd.updateInterval * 1000 * 60)
                  : now;
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
    scope.info(
        "TODO - Checking for updates in unlocked project "
            + projectDescriptor.name
            + ", "
            + "scheduled each "
            + projectDescriptor.updateInterval
            + " minutes");
  }

  private void readConfiguration(ServiceStartupOptions options) {

    File config = BaseService.getFileInConfigurationDirectory(options, "resources.yaml");
    if (config.exists() && config.length() > 0 && !options.isClean()) {
      this.configuration =
          org.integratedmodelling.common.utils.Utils.YAML.load(
              config, ResourcesConfiguration.class);
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
      if (!this.workspaces.containsKey(workspace)) {
        var ws = new WorkspaceImpl();
        ws.setUrn(workspace);
        this.workspaces.put(workspace, ws);
      }

      // TODO must read all worldview providing projects first

      for (var projectName : configuration.getWorkspaces().get(workspace)) {

        var projectConfiguration = configuration.getProjectConfiguration().get(projectName);
        var storage =
            switch (projectConfiguration.getStorageType()) {
              case FILE ->
                  new FileProjectStorage(
                      projectConfiguration.getLocalPath(), projectName, this::handleFileChange);
              // TODO others
              default -> {
                scope.error(
                    "Project "
                        + projectName
                        + " cannot be loaded. Configuration is "
                        + "invalid"
                        + ".");
                yield null;
              }
            };

        // TODO put this outside the workspace loop after checking for worldviews and sorting
        if (storage != null) {

          ProjectDescriptor descriptor = new ProjectDescriptor();
          descriptor.storage = storage;
          descriptor.manifest = readManifest(storage);
          descriptor.workspace = workspace;
          descriptor.name = storage.getProjectName();
          descriptor.updateInterval = projectConfiguration.getSyncIntervalMinutes();
          projectDescriptors.put(storage.getProjectName(), descriptor);
        }
      }
    }
  }

  /**
   * Return all ontologies sorted in order of dependency. Automatically adapt the local ones from
   * their syntactic form. Project dependencies will ensure the consistency of the result; if any of
   * the ontologies is part of a missing project, return an empty list.
   *
   * @param worldviewOnly if true, only ontologies that are part of a project tagged as worldview
   *     will be returned
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
                scope.error(
                    "Ontology resource has errors: " + ontologyUrl,
                    Klab.ErrorCode.RESOURCE_VALIDATION,
                    Klab.ErrorContext.ONTOLOGY);
                //                                return Collections.emptyList();
              }
              urlCache.put(parsed.getNamespace().getName(), ontologyUrl);
              ontologyProjects.put(parsed.getNamespace().getName(), pd.name);
              cache.put(parsed.getNamespace().getName(), Triple.of(parsed, null, isWorldview));
            } catch (IOException e) {
              // log error and return failure
              scope.error(
                  "Error loading ontology " + ontologyUrl,
                  Klab.ErrorCode.READ_FAILED,
                  Klab.ErrorContext.ONTOLOGY);
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
        scope.error(
            "Circular dependencies in ontology graph: cannot continue",
            Klab.ErrorCode.CIRCULAR_REFERENCES,
            Klab.ErrorContext.ONTOLOGY);
        return Collections.emptyList();
      }

      // finish building the ontologies in the given order using a new language validator
      TopologicalOrderIterator<String, DefaultEdge> sort =
          new TopologicalOrderIterator<>(dependencyGraph);
      while (sort.hasNext()) {
        var ontologyId = sort.next();
        var od = cache.get(ontologyId);
        if (od == null) {
          scope.error(
              "Ontology "
                  + ontologyId
                  + " cannot be resolved either locally or "
                  + "through"
                  + " the network",
              Klab.ErrorCode.UNRESOLVED_REFERENCE,
              Klab.ErrorContext.ONTOLOGY);
          return Collections.emptyList();
        }
        AtomicBoolean errors = new AtomicBoolean(false);
        List<Notification> notifications = new ArrayList<>();
        var ontology = od.getSecond();
        if (ontology == null) {
          var syntax =
              new OntologySyntaxImpl(od.getFirst(), languageValidationScope) {

                @Override
                protected void logWarning(
                    ParsedObject target,
                    EObject object,
                    EStructuralFeature feature,
                    String message) {
                  notifications.add(
                      makeNotification(
                          target,
                          object,
                          feature,
                          message,
                          org.integratedmodelling.klab.api.services.runtime.Notification.Level
                              .Warning));
                }

                @Override
                protected void logError(
                    ParsedObject target,
                    EObject object,
                    EStructuralFeature feature,
                    String message) {
                  notifications.add(
                      makeNotification(
                          target,
                          object,
                          feature,
                          message,
                          org.integratedmodelling.klab.api.services.runtime.Notification.Level
                              .Error));
                  errors.set(true);
                }
              };
          ontology =
              LanguageAdapter.INSTANCE.adaptOntology(
                  syntax, ontologyProjects.get(syntax.getName()), notifications);
          documentURLs.put(ontology.getUrn(), urlCache.get(ontology.getUrn()));
        }

        if (errors.get()) {
          scope.error(
              "Logical errors in ontology " + ontologyId + ": cannot continue",
              Klab.ErrorCode.RESOURCE_VALIDATION,
              Klab.ErrorContext.ONTOLOGY);
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
   * Return all the namespaces in order of dependency. Resolution is internal like in {@link
   * #getOntologies(boolean)}.
   *
   * @return
   */
  public List<KimNamespace> getNamespaces() {

    if (_namespaceOrder == null) {
      _namespaceOrder = new ArrayList<>();
      _namespaceMap = new HashMap<>();

      Map<String, String> kimProjects = new HashMap<>();
      Map<String, Pair<Model, KimNamespace>> cache = new HashMap<>();
      Map<String, URL> urlCache = new HashMap<>();
      for (var pd : projectDescriptors.values()) {
        if (pd.externalProject == null) {
          for (var namespaceUrl :
              pd.storage.listResources(ProjectStorage.ResourceType.MODEL_NAMESPACE)) {
            try (var input = namespaceUrl.openStream()) {
              var errors = new ArrayList<Notification>();
              var parsed = namespaceParser.parse(input, errors);
              if (!errors.isEmpty()) {
                scope.error(
                    "Namespace resource has errors: " + namespaceUrl,
                    Klab.ErrorCode.RESOURCE_VALIDATION,
                    Klab.ErrorContext.NAMESPACE);
                //                                return Collections.emptyList();
              }
              urlCache.put(parsed.getNamespace().getName(), namespaceUrl);
              kimProjects.put(parsed.getNamespace().getName(), pd.name);
              cache.put(parsed.getNamespace().getName(), Pair.of(parsed, null));
            } catch (IOException e) {
              // log error and return failure
              scope.error(
                  "Error loading namespace " + namespaceUrl,
                  Klab.ErrorCode.READ_FAILED,
                  Klab.ErrorContext.NAMESPACE);
              //                            return Collections.emptyList();
            }
          }
        }
      }

      // we have the ontologies and there are no errors this far: now build the order and if
      // something is unresolved, log error and say goodbye
      Graph<String, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
      Map<String, KimOntology> ontologies = new HashMap<>();
      for (String namespaceId : cache.keySet()) {
        var od = cache.get(namespaceId);
        dependencyGraph.addVertex(namespaceId);
        if (od.getFirst() != null) {
          for (var imported : od.getFirst().getNamespace().getImported()) {
            dependencyGraph.addVertex(imported.getName());
            dependencyGraph.addEdge(imported.getName(), namespaceId);
          }
        } else {
          for (var imported : od.getSecond().getImports().keySet()) {
            dependencyGraph.addVertex(imported);
            dependencyGraph.addEdge(imported, namespaceId);
          }
        }
      }

      CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(dependencyGraph);
      if (cycleDetector.detectCycles()) {
        scope.error(
            "Circular dependencies in namespace graph: cannot continue",
            Klab.ErrorCode.CIRCULAR_REFERENCES,
            Klab.ErrorContext.NAMESPACE);
        return Collections.emptyList();
      }

      // finish building the ontologies in the given order using a new language validator
      TopologicalOrderIterator<String, DefaultEdge> sort =
          new TopologicalOrderIterator<>(dependencyGraph);
      while (sort.hasNext()) {
        var namespaceId = sort.next();
        var od = cache.get(namespaceId);
        if (od == null) {
          scope.error(
              "Namespace "
                  + namespaceId
                  + " cannot be resolved either locally or "
                  + "through"
                  + " the network",
              Klab.ErrorCode.UNRESOLVED_REFERENCE,
              Klab.ErrorContext.ONTOLOGY);
          return Collections.emptyList();
        }
        AtomicBoolean errors = new AtomicBoolean(false);
        List<Notification> notifications = new ArrayList<>();
        var namespace = od.getSecond();
        if (namespace == null) {
          var syntax =
              new NamespaceSyntaxImpl(od.getFirst(), this.languageValidationScope) {

                @Override
                protected void logWarning(
                    ParsedObject target,
                    EObject object,
                    EStructuralFeature feature,
                    String message) {
                  notifications.add(
                      makeNotification(
                          target,
                          object,
                          feature,
                          message,
                          org.integratedmodelling.klab.api.services.runtime.Notification.Level
                              .Warning));
                }

                @Override
                protected void logError(
                    ParsedObject target,
                    EObject object,
                    EStructuralFeature feature,
                    String message) {
                  notifications.add(
                      makeNotification(
                          target,
                          object,
                          feature,
                          message,
                          org.integratedmodelling.klab.api.services.runtime.Notification.Level
                              .Error));
                  errors.set(true);
                }
              };
          namespace =
              LanguageAdapter.INSTANCE.adaptNamespace(
                  syntax, kimProjects.get(syntax.getUrn()), notifications);
          documentURLs.put(namespace.getUrn(), urlCache.get(namespace.getUrn()));
        }

        if (errors.get()) {
          scope.error(
              "Logical errors in namespace " + namespaceId + ": cannot continue",
              Klab.ErrorCode.RESOURCE_VALIDATION,
              Klab.ErrorContext.ONTOLOGY);
          //                    return Collections.emptyList();
        }

        this._namespaceOrder.add(namespace);
        this._namespaceMap.put(namespace.getUrn(), namespace);
      }
    }
    return _namespaceOrder;
  }

  public ResourcesConfiguration getConfiguration() {
    return this.configuration;
  }

  public List<KActorsBehavior> getBehaviors() {
    if (_behaviorOrder == null) {
      _behaviorOrder = new ArrayList<>();
      _behaviorMap = new HashMap<>();
      // TODO load them from all projects in dependency order, same as ontologies; fill in the URL
      //  cache and everything
      for (var pd : projectDescriptors.values()) {
        if (pd.externalProject == null) {

          for (var behaviorUrl :
              pd.storage.listResources(
                  ProjectStorage.ResourceType.APPLICATION,
                  ProjectStorage.ResourceType.BEHAVIOR_COMPONENT,
                  ProjectStorage.ResourceType.TESTCASE,
                  ProjectStorage.ResourceType.BEHAVIOR)) {

            try (var input = behaviorUrl.openStream()) {

              var errors = new AtomicBoolean(false);
              var notams = new ArrayList<Notification>();
              var parsed = behaviorParser.parse(input, notams);

            } catch (IOException e) {
              // log error and return failure
              scope.error(
                  "Error loading k.Actors behavior " + behaviorUrl,
                  Klab.ErrorCode.READ_FAILED,
                  Klab.ErrorContext.ONTOLOGY);
            }
          }
        }
      }
    }
    return _behaviorOrder;
  }

  public List<KimObservationStrategyDocument> getStrategyDocuments() {

    if (_observationStrategyDocuments == null) {
      _observationStrategyDocuments = new ArrayList<>();
      _observationStrategyDocumentMap = new HashMap<>();

      for (var pd : projectDescriptors.values()) {
        if (pd.externalProject == null) {

          for (var strategyUrl : pd.storage.listResources(ProjectStorage.ResourceType.STRATEGY)) {
            try (var input = strategyUrl.openStream()) {

              var errors = new AtomicBoolean(false);
              var notams = new ArrayList<Notification>();
              var parsed = strategyParser.parse(input, notams);

              if (!notams.isEmpty()) {
                scope.error(
                    "Observation strategy resource has errors: " + strategyUrl,
                    Klab.ErrorCode.RESOURCE_VALIDATION,
                    Klab.ErrorContext.OBSERVATION_STRATEGY);
                //                                return Collections.emptyList();
              } else {

                List<Notification> notifications = new ArrayList<>();
                var syntax =
                    new ObservationStrategiesSyntaxImpl(parsed, this.languageValidationScope) {

                      @Override
                      protected void logWarning(
                          ParsedObject target,
                          EObject object,
                          EStructuralFeature feature,
                          String message) {
                        notifications.add(
                            makeNotification(
                                target,
                                object,
                                feature,
                                message,
                                org.integratedmodelling.klab.api.services.runtime.Notification.Level
                                    .Warning));
                      }

                      @Override
                      protected void logError(
                          ParsedObject target,
                          EObject object,
                          EStructuralFeature feature,
                          String message) {
                        notifications.add(
                            makeNotification(
                                target,
                                object,
                                feature,
                                message,
                                org.integratedmodelling.klab.api.services.runtime.Notification.Level
                                    .Error));
                        errors.set(true);
                      }
                    };

                if (!errors.get()) {
                  var document =
                      LanguageAdapter.INSTANCE.adaptStrategies(syntax, pd.name, notifications);
                  _observationStrategyDocuments.add(document);
                  _observationStrategyDocumentMap.put(document.getUrn(), document);
                }
              }
            } catch (IOException e) {
              // log error and return failure
              scope.error(
                  "Error loading ontology " + strategyUrl,
                  Klab.ErrorCode.READ_FAILED,
                  Klab.ErrorContext.ONTOLOGY);
            }
          }
        }
      }
    }
    return _observationStrategyDocuments;
  }

  public List<String> getWorkspaceURNs() {
    return new ArrayList<>(workspaces.keySet());
  }

  /**
   * Create the project implementation with every namespace and manifest filled in. CAUTION this can
   * be a large object. The project must exist in a local workspace; if not, null will be returned
   * without error.
   *
   * @param projectId
   * @return the filled in project or null
   */
  public Project createProjectData(String projectId, String workspaceName) {

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

      for (KimObservationStrategyDocument strategyDocument : getStrategyDocuments()) {
        if (projectId.equals(strategyDocument.getProjectName())) {
          ret.getObservationStrategies().add(strategyDocument);
        }
      }

      for (KimNamespace namespace : getNamespaces()) {
        if (projectId.equals(namespace.getProjectName())) {
          ret.getNamespaces().add(namespace);
        }
      }

      // TODO the rest

      for (KActorsBehavior behavior : getBehaviors()) {
        if (projectId.equals(behavior.getProjectName())) {
          // FIXME choose based on where they belong
          ret.getBehaviors().add(behavior);
        }
      }

      this.projects.put(ret.getUrn(), ret);
      var project = ret;
      var workspace = getWorkspace(workspaceName);

      if (workspace.getProjects().stream().anyMatch(p -> p.getUrn().equals(project.getUrn()))) {
        workspace.setProjects(
            workspace.getProjects().stream()
                .map(p -> p.getUrn().equals(project.getUrn()) ? project : p)
                .collect(toList()));
      } else {
        workspace.getProjects().add(ret);
      }
    }

    return ret;
  }

  List<ObservationStrategySyntax> getStrategies() {
    return null;
  }

  /**
   * Import a project from a URL into the given workspace and return the associated storage. Project
   * configuration must not exist already, if so it is removed and rebuilt. All project implications
   * in the workspace are resolved downstream.
   *
   * @param projectUrl
   * @param workspaceName
   * @return
   */
  public ProjectStorage importProject(String projectUrl, String workspaceName) {

    String projectName = Utils.URLs.getURLBaseName(projectUrl);
    var configuration = this.configuration.getProjectConfiguration().get(projectName);
    if (configuration != null) {
      scope.warn(
          "Configuration of imported project "
              + projectName
              + " exists already: "
              + "import "
              + "will "
              + "rewrite it");
    }

    ProjectStorage ret = null;

    try {

      if (Utils.Git.isRemoteGitURL(projectUrl)) {

        File workspace = BaseService.getConfigurationSubdirectory(startupOptions, "workspaces");
        File projectHome = new File(workspace + File.separator + projectName);

        if (projectHome.isDirectory()) {
          scope.warn(
              "Deleting and reimporting " + projectName + " from Git repository " + projectUrl);
        }

        try {
          projectName = Utils.Git.clone(projectUrl, workspace, true, scope);
          if (projectHome.exists()) {
            ret = new FileProjectStorage(projectHome, projectName, this::handleFileChange);
          }

        } catch (Throwable t) {
          // just make the return value null
          if (projectHome.exists()) {
            Utils.Files.deleteQuietly(projectHome);
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
          ret = new FileProjectStorage(file, projectName, this::handleFileChange);
        } else if (Utils.Files.JAVA_ARCHIVE_EXTENSIONS.contains(
            Utils.Files.getFileExtension(file))) {
          // TODO ret = read from archive
        }
      }
    } catch (Throwable t) {
      scope.error(t);
    } finally {
      //            service.setBusy(false);
    }

    /** (Re)Create configuration */
    if (ret != null) {

      configuration = new ResourcesConfiguration.ProjectConfiguration();
      configuration.setSourceUrl(projectUrl);
      configuration.setWorkspaceName(workspaceName);
      configuration.setSyncIntervalMinutes(DEFAULT_GIT_SYNC_INTERVAL_MINUTES);
      configuration.setStorageType(ret.getType());
      /*
       * Default privileges are exclusive to the service, the API can be used to change them
       */
      configuration.setPrivileges(ResourcePrivileges.empty());
      if (ret instanceof FileProjectStorage fps) {
        configuration.setLocalPath(fps.getRootFolder());
      }
      this.configuration.getProjectConfiguration().put(ret.getProjectName(), configuration);
      configuration.setWorldview(readManifest(ret).getDefinedWorldview() != null);

      Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
      if (projects == null) {
        projects = new LinkedHashSet<>();
        this.configuration.getWorkspaces().put(workspaceName, projects);
      }

      projects.add(ret.getProjectName());

      if (!this.workspaces.containsKey(workspaceName)) {
        var ws = new WorkspaceImpl();
        ws.setUrn(workspaceName);
        this.workspaces.put(workspaceName, ws);
      }

      saveConfiguration();

      /*
      create project descriptor
       */
      ProjectDescriptor descriptor = new ProjectDescriptor();
      descriptor.storage = ret;
      descriptor.manifest = readManifest(ret);
      descriptor.workspace = workspaceName;
      descriptor.name = ret.getProjectName();
      descriptor.updateInterval = configuration.getSyncIntervalMinutes();
      projectDescriptors.put(ret.getProjectName(), descriptor);

      // review all dependencies and rebuild caches
      loadWorkspace();
    }

    return ret;
  }

  public Project loadProject(ProjectStorage storage, String workspaceName) {

    var configuration = this.configuration.getProjectConfiguration().get(storage.getProjectName());
    if (configuration == null) {
      throw new KlabResourceAccessException(
          "project configuration for " + storage.getProjectName() + " is missing");
    }
    return createProjectData(storage.getProjectName(), workspaceName);
  }

  /**
   * Either called automatically by the file watcher in {@link FileProjectStorage} or explicitly
   * invoked in synchronous CRUD operations on projects when the project is locked by the requesting
   * user.
   *
   * @param project
   * @param changes
   */
  public synchronized List<ResourceSet> handleFileChange(
      String project, List<Triple<ProjectStorage.ResourceType, CRUDOperation, URL>> changes) {

    if (loading.get()) {
      return Collections.emptyList();
    }

    /*
    populate the resource set changes in order of workspace affected
     */
    Map<String, ResourceSet> result = new LinkedHashMap<>();

    // this may or may not end up in the result set
    var worldviewChange = new ResourceSet();
    worldviewChange.setWorkspace(Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER);
    worldviewChange.getServices().put(configuration.getServiceId(), service.getUrl());
    var projectDescriptor = projectDescriptors.get(project);

    Set<String> affectedOntologies = new HashSet<>();
    Set<String> affectedNamespaces = new HashSet<>();
    Set<String> affectedBehaviors = new HashSet<>();
    Set<String> affectedStrategies = new HashSet<>();
    List<KlabDocument<?>> newAssets = new ArrayList<>();

    boolean mustRecomputeOrder = false;

    for (var change : changes) {

      if (change.getSecond() == CRUDOperation.DELETE) {

        // there's no new asset but all the affected must be reloaded
        if (projectDescriptor.storage instanceof FileProjectStorage fps) {

          String deletedUrn = fps.getDocumentUrn(change.getFirst(), change.getThird());

          if (deletedUrn != null) {

            affectedOntologies.add(deletedUrn);
            for (var ontology : getOntologies(false)) {
              if (!Sets.intersection(affectedOntologies, ontology.importedNamespaces(false))
                  .isEmpty()) {
                affectedOntologies.add(ontology.getUrn());
              }
            }

            affectedNamespaces.addAll(affectedOntologies);
            for (var namespace : getNamespaces()) {
              if (!Sets.intersection(affectedNamespaces, namespace.importedNamespaces(false))
                  .isEmpty()) {
                affectedNamespaces.add(namespace.getUrn());
              }
            }
            affectedNamespaces.removeAll(affectedOntologies);

            // same for strategies and behaviors
            affectedBehaviors.addAll(affectedOntologies);
            for (var behavior : getBehaviors()) {
              if (!Sets.intersection(affectedBehaviors, behavior.importedNamespaces(false))
                  .isEmpty()) {
                affectedBehaviors.add(behavior.getUrn());
              }
            }
            affectedBehaviors.removeAll(affectedOntologies);

            affectedStrategies.addAll(affectedOntologies);
            for (var strategies : getStrategyDocuments()) {
              if (!Sets.intersection(affectedStrategies, strategies.importedNamespaces(false))
                  .isEmpty()) {
                affectedStrategies.add(strategies.getUrn());
              }
            }
            affectedStrategies.removeAll(affectedOntologies);
          }
        }
      } else if (change.getSecond() == CRUDOperation.CREATE) {

        // just a new asset, nothing should be affected, let this through
        KlabDocument<?> newAsset =
            switch (change.getFirst()) {
              case ONTOLOGY -> loadOntology(change.getThird(), project);
              case MODEL_NAMESPACE -> loadNamespace(change.getThird(), project);
              case BEHAVIOR -> loadBehavior(change.getThird(), project);
              case STRATEGY -> loadStrategy(change.getThird(), project);
              default -> null;
            };

        // TODO Add document to project. FileStorage should have added it to the repository if
        //  there's one

        newAssets.add(newAsset);

      } else {

        /*
        figure out which asset is affected and load it
        */
        KlabDocument<?> newAsset =
            switch (change.getFirst()) {
              case ONTOLOGY -> loadOntology(change.getThird(), project);
              case MODEL_NAMESPACE -> loadNamespace(change.getThird(), project);
              case BEHAVIOR -> loadBehavior(change.getThird(), project);
              case STRATEGY -> loadStrategy(change.getThird(), project);
              default -> null;
            };

        if (newAsset != null) {

          newAssets.add(newAsset);

          KlabDocument<?> oldAsset =
              switch (newAsset) {
                case KimOntology ontology -> getOntology(ontology.getUrn());
                case KimNamespace namespace -> getNamespace(namespace.getUrn());
                case KActorsBehavior behavior -> getBehavior(behavior.getUrn());
                case KimObservationStrategyDocument strategy ->
                    getStrategyDocument(strategy.getUrn());
                default -> null;
              };

          if (oldAsset == null) {
            scope.error("Internal: cannot update a non-existing document: " + change.getSecond());
            return Collections.emptyList();
          }

          /*
          if the implicit or explicit import statements have changed, the full order of loading
          must be
          recomputed.
          */
          if (!mustRecomputeOrder) {
            mustRecomputeOrder =
                !newAsset.importedNamespaces(false).equals(oldAsset.importedNamespaces(false));
          }

          /*
          establish what needs to be reloaded and which workspaces are affected: dry run across
          ontologies (if the asset is an ontology), then strategies, namespaces and behaviors. First
          establish the affected ones and compile the result sets per workspace. Then send those and
          start the loading based on the collected metadata in the sets.
          */
          if (change.getFirst() == ProjectStorage.ResourceType.ONTOLOGY) {
            affectedOntologies.add(oldAsset.getUrn());
            for (var ontology : getOntologies(false)) {
              if (!Sets.intersection(affectedOntologies, ontology.importedNamespaces(false))
                  .isEmpty()) {
                affectedOntologies.add(ontology.getUrn());
              }
            }
          }

          if (change.getFirst() == ProjectStorage.ResourceType.ONTOLOGY
              || change.getFirst() == ProjectStorage.ResourceType.MODEL_NAMESPACE) {
            affectedNamespaces.addAll(affectedOntologies);
            affectedNamespaces.add(oldAsset.getUrn());
            for (var namespace : getNamespaces()) {
              if (!Sets.intersection(affectedNamespaces, namespace.importedNamespaces(false))
                  .isEmpty()) {
                affectedNamespaces.add(namespace.getUrn());
              }
            }
            affectedNamespaces.removeAll(affectedOntologies);
          }

          if (change.getFirst() == ProjectStorage.ResourceType.ONTOLOGY
              || change.getFirst() == ProjectStorage.ResourceType.MODEL_NAMESPACE
              || change.getFirst() == ProjectStorage.ResourceType.BEHAVIOR) {
            // same for strategies and behaviors
            affectedBehaviors.addAll(affectedOntologies);
            affectedBehaviors.add(oldAsset.getUrn());

            for (var behavior : getBehaviors()) {
              if (!Sets.intersection(affectedBehaviors, behavior.importedNamespaces(false))
                  .isEmpty()) {
                affectedBehaviors.add(behavior.getUrn());
              }
            }
            affectedBehaviors.removeAll(affectedOntologies);
          }

          if (change.getFirst() == ProjectStorage.ResourceType.STRATEGY) {
            affectedStrategies.addAll(affectedOntologies);
            affectedStrategies.add(oldAsset.getUrn());
            for (var strategies : getStrategyDocuments()) {
              if (!Sets.intersection(affectedStrategies, strategies.importedNamespaces(false))
                  .isEmpty()) {
                affectedStrategies.add(strategies.getUrn());
              }
            }
            affectedStrategies.removeAll(affectedOntologies);
          }
        } else {
          // TODO report failure
        }
      }
    }

    this.loading.set(true);

    /*
    make the actual change. For each modification: if
    it's the modified object, reset the corresponding concept descriptors in the language
    validator (if an ontology) or the kbox for the namespace. Then reload and substitute in the
    ontology, worldview and namespace arrays for the modified and the affected in the order
    specified by the resourcesets.
     */

    if (mustRecomputeOrder) {
      computeLoadOrder();
    }

    for (var newAsset : newAssets) {

      /*
      compile the ResourceSets based on the (possibly new) order
       */
      if (!affectedOntologies.isEmpty()) {
        for (var ontology : getOntologies(false)) {
          if (affectedOntologies.contains(ontology.getUrn())) {
            var descriptor = addToResultSet(ontology, Workspace.EXTERNAL_WORKSPACE_URN, result);
            if (_worldviewOntologies.stream()
                .anyMatch(ont -> newAsset.getUrn().equals(ont.getUrn()))) {
              worldviewChange.getOntologies().add(descriptor);
            }
          }
        }
      }

      if (!affectedNamespaces.isEmpty()) {
        for (var namespace : getNamespaces()) {
          if (affectedNamespaces.contains(namespace.getUrn())) {
            addToResultSet(namespace, Workspace.EXTERNAL_WORKSPACE_URN, result);
          }
        }
      }

      if (!affectedBehaviors.isEmpty()) {
        for (var behavior : getBehaviors()) {
          if (affectedBehaviors.contains(behavior.getUrn())) {
            addToResultSet(behavior, Workspace.EXTERNAL_WORKSPACE_URN, result);
          }
        }
      }

      if (!affectedStrategies.isEmpty()) {
        for (var strategies : getStrategyDocuments()) {
          if (affectedStrategies.contains(strategies.getUrn())) {
            var descriptor = addToResultSet(strategies, Workspace.EXTERNAL_WORKSPACE_URN, result);
            if (_worldviewOntologies.stream()
                .anyMatch(ont -> newAsset.getUrn().equals(ont.getUrn()))) {
              worldviewChange.getObservationStrategies().add(descriptor);
            }
          }
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

          this.languageValidationScope.clearNamespace(oldOntology.getUrn());
          var newOntology =
              oldOntology.getUrn().equals(newAsset.getUrn())
                  ? newAsset
                  : loadOntology(
                      documentURLs.get(oldOntology.getUrn()), oldOntology.getProjectName());
          this.languageValidationScope.addNamespace((KimOntology) newOntology);
          newDocuments.add(newOntology);
        }
      }
      for (var oldNamespace : _namespaceOrder) {
        if (affectedNamespaces.contains(oldNamespace.getUrn())) {
          newDocuments.add(
              oldNamespace.getUrn().equals(newAsset.getUrn())
                  ? newAsset
                  : loadNamespace(
                      documentURLs.get(oldNamespace.getUrn()), oldNamespace.getProjectName()));
        }
      }
      for (var oldBehavior : _behaviorOrder) {
        if (affectedBehaviors.contains(oldBehavior.getUrn())) {
          newDocuments.add(
              oldBehavior.getUrn().equals(newAsset.getUrn())
                  ? newAsset
                  : loadBehavior(
                      documentURLs.get(oldBehavior.getUrn()), oldBehavior.getProjectName()));
        }
      }
      for (var oldStrategy : _observationStrategyDocuments) {
        if (affectedStrategies.contains(oldStrategy.getUrn())) {
          newDocuments.add(
              oldStrategy.getUrn().equals(newAsset.getUrn())
                  ? newAsset
                  : loadStrategy(
                      documentURLs.get(oldStrategy.getUrn()), oldStrategy.getProjectName()));
        }
      }

      for (var document : newDocuments) {
        switch (document) {
          case KimOntology ontology -> {
            if (_worldviewOntologies.stream().anyMatch(o -> newAsset.getUrn().equals(o.getUrn()))) {
              _worldviewOntologies =
                  _worldviewOntologies.stream()
                      .map(o -> o.getUrn().equals(document.getUrn()) ? ontology : o)
                      .collect(toList());
              _worldview.setOntologies(_worldviewOntologies);
            }
            _ontologyOrder =
                _ontologyOrder.stream()
                    .map(o -> o.getUrn().equals(document.getUrn()) ? ontology : o)
                    .collect(toList());
            replaceAndIndex(ontology);
          }
          case KimNamespace namespace -> {
            _namespaceOrder =
                _namespaceOrder.stream()
                    .map(o -> o.getUrn().equals(document.getUrn()) ? namespace : o)
                    .collect(toList());
            replaceAndIndex(namespace);
          }
          case KActorsBehavior behavior -> {
            _behaviorOrder =
                _behaviorOrder.stream()
                    .map(o -> o.getUrn().equals(document.getUrn()) ? behavior : o)
                    .collect(toList());
            replaceAndIndex(behavior);
          }
          case KimObservationStrategyDocument strategies -> {
            _observationStrategyDocuments =
                _observationStrategyDocuments.stream()
                    .map(o -> o.getUrn().equals(document.getUrn()) ? strategies : o)
                    .collect(toList());
            _observationStrategyDocumentMap.put(strategies.getUrn(), strategies);
            _worldview.setObservationStrategies(_observationStrategyDocuments);
          }
          default -> throw new KlabIllegalStateException("can't deal with " + document);
        }
      }
    }

    this.loading.set(false);

    var ret = new ArrayList<ResourceSet>();
    if (!worldviewChange.getOntologies().isEmpty()
        || !worldviewChange.getObservationStrategies().isEmpty()) {
      ret.add(worldviewChange);
    }
    ret.addAll(result.values());

    /*
    Report a ResourceSet per workspace affected. The listening end(s) will have to request the
    contents.
    */
    for (var resourceSet : ret) {
      scope.send(
          Message.MessageClass.ResourceLifecycle,
          Message.MessageType.WorkspaceChanged,
          resourceSet);
    }

    return ret;
  }

  private void replaceAndIndex(KimNamespace namespace) {
    _namespaceMap.put(namespace.getUrn(), namespace);
  }

  private void replaceAndIndex(KActorsBehavior behavior) {
    // TODO index app and component metadata for queries
    _behaviorMap.put(behavior.getUrn(), behavior);
  }

  private void replaceAndIndex(KimOntology ontology) {
    // TODO index concept declarations for queries
    _ontologyMap.put(ontology.getUrn(), ontology);
  }

  /**
   * Add the document info to the result set that corresponds to the passed workspace in the passed
   * result map, creating whatever is needed. If the external workspace name is given, use that for
   * an external document, otherwise skip it.
   *
   * @param asset
   * @param result
   */
  private ResourceSet.Resource addToResultSet(
      KlabDocument<?> asset, String externalWorkspaceId, Map<String, ResourceSet> result) {

    String workspace = getWorkspaceForProject(asset.getProjectName());
    ResourceSet.Resource resource = null;
    if (workspace == null) workspace = externalWorkspaceId;

    if (workspace != null) {

      ResourceSet resourceSet = result.get(workspace);
      if (resourceSet == null) {
        resourceSet = new ResourceSet();
        resourceSet.setWorkspace(workspace);
        resourceSet.getServices().put(configuration.getServiceId(), service.getUrl());
        result.put(workspace, resourceSet);
      }

      resource = new ResourceSet.Resource();
      resource.setResourceUrn(asset.getUrn());
      resource.setResourceVersion(asset.getVersion());
      resource.setServiceId(configuration.getServiceId());
      resource.setKnowledgeClass(KlabAsset.classify(asset));
      resource.getNotifications().addAll(asset.getNotifications());
      if (resourceSet.getServices().containsKey(configuration.getServiceId())) {
        resourceSet.getServices().put(configuration.getServiceId(), service.getUrl());
      }

      /*
       * Must check because a previous change may already have added this dependency
       */
      if (!Utils.Resources.contains(resourceSet, resource)) {
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
      }

      //            resourceSet.getResources().add(resource);

    }

    return resource;
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

  private <T extends KlabDocument<?>> void sortDocuments(
      List<T> documents, Klab.ErrorContext errorContext) {

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
      scope.error(
          "Circular dependencies in workspace: cannot continue. Cyclic dependencies "
              + "affect "
              + cycleDetector.findCycles(),
          Klab.ErrorCode.CIRCULAR_REFERENCES,
          errorContext);
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
    return updateStatus(_ontologyMap.get(urn));
  }

  public KimNamespace getNamespace(String urn) {
    return updateStatus(_namespaceMap.get(urn));
  }

  public KActorsBehavior getBehavior(String urn) {
    return null; // TODO _ontologyMap.get(urn);
  }

  public KimObservationStrategyDocument getStrategyDocument(String urn) {
    return _observationStrategyDocumentMap.get(urn);
  }

  private KimOntology loadOntology(URL url, String project) {
    try (var input = url.openStream()) {
      List<Notification> notifications = new ArrayList<>();
      var parsed = ontologyParser.parse(input, notifications);
      var syntax =
          new OntologySyntaxImpl(parsed, languageValidationScope) {

            @Override
            protected void logWarning(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
                      org.integratedmodelling.klab.api.services.runtime.Notification.Level
                          .Warning));
            }

            @Override
            protected void logError(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
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
    try (var input = url.openStream()) {
      List<Notification> notifications = new ArrayList<>();
      var parsed = namespaceParser.parse(input, notifications);
      var syntax =
          new NamespaceSyntaxImpl(parsed, languageValidationScope) {

            @Override
            protected void logWarning(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
                      org.integratedmodelling.klab.api.services.runtime.Notification.Level
                          .Warning));
            }

            @Override
            protected void logError(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
                      org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
            }
          };
      return LanguageAdapter.INSTANCE.adaptNamespace(syntax, project, notifications);
    } catch (IOException e) {
      scope.error(e);
    }
    return null;
  }

  private KActorsBehavior loadBehavior(URL url, String project) {
    //        try (var input = url.openStream()) {
    //            List<Notification> notifications = new ArrayList<>();
    //            var parsed = behaviorParser.parse(input, notifications);
    //            var syntax = new KActorsBehaviorImpl(parsed, languageValidationScope) {
    //
    //                @Override
    //                protected void logWarning(ParsedObject target, EObject object,
    //                EStructuralFeature
    //                feature,
    //                                          String message) {
    //                    notifications.add(makeNotification(target, object, feature, message,
    //
    // org.integratedmodelling.klab.api.services.runtime.Notification.Level
    //                            .Warning));
    //                }
    //
    //                @Override
    //                protected void logError(ParsedObject target, EObject object,
    // EStructuralFeature
    //                feature,
    //                                        String message) {
    //                    notifications.add(makeNotification(target, object, feature, message,
    //
    // org.integratedmodelling.klab.api.services.runtime.Notification.Level
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
    try (var input = url.openStream()) {
      List<Notification> notifications = new ArrayList<>();
      var parsed = strategyParser.parse(input, notifications);
      var syntax =
          new ObservationStrategiesSyntaxImpl(parsed, languageValidationScope) {

            @Override
            protected void logWarning(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
                      org.integratedmodelling.klab.api.services.runtime.Notification.Level
                          .Warning));
            }

            @Override
            protected void logError(
                ParsedObject target, EObject object, EStructuralFeature feature, String message) {
              notifications.add(
                  makeNotification(
                      target,
                      object,
                      feature,
                      message,
                      org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
            }
          };
      return LanguageAdapter.INSTANCE.adaptStrategies(syntax, project, notifications);
    } catch (IOException e) {
      scope.error(e);
      return null;
    }
  }

  /**
   * TODO pass document name, type and project name to complete the LC (not needed at the moment).
   *
   * @param target
   * @param object
   * @param feature
   * @param message
   * @param level
   * @return
   */
  private Notification makeNotification(
      ParsedObject target,
      EObject object,
      EStructuralFeature feature,
      String message,
      Notification.Level level) {
    if (target != null) {
      var context = new NotificationImpl.LexicalContextImpl();
      context.setLength(target.getCodeLength());
      context.setOffsetInDocument(target.getCodeOffset());
      //            context.setUrl(target.uri());
      return Notification.create(message, level, context);
    }
    return Notification.create(message, level);
  }

  public List<Pair<ProjectStorage, Project>> getProjectLoadOrder() {
    return this._projectLoadOrder;
  }

  /**
   * Read, validate, resolve and sorts projects locally (all workspaces) and from the network,
   * returning the load order for all projects, including local and externally resolved ones. Check
   * errors (reported in the configured monitor) and unresolved projects after calling. Does not
   * throw exceptions.
   *
   * <p>While loading the workspaces, (re)build the workspace list so that {@link #getWorkspaces()}
   * can work. The workspaces are also listed in order of first-contact dependency although circular
   * deps between workspaces are permitted.
   *
   * @return the load order or an empty collection in case of circular dependencies or no
   *     configuration. If errors happened they will be notified through the monitor and {@link
   *     #getUnresolvedProjects()} will return the list of projects that have not resolved properly
   *     (including resource not found and version mismatch errors). Only one of the elements in
   *     each returned pair will be non-null.
   */
  public synchronized boolean loadWorkspace() {

    // clear all caches
    this._projectLoadOrder = null;
    this._ontologyOrder = null;
    this._ontologyMap = null;
    this._namespaceMap = null;
    this._namespaceOrder = null;
    this._observationStrategyDocuments = null;
    this._observationStrategies = null;
    this._behaviorMap = null;
    this._behaviorOrder = null;
    this._worldview = null;
    this.worldviewProvider = false;

    for (var workspace : configuration.getWorkspaces().keySet()) {
      for (var projectName : configuration.getWorkspaces().get(workspace)) {
        var descriptor = projectDescriptors.get(projectName);
        if (!this.worldviewProvider && descriptor.manifest.getDefinedWorldview() != null) {
          this.worldviewProvider = true;
          this.adoptedWorldview = descriptor.manifest.getDefinedWorldview();
        }
      }
    }

    //
    //        for (var workspace : configuration.getWorkspaces().keySet()) {
    //            for (var projectName : configuration.getWorkspaces().get(workspace)) {
    //                var projectConfiguration = projectDescriptors.get(projectName);
    //                // TODO put this outside the workspace loop after checking for worldviews and
    //                 sorting
    //                var project = loadProject(projectConfiguration.storage, workspace);
    //                projects.put(projectConfiguration.name, project);
    //            }
    //        }
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

    Graph<Pair<String, Version>, DefaultEdge> dependencyGraph =
        new DefaultDirectedGraph<>(DefaultEdge.class);

    // first insert worldview -> project dependencies
    Map<Pair<String, Version>, List<Pair<String, Version>>> wdeps = new HashMap<>();
    for (var pd : projectDescriptors.values()) {
      if (pd.manifest.getDefinedWorldview() != null) {
        wdeps.computeIfAbsent(
            Pair.of(pd.manifest.getDefinedWorldview(), pd.manifest.getVersion()),
            s -> new ArrayList<>());
      } else if (pd.manifest.getWorldview() != null) {
        wdeps
            .computeIfAbsent(
                Pair.of(
                    pd.manifest.getWorldview(), getWorldviewVersion(pd.manifest.getWorldview())),
                s -> new ArrayList<>())
            .add(Pair.of(pd.name, pd.manifest.getVersion()));
      }
    }

    for (var wv : wdeps.keySet()) {
      dependencyGraph.addVertex(wv);
      for (var dep : wdeps.get(wv)) {
        dependencyGraph.addVertex(dep);
        dependencyGraph.addEdge(dep, wv);
      }
    }

    // build a version-aware dependency tree
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
      scope.error(
          Klab.ErrorCode.CIRCULAR_REFERENCES,
          Klab.ErrorContext.PROJECT,
          "Projects in configuration have cyclic dependencies on each other: "
              + "will not proceed. Review  configuration");
      this.loading.set(false);
      return false;
    } else {

      var sort = new TopologicalOrderIterator<>(dependencyGraph);

      while (sort.hasNext()) {
        var proj = sort.next();
        // verify availability
        if (projectDescriptors.get(proj.getFirst()) != null) {
          // local dependency: check version
          var pd = projectDescriptors.get(proj.getFirst());
          if (pd.manifest.getVersion().compatible(proj.getSecond())) {
            this._projectLoadOrder.add(Pair.of(pd.storage, null));
          } else {
            scope.error(
                Klab.ErrorContext.PROJECT,
                Klab.ErrorCode.MISMATCHED_VERSION,
                "Project "
                    + proj.getFirst()
                    + "@"
                    + proj.getSecond()
                    + " is required by other projects in workspace but incompatible version "
                    + pd.manifest.getVersion()
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
              scope.error(
                  Klab.ErrorContext.PROJECT,
                  Klab.ErrorCode.MISMATCHED_VERSION,
                  "Project "
                      + proj.getFirst()
                      + "@"
                      + proj.getSecond()
                      + " is required by other projects in workspace but incompatible version "
                      + externalProject.getManifest().getVersion()
                      + " is available externally");
              unresolvedProjects.add(proj);
            }
          } else {
            scope.error(
                Klab.ErrorContext.PROJECT,
                Klab.ErrorCode.UNRESOLVED_REFERENCE,
                "Project "
                    + proj.getFirst()
                    + "@"
                    + proj.getSecond()
                    + " is required by other projects in workspace but cannot be resolved from "
                    + "the network");
            unresolvedProjects.add(proj);
          }
        }
      }
    }

    /*
    we have workspaces and project descriptors; load ontologies and namespaces
     */
    for (var ontology : getOntologies(false)) {
      replaceAndIndex(ontology);
    }
    for (var namespace : getNamespaces()) {
      replaceAndIndex(namespace);
    }

    // TODO behaviors

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
          var project = createProjectData(pdesc.name, pdesc.workspace);
          this.projects.put(pdesc.name, project);
          if (ws != null) {
            if (ws.getProjects().stream().anyMatch(p -> p.getUrn().equals(project.getUrn()))) {
              var newProjects =
                  ws.getProjects().stream()
                      .map(p -> p.getUrn().equals(project.getUrn()) ? project : p)
                      .toList();
              ws.getProjects().clear();
              ws.getProjects().addAll(newProjects);
            } else {
              ws.getProjects().add(project);
            }
          }
        }
      }
    }

    /*
    Ensure we have all descriptors for workspaces and projects; if we don't, replace them with
    defaults so that admins can modify them. adding a warning notification
     */
    var kbox = service.getResourcesKbox();
    for (var ws : this.workspaces.keySet()) {
      var info = kbox.getStatus(ws, null);
      boolean wasNull = info == null;
      if (wasNull) {
        info = new ResourceInfo();
        info.setUrn(ws);
        info.setKnowledgeClass(KlabAsset.KnowledgeClass.WORKSPACE);
        // this should be private, but in recovery we don't know the user
        info.setRights(ResourcePrivileges.create("*"));
        info.setType(ResourceInfo.Type.AVAILABLE);
        info.getNotifications()
            .add(
                Notification.warning(
                    "Missing metadata for workspace "
                        + ws
                        + " were reconstructed with public rights"));
        info.getMetadata().put(Metadata.DC_COMMENT, "No information given");
        info.getMetadata().put(Metadata.DC_DATE_CREATED, TimeInstant.create().toRFC3339String());
        info.setOwner(service.serviceScope().getIdentity().toString());
      }
      var workspace = this.workspaces.get(ws);

      for (var project : workspace.getProjects()) {
        var pInfo = kbox.getStatus(project.getUrn(), null);
        if (pInfo == null) {
          pInfo = new ResourceInfo();
          pInfo.setUrn(project.getUrn());
          pInfo.setOwner(info.getOwner());
          pInfo.setRights(info.getRights());
          pInfo.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT);
          pInfo.setType(ResourceInfo.Type.AVAILABLE);
          pInfo
              .getNotifications()
              .add(
                  Notification.warning(
                      "Missing metadata for project "
                          + project.getUrn()
                          + " were reconstructed with public rights"));
          pInfo.getMetadata().put(Metadata.DC_COMMENT, "No information given");
          pInfo.getMetadata().put(Metadata.DC_DATE_CREATED, TimeInstant.create().toRFC3339String());
          Logging.INSTANCE.warn(
              "Recreating lost metadata for project " + project.getUrn() + " with public rights");
          kbox.putStatus(pInfo);
        }
        if (!info.getChildResourceUrns().contains(project.getUrn())) {
          info.getChildResourceUrns().add(project.getUrn());
        }
      }

      if (wasNull) {
        Logging.INSTANCE.warn(
            "Recreating lost metadata for workspace " + ws + " with public rights");
        kbox.putStatus(info);
      }
    }

    /**
     * Add any workspace that didn't have projects configured
     */
    for (var ws : this.configuration.getWorkspaces().keySet()) {
      if (!workspaces.containsKey(ws)) {
        var info = service.getResourcesKbox().getStatus(ws, null);
        if (info != null) {
          WorkspaceImpl wsImpl = new WorkspaceImpl();
          wsImpl.setUrn(ws);
          wsImpl.setPrivileges(info.getRights());
          wsImpl.getMetadata().putAll(info.getMetadata());
          workspaces.put(ws, wsImpl);
        }
      }
    }

    this.loading.set(false);

    return true;
  }

  private Version getWorldviewVersion(String worldview) {
    for (var pd : projectDescriptors.values()) {
      if (worldview.equals(pd.manifest.getDefinedWorldview())) {
        return pd.manifest.getVersion();
      }
    }
    return Version.ANY_VERSION;
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
    var project = this.projectDescriptors.remove(projectName);
    if (project != null && project.storage != null) {
      Workspace workspace = getWorkspace(project.workspace);
      Utils.Files.deleteQuietly(configuration.getLocalPath());
      if (this.configuration.getWorkspaces().get(project.workspace) != null) {
        this.configuration.getWorkspaces().get(project.workspace).remove(projectName);
      }
      workspace.getProjects().remove(project.externalProject);
      saveConfiguration();
    }

    // rebuild all
    loadWorkspace();

    return true;
  }

  private Project.Manifest readManifest(ProjectStorage project) {
    return Utils.Json.load(
        project.listResources(ProjectStorage.ResourceType.MANIFEST).getFirst(),
        ProjectImpl.ManifestImpl.class);
  }

  public WorkspaceImpl getWorkspace(String workspaceName) {
    return updateStatus(this.workspaces.get(workspaceName));
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

  private abstract static class Parser<T extends EObject> {

    @Inject protected IParser parser;

    public Parser() {
      createInjector().injectMembers(this);
    }

    protected abstract Injector createInjector();

    public T parse(InputStream input, List<Notification> errors) {
      return parse(new InputStreamReader(input, StandardCharsets.UTF_8), errors);
    }

    /**
     * Parses data provided by an input reader using Xtext and returns the root node of the
     * resulting object tree.
     *
     * @param reader Input reader
     * @return root object node
     * @throws IOException when errors occur during the parsing process
     */
    public T parse(Reader reader, List<Notification> errors) {
      try {
        IParseResult result = parser.parse(reader);
        for (var error : result.getSyntaxErrors()) {
          System.out.println(error);
          // TODO syntax context
          errors.add(
              Notification.create(
                  error.getSyntaxErrorMessage().getMessage(), Notification.Level.Error));
        }
        return (T) result.getRootASTElement();
      } catch (Throwable throwable) {
        errors.add(Notification.create(throwable));
      }
      return null;
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
            _worldview
                .getObservationStrategies()
                .add(LanguageAdapter.INSTANCE.adaptStrategies(parsed, pd.name, List.of()));
          }
        }
      }
    }

    /*
    Validate the first ontology as the root ontology and set the worldview name from it
     */
    if (!_worldview.getOntologies().isEmpty()) {

      for (var ontology : _worldview.getOntologies()) {
        if (Utils.Notifications.hasErrors(ontology.getNotifications())) {
          _worldview.setEmpty(true);
          scope.error(
              "Namespace "
                  + ontology.getUrn()
                  + " has fatal errors: worldview "
                  + "is "
                  + "inconsistent");
        }
      }

      KimOntology root = _worldview.getOntologies().get(0);
      if (!(root.getDomain() == KimOntology.rootDomain)) {
        _worldview.setEmpty(true);
        scope.error(
            "The first namespace in the worldview is not the root namespace: worldview "
                + "is inconsistent");
      } else {
        _worldview.setUrn(root.getUrn());
      }
    } else {
      _worldview.setEmpty(true);
    }

    return _worldview;
  }

  private void saveConfiguration() {
    File config = BaseService.getFileInConfigurationDirectory(startupOptions, "resources.yaml");
    org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
  }

  /**
   * TODO this one MUST ALSO update the BUILD number in the version, so that whoever uses this can
   * see the changes. and reload. The build number only applies to the document and is not saved
   * with the project.
   *
   * @param projectName
   * @param documentType
   * @param contents
   * @param lockingScope
   * @return
   */
  public List<ResourceSet> updateDocument(
      String projectName,
      ProjectStorage.ResourceType documentType,
      String contents,
      Scope lockingScope) {

    String lockingAuthorization = lockingScope.getIdentity().getId();
    List<ResourceSet> ret = new ArrayList<>();
    RepositoryState repositoryState = null;

    if (lockingAuthorization == null
        || !lockingAuthorization.equals(projectLocks.get(projectName))) {
      return List.of(
          ResourceSet.empty(
              Notification.error("Project " + projectName + " is not locked. Update ignored.")));
    }

    var pd = projectDescriptors.get(projectName);
    if (pd == null || !(pd.storage instanceof FileProjectStorage)) {
      return List.of(
          ResourceSet.empty(
              Notification.error(
                  "Project " + projectName + " is not handled by this service. Update ignored.")));
    }

    /*
    file storage: modify as specified
     */
    List<Notification> notifications = new ArrayList<>();
    var parsed =
        switch (documentType) {
          case ONTOLOGY ->
              ontologyParser
                  .parse(new StringReader(contents), notifications)
                  .getNamespace()
                  .getName();
          case MODEL_NAMESPACE ->
              namespaceParser
                  .parse(new StringReader(contents), notifications)
                  .getNamespace()
                  .getName();
          //            case BEHAVIOR-> null; // TODO
          case STRATEGY ->
              strategyParser
                  .parse(new StringReader(contents), notifications)
                  .getPreamble()
                  .getName();
          default -> throw new KlabUnimplementedException("parsing new " + documentType);
        };

    if (parsed != null && pd.storage instanceof FileProjectStorage fileProjectStorage) {

      // do the update in the stored project and screw it
      var url = fileProjectStorage.update(documentType, parsed, contents);

      ret =
          handleFileChange(
              projectName, List.of(Triple.of(documentType, CRUDOperation.UPDATE, url)));

      repositoryState = fileProjectStorage.getRepositoryState();
    }

    if (repositoryState != null) {
      for (var result : ret) {
        var projectResource = new ResourceSet.Resource();
        projectResource.setResourceVersion(pd.manifest.getVersion());
        projectResource.setProjectUrn(pd.name);
        projectResource.setResourceUrn(pd.name);
        projectResource.setRepositoryState(repositoryState);
        projectResource.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT);
        result.getProjects().add(projectResource);
      }
    }

    return ret;
  }

  public List<ResourceSet> createDocument(
      String projectName,
      ProjectStorage.ResourceType documentType,
      String documentUrn,
      Scope lockingScope) {

    List<ResourceSet> ret = new ArrayList<>();
    String lockingAuthorization = scope.getIdentity().getId();

    if (lockingAuthorization == null
        || !lockingAuthorization.equals(projectLocks.get(projectName))) {
      return List.of(
          ResourceSet.empty(
              Notification.error("Project " + projectName + " is not locked. Update ignored.")));
    }

    var pd = projectDescriptors.get(projectName);
    if (pd == null || !(pd.storage instanceof FileProjectStorage fileProjectStorage)) {
      return List.of(
          ResourceSet.empty(
              Notification.error(
                  "Project " + projectName + " is not handled by this service. Update ignored.")));
    }

    var document = fileProjectStorage.create(documentUrn, documentType);
    if (document != null) {
      return handleFileChange(
          projectName, List.of(Triple.of(documentType, CRUDOperation.CREATE, document)));
    }
    return ret;
  }
}
