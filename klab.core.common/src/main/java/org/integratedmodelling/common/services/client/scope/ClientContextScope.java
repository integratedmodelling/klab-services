package org.integratedmodelling.common.services.client.scope;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

public class ClientContextScope extends ClientSessionScope implements ContextScope {

  private Observation observer;
  private Observation contextObservation;
  private final Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
      new LinkedHashMap<>();
  private ClientDigitalTwin digitalTwin;
  private DigitalTwin.Transaction transaction;
  private final DigitalTwin.Configuration configuration;
  private final Data.ShardingStrategy shardingStrategy;

  /**
   * The default client scope has the user as the embedded agent.
   *
   * @param parent
   * @param runtimeService
   */
  public ClientContextScope(
      ClientSessionScope parent,
      RuntimeService runtimeService,
      DigitalTwin.Configuration configuration) {
    super(parent, configuration.getName(), runtimeService);
    this.configuration = configuration;
    if (configuration.getUrl() == null
        && configuration instanceof ConfigurationImpl configurationImpl) {
      configurationImpl.setUrl(Utils.URLs.newURL(runtimeService.getUrl() + "/dt/" + getId()));
    }
    this.name = configuration.getName();
    this.shardingStrategy = new Data.ShardingStrategy();
    this.setHostServiceId(runtimeService.serviceId());
    // user in the session may be different from the user owning the engine
    var userScope = getParentScope(Type.USER, UserScope.class);
    resolutionConstraints.put(
        ResolutionConstraint.Type.Provenance,
        ResolutionConstraint.of(
            ResolutionConstraint.Type.Provenance, Agent.create(userScope.getUser().getUsername())));
  }

  /**
   * This is for derived scopes only
   *
   * @param parent
   */
  private ClientContextScope(ClientContextScope parent) {
    super(parent, parent.name, parent.runtimeService);
    // this will have been reset by super to the user's id
    setId(parent.getId());
    this.digitalTwin = parent.digitalTwin;
    resolutionConstraints.putAll(parent.resolutionConstraints);
    observer = parent.observer;
    configuration = parent.configuration;
    contextObservation = parent.contextObservation;
    shardingStrategy = parent.shardingStrategy;
    this.transaction = parent.transaction;
  }

  @Override
  public URL getUrl() {
    return configuration.getUrl();
  }

  @Override
  public Observation getObserver() {
    return this.observer;
  }

  @Override
  public Observation getContextObservation() {
    return this.contextObservation;
  }

  @Override
  public ContextScope withObserver(Observation observer) {
    var ret = childContext(this);
    ret.observer = observer;
    return ret;
  }

  protected ClientContextScope childContext(final ClientContextScope parent) {
    var ret = new ClientContextScope(parent);
    ret.copyMessagingSetup(parent);
    return ret;
  }

  @Override
  public ContextScope within(Observation contextObservation) {
    var ret = childContext(this);
    ret.contextObservation = contextObservation;
    return ret;
  }

  @Override
  public Provenance getProvenanceOf(Observation observation) {
    // TODO
    return null;
  }

  @Override
  public void setId(String id) {
    super.setId(id);
    if (this.configuration instanceof ConfigurationImpl configurationImpl) {
      configurationImpl.setId(id);
    }
  }

  @Override
  public ContextScope connect(ContextScope remoteContext) {
    return null;
  }

  @Override
  public CompletableFuture<Observation> submit(Observation observation) {
    var runtime = getService(RuntimeService.class);
    var submissionContext = this;
    // substantials are always submitted at root level
    if (SemanticType.isSubstantial(observation.getObservable().getSemantics().getType())
        && contextObservation != null) {
      // keep scenarios and any other config by deriving a new context with ctx = null
      submissionContext = (ClientContextScope) submissionContext.within(null);
    }
    return runtime.submit(observation, submissionContext);
  }

  @Override
  public Provenance getProvenance() {
    return null;
  }

  @Override
  public Report getReport() {
    return null;
  }

  @Override
  public Dataflow getDataflow() {
    return null;
  }

  @Override
  public RuntimeAsset getParentOf(RuntimeAsset observation) {

    if (observation.getParentId() <= 1000) {
      // switching on long would be so much better
      return observation.getParentId() == RuntimeAsset.CONTEXT_ASSET_ID
          ? RuntimeAsset.CONTEXT_ASSET
          : (observation.getParentId() == RuntimeAsset.PROVENANCE_ASSET_ID
              ? RuntimeAsset.PROVENANCE_ASSET
              : RuntimeAsset.DATAFLOW_ASSET);
    }

    if (observation.getParentId() > 0) {
      return digitalTwin
          .getKnowledgeGraph()
          .getAsset(observation.getParentId(), this, RuntimeAsset.class);
    }

    // this only happens with parentId == -1, which is probably an error. Observations
    // that are being resolved should never be in the client-side KG.
    return digitalTwin
        .getKnowledgeGraph()
        .getLinks(
            observation,
            GraphModel.Relationship.Direction.INCOMING,
            this,
            GraphModel.Relationship.HAS_CHILD)
        .stream()
        .map(KnowledgeGraph.Link::source)
        .findAny()
        .orElse(null);
  }

  @Override
  public Collection<RuntimeAsset> getChildrenOf(RuntimeAsset asset) {

    // TODO use the client graph if the children # is the same as the existing relationships
    return digitalTwin
        .getKnowledgeGraph()
        .getLinks(
            asset,
            GraphModel.Relationship.Direction.OUTGOING,
            this,
            GraphModel.Relationship.HAS_CHILD)
        .stream()
        .map(KnowledgeGraph.Link::target)
        .toList();
  }

  @Override
  public Collection<RuntimeAsset> getOutgoingRelationshipsOf(RuntimeAsset observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(RuntimeAsset.class, this)
        .source(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  @Override
  public Collection<RuntimeAsset> getIncomingRelationshipsOf(RuntimeAsset observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(RuntimeAsset.class, this)
        .target(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  @Override
  public void close() {
    ClientScopeManager.INSTANCE.unregister(this);
    var runtime = getService(RuntimeService.class);
    if (runtime != null) {
      runtime.releaseContext(this);
    } else {
      throw new KlabInternalErrorException("Context scope: no runtime service available");
    }
  }

  @Override
  public <T extends Observation> Collection<T> getPerspectives(Observable observable) {
    return List.of();
  }

  @Override
  public Observation getObserverOf(Observation observation) {
    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .target(observation)
            .along(GraphModel.Relationship.HAS_OBSERVER)
            .run(this);
    return ret.isEmpty() ? null : ret.getFirst();
  }

  @Override
  public Collection<Observation> getRootObservations() {
    return getRootContextScope().getObservations();
  }

  @Override
  public ContextScope getRootContextScope() {
    var ret = this;
    while (ret.parentScope instanceof ClientContextScope contextScope) {
      ret = contextScope;
    }
    return ret;
  }

  @Override
  public ContextScope between(Observation source, Observation target) {
    // TODO
    return null;
  }

  @Override
  public ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints) {

    final var thisScope = this;
    ClientContextScope ret = childContext(this);

    if (resolutionConstraints == null) {
      ret.resolutionConstraints.clear();
    } else {
      for (var constraint : resolutionConstraints) {
        if (constraint == null || constraint.empty()) {
          continue;
        }
        if (constraint.getType() == ResolutionConstraint.Type.UnresolvedContextObservation) {
          ret.contextObservation = constraint.payload(Observation.class).getFirst();
        } else if (constraint.getType().incremental
            && ret.resolutionConstraints.containsKey(constraint.getType())) {
          ret.resolutionConstraints.put(
              constraint.getType(),
              ret.resolutionConstraints.get(constraint.getType()).merge(constraint));
        } else {
          ret.resolutionConstraints.put(constraint.getType(), constraint);
        }
      }
    }

    return ret;
  }

  @Override
  public List<ResolutionConstraint> getResolutionConstraints() {
    return Utils.Collections.promoteToList(this.resolutionConstraints.values());
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, T defaultValue) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return defaultValue;
    }
    return (T) constraint.payload(defaultValue.getClass()).getFirst();
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return null;
    }
    return (T) constraint.payload(resultClass).getFirst();
  }

  @Override
  public <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return List.of();
    }
    return constraint.payload(resultClass);
  }

  @Override
  public Observation getObservation(Observation observation) {

    var query =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .source(contextObservation == null ? this : contextObservation)
            .along(GraphModel.Relationship.HAS_CHILD);
    if (SemanticType.isSubstantial(observation.getObservable().getSemantics().getType())) {
      query =
          query.where(
              "urn",
              KnowledgeGraph.Query.Operator.EQUALS,
              getId()
                  + ":"
                  + ObservationImpl.INDIVIDUALS_CATALOG_NAME
                  + ":"
                  + observation.getUrn());
    } else {
      query =
          query.where(
              "semantics",
              KnowledgeGraph.Query.Operator.EQUALS,
              observation.getObservable().asConcept().getUrn());
    }

    var ret = query.run(this);

    return ret.isEmpty() ? null : ret.getFirst();
  }

  @Override
  public List<Observation> getObservations() {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(this)
        .along(GraphModel.Relationship.HAS_CHILD)
        .run(this);
  }

  @Override
  public Data getData(Observation... observations) {
    return null;
  }

  @Override
  public ClientDigitalTwin getDigitalTwin() {
    return this.digitalTwin;
  }

  public void createDigitalTwin(String id) {
    this.digitalTwin = new ClientDigitalTwin(this, id);
    this.digitalTwin
        .getKnowledgeGraph()
        .populate(RuntimeAsset.CONTEXT_ASSET, GraphModel.Relationship.HAS_CHILD, 2);
  }

  @Override
  public DigitalTwin.Transaction getCurrentTransaction() {
    return transaction;
  }

  public String toString() {
    return "[ClientContextScope] "
        + name
        + ": "
        + getId()
        + " ("
        + (isConnected() ? "connected" : "not connected")
        + ")";
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy(Observation observation) {
    return shardingStrategy;
  }

  @Override
  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }
}
