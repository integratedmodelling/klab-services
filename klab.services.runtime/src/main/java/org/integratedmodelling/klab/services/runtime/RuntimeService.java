package org.integratedmodelling.klab.services.runtime;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
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
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.view.UIView;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4JEmbedded;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;
import org.integratedmodelling.klab.utilities.Utils;

public class RuntimeService extends BaseService
    implements org.integratedmodelling.klab.api.services.RuntimeService,
        org.integratedmodelling.klab.api.services.RuntimeService.Admin {

  private String hardwareSignature =
      org.integratedmodelling.common.utils.Utils.Strings.hash(Utils.OS.getMACAddress());
  private RuntimeConfiguration configuration;
  private KnowledgeGraphNeo4j knowledgeGraph;
  private SystemLauncher systemLauncher;

  public RuntimeService(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {
    super(scope, Type.RUNTIME, options);
    ServiceConfiguration.INSTANCE.setMainService(this);
    readConfiguration(options);
    initializeMessaging();
  }

  private void initializeMessaging() {
    if (startupOptions.isStartLocalBroker()) {
      this.embeddedBroker = new EmbeddedBroker();
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
    var path = BaseService.getConfigurationSubdirectory(startupOptions, "dt").toPath();
    this.knowledgeGraph = new KnowledgeGraphNeo4JEmbedded(path);
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
  public void initializeService() {

    Logging.INSTANCE.setSystemIdentifier("Runtime service: ");

    //    serviceScope()
    //        .send(
    //            Message.MessageClass.ServiceLifecycle,
    //            Message.MessageType.ServiceInitializing,
    //            capabilities(serviceScope()).toString());

    if (createMainKnowledgeGraph()) {

      // TODO internal libraries
      getComponentRegistry().loadExtensions("org.integratedmodelling.klab.runtime");
      getComponentRegistry()
          .initializeComponents(
              BaseService.getConfigurationSubdirectory(startupOptions, "components"));
      //      serviceScope()
      //          .send(
      //              Message.MessageClass.ServiceLifecycle,
      //              Message.MessageType.ServiceAvailable,
      //              capabilities(serviceScope()));
    } else {

      //      serviceScope()
      //          .send(
      //              Message.MessageClass.ServiceLifecycle,
      //              Message.MessageType.ServiceUnavailable,
      //              capabilities(serviceScope()));
    }
  }

  @Override
  public boolean operationalizeService() {
    // nothing to do here
    return true;
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

    //    serviceScope()
    //        .send(
    //            Message.MessageClass.ServiceLifecycle,
    //            Message.MessageType.ServiceUnavailable,
    //            capabilities(serviceScope()));
    if (systemLauncher != null) {
      systemLauncher.shutdown();
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
   * @param userScope used to set up federated behavior
   * @return
   */
  @Override
  public String declareContextScope(ContextScope contextScope, SessionScope userScope) {

    if (!serviceId().equals(contextScope.getHostServiceId())) {
      return super.declareContextScope(contextScope, userScope);
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

      return serviceContextScope.getId();
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
      Observation observation, ServiceContextScope scope, Actuator actuator) {
    return ScalarComputationGroovy.builder(observation, scope, actuator);
  }

  /**
   * Submission is entirely within a transaction, created new at the root submission. Observations
   * are directly used as keys for everything, so the object must never be substituted by another.
   * All observations created upon submissions remain without URN or ID until committed. Each
   * submission creates a submission activity followed by resolution and, if successful,
   * contextualization of all resolved observations. Instantiators cause other submissions within
   * the same transaction.
   *
   * @param observation the observation to submit
   * @param scope the context scope in which to submit the observation
   * @return the submission task
   */
  @Override
  public CompletableFuture<Observation> submit(Observation observation, ContextScope scope) {

    if (observation.getId() > 0) {
      return CompletableFuture.completedFuture(observation);
    }

    if (scope instanceof ServiceContextScope serviceContextScope) {

      /*
       * Pre-existing observations are checked unless it's an acknowledged single subject, which can
       * always be added.
       */
      var existing =
          observation.getObservable().is(SemanticType.SUBJECT)
                  && !observation.getObservable().getSemantics().isCollective()
              ? null
              : scope.getObservation(observation.getObservable());

      if (existing != null) {
        return CompletableFuture.completedFuture(existing);
      }

      if (observation.getObservable().is(SemanticType.QUALITY)
          && scope.getContextObservation() == null) {
        throw new KlabIllegalStateException(
            "Cannot observe a quality without a context observation");
      }

      /** Only situation when we accept an observation w/o geometry */
      if (observation.getGeometry() == null
          && observation instanceof ObservationImpl observation1) {
        if (observation.getObservable().is(SemanticType.QUALITY)
            && scope.getContextObservation() != null) {
          observation1.setGeometry(scope.getContextObservation().getGeometry());
        } else if (observation.getObservable().is(SemanticType.COUNTABLE)
            && observation.getObservable().getSemantics().isCollective()
            && scope.getObserver() != null) {
          observation1.setGeometry(scope.getObserver().getGeometry());
        }
      }

      /* adjourn contextualization data with our service coordinates */
      ObservationImpl.ContextualizationDataImpl contextualizationData =
          observation.getContextualizationData()
                  instanceof ObservationImpl.ContextualizationDataImpl cd
              ? cd
              : new ObservationImpl.ContextualizationDataImpl();

      contextualizationData.setServiceId(serviceId());
      contextualizationData.setServiceUrl(getUrl());
      if (observation.getContextualizationData() == null
          && observation instanceof ObservationImpl observationImpl) {
        observationImpl.setContextualizationData(contextualizationData);
      }

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
              storedAgent,
              serviceContextScope.getActivity(),
              observation + " submitted");

      //      var submissionScope = serviceContextScope.initializeResolution(submission);
      var submissionScope = serviceContextScope.executing(submission);
      var resolver = scope.getService(Resolver.class);
      var resolution =
          Activity.of(
              "Resolution of " + observation,
              Activity.Type.RESOLUTION,
              this,
              agent,
              submission,
              "Resolution of " + observation,
              submissionScope);

      submissionScope.getCurrentTransaction().add(observation);
      submissionScope
          .getCurrentTransaction()
          .link(
              scope.getContextObservation() == null
                  ? RuntimeAsset.CONTEXT_ASSET
                  : scope.getContextObservation(),
              observation,
              GraphModel.Relationship.HAS_CHILD);
      submissionScope
          .getCurrentTransaction()
          .link(submission, observation, GraphModel.Relationship.CREATED);

      var resolutionScope = submissionScope.executing(resolution /*, true*/);
      return resolver
          /* resolve asynchronously. If there are contextualization data the resolver will compile them in. */
          .resolve(observation, resolutionScope)
          .exceptionally(
              t -> {
                resolutionScope.fail(t);
                return Dataflow.empty();
              })
          /* then compile the dataflow */
          .thenApply(
              dataflow -> {
                if (!dataflow.isEmpty()) {
                  if (compile(observation, dataflow, resolutionScope)) {
                    if (resolutionScope.commit()) {
                      return observation;
                    }
                  }
                }
                resolutionScope.fail();
                return Observation.empty();
              })
          /* then submit the observation to the scheduler, which will trigger contextualization */
          .thenApply(
              o -> {
                if (!o.isEmpty()) {
                  submissionScope.getCurrentTransaction().registerExecutors();
                  submissionScope.contextualize(o);
                  // TODO add info about the contextualization to the action's metadata
                  submissionScope.commit();
                } else {
                  submissionScope.fail();
                }
                return o;
              })
          .exceptionally(
              t -> {
                submissionScope.fail(t);
                return Observation.empty();
              });
    }
    throw new KlabInternalErrorException(
        "RuntimeService::observe() called with unexpected scope implementation");
  }

  private boolean compile(
      Observation rootObservation, Dataflow dataflow, ServiceContextScope scope) {

    scope.getCurrentTransaction().add(rootObservation);
    scope
        .getCurrentTransaction()
        .link(
            scope.getContextObservation() == null
                ? scope.getDigitalTwin().getKnowledgeGraph().scope()
                : scope.getContextObservation(),
            rootObservation,
            GraphModel.Relationship.HAS_CHILD);

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
          return false;
        }
      }

      return true;
    }

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
        var resolution =
            resourcesService.resolveServiceCall(
                contextualizable.getServiceCall().getUrn(),
                contextualizable.getServiceCall().getRequiredVersion(),
                scope);
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

        // ensure resource or adapter is accessible, pre-cache any multiple URN configuration
        var resolution =
            resourcesService.resolveResource(contextualizable.getResourceUrns(), scope);
        if (resolution.isEmpty()) {
          return resolution;
        }
        ret = Utils.Resources.merge(ret, resolution);
        if (!ret.isEmpty()) {
          for (var resource : resolution.getResults()) {
            if (resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE) {
              var service =
                  scope.getService(
                      ResourcesService.class, ks -> ks.serviceId().equals(resource.getServiceId()));
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

      // if any embeddable component was returned, attempt to load it
      if (settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)
          && !getComponentRegistry().loadComponents(ret, scope)) {
        Logging.INSTANCE.warn("Could not load components suggested after ingestion of resource");
      }
    }

    return ret;
  }

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return knowledgeGraph.getSessionInfo(scope);
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
    var ret = new ServiceContextScope((ServiceSessionScope) session, configuration);
    declareContextScope(ret, session);
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
  public GraphModel.KnowledgeGraph retrieveSubgraph(
      long focalNodeId,
      int depth,
      Collection<Long> requiredNodes,
      Collection<RuntimeAsset.Type> acceptedTypes,
      ContextScope scope) {
    return scope
        .getDigitalTwin()
        .getKnowledgeGraph()
        .subgraph(focalNodeId, depth, requiredNodes, acceptedTypes, scope);
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
  public Data.ShardingStrategy getDefaultShardingStrategy(Observation observation) {

    var ret = Data.ShardingStrategy.neutral();
    ret.setDataType(
        switch (observation.getObservable().getDescriptionType()) {
          case QUANTIFICATION -> Storage.Type.DOUBLE;
          case CATEGORIZATION -> Storage.Type.KEYED;
          case VERIFICATION -> Storage.Type.BOOLEAN;
          default ->
              throw new KlabIllegalStateException(
                  "Unexpected observable type for sharding strategy");
        });

    // apply settings to modify defaults
    var forceFloats = settings.get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class);
    var forceScalar = settings.get(Setting.DO_NOT_PARALLELIZE_OBSERVATIONS, Boolean.class);
    if (ret.getDataType() == Storage.Type.DOUBLE && forceFloats) {
      ret.setDataType(Storage.Type.FLOAT);
    }
    if (forceScalar) {
      ret.setSuggestedSplits(1);
    }

    return ret;
  }
}
