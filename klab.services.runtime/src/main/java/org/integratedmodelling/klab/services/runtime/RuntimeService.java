package org.integratedmodelling.klab.services.runtime;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Federation;
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
          && serviceContextScope.getPersistence() == Persistence.SERVICE_SHUTDOWN) {
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
  public Capabilities capabilities(Scope scope) {

    var ret = new RuntimeCapabilitiesImpl();
    ret.setLocalName(localName);
    ret.setType(Type.RUNTIME);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("RUNTIME_" + hardwareSignature));
    ret.setServiceId(configuration.getServiceId());
    ret.setServiceName("Runtime");

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

  @Override
  public String registerSession(SessionScope sessionScope, Federation federation) {

    if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

      serviceSessionScope.setId(Utils.Names.shortUUID());
      getScopeManager().registerScope(serviceSessionScope, federation);

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
                          .equals(service.registerSession(serviceSessionScope, federation))) {
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
                UIView.Interactivity.DISPLAY));
        serviceSessionScope.setOperative(false);
      }

      return serviceSessionScope.getId();
    }
    throw new KlabIllegalArgumentException("unexpected scope class");
  }

  @Override
  public String registerContext(ContextScope contextScope, Federation federation) {

    if (contextScope instanceof ServiceContextScope serviceContextScope) {

      serviceContextScope.setHostServiceId(serviceId());

      serviceContextScope.setId(
          serviceContextScope.getParentScope().getId() + "." + Utils.Names.shortUUID());
      getScopeManager().registerScope(serviceContextScope, federation);
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
                          .equals(service.registerContext(serviceContextScope, federation))) {
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
                UIView.Interactivity.DISPLAY));
        serviceContextScope.setOperative(false);
      }

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
   * The structure of the graph will be:
   *
   * <ul>
   *   <li>The RESOLUTION activity as top node, linking to ALL the observations created by a
   *       RESOLVED link. The dataflow source code is also attached to it. Pre-existing observations
   *       referenced are not linked to the activity but will get any additional AFFECTS
   *       relationship. Activity is attached to the agent and the provenance, timestamped for
   *       reconstruction
   *   <li>The link to each observation contains the sequence number (0-based) to reconstruct the
   *       contextualization order. -1 flags links to pre-existing referenced observations.
   *   <li>AFFECTS relationships are added for all causal links
   *   <li>The actuator is linked to each observation. Activities can reconstruct the dataflow by
   *       following the observations.
   *   <li>HAS_CHILD relationships are added between dependents and substantials and between
   *       instantiated substantials and their collective observations
   *   <li>Any other observation is linked to the context
   *   <li>All observations are linked to their observer if any
   *   <li>Contextualization will add data buffers for qualities and set the computation time
   *       (incrementally) and the latest contextualization update time, with -1 before
   *       contextualization
   *   <li>The INIT CONTEXTUALIZATION activity is added by the scheduler when the initialization
   *       event is scheduled on the submitted root observation. The activity is TRIGGERED by
   *       RESOLUTION and linked to the Observations affected by a timestamped CONTEXTUALIZED link.
   * </ul>
   *
   * TODO the observation may come with resolution informations added from the outside, in the form
   *  of metadata that point to an adapter configuration. That needs to be validated and ingested by
   *  the DT before assigning an ID and returning.
   *
   * @param observation
   * @param scope
   * @return
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

      var agent =
          serviceContextScope.getConstraint(ResolutionConstraint.Type.Provenance, Agent.class);
      var storedAgent =
          agent == null
              ? null
              : serviceContextScope
                  .getDigitalTwin()
                  .getKnowledgeGraph()
                  .requireAgent(agent.getName());
      var contextScope = serviceContextScope.initializeResolution();
      var resolver = scope.getService(Resolver.class);
      var resolution =
          Activity.of("Resolution of " + observation, Activity.Type.RESOLUTION, this, agent);

      var runningScope = contextScope.executing(resolution);
      return resolver
          /* resolve asynchronously */
          .resolve(observation, contextScope)
          /* then compile the dataflow */
          .thenApply(
              dataflow -> {
                if (!dataflow.isEmpty()) {
                  /*
                   * Compile an atomic transaction from the dataflow, adding new observations if the digital twin does not have them.
                   */
                  var transaction =
                      scope
                          .getDigitalTwin()
                          .transaction(
                              resolution, runningScope, dataflow, observation, storedAgent);

                  if (compile(observation, dataflow, runningScope, transaction)) {
                    if (transaction.commit()) {
                      // send the committed graph before submitting the observation to the
                      // scheduler,
                      runningScope.send(
                          Message.MessageClass.DigitalTwin,
                          Message.MessageType.KnowledgeGraphCommitted,
                          transaction.getGraph());
                      return observation;
                    }
                  }
                }
                return Observation.empty();
              })
          /* then submit the observation to the scheduler, which will trigger contextualization */
          .thenApply(
              o -> {
                runningScope.getDigitalTwin().getScheduler().submit(o, resolution);
                return o;
              });
    }
    throw new KlabInternalErrorException(
        "RuntimeService::observe() called with unexpected scope implementation");
  }

  private boolean compile(
      Observation rootObservation,
      Dataflow dataflow,
      ServiceContextScope scope,
      DigitalTwin.Transaction transaction) {

    transaction.add(rootObservation);
    transaction.link(
        scope.getContextObservation() == null
            ? scope.getDigitalTwin().getKnowledgeGraph().scope()
            : scope.getContextObservation(),
        rootObservation,
        GraphModel.Relationship.HAS_CHILD);

    transaction.link(
        transaction.getActivity(),
        rootObservation,
        rootObservation.getId() < 0
            ? GraphModel.Relationship.CREATED
            : GraphModel.Relationship.RESOLVED);

    if (transaction instanceof DigitalTwinImpl.TransactionImpl transactionImpl) {
      for (var rootActuator : dataflow.getComputation()) {
        var executionSequence = new CompiledDataflow(this, /*dataflow,*/ rootObservation, scope);
        if (!executionSequence.compile(rootActuator)) {
          transaction.fail(
              new KlabCompilationError(
                  "Could not compile execution sequence for this target observation"));
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
