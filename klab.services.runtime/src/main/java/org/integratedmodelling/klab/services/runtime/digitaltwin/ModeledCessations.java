package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Modeled outputs only: no endpoint, metadata marker or named termination event authorizes
 * cessation.
 */
public final class ModeledCessations {
  private ModeledCessations() {}

  public static final class Plan {
    private final CompiledDataflow.ModeledCessationAuthority authority;
    private final ServiceContextScope execution;
    private final KnowledgeGraph.ConsequenceReadSet reads;
    private final KnowledgeGraph.RoleSupportCatalogue roles;
    private final Map<Long, Observation> observations;
    private final Map<Long, String> reasons;
    private final SemanticDependencyRequest request;
    private final SemanticDependencyWitness witness;
    private final String source;

    private Plan(
        CompiledDataflow.ModeledCessationAuthority authority,
        ServiceContextScope execution,
        KnowledgeGraph.ConsequenceReadSet reads,
        KnowledgeGraph.RoleSupportCatalogue roles,
        Map<Long, Observation> observations,
        Map<Long, String> reasons,
        SemanticDependencyRequest request,
        SemanticDependencyWitness witness,
        String source) {
      this.authority = authority;
      this.execution = execution;
      this.reads = reads;
      this.roles = roles;
      this.observations = Map.copyOf(observations);
      this.reasons = Map.copyOf(reasons);
      this.request = request;
      this.witness = witness;
      this.source = source;
    }

    public Set<Long> ids() {
      return reasons.keySet();
    }

    public KnowledgeGraph.ConsequenceReadSet reads() {
      return reads;
    }

    public KnowledgeGraph.RoleSupportCatalogue roles() {
      return roles;
    }

    public CompiledDataflow.ModeledCessationAuthority authority() {
      return authority;
    }

    public void require() {
      if (!source.equals(authority.require(authority.execution())))
        throw new IllegalStateException("Modeled cessation source changed");
      var current = execution.getService(Reasoner.class).semanticDependencies(request, execution);
      if (!SemanticDependencyWitness.revalidates(witness, current))
        throw new IllegalStateException("Cessation input semantic authority no longer revalidates");
    }

    public List<KnowledgeGraph.ObservationCessation> nativeWrites(long commitId) {
      if (authority.activity().getId() <= 0)
        throw new IllegalStateException("Actual modeled activity was not durably allocated");
      return ids().stream()
          .sorted()
          .map(
              id ->
                  new KnowledgeGraph.ObservationCessation(
                      id,
                      commitId,
                      authority.activity().getId(),
                      authority.support().encode(),
                      Utils.Json.asString(
                          Map.of(
                              "source",
                              source,
                              "reason",
                              reasons.get(id),
                              "request",
                              request,
                              "witness",
                              witness)),
                      reads))
          .toList();
    }
  }

  public static void stage(
      CompiledDataflow.ModeledCessationAuthority authority, ServiceContextScope execution) {
    Objects.requireNonNull(authority);
    var owner =
        Objects.requireNonNull(authority.root(), "Modeled cessation has no actual owning root");
    owner.prepareModeledCessation(() -> stageWithCycle(authority, execution));
  }

  private static void stageWithCycle(
      CompiledDataflow.ModeledCessationAuthority authority, ServiceContextScope execution) {
    var source = authority.require(authority.execution());
    if (authority.execution() != execution
        || !(execution.getDigitalTwin() instanceof DigitalTwinImpl twin)
        || !(execution.getCurrentTransaction()
            instanceof DigitalTwinImpl.TransactionImpl transaction))
      throw new IllegalStateException("Modeled cessation requires its actual owning execution");
    // A portable binding cannot replace server-owned cycle admission. Ordinary modeled effects are
    // still permitted when automatic consequence handling is disabled.
    var bound = twin.getResolutionCycles().require(execution);
    var graph = bound.getDigitalTwin().getKnowledgeGraph();
    var requested = new TreeMap<Long, Observation>();
    for (var observation : authority.requests()) {
      if (observation == null || observation.getId() <= 0)
        throw new UnsupportedOperationException(
            "Cessation requires a committed beforeimage; same-transaction birth/cessation is unsupported");
      if (requested.putIfAbsent(observation.getId(), observation) != null) continue;
    }
    var ids = new TreeSet<Long>(requested.keySet());
    var reasons = new TreeMap<Long, String>();
    requested.keySet().forEach(id -> reasons.put(id, "MODELED_CESSATION"));
    var observer = bound.getObserverId();
    var focus = bound.getContextObservation();
    if (observer != null && observer > 0) ids.add(observer);
    if (focus != null && focus.getId() > 0) ids.add(focus.getId());
    var selections = new LinkedHashSet<KnowledgeGraph.ConsequenceEdgeSelection>();
    var rounds = new ArrayList<KnowledgeGraph.ConsequenceReadSet>();
    var observations = new TreeMap<Long, Observation>();
    KnowledgeGraph.ConsequenceReadSet captured = null;
    KnowledgeGraph.RoleSupportCatalogue roleCatalogue = null;
    for (int round = 0; round < 16; round++) {
      int count = ids.size(), selected = selections.size(), affected = reasons.size();
      for (var id : reasons.keySet())
        for (var type :
            List.of(
                GraphModel.Relationship.HAS_MEMBER,
                GraphModel.Relationship.HAS_CHILD,
                GraphModel.Relationship.AFFECTS,
                GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
                GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
                GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT))
          for (var direction : GraphModel.Relationship.Direction.values())
            selections.add(new KnowledgeGraph.ConsequenceEdgeSelection(id, type, direction));
      var proofTargets = new TreeSet<Long>(reasons.keySet());
      if (observer != null && observer > 0) proofTargets.add(observer);
      if (focus != null && focus.getId() > 0) proofTargets.add(focus.getId());
      var effects = EnumSet.copyOf(GraphModel.Relationship.CONTEXTUALIZATION_EFFECTS);
      effects.add(GraphModel.Relationship.CREATED);
      for (var id : proofTargets)
        for (var type : effects)
          selections.add(
              new KnowledgeGraph.ConsequenceEdgeSelection(
                  id, type, GraphModel.Relationship.Direction.INCOMING));
      try (var tx = graph.createTransaction(bound)) {
        captured = tx.captureConsequenceInputs(ids, selections);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
      rounds.add(captured);
      var structural = new TreeSet<Long>();
      for (var edge : captured.edges()) {
        for (var endpoint : List.of(edge.source(), edge.target()))
          if (endpoint.id() instanceof Number id
              && (endpoint.labels().contains(GraphModel.Labels.OBSERVATION)
                  || endpoint.labels().contains(GraphModel.Labels.COHORT)
                  || endpoint.labels().contains(GraphModel.Labels.ACTIVITY)))
            ids.add(id.longValue());
        if (edge.source().id() instanceof Number parent
            && reasons.containsKey(parent.longValue())
            && edge.relationship() == GraphModel.Relationship.HAS_CHILD
            && edge.target().id() instanceof Number child) structural.add(child.longValue());
        if (edge.target().id() instanceof Number endpoint
            && reasons.containsKey(endpoint.longValue())
            && List.of(
                    GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
                    GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
                    GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)
                .contains(edge.relationship())
            && edge.source().id() instanceof Number relation) structural.add(relation.longValue());
      }
      var decode = new TreeSet<Long>(reasons.keySet());
      decode.addAll(structural);
      if (observer != null && observer > 0) decode.add(observer);
      if (focus != null && focus.getId() > 0) decode.add(focus.getId());
      for (var asset : captured.assets()) {
        if (asset.labels().contains(GraphModel.Labels.COHORT))
          for (var type :
              List.of(GraphModel.Relationship.HAS_MEMBER, GraphModel.Relationship.AFFECTS))
            selections.add(
                new KnowledgeGraph.ConsequenceEdgeSelection(
                    asset.id(), type, GraphModel.Relationship.Direction.OUTGOING));
        if (!decode.contains(asset.id()) || !asset.labels().contains(GraphModel.Labels.OBSERVATION))
          continue;
        if (Boolean.FALSE.equals(asset.properties().get(KnowledgeGraph.CURRENT_PROPERTY))) {
          if (requested.containsKey(asset.id()))
            throw new IllegalStateException("Requested cessation target is no longer current");
          continue;
        }
        var observation = graph.materializeConsequenceObservation(asset, bound);
        if (observation == null || observation.getObservable() == null)
          throw new IllegalStateException("Cessation target meaning unavailable");
        observations.put(asset.id(), observation);
      }
      for (var id : requested.keySet())
        if (observations.containsKey(id)
            && (observations.get(id).getObservable().is(SemanticType.CONFIGURATION)
                || observations.get(id).getObservable().getSemantics().isCollective()
                || !SemanticType.isEnumerableSubstantial(
                    observations.get(id).getObservable().getSemantics().getType())))
          throw new UnsupportedOperationException(
              "Direct modeled cessation applies to substantials; configuration termination models remain unsupported");
      for (var id : structural) {
        var candidate = observations.get(id);
        if (candidate == null) continue;
        if (candidate.getObservable().is(SemanticType.RELATIONSHIP)) {
          ids.add(id);
          reasons.putIfAbsent(id, "REQUIRED_RELATIONSHIP_ENDPOINT");
        } else if (candidate.getObservable().is(SemanticType.CONFIGURATION)) {
          var encoded = candidate.getMetadata().get("klab.configuration.authorities");
          if (!(encoded instanceof String text))
            throw new UnsupportedOperationException(
                "Dependent configuration lacks retained grouping authority");
          var placements = DefaultConfigurationBackend.priorPlacements(text);
          if (placements.stream()
              .anyMatch(
                  p ->
                      p.kind()
                              == org.integratedmodelling.klab.runtime.consequence
                                  .ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER
                          && reasons.containsKey(p.id())))
            reasons.putIfAbsent(id, "REQUIRED_CONFIGURATION_BEARER");
          else
            throw new UnsupportedOperationException(
                "Configuration parent loss has no unambiguous original dependent grouping proof");
        } else if (SemanticType.isDependent(candidate.getObservable().getSemantics().getType())) {
          ids.add(id);
          selections.add(
              new KnowledgeGraph.ConsequenceEdgeSelection(
                  id,
                  GraphModel.Relationship.HAS_CHILD,
                  GraphModel.Relationship.Direction.INCOMING));
          var parents =
              captured.edges().stream()
                  .filter(
                      e ->
                          e.relationship() == GraphModel.Relationship.HAS_CHILD
                              && Objects.equals(e.target().id(), id)
                              && e.source().labels().contains(GraphModel.Labels.OBSERVATION))
                  .map(e -> ((Number) e.source().id()).longValue())
                  .distinct()
                  .toList();
          if (!parents.isEmpty() && parents.stream().allMatch(reasons::containsKey))
            reasons.putIfAbsent(id, "REQUIRED_INHERENT_BEARER");
          else if (parents.stream().anyMatch(reasons::containsKey))
            throw new UnsupportedOperationException(
                "Dependent observation has ambiguous independent parent support");
        }
      }
      if (count == ids.size() && selected == selections.size() && affected == reasons.size()) break;
      if (round == 15)
        throw new UnsupportedOperationException(
            "Cessation necessary-support capture did not reach a finite local closure");
    }
    try (var tx = graph.createTransaction(bound)) {
      roleCatalogue = tx.captureRoleSupportCatalogue(reasons.keySet());
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    // Unrelated recipients are never deactivated merely because they carry a conferred role.
    for (var edge : roleCatalogue.matches())
      if (edge.target().id() instanceof Number recipient
          && !reasons.containsKey(recipient.longValue()))
        throw new UnsupportedOperationException(
            "Live conferred-role recipient requires a typed support-withdrawal plan");
    ids.addAll(roleCatalogue.inputs().assetIds());
    try (var tx = graph.createTransaction(bound)) {
      captured = tx.captureConsequenceInputs(ids, selections);
      for (var earlier : rounds) tx.validateConsequenceInputs(earlier);
      tx.validateRoleSupportCatalogue(roleCatalogue);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    for (var entry : requested.entrySet()) {
      var actual = observations.get(entry.getKey());
      if (actual == null || !samePayload(actual, entry.getValue()))
        throw new IllegalStateException(
            "Reported cessation target differs from its committed native beforeimage");
      if (!covers(authority.support(), actual.getGeometry(), authority.execution()))
        throw new IllegalStateException(
            "Modeled invocation does not cover the target's full current support");
    }
    var meaningOnly = new TreeSet<Long>(reasons.keySet());
    if (observer != null && observer > 0) meaningOnly.add(observer);
    if (focus != null && focus.getId() > 0) meaningOnly.add(focus.getId());
    var snapshot =
        new ConsequenceNativeInputs.Snapshot(
            captured, observations, observer, focus == null ? 0 : focus.getId());
    var terms =
        ConsequenceInputEvidence.inherit(
            snapshot, List.of(), meaningOnly, bound.getService(Reasoner.class), bound);
    var all =
        new TreeSet<String>(
            terms); // Actual compiled source authority is separately revalidated unchanged.
    var semantic = bound.getResolutionCycleBinding().semanticBinding();
    var request =
        new SemanticDependencyRequest(
            semantic.reasonerServiceId(),
            semantic.knowledgeRevision(),
            semantic.semanticFingerprint(),
            List.copyOf(all));
    var witness = bound.getService(Reasoner.class).semanticDependencies(request, bound);
    if (witness == null
        || !request.inputHash().equals(witness.inputHash())
        || !witness.completeAt(System.currentTimeMillis()))
      throw new IllegalStateException("Cessation current input semantics are unavailable");
    transaction.stageModeledCessation(
        new Plan(
            authority,
            bound,
            captured,
            roleCatalogue,
            observations,
            reasons,
            request,
            witness,
            source));
  }

  static boolean samePayload(Observation actual, Observation requested) {
    if (actual == null
        || requested == null
        || actual.getObservable() == null
        || requested.getObservable() == null) return false;
    var a = actual.getObservable();
    var b = requested.getObservable();
    // Presentation names and synthesized empty adapter parameters are not source authority.
    // Full raw native payload remains the only preserved beforeimage and atomic commit fence.
    return actual.getId() == requested.getId()
        && actual.getParentId() == requested.getParentId()
        && Objects.equals(a.getUrn(), b.getUrn())
        && Objects.equals(a.asConcept().getUrn(), b.asConcept().getUrn())
        && Objects.equals(
            a.getObserverSemantics() == null ? null : a.getObserverSemantics().getUrn(),
            b.getObserverSemantics() == null ? null : b.getObserverSemantics().getUrn())
        && Objects.equals(
            actual.getGeometry() == null ? null : actual.getGeometry().encode(),
            requested.getGeometry() == null ? null : requested.getGeometry().encode())
        && Double.compare(actual.getResolvedCoverage(), requested.getResolvedCoverage()) == 0;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static boolean covers(Geometry support, Geometry target, ServiceContextScope scope) {
    if (support == null || target == null || support.isEmpty() || target.isEmpty()) return false;
    if (support.encode().equals(target.encode())) return true;
    var actual =
        org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(support);
    var required =
        org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(target);
    if (actual == null || required == null) return false;
    for (var extent : required.getExtents()) {
      var available = actual.extent(extent.getType());
      if (available == null
          || !((org.integratedmodelling.klab.api.knowledge.observation.scale
                      .TopologicallyComparable)
                  available)
              .contains(extent)) return false;
    }
    return true;
  }
}
