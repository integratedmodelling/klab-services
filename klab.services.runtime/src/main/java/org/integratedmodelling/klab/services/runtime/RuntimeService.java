package org.integratedmodelling.klab.services.runtime;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4JEmbedded;
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
  private KnowledgeGraph knowledgeGraph;
  private SystemLauncher systemLauncher;

  public RuntimeService(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {
    super(scope, Type.RUNTIME, options);
    ServiceConfiguration.INSTANCE.setMainService(this);
    readConfiguration(options);
    initializeMessaging();
  }

  private void initializeMessaging() {
    if (this.configuration.getBrokerURI() == null) {
      this.embeddedBroker = new EmbeddedBroker();
    }
  }

  private void readConfiguration(ServiceStartupOptions options) {
    File config = BaseService.getFileInConfigurationDirectory(options, "runtime.yaml");
    if (config.exists() && config.length() > 0 && !options.isClean()) {
      this.configuration = Utils.YAML.load(config, RuntimeConfiguration.class);
    } else {
      // make an empty config
      this.configuration = new RuntimeConfiguration();
      this.configuration.setServiceId(UUID.randomUUID().toString());
      saveConfiguration();
    }
  }

  private boolean createMainKnowledgeGraph() {
    // TODO choose the DB from configuration - client or embedded server
    var path = BaseService.getConfigurationSubdirectory(startupOptions, "dt").toPath();
    this.knowledgeGraph = new KnowledgeGraphNeo4JEmbedded(path);
    return this.knowledgeGraph.isOnline();
  }

  public KnowledgeGraph getMainKnowledgeGraph() {
    return this.knowledgeGraph;
  }

  private void saveConfiguration() {
    File config = BaseService.getFileInConfigurationDirectory(startupOptions, "runtime.yaml");
    org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
  }

  @Override
  public boolean scopesAreReactive() {
    return true;
  }

  @Override
  public void initializeService() {

    Logging.INSTANCE.setSystemIdentifier("Runtime service: ");

    serviceScope()
        .send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceInitializing,
            capabilities(serviceScope()).toString());

    if (createMainKnowledgeGraph()) {

      // TODO internal libraries
      getComponentRegistry().loadExtensions("org.integratedmodelling.klab.runtime");
      getComponentRegistry()
          .initializeComponents(
              BaseService.getConfigurationSubdirectory(startupOptions, "components"));
      serviceScope()
          .send(
              Message.MessageClass.ServiceLifecycle,
              Message.MessageType.ServiceAvailable,
              capabilities(serviceScope()));
    } else {

      serviceScope()
          .send(
              Message.MessageClass.ServiceLifecycle,
              Message.MessageType.ServiceUnavailable,
              capabilities(serviceScope()));
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
          && serviceContextScope.getPersistence() == Persistence.SERVICE_SHUTDOWN) {
        scope.send(
            Message.MessageClass.SessionLifecycle,
            Message.MessageType.ContextClosed,
            scope.getId());
        scope.close();
        Logging.INSTANCE.info("Context " + scope.getId() + " closed upon service shutdown");
      }
    }

    serviceScope()
        .send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceUnavailable,
            capabilities(serviceScope()));
    if (systemLauncher != null) {
      systemLauncher.shutdown();
    }
    if (knowledgeGraph != null) {
      knowledgeGraph.shutdown();
    }
    return super.shutdown();
  }

  @Override
  public Capabilities capabilities(Scope scope) {

    var ret = new RuntimeCapabilitiesImpl();
    ret.setLocalName(localName);
    ret.setType(Type.RUNTIME);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("RUNTIME_" + hardwareSignature));
    ret.setServiceId(configuration.getServiceId());
    ret.setServiceName("Runtime");
    ret.setBrokerURI(
        embeddedBroker != null ? embeddedBroker.getURI() : configuration.getBrokerURI());
    ret.setBrokerURI(
        embeddedBroker != null ? embeddedBroker.getURI() : configuration.getBrokerURI());
    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());
    ret.getComponents().addAll(getComponentRegistry().getComponents(scope));

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

  @Override
  public String registerSession(SessionScope sessionScope) {
    if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

      serviceSessionScope.setId(Utils.Names.shortUUID());
      getScopeManager()
          .registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());

      if (serviceSessionScope.getServices(RuntimeService.class).isEmpty()) {
        // add self as the runtime service, which is needed by the slave scopes
        serviceSessionScope.getServices(RuntimeService.class).add(this);
      }

      // all other services need to know the session we created
      var fail = new AtomicBoolean(false);
      for (var serviceClass : List.of(Resolver.class, Reasoner.class, ResourcesService.class)) {
        try {
          Thread.ofVirtual()
              .start(
                  () -> {
                    for (var service : serviceSessionScope.getServices(serviceClass)) {
                      // if things are OK, the service repeats the ID back
                      if (!serviceSessionScope
                          .getId()
                          .equals(service.registerSession(serviceSessionScope))) {
                        fail.set(true);
                      }
                    }
                  })
              .join();
        } catch (InterruptedException e) {
          fail.set(true);
        }
      }

      if (fail.get()) {
        serviceSessionScope.send(
            Notification.error(
                "Error registering session with other services:" + " session is inoperative",
                UI.Interactivity.DISPLAY));
        serviceSessionScope.setOperative(false);
      }

      return serviceSessionScope.getId();
    }
    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  @Override
  public String registerContext(ContextScope contextScope) {

    if (contextScope instanceof ServiceContextScope serviceContextScope) {

      serviceContextScope.setHostServiceId(serviceId());

      serviceContextScope.setId(
          serviceContextScope.getParentScope().getId() + "." + Utils.Names.shortUUID());
      getScopeManager()
          .registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());
      serviceContextScope.setDigitalTwin(
          new DigitalTwinImpl(this, serviceContextScope, getMainKnowledgeGraph()));

      if (serviceContextScope.getServices(RuntimeService.class).isEmpty()) {
        // add self as the runtime service, which is needed by the slave scopes
        serviceContextScope.getServices(RuntimeService.class).add(this);
      }

      // all other services need to know the context we created. TODO we may also need to
      // register with the stats services and maybe any independent authorities
      var fail = new AtomicBoolean(false);
      for (var serviceClass : List.of(Resolver.class, Reasoner.class, ResourcesService.class)) {
        try {
          Thread.ofVirtual()
              .start(
                  () -> {
                    for (var service : serviceContextScope.getServices(serviceClass)) {
                      // if things are OK, the service repeats the ID back
                      if (!serviceContextScope
                          .getId()
                          .equals(service.registerContext(serviceContextScope))) {
                        fail.set(true);
                      }
                    }
                  })
              .join();
        } catch (InterruptedException e) {
          fail.set(true);
        }
      }

      if (fail.get()) {
        serviceContextScope.send(
            Notification.error(
                "Error registering context with other services:" + " context is inoperative",
                UI.Interactivity.DISPLAY));
        serviceContextScope.setOperative(false);
      }

      return serviceContextScope.getId();
    }
    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  @Override
  public long submit(Observation observation, ContextScope scope) {

    if (observation.isResolved()) {
      // TODO there may be a context for this at some point.
      throw new KlabIllegalStateException(
          "A resolved observation cannot be submitted to the " + "knowledge graph for now");
    }

    if (observation.getObservable().is(SemanticType.QUALITY)
        && scope.getContextObservation() == null) {
      throw new KlabIllegalStateException("Cannot observe a quality without a context observation");
    }

    /** Only situation when we accept an observation w/o geometry */
    if (observation.getGeometry() == null && observation instanceof ObservationImpl observation1) {
      if (observation.getObservable().is(SemanticType.QUALITY)
          && scope.getContextObservation() != null) {
        observation1.setGeometry(scope.getContextObservation().getGeometry());
      } else if (observation.getObservable().is(SemanticType.COUNTABLE)
          && observation.getObservable().getSemantics().isCollective()
          && scope.getObserver() != null) {
        observation1.setGeometry(scope.getObserver().getGeometry());
      }
    }

    if (scope instanceof ServiceContextScope serviceContextScope) {

      var digitalTwin = getDigitalTwin(scope);
      var parentActivity = Provenance.getActivity(scope);
      var agent = getAgent(scope);

      /*
       * The initial activity should be in the scope; if not, we're observing at the
       * root DT level and we get the context initialization activity as parent.
       */
      var instantiation =
          digitalTwin
              .getKnowledgeGraph()
              .operation(
                  agent,
                  parentActivity,
                  Activity.Type.INSTANTIATION,
                  "Instantiation of " + observation,
                  observation,
                  this);

      // if root, closing the operation will commit all transactions, or rollback if unsuccessful.
      try (instantiation) {

        var ret = instantiation.store(observation);
        instantiation.link(
            instantiation.getActivity(), observation, DigitalTwin.Relationship.CREATED);
        if (scope.getContextObservation() != null) {
          instantiation.link(
              scope.getContextObservation(), observation, DigitalTwin.Relationship.HAS_CHILD);
        } else {
          instantiation.linkToRootNode(observation, DigitalTwin.Relationship.HAS_CHILD);
        }

        if (scope.getObserver() != null) {
          instantiation.link(
              observation, scope.getObserver(), DigitalTwin.Relationship.HAS_OBSERVER);
        }

        /*
         * TODO start computing the set of consequences that this operation engenders if successful and
         * link them to the observation for deferred execution after contextualization (failure of
         * one of those shouldn't jeopardize the success of the contextualization). This includes computing
         * configurations that may emerge and must consider any linked DTs. If the observation is a time
         * event, the consequences are the only thing that matters and the storage of the event is conditional
         * to being consequential. These can be computed in a thread to avoid interrupting the execution of
         * this submission.
         */

        instantiation.success(scope, observation);

        return ret;

      } catch (Throwable t) {
        instantiation.fail(scope, observation, t);
      }
    }

    return Observation.UNASSIGNED_ID;
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

  private Activity getInitializationActivity(Observation observation, ContextScope scope) {
    var ret = Provenance.getActivity(scope);
    if (ret != null) {
      return ret;
    }
    var activities =
        getDigitalTwin(scope)
            .getKnowledgeGraph()
            .get(scope, Activity.class, Activity.Type.INITIALIZATION);
    if (activities.size() == 1) {
      return activities.getFirst();
    }
    throw new KlabInternalErrorException("cannot locate the context initialization activity");
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

  @Override
  public CompletableFuture<Observation> resolve(long id, ContextScope scope) {

    if (scope instanceof ServiceContextScope serviceContextScope) {

      /*
      TODO must consult the knowledge graph to see if we have a pre-resolved dataflow that
       can handle this observation. Could be done by looking up an existing RESOLUTION activity
       that was linked to the overall covered geometry of the dataflow. Should use the spatial
       queries in Neo4J or maintain a separate spatial index for the dataflows/activities.
       */

      var resolver = serviceContextScope.getService(Resolver.class);
      var observation = serviceContextScope.getObservation(id);
      var digitalTwin = getDigitalTwin(scope);
      var parentActivities =
          digitalTwin
              .getKnowledgeGraph()
              .get(scope, Activity.class, Activity.Type.INSTANTIATION, observation);

      // TODO check
      var parentActivity = parentActivities.getFirst();
      final var ret = new CompletableFuture<Observation>();

      Thread.ofVirtual()
          .start(
              () -> {
                Dataflow dataflow = null;
                Activity resolutionActivity = null;
                Observation result = null;

                /*
                This will commit or rollback at close()
                 */
                var resolution =
                    digitalTwin
                        .getKnowledgeGraph()
                        .operation(
                            digitalTwin.getKnowledgeGraph().klab(),
                            parentActivity,
                            Activity.Type.RESOLUTION,
                            "Resolution of " + observation,
                            resolver);

                try (resolution) {
                  result = observation;
                  scope.send(
                      Message.MessageClass.ObservationLifecycle,
                      Message.MessageType.ResolutionStarted,
                      result);
                  try {
                    // TODO send out the activity with the scope
                    dataflow = resolver.resolve(observation, scope);
                    if (dataflow != null) {
                      resolution.success(
                          scope,
                          result,
                          dataflow,
                          "Resolution of observation _"
                              + observation.getUrn()
                              + "_ of **"
                              + observation.getObservable().getUrn()
                              + "**",
                          resolver);
                      scope.send(
                          Message.MessageClass.ObservationLifecycle,
                          dataflow.isEmpty()
                              ? Message.MessageType.ResolutionUnsuccessful
                              : Message.MessageType.ResolutionSuccessful,
                          result);
                      resolutionActivity = resolution.getActivity();
                    } else {
                      resolution.fail(scope, observation);
                      ret.completeExceptionally(
                          new KlabResourceAccessException(
                              "Resolution of " + observation.getUrn() + " failed"));
                    }
                  } catch (Throwable t) {
                    Logging.INSTANCE.error(t);
                    ret.completeExceptionally(t);
                    resolution.fail(scope, observation, t);
                    scope.send(
                        Message.MessageClass.ObservationLifecycle,
                        Message.MessageType.ResolutionAborted,
                        observation);
                  }
                } catch (Throwable t) {
                  Logging.INSTANCE.error(t);
                  scope.send(
                      Message.MessageClass.ObservationLifecycle,
                      Message.MessageType.ResolutionAborted,
                      observation);
                  resolution.fail(scope, observation, t);
                  ret.completeExceptionally(t);
                }

                if (!ret.isCompletedExceptionally() && dataflow != null) {

                  if (dataflow.isEmpty()
                      && observation.getObservable().is(SemanticType.COUNTABLE)) {
                    // if there is a dataflow, this step will be done in execution
                    serviceContextScope.finalizeObservation(observation, resolution, false);
                  } else {
                    /*
                    this will commit all resources at close()
                     */
                    var contextualization =
                        digitalTwin
                            .getKnowledgeGraph()
                            .operation(
                                digitalTwin.getKnowledgeGraph().klab(),
                                resolutionActivity,
                                Activity.Type.EXECUTION,
                                "Execution of resolved dataflow to contextualize " + observation,
                                dataflow,
                                observation,
                                this);

                    try (contextualization) {
                      // TODO contextualization gets its own activities to use in operations
                      //  (dependent on resolution) linked to actuators by runDataflow
                      result = runDataflow(dataflow, scope, contextualization);
                      ret.complete(result);
                      if (result.isEmpty()) {
                        contextualization.fail(scope, dataflow);
                      } else {
                        contextualization.success(scope, dataflow, result);
                      }
                    } catch (Throwable t) {
                      Logging.INSTANCE.error(t);
                      contextualization.fail(scope, dataflow, result, t);
                      ret.completeExceptionally(t);
                    }
                  }
                }
              });

      return ret;
    }

    throw new KlabInternalErrorException(
        "Digital twin is inaccessible because of unexpected scope implementation");
  }

  @Override
  public Observation runDataflow(Dataflow dataflow, ContextScope contextScope) {
    // TODO fill in the operation representing an external dataflow run
    return runDataflow(dataflow, contextScope, null);
  }

  public Observation runDataflow(
      Dataflow dataflow, ContextScope contextScope, KnowledgeGraph.Operation contextualization) {

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
            new ExecutionSequence(
                this, contextualization, dataflow, getComponentRegistry(), serviceContextScope);
        var compiled = executionSequence.compile(rootActuator);
        if (!compiled) {
          contextualization.fail(
              contextScope,
              dataflow.getTarget(),
              new KlabCompilationError(
                  "Could not compile execution sequence for this target observation"));
          return Observation.empty();
        } else if (!executionSequence.isEmpty()) {
          if (!executionSequence.run()) {
            contextualization.fail(
                contextScope, dataflow.getTarget(), executionSequence.getCause());
            return Observation.empty();
          }
        }
      }

      /*
      intersect coverage from dataflow with contextualization scale
       */

      if (dataflow instanceof DataflowImpl df
          && dataflow.getTarget() instanceof ObservationImpl obs) {
        obs.setResolved(true);
        obs.setResolvedCoverage(df.getResolvedCoverage());
      }

      contextualization.success(contextScope, dataflow.getTarget(), dataflow);
    }

    return dataflow.getTarget();
  }

  private DigitalTwin getDigitalTwin(ContextScope contextScope) {
    if (contextScope instanceof ServiceContextScope serviceContextScope) {
      return serviceContextScope.getDigitalTwin();
    }
    throw new KlabInternalErrorException(
        "Digital twin is inaccessible because of unexpected scope " + "implementation");
  }

  @Override
  public <T extends RuntimeAsset> List<T> retrieveAssets(
      ContextScope contextScope, Class<T> assetClass, Object... queryParameters) {
    return knowledgeGraph.get(contextScope, assetClass, queryParameters);
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

        if (!ingestResources(resolution, scope)) {
          return ResourceSet.empty(
              Notification.error(
                  "Cannot receive resources from " + resourcesService.getServiceName()));
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
      }
    }

    return ret;
  }

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return knowledgeGraph.getSessionInfo(scope);
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
}
