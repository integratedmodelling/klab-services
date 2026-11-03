package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** An exact local configuration catalogue, including its empty-membership proof. */
final class ConfigurationCatalogSnapshot {
  private ConfigurationCatalogSnapshot() {}

  record Existing(Observation observation, ConfigurationConsequenceRuntime.Revision revision) {}
  record View(RuntimeAsset placement, KnowledgeGraph.ConsequenceReadSet reads,
      Map<Long, Existing> configurations) {
    View { configurations = Map.copyOf(configurations); }
  }

  static View capture(RuntimeAsset placement, Concept meaning, ServiceContextScope scope) {
    Objects.requireNonNull(placement); Objects.requireNonNull(meaning);
    if (placement.getId() <= 0 || !(placement instanceof Cohort || placement instanceof Observation))
      throw new IllegalArgumentException("A durable configuration catalogue or bearer is required");
    var relationship = placement instanceof Cohort ? GraphModel.Relationship.HAS_MEMBER
        : GraphModel.Relationship.HAS_CHILD;
    var selection = new KnowledgeGraph.ConsequenceEdgeSelection(placement.getId(), relationship,
        GraphModel.Relationship.Direction.OUTGOING);
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    // Each provisional expansion closes its locks. The final complete union is acquired once,
    // in canonical native order, and revalidates every provisional read before releasing it.
    var perspective=ConfigurationPerspective.capture(scope);
    final KnowledgeGraph.ConsequenceReadSet first,members,captured;
    var candidateIds = new TreeSet<Long>();
    var ids = new TreeSet<Long>(); ids.add(placement.getId());
    var selections = new ArrayList<KnowledgeGraph.ConsequenceEdgeSelection>(); selections.add(selection);
    try (var transaction = graph.createTransaction(scope)) {
      first=transaction.captureConsequenceInputs(ids,selections);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Configuration catalogue discovery could not close",failure);
    }
    for (var edge : first.edges()) {
      if (edge.relationship() != relationship || !Objects.equals(edge.source().id(), placement.getId())) continue;
      if (!(edge.target().id() instanceof Long id)
          || !edge.target().labels().contains(GraphModel.Labels.OBSERVATION))
        throw new IllegalStateException("Configuration catalogue has an unsupported native member");
      candidateIds.add(id); ids.add(id);
      selections.add(new KnowledgeGraph.ConsequenceEdgeSelection(id,
          GraphModel.Relationship.EMERGED_FROM, GraphModel.Relationship.Direction.OUTGOING));
      selections.add(new KnowledgeGraph.ConsequenceEdgeSelection(id,
          GraphModel.Relationship.AFFECTS, GraphModel.Relationship.Direction.INCOMING));
    }
    try (var transaction = graph.createTransaction(scope)) {
      members=transaction.captureConsequenceInputs(ids,selections);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Configuration catalogue members could not close",failure);
    }
    for (var edge : members.edges()) {
      if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
          || edge.relationship() == GraphModel.Relationship.AFFECTS
              && "configuration-input".equals(edge.properties().get("role"))) {
        var participant=edge.relationship()==GraphModel.Relationship.EMERGED_FROM?edge.target():edge.source();
        if (!(participant.id() instanceof Long id)
            || !participant.labels().contains(GraphModel.Labels.OBSERVATION))
          throw new IllegalStateException("Configuration membership has an unsupported native identity");
        ids.add(id);
      }
    }
    if(perspective.observerId()>0)ids.add(perspective.observerId());
    if(perspective.focusId()>0)ids.add(perspective.focusId());
    try (var transaction = graph.createTransaction(scope)) {
      captured=transaction.captureConsequenceInputs(ids,selections);
      transaction.validateConsequenceInputs(first);
      transaction.validateConsequenceInputs(members);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Configuration catalogue capture could not close",failure);
    }
    if(!perspective.equals(ConfigurationPerspective.capture(scope)))
      throw new IllegalStateException("Configuration catalogue perspective changed during capture");
    if (!Objects.equals(captured.contextId(), scope.getId()))
      throw new IllegalStateException("Configuration catalogue belongs to another context");
    var reasoner = scope.getService(Reasoner.class);
    var result = new TreeMap<Long, Existing>();
    for (var asset : captured.assets()) {
      if (!candidateIds.contains(asset.id())) continue;
      if (Boolean.FALSE.equals(asset.properties().get(KnowledgeGraph.CURRENT_PROPERTY))) continue;
      var observation = graph.materializeConsequenceObservation(asset, scope);
      if (observation == null || observation.getObservable() == null)
        throw new IllegalStateException("Configuration catalogue member cannot be materialized");
      if (!observation.getObservable().is(SemanticType.CONFIGURATION)
          || !reasoner.is(observation.getObservable().asConcept(), meaning)) continue;
      var encoded = observation.getMetadata().get("klab.configuration.current");
      // Ordinary, model-created configurations are not silently adopted as detected networks.
      if (encoded == null) continue;
      if (!(encoded instanceof String text))
        throw new IllegalStateException("Managed configuration has a malformed revision receipt");
      if(!ConfigurationPerspective.read(observation).equals(perspective))continue;
      var revision = Utils.Json.parseObject(text, ConfigurationConsequenceRuntime.Revision.class);
      var participants = new TreeSet<Long>();
      for (var edge : captured.edges())
        if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
            && Objects.equals(edge.source().id(), observation.getId()) && edge.target().id() instanceof Long id)
          participants.add(id);
      if (revision.observationId() != observation.getId()
          || !participants.equals(new TreeSet<>(revision.participants()))
          || observation.getGeometry() == null
          || !revision.support().equals(observation.getGeometry().encode()))
        throw new IllegalStateException("Configuration receipt differs from its captured current state");
      result.put(observation.getId(), new Existing(observation, revision));
    }
    return new View(placement, captured, result);
  }
}
