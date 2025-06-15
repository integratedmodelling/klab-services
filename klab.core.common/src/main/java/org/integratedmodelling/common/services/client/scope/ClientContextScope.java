package org.integratedmodelling.common.services.client.scope;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

public abstract class ClientContextScope extends ClientSessionScope implements ContextScope {

  private Observation observer;
  private Observation contextObservation;
  private Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
      new LinkedHashMap<>();
  private DigitalTwin digitalTwin;
  private Activity activity;
  private DigitalTwin.Configuration configuration;

  /**
   * The default client scope has the user as the embedded agent.
   *
   * @param parent
   * @param runtimeService
   */
  public ClientContextScope(
      ClientUserScope parent,
      RuntimeService runtimeService,
      DigitalTwin.Configuration configuration) {
    super(parent, configuration.getName(), runtimeService);
    this.configuration = configuration;
    resolutionConstraints.put(
        ResolutionConstraint.Type.Provenance,
        ResolutionConstraint.of(
            ResolutionConstraint.Type.Provenance, Agent.create(parent.getUser().getUsername())));
  }

  private ClientContextScope(ClientContextScope parent) {
    super(parent, parent.name, parent.runtimeService);
    // this will have been reset by super to the user's id
    setId(parent.getId());
    this.digitalTwin = parent.digitalTwin;
    resolutionConstraints.putAll(parent.resolutionConstraints);
    observer = parent.observer;
    configuration = parent.configuration;
    contextObservation = parent.contextObservation;
    this.activity = parent.activity;
  }

  @Override
  public URL getUrl() {
    return null;
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
    var ret =
        new ClientContextScope(parent) {

          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            return parent.getService(serviceClass);
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            return parent.getServices(serviceClass);
          }
        };
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
  public ContextScope connect(ContextScope remoteContext) {
    return null;
  }

  @Override
  public CompletableFuture<Observation> submit(Observation observation) {
    var runtime = getService(RuntimeService.class);
    return runtime.submit(observation, this);
  }

  @Override
  public Collection<Observation> affects(Observation observation) {
    return List.of();
  }

  @Override
  public Collection<Observation> affected(Observation observation) {
    return List.of();
  }

  @Override
  public void runTransitions() {}

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
  public Observation getParentOf(Observation observation) {
    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .target(observation)
            .along(GraphModel.Relationship.HAS_CHILD)
            .run(this);
    return ret.isEmpty() ? null : ret.getFirst();
  }

  @Override
  public Collection<Observation> getChildrenOf(Observation observation) {
    return getDigitalTwin()
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(observation)
        .along(GraphModel.Relationship.HAS_CHILD)
        .run(this);
  }

  @Override
  public Collection<Observation> getOutgoingRelationshipsOf(Observation observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  @Override
  public Collection<Observation> getIncomingRelationshipsOf(Observation observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .target(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  @Override
  public void close() {
    var runtime = getService(RuntimeService.class);
    if (runtime != null) {
      runtime.releaseContext(this);
    } else {
      throw new KlabInternalErrorException("Context scope: no runtime service available");
    }
  }

  @Override
  public boolean isConsistent() {
    return false;
  }

  @Override
  public Collection<Observation> getInconsistencies(boolean dependentOnly) {
    return List.of();
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
  public Observation getObservation(Semantics observable) {
    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .source(this)
            .along(GraphModel.Relationship.HAS_CHILD)
            .where(
                "semantics", KnowledgeGraph.Query.Operator.EQUALS, observable.asConcept().getUrn())
            .run(this);
    // TODO may need to adapt units or the like if the request is an observable
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
  public DigitalTwin getDigitalTwin() {
    return this.digitalTwin;
  }

  public void createDigitalTwin(String id) {
    this.digitalTwin = new ClientDigitalTwin(this, id);
  }

  @Override
  public Activity getCurrentActivity() {
    return activity;
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

  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }
}
