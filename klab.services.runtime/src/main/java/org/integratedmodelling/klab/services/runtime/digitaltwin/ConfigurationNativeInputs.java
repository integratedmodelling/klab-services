package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Complete bearer/cohort-local native projection; no context-wide observation scan or speculative
 * cache reads. First capture locks placement, then expands the exact captured structural domain.
 */
public final class ConfigurationNativeInputs {
  private ConfigurationNativeInputs() {}

  public record Capture(
      KnowledgeGraph.ConsequenceReadSet reads,
      Map<Long, Observation> observations,
      List<ConsequencePatternMatcher.Fact> facts,
      Map<ConsequencePatternMatcher.Placement, ConsequencePatternMatcher.Fact> contexts,
      Map<Long, ConfigurationComponents.EndpointKind> relationships,
      KnowledgeGraph.ContextCohortCatalogue catalogue,
      Set<String> domainTerms) {
    public Capture(
        KnowledgeGraph.ConsequenceReadSet reads,
        Map<Long, Observation> observations,
        List<ConsequencePatternMatcher.Fact> facts,
        Map<ConsequencePatternMatcher.Placement, ConsequencePatternMatcher.Fact> contexts,
        Map<Long, ConfigurationComponents.EndpointKind> relationships) {
      this(reads, observations, facts, contexts, relationships, null, Set.of());
    }

    public Capture {
      observations = Map.copyOf(observations);
      facts = List.copyOf(facts);
      contexts = Map.copyOf(contexts);
      relationships = Map.copyOf(relationships);
      domainTerms = Set.copyOf(domainTerms);
    }

    public ConsequencePatternMatcher.Snapshot snapshot(ConsequenceRuleEpoch epoch, long observer) {
      return new ConsequencePatternMatcher.Snapshot(
          reads.contextId(), observer, epoch.value(), facts, contexts, true, reads, catalogue);
    }
  }

  // A distinct type prevents accidentally passing current coordinator capabilities at projection.
  public record ConsequenceRuleEpoch(
      org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse.Epoch
          value) {
    public ConsequenceRuleEpoch {
      if (value == null || !value.valid())
        throw new IllegalArgumentException("Retained semantic epoch required");
    }
  }

  public static Capture captureRoot(
      List<org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse.Entry>
          recipes,
      long observer,
      ServiceContextScope scope) {
    final KnowledgeGraph.ContextCohortCatalogue catalogue;
    try (var tx = scope.getDigitalTwin().getKnowledgeGraph().createTransaction(scope)) {
      catalogue = tx.captureContextCohortCatalogue();
    } catch (java.io.IOException unavailable) {
      throw new IllegalStateException("Context catalogue could not close safely", unavailable);
    }
    if (catalogue == null || !scope.getId().equals(catalogue.contextId()))
      throw new ConfigurationComponents.UnsupportedGrouping(
          "Complete owned context catalogue unavailable");
    var reasoner = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    var terms = new TreeSet<String>();
    var bases = new TreeMap<String, org.integratedmodelling.klab.api.knowledge.Concept>();
    for (var recipe : recipes)
      for (var leaf :
          org.integratedmodelling.klab.runtime.consequence.ConsequencePatterns.positiveLeaves(
              recipe.rule().target())) {
        var meaning = reasoner.resolveConcept(leaf.getUrn());
        var base = meaning == null ? null : reasoner.baseSubstantialType(meaning, scope);
        if (base == null
            || base.is(SemanticType.NOTHING)
            || !SemanticType.isEnumerableSubstantial(base.getType()))
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Ingredient base cohort meaning unavailable or unsupported");
        bases.put(base.getUrn(), base);
        terms.add(leaf.getUrn());
        terms.add(base.getUrn());
      }
    var cohorts = new TreeSet<Long>();
    for (var header : catalogue.cohorts()) {
      var urn = header.properties().get(GraphModel.Fields.OBSERVABLE);
      var meaning = urn instanceof String text ? reasoner.resolveConcept(text) : null;
      if (meaning == null)
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Captured cohort header meaning unavailable");
      terms.add(meaning.getUrn());
      boolean selected = false;
      for (var base : bases.values()) selected |= cohortMayMatch(meaning, base, reasoner, terms);
      if (selected) cohorts.add(header.id());
    }
    return captureDomain(
        ConsequencePatternMatcher.Placement.context(scope.getId()),
        observer,
        scope,
        catalogue,
        cohorts,
        terms);
  }

  /**
   * Negative ancestry is not disjointness: qualified/fallback aliases and multiple inheritance may
   * still have members in this native cohort. Only typed relationship nonoverlap or an actual
   * unsatisfiable intersection excludes it; every comparison term enters fresh authority.
   */
  static boolean cohortMayMatch(
      org.integratedmodelling.klab.api.knowledge.Concept header,
      org.integratedmodelling.klab.api.knowledge.Concept base,
      org.integratedmodelling.klab.api.services.Reasoner reasoner,
      Set<String> terms) {
    try {
      if (header == null
          || base == null
          || header.is(SemanticType.NOTHING)
          || base.is(SemanticType.NOTHING)
          || header.getType() == null
          || header.getType().isEmpty()
          || base.getType() == null
          || base.getType().isEmpty()
          || SemanticType.fundamentalType(header.getType()) == SemanticType.NOTHING
          || !SemanticType.isEnumerableSubstantial(base.getType()))
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Canonical cohort category comparison unavailable");
      terms.add(header.getUrn());
      terms.add(base.getUrn());
      if (reasoner.is(header, base) || reasoner.is(base, header)) return true;
      // The native cohort's canonical substantial head cannot change through predicate attribution.
      // Existing OWL composition itself forbids mixed relationship/nonrelationship expressions.
      if (header.is(SemanticType.RELATIONSHIP) != base.is(SemanticType.RELATIONSHIP)) return false;
      var overlap =
          reasoner.compose(
              List.of(header, base),
              org.integratedmodelling.klab.api.lang.LogicalConnector.INTERSECTION);
      if (overlap == null || overlap.getUrn() == null || overlap.getUrn().isBlank())
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Cohort intersection proof unavailable");
      var canonical = reasoner.resolveConcept(overlap.getUrn());
      if (canonical == null || !overlap.getUrn().equals(canonical.getUrn()))
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Cohort intersection is not canonically retained");
      terms.add(canonical.getUrn());
      return reasoner.satisfiable(canonical);
    } catch (ConfigurationComponents.UnsupportedGrouping unsupported) {
      throw unsupported;
    } catch (RuntimeException unavailable) {
      throw new ConfigurationComponents.UnsupportedGrouping(
          "Cohort exclusion proof unavailable: " + unavailable.getMessage());
    }
  }

  public static Capture capture(
      ConsequencePatternMatcher.Placement placement, long observer, ServiceContextScope scope) {
    if (placement.kind() == ConsequencePatternMatcher.PlacementKind.ROOT_CONTEXT)
      throw new IllegalArgumentException(
          "Context domain requires an authored bounded cohort catalogue query");
    return captureDomain(placement, observer, scope, null, Set.of(placement.id()), Set.of());
  }

  private static Capture captureDomain(
      ConsequencePatternMatcher.Placement placement,
      long observer,
      ServiceContextScope scope,
      KnowledgeGraph.ContextCohortCatalogue catalogue,
      Collection<Long> domainIds,
      Set<String> domainTerms) {
    var focus = scope.getContextObservation();
    long focusId = focus == null ? 0 : focus.getId();
    if (focusId < 0)
      throw new ConfigurationComponents.UnsupportedGrouping(
          "Configuration focus is not a durable observed context");
    boolean root = placement.kind() != ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER;
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var membership = root ? GraphModel.Relationship.HAS_MEMBER : GraphModel.Relationship.HAS_CHILD;
    var selections = new ArrayList<KnowledgeGraph.ConsequenceEdgeSelection>();
    for (var id : domainIds)
      selections.add(
          new KnowledgeGraph.ConsequenceEdgeSelection(
              id, membership, GraphModel.Relationship.Direction.OUTGOING));
    final KnowledgeGraph.ConsequenceReadSet read;
    var memberIds = new TreeSet<Long>();
    var origins = new TreeMap<Long, Long>();
    var placementRead = detachedCapture(scope, domainIds, selections, List.of());
    var ids = new TreeSet<Long>(domainIds);
    if (catalogue != null) ids.addAll(catalogue.cohortIds());
    for (var edge : placementRead.edges())
      if (edge.relationship() == membership
          && edge.source().id() instanceof Long source
          && domainIds.contains(source)) {
        if (!(edge.target().id() instanceof Long id)
            || !edge.target().labels().contains(GraphModel.Labels.OBSERVATION))
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Local placement has unsupported member identity");
        var prior = origins.putIfAbsent(id, source);
        if (prior != null && !prior.equals(source))
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Observation has ambiguous native cohort membership");
        memberIds.add(id);
        ids.add(id);
      }
    if (observer > 0) ids.add(observer);
    if (focusId > 0) ids.add(focusId);
    for (var id : memberIds)
      for (var relation :
          List.of(
              GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
              GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
              GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT,
              GraphModel.Relationship.EMERGED_FROM,
              GraphModel.Relationship.AFFECTS))
        selections.add(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                id, relation, GraphModel.Relationship.Direction.OUTGOING));
    var local = detachedCapture(scope, ids, selections, List.of());
    for (var edge : local.edges())
      for (var endpoint : List.of(edge.source(), edge.target()))
        if (endpoint.id() instanceof Long id
            && (endpoint.labels().contains(GraphModel.Labels.OBSERVATION)
                || endpoint.labels().contains(GraphModel.Labels.COHORT))) ids.add(id);
    // Every provisional lock has been released. Only this final complete sorted union is held
    // while validating all earlier snapshots; newly discovered low IDs cannot invert lock order.
    try (var tx = graph.createTransaction(scope)) {
      read = tx.captureConsequenceInputs(new TreeSet<>(ids), selections);
      tx.validateConsequenceInputs(placementRead);
      tx.validateConsequenceInputs(local);
      if (catalogue != null) tx.validateContextCohortCatalogue(catalogue);
    } catch (java.io.IOException unavailable) {
      throw new IllegalStateException("Final context capture could not close safely", unavailable);
    }
    if (!Objects.equals(scope.getId(), read.contextId()))
      throw new IllegalStateException("Captured configuration context changed");
    var observations = new TreeMap<Long, Observation>();
    for (var asset : read.assets())
      if (asset.labels().contains(GraphModel.Labels.OBSERVATION)
          && !Boolean.FALSE.equals(asset.properties().get(KnowledgeGraph.CURRENT_PROPERTY))) {
        var observation = graph.materializeConsequenceObservation(asset, scope);
        if (observation == null || observation.getId() != asset.id())
          throw new IllegalStateException("Canonical configuration fact projection is unavailable");
        observations.put(asset.id(), observation);
      }
    if (observer > 0) {
      var selected = observations.get(observer);
      if (selected == null
          || selected.isEmpty()
          || selected.getObservable() == null
          || !selected.getObservable().is(SemanticType.AGENT))
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Selected observer is not an accessible captured Agent");
    }
    var facts = new ArrayList<ConsequencePatternMatcher.Fact>();
    var relationships = new TreeMap<Long, ConfigurationComponents.EndpointKind>();
    for (var id : memberIds) {
      if (read.assets().stream()
          .anyMatch(
              a ->
                  a.id() == id
                      && Boolean.FALSE.equals(a.properties().get(KnowledgeGraph.CURRENT_PROPERTY))))
        continue;
      var observation = observations.get(id);
      var meaning = observation == null ? null : observation.getObservable();
      if (meaning == null || observation.isEmpty())
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Local member has unavailable committed meaning");
      double coverage = observation.getResolvedCoverage();
      if (!Double.isFinite(coverage) || coverage < 0 || coverage > 1.0 + 1e-9)
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Local member " + id + " has malformed coverage evidence: " + coverage);
      // Retain the complete native family and meaning. A requested geometry is never promoted
      // to actual support; recipe/observer relevance is established before support is required.
      String actualSupport =
          observation.getGeometry() != null && coverage >= 1.0 - 1e-9
              ? observation.getGeometry().encode()
              : null;
      boolean dependent =
          placement.kind() == ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER
              && SemanticType.isDependent(meaning.getSemantics().getType());
      facts.add(
          new ConsequencePatternMatcher.Fact(
              id,
              meaning.getSemantics().getUrn(),
              actualSupport,
              dependent,
              dependent ? placement.id() : 0,
              root ? origins.get(id) : 0));
      if (meaning.is(SemanticType.RELATIONSHIP)) {
        var axes =
            org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(
                meaning.getSemantics().getType());
        try {
          axes.requireComplete(meaning.getUrn());
        } catch (IllegalArgumentException unresolved) {
          throw new ConfigurationComponents.UnsupportedGrouping(unresolved.getMessage());
        }
        relationships.put(
            id,
            axes.orientation()
                    == org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Orientation.BOND
                ? ConfigurationComponents.EndpointKind.BOND
                : ConfigurationComponents.EndpointKind.DIRECTED);
      }
    }
    var contexts =
        new HashMap<ConsequencePatternMatcher.Placement, ConsequencePatternMatcher.Fact>();
    if (placement.kind() == ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER) {
      var bearer = observations.get(placement.id());
      if (bearer == null || bearer.getObservable() == null || bearer.getGeometry() == null)
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Configuration bearer/support is not captured");
      contexts.put(
          placement,
          new ConsequencePatternMatcher.Fact(
              bearer.getId(),
              bearer.getObservable().getSemantics().getUrn(),
              bearer.getGeometry().encode(),
              false,
              0,
              0));
    }
    if (root && focusId > 0) {
      var context = observations.get(focusId);
      if (context == null
          || context.isEmpty()
          || context.getObservable() == null
          || context.getGeometry() == null
          || !SemanticType.isSubstantial(context.getObservable().getSemantics().getType()))
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Observed configuration context is not a captured substantial focus");
      contexts.put(
          placement,
          new ConsequencePatternMatcher.Fact(
              context.getId(),
              context.getObservable().getSemantics().getUrn(),
              context.getGeometry().encode(),
              false,
              0,
              0));
    }
    if (focusId
            != (scope.getContextObservation() == null ? 0 : scope.getContextObservation().getId())
        || observer != (scope.getObserverId() == null ? 0 : scope.getObserverId()))
      throw new ConfigurationComponents.UnsupportedGrouping(
          "Configuration execution perspective changed during capture");
    return new Capture(read, observations, facts, contexts, relationships, catalogue, domainTerms);
  }

  static KnowledgeGraph.ConsequenceReadSet detachedCapture(
      ServiceContextScope scope,
      Collection<Long> ids,
      Collection<KnowledgeGraph.ConsequenceEdgeSelection> selections,
      List<KnowledgeGraph.ConsequenceReadSet> expected) {
    try (var tx = scope.getDigitalTwin().getKnowledgeGraph().createTransaction(scope)) {
      var result = tx.captureConsequenceInputs(new TreeSet<>(ids), List.copyOf(selections));
      for (var earlier : expected) tx.validateConsequenceInputs(earlier);
      return result;
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(
          "Local configuration capture could not close safely", failure);
    }
  }
}
