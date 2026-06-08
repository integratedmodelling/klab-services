package org.integratedmodelling.klab.services.runtime;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4JClient;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.ojalgo.concurrent.Parallelism;

public class RuntimeService extends BaseService
    implements org.integratedmodelling.klab.api.services.RuntimeService,
        org.integratedmodelling.klab.api.services.RuntimeService.Admin {

  private String hardwareSignature = Utils.Strings.hash(Utils.OS.getMACAddress());
  private RuntimeConfiguration configuration;
  private KnowledgeGraphNeo4j knowledgeGraph;
  //  private SystemLauncher systemLauncher;
  private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  /**
   * We keep identification strategies for each concept encountered. The base one is implemented for
   * odo:Substantial.
   */
  private Map<Concept, IdentificationStrategy> identificationStrategies = new HashMap<>();

  private IdentificationStrategy defaultIdentificationStrategy =
      new IdentificationStrategy() {

        @Override
        public int compare(Observation o1, Observation o2) {
          // TODO add prefix. O1 is the unknown observation so it gets the prefix if it doesn't have
          // one
          return o1.getUrn().compareTo(o2.getUrn());
        }

        @Override
        public String getUrn() {
          return "identification.strategy.default";
        }

        @Override
        public Metadata getMetadata() {
          return Metadata.create();
        }

        @Override
        public Collection<Annotation> getAnnotations() {
          return List.of();
        }
      };

  public RuntimeService(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {
    super(scope, Type.RUNTIME, options);
    readConfiguration(options);
    setComponentRegistry();
    ServiceConfiguration.INSTANCE.setMainService(this);
    initializeMessaging();
  }

  private void initializeMessaging() {
    if (startupOptions.isStartLocalBroker()) {
      Utils.DebugFile.println("Starting embedded broker for local messaging");
      //      this.embeddedBroker = new EmbeddedBroker();
    } else {
      Utils.DebugFile.println("NOT starting embedded broker for local messaging");
    }
  }

  private void readConfiguration(ServiceStartupOptions options) {
    File config = BaseService.getFileInConfigurationDirectory(options, "runtime.yaml");
    if (config.exists() && config.length() > 0 && !options.isClean()) {
      try {
        this.configuration = Utils.YAML.load(config, RuntimeConfiguration.class);
      } catch (Exception e) {
        Logging.INSTANCE.warn("Configuration file is being reset after corruption was detected");
        Utils.Files.deleteQuietly(config);
        this.configuration = new RuntimeConfiguration();
        this.configuration.setServiceId(UUID.randomUUID().toString());
        Utils.YAML.save(this.configuration, config);
      }
    } else {
      // make an empty config
      this.configuration = new RuntimeConfiguration();
      this.configuration.setServiceId(UUID.randomUUID().toString());
      saveConfiguration();
    }
    // for the local client to know when the service is off
    super.setRuntimeLockfile(this.configuration.getServiceId());
  }

  private boolean createMainKnowledgeGraph() {
    // TODO choose the DB from configuration - client or embedded server
    //    var path = BaseService.getConfigurationSubdirectory(startupOptions, "dt").toPath();
    this.knowledgeGraph = new KnowledgeGraphNeo4JClient(this.configuration.getGraphDatabaseUrl());
    return this.knowledgeGraph.isOnline();
  }

  public KnowledgeGraphNeo4j getMainKnowledgeGraph() {
    return this.knowledgeGraph;
  }

  private void saveConfiguration() {
    File config = BaseService.getFileInConfigurationDirectory(startupOptions, "runtime.yaml");
    org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
  }

  @Override
  public boolean initializeService() {

    Logging.INSTANCE.setSystemIdentifier("Runtime service: ");

    if (settings instanceof SettingsImpl settingsImpl) {
      settingsImpl.setExecutionHandler(
          Setting.USE_LOCAL_FEDERATION,
          value -> {
            if (serviceScope().getIdentity() instanceof UserIdentity userIdentity) {
              Klab.INSTANCE.setupLocalFederation(userIdentity, this);
            }
            return null;
          });
    }

    if (createMainKnowledgeGraph()) {
      // internal libraries
      getComponentRegistry().loadExtensions("org.integratedmodelling.klab.services.runtime");
      getComponentRegistry()
          .initializeComponents(
              BaseService.getConfigurationSubdirectory(startupOptions, "components"));
    } else {
      Logging.INSTANCE.error("Cannot connect to the knowledge graph database");
      this.setOperational(
          false, Notification.error("Cannot connect to the knowledge graph database"));
      return false;
    }

    return true;
  }

  @Override
  public boolean operationalizeService() {
    // start the timed DT maintenance process
    Logging.INSTANCE.info("Starting scheduled DT maintenance thread");
    scheduler.scheduleAtFixedRate(
        () -> {
          dtScheduledMaintenance();
        },
        0,
        5,
        TimeUnit.MINUTES);

    return true;
  }

  private void dtScheduledMaintenance() {
    //    for (var session : getSessionInfo(serviceScope())) {
    for (var context : getContextInfo(serviceScope())) {
      checkForOrphanContext(context);
    }
    //    }
  }

  private void checkForOrphanContext(ContextInfo context) {
    Logging.INSTANCE.info(
        "Checking for orphan context "
            + context.getConfiguration().getName()
            + "/"
            + context.getConfiguration().getId()
            + " "
            + context.getConfiguration().getPersistence()
            + " created "
            + TimeInstant.create(context.getCreationTime())
            + " idle "
            + Utils.Time.formatDuration(context.getIdleTimeMs()));

    if (!context.getConfiguration().getPersistence().persistent) {
      var orphan = false;
      var timeout = false;
      var reinit = false;
      var maxIdleTime =
          settings().get(Setting.DIGITAL_TWIN_TIMEOUT_MINUTES, Integer.class)
              * TimeUnit.MINUTES.toMillis(1);
      var maxIdleReinitTime =
          settings().get(Setting.DIGITAL_TWIN_REINITIALIZATION_TIMEOUT_MINUTES, Integer.class)
              * TimeUnit.MINUTES.toMillis(1);
      var existingScope =
          getScopeManager().getScope(context.getConfiguration().getId(), ServiceContextScope.class);
      if (existingScope == null) {
        orphan = true;
      } else if (context.getConfiguration().getPersistence() == Persistence.IDLE_TIMEOUT) {
        timeout = context.getIdleTimeMs() > maxIdleTime;
      } else if (context.getConfiguration().getPersistence()
          == Persistence.REINITIALIZED_ON_TIMEOUT) {
        reinit = context.getIdleTimeMs() > maxIdleReinitTime;
      }

      final boolean horphan = orphan;
      if (orphan || timeout) {
        executorService.submit(
            () -> {
              Utils.DebugFile.println(
                  "Orphan context "
                      + context.getConfiguration().getName()
                      + "/"
                      + context.getConfiguration().getId()
                      + " being removed due to "
                      + (horphan ? "being orphaned" : "inactivity"));

              Logging.INSTANCE.info(
                  "Orphan context "
                      + context.getConfiguration().getName()
                      + "/"
                      + context.getConfiguration().getId()
                      + " being removed due to "
                      + (horphan ? "being orphaned" : "inactivity"));
              if (existingScope != null) {
                existingScope.close();
              } else {
                // yank it off the knowledge graph
                knowledgeGraph.deleteContext(context, serviceScope());
                StorageManagerImpl.removeStorage(context, this);
              }
            });
      } else if (reinit) {
        executorService.submit(
            () -> {
              Logging.INSTANCE.info(
                  "Reinitializing context "
                      + context.getConfiguration().getName()
                      + "/"
                      + context.getConfiguration().getId()
                      + " due to inactivity");
              existingScope.reinitialize();
            });
      }
    }
  }

  @Override
  public boolean shutdown() {

    /** Close every scope that's scheduled for closing at service shutdown */
    for (var scope : getScopeManager().getScopes(Scope.Type.CONTEXT, ContextScope.class)) {
      if (scope instanceof ServiceContextScope serviceContextScope
          && serviceContextScope.getConfiguration().getPersistence()
              == Persistence.SERVICE_SHUTDOWN) {
        scope.send(
            Message.MessageClass.SessionLifecycle,
            Message.MessageType.ContextClosed,
            scope.getId());
        scope.close();
        Logging.INSTANCE.info("Context " + scope.getId() + " closed upon service shutdown");
      }
    }

    if (knowledgeGraph != null) {
      knowledgeGraph.shutdown();
    }
    return super.shutdown();
  }

  @Override
  protected org.integratedmodelling.klab.services.configuration.ServiceConfiguration
      getServiceConfiguration() {
    return this.configuration;
  }

  @Override
  public Capabilities capabilities(Scope scope) {

    var ret = new RuntimeCapabilitiesImpl();
    ret.setServiceName(serviceName);
    ret.setType(Type.RUNTIME);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("RUNTIME_" + hardwareSignature));
    ret.setServiceId(configuration.getServiceId());
    //    ret.setBroker(getEmbeddedBroker() != null);

    // TODO this enables creating DTs from the passed scope
    ret.getPermissions()
        .addAll(
            EnumSet.of(
                CRUDOperation.CREATE,
                CRUDOperation.READ,
                CRUDOperation.UPDATE,
                CRUDOperation.DELETE));

    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());
    ret.getComponents().addAll(getComponentRegistry().getComponents(scope));
    ret.setDefaultStorageType(configuration.getNumericStorageType());

    return ret;
  }

  public String serviceId() {
    return configuration.getServiceId();
  }

  @Override
  public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
    Map<String, String> ret = new HashMap<>();
    return ret;
  }

  /**
   * The context declaration in the runtime will create a server-side digital twin IIF the service
   * in the scope is this service.
   *
   * @param contextScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @param sessionScope used to set up federated behavior
   * @param userScope used to set up federated behavior
   * @return
   */
  @Override
  public DigitalTwin.Configuration declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope) {

    if (!serviceId().equals(contextScope.getHostServiceId())) {
      return super.declareContextScope(contextScope, sessionScope, userScope);
    }

    if (contextScope instanceof ServiceContextScope serviceContextScope) {

      boolean isNew = serviceContextScope.getConfiguration().getId() == null;
      String scopeId =
          isNew
              ? serviceContextScope.getParentScope().getId() + "." + Utils.Names.shortUUID()
              : serviceContextScope.getConfiguration().getId();

      if (serviceContextScope.getConfiguration() instanceof ConfigurationImpl configurationImpl) {
        configurationImpl.setServiceId(serviceId());
      }

      serviceContextScope.setId(scopeId);
      getScopeManager().registerScope(serviceContextScope);

      /*
       * this may take a while and it's done within a response. We should either spawn a thread (but then wait for the
       * DT in subsequent calls) or make the context creation call asynchronous.
       */
      serviceContextScope.setDigitalTwin(
          new DigitalTwinImpl(
              this, serviceContextScope, scopeId, userScope, getMainKnowledgeGraph()));

      return serviceContextScope.getConfiguration();
    }
    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  private Agent getAgent(ContextScope scope) {

    var ret = Provenance.getAgent(scope);
    if (ret != null) {
      return ret;
    }
    if (scope instanceof ServiceContextScope serviceContextScope) {
      // assume the user is the agent
      return serviceContextScope.getDigitalTwin().getKnowledgeGraph().user();
    }
    throw new KlabIllegalStateException("Cannot determine the requesting agent from scope");
  }

  /**
   * Return the configured computation builder for the passed observation and scope. This may
   * eventually analyze the scope and the dataflow to assess which kind of computation fits the
   * problem best. Different runtimes may support Spark or other computational engines. The default
   * for now is to use the Groovy builder.
   */
  public ScalarComputation.Builder getComputationBuilder(
      Observation observation,
      ServiceContextScope scope,
      Actuator actuator,
      Map<String, Observation> observations) {
    return ScalarComputationGroovy.builder(observation, scope, actuator, observations);
  }

  @Override
  public KnowledgeGraph.Commit getCommit(long commitId, ContextScope scope) {
    if (scope.getDigitalTwin() instanceof DigitalTwinImpl dt) {
      return dt.getCommit(commitId);
    }
    return null;
  }

  @Override
  public ServiceInfo getServiceInfo(String urn, Scope scope) {
    var ret = getComponentRegistry().getFunctionDescriptor(urn, Version.ANY_VERSION);
    return ret.isEmpty() ? null : ret.stream().map(s -> s.serviceInfo).findFirst().orElse(null);
  }

  /**
   * Submission happens entirely within a transaction, created new at the root submission.
   * Observations are directly used as keys for everything, so the object may be updated but must
   * never be substituted with another. All observations created upon submissions will have no valid
   * URN or ID until the root transaction is committed. Each submission creates a submission
   * activity followed by resolution and, if successful, contextualization of all resolved
   * observations. Instantiators cause other submissions within the same transaction.
   *
   * @param submitted the observation to submit
   * @param scope the context scope in which to submit the observation
   * @return the submission task
   */
  @Override
  public CompletableFuture<Observation> submit(Observation submitted, ContextScope scope) {

    var observation = register(submitted, scope);

    if (observation.getId() > 0 || observation.isEmpty()) {
      return CompletableFuture.completedFuture(observation);
    }

    if (scope instanceof ServiceContextScope serviceContextScope) {

      /*
       * Check for pre-existing observations, either from the KG or transaction, or instances from a
       * definition in the same scope. Only checked if we're not submitting the children of
       * an instantiator, which must be submitted no matter what.
       */
      var instantiating =
          scope.getContextObservation() != null
              && scope.getContextObservation().getObservable().getSemantics().isCollective();

      if (!instantiating) {
        var existing = scope.getObservation(observation);
        if (existing != null) {
          return CompletableFuture.completedFuture(existing);
        }
      } else {
        /**
         * TODO we must check the existing cohorts and build the observation that queries the
         * objects in the requested geometry. Any missing coverage will become the next
         * observation's geometry. For the existing ones, we must resolve any non-identifying
         * predicates as well.
         */
      }

      if (observation.getObservable().is(SemanticType.QUALITY)
          && scope.getContextObservation() == null) {
        return CompletableFuture.completedFuture(
            Observation.empty(
                Notification.error("Cannot observe a quality without a context observation")));
      }

      /* Dependents are the only situation when we accept an observation w/o geometry */
      if (observation.getGeometry() == null
          && observation instanceof ObservationImpl observation1) {
        if (observation.getObservable().is(SemanticType.QUALITY)
            && scope.getContextObservation() != null) {
          observation1.setGeometry(scope.getContextObservation().getGeometry());
        } else if (observation.getObservable().is(SemanticType.COUNTABLE)
            && observation.getObservable().getSemantics().isCollective()
            && scope.getObserver() != null) {
          // FIXME no - this should run a query over the cohort and if needed, resolve the
          //  unaddressed coverage. If the observation has id == 0, it is a query and it can
          //  use the overall covered geometry of the cohort if the geometry is not there.
          observation1.setGeometry(scope.getObserver().getGeometry());
        }
      }

      // sanitize whatever geometry we have before any use is made of it. TODO add a flag or
      // something to
      //  avoid doing this when not necessary.
      if (observation instanceof ObservationImpl observation1) {
        var geometry = observation.getGeometry();
        if (geometry != null) {
          geometry = GeometryRepository.INSTANCE.sanitize(geometry);
          observation1.setGeometry(geometry);
        }
      }

      /* adjourn contextualization data with our service coordinates */
      ObservationImpl.ContextualizationDataImpl contextualizationData =
          observation.getContextualizationData()
                  instanceof ObservationImpl.ContextualizationDataImpl cd
              ? cd
              : new ObservationImpl.ContextualizationDataImpl();

      /*
       * Due diligence to verify that the adapter is there and available.
       */
      Observation.ContextualizationData predefinedContextualization;
      if (observation.getContextualizationData() != null) {

        // first check if we have the adapter locally
        if (getComponentRegistry()
                .getAdapter(
                    observation.getContextualizationData().getAdapterId(),
                    Version.ANY_VERSION, // TODO this should probably be deprecated in favor of
                    // urn@version
                    scope)
            == null) {

          // TODO use all services!
          var requirements =
              scope
                  .getService(ResourcesService.class)
                  // FIXME use the generic resource resolution service
                  .resolveResourceAdapter(contextualizationData.getAdapterId(), scope);
          if (requirements == null || requirements.isEmpty()) {
            return CompletableFuture.completedFuture(
                Observation.empty(
                    Notification.error(
                        "Adapter '"
                            + contextualizationData.getAdapterId()
                            + "' referenced in submission is not visible to the digital twin runtime")));
          }
          if (!ingestResources(
              requirements,
              scope,
              settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class))) {
            return CompletableFuture.completedFuture(
                Observation.empty(
                    Notification.error(
                        "Adapter '"
                            + contextualizationData.getAdapterId()
                            + "' referenced in submission is not accessible to the digital twin runtime")));
          }

          if (settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)) {
            var adapter =
                getComponentRegistry()
                    .getAdapter(contextualizationData.getAdapterId(), Version.ANY_VERSION, scope);
            if (adapter == null || !adapter.isEmbeddable()) {
              return CompletableFuture.completedFuture(
                  Observation.empty(
                      Notification.error(
                          "Adapter '"
                              + contextualizationData.getAdapterId()
                              + "' referenced in submission is unavailable or not embeddable into to the digital twin runtime")));
            }
          }
        }

        // we have the adapter, no need to call the resolver but we must embed the call into
        // the resolution step. Only direct submissions are supported with this method.
        predefinedContextualization = contextualizationData;
      } else {
        predefinedContextualization = null;
      }

      contextualizationData.setServiceId(serviceId());
      contextualizationData.setServiceUrl(getUrl());
      if (observation.getContextualizationData() == null
          && observation instanceof ObservationImpl observationImpl) {
        observationImpl.setContextualizationData(contextualizationData);
      }

      var isRoot = serviceContextScope.getActivity() == null;
      var agent =
          serviceContextScope.getConstraint(ResolutionConstraint.Type.Provenance, Agent.class);
      var storedAgent =
          agent == null
              ? null
              : serviceContextScope
                  .getDigitalTwin()
                  .getKnowledgeGraph()
                  .requireAgent(agent.getName());

      var submission =
          Activity.of(
              Activity.Type.SUBMISSION,
              this,
              observation,
              scope,
              serviceContextScope.getActivity(),
              observation + " submitted");

      var submissionScope =
          serviceContextScope.executing(submission, isRoot ? storedAgent : null, observation);
      var resolver = scope.getService(Resolver.class);
      var resolution =
          Activity.of(
              "Resolution of " + observation,
              Activity.Type.RESOLUTION,
              this,
              submission,
              "Resolution of " + observation,
              submissionScope);

      var cohort = getCohortFor(observation.getObservable(), submissionScope, true);

      submissionScope
          .getCurrentTransaction()
          .link(
              scope.getContextObservation() == null
                  ? (cohort == null ? RuntimeAsset.CONTEXT_ASSET : cohort)
                  : scope.getContextObservation(),
              observation,
              (scope.getContextObservation() == null && cohort != null)
                  ? GraphModel.Relationship.HAS_MEMBER
                  : GraphModel.Relationship.HAS_CHILD);

      if (cohort != null
          && scope.getContextObservation() != null
          && observation.getObservable().is(SemanticType.COUNTABLE)) {
        // ALSO link the observation to the cohort, which wasn't done in the previous statement
        submissionScope
            .getCurrentTransaction()
            .link(cohort, observation, GraphModel.Relationship.HAS_MEMBER);
        /* include the observation's geometry in the cohort's and check a flag if there were changes. If we're
        adding instances from an instantiator, we add the full collective. Otherwise each geometry will be added at
         */
        if (observation.getObservable().getSemantics().isCollective()) {
          updateCohortGeometry(cohort, observation, submissionScope);
        }
      }

      submissionScope
          .getCurrentTransaction()
          .link(submission, observation, GraphModel.Relationship.CREATED);

      if (scope.getContextObservation() != null) {
        submissionScope
            .getCurrentTransaction()
            .link(submission, scope.getContextObservation(), GraphModel.Relationship.HAS_CONTEXT);
      }

      if (scope.getObserver() != null) {
        submissionScope
            .getCurrentTransaction()
            .link(submission, scope.getObserver(), GraphModel.Relationship.HAS_OBSERVER);
      }

      // keep the k.LAB ownership for the resolution only if we're the root action.
      // FIXME the observer should take care of this but for now the k.LAB actor isn't considered
      var resolutionScope =
          submissionScope
              .executing(
                  resolution,
                  isRoot ? scope.getDigitalTwin().getKnowledgeGraph().klab() : null,
                  observation)
              //  Add any cohort and identification strategy needed for KG maintenance.
              .contextualizeFor(observation);

      /*
       * Save the resolution constraints in the metadata for debugging and provenance. This includes
       * scenarios, project and namespace.
       */
      resolution
          .getMetadata()
          .put("constraints", Utils.Json.asString(resolutionScope.getResolutionConstraints()));

      return (predefinedContextualization != null
              ? createPredefinedDataflow(predefinedContextualization, observation, scope)
              : resolver
                  /* resolve asynchronously. If there are contextualization data the resolver will compile them in. */
                  .resolve(observation, resolutionScope))
          .exceptionally(
              t -> {
                resolutionScope.fail(t);
                var ret = Dataflow.empty();
                ret.getNotifications().add(Notification.error(t.getMessage(), t));
                return ret;
              })
          /* then compile the dataflow */
          .thenApply(
              dataflow -> {
                observation.getNotifications().addAll(dataflow.getNotifications());
                var encoded =
                    org.integratedmodelling.common.utils.Utils.Dataflows.encode(
                        dataflow, resolutionScope);
                resolution.getMetadata().put("dataflow", encoded);
                if (!dataflow.isEmpty()) {
                  if (compile(observation, dataflow, resolutionScope)) {
                    if (resolutionScope.commit() >= 0) {
                      if (predefinedContextualization != null) {
                        publishContextualization(observation, resolutionScope);
                      }
                      return observation;
                    }
                  }
                }
                resolutionScope.fail();
                return Observation.empty(
                    Notification.error(
                        "Resolution of "
                            + observation.getObservable().getUrn()
                            + " failed: empty dataflow"));
              })
          /* then submit the observation to the scheduler, which will trigger contextualization */
          .thenApply(
              o -> {

                // FIXME this comes from the original submission while the executors and storage
                //  have been registered with the observations in the actuators. These are only
                //  correct if the observations have been created at compilation.

                if (!o.isEmpty()) {
                  submissionScope.getCurrentTransaction().registerExecutors();
                  submissionScope.contextualize(o);

                  // TODO add more info about the contextualization to the action's metadata
                  submission.setName("SUB OK");
                  var commitId = submissionScope.commit();
                  if (commitId > 0) {
                    o.getMetadata().put(Metadata.IM_COMMIT_ID, commitId);
                  }
                } else {
                  submission.setName("SUB FAIL");
                  submissionScope.fail();
                  o.getNotifications().add(Notification.error("Submission failed"));
                }
                return o;
              })
          .exceptionally(
              t -> {
                submissionScope.fail(t);
                var ret = Observation.empty();
                ret.getNotifications().add(Notification.error(t.getMessage(), t));
                return ret;
              });
    }
    throw new KlabInternalErrorException(
        "RuntimeService::observe() called with unexpected scope implementation");
  }

  /**
   * @param cohort
   * @param observation
   * @param submissionScope
   */
  private void updateCohortGeometry(
      Cohort cohort, Observation observation, ServiceContextScope submissionScope) {
    var total = cohort.getGeometry();
    var incoming = observation.getGeometry();
    if (total.isUniversal()) {
      total = incoming;
    } else {
      total = GeometryRepository.INSTANCE.outerUnion(total, incoming);
    }
    var transaction = submissionScope.getCurrentTransaction();
    // TODO set the geometry for the cohort's observable
  }

  /**
   * Find an observation with the same identity as the given observation in the given cohort.
   *
   * @param observation
   * @param cohort
   * @param submissionScope
   * @return
   */
  private Observation checkIdentity(
      Observation observation, Cohort cohort, ServiceContextScope submissionScope) {

    if (cohort.getId() < 0) {
      // cohort is new, can't have observations
      return null;
    }

    var reasoner = submissionScope.getService(Reasoner.class);
    //    var comparisonStrategy =
    //        reasoner.computeIdentificationStrategies(observation.getObservable(),
    // submissionScope);
    var identificationStrategy = defaultIdentificationStrategy;
    //    if (comparisonStrategy != null) {
    // TODO compile into identificationStrategy
    //    }

    for (var sibling :
        submissionScope
            .getDigitalTwin()
            .getKnowledgeGraph()
            .query(Observation.class, scope)
            .source(cohort)
            .along(GraphModel.Relationship.HAS_MEMBER)
            .run(submissionScope)) {
      if (identificationStrategy.compare(observation, sibling) == 0) {
        return sibling;
      }
    }

    return null;
  }

  @Override
  public Observation register(Observation observation, ContextScope scope) {

    if (observation.getId() < -1 || observation.getId() > 0 || observation.isEmpty()) {
      return observation;
    }

    var mayExistInCohort =
        SemanticType.isSubstantial(observation.getObservable().getSemantics().getType())
            && !observation.getObservable().getSemantics().isCollective();

    if (observation instanceof ObservationImpl observationImpl
        && scope instanceof ServiceContextScope serviceContextScope) {

      // TODO this will need to be more sophisticated re: contextualization when looking for events
      //  and relationships

      if (mayExistInCohort) {
        // query for the object's identity within the cohort. If existing, extract and return it
        var cohort = getCohortFor(observation.getObservable(), scope, false);
        if (cohort != null) { // should never happen
          var existing = checkIdentity(observation, cohort, serviceContextScope);
          if (existing != null) {
            return existing;
          }
        }
      } else if (SemanticType.isDependent(observation.getObservable().getSemantics().getType())) {

        if (serviceContextScope.getContextObservation() != null) {

          var existing =
              serviceContextScope
                  .getChildrenOf(serviceContextScope.getContextObservation())
                  .stream()
                  .filter(
                      child ->
                          child instanceof Observation oChild
                              && oChild
                                  .getObservable()
                                  .asConcept()
                                  .getUrn()
                                  .equals(
                                      observation
                                          .getObservable()
                                          .getSemantics()
                                          .asConcept()
                                          .getUrn()))
                  .findFirst()
                  .orElse(null);

          if (existing instanceof ObservationImpl existingImpl) {
            return existingImpl;
          }
        }
      }

      observationImpl.setId(serviceContextScope.getNextObservationId());

      return observation;
    }

    throw new KlabInternalErrorException(
        "RuntimeService::register() called with unexpected scope implementation");
  }

  /**
   * Find the cohort for the passed observation and optionally create it if missing. Must be called
   * with a current transaction unless the cohort is guaranteed to exist.
   *
   * @param observable
   * @param scope
   * @param addCohortIfMissing
   * @return the (existing or newly created) cohort
   */
  public Cohort getCohortFor(Semantics observable, ContextScope scope, boolean addCohortIfMissing) {

    var needsCohort =
        observable.is(SemanticType.COUNTABLE) && !observable.asConcept().isCollective();

    if (needsCohort) {

      var reasoner = scope.getService(Reasoner.class);
      var cohortObservable = reasoner.baseSubstantialType(observable, scope);

      // local uncommitted
      if (scope.getCurrentTransaction() != null) {
        var existing =
            scope.getCurrentTransaction().assets().stream()
                .filter(
                    a ->
                        a instanceof Cohort cohort
                            && cohort.getObservable().getUrn().equals(cohortObservable.getUrn()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
          return (Cohort) existing;
        }
      }

      var result =
          scope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .query(Cohort.class, scope)
              .source(RuntimeAsset.CONTEXT_ASSET)
              .along(GraphModel.Relationship.HAS_CHILD)
              .where(
                  GraphModel.Cohort.OBSERVABLE_FIELD,
                  KnowledgeGraph.Query.Operator.EQUALS,
                  cohortObservable.getUrn())
              .run(scope);

      if (result.isEmpty()) {

        if (addCohortIfMissing) {
          var cohort = new CohortImpl();
          cohort.setObservable(Observable.promote(cohortObservable));
          cohort.setChildrenCount(0);
          scope.getCurrentTransaction().add(cohort);
          scope
              .getCurrentTransaction()
              .link(RuntimeAsset.CONTEXT_ASSET, cohort, GraphModel.Relationship.HAS_CHILD);
          return cohort;
        }
      } else {
        return result.getFirst();
      }
    }
    return null;
  }

  private void publishContextualization(
      Observation observation, ServiceContextScope resolutionScope) {
    if (observation.getContextualizationData().isPersistent()) {
      resolutionScope.getService(Resolver.class).submitResource(observation, resolutionScope);
    }
  }

  private CompletableFuture<Dataflow> createPredefinedDataflow(
      Observation.ContextualizationData predefinedContextualization,
      Observation observation,
      ContextScope scope) {

    var ret = new DataflowImpl();
    ret.setChildrenCount(1);

    var actuator = new ActuatorImpl();
    actuator.setObservation(observation);
    actuator.setId(observation.getId());
    actuator.setActuatorType(Actuator.Type.OBSERVE);
    actuator
        .getComputation()
        .add(
            new ServiceCallImpl(
                CoreFunctor.ADAPTER_RESOLVER.getServiceCallName(),
                "value",
                predefinedContextualization));

    ret.getComputation().add(actuator);

    return CompletableFuture.completedFuture(ret);
  }

  private boolean compile(
      Observation rootObservation, Dataflow dataflow, ServiceContextScope scope) {

    scope
        .getCurrentTransaction()
        .link(
            scope.getCurrentTransaction().getActivity(),
            rootObservation,
            rootObservation.getId() < 0
                ? GraphModel.Relationship.CREATED
                : GraphModel.Relationship.RESOLVED);

    if (scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl transactionImpl) {
      for (var rootActuator : dataflow.getComputation()) {
        var executionSequence = new CompiledDataflow(this, rootObservation, scope);
        if (!executionSequence.compile(rootActuator)) {
          scope
              .getCurrentTransaction()
              .fail(
                  new KlabCompilationError(
                      "Could not compile execution sequence for target observation "
                          + rootObservation));
          return false;
        }

        if (!executionSequence.store(transactionImpl)) {
          scope
              .getCurrentTransaction()
              .fail(
                  new KlabCompilationError(
                      "Could not store execution sequence for target observation "
                          + rootObservation));
          return false;
        }
      }

      return true;
    }

    // won't happen
    throw new KlabInternalErrorException(
        "RuntimeService::observe() called with unexpected transaction implementation");
  }

  // TODO if we keep this, it must become asynchronous
  public Observation runDataflow(
      Dataflow dataflow, Geometry geometry, ContextScope contextScope /*,
      KnowledgeGraph.Operation contextualization*/) {

    /*
    TODO Load or confirm availability of all needed resources and create any non-existing observations
     */

    /*
    TODO find contextualization scale and hook point into the DT from the scope
     */

    if (contextScope instanceof ServiceContextScope serviceContextScope) {
      /** Run each actuator set in order */
      for (var rootActuator : dataflow.getComputation()) {
        var executionSequence =
            new CompiledDataflow(
                this, /*contextualization,*/ dataflow, getComponentRegistry(), serviceContextScope);
        var compiled = executionSequence.compile(rootActuator);
        if (!compiled) {
          //          contextualization.fail(
          //              contextScope,
          //              dataflow.getTarget(),
          //              new KlabCompilationError(
          //                  "Could not compile execution sequence for this target observation"));
          return Observation.empty();
        } else if (!executionSequence.isEmpty()) {
          // TODO run it the old way, calling the executors one by one. This is for explicit
          // dataflows and may not be
          //  needed.
          //          executionSequence.run(geometry);
        }
      }

      /*
      intersect coverage from dataflow with contextualization scale
       */

      //      if (dataflow instanceof DataflowImpl df
      //          && dataflow.getTarget() instanceof ObservationImpl obs) {
      //        obs.setResolvedCoverage(df.getResolvedCoverage());
      //      }

      //      contextualization.success(contextScope, dataflow.getTarget(), dataflow);
    }

    return null; // dataflow.getTarget();
  }

  @Override
  public ResourceSet resolveContextualizables(
      List<Contextualizable> contextualizables, ContextScope scope) {

    ResourceSet ret = new ResourceSet();
    // TODO FIXME USE ALL SERVICES
    var resourcesService = scope.getService(ResourcesService.class);
    /*
     * These are the contextualizables that need resolution at the runtime side, the others come
     * with their definition and are directly inserted in the dataflow
     */
    for (var contextualizable : contextualizables) {
      if (contextualizable.getServiceCall() != null) {

        ResourceSet resolution = ResourceSet.empty();

        /*
        first check if we have the service in our own catalog.
        */
        var executor =
            getComponentRegistry().getFunctionDescriptor(contextualizable.getServiceCall());

        if (executor != null && !executor.isEmpty()) {
          resolution =
              ResourceSet.of(
                  new ResourceSet.Resource(
                      this.serviceId(),
                      contextualizable.getServiceCall().getUrn(),
                      null,
                      Version.CURRENT_VERSION,
                      KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION,
                      System.currentTimeMillis(),
                      false));
        } else {

          /*
          Lookup a component that implements the service
           */
          resolution =
              resourcesService.resolveServiceCall(
                  contextualizable.getServiceCall().getUrn(),
                  contextualizable.getServiceCall().getRequiredVersion(),
                  scope);
        }

        if (resolution.isEmpty()) {
          return resolution;
        }

        if (!ingestResources(
            resolution,
            scope,
            settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class))) {
          return ResourceSet.empty(
              Notification.error(
                  "Cannot receive resources from service " + resourcesService.serviceName()));
        }
        ret = Utils.Resources.merge(ret, resolution);
      }

      if (!contextualizable.getResourceUrns().isEmpty()) {

        // TODO the pre-resolution step should become the key to handle multiple URNs
        var preResolveResourceData = preResolveResource(contextualizable.getResourceUrns(), scope);
        if (preResolveResourceData == null) {
          return ResourceSet.empty(
              Notification.error(
                  "Resources " + contextualizable.getResourceUrns() + " not available"));
        }
        if (preResolveResourceData.getSecond() != null) {
          // put the resource away for later and return
          scope
              .getData()
              .put(preResolveResourceData.getFirst(), preResolveResourceData.getSecond());

        } else {

          // ensure resource or adapter is accessible, pre-cache any multiple URN configuration
          var resolution =
              resourcesService.resolveResource(preResolveResourceData.getFirst(), scope);
          if (resolution.isEmpty()) {
            return resolution;
          }
          ret = Utils.Resources.merge(ret, resolution);
          if (!ret.isEmpty()) {
            for (var resource : resolution.getResults()) {
              if (resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE) {
                var service =
                    scope
                        .findService(
                            ResourcesService.class,
                            ks -> ks.serviceId().equals(resource.getServiceId()))
                        .orElse(null);
                if (service == null) {
                  return ResourceSet.empty(
                      Notification.error(
                          "Resource "
                              + resource.getResourceUrn()
                              + " is in a service that is not available"));
                }
                var res = service.retrieveResource(List.of(resource.getResourceUrn()), scope);
                if (res == null) {
                  return ResourceSet.empty(
                      Notification.error(
                          "Resource " + resource.getResourceUrn() + " is not available"));
                }
              }
            }
          }
        }
      }

      // if any embeddable component was returned, attempt to load it
      if (settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)
          && !getComponentRegistry().loadComponents(ret, scope)) {
        Logging.INSTANCE.warn("Could not load components suggested after ingestion of resource");
      }
    }

    return ret;
  }

  @Override
  public void submitContextualizationResult(
      ContextualizationScope scope, ContextScope contextScope, Activity.Outcome outcome) {

    /*
    Any submission tasks to be spawned for sub-contextualizations
     */
    List<Callable<Observation>> tasks = new ArrayList<>();

    if (outcome == Activity.Outcome.SUCCESS) {
      if (scope.getTarget().getObservable().getContextualization()
          == Contextualization.CLASSIFICATION) {
        // the execution scope must contain all attributions
        throw new KlabUnimplementedException("Contextualization not implemented");
      } else if (scope.getTarget().getObservable().getContextualization()
          == Contextualization.INSTANTIATION) {

        for (var child : scope.getOutcomes()) {
          // enqueue tasks to resolve any new observation
          tasks.add(
              Executors.callable(
                  () -> {
                    contextScope
                        .getService(org.integratedmodelling.klab.api.services.RuntimeService.class)
                        .submit(child, contextScope)
                        .thenApply(
                            obs -> {
                              // TODO any sub-states for the new object!
                              return obs;
                            })
                        .exceptionally(
                            (obs -> {
                              scope
                                  .getTarget()
                                  .getNotifications()
                                  .add(Notification.error(obs.getMessage(), obs));
                              return child;
                            }));
                  },
                  child));
        }

      } else if (scope.getTarget().getObservable().getContextualization()
          == Contextualization.CONNECTION) {
        // TODO the observations have been created but are not yet in the KG or in the transaction.
        // Take them
        //  from the execution scope, then resolve them here in the between() scope of the
        // collective.
        throw new KlabUnimplementedException("Contextualization not implemented");
      } else if (scope.getTarget().getObservable().is(SemanticType.QUALITY)
          && scope.getEvent().getType() == Scheduler.Event.Type.INITIALIZATION) {
        // TODO the finalizeStorage() could be done here as a sub-task instead of coming with the
        // executors
      } // TODO check if value ops should be handled here. Same for transformation instead of
      // surgically
      //  altering the dataflow?

      /*
      TODO update statistics. We can use the transaction to check if this was the top-level contextualization.
       */

      if (!tasks.isEmpty()) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
          if (executor.invokeAll(tasks).stream().anyMatch(Future::isCancelled)) {
            scope
                .getTarget()
                .getNotifications()
                .add(Notification.error("One or more contextualizations were cancelled"));
          }
        } catch (InterruptedException e) {
          scope.getTarget().getNotifications().add(Notification.error(e.getMessage(), e));
        }
      }

    } else {
      // the KG is self-cleaning
      // the storage may not be transactional so any observation storage must be cleaned up
      // TODO the filesystem location of the storage could be linked to a unique ID for the ctxscope
      // report for debugging/posterity if so configured
    }
  }

  /**
   * Create a single resolvable URN from the set of URNs received, which can be later sent to the
   * resource resolver unless the URN is specially handled. If the pre-inspection produces a
   * resource directly, return it as the second field so that the call to the resources service can
   * be skipped.
   *
   * @param resourceUrns
   * @param scope
   * @return
   */
  private Pair<String, Resource> preResolveResource(List<Urn> resourceUrns, ContextScope scope) {

    if (resourceUrns.size() == 1) {

      if (scope.getData().containsKey(resourceUrns.getFirst().getUrn())) {
        return Pair.of(
            resourceUrns.getFirst().getUrn(),
            scope.getData().get(resourceUrns.getFirst().getUrn(), Resource.class));
      } else if (Utils.Urns.isNamespaceBound(resourceUrns.getFirst().getUrn())) {

        // must be found in the namespace
        var definitionUrn =
            resourceUrns.getFirst().getNamespace() + "." + resourceUrns.getFirst().getResourceId();

        var namespace =
            Utils.Resources.resolveNamespace(resourceUrns.getFirst().getNamespace(), scope);

        if (namespace == null) {
          return null;
        }

        var definition =
            namespace.getStatements().stream()
                .filter(s -> s.getUrn().equals(definitionUrn))
                .findFirst()
                .orElse(null);

        if (definition instanceof KimSymbolDefinition symbolDefinition
            && symbolDefinition.getValue() instanceof Map<?, ?> map) {

          // 1. ensure we resolve within the local namespace (TODO) - not legal otherwise
          // TODO

          // 2. pre-resolve the adapter, which must be embeddable
          var adapterId = map.get("adapter");
          boolean adapterOK = false;
          if (adapterId != null) {
            var adapter = Utils.Resources.resolveAdapter(adapterId.toString(), scope, this);
            if (adapter != null) {
              adapterOK = adapter.isEmbeddable();
            }
          }

          if (!adapterOK) {
            return null;
          }

          // 3. return the resource once we know we can compute it locally

          return Pair.of(
              resourceUrns.getFirst().getUrn(),
              Resource.builder(resourceUrns.getFirst().getUrn()).withInlineDefinition(map).build());
        }

        return Pair.of(resourceUrns.getFirst().getUrn(), null);
      }

    } else {

      // TODO register the multiple resource as needed: resolve each of them, then create a
      // collection
      //  and store it in the scope with a temporary URN
      throw new KlabUnimplementedException("Multiple URNs not supported for contextualization");
    }

    // this will cause normal resolution downstream
    return Pair.of(resourceUrns.getFirst().getUrn(), null);
  }

  @Override
  public List<ContextInfo> getContextInfo(Scope scope) {
    return knowledgeGraph.getContextInfo(scope);
  }

  @Override
  public ContextScope connectContext(DigitalTwin.Configuration configuration, UserScope userScope) {
    // TODO for now we just return the existing. Later we should create it if the user is enabled
    var scope = getScopeManager().getScope(configuration.getId(), ContextScope.class);
    if (scope == null) {
      scope = reconstructContext(configuration, userScope);
    }
    return scope;
  }

  @Override
  public DigitalTwin.Configuration getConfiguration(String scopeId, UserScope scope) {
    var contextScope = getScopeManager().getScope(scopeId, ContextScope.class);
    return contextScope == null ? null : contextScope.getConfiguration();
  }

  private ContextScope reconstructContext(
      DigitalTwin.Configuration configuration, UserScope userScope) {
    // TODO find the scope in the knowledge graph. If existing, recreate the scope and the owning
    //  session.
    var sessionId = configuration.getId().substring(0, configuration.getId().lastIndexOf("."));
    var session = getScopeManager().getScope(sessionId, SessionScope.class);
    if (session == null) {
      session = userScope.getUserSession(this);
    }
    var ret =
        new ServiceContextScope((ServiceSessionScope) session, configuration, userScope.getUser());
    if (!userScope.getUser().getUsername().equals(ret.getUser().getUsername())) {
      ret = ret.withIdentity(userScope.getIdentity());
    }

    declareContextScope(ret, session, userScope);

    return ret;
  }

  @Override
  public boolean releaseSession(SessionScope scope) {
    try {
      scope.close();
      return true;
    } catch (Throwable t) {
      // shut up
    }
    return false;
  }

  @Override
  public boolean releaseContext(ContextScope scope) {
    try {
      scope.close();
      return true;
    } catch (Throwable t) {
      // shut up
    }
    return false;
  }

  @Override
  public <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
      KnowledgeGraph.Query<T> knowledgeGraphQuery, Scope scope) {
    if (scope instanceof ContextScope contextScope) {
      var knowledgeGraph = contextScope.getDigitalTwin().getKnowledgeGraph();
      if (knowledgeGraphQuery instanceof KnowledgeGraphQuery<T> qc) {
        return knowledgeGraph.query(
            knowledgeGraphQuery, (Class<T>) qc.getResultType().getAssetClass(), scope);
      }
      throw new KlabUnimplementedException(
          "Not ready to compile arbitrary KG query implementations");
    }
    return List.of();
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    // TODO
    return null;
  }

  /**
   * Return a default sharding strategy for any observation based on the stored settings
   *
   * @return
   */
  @Override
  public Data.ShardingStrategy getDefaultShardingStrategy(
      Observation observation, ContextScope scope) {

    var ret = Data.ShardingStrategy.neutral();
    ret.setDataType(
        switch (observation.getObservable().getContextualization()) {
          case QUANTIFICATION, MEASURE, VALUATION -> Storage.Type.DOUBLE;
          case CATEGORIZATION -> Storage.Type.KEYED;
          case VERIFICATION -> Storage.Type.BOOLEAN;
          default ->
              throw new KlabIllegalStateException(
                  "Unexpected observable type for sharding strategy");
        });

    // apply settings to modify defaults. TODO may need the short float option in the scope config
    // too
    var forceFloats = settings.get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class);
    var forceScalar = !settings.get(Setting.PARALLELIZE_OBSERVATIONS, Boolean.class);
    if (ret.getDataType() == Storage.Type.DOUBLE && forceFloats) {
      ret.setDataType(Storage.Type.FLOAT);
    }

    var geometrySlice = observation.getGeometry().without(Geometry.Dimension.Type.TIME);
    var space = geometrySlice.dimension(Geometry.Dimension.Type.SPACE);

    var fillCurve = Data.FillCurve.D1_LINEAR;
    if (space != null && space.isRegular()) {
      fillCurve =
          switch (space.getDimensionality()) {
            case 2 -> Data.FillCurve.D2_XY;
            case 3 -> Data.FillCurve.D3_XYZ;
            default -> Data.FillCurve.D1_LINEAR;
          };
    }
    ret.setCurve(fillCurve);

    if (forceScalar) {
      ret.setSuggestedSplits(1);
    } else {
      ret.setSuggestedSplits(Parallelism.CORES.getAsInt());
    }

    return ret;
  }

  /**
   * Matches the provided service implementation with the specified service call and observation.
   * Typically used to determine compatibility or processing suitability between a service
   * implementation, a service call, and an observation. Matches parameters to best scanner types,
   * fill curve w.r.t. observation geometry, and scope preferences.
   *
   * @param serviceInfo the service info to be matched, which may contain additional information
   * @param implementation the service implementation to be matched
   * @param call the service call to be matched
   * @param observation the observation used in the matching process
   * @return an integer representing the match result, where the specific value and its meaning
   *     depend on the context of the matching logic
   */
  public int matchImplementation(
      ServiceInfo serviceInfo,
      ComponentRegistry.ServiceImplementation implementation,
      ServiceCall call,
      Observation observation,
      ContextScope scope) {
    // TODO
    var scannerScore =
        0; // appropriateness of scanner parameter w.r.t. observable type & scope prefs
    var geometryScore = 0; // appropriateness of geometry parameter w.r.t. observation geometry
    var eventScore = 0; // appropriateness of scheduler parameters w.r.t. observation geometry

    // TODO match geometry to fill curve and geometry in service info
    if (serviceInfo.getGeometry() != null && !serviceInfo.getGeometry().isUniversal()) {}

    for (var parameter :
        (implementation.constructor == null
            ? implementation.method.getParameterTypes()
            : implementation.constructor.getParameterTypes())) {
      if (Scanner.class.isAssignableFrom(parameter)) {
        if (SemanticType.isNumeric(observation.getObservable().getSemantics().getType())) {
          var preferredType = configuration.getNumericStorageType();
          if (Storage.DoubleScanner.class.isAssignableFrom(parameter)) {
            scannerScore = preferredType == Storage.Type.DOUBLE ? 0 : 1;
          } else if (Storage.FloatScanner.class.isAssignableFrom(parameter)) {
            scannerScore = preferredType == Storage.Type.FLOAT ? 0 : 1;
          }
        } else if (observation
            .getObservable()
            .getSemantics()
            .getType()
            .contains(SemanticType.CLASS)) {
          scannerScore = Storage.KeyScanner.class.isAssignableFrom(parameter) ? 0 : -1;
        } else if (observation
            .getObservable()
            .getSemantics()
            .getType()
            .contains(SemanticType.PRESENCE)) {
          scannerScore = Storage.BooleanScanner.class.isAssignableFrom(parameter) ? 0 : -1;
        } else {
          scannerScore = 1;
        }
      } // TODO match the rest!

      if (DataKey.class.isAssignableFrom(parameter)) {
        var storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);
        if (storage == null || storage.getKey() == null) {
          return -1;
        }
      }
    }

    if (scannerScore < 0 || geometryScore < 0 || eventScore < 0) {
      return -1;
    }
    return Math.max(Math.max(scannerScore, geometryScore), eventScore);
  }
}
