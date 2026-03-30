package org.integratedmodelling.klab.modeler;

import fi.iki.elonen.SimpleWebServer;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.UIView;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;
import org.integratedmodelling.klab.modeler.model.NavigableKlabStatement;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.integratedmodelling.klab.modeler.panels.controllers.DocumentEditorControllerImpl;
import org.integratedmodelling.klab.modeler.views.controllers.*;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose
 * the k.Modeler application. Uses an {@link EngineImpl} which will connect to local services if
 * available. Also handles one or more users and keeps a catalog of sessions and contexts, tagging
 * the "current" one in focus in the UI.
 *
 * <p>Call {@link #boot()} in a separate thread when the view is initialized and let the UI events
 * do the rest.
 */
public class ModelerImpl extends AbstractUIController implements Modeler, PropertyHolder {

  private final Map<String, ContextScope> contexts = new LinkedHashMap<>();
  private SimpleWebServer httpServer = null;
  private File tempDirectory;
  private int serverPort;
  EngineConfiguration workbench;
  File workbenchDefinition;

  public ModelerImpl() {
    super();
    // read the workbench config
    this.workbenchDefinition =
        Configuration.INSTANCE.getFileWithTemplate(
            "modeler/workbench.yaml", Utils.YAML.asString(new EngineConfiguration()));
    this.workbench = Utils.YAML.load(workbenchDefinition, EngineConfiguration.class);
  }

  public ModelerImpl(UIView uiView) {
    super(uiView);
  }

  private void dispatchServerMessage(
      KlabService.ServiceCapabilities capabilities, Message message) {
    // TODO do things
    System.out.println(
        "SERVER MESSAGE FROM "
            + capabilities.getType()
            + " "
            + capabilities.getServiceId()
            + ": "
            + message);
  }

  @Override
  public Engine createEngine() {
    return new EngineImpl(
        this::updateEngineStatus,
        (service, status) -> dispatch(this, UIEvent.ServiceStatus, service, status));
  }

  private void updateEngineStatus(Engine.Status status) {
    // TODO on local engines active and no federation, setup local federation so that user can get
    //  messages
    var federation = Klab.INSTANCE.getFederationData(user().getUser());
    if (federation == null || federation.getId().equals(Federation.LOCAL_FEDERATION_ID)) {
      if (status.getCondition() == Engine.Status.EngineCondition.ACTIVE_LOCAL_AND_REMOTE
          || status.getCondition() == Engine.Status.EngineCondition.ACTIVE_LOCAL_ONLY) {
        var localRuntime =
            user().getServices(RuntimeService.class).stream()
                .filter(
                    service ->
                        org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(
                            service.getUrl()))
                .findAny()
                .orElse(null);

        federation = Klab.INSTANCE.setupLocalFederation(user().getUser(), localRuntime);
        if (federation != null && localRuntime != null) {
          // inform the runtime that the local federation is available
          localRuntime.set(Setting.USE_LOCAL_FEDERATION, Map.of(), Map.class);
        }
      }
    }
    dispatch(this, UIEvent.EngineStatusChanged, status);
  }

  @Override
  protected void createView() {

    /*
    pre-built view controllers. View implementations will self-register upon creation.
     */
    registerViewController(new ServicesViewControllerImpl(this));
    //    registerViewController(new DistributionViewImplController(this));
    registerViewController(new ResourcesNavigatorControllerImpl(this));
    registerViewController(new ContextInspectorControllerImpl(this));
    registerViewController(new AuthenticationViewControllerImpl(this));
    registerViewController(new RuntimeControllerImpl(this));
    registerViewController(new KnowledgeInspectorControllerImpl(this));
    // TODO etc.

    /*
    panel classes
     */
    registerPanelControllerClass(DocumentEditorControllerImpl.class);
  }

  @Override
  public void switchWorkbenchService(
      UIReactor requestingReactor, KlabService.ServiceCapabilities service) {
    // TODO
    super.switchWorkbenchService(requestingReactor, service);
  }

  @Override
  public void switchWorkbench(UIReactor requestingReactor, NavigableContainer container) {
    if (getUI() != null) {
      // we assume that the workspace is mainly intended to show documents and focus on assets.
      // Switching the focal container changes all that, so we first clean everything.
      getUI().cleanWorkspace();
    }
    super.switchWorkbench(requestingReactor, container);
  }

  @Override
  public void configureWorkbench(
      UIReactor requestingReactor, NavigableDocument document, boolean shown) {
    // TODO
    super.configureWorkbench(requestingReactor, document, shown);
  }

  @Override
  public CompletableFuture<Observation> observe(
      ContextScope currentContext, Object asset, boolean adding) {

    List<Object> resolvables = new ArrayList<>();
    List<ResolutionConstraint> constraints = new ArrayList<>();
    boolean isObserver = false;

    /** Assets are observed by URN unless they're models or observation definitions */
    if (asset instanceof NavigableKlabStatement<?> navigableAsset) {
      asset = navigableAsset.getDelegate();
    }

    if (asset instanceof KlabStatement statement) {

      if (asset instanceof KimModel || asset instanceof KimSymbolDefinition) {
        constraints.add(
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionNamespace, statement.getNamespace()));
        constraints.add(
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionProject, statement.getProjectName()));
      }

      if (statement instanceof KimModel model) {
        resolvables.add(model.getObservables().getFirst());
        constraints.add(
            ResolutionConstraint.of(ResolutionConstraint.Type.UsingModel, model.getUrn()));
      } else if (statement instanceof KimSymbolDefinition definition) {
        if ("observation".equals(definition.getDefineClass())) {
          resolvables.add(statement);
        } else if ("observer".equals(definition.getDefineClass())) {
          resolvables.add(statement);
          isObserver = true;
          constraints.add(ResolutionConstraint.of(ResolutionConstraint.Type.UseAsObserver));
        }
      } else if (statement instanceof KimConceptStatement conceptStatement) {
        // TODO check observable vs. context (qualities w/ their context etc.)
        resolvables.add(conceptStatement);
      } else if (statement instanceof KimObservable conceptStatement) {
        // TODO check observable vs. context (qualities w/ their context etc.)
        resolvables.add(conceptStatement);
      }
    } else if (asset instanceof String || asset instanceof Urn) {
      resolvables.add(asset.toString());
    }

    /*
    TODO add scenario constraints - scenario controller (TBI) should keep them between contexts
     */

    if (resolvables.isEmpty()) {
      currentContext.warn("No resolvable assets: observation not started");
      return CompletableFuture.completedFuture(Observation.empty());
    }

    var observation = DigitalTwin.createObservation(currentContext, resolvables.toArray());

    if (observation == null) {
      currentContext.error("Cannot create an observation out of " + asset + ": aborting");
      return CompletableFuture.completedFuture(Observation.empty());
    }

    final boolean observering = isObserver;

    // for the benefit of linked DTs - ACHTUNG, this has no ID yet, should be sent by the scope
    currentContext.send(
        Message.MessageClass.DigitalTwin,
        Message.MessageType.ObservationSubmissionStarted,
        observation);
    dispatch(
        this,
        UIEvent.ObservationSubmissionStarted,
        currentContext,
        currentContext.getService(RuntimeService.class),
        observation);

    return currentContext
        .withResolutionConstraints(constraints.toArray(ResolutionConstraint[]::new))
        .submit(observation)
        .exceptionally(
            t -> {
              // for the benefit of linked DTs
              currentContext.send(
                  Message.MessageClass.DigitalTwin,
                  Message.MessageType.ObservationSubmissionAborted,
                  observation);
              // for the UI
              dispatch(
                  this,
                  UIEvent.ObservationSubmissionAborted,
                  currentContext,
                  currentContext.getService(RuntimeService.class),
                  observation);
              currentContext.error(
                  "Resolution of observation "
                      + observation
                      + " was aborted"
                      + " due to errors: "
                      + Utils.Exceptions.stackTrace(t));

              return observation;
            })
        .thenApply(
            obs -> {
              // for the benefit of linked DTs
              currentContext.send(
                  Message.MessageClass.DigitalTwin,
                  Message.MessageType.ObservationSubmissionFinished,
                  obs);
              dispatch(
                  this,
                  UIEvent.ObservationSubmissionFinished,
                  currentContext,
                  currentContext.getService(RuntimeService.class),
                  obs);
              if (obs.isEmpty()) {
                currentContext.error(
                    "Observation " + observation + " was not resolved due to errors");
              } else {
                if (observering) {
                  // Send a DT focus event with observer emphasis. The
                  //  observation will be in the KG anyway.
                  currentContext.send(
                      Message.MessageClass.DigitalTwin, Message.MessageType.ObserverResolved, obs);
                  //                  setCurrentContext(currentContext.withObserver(obs));
                  currentContext.ui(
                      Message.create(
                          currentContext,
                          Message.MessageClass.UserInterface,
                          Message.MessageType.CurrentContextModified));
                  dispatch(
                      this,
                      UIEvent.ObserverResolved,
                      currentContext,
                      currentContext.getService(RuntimeService.class),
                      obs);
                  currentContext.info(obs + " is now the current observer");
                } else {
                  currentContext.info("Observation of " + obs + " resolved successfully");
                }
              }
              return obs;
            });
  }

  //  @Override
  public ContextScope openNewContext(
      DigitalTwin.Configuration configuration, boolean dispatchEvent) {
    var runtimeService = user().getService(RuntimeService.class);
    if (runtimeService == null) {
      user()
          .error("Cannot create a context without a runtime service", UIView.Interactivity.DISPLAY);
      return null;
    }
    var ret = user().getUserSession(runtimeService).createContext(configuration);
    if (ret != null) {
      contexts.put(ret.getId(), ret);
      if (dispatchEvent) {
        dispatch(this.getController(), UIEvent.ContextCreated, ret, runtimeService);
      }
    }
    return ret;
  }

  //  @Override
  //  public List<ContextScope> getOpenContexts() {
  //    return new ArrayList<>(contexts.values());
  //  }

  @Override
  public ContextScope createDefaultContext() {

    ContextScope currentContext = null;

    if (currentUser() == null) {
      throw new KlabAuthorizationException("Cannot make observations with an invalid user");
    }

    var name =
        // TODO revise, but it's more fun than DT1, DT2 etc
        Utils.Strings.capitalize(
            Utils.Words.makeUpName(
                "elephant",
                "intelligence",
                "code",
                "planet",
                "environment",
                "cacophony",
                "paradox",
                "conundrum",
                "troglodyte",
                "anaconda",
                "octothorpe",
                "chicken",
                "blasphemy",
                "enema",
                "pain",
                "gibbon",
                "slumgullion",
                "knowledge",
                "wisdom",
                "avocado",
                "suffering",
                "sausage",
                "cucumber"));

    var configuration = defaultDigitalTwinConfiguration(name);

    if (configuration != null) {
      currentContext = openNewContext(configuration, false);
    }

    return currentContext;
  }

  private DigitalTwin.Configuration defaultDigitalTwinConfiguration(String name) {
    var runtime =
        user()
            .findService(RuntimeService.class, service -> Utils.URLs.isLocalHost(service.getUrl()))
            .orElse(null);

    if (runtime == null) {
      user()
          .error(
              "A default digital twin can only be created in a locally running runtime service.",
              UIView.Interactivity.DISPLAY);
      return null;
    }

    return DigitalTwin.Configuration.builder()
        .name(name)
        .serverUrl(runtime.getUrl())
        .serviceId(runtime.serviceId())
        .accessRights(ResourcePrivileges.create(user()))
        .persistence(Persistence.IDLE_TIMEOUT)
        .build();
  }

  @Override
  public void importProject(
      ResourcesService resources,
      String workspaceName,
      String projectUrl,
      boolean overwriteExisting) {

    Thread.ofVirtual()
        .start(
            () -> {
              // TODO use import schema, then resolve project to obtain the ResourceSet
              throw new KlabUnimplementedException("import project");
              //                var ret = admin.importProject(workspaceName, projectUrl,
              // overwriteExisting, currentUser());
              //                if (ret != null) {
              //                    handleResultSets(ret);
              //                }
            });
  }

  @Override
  public void deleteProject(ResourcesService resources, String projectUrl) {

    Thread.ofVirtual()
        .start(
            () -> {
              handleResultSets(resources.deleteProject(projectUrl, currentUser()));
            });
  }

  @Override
  public boolean updateDocument(
      ResourcesService service,
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType,
      String updatedContent) {

    Thread.ofVirtual()
        .start(
            () -> {
              handleResultSets(
                  service.updateDocument(projectName, documentType, updatedContent, currentUser()));
            });
    return true;
  }

  @Override
  public void deleteAsset(ResourcesService resources, NavigableAsset asset) {

    if (asset instanceof KlabDocument<?> document) {
      Thread.ofVirtual()
          .start(
              () -> {
                var project = asset.parent(NavigableProject.class);
                var ret =
                    resources.deleteDocument(
                        project.getUrn(),
                        asset.getUrn(),
                        ProjectStorage.ResourceType.classify(document),
                        currentUser());
                handleResultSets(ret);
              });
    }
  }

  @Override
  public void manageProject(
      ResourcesService resources,
      String projectId,
      RepositoryState.Operation operation,
      String... arguments) {

    Thread.ofVirtual()
        .start(
            () -> {
              var ret = resources.manageRepository(projectId, operation, arguments);
              handleResultSets(ret);
            });
  }

  private boolean handleResultSets(List<ResourceSet> ret) {
    var result = true;
    if (ret != null && !ret.isEmpty()) {
      for (var change : ret) {
        if (change.isEmpty() || Utils.Notifications.hasErrors(change.getNotifications())) {
          result = false;
        }
        dispatch(
            this,
            UIEvent.WorkspaceModified,
            getUI() == null ? change : getUI().processAlerts(change));
      }
    }
    return result;
  }

  @Override
  public void editProperties(ResourcesService service, String projectId) {}

  @Override
  public boolean createDocument(
      ResourcesService service,
      String newDocumentUrn,
      String projectName,
      ProjectStorage.ResourceType documentType) {

    Thread.ofVirtual()
        .start(
            () -> {
              var changes =
                  service.createDocument(projectName, newDocumentUrn, documentType, currentUser());
              if (changes != null) {
                for (var change : changes) {
                  dispatch(
                      this,
                      UIEvent.WorkspaceModified,
                      getUI() == null ? change : getUI().processAlerts(change));
                }
              }
            });
    return true;
  }

  @Override
  public UserScope user() {
    return ((EngineImpl) engine()).getUser();
  }

  //  public Future<Boolean> startLocalServices() {
  //    return CompletableFuture.completedFuture(false);
  //  }

  public CompletableFuture<Boolean> shutdownLocalServices() {

    List<BaseServiceClient> services = new ArrayList<>();
    for (var serviceType : List.of(KlabService.Type.RESOURCES)) {
      for (var service : engine().getOwner().getServices(serviceType.classify())) {
        if (service instanceof BaseServiceClient serviceClient && serviceClient.isLocal()) {
          services.add(serviceClient);
        }
      }
    }

    return CompletableFuture.supplyAsync(
        () -> {
          // 10 sec timeout
          final long timeout = 10000L;
          var ns = services.size();
          if (ns > 0) {

            Logging.INSTANCE.warn("Waiting for " + services.size() + " local services to exit");

            long time = System.currentTimeMillis();
            while (true) {

              int n = 0;
              for (var client : services) {
                if (!client.isAlive()) {
                  n++;
                }
              }

              if (n == services.size()) {
                Logging.INSTANCE.warn("All local services have shut down");
                return true;
              }

              if ((System.currentTimeMillis() - time) > timeout) {
                Logging.INSTANCE.error("Timeout reached: shutdown unsuccessful, continuing");
                break;
              }

              try {
                Thread.sleep(300);
              } catch (InterruptedException e) {
                Logging.INSTANCE.error("Thread exception: shutdown unsuccessful, continuing");
                break;
              }
            }
          }

          return false;
        });
  }

  @Override
  public boolean shutdown(boolean shutdownLocalServices) {

    if (httpServer != null) {
      httpServer.stop();
    }

    if (shutdownLocalServices) {
      try {
        return shutdownLocalServices().thenApply(b -> engine().shutdown()).get();
      } catch (Exception e) {
        Logging.INSTANCE.error("Shutdown procedure terminated with errors", e);
        return false;
      }
    }

    return engine().shutdown();
  }

  @Override
  protected Scope scope() {
    return user();
  }

  @Override
  public String configurationPath() {
    return "modeler";
  }

  public UserScope currentUser() {
    return engine() == null || engine().getUsers().isEmpty()
        ? null
        : engine().getUsers().getFirst();
  }

  @Override
  public UIController getController() {
    return this;
  }

  @Override
  public URL publishLocally(File inputFile, String workspace, File... additionalFiles) {
    if (this.httpServer == null) {
      try {
        this.tempDirectory = Files.createTempDirectory("klab-local").toFile();
        if (workspace != null) {
          new File(tempDirectory, workspace).mkdirs();
        }
        this.serverPort = Utils.URLs.findAvailablePort();
        this.httpServer = new SimpleWebServer("localhost", serverPort, tempDirectory, false);
        Utils.Files.touch(new File(tempDirectory + File.separator + "favicon.ico")); // JFC
        Logging.INSTANCE.info("Starting local web server on port " + serverPort);
        this.httpServer.start();
      } catch (IOException e) {
        throw new RuntimeException("Could not initialize local web server", e);
      }
    }

    List<File> files = new ArrayList<>();
    files.add(inputFile);
    if (additionalFiles != null) {
      files.addAll(Arrays.asList(additionalFiles));
    }

    try {
      for (var file : files) {
        Files.copy(
            file.toPath(),
            new File(
                    tempDirectory,
                    workspace == null ? inputFile.getName() : workspace + "/" + file.getName())
                .toPath());
      }
      return new URL(
          "http://localhost:"
              + serverPort
              + "/"
              + (workspace == null ? "" : workspace + "/")
              + inputFile.getName());
    } catch (IOException e) {
      throw new KlabIOException("Error publishing file " + inputFile);
    }
  }

  @Override
  public boolean createProject(ResourcesService service, String projectName, String workspaceName) {
    if (projectName != null) {
      return handleResultSets(List.of(service.createProject(workspaceName, projectName, user())));
    }
    return false;
  }
}
