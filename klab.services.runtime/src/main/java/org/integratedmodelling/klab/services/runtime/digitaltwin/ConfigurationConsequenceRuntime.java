package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Incremental configuration planning, deliberately separate from graph publication and model
 * execution. Only an explicitly supplied durable configuration identity can be updated here.
 * Grouping/new-identity policy is not inferred from a bag of matching ingredients.
 *
 * <p>The backend must be server-owned. It captures local domains (not a global context scan),
 * decodes facts from native snapshots with the retained semantic scope, and supplies complete
 * applicability and actual support. It must validate every original read set before native
 * publication, append a new revision without rewriting history, then run the ordinary explanatory
 * resolution/affects path. No COMPLETE candidate response or caller-built ID list is commit
 * authority.
 */
public final class ConfigurationConsequenceRuntime implements ConsequenceEngine.Handler {
  public enum Status {
    COMPLETE,
    UNAVAILABLE
  }

  public record Changes(Status status, List<Concept> meanings, String reason) {
    public Changes {
      Objects.requireNonNull(status);
      meanings = List.copyOf(meanings);
      Objects.requireNonNull(reason);
      if (status != Status.COMPLETE && !meanings.isEmpty())
        throw new IllegalArgumentException("Partial triggers are not complete discovery");
    }
  }

  /**
   * Append-only participant revision. The observation identity is independent of participant IDs.
   */
  public record Revision(
      long observationId,
      long number,
      long triggeringCommitId,
      List<Long> participants,
      String support) {
    public Revision {
      if (observationId <= 0 || number < 0 || triggeringCommitId <= 0)
        throw new IllegalArgumentException("Durable revision identity required");
      if (participants.stream().anyMatch(id -> id == null || id <= 0))
        throw new IllegalArgumentException("Durable participants required");
      participants = participants.stream().distinct().sorted().toList();
      Objects.requireNonNull(support);
    }
  }

  public record Domain(
      ConsequencePatternMatcher.Snapshot snapshot,
      ConsequencePatternMatcher.Semantics semantics,
      ConsequencePatternMatcher.Support support) {
    public Domain {
      Objects.requireNonNull(snapshot);
      Objects.requireNonNull(semantics);
      Objects.requireNonNull(support);
    }
  }

  /**
   * Existing identity selected by authoritative placement/grouping evidence, never guessed here.
   */
  public record Existing(Revision previous, KnowledgeGraph.ConsequenceReadSet identityReads) {
    public Existing {
      Objects.requireNonNull(previous);
      Objects.requireNonNull(identityReads);
      if (!identityReads.assetIds().contains(previous.observationId()))
        throw new IllegalArgumentException("Configuration identity is not captured");
    }
  }

  public sealed interface AuthorityReceipt permits SourceReceipt, AffectsReceipt {
    SemanticDependencyWitness witness();
  }

  /** Non-executable audit binding, not a native read set or a recipe for reconstructing one. */
  public record ReadSetReference(
      int version,
      String contextId,
      List<Long> assetIds,
      List<KnowledgeGraph.ConsequenceEdgeSelection> selections,
      List<String> edgeIds,
      String sha256) {
    public ReadSetReference {
      if (version != 1
          || contextId == null
          || contextId.isBlank()
          || sha256 == null
          || !sha256.matches("[a-f0-9]{64}"))
        throw new IllegalArgumentException(
            "Versioned exact native snapshot audit reference required");
      assetIds = List.copyOf(assetIds);
      selections = List.copyOf(selections);
      edgeIds = List.copyOf(edgeIds);
    }
  }

  private static ReadSetReference auditReference(KnowledgeGraph.ConsequenceReadSet reads) {
    if (reads == null) return null;
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      // Hash the FULL immutable native capture, including opaque metadata and old history.
      // Stream to avoid allocating another potentially large JSON String. No audit projection
      // applies inside a native read set: its properties are detached primitive/string data.
      try (var output =
          new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest)) {
        org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
            .writeValue(output, reads);
      }
      return new ReadSetReference(
          1,
          reads.contextId(),
          reads.assetIds(),
          reads.selections(),
          reads.edges().stream().map(KnowledgeGraph.ConsequenceEdgeSnapshot::elementId).toList(),
          java.util.HexFormat.of().formatHex(digest.digest()));
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Cannot bind native snapshot audit reference", failure);
    }
  }

  /** JSON freezes the exact original query/recipe, including all scoped source clauses. */
  public record SourceReceipt(
      ConsequenceRuleResponse.Epoch epoch,
      SemanticDependencyWitness witness,
      String queryJson,
      String recipeJson,
      @com.fasterxml.jackson.annotation.JsonIgnoreProperties("reads")
          ConsequencePatternMatcher.Match match,
      String sourceResponseJson,
      SemanticDependencyRequest admissionRequest,
      SemanticDependencyWitness admissionWitness)
      implements AuthorityReceipt {
    public SourceReceipt(
        ConsequenceRuleResponse.Epoch epoch,
        SemanticDependencyWitness witness,
        String queryJson,
        String recipeJson,
        ConsequencePatternMatcher.Match match) {
      this(epoch, witness, queryJson, recipeJson, match, null, null, null);
    }

    public SourceReceipt(
        ConsequenceRuleResponse.Epoch epoch,
        SemanticDependencyWitness witness,
        String queryJson,
        String recipeJson,
        ConsequencePatternMatcher.Match match,
        String sourceResponseJson) {
      this(epoch, witness, queryJson, recipeJson, match, sourceResponseJson, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("matchReadSetReference")
    public ReadSetReference matchReadSetReference() {
      return auditReference(match.reads());
    }

    public SourceReceipt withInputs(
        SemanticDependencyRequest request, SemanticDependencyWitness proof) {
      Objects.requireNonNull(request);
      Objects.requireNonNull(proof);
      if (!request.inputHash().equals(proof.inputHash()))
        throw new IllegalArgumentException(
            "Actual configuration input proof covers different obligations");
      return new SourceReceipt(
          epoch, witness, queryJson, recipeJson, match, sourceResponseJson, request, proof);
    }

    public SourceReceipt {
      Objects.requireNonNull(epoch);
      Objects.requireNonNull(witness);
      Objects.requireNonNull(queryJson);
      Objects.requireNonNull(recipeJson);
      Objects.requireNonNull(match);
    }

    static SourceReceipt capture(
        ConsequenceRuleQuery query,
        ConsequenceRuleResponse response,
        ConsequenceRuleResponse.Entry recipe,
        ConsequencePatternMatcher.Match match) {
      try {
        var json =
            org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
        return new SourceReceipt(
            response.epoch(),
            response.witness(),
            json.writeValueAsString(query),
            json.writeValueAsString(recipe),
            match,
            json.writeValueAsString(response));
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("Cannot freeze configuration source authority", failure);
      }
    }
  }

  /**
   * Prior modeled dependency authority is not a fabricated fresh source query. It must be
   * revalidated operationally, while a new worldview definition never rewrites the old recipe.
   */
  public record AffectsReceipt(
      long configurationId,
      String modelPlanJson,
      SemanticDependencyRequest request,
      SemanticDependencyWitness witness,
      @com.fasterxml.jackson.annotation.JsonIgnore
          KnowledgeGraph.ConsequenceReadSet dependencyReads)
      implements AuthorityReceipt {
    @com.fasterxml.jackson.annotation.JsonProperty("dependencyReadSetReference")
    public ReadSetReference dependencyReadSetReference() {
      return auditReference(dependencyReads);
    }

    public AffectsReceipt {
      if (configurationId <= 0)
        throw new IllegalArgumentException("Durable affected configuration required");
      Objects.requireNonNull(modelPlanJson);
      Objects.requireNonNull(request);
      Objects.requireNonNull(witness);
      Objects.requireNonNull(dependencyReads);
      if (!request.inputHash().equals(witness.inputHash()))
        throw new IllegalArgumentException("Modeled dependency proof covers different inputs");
    }
  }

  /**
   * Serializable match projection. Native readsets stay in the opaque transaction proof, not
   * JSON-decoded assets.
   */
  public record RecipeMatch(
      String ruleId,
      String contextId,
      long observerId,
      ConsequencePatternMatcher.Placement placement,
      List<Long> ingredientIds,
      String support) {
    public RecipeMatch {
      ingredientIds = List.copyOf(ingredientIds);
    }

    public static RecipeMatch of(ConsequencePatternMatcher.Match match) {
      return new RecipeMatch(
          match.ruleId(),
          match.contextId(),
          match.observerId(),
          match.placement(),
          match.ingredientIds(),
          match.support());
    }
  }

  /** Persisted scientific/source authority carried by a subsequent modeled input event. */
  public record RebuildAuthority(
      long configurationId,
      String priorAuthorities,
      String priorExplanation,
      String familyJson,
      List<RecipeMatch> matches) {
    public RebuildAuthority {
      matches = List.copyOf(matches);
      Objects.requireNonNull(priorAuthorities);
      Objects.requireNonNull(priorExplanation);
      Objects.requireNonNull(familyJson);
    }
  }

  /**
   * A changed source reached this configuration through its existing modeled affects dependencies.
   */
  public record Affected(
      Existing existing,
      List<Long> changedDependencies,
      List<Long> currentParticipants,
      String currentSupport,
      KnowledgeGraph.ConsequenceReadSet currentReads,
      AffectsReceipt authority) {
    public Affected {
      Objects.requireNonNull(existing);
      changedDependencies = List.copyOf(changedDependencies);
      currentParticipants = List.copyOf(currentParticipants);
      Objects.requireNonNull(currentSupport);
      Objects.requireNonNull(currentReads);
      Objects.requireNonNull(authority);
      if (authority.configurationId() != existing.previous().observationId())
        throw new IllegalArgumentException("Modeled dependency belongs to another configuration");
      if (changedDependencies.isEmpty())
        throw new IllegalArgumentException(
            "Affects recomputation requires an actual changed dependency");
    }
  }

  public record Update(
      Revision previous,
      Revision next,
      List<KnowledgeGraph.ConsequenceReadSet> reads,
      boolean recompute,
      List<AuthorityReceipt> authorities,
      List<KnowledgeGraph.ContextCohortCatalogue> catalogues) {
    public Update(
        Revision previous,
        Revision next,
        List<KnowledgeGraph.ConsequenceReadSet> reads,
        boolean recompute,
        List<AuthorityReceipt> authorities) {
      this(previous, next, reads, recompute, authorities, List.of());
    }

    public Update {
      Objects.requireNonNull(previous);
      Objects.requireNonNull(next);
      reads = List.copyOf(reads);
      authorities = List.copyOf(authorities);
      catalogues = List.copyOf(catalogues);
      if (authorities.isEmpty())
        throw new IllegalArgumentException(
            "Configuration publication requires exact semantic authority receipts");
      if (previous.observationId() != next.observationId()
          || next.number() != previous.number() + 1)
        throw new IllegalArgumentException(
            "Participant updates retain identity and append a revision");
      var context = reads.isEmpty() ? null : reads.getFirst().contextId();
      if (context == null || reads.stream().anyMatch(r -> !context.equals(r.contextId())))
        throw new IllegalArgumentException("One native context required");
      if (reads.stream().noneMatch(r -> r.assetIds().contains(previous.observationId())))
        throw new IllegalArgumentException("Configuration baseline is absent");
      if (reads.stream()
              .flatMap(r -> r.assetIds().stream())
              .collect(java.util.stream.Collectors.toSet())
              .containsAll(next.participants())
          == false)
        throw new IllegalArgumentException("New participants must retain captured native evidence");
    }
  }

  public record Effect(Consequence.Outcome outcome, List<Long> observations, String reason) {
    public Effect {
      Objects.requireNonNull(outcome);
      observations = List.copyOf(observations);
      Objects.requireNonNull(reason);
    }
  }

  public interface Backend {
    /**
     * Includes before/after meanings of changed/lost facts. Missing event evidence is UNAVAILABLE.
     */
    Changes changes(Set<Long> changed, KnowledgeGraph.Commit commit, ServiceContextScope scope)
        throws Exception;

    /**
     * Follow only existing affects dependencies of these changed IDs, including cessation events.
     */
    List<Affected> affected(
        Set<Long> changed, KnowledgeGraph.Commit commit, ServiceContextScope scope)
        throws Exception;

    List<Domain> domains(
        ConsequenceRuleResponse.Entry recipe, Set<Long> changed, ServiceContextScope scope)
        throws Exception;

    /** Empty means new/ambiguous identity, which remains deferred until its policy is available. */
    Optional<Existing> existing(
        ConsequenceRuleResponse.Entry recipe,
        ConsequencePatternMatcher.Match match,
        ServiceContextScope scope)
        throws Exception;

    /**
     * Admission/security/operational checks and all native guards precede a single atomic update.
     * Recompute through ordinary explanatory resolution and affects even after participant
     * cessation; no generic configuration deletion/deactivation and no edits to previous revisions
     * are allowed.
     */
    void admit(Update update, ServiceContextScope scope) throws Exception;

    Effect publish(Update update, Consequence.Cause cause, ServiceContextScope scope)
        throws Exception;
  }

  private final Backend backend;
  private final java.util.function.LongSupplier clock;

  public ConfigurationConsequenceRuntime(Backend backend) {
    this(backend, System::currentTimeMillis);
  }

  ConfigurationConsequenceRuntime(Backend backend, java.util.function.LongSupplier clock) {
    this.backend = Objects.requireNonNull(backend);
    this.clock = Objects.requireNonNull(clock);
  }

  public static Set<Long> changedIds(KnowledgeGraph.Commit commit) {
    var ids = new TreeSet<Long>();
    ids.addAll(commit.getAddedObservations());
    ids.addAll(commit.getModifiedAssets());
    ids.addAll(commit.getDeletedAssets());
    for (var links : List.of(commit.getAddedLinks(), commit.getDeletedLinks()))
      for (var link : links) {
        ids.add(link.getFirst());
        ids.add(link.getSecond());
      }
    ids.removeIf(id -> id == null || id <= 0);
    return Collections.unmodifiableSet(ids);
  }

  @Override
  public ConsequenceEngine.Result evaluate(
      Consequence.Cause cause, KnowledgeGraph.Commit commit, ServiceContextScope scope)
      throws Exception {
    Objects.requireNonNull(cause);
    Objects.requireNonNull(commit);
    Objects.requireNonNull(scope);
    if (cause.kind() != Consequence.Kind.CONFIGURATION_DETECTION
        || cause.triggeringCommitId() != commit.getId())
      throw new IllegalArgumentException(
          "Configuration cause does not identify this committed event");
    var cycle = scope.getResolutionCycleBinding();
    if (cycle == null)
      return result(
          Consequence.Outcome.DEFERRED, "Retained semantic cycle is unavailable", List.of());
    if (!cycle.consequencesEnabled())
      return result(
          Consequence.Outcome.NO_ACTION, "Consequences are disabled for this cycle", List.of());
    if (cycle.semanticBinding() == null)
      return result(
          Consequence.Outcome.DEFERRED, "Retained semantic cycle is unavailable", List.of());
    if (!cycle.contextId().equals(cause.contextId()))
      throw new IllegalArgumentException("Cycle/context mismatch");
    var changed = changedIds(commit);
    if (changed.isEmpty())
      return result(
          Consequence.Outcome.NO_ACTION, "No modeled observation or dependency changed", List.of());
    cancelled(scope);
    var binding = cycle.semanticBinding();
    var epoch =
        new ConsequenceRuleResponse.Epoch(
            binding.reasonerServiceId(),
            binding.knowledgeRevision(),
            binding.semanticFingerprint(),
            binding.reasonerSessionId());
    // Scope.getService must return the retained facade. Never obtain a current coordinator here.
    var reasoner = scope.getService(Reasoner.class);
    var changes = backend.changes(changed, commit, scope);
    var affected = backend.affected(changed, commit, scope);
    var plans = new LinkedHashMap<Long, Update>();
    boolean deferred = changes.status() != Status.COMPLETE;
    // Existing configurations react to actual modeled events even if later source definitions
    // differ.
    // Source-only drift has no changed IDs/affects event and therefore cannot rewrite twin history.
    for (var event : affected) {
      cancelled(scope);
      if (!changed.containsAll(event.changedDependencies()))
        throw new IllegalArgumentException("Unchanged dependency cannot trigger recomputation");
      var old = event.existing().previous();
      var next =
          new Revision(
              old.observationId(),
              old.number() + 1,
              commit.getId(),
              event.currentParticipants(),
              event.currentSupport());
      var update =
          new Update(
              old,
              next,
              List.of(
                  event.existing().identityReads(),
                  event.currentReads(),
                  event.authority().dependencyReads()),
              true,
              List.of(event.authority()));
      requireContext(update, cause.contextId());
      put(plans, update);
    }
    if (changes.status() == Status.COMPLETE && !changes.meanings().isEmpty()) {
      var query =
          new ConsequenceRuleQuery(
              List.of(), changes.meanings(), Set.of(KimConsequenceRule.Kind.EMERGES_FROM), epoch);
      var response = reasoner.consequenceRules(query);
      if (response == null
          || response.status() != ConsequenceRuleResponse.Status.COMPLETE
          || !epoch.equals(response.epoch())
          || response.witness() == null
          || !response.witness().completeAt(clock.getAsLong())
          || !epoch.serviceId().equals(response.witness().serviceId())
          || epoch.knowledgeRevision() != response.witness().knowledgeRevision()
          || !epoch.semanticFingerprint().equals(response.witness().baseFingerprint()))
        deferred = true;
      else
        for (var recipe : response.rules()) {
          final List<Domain> domains;
          try {
            domains = backend.domains(recipe, changed, scope);
          } catch (ConfigurationComponents.UnsupportedGrouping unsupported) {
            deferred = true;
            continue;
          }
          for (var domain : domains) {
            cancelled(scope);
            if (!cause.contextId().equals(domain.snapshot().contextId())
                || cause.observerId() != domain.snapshot().observerId()
                || !epoch.equals(domain.snapshot().epoch()))
              throw new IllegalArgumentException(
                  "Detection domain belongs to another cycle or perspective");
            var detected =
                new ConsequencePatternMatcher()
                    .evaluate(recipe, domain.snapshot(), domain.semantics(), domain.support());
            if (detected.outcome() == ConsequencePatternMatcher.Outcome.DEFERRED) {
              deferred = true;
              continue;
            }
            for (var match : detected.matches()) {
              var existing = backend.existing(recipe, match, scope);
              if (existing.isEmpty()) {
                deferred = true;
                continue;
              }
              var prior = existing.get().previous();
              if (prior.participants().equals(match.ingredientIds())
                  && prior.support().equals(match.support())) continue;
              var next =
                  new Revision(
                      prior.observationId(),
                      prior.number() + 1,
                      commit.getId(),
                      match.ingredientIds(),
                      match.support());
              var update =
                  new Update(
                      prior,
                      next,
                      List.of(existing.get().identityReads(), match.reads()),
                      true,
                      List.of(SourceReceipt.capture(query, response, recipe, match)));
              requireContext(update, cause.contextId());
              put(plans, update);
            }
          }
        }
    }
    var observations = new ArrayList<Long>();
    boolean failed = false;
    int applied = 0;
    for (var plan : plans.values()) {
      cancelled(scope);
      validateReceipts(plan, epoch);
      backend.admit(plan, scope);
      cancelled(scope);
      validateReceipts(plan, epoch);
      var effect = backend.publish(plan, cause, scope);
      observations.addAll(effect.observations());
      if (effect.outcome() == Consequence.Outcome.FAILED) failed = true;
      else if (effect.outcome() == Consequence.Outcome.DEFERRED) deferred = true;
      else if (effect.outcome() == Consequence.Outcome.APPLIED) applied++;
    }
    return result(
        failed
            ? Consequence.Outcome.FAILED
            : deferred
                ? Consequence.Outcome.DEFERRED
                : applied > 0 ? Consequence.Outcome.APPLIED : Consequence.Outcome.NO_ACTION,
        "Incremental configurations: "
            + applied
            + " existing identities updated; new/ambiguous identities remain deferred",
        observations.stream().distinct().toList());
  }

  private void validateReceipts(Update update, ConsequenceRuleResponse.Epoch epoch) {
    for (var receipt : update.authorities()) {
      if (!receipt.witness().completeAt(clock.getAsLong()))
        throw new IllegalStateException(
            "Configuration authority is unavailable or expired before publication");
      if (receipt instanceof SourceReceipt source
          && (!epoch.equals(source.epoch())
              || !epoch.equals(source.match().epoch())
              || !epoch.serviceId().equals(source.witness().serviceId())
              || epoch.knowledgeRevision() != source.witness().knowledgeRevision()
              || !epoch.semanticFingerprint().equals(source.witness().baseFingerprint())))
        throw new IllegalStateException("Configuration source authority changed cycle");
      if (receipt instanceof AffectsReceipt affects
          && affects.configurationId() != update.previous().observationId())
        throw new IllegalStateException(
            "Modeled dependency authority covers a different configuration");
    }
  }

  private static void requireContext(Update update, String context) {
    if (update.reads().stream().anyMatch(read -> !context.equals(read.contextId())))
      throw new IllegalArgumentException("Native capture belongs to another context");
  }

  private static void put(Map<Long, Update> plans, Update update) {
    var old = plans.putIfAbsent(update.previous().observationId(), update);
    if (old != null) {
      if (!old.previous().equals(update.previous()) || !old.next().equals(update.next()))
        throw new IllegalStateException("Conflicting configuration grouping/update evidence");
      var reads = new ArrayList<>(old.reads());
      reads.addAll(update.reads());
      plans.put(
          update.previous().observationId(),
          new Update(
              old.previous(),
              old.next(),
              reads.stream().distinct().toList(),
              old.recompute() || update.recompute(),
              java.util.stream.Stream.concat(
                      old.authorities().stream(), update.authorities().stream())
                  .distinct()
                  .toList()));
    }
  }

  private static void cancelled(ServiceContextScope scope) throws InterruptedException {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new InterruptedException("Configuration consequence cancelled");
  }

  private static ConsequenceEngine.Result result(
      Consequence.Outcome outcome, String reason, List<Long> ids) {
    return new ConsequenceEngine.Result(outcome, reason, ids);
  }
}
