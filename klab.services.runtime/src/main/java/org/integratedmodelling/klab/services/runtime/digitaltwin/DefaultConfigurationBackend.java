package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.integratedmodelling.klab.services.runtime.digitaltwin.ConfigurationConsequenceRuntime.*;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.consequence.*;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Concrete incremental configuration orchestration. Only captured local placements are scanned; the
 * complete owner family is partitioned once, then published in one guarded transaction.
 */
public final class DefaultConfigurationBackend implements ConsequenceEngine.Handler {
  @FunctionalInterface
  public interface Cycles {
    ServiceContextScope require(ServiceContextScope scope);
  }

  private record Event(
      KnowledgeGraph.ConsequenceReadSet reads,
      Map<Long, Observation> observations,
      Set<Long> changed,
      Set<ConsequencePatternMatcher.Placement> placements) {}

  private record Family(ConsequenceRuleQuery query, ConsequenceRuleResponse response) {}

  private record InputProof(
      KnowledgeGraph.ConsequenceReadSet reads,
      SemanticDependencyRequest request,
      SemanticDependencyWitness witness) {}

  public record PriorAuthorityReference(
      String contentHash, List<ConsequencePatternMatcher.Placement> placements) {
    public PriorAuthorityReference {
      placements = List.copyOf(placements);
      if (contentHash == null || !contentHash.matches("[a-f0-9]{64}"))
        throw new IllegalArgumentException("Exact prior native authority hash required");
    }
  }

  static String digest(String text) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  static List<ConsequencePatternMatcher.Placement> priorPlacements(String text) {
    try {
      var tree =
          org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
              .readTree(text);
      var placements = new LinkedHashSet<ConsequencePatternMatcher.Placement>();
      for (var receipt : tree) {
        if (receipt.hasNonNull("match"))
          placements.add(
              Utils.Json.parseObject(
                  receipt.get("match").get("placement").toString(),
                  ConsequencePatternMatcher.Placement.class));
        else if (receipt.hasNonNull("modelPlanJson")) {
          var prior =
              Utils.Json.parseObject(receipt.get("modelPlanJson").asText(), RebuildAuthority.class);
          placements.addAll(
              Utils.Json.parseObject(prior.priorAuthorities(), PriorAuthorityReference.class)
                  .placements());
        }
      }
      return List.copyOf(placements);
    } catch (java.io.IOException malformed) {
      throw new IllegalStateException("Configuration prior placement receipt malformed", malformed);
    }
  }

  private final RuntimeService runtime;
  private final Cycles cycles;
  private final GuardedConfigurationPublisher publisher;

  public DefaultConfigurationBackend(RuntimeService runtime, Cycles cycles) {
    this(
        runtime,
        cycles,
        (tx, outputs, prepared, updates, births, scope) -> {
          throw new IllegalStateException("Authenticated Runtime detection completion is required");
        });
  }

  public DefaultConfigurationBackend(
      RuntimeService runtime, Cycles cycles, GuardedConfigurationPublisher.Completion completion) {
    this.runtime = Objects.requireNonNull(runtime);
    this.cycles = Objects.requireNonNull(cycles);
    publisher =
        new GuardedConfigurationPublisher(
            this::admit,
            this::admit,
            (observation, execution) -> {
              var prepared = runtime.prepareConfigurationExplanation(observation, execution);
              prepared.require(runtime, observation, execution);
              observation
                  .getMetadata()
                  .put(RuntimeService.CONFIGURATION_EXPLANATION_METADATA, prepared.provenance());
              execution.getCurrentTransaction().update(observation);
              return new GuardedConfigurationPublisher.Prepared(
                  GuardedConfigurationPublisher.Preparation.valueOf(prepared.status().name()),
                  prepared.support(),
                  prepared);
            },
            completion);
  }

  private static final class Deferred extends RuntimeException {
    Deferred(String reason) {
      super(reason);
    }
  }

  private static void active(ServiceContextScope scope) throws InterruptedException {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new InterruptedException("Configuration detection cancelled");
  }

  private static ConsequenceRuleResponse.Epoch epoch(ServiceContextScope scope) {
    var binding = scope.getResolutionCycleBinding();
    if (binding == null || binding.semanticBinding() == null)
      throw new Deferred("Retained semantic cycle unavailable");
    var semantic = binding.semanticBinding();
    return new ConsequenceRuleResponse.Epoch(
        semantic.reasonerServiceId(),
        semantic.knowledgeRevision(),
        semantic.semanticFingerprint(),
        semantic.reasonerSessionId());
  }

  private ServiceContextScope execution(ServiceContextScope scope) {
    var result = Objects.requireNonNull(cycles.require(scope));
    var binding = result.getResolutionCycleBinding();
    if (binding == null
        || !binding.contextId().equals(scope.getId())
        || !binding.runtimeServiceId().equals(runtime.serviceId()))
      throw new SecurityException("Configuration cycle belongs to another runtime/context");
    return result;
  }

  @Override
  public ConsequenceEngine.Result evaluate(
      Consequence.Cause cause, KnowledgeGraph.Commit commit, ServiceContextScope requested)
      throws Exception {
    var scope = execution(requested);
    if (cause.kind() != Consequence.Kind.CONFIGURATION_DETECTION
        || cause.triggeringCommitId() != commit.getId()
        || !scope.getId().equals(cause.contextId()))
      throw new IllegalArgumentException(
          "Configuration cause does not identify this commit/context");
    if (!scope.getResolutionCycleBinding().consequencesEnabled())
      return result(Consequence.Outcome.NO_ACTION, "Consequences disabled", List.of());
    try {
      active(scope);
      var event = capture(commit, cause.triggeringActivityId(), scope);
      if (event.changed().isEmpty())
        return result(
            Consequence.Outcome.NO_ACTION, "Only configuration bookkeeping changed", List.of());
      var reasoner = scope.getService(Reasoner.class);
      var current = epoch(scope);
      var meanings =
          event.changed().stream()
              .map(event.observations()::get)
              .filter(Objects::nonNull)
              .map(o -> o.getObservable().asConcept())
              .toList();
      var initial =
          query(
              new ConsequenceRuleQuery(
                  List.of(), meanings, Set.of(KimConsequenceRule.Kind.EMERGES_FROM), current),
              scope);
      var owners = new TreeMap<String, Concept>();
      initial.response().rules().forEach(r -> owners.put(r.owner().getUrn(), r.owner()));
      // Existing managed dependencies retain their original recipe even after source definition
      // drift.
      for (var edge : event.reads().edges())
        if (edge.relationship() == GraphModel.Relationship.AFFECTS
            && event.changed().contains(number(edge.source().id()))
            && managed(edge.properties())) {
          var target = event.observations().get(number(edge.target().id()));
          if (target == null || !target.getObservable().is(SemanticType.CONFIGURATION))
            throw new Deferred("Managed dependency target is unavailable");
          owners.put(
              target.getObservable().asConcept().getUrn(), target.getObservable().asConcept());
        }
      var updates = new ArrayList<Update>();
      var births = new ArrayList<GuardedConfigurationPublisher.Birth>();
      var families = new LinkedHashMap<Concept, Family>();
      var domains = new LinkedHashMap<Concept, Set<ConsequencePatternMatcher.Placement>>();
      for (var owner : owners.values()) {
        active(scope);
        if (!owner.is(SemanticType.CONFIGURATION))
          throw new Deferred("Emergence declaration does not name a configuration");
        var family =
            query(
                new ConsequenceRuleQuery(
                    List.of(owner),
                    List.of(),
                    Set.of(KimConsequenceRule.Kind.EMERGES_FROM),
                    current),
                scope);
        var placements = new LinkedHashSet<ConsequencePatternMatcher.Placement>();
        var dependent =
            placementRecipes(
                family.response().rules(),
                ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,
                reasoner);
        var substantial =
            placementRecipes(
                family.response().rules(),
                ConsequencePatternMatcher.PlacementKind.ROOT_CONTEXT,
                reasoner);
        var retained = retainedPlacements(owner, event);
        for (var placement : event.placements())
          if (placement.kind() == ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER
              && (!dependent.isEmpty() || retained.contains(placement))) placements.add(placement);
        boolean root =
            !substantial.isEmpty()
                && event.placements().stream()
                    .anyMatch(
                        p -> p.kind() != ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER);
        root |=
            retained.stream()
                .anyMatch(
                    p -> p.kind() != ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER);
        var focus = scope.getContextObservation();
        if (focus != null && event.changed().contains(focus.getId()))
          for (var recipe : substantial)
            for (var leaf : ConsequencePatterns.positiveLeaves(recipe.rule().target())) {
              var meaning = reasoner.resolveConcept(leaf.getUrn());
              if (meaning == null) throw new Deferred("Ingredient domain meaning unavailable");
              root |= SemanticType.isEnumerableSubstantial(meaning.getType());
            }
        if (root) placements.add(ConsequencePatternMatcher.Placement.context(scope.getId()));
        families.put(owner, family);
        domains.put(owner, Set.copyOf(placements));
        if (root && runtime.getConfigurationCohortFor(owner, scope, true) == null)
          throw new Deferred("Dedicated output configuration cohort unavailable");
      }
      // All required output cohorts now exist. No later owner can invalidate an earlier
      // owner's complete context catalogue merely by publishing its own cohort header.
      for (var owner : families.keySet())
        for (var placement : domains.get(owner))
          plan(owner, families.get(owner), placement, event, commit, scope, updates, births);
      if (updates.isEmpty() && births.isEmpty())
        return result(
            Consequence.Outcome.NO_ACTION, "No complete local configuration change", List.of());
      var effect = publisher.publishBatch(updates, births, cause, scope);
      return result(effect.outcome(), effect.reason(), effect.observations());
    } catch (Deferred
        | ConfigurationComponents.UnsupportedGrouping
        | ConfigurationObserverFilter.Unavailable unavailable) {
      return result(Consequence.Outcome.DEFERRED, unavailable.getMessage(), List.of());
    }
  }

  private static ConsequenceEngine.Result result(
      Consequence.Outcome outcome, String reason, List<Long> ids) {
    return new ConsequenceEngine.Result(outcome, reason, ids);
  }

  private static long number(Object id) {
    return id instanceof Number n ? n.longValue() : 0;
  }

  private static boolean affected(Revision previous, Event event) {
    if (!Collections.disjoint(previous.participants(), event.changed())) return true;
    return event.reads().edges().stream()
        .anyMatch(
            edge ->
                edge.relationship() == GraphModel.Relationship.AFFECTS
                    && managed(edge.properties())
                    && number(edge.target().id()) == previous.observationId()
                    && event.changed().contains(number(edge.source().id())));
  }

  static boolean managed(Map<String, Object> properties) {
    return "configuration-input".equals(properties.get("role"));
  }

  private Event capture(
      KnowledgeGraph.Commit commit, long triggeringActivity, ServiceContextScope scope) {
    var candidates = new TreeSet<Long>(ConfigurationModeledChanges.candidates(commit));
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var selections = new ArrayList<KnowledgeGraph.ConsequenceEdgeSelection>();
    final KnowledgeGraph.ConsequenceReadSet read;
    {
      var activity =
          ConfigurationNativeInputs.detachedCapture(
              scope, List.of(triggeringActivity), List.of(), List.of());
      var proof =
          activity.assets().stream()
              .filter(
                  a ->
                      a.id() == triggeringActivity
                          && a.labels().contains(GraphModel.Labels.ACTIVITY))
              .findFirst()
              .orElseThrow(
                  () ->
                      new SecurityException(
                          "Triggering activity is not native context provenance"));
      var journal = ConfigurationModeledChanges.journalChanges(proof, commit, scope.getId());
      candidates.addAll(journal);
      var declared = new TreeSet<Long>(commit.getAddedObservations());
      declared.addAll(journal);
      for (var links : List.of(commit.getAddedLinks(), commit.getDeletedLinks()))
        for (var link : links) {
          GraphModel.Relationship type;
          try {
            type = GraphModel.Relationship.valueOf(link.getThird());
          } catch (IllegalArgumentException unknown) {
            continue;
          }
          if (type == GraphModel.Relationship.HAS_MEMBER) declared.add(link.getSecond());
          else if (ConfigurationModeledChanges.STRUCTURE.contains(type)
              && type != GraphModel.Relationship.HAS_CHILD) {
            declared.add(link.getFirst());
            declared.add(link.getSecond());
          }
        }
      candidates.retainAll(nativeObservationCandidates(candidates, declared, graph, scope));
      for (var id : candidates) {
        selections.add(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                id,
                GraphModel.Relationship.HAS_MEMBER,
                GraphModel.Relationship.Direction.INCOMING));
        selections.add(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                id, GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.Direction.INCOMING));
        selections.add(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                id, GraphModel.Relationship.AFFECTS, GraphModel.Relationship.Direction.OUTGOING));
      }
      var captureIds = new TreeSet<Long>(candidates);
      for (var link : commit.getDeletedLinks())
        if (link.getFirst() > 0
            && (GraphModel.Relationship.HAS_MEMBER.name().equals(link.getThird())
                || GraphModel.Relationship.HAS_CHILD.name().equals(link.getThird())))
          captureIds.add(link.getFirst());
      var first =
          captureIds.isEmpty()
              ? activity
              : ConfigurationNativeInputs.detachedCapture(scope, captureIds, selections, List.of());
      var ids = new TreeSet<Long>(captureIds);
      ids.add(triggeringActivity);
      for (var edge : first.edges())
        for (var endpoint : List.of(edge.source(), edge.target()))
          if (number(endpoint.id()) > 0
              && (endpoint.labels().contains(GraphModel.Labels.OBSERVATION)
                  || endpoint.labels().contains(GraphModel.Labels.COHORT)))
            ids.add(number(endpoint.id()));
      var selectedObserver = scope.getObserverId();
      if (selectedObserver != null) {
        if (selectedObserver <= 0)
          throw new Deferred("Selected configuration observer is not a durable Agent");
        ids.add(selectedObserver);
      }
      var focus = scope.getContextObservation();
      if (focus != null) ids.add(focus.getId());
      read =
          ConfigurationNativeInputs.detachedCapture(
              scope, ids, selections, List.of(first, activity));
    }
    var observations = new TreeMap<Long, Observation>();
    for (var asset : read.assets())
      if (asset.labels().contains(GraphModel.Labels.OBSERVATION))
        observations.put(asset.id(), graph.materializeConsequenceObservation(asset, scope));
    if (!observations.keySet().containsAll(candidates))
      throw new Deferred("Configuration observation kind changed during native capture");
    var changed = ConfigurationModeledChanges.observations(candidates, observations);
    var placements = new LinkedHashSet<ConsequencePatternMatcher.Placement>();
    for (var edge : read.edges())
      if (changed.contains(number(edge.target().id()))) {
        if (edge.relationship() == GraphModel.Relationship.HAS_MEMBER
            && edge.source().labels().contains(GraphModel.Labels.COHORT))
          placements.add(
              new ConsequencePatternMatcher.Placement(
                  ConsequencePatternMatcher.PlacementKind.ROOT_COHORT, number(edge.source().id())));
        if (edge.relationship() == GraphModel.Relationship.HAS_CHILD
            && edge.source().labels().contains(GraphModel.Labels.OBSERVATION))
          placements.add(
              new ConsequencePatternMatcher.Placement(
                  ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,
                  number(edge.source().id())));
      }
    // A removed placement must be reconstructed from the authenticated commit, not guessed from a
    // cache.
    for (var link : commit.getDeletedLinks())
      if (changed.contains(link.getSecond())
          && ConfigurationModeledChanges.STRUCTURE.stream()
              .anyMatch(r -> r.name().equals(link.getThird()))) {
        if (GraphModel.Relationship.HAS_MEMBER.name().equals(link.getThird())
            && read.assets().stream()
                .anyMatch(
                    a ->
                        a.id() == link.getFirst() && a.labels().contains(GraphModel.Labels.COHORT)))
          placements.add(
              new ConsequencePatternMatcher.Placement(
                  ConsequencePatternMatcher.PlacementKind.ROOT_COHORT, link.getFirst()));
        if (GraphModel.Relationship.HAS_CHILD.name().equals(link.getThird())
            && observations.containsKey(link.getFirst()))
          placements.add(
              new ConsequencePatternMatcher.Placement(
                  ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER, link.getFirst()));
      }
    for (var edge : read.edges())
      if (edge.relationship() == GraphModel.Relationship.AFFECTS
          && changed.contains(number(edge.source().id()))
          && managed(edge.properties())) {
        var config = observations.get(number(edge.target().id()));
        if (config == null) throw new Deferred("Managed affected configuration is unavailable");
        var encoded = config.getMetadata().get("klab.configuration.authorities");
        if (!(encoded instanceof String text))
          throw new Deferred("Affected configuration has no retained local placement proof");
        placements.addAll(priorPlacements(text));
      }
    if (!commit.getDeletedAssets().isEmpty())
      throw new Deferred(
          "Cessation requires retained committed beforeimage and current durable placement exclusion");
    return new Event(read, Map.copyOf(observations), changed, Set.copyOf(placements));
  }

  private Set<Long> nativeObservationCandidates(
      Set<Long> candidates, Set<Long> declared, KnowledgeGraph graph, ServiceContextScope scope) {
    if (declared.stream().anyMatch(id -> id == null || id <= 0)
        || !candidates.containsAll(declared))
      throw new SecurityException(
          "Committed observation changes require durable candidate identities");
    var observations = new TreeSet<Long>();
    for (var id : candidates) {
      // Discovery selects IDs only. The subsequent detached native capture remains the
      // authority for payload, context ownership, locks and source/read-set validation.
      var rows =
          graph
              .query(RuntimeAsset.class, scope)
              .where(GraphModel.Fields.ID, KnowledgeGraph.Query.Operator.EQUALS, id)
              .run(scope);
      if (rows == null
          || rows.size() != 1
          || rows.getFirst() == null
          || rows.getFirst().getId() != id)
        throw new Deferred("Native configuration candidate kind is missing or ambiguous: " + id);
      var asset = rows.getFirst();
      if (asset instanceof Observation) observations.add(id);
      else {
        if (declared.contains(id))
          throw new SecurityException(
              "Committed observation change names a non-observation: " + id);
        // ANY queries materialize only registered native RuntimeAsset kinds. Bookkeeping
        // writes and provenance children are not scientific observation inputs.
        RuntimeAsset.Type.forClass(asset.getClass());
        if (Boolean.getBoolean("klab.consequences.trace"))
          try {
            org.integratedmodelling.common.logging.Logging.INSTANCE.info(
                "CONFIGURATION_INPUT_DISCOVERY context="
                    + scope.getId()
                    + " excluded="
                    + id
                    + " type="
                    + RuntimeAsset.Type.forClass(asset.getClass()));
          } catch (RuntimeException ignored) {
            /* Diagnostics cannot alter discovery. */
          }
      }
    }
    return observations;
  }

  private Family query(ConsequenceRuleQuery query, ServiceContextScope scope) {
    var response = scope.getService(Reasoner.class).consequenceRules(query);
    if (response == null
        || response.status() != ConsequenceRuleResponse.Status.COMPLETE
        || !epoch(scope).equals(response.epoch())
        || response.witness() == null
        || !response.witness().completeAt(System.currentTimeMillis()))
      throw new Deferred("Complete retained consequence source enumeration unavailable");
    return new Family(query, response);
  }

  static org.integratedmodelling.klab.api.knowledge.Observable configurationObservable(
      Concept owner, Reasoner reasoner) {
    // Portable rule owners carry semantic identity, not a fully constructed observable activity.
    var observable = reasoner.resolveObservable(owner.getUrn());
    if (observable == null
        || observable.asConcept() == null
        || !owner.getUrn().equals(observable.asConcept().getUrn())
        || !owner.getUrn().equals(observable.getUrn())
        || !observable.is(SemanticType.CONFIGURATION)
        || observable.getContextualization() != Contextualization.DETECTION)
      throw new Deferred(
          "Canonical configuration observable is unavailable or differs from its accepted owner");
    return observable;
  }

  static List<ConsequenceRuleResponse.Entry> placementRecipes(
      List<ConsequenceRuleResponse.Entry> recipes,
      ConsequencePatternMatcher.PlacementKind placement,
      Reasoner reasoner) {
    var result = new ArrayList<ConsequenceRuleResponse.Entry>();
    boolean dependent = placement == ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER;
    for (var recipe : recipes) {
      Boolean domain = null;
      for (var leaf : ConsequencePatterns.positiveLeaves(recipe.rule().target())) {
        var meaning = reasoner.resolveConcept(leaf.getUrn());
        if (meaning == null || meaning.is(SemanticType.NOTHING))
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Configuration ingredient domain is unavailable");
        boolean kind;
        if (SemanticType.isDependent(meaning.getType())) kind = true;
        else if (SemanticType.isEnumerableSubstantial(meaning.getType())) kind = false;
        else
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Configuration ingredient domain is unsupported");
        if (domain != null && domain != kind)
          throw new ConfigurationComponents.UnsupportedGrouping(
              "Mixed dependent/substantial configuration ingredients are unsupported");
        domain = kind;
      }
      if (domain == null)
        throw new ConfigurationComponents.UnsupportedGrouping(
            "Configuration recipe has no bounded ingredient domain");
      if (domain == dependent) result.add(recipe);
    }
    return List.copyOf(result);
  }

  private Set<ConsequencePatternMatcher.Placement> retainedPlacements(Concept owner, Event event) {
    var result = new LinkedHashSet<ConsequencePatternMatcher.Placement>();
    for (var edge : event.reads().edges())
      if (edge.relationship() == GraphModel.Relationship.AFFECTS
          && managed(edge.properties())
          && event.changed().contains(number(edge.source().id()))) {
        var previous = event.observations().get(number(edge.target().id()));
        if (previous == null
            || previous.getObservable() == null
            || !owner.getUrn().equals(previous.getObservable().asConcept().getUrn())) continue;
        var encoded = previous.getMetadata().get("klab.configuration.authorities");
        if (!(encoded instanceof String text))
          throw new Deferred("Affected configuration has no retained placement authority");
        result.addAll(priorPlacements(text));
      }
    return Set.copyOf(result);
  }

  private void plan(
      Concept owner,
      Family family,
      ConsequencePatternMatcher.Placement placement,
      Event event,
      KnowledgeGraph.Commit commit,
      ServiceContextScope scope,
      List<Update> updates,
      List<GuardedConfigurationPublisher.Birth> births)
      throws Exception {
    boolean root = placement.kind() == ConsequencePatternMatcher.PlacementKind.ROOT_CONTEXT;
    var compatible =
        placementRecipes(
            family.response().rules(), placement.kind(), scope.getService(Reasoner.class));
    var retainedDomains = retainedPlacements(owner, event);
    if (compatible.isEmpty()
        && !retainedDomains.contains(placement)
        && !(root
            && retainedDomains.stream()
                .anyMatch(
                    p -> p.kind() != ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER)))
      return;
    // Materialize the dedicated output cohort before capturing the context catalogue; our own
    // legitimate catalogue insertion must not invalidate the proof later published by this batch.
    ConfigurationNativeInputs.Capture initialLocal =
        root
            ? null
            : ConfigurationNativeInputs.capture(
                placement, scope.getObserverId() == null ? 0 : scope.getObserverId(), scope);
    RuntimeAsset catalogue =
        root
            ? runtime.getConfigurationCohortFor(owner, scope, true)
            : initialLocal.observations().get(placement.id());
    if (catalogue == null) throw new Deferred("Configuration catalogue unavailable");
    var capturedCatalogue = ConfigurationCatalogSnapshot.capture(catalogue, owner, scope);
    var placed = new TreeMap<Long, ConfigurationCatalogSnapshot.Existing>();
    for (var entry : capturedCatalogue.configurations().entrySet()) {
      var authority =
          entry.getValue().observation().getMetadata().get("klab.configuration.authorities");
      if (!(authority instanceof String text))
        throw new Deferred("Managed configuration lacks its immutable original placement proof");
      var prior = priorPlacements(text);
      if (prior.contains(placement)
          || root
              && prior.stream()
                  .anyMatch(p -> p.kind() == ConsequencePatternMatcher.PlacementKind.ROOT_COHORT))
        placed.put(entry.getKey(), entry.getValue());
    }
    var previous =
        new ConfigurationCatalogSnapshot.View(
            capturedCatalogue.placement(), capturedCatalogue.reads(), placed);
    var historical = historicalFamily(previous, scope);
    if (historical != null) family = historical;
    var recipes =
        placementRecipes(
            family.response().rules(), placement.kind(), scope.getService(Reasoner.class));
    if (recipes.isEmpty()) return;
    var local =
        root
            ? ConfigurationNativeInputs.captureRoot(
                recipes, scope.getObserverId() == null ? 0 : scope.getObserverId(), scope)
            : initialLocal;
    var catalogues =
        local.catalogue() == null
            ? List.<KnowledgeGraph.ContextCohortCatalogue>of()
            : List.of(local.catalogue());
    var semantics = semantics(scope);
    var unfiltered =
        local.snapshot(
            new ConfigurationNativeInputs.ConsequenceRuleEpoch(epoch(scope)),
            scope.getObserverId() == null ? 0 : scope.getObserverId());
    var observerView = ConfigurationObserverFilter.filter(local, unfiltered, recipes, scope);
    var full = observerView.snapshot();
    var components = partition(full, local.relationships(), recipes, semantics);
    var matches = new ArrayList<List<ConsequencePatternMatcher.Match>>();
    var members = new ArrayList<Set<Long>>();
    var matcher = new ConsequencePatternMatcher();
    // A relation admitted only by an incomplete alternative recipe cannot bridge two successful
    // networks.
    // Remove facts unused by any complete local pattern and repartition until the finite domain
    // stabilizes.
    for (int pass = 0; ; pass++) {
      matches.clear();
      members.clear();
      var retained = new TreeSet<Long>();
      for (var component : components) {
        var found = new ArrayList<ConsequencePatternMatcher.Match>();
        var ids = new TreeSet<Long>();
        for (var recipe : recipes) {
          var outcome = matcher.evaluate(recipe, component, semantics, support());
          if (outcome.outcome() == ConsequencePatternMatcher.Outcome.DEFERRED)
            throw new Deferred(outcome.reason());
          for (var match : outcome.matches()) {
            found.add(match);
            ids.addAll(match.ingredientIds());
          }
        }
        if (!ids.isEmpty()) {
          matches.add(List.copyOf(found));
          members.add(Set.copyOf(ids));
          retained.addAll(ids);
        }
      }
      var considered =
          components.stream()
              .flatMap(c -> c.facts().stream())
              .map(ConsequencePatternMatcher.Fact::id)
              .collect(java.util.stream.Collectors.toSet());
      if (considered.equals(retained)) break;
      if (pass >= full.facts().size())
        throw new IllegalStateException("Finite local configuration projection did not stabilize");
      var reduced =
          new ConsequencePatternMatcher.Snapshot(
              full.contextId(),
              full.observerId(),
              full.epoch(),
              full.facts().stream().filter(f -> retained.contains(f.id())).toList(),
              full.contexts(),
              true,
              full.reads(),
              full.catalogue());
      var kinds = new TreeMap<Long, ConfigurationComponents.EndpointKind>();
      local
          .relationships()
          .forEach(
              (id, kind) -> {
                if (retained.contains(id)) kinds.put(id, kind);
              });
      components = partition(reduced, kinds, recipes, semantics);
    }
    var references = new TreeSet<Long>();
    matches.forEach(group -> group.forEach(match -> references.addAll(match.ingredientIds())));
    for (var old : previous.configurations().values())
      for (var id : old.revision().participants())
        if (event.changed().contains(id)) references.add(id);
    var meaningOnly = new TreeSet<Long>();
    if (scope.getObserverId() != null && scope.getObserverId() > 0)
      meaningOnly.add(scope.getObserverId());
    var focus = scope.getContextObservation();
    if (focus != null) meaningOnly.add(focus.getId());
    // Exclusion consumes current meaning, including any classifier/characterization attribution.
    // Preserve those native effect sets and certificates without demanding a nonexistent model
    // execution from an unrelated unresolved sibling. Matched references retain full accounting.
    for (var fact : local.facts()) meaningOnly.add(fact.id());
    var prior = new TreeMap<Long, Set<Long>>();
    previous
        .configurations()
        .forEach((id, old) -> prior.put(id, Set.copyOf(old.revision().participants())));
    var birthProof = new HashSet<Integer>();
    for (int i = 0; i < members.size(); i++) birthProof.add(i);
    var identityInputs = new TreeMap<Long, Set<Long>>(prior);
    // Explicit native modeled dependencies retain the identity even after complete member
    // replacement.
    // A deterministic first component continues it; additional components follow ordinary split
    // policy.
    if (!members.isEmpty())
      for (var edge : event.reads().edges())
        if (edge.relationship() == GraphModel.Relationship.AFFECTS
            && managed(edge.properties())
            && event.changed().contains(number(edge.source().id()))
            && prior.containsKey(number(edge.target().id()))) {
          long target = number(edge.target().id());
          if (members.stream()
              .allMatch(component -> Collections.disjoint(component, prior.get(target))))
            identityInputs.put(
                target,
                members.stream().min(Comparator.comparingLong(Collections::min)).orElseThrow());
        }
    var assignment = ConfigurationComponents.assign(members, identityInputs, birthProof);
    var absorbed = new HashSet<Long>();
    for (var id : assignment.emptyIdentities())
      if (members.stream().anyMatch(component -> !Collections.disjoint(component, prior.get(id))))
        absorbed.add(id);
    boolean pending = false;
    for (int i = 0; i < members.size(); i++) {
      var identity = assignment.components().get(i);
      if (identity.kind() == ConfigurationComponents.IdentityKind.NEW) pending = true;
      else if (identity.kind() == ConfigurationComponents.IdentityKind.EXISTING) {
        var old = previous.configurations().get(identity.observationId()).revision();
        pending |=
            !new TreeSet<>(old.participants()).equals(new TreeSet<>(members.get(i)))
                || affected(old, event);
      }
    }
    for (var id : assignment.emptyIdentities()) {
      var old = previous.configurations().get(id).revision();
      pending |= affected(old, event) || absorbed.contains(id);
    }
    if (!pending) return;
    var inputProof =
        inputs(
            family.response(),
            references,
            meaningOnly,
            observerView,
            List.of(event.reads(), local.reads(), previous.reads()),
            scope);
    for (int i = 0; i < members.size(); i++) {
      var identity = assignment.components().get(i);
      var matched = matches.get(i);
      var support =
          RuntimeService.exactConfigurationSupport(
              matched.stream().map(m -> Geometry.create(m.support())).toList());
      if (support == null)
        throw new Deferred("Configuration support union is not exactly representable");
      var reads = List.of(event.reads(), local.reads(), previous.reads(), inputProof.reads());
      List<AuthorityReceipt> authorities =
          authorities(family, matched, previous, reads, inputProof, scope);
      if (identity.kind() == ConfigurationComponents.IdentityKind.EXISTING) {
        var old = previous.configurations().get(identity.observationId()).revision();
        if (old.participants().equals(new ArrayList<>(new TreeSet<>(members.get(i))))
            && !affected(old, event)) continue;
        updates.add(
            new Update(
                old,
                new Revision(
                    old.observationId(),
                    old.number() + 1,
                    commit.getId(),
                    List.copyOf(members.get(i)),
                    support.encode()),
                reads,
                true,
                authorities,
                catalogues));
      } else if (identity.kind() == ConfigurationComponents.IdentityKind.NEW) {
        var observation = new ObservationImpl();
        observation.setId(Observation.UNASSIGNED_ID);
        observation.setObservable(configurationObservable(owner, scope.getService(Reasoner.class)));
        observation.setGeometry(support);
        observation.setResolvedCoverage(1);
        observation
            .getMetadata()
            .put(
                ConfigurationPerspective.METADATA,
                Utils.Json.asString(ConfigurationPerspective.capture(scope)));
        long applicationContext =
            placement.kind() == ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER
                ? placement.id()
                : (scope.getContextObservation() == null
                    ? 0
                    : scope.getContextObservation().getId());
        if (applicationContext > 0)
          observation
              .getMetadata()
              .put("klab.configuration.applicationContext", applicationContext);
        births.add(
            new GuardedConfigurationPublisher.Birth(
                observation,
                catalogue,
                List.copyOf(members.get(i)),
                reads,
                authorities,
                Utils.Json.asString(Map.of("trigger", commit.getId(), "prior", prior.keySet())),
                catalogues));
      } else throw new Deferred(identity.reason());
    }
    for (var id : assignment.emptyIdentities()) {
      var old = previous.configurations().get(id).revision();
      if (!affected(old, event) && !absorbed.contains(id)) continue;
      var reads = List.of(event.reads(), local.reads(), previous.reads(), inputProof.reads());
      var authorities = authorities(family, List.of(), previous, reads, inputProof, scope);
      updates.add(
          new Update(
              old,
              new Revision(id, old.number() + 1, commit.getId(), List.of(), old.support()),
              reads,
              true,
              authorities,
              catalogues));
    }
  }

  static String familyIdentity(ConsequenceRuleResponse response) {
    // A renewed dependency lease is not a new authored family. Accepted generation, full
    // source provenance and every authored condition remain part of its exact identity.
    var json = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var entries =
        response.rules().stream()
            .map(entry -> canonicalJson(json.valueToTree(entry)))
            .sorted()
            .toList();
    return Utils.Json.asString(Map.of("epoch", response.epoch(), "rules", entries));
  }

  private static String canonicalJson(com.fasterxml.jackson.databind.JsonNode value) {
    if (value.isObject()) {
      var fields = new TreeMap<String, String>();
      value
          .fields()
          .forEachRemaining(field -> fields.put(field.getKey(), canonicalJson(field.getValue())));
      return "{"
          + fields.entrySet().stream()
              .map(field -> Utils.Json.asString(field.getKey()) + ":" + field.getValue())
              .collect(java.util.stream.Collectors.joining(","))
          + "}";
    }
    if (value.isArray()) {
      var values = new ArrayList<String>();
      value.forEach(item -> values.add(canonicalJson(item)));
      return "[" + String.join(",", values) + "]";
    }
    return value.toString();
  }

  private Family historicalFamily(
      ConfigurationCatalogSnapshot.View previous, ServiceContextScope scope) {
    Family result = null;
    String encoded = null;
    for (var old : previous.configurations().values()) {
      var raw = old.observation().getMetadata().get("klab.configuration.authorities");
      if (!(raw instanceof String text))
        throw new Deferred("Prior configuration source authority is unavailable");
      try {
        var tree =
            org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
                .readTree(text);
        for (var receipt : tree)
          if (receipt.hasNonNull("sourceResponseJson") || receipt.hasNonNull("modelPlanJson")) {
            var familyJson =
                receipt.hasNonNull("sourceResponseJson")
                    ? receipt.get("sourceResponseJson").asText()
                    : Utils.Json.parseObject(
                            receipt.get("modelPlanJson").asText(), RebuildAuthority.class)
                        .familyJson();
            var response = Utils.Json.parseObject(familyJson, ConsequenceRuleResponse.class);
            var identity = familyIdentity(response);
            if (encoded != null && !encoded.equals(identity))
              throw new Deferred(
                  "Mixed historical source families require separate partition identities");
            encoded = identity;
            var query =
                receipt.hasNonNull("queryJson")
                    ? Utils.Json.parseObject(
                        receipt.get("queryJson").asText(), ConsequenceRuleQuery.class)
                    : new ConsequenceRuleQuery(
                        response.rules().stream()
                            .map(ConsequenceRuleResponse.Entry::owner)
                            .distinct()
                            .toList(),
                        List.of(),
                        Set.of(KimConsequenceRule.Kind.EMERGES_FROM),
                        response.epoch());
            result = new Family(query, response);
          }
      } catch (java.io.IOException malformed) {
        throw new IllegalStateException("Malformed native configuration authority", malformed);
      }
    }
    return result;
  }

  private List<AuthorityReceipt> authorities(
      Family family,
      List<ConsequencePatternMatcher.Match> matches,
      ConfigurationCatalogSnapshot.View previous,
      List<KnowledgeGraph.ConsequenceReadSet> reads,
      InputProof inputProof,
      ServiceContextScope scope) {
    if (previous.configurations().isEmpty()) {
      var receipts = new ArrayList<AuthorityReceipt>();
      for (var match : matches) {
        var recipe =
            family.response().rules().stream()
                .filter(r -> r.ruleId().equals(match.ruleId()))
                .findFirst()
                .orElseThrow();
        receipts.add(
            SourceReceipt.capture(family.query(), family.response(), recipe, match)
                .withInputs(inputProof.request(), inputProof.witness()));
      }
      if (receipts.isEmpty())
        throw new Deferred("A new configuration requires a positive source match");
      return List.copyOf(receipts);
    }
    var result = new ArrayList<AuthorityReceipt>();
    var request = inputProof.request();
    var proof = inputProof.witness();
    for (var old : previous.configurations().values()) {
      var raw = old.observation().getMetadata().get("klab.configuration.authorities");
      var explanation =
          old.observation().getMetadata().get(RuntimeService.CONFIGURATION_EXPLANATION_METADATA);
      if (!(raw instanceof String source) || !(explanation instanceof String model))
        throw new Deferred("Prior modeled configuration dependency receipt unavailable");
      var authority =
          new RebuildAuthority(
              old.observation().getId(),
              Utils.Json.asString(
                  new PriorAuthorityReference(digest(source), priorPlacements(source))),
              digest(model),
              Utils.Json.asString(family.response()),
              matches.stream().map(RecipeMatch::of).toList());
      result.add(
          new AffectsReceipt(
              old.observation().getId(),
              Utils.Json.asString(authority),
              request,
              proof,
              previous.reads()));
    }
    return List.copyOf(result);
  }

  static List<ConsequencePatternMatcher.Snapshot> partition(
      ConsequencePatternMatcher.Snapshot snapshot,
      Map<Long, ConfigurationComponents.EndpointKind> relationships,
      List<ConsequenceRuleResponse.Entry> recipes,
      ConsequencePatternMatcher.Semantics semantics) {
    var eligible = new ArrayList<ConsequencePatternMatcher.Fact>();
    var kinds = new TreeMap<Long, ConfigurationComponents.EndpointKind>();
    for (var fact : snapshot.facts()) {
      boolean matches = false;
      for (var recipe : recipes)
        for (var leaf : ConsequencePatterns.positiveLeaves(recipe.rule().target())) {
          var decision = semantics.matches(fact.semantics(), leaf);
          if (decision == ConsequencePatternMatcher.Decision.UNAVAILABLE)
            throw new Deferred("Ingredient semantic matching unavailable");
          matches |= decision == ConsequencePatternMatcher.Decision.YES;
        }
      if (matches) {
        if (fact.resolvedSupport() == null || fact.resolvedSupport().isBlank())
          throw new Deferred(
              "Relevant ingredient " + fact.id() + " has unknown/partial actual support");
        eligible.add(fact);
        if (relationships.containsKey(fact.id()))
          kinds.put(fact.id(), relationships.get(fact.id()));
      }
    }
    if (eligible.isEmpty()) return List.of();
    var selected =
        new ConsequencePatternMatcher.Snapshot(
            snapshot.contextId(),
            snapshot.observerId(),
            snapshot.epoch(),
            eligible,
            snapshot.contexts(),
            true,
            snapshot.reads(),
            snapshot.catalogue());
    if (eligible.stream().allMatch(ConsequencePatternMatcher.Fact::dependent))
      return List.of(selected);
    if (kinds.isEmpty())
      throw new Deferred(
          "Root configuration requires authored structuring relationship conditions, not bare substantial co-occurrence");
    return ConfigurationComponents.partition(selected, kinds);
  }

  private ConsequencePatternMatcher.Semantics semantics(ServiceContextScope scope) {
    var reasoner = scope.getService(Reasoner.class);
    return new ConsequencePatternMatcher.Semantics() {
      public ConsequenceRuleResponse.Epoch epoch() {
        return DefaultConfigurationBackend.epoch(scope);
      }

      public ConsequencePatternMatcher.Decision matches(String actual, KimConcept expected) {
        var a = reasoner.resolveConcept(actual);
        var e = reasoner.resolveConcept(expected.getUrn());
        return a == null || e == null
            ? ConsequencePatternMatcher.Decision.UNAVAILABLE
            : reasoner.is(a, e)
                ? ConsequencePatternMatcher.Decision.YES
                : ConsequencePatternMatcher.Decision.NO;
      }

      public ConsequencePatternMatcher.Decision applicable(
          ConsequenceRuleResponse.Entry recipe,
          ConsequencePatternMatcher.Placement placement,
          ConsequencePatternMatcher.Fact context) {
        Concept bearer = context == null ? null : reasoner.resolveConcept(context.semantics());
        if (context != null && bearer == null)
          return ConsequencePatternMatcher.Decision.UNAVAILABLE;
        return reasoner.consequenceApplicable(recipe.owner(), bearer)
            ? ConsequencePatternMatcher.Decision.YES
            : ConsequencePatternMatcher.Decision.NO;
      }
    };
  }

  private static ConsequencePatternMatcher.Support support() {
    return (expression, clauses, placement) -> {
      var facts = clauses.values().stream().flatMap(Collection::stream).toList();
      if (facts.stream()
          .anyMatch(f -> f.resolvedSupport() == null || f.resolvedSupport().isBlank())) return null;
      var fragments = facts.stream().map(f -> Geometry.create(f.resolvedSupport())).toList();
      var geometry = RuntimeService.exactConfigurationSupport(fragments);
      return geometry == null ? null : geometry.encode();
    };
  }

  private SemanticDependencyRequest request(
      ConsequenceRuleResponse response, ServiceContextScope scope, Collection<String> inputTerms) {
    var terms = new TreeSet<String>(inputTerms);
    var reasoner = scope.getService(Reasoner.class);
    for (var recipe : response.rules()) {
      terms.add(recipe.owner().getUrn());
      for (var syntax : Arrays.asList(recipe.rule().target(), recipe.rule().within()))
        if (syntax != null) {
          var meaning = reasoner.resolveConcept(syntax.getUrn());
          if (meaning == null) throw new Deferred("Persisted recipe meaning unavailable");
          terms.add(meaning.getUrn());
        }
      for (var leaf : ConsequencePatterns.positiveLeaves(recipe.rule().target())) {
        var meaning = reasoner.resolveConcept(leaf.getUrn());
        if (meaning == null) throw new Deferred("Persisted ingredient meaning unavailable");
        terms.add(meaning.getUrn());
      }
    }
    var epoch = epoch(scope);
    return new SemanticDependencyRequest(
        epoch.serviceId(),
        epoch.knowledgeRevision(),
        epoch.semanticFingerprint(),
        List.copyOf(terms));
  }

  private InputProof inputs(
      ConsequenceRuleResponse response,
      Set<Long> references,
      Set<Long> meaningOnly,
      ConfigurationObserverFilter.Result observerView,
      List<KnowledgeGraph.ConsequenceReadSet> original,
      ServiceContextScope scope) {
    var consumed = new TreeSet<Long>(references);
    consumed.addAll(meaningOnly);
    if (consumed.isEmpty())
      throw new Deferred("Configuration input event lacks scientific source evidence");
    try {
      var refreshed =
          scope.getService(Reasoner.class).semanticDependencies(observerView.request(), scope);
      ConfigurationObserverFilter.requireWitness(observerView.request(), refreshed);
      if (!SemanticDependencyWitness.revalidates(observerView.witness(), refreshed))
        throw new Deferred("Observer comparison authority changed before input capture");
      var nativeInputs =
          ConsequenceNativeInputs.capture(consumed.iterator().next(), consumed, scope);
      for (var earlier : original)
        for (var asset : earlier.assets())
          for (var current : nativeInputs.reads().assets())
            if (asset.id() == current.id() && !asset.equals(current))
              throw new Deferred("Configuration input changed before scientific proof capture");
      var reasoner = scope.getService(Reasoner.class);
      var terms =
          new TreeSet<String>(
              ConsequenceInputEvidence.inherit(
                  nativeInputs, references, meaningOnly, reasoner, scope));
      terms.addAll(observerView.semanticTerms());
      for (var id : consumed)
        terms.add(nativeInputs.observation(id).getObservable().asConcept().getUrn());
      var request = request(response, scope, terms);
      var proof = reasoner.semanticDependencies(request, scope);
      requireProof(proof, request, scope);
      ConfigurationObserverFilter.requireUnion(refreshed, proof);
      return new InputProof(nativeInputs.reads(), request, proof);
    } catch (IllegalStateException unavailable) {
      throw new Deferred(
          "Scientific configuration input accounting unavailable: " + unavailable.getMessage());
    }
  }

  private static void requireProof(
      SemanticDependencyWitness proof,
      SemanticDependencyRequest request,
      ServiceContextScope scope) {
    var epoch = epoch(scope);
    if (!epoch.serviceId().equals(request.expectedServiceId())
        || epoch.knowledgeRevision() != request.expectedKnowledgeRevision()
        || !epoch.semanticFingerprint().equals(request.expectedBaseFingerprint()))
      throw new Deferred("Configuration admission request belongs to another semantic epoch");
    if (proof == null
        || !proof.completeAt(System.currentTimeMillis())
        || !request.inputHash().equals(proof.inputHash())
        || !epoch.serviceId().equals(proof.serviceId())
        || epoch.knowledgeRevision() != proof.knowledgeRevision()
        || !epoch.semanticFingerprint().equals(proof.baseFingerprint()))
      throw new Deferred("Configuration request semantic admission unavailable");
  }

  private void admit(Update update, ServiceContextScope scope) {
    admit(update.authorities(), scope);
  }

  private void admit(GuardedConfigurationPublisher.Birth birth, ServiceContextScope scope) {
    admit(birth.authorities(), scope);
  }

  private void admit(List<? extends AuthorityReceipt> authorities, ServiceContextScope requested) {
    var scope = execution(requested);
    var reasoner = scope.getService(Reasoner.class);
    for (var authority : authorities) {
      if (authority instanceof SourceReceipt source) {
        if (source.admissionRequest() == null || source.admissionWitness() == null)
          throw new Deferred("Configuration input semantic admission receipt is unavailable");
        var inputProof = reasoner.semanticDependencies(source.admissionRequest(), scope);
        requireProof(inputProof, source.admissionRequest(), scope);
        if (!SemanticDependencyWitness.revalidates(source.admissionWitness(), inputProof))
          throw new Deferred("Configuration input semantic authority changed before publication");
        if (source.sourceResponseJson() == null)
          throw new Deferred("Complete source response missing from configuration receipt");
        var query = Utils.Json.parseObject(source.queryJson(), ConsequenceRuleQuery.class);
        var current = query(query, scope).response();
        var expected =
            Utils.Json.parseObject(source.sourceResponseJson(), ConsequenceRuleResponse.class);
        if (!Utils.Json.asString(current.rules()).equals(Utils.Json.asString(expected.rules()))
            || !SemanticDependencyWitness.revalidates(source.witness(), current.witness()))
          throw new Deferred("Configuration source authority changed before publication");
      } else if (authority instanceof AffectsReceipt affects) {
        var original = Utils.Json.parseObject(affects.modelPlanJson(), RebuildAuthority.class);
        if (original.configurationId() != affects.configurationId())
          throw new SecurityException("Configuration dependency authority target differs");
        var asset =
            affects.dependencyReads().assets().stream()
                .filter(a -> a.id() == affects.configurationId())
                .findFirst()
                .orElseThrow();
        var nativeConfig =
            scope
                .getDigitalTwin()
                .getKnowledgeGraph()
                .materializeConsequenceObservation(asset, scope);
        var source = nativeConfig.getMetadata().get("klab.configuration.authorities");
        var explanation =
            nativeConfig.getMetadata().get(RuntimeService.CONFIGURATION_EXPLANATION_METADATA);
        var reference =
            Utils.Json.parseObject(original.priorAuthorities(), PriorAuthorityReference.class);
        if (!(source instanceof String sourceJson)
            || !(explanation instanceof String modelJson)
            || !digest(sourceJson).equals(reference.contentHash())
            || !digest(modelJson).equals(original.priorExplanation()))
          throw new SecurityException(
              "Configuration dependency authority differs from captured native provenance");
        var fresh = reasoner.semanticDependencies(affects.request(), scope);
        requireProof(fresh, affects.request(), scope);
        if (!SemanticDependencyWitness.revalidates(affects.witness(), fresh))
          throw new Deferred("Configuration semantic provider changed before publication");
      }
    }
  }
}
