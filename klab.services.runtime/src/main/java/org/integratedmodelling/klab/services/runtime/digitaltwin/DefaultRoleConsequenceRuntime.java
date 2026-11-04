package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.runtime.ConsequenceDiscoverySnapshot;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.scopes.ConsequenceSubmissionAdmission;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** Production rule execution against an authenticated retained cycle, never current semantics. */
public final class DefaultRoleConsequenceRuntime implements RoleConsequenceRuntime {
  private record Cycle(
      ResolutionCycleBinding binding, Reasoner reasoner, ServiceContextScope scope) {
    public Cycle {
      Objects.requireNonNull(binding);
      Objects.requireNonNull(reasoner);
    }

    ConsequenceRuleResponse.Epoch epoch() {
      var semantic = binding.semanticBinding();
      if (semantic == null)
        throw new IllegalStateException("Cycle has no retained semantic authority");
      return new ConsequenceRuleResponse.Epoch(
          semantic.reasonerServiceId(),
          semantic.knowledgeRevision(),
          semantic.semanticFingerprint(),
          semantic.reasonerSessionId());
    }
  }

  /** Runtime supplies its registry-validated scope binding and bound Reasoner, not a wire claim. */
  @FunctionalInterface
  public interface Cycles {
    ServiceContextScope require(ServiceContextScope scope);
  }

  @FunctionalInterface
  public interface Configurations {
    Effect implications(
        Consequence.Cause cause, KnowledgeGraph.Commit commit, ServiceContextScope scope)
        throws Exception;
  }

  private record Perspective(long focus, Long observer) {}

  private record Discovered(
      Object issuer,
      long sourceId,
      Perspective perspective,
      Cycle cycle,
      ConsequenceNativeInputs.Snapshot inputs,
      ConsequenceRuleResponse candidates,
      ConsequenceRuleResponse.Entry entry,
      String sourceIdentity,
      String roleUrn,
      Concept role)
      implements Evidence {}

  private record SelectionProof(
      Object issuer,
      long sourceId,
      String ruleIdentity,
      String expression,
      String resolvedExpression,
      ResolutionCycleBinding cycle,
      Perspective perspective,
      ConsequenceDiscoverySnapshot.Complete complete,
      long requestCommit)
      implements ConsequenceRecipients.Evidence {}

  private final Cycles cycles;
  private final RuntimeService localRuntime;
  private final Configurations configurations;
  private final Object issuer = new Object();

  public DefaultRoleConsequenceRuntime(
      RuntimeService localRuntime, Cycles cycles, Configurations configurations) {
    this.localRuntime = Objects.requireNonNull(localRuntime);
    this.cycles = Objects.requireNonNull(cycles);
    this.configurations = configurations;
  }

  private Cycle cycle(ServiceContextScope scope) {
    var execution = Objects.requireNonNull(cycles.require(scope));
    var cycle =
        new Cycle(
            Objects.requireNonNull(execution.getResolutionCycleBinding()),
            execution.getService(Reasoner.class),
            execution);
    if (!cycle.binding().contextId().equals(scope.getId())
        || !cycle.binding().consequencesEnabled()
        || !cycle.binding().runtimeServiceId().equals(localRuntime.serviceId()))
      throw new IllegalStateException(
          "Consequence cycle is disabled or belongs to another context");
    cycle.epoch();
    return cycle;
  }

  private static Perspective perspective(ServiceContextScope scope) {
    var observerId = scope.getObserverId();
    if (observerId != null) {
      var observer = scope.getObserver();
      if (observer == null
          || observer.getId() != observerId
          || observer.getObservable() == null
          || !observer.getObservable().is(SemanticType.AGENT))
        throw new IllegalStateException("Selected consequence observer is no longer accessible");
    }
    var focus = scope.getContextObservation();
    return new Perspective(focus == null ? 0 : focus.getId(), observerId);
  }

  private static void active(ServiceContextScope scope) throws InterruptedException {
    RoleConferralHandler.cancelled(scope);
  }

  private static Effect effect(
      Consequence.Outcome outcome, String reason, Resolution resolution, Long... ids) {
    return new Effect(outcome, List.of(ids), reason, resolution);
  }

  private void revalidateSelection(SelectionProof selection, ServiceContextScope scope) {
    var cycle = cycle(scope);
    var complete = selection.complete();
    if (selection.issuer() != issuer
        || !selection.cycle().equals(cycle.binding())
        || !selection.cycle().equals(complete.cycle())
        || !selection.resolvedExpression().equals(complete.expression())
        || !Objects.equals(selection.perspective().observer(), complete.observerId())
        || selection.perspective().focus() != complete.focusId())
      throw new IllegalStateException(
          "Complete recipient selection belongs to different semantics or perspective");
    complete.revalidateForRequest(cycle.scope());
    var evidence = complete.semanticEvidence();
    if (evidence == null || !evidence.complete())
      throw new IllegalStateException("Recipient discovery semantic accounting is incomplete");
    var epoch = cycle.epoch();
    var request =
        new SemanticDependencyRequest(
            epoch.serviceId(),
            epoch.knowledgeRevision(),
            epoch.semanticFingerprint(),
            evidence.obligations());
    var current = cycle.reasoner().semanticDependencies(request, cycle.scope());
    witness(current, cycle);
    if (!request.inputHash().equals(current.inputHash())
        || !SemanticDependencyWitness.revalidates(evidence.witness(), current))
      throw new IllegalStateException(
          "Recipient discovery semantic certification no longer revalidates");
  }

  private static void validateNativeSelection(SelectionProof selection, ServiceContextScope scope) {
    try (var transaction = scope.getDigitalTwin().getKnowledgeGraph().createTransaction(scope)) {
      transaction.lockConsequenceInputs(selection.complete().inputs().assetIds());
      transaction.validateConsequenceInputs(selection.complete().inputs());
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Recipient selection could not close safely", failure);
    }
  }

  private static Concept meaning(Observation observation) {
    return observation.getObservable().asConcept();
  }

  private static String urn(KimConcept syntax) {
    return syntax == null ? null : syntax.getUrn();
  }

  private static Concept roleSnapshot(Concept role) {
    var result = new org.integratedmodelling.common.knowledge.ConceptImpl();
    result.setUrn(role.getUrn());
    result.setType(Set.copyOf(role.getType()));
    result.setName(role.getName());
    result.setNamespace(role.getNamespace());
    result.setReferenceName(role.getReferenceName());
    result.setAbstract(role.isAbstract());
    return result;
  }

  private static KimConcept syntaxSnapshot(KimConcept value) {
    if (value == null) return null;
    var result = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    result.setUrn(value.getUrn());
    return result;
  }

  private static String identity(ConsequenceRuleResponse.Entry entry) {
    var rule = entry.rule();
    return Utils.Json.asString(
        Arrays.asList(
            entry.ruleId(),
            entry.declarationUrn(),
            entry.owner().getUrn(),
            entry.sourceOntology(),
            entry.sourceVersion(),
            entry.sourceHash(),
            rule.kind(),
            urn(rule.target()),
            rule.recipient().kind(),
            urn(rule.recipient().expression()),
            urn(rule.secondaryInherency()),
            urn(rule.within())));
  }

  private static void witness(SemanticDependencyWitness witness, Cycle cycle) {
    if (witness == null
        || !witness.completeAt(System.currentTimeMillis())
        || !Objects.equals(witness.serviceId(), cycle.epoch().serviceId())
        || witness.knowledgeRevision() != cycle.epoch().knowledgeRevision()
        || !Objects.equals(witness.baseFingerprint(), cycle.epoch().semanticFingerprint()))
      throw new IllegalStateException(
          "Retained consequence semantic witness is unavailable or stale");
  }

  private static ConsequenceRuleResponse query(
      Cycle cycle, Concept owner, KimConsequenceRule.Kind kind) {
    var result =
        cycle
            .reasoner()
            .consequenceRules(
                new ConsequenceRuleQuery(List.of(owner), List.of(), Set.of(kind), cycle.epoch()));
    if (result == null
        || result.status() != ConsequenceRuleResponse.Status.COMPLETE
        || !cycle.epoch().equals(result.epoch()))
      throw new IllegalStateException("Retained consequence source enumeration is incomplete");
    witness(result.witness(), cycle);
    return result;
  }

  private static ConsequenceNativeInputs.Snapshot captureSource(
      long source, Collection<Long> recipients, ServiceContextScope scope) {
    var inputs = ConsequenceNativeInputs.capture(source, recipients, scope);
    return inputs.observation(source).getObservable().is(SemanticType.CONFIGURATION)
        ? ConfigurationRoleInputs.capture(source, inputs, scope)
        : inputs;
  }

  @Override
  public Rules conferrals(Observation source, ServiceContextScope scope) throws Exception {
    active(scope);
    var cycle = cycle(scope);
    scope = cycle.scope();
    var inputs = captureSource(source.getId(), List.of(), scope);
    var nativeSource = inputs.observation(source.getId());
    if (nativeSource.isEmpty()
        || nativeSource.getObservable() == null
        || nativeSource.getObservable().getSemantics().isCollective())
      return new Rules(
          ConsequenceRecipients.Status.DEFERRED,
          List.of(),
          "Conferral requires an accessible individual born source");
    if (!nativeSource.getObservable().is(SemanticType.PROCESS)
        && !nativeSource.getObservable().is(SemanticType.RELATIONSHIP))
      return new Rules(
          ConsequenceRecipients.Status.COMPLETE,
          List.of(),
          "Source is not a conferring process or relationship");
    var candidates = query(cycle, meaning(nativeSource), KimConsequenceRule.Kind.CONFERS);
    var rules = new ArrayList<Rule>();
    for (var entry : candidates.rules()) {
      active(scope);
      if (entry.rule().kind() != KimConsequenceRule.Kind.CONFERS
          || !cycle.reasoner().is(meaning(nativeSource), entry.owner())) continue;
      var role = cycle.reasoner().resolveConcept(urn(entry.rule().target()));
      if (role == null || !role.is(SemanticType.ROLE) || role.is(SemanticType.NOTHING))
        throw new IllegalStateException("Authored conferral target is not an available role");
      var selector = ConsequenceRecipients.Selector.valueOf(entry.rule().recipient().kind().name());
      var value =
          new Discovered(
              issuer,
              source.getId(),
              perspective(scope),
              cycle,
              inputs,
              candidates,
              entry,
              identity(entry),
              role.getUrn(),
              roleSnapshot(role));
      rules.add(
          new Rule(
              entry.ruleId(),
              roleSnapshot(role),
              selector,
              syntaxSnapshot(entry.rule().recipient().expression()),
              value));
    }
    return new Rules(
        ConsequenceRecipients.Status.COMPLETE, rules, "Retained authored conferrals enumerated");
  }

  @Override
  public ConsequenceRecipients.Selection resolveEach(
      KimConcept recipient, ServiceContextScope scope) throws Exception {
    return new ConsequenceRecipients.Selection(
        ConsequenceRecipients.Status.DEFERRED,
        List.of(),
        "Recipient discovery requires source-bound rule admission");
  }

  private ConsequenceRecipients.Selection discover(
      KimConcept recipient,
      Cycle cycle,
      ConsequenceNativeInputs.Snapshot inputs,
      long sourceId,
      Concept trigger,
      ConsequenceRuleResponse candidates,
      ConsequenceRuleResponse.Entry entry,
      ServiceContextScope scope)
      throws Exception {
    active(scope);
    var observable = cycle.reasoner().resolveObservable("each " + urn(recipient));
    if (observable == null
        || !observable.getSemantics().isCollective()
        || !SemanticType.isEnumerableSubstantial(observable.getSemantics().getType()))
      return new ConsequenceRecipients.Selection(
          ConsequenceRecipients.Status.DEFERRED,
          List.of(),
          "Recipient expression is not a collective substantial");
    var admission =
        new RequestAdmission(
            cycle, inputs, sourceId, trigger, candidates, entry, observable, scope, null);
    var admitted = scope.withConsequenceSubmissionAdmission(admission);
    var result = submit(observable, admitted);
    if (result.effect().resolution() != Resolution.RESOLVED)
      return new ConsequenceRecipients.Selection(
          result.effect().resolution() == Resolution.FAILED
              ? ConsequenceRecipients.Status.FAILED
              : ConsequenceRecipients.Status.DEFERRED,
          List.of(),
          result.effect().reason());
    if (admission.bound && admission.committed <= 0)
      throw new IllegalStateException("Successful discovery has no observed root commit receipt");
    var freshCycle = cycle(admitted);
    var freshScope = freshCycle.scope();
    // Completion is a separate ordinary request after discovery's own committed effects.
    // Re-establish the same authored source under fresh native inputs, never normalize drift.
    var completionInputs = captureSource(sourceId, List.of(), freshScope);
    var completionSource = completionInputs.observation(sourceId);
    var completionTrigger = trigger.is(SemanticType.ROLE) ? trigger : meaning(completionSource);
    if (trigger.is(SemanticType.ROLE)
        ? !freshCycle.reasoner().hasRole(completionSource.getObservable(), trigger)
        : !freshCycle.reasoner().is(meaning(completionSource), entry.owner()))
      throw new IllegalStateException("Consequence source changed after recipient discovery");
    var completionCandidates = query(freshCycle, completionTrigger, entry.rule().kind());
    var authoredIdentity = identity(entry);
    var completionEntry =
        completionCandidates.rules().stream()
            .filter(candidate -> identity(candidate).equals(authoredIdentity))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authored consequence changed after recipient discovery"));
    if (completionTrigger.is(SemanticType.ROLE)
        && entry.rule().kind() == KimConsequenceRule.Kind.IMPLIES
        && !within(freshCycle, completionEntry.rule(), completionSource))
      throw new IllegalStateException(
          "Role implication context no longer applies after recipient discovery");
    var completionAdmission =
        new RequestAdmission(
            freshCycle,
            completionInputs,
            sourceId,
            completionTrigger,
            completionCandidates,
            completionEntry,
            observable,
            freshScope,
            null);
    var completionScope = freshScope.withConsequenceSubmissionAdmission(completionAdmission);
    // Normal authorized local query is the issuer boundary. Preserve an existing qualified
    // virtual receipt exactly; a durable unqualified producer is copied only to request ID zero.
    Observation query = result.observation();
    if (query.getId() > 0) {
      if (query.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY))
        throw new IllegalStateException(
            "A qualified discovery receipt did not retain its issued virtual identity");
      var request =
          (org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)
              Observation.forTransport(query);
      request.setId(Observation.QUERY_ID);
      query = request;
    } else if (query.getId() != Observation.QUERY_ID)
      throw new IllegalStateException(
          "Discovery did not return a durable producer or issued query view");
    var issued =
        ChangeProcessHandler.awaitSubmissionDrain(localRuntime.submit(query, completionScope));
    active(completionScope);
    if (issued == null || issued.isEmpty())
      throw new IllegalStateException("Authorized recipient completion query did not succeed");
    var checkedScope = cycle(completionScope).scope();
    var complete = ConsequenceDiscoverySnapshot.capture(localRuntime, issued, checkedScope);
    var proof =
        new SelectionProof(
            issuer,
            sourceId,
            identity(entry),
            urn(recipient),
            observable.getUrn(),
            cycle.binding(),
            perspective(checkedScope),
            complete,
            admission.committed);
    return new ConsequenceRecipients.Selection(
        ConsequenceRecipients.Status.COMPLETE,
        complete.members(),
        "Completed native recipient selection captured",
        proof);
  }

  @Override
  public ConsequenceRecipients.Selection resolveRecipients(
      Observation source, Rule rule, ServiceContextScope scope) throws Exception {
    var cycle = cycle(scope);
    scope = cycle.scope();
    var original = requireRule(source, rule, cycle, scope);
    var inputs = captureSource(source.getId(), List.of(), scope);
    var fresh = refresh(original, inputs, scope);
    return discover(
        rule.recipient(),
        cycle,
        inputs,
        source.getId(),
        meaning(inputs.observation(source.getId())),
        fresh.candidates(),
        fresh.entry(),
        scope);
  }

  private static KimConcept recipientExpression(KimConsequenceRule rule) {
    return rule.kind() == KimConsequenceRule.Kind.IMPLIES
        ? rule.secondaryInherency()
        : rule.recipient().expression();
  }

  private static ConsequenceRecipients.Selector selector(KimConsequenceRule rule) {
    return rule.kind() == KimConsequenceRule.Kind.IMPLIES
        ? (rule.secondaryInherency() == null
            ? ConsequenceRecipients.Selector.DEFAULT
            : ConsequenceRecipients.Selector.EXPRESSION)
        : ConsequenceRecipients.Selector.valueOf(rule.recipient().kind().name());
  }

  private Discovered requireRule(
      Observation source, Rule rule, Cycle cycle, ServiceContextScope scope) {
    if (!(rule.evidence() instanceof Discovered expected)
        || expected.issuer() != issuer
        || expected.sourceId() != source.getId()
        || !expected.entry().ruleId().equals(rule.id())
        || !expected.roleUrn().equals(rule.role().getUrn())
        || selector(expected.entry().rule()) != rule.selector()
        || !Objects.equals(urn(recipientExpression(expected.entry().rule())), urn(rule.recipient()))
        || !expected.perspective().equals(perspective(scope))
        || !expected.cycle().binding().equals(cycle.binding()))
      throw new IllegalStateException("Conferral has no retained source-rule evidence");
    return expected;
  }

  /** A previous ordinary discovery may legitimately update focus/perception in this same chain. */
  private Discovered refresh(
      Discovered previous, ConsequenceNativeInputs.Snapshot inputs, ServiceContextScope scope) {
    var cycle = cycle(scope);
    var source = inputs.observation(previous.sourceId());
    if (source.isEmpty()
        || source.getObservable() == null
        || source.getObservable().getSemantics().isCollective()
        || !source.getObservable().is(SemanticType.PROCESS)
            && !source.getObservable().is(SemanticType.RELATIONSHIP)
            && !(source.getObservable().is(SemanticType.CONFIGURATION)
                && previous.entry().rule().kind() == KimConsequenceRule.Kind.IMPLIES))
      throw new IllegalStateException("Conferring source is no longer a process or relationship");
    var response = query(cycle, meaning(source), previous.entry().rule().kind());
    var entry =
        response.rules().stream()
            .filter(value -> identity(value).equals(previous.sourceIdentity()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authored conferral changed within its retained cycle"));
    if (!cycle.reasoner().is(meaning(source), entry.owner()))
      throw new IllegalStateException(
          "Current native source no longer matches authored conferral owner");
    var role = cycle.reasoner().resolveConcept(urn(entry.rule().target()));
    if (role == null
        || !role.is(SemanticType.ROLE)
        || role.is(SemanticType.NOTHING)
        || !previous.roleUrn().equals(role.getUrn()))
      throw new IllegalStateException("Authored conferral target no longer revalidates");
    return new Discovered(
        issuer,
        previous.sourceId(),
        perspective(scope),
        cycle,
        inputs,
        response,
        entry,
        identity(entry),
        role.getUrn(),
        roleSnapshot(role));
  }

  private static Set<Long> edgeRecipients(
      ConsequenceNativeInputs.Snapshot snapshot,
      long source,
      GraphModel.Relationship relationship,
      boolean incoming) {
    var result = new TreeSet<Long>();
    for (var edge : snapshot.reads().edges())
      if (edge.relationship() == relationship) {
        var anchor = incoming ? edge.target() : edge.source();
        var other = incoming ? edge.source() : edge.target();
        if (anchor.id() instanceof Number id
            && id.longValue() == source
            && other.id() instanceof Number endpoint
            && snapshot.observations().containsKey(endpoint.longValue()))
          result.add(endpoint.longValue());
      }
    return result;
  }

  private static Set<Long> defaultRecipients(
      ConsequenceNativeInputs.Snapshot inputs,
      long source,
      ConsequenceRecipients.Selector selector) {
    var observation = inputs.observation(source);
    if (observation.getObservable().is(SemanticType.CONFIGURATION)) {
      if (selector != ConsequenceRecipients.Selector.DEFAULT)
        throw new IllegalStateException("Configuration role needs default bearer or explicit for");
      var result = ConfigurationRoleInputs.bearers(source, inputs);
      if (result.size() != 1)
        throw new IllegalStateException(
            "Substantial configuration requires explicit for; dependent bearer is missing or ambiguous");
      return result;
    }
    if (observation.getObservable().is(SemanticType.PROCESS)) {
      if (selector != ConsequenceRecipients.Selector.DEFAULT)
        throw new IllegalStateException("A process has no relationship endpoint selector");
      var result = edgeRecipients(inputs, source, GraphModel.Relationship.HAS_CHILD, true);
      if (result.size() != 1)
        throw new IllegalStateException("Process bearer is missing or ambiguous");
      return result;
    }
    if (observation.getObservable().is(SemanticType.BIDIRECTIONAL)) {
      if (selector != ConsequenceRecipients.Selector.DEFAULT)
        throw new IllegalStateException("Bond endpoint direction is ambiguous");
      var result =
          edgeRecipients(
              inputs, source, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT, false);
      if (result.size() != 2)
        throw new IllegalStateException("Bond participants are missing or ambiguous");
      return result;
    }
    var from =
        edgeRecipients(inputs, source, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, false);
    var to = edgeRecipients(inputs, source, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, false);
    if (from.size() != 1 || to.size() != 1)
      throw new IllegalStateException("Relationship endpoints are missing or ambiguous");
    if (selector == ConsequenceRecipients.Selector.SOURCE) return from;
    if (selector == ConsequenceRecipients.Selector.TARGET) return to;
    var both = new TreeSet<Long>(from);
    both.addAll(to);
    return both;
  }

  private static boolean within(Cycle cycle, KimConsequenceRule rule, Observation recipient) {
    if (rule.within() == null) return true;
    var context = cycle.reasoner().resolveConcept(urn(rule.within()));
    if (context == null
        || context.is(SemanticType.NOTHING)
        || !SemanticType.isEnumerableSubstantial(context.getType())
        || context.isCollective())
      throw new IllegalStateException(
          "Consequence within does not identify an available substantial context");
    return cycle.reasoner().is(meaning(recipient), context);
  }

  @Override
  public Effect attribute(
      Consequence.Cause cause,
      Observation source,
      Rule rule,
      List<Observation> recipients,
      ServiceContextScope scope)
      throws Exception {
    if (rule.selector() == ConsequenceRecipients.Selector.EXPRESSION)
      throw new IllegalStateException(
          "Explicit recipients require complete native discovery evidence");
    return attribute(
        cause,
        source,
        rule,
        new ConsequenceRecipients.Selection(
            ConsequenceRecipients.Status.COMPLETE, recipients, "Implicit recipients"),
        scope,
        null);
  }

  @Override
  public Effect attributeSelected(
      Consequence.Cause cause,
      Observation source,
      Rule rule,
      ConsequenceRecipients.Selection selected,
      ServiceContextScope scope)
      throws Exception {
    if (selected.status() != ConsequenceRecipients.Status.COMPLETE)
      throw new IllegalStateException("Recipient selection did not complete");
    SelectionProof proof = null;
    if (rule.selector() == ConsequenceRecipients.Selector.EXPRESSION) {
      if (!(selected.evidence() instanceof SelectionProof value))
        throw new IllegalStateException(
            "Explicit recipients have no native complete-selection proof");
      proof = value;
      var cycle = cycle(scope);
      if (proof.issuer() != issuer
          || proof.sourceId() != source.getId()
          || !proof.cycle().equals(cycle.binding())
          || !proof.perspective().equals(perspective(cycle.scope()))
          || !proof.expression().equals(urn(rule.recipient()))
          || !(rule.evidence() instanceof Discovered valueRule)
          || !proof.ruleIdentity().equals(valueRule.sourceIdentity()))
        throw new IllegalStateException(
            "Recipient selection belongs to another source/rule/cycle/perspective");
      proof.complete().requireExactMembers(selected.recipients());
      revalidateSelection(proof, cycle.scope());
      validateNativeSelection(proof, cycle.scope());
    }
    return attribute(cause, source, rule, selected, scope, proof);
  }

  private Effect attribute(
      Consequence.Cause cause,
      Observation source,
      Rule rule,
      ConsequenceRecipients.Selection selected,
      ServiceContextScope scope,
      SelectionProof selection)
      throws Exception {
    active(scope);
    var cycle = cycle(scope);
    scope = cycle.scope();
    var original = requireRule(source, rule, cycle, scope);
    var recipients = selected.recipients();
    var ids = recipients.stream().map(Observation::getId).distinct().sorted().toList();
    var inputs = captureSource(source.getId(), ids, scope);
    var expected = refresh(original, inputs, scope);
    if (rule.selector() != ConsequenceRecipients.Selector.EXPRESSION
        && !defaultRecipients(inputs, source.getId(), rule.selector()).equals(new TreeSet<>(ids)))
      throw new IllegalStateException(
          "Selected recipients differ from captured source/bearer/endpoint evidence");
    var eligible = new ArrayList<Observation>();
    Concept explicit =
        rule.selector() == ConsequenceRecipients.Selector.EXPRESSION
            ? cycle.reasoner().resolveConcept(urn(rule.recipient()))
            : null;
    for (var id : ids) {
      var recipient = inputs.observation(id);
      var selectedMember =
          recipients.stream().filter(value -> value.getId() == id).findFirst().orElseThrow();
      if (selection != null
          && !org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
              .persistedObservationProperties(selectedMember)
              .equals(
                  org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
                      .persistedObservationProperties(recipient)))
        throw new IllegalStateException(
            "Selected recipient payload changed before attribution capture");
      if (inputs.observation(source.getId()).getObservable().is(SemanticType.CONFIGURATION)
          && !ConfigurationRoleInputs.participants(source.getId(), inputs).contains(id))
        continue; // Complete discovery remains guarded; only its exact captured participant
                  // intersection acquires the role.
      if (recipient.getObservable().getSemantics().isCollective()
          || !SemanticType.isEnumerableSubstantial(
              recipient.getObservable().getSemantics().getType()))
        throw new IllegalStateException(
            "Conferred roles require individual substantial recipients");
      if (rule.selector() == ConsequenceRecipients.Selector.EXPRESSION
          && (explicit == null || !cycle.reasoner().is(meaning(recipient), explicit)))
        throw new IllegalStateException(
            "Resolved recipient does not match authored recipient semantics");
      if (within(cycle, expected.entry().rule(), recipient)) eligible.add(recipient);
    }
    if (eligible.isEmpty())
      return effect(
          Consequence.Outcome.NO_ACTION,
          "No new applicable role acquisition",
          Resolution.NOT_REQUESTED);
    var execution = scope.forConsequence(cause);
    active(execution);
    var transaction =
        execution
            .getDigitalTwin()
            .transaction(
                Activity.of(Activity.Type.CLASSIFICATION),
                execution,
                RuntimeAsset.PROVENANCE_ASSET);
    if (!(transaction instanceof DigitalTwinImpl.TransactionImpl nativeTransaction)) {
      transaction.fail(new IllegalStateException("Guarded consequence attribution is unavailable"));
      throw new IllegalStateException("Guarded consequence attribution is unavailable");
    }
    try {
      nativeTransaction.guardConsequenceBaseline(inputs.reads(), execution);
      if (selection != null)
        nativeTransaction.guardConsequenceBaseline(selection.complete().inputs(), execution);
      var admission = new RuleAdmission(expected, inputs, execution, selection);
      var pending =
          eligible.stream()
              .map(
                  recipient ->
                      new ConsequenceRoleAttribution(
                          inputs.observation(source.getId()),
                          recipient,
                          expected.role(),
                          rule.id()))
              .toList();
      var supports = nativeTransaction.stageConsequenceRoleSupports(pending, execution, admission);
      if (!supports.supportChanged()) {
        nativeTransaction.fail(null);
        return effect(
            Consequence.Outcome.NO_ACTION,
            "Role support is already recorded",
            Resolution.NOT_REQUESTED);
      }
      if (nativeTransaction.commit() <= 0)
        throw new IllegalStateException("Guarded consequence role commit failed");
      if (supports.acquiredRecipientIds().isEmpty())
        return effect(
            Consequence.Outcome.NO_ACTION,
            "Independent role support committed without a new acquisition",
            Resolution.NOT_REQUESTED);
      return new Effect(
          Consequence.Outcome.APPLIED,
          supports.acquiredRecipientIds(),
          "Roles committed before optional characterization",
          Resolution.NOT_REQUESTED);
    } catch (Exception failure) {
      nativeTransaction.fail(failure);
      throw failure;
    }
  }

  private final class RuleAdmission implements ConsequenceRoleAdmission {
    private final Discovered discovered;
    private final ConsequenceNativeInputs.Snapshot inputs;
    private final ServiceContextScope execution;
    private final SelectionProof selection;
    private SemanticDependencyRequest request;
    private SemanticDependencyWitness proof;

    RuleAdmission(
        Discovered discovered,
        ConsequenceNativeInputs.Snapshot inputs,
        ServiceContextScope execution,
        SelectionProof selection) {
      this.discovered = discovered;
      this.inputs = inputs;
      this.execution = execution;
      this.selection = selection;
    }

    private void source() {
      var current = cycle(execution);
      if (!current.binding().equals(discovered.cycle().binding())
          || !discovered.perspective().equals(perspective(execution)))
        throw new IllegalStateException("Consequence cycle or perspective changed");
      var owner = meaning(inputs.observation(discovered.sourceId()));
      var response = query(current, owner, discovered.entry().rule().kind());
      if (!SemanticDependencyWitness.revalidates(
              discovered.candidates().witness(), response.witness())
          || response.rules().stream()
              .noneMatch(entry -> identity(entry).equals(discovered.sourceIdentity()))
          || !current.reasoner().is(owner, discovered.entry().owner()))
        throw new IllegalStateException(
            "Authored consequence rule no longer revalidates within its cycle");
      if (selection != null) revalidateSelection(selection, execution);
    }

    @Override
    public void validate(List<Prepared> prepared, ContextScope scope) {
      if (scope != execution)
        throw new IllegalStateException("Consequence admission execution scope changed");
      source();
      var obligations =
          new ArrayList<String>(
              ConsequenceInputEvidence.inherit(
                  inputs,
                  List.of(discovered.sourceId()),
                  inputs.observations().keySet(),
                  discovered.cycle().reasoner(),
                  execution));
      inputs.observations().values().forEach(value -> obligations.add(meaning(value).getUrn()));
      if (selection != null)
        obligations.addAll(selection.complete().semanticEvidence().obligations());
      obligations.add(discovered.entry().owner().getUrn());
      obligations.add(discovered.role().getUrn());
      for (var value : prepared) {
        if (!value.ruleId().equals(discovered.entry().ruleId())
            || !value.role().getUrn().equals(discovered.role().getUrn())
            || value.sourceId() != discovered.sourceId()
            || !inputs.observations().containsKey(value.recipientId())
            || !within(
                discovered.cycle(),
                discovered.entry().rule(),
                inputs.observation(value.recipientId())))
          throw new IllegalStateException(
              "Consequence attribution is not covered by its source rule");
        obligations.add(value.before().getUrn());
        obligations.add(value.after().getUrn());
        obligations.add(meaning(inputs.observation(value.sourceId())).getUrn());
      }
      if (discovered.entry().rule().within() != null)
        obligations.add(urn(discovered.entry().rule().within()));
      if (discovered.entry().rule().recipient().expression() != null)
        obligations.add(urn(discovered.entry().rule().recipient().expression()));
      if (discovered.entry().rule().secondaryInherency() != null)
        obligations.add(urn(discovered.entry().rule().secondaryInherency()));
      var epoch = discovered.cycle().epoch();
      request =
          new SemanticDependencyRequest(
              epoch.serviceId(),
              epoch.knowledgeRevision(),
              epoch.semanticFingerprint(),
              obligations);
      proof = discovered.cycle().reasoner().semanticDependencies(request, execution);
      witness(proof, discovered.cycle());
      if (!request.inputHash().equals(proof.inputHash()))
        throw new IllegalStateException(
            "Consequence applicability witness covers different inputs");
    }

    @Override
    public void admitCommit() {
      source();
      var current = discovered.cycle().reasoner().semanticDependencies(request, execution);
      witness(current, discovered.cycle());
      if (!SemanticDependencyWitness.revalidates(proof, current))
        throw new IllegalStateException("Consequence applicability witness changed before commit");
    }

    @Override
    public SemanticEvidenceBinding semanticEvidence() {
      if (request == null || proof == null)
        throw new IllegalStateException("Consequence execution evidence has not been admitted");
      return new SemanticEvidenceBinding(
          SemanticEvidenceBinding.VERSION, true, request.obligations(), proof);
    }

    @Override
    public Map<String, Object> provenance() {
      var result = new LinkedHashMap<String, Object>();
      result.put("cycle", discovered.cycle().binding());
      result.put("rule", discovered.entry());
      result.put("sourceWitness", discovered.candidates().witness());
      result.put("applicabilityWitness", proof);
      if (selection != null)
        result.put("recipientDiscoveryRequestCommit", selection.requestCommit());
      return Map.copyOf(result);
    }
  }

  @Override
  public Arrivals arrivals(KnowledgeGraph.Commit commit, ServiceContextScope scope) {
    var cycle = cycle(scope);
    return RoleAttributionEvents.read(commit, cycle.scope(), cycle.reasoner()::resolveConcept);
  }

  private record Submitted(Observation observation, Effect effect) {}

  private Submitted submit(Observable observable, ServiceContextScope scope) throws Exception {
    active(scope);
    var optional = observable.builder(scope).optional(true).buildObservable();
    var request = scope.observation(optional).build();
    if (request == null)
      return new Submitted(
          null,
          effect(
              Consequence.Outcome.DEFERRED,
              "Optional request construction is unavailable",
              Resolution.UNRESOLVED));
    var task =
        request.isEmpty()
            ? java.util.concurrent.CompletableFuture.completedFuture(request)
            : localRuntime.submit(request, scope);
    var result = ChangeProcessHandler.awaitSubmissionDrain(task);
    active(scope);
    if (result != null && !result.isEmpty() && result.getObservable() != null)
      return new Submitted(
          result,
          effect(
              Consequence.Outcome.APPLIED,
              "Optional target resolved",
              Resolution.RESOLVED,
              result.getId()));
    // A typed terminal receipt is required to call an empty result NO_MODEL. Generic empty,
    // inaccessible and missing results remain unresolved/failed, never successful discovery.
    var receipt =
        org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.read(result).orElse(null);
    if (receipt
        == org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.NO_MODEL)
      return new Submitted(
          result,
          effect(Consequence.Outcome.NO_ACTION, "No optional target model", Resolution.NO_MODEL));
    if (receipt
        == org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.DEFERRED)
      return new Submitted(
          result,
          effect(
              Consequence.Outcome.DEFERRED,
              "Optional target is unresolved",
              Resolution.UNRESOLVED));
    if (receipt
            == org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.FAILED
        || result != null && Utils.Notifications.hasErrors(result.getNotifications()))
      return new Submitted(
          result,
          effect(
              Consequence.Outcome.FAILED, "Optional target resolution failed", Resolution.FAILED));
    return new Submitted(
        result,
        effect(
            Consequence.Outcome.DEFERRED,
            "Optional target completion is unavailable",
            Resolution.UNRESOLVED));
  }

  /** Carries source evidence into the real ordinary target transaction, not an HTTP claim. */
  private final class RequestAdmission implements ConsequenceSubmissionAdmission {
    private final Cycle cycle;
    private final ConsequenceNativeInputs.Snapshot inputs;
    private final long sourceId;
    private final Concept trigger;
    private final ConsequenceRuleResponse candidates;
    private final ConsequenceRuleResponse.Entry entry;
    private final String ruleIdentity;
    private final Perspective perspective;
    private final String targetUrn;
    private final SelectionProof selection;
    private final ServiceContextScope origin;
    private final String owner;
    private volatile DigitalTwin.Transaction boundRoot;
    private volatile boolean bound;
    private volatile long committed;

    private record RootProof(
        SemanticDependencyRequest request, SemanticDependencyWitness witness) {}

    private final java.util.concurrent.atomic.AtomicReference<RootProof> rootProof =
        new java.util.concurrent.atomic.AtomicReference<>();

    RequestAdmission(
        Cycle cycle,
        ConsequenceNativeInputs.Snapshot inputs,
        long sourceId,
        Concept trigger,
        ConsequenceRuleResponse candidates,
        ConsequenceRuleResponse.Entry entry,
        Observable target,
        ServiceContextScope execution,
        SelectionProof selection) {
      this.cycle = cycle;
      this.inputs = inputs;
      this.sourceId = sourceId;
      this.trigger = roleSnapshot(trigger);
      this.candidates = candidates;
      this.entry = entry;
      this.ruleIdentity = entry == null ? null : identity(entry);
      this.perspective = perspective(execution);
      this.targetUrn = target.getUrn();
      this.selection = selection;
      this.origin = execution;
      this.owner = execution.getUser().getUsername();
    }

    private static DigitalTwin.Transaction root(DigitalTwin.Transaction transaction) {
      if (transaction == null) return null;
      while (transaction.getParent() != null) transaction = transaction.getParent();
      return transaction;
    }

    private void caller(
        ServiceContextScope execution, DigitalTwin.Transaction current, boolean firstBind) {
      var active = cycle(execution);
      if (!active.binding().equals(cycle.binding())
          || !Objects.equals(owner, execution.getUser().getUsername())
          || !Objects.equals(perspective.observer(), execution.getObserverId()))
        throw new IllegalStateException("Foreign consequence existing-result scope");
      var actualRoot = root(current);
      if (actualRoot != null
          && (boundRoot == null && !firstBind || boundRoot != null && actualRoot != boundRoot))
        throw new IllegalStateException(
            "Existing consequence dependency belongs to an unknown or foreign native root");
      if (!perspective.equals(perspective(execution))
          && (boundRoot == null || actualRoot != boundRoot))
        throw new IllegalStateException(
            "Derived consequence focus is not owned by the admitted request root");
    }

    private void semantic(ServiceContextScope execution) {
      var active = cycle(execution);
      if (!active.binding().equals(cycle.binding()) || !perspective.equals(perspective(execution)))
        throw new IllegalStateException("Consequence request cycle/perspective changed");
      var source = inputs.observation(sourceId);
      if (trigger.is(SemanticType.ROLE)) {
        if (!active.reasoner().hasRole(source.getObservable(), trigger))
          throw new IllegalStateException("Trigger role no longer applies to captured source");
      } else if (!active.reasoner().is(meaning(source), trigger))
        throw new IllegalStateException("Consequence source no longer matches its owner");
      if (entry != null) {
        var result = query(active, trigger, entry.rule().kind());
        if (!SemanticDependencyWitness.revalidates(candidates.witness(), result.witness())
            || result.rules().stream().noneMatch(rule -> identity(rule).equals(ruleIdentity)))
          throw new IllegalStateException(
              "Implication source rule no longer revalidates in its retained cycle");
        if (entry.rule().kind() == KimConsequenceRule.Kind.IMPLIES
            && trigger.is(SemanticType.ROLE)
            && !within(active, entry.rule(), source))
          throw new IllegalStateException(
              "Role implication within condition is no longer satisfied");
      }
      if (selection != null) revalidateSelection(selection, execution);
    }

    @Override
    public void requireTarget(Observable requested, ServiceContextScope execution) {
      caller(execution, execution.getCurrentTransaction(), false);
      semantic(execution);
      if (requested == null || !Objects.equals(targetUrn, requested.getUrn()))
        throw new SecurityException(
            "Consequence characterization target differs from its admitted request");
    }

    @Override
    public void bind(DigitalTwin.Transaction transaction, ServiceContextScope execution) {
      if (!(transaction instanceof DigitalTwinImpl.TransactionImpl guarded))
        throw new IllegalStateException("Guarded consequence requests are unavailable");
      caller(execution, transaction, true);
      var actualRoot = root(transaction);
      synchronized (this) {
        if (boundRoot == null) boundRoot = actualRoot;
        else if (boundRoot != actualRoot)
          throw new IllegalStateException("Consequence admission cannot bind another native root");
      }
      semantic(origin);
      guarded.guardConsequenceBaseline(inputs.reads(), execution);
      if (selection != null)
        guarded.guardConsequenceBaseline(selection.complete().inputs(), execution);
      var previous = rootProof.get();
      if (previous != null) {
        // A child uses the already issued immutable causal root proof. Its actual target,
        // model and data dependencies remain accounted by the ordinary child collector.
        var originalRequest = previous.request();
        var originalProof = previous.witness();
        guarded.guardConsequenceCommitAdmission(
            () -> {
              semantic(origin);
              var refreshed = cycle.reasoner().semanticDependencies(originalRequest, origin);
              witness(refreshed, cycle);
              if (!SemanticDependencyWitness.revalidates(originalProof, refreshed))
                throw new IllegalStateException(
                    "Consequence root authority changed during dependency execution");
            });
        bound = true;
        return;
      }
      if (trigger.is(SemanticType.ROLE))
        ConsequenceInputEvidence.requireAttribution(inputs, sourceId);
      var obligations =
          new TreeSet<String>(
              ConsequenceInputEvidence.inherit(
                  inputs,
                  trigger.is(SemanticType.ROLE) ? List.of() : List.of(sourceId),
                  inputs.observations().keySet(),
                  cycle.reasoner(),
                  execution));
      if (selection != null)
        obligations.addAll(selection.complete().semanticEvidence().obligations());
      obligations.add(targetUrn);
      obligations.add(trigger.getUrn());
      inputs
          .observations()
          .values()
          .forEach(observation -> obligations.add(observation.getObservable().getUrn()));
      if (entry != null) {
        if (entry.rule().within() != null) obligations.add(urn(entry.rule().within()));
        if (entry.rule().secondaryInherency() != null)
          obligations.add(urn(entry.rule().secondaryInherency()));
      }
      var epoch = cycle.epoch();
      var request =
          new SemanticDependencyRequest(
              epoch.serviceId(),
              epoch.knowledgeRevision(),
              epoch.semanticFingerprint(),
              List.copyOf(obligations));
      var proof = cycle.reasoner().semanticDependencies(request, execution);
      witness(proof, cycle);
      if (!request.inputHash().equals(proof.inputHash()))
        throw new IllegalStateException("Implication semantic receipt covers different inputs");
      var requestAuthority = new LinkedHashMap<String, Object>();
      requestAuthority.put("cycle", cycle.binding());
      requestAuthority.put("sourceId", sourceId);
      requestAuthority.put("trigger", trigger.getUrn());
      requestAuthority.put(
          "rule", ruleIdentity == null ? "intrinsic-role-characterization" : ruleIdentity);
      requestAuthority.put("witness", proof);
      // Capture the source fact's original perspective, not the target's derived `within`
      // focus. This is request provenance only; actual model publication must prove its use.
      if (trigger.is(SemanticType.ROLE))
        requestAuthority.put(
            "logicalRolePresence",
            RolePresenceKey.captured(cycle.binding().contextId(), inputs, sourceId, trigger));
      transaction
          .getActivity()
          .getMetadata()
          .put("klab.consequence.requestAuthority", Utils.Json.asString(requestAuthority));
      rootProof.compareAndSet(null, new RootProof(request, proof));
      var admitted = rootProof.get();
      if (!admitted.request().inputHash().equals(request.inputHash())
          || !SemanticDependencyWitness.revalidates(admitted.witness(), proof))
        throw new IllegalStateException(
            "Concurrent consequence root admission disagrees on its semantic closure");
      guarded.guardConsequenceCommitAdmission(
          () -> {
            semantic(origin);
            var refreshed = cycle.reasoner().semanticDependencies(admitted.request(), origin);
            witness(refreshed, cycle);
            if (!SemanticDependencyWitness.revalidates(admitted.witness(), refreshed))
              throw new IllegalStateException(
                  "Consequence request semantic proof changed before commit");
          });
      bound = true;
    }

    @Override
    public ConsequenceSubmissionAdmission.ExistingResultGuard prepareExisting(
        Observation result, ServiceContextScope execution) {
      caller(execution, execution.getCurrentTransaction(), false);
      if (execution.getCurrentTransaction() != null && rootProof.get() == null)
        throw new IllegalStateException("Consequence causal root has no completed admission proof");
      semantic(origin);
      if (result != null && !result.isEmpty() && result.getId() == Observation.QUERY_ID) {
        boolean nested = boundRoot != null && root(execution.getCurrentTransaction()) == boundRoot;
        var expected =
            cycle
                .reasoner()
                .resolveObservable(nested ? result.getObservable().getUrn() : targetUrn);
        if (expected == null
            || !Objects.equals(
                expected.asConcept().getUrn(), result.getObservable().asConcept().getUrn()))
          throw new IllegalStateException(
              "Issued consequence query has a different requested goal");
        var complete = ConsequenceDiscoverySnapshot.capture(localRuntime, result, execution);
        if (trigger.is(SemanticType.ROLE))
          ConsequenceInputEvidence.requireAttribution(inputs, sourceId);
        var terms =
            new TreeSet<String>(
                ConsequenceInputEvidence.inherit(
                    inputs,
                    trigger.is(SemanticType.ROLE) ? List.of() : List.of(sourceId),
                    inputs.observations().keySet(),
                    cycle.reasoner(),
                    origin));
        terms.addAll(complete.semanticEvidence().obligations());
        terms.add(targetUrn);
        terms.add(trigger.getUrn());
        if (selection != null) terms.addAll(selection.complete().semanticEvidence().obligations());
        if (entry != null) {
          if (entry.rule().within() != null) terms.add(urn(entry.rule().within()));
          if (entry.rule().secondaryInherency() != null)
            terms.add(urn(entry.rule().secondaryInherency()));
        }
        var epoch = cycle.epoch();
        var queryRequest =
            new SemanticDependencyRequest(
                epoch.serviceId(),
                epoch.knowledgeRevision(),
                epoch.semanticFingerprint(),
                List.copyOf(terms));
        var queryProof = cycle.reasoner().semanticDependencies(queryRequest, origin);
        witness(queryProof, cycle);
        if (!queryRequest.inputHash().equals(queryProof.inputHash()))
          throw new IllegalStateException("Existing query receipt covers different causal inputs");
        var reads = new ArrayList<KnowledgeGraph.ConsequenceReadSet>();
        reads.add(inputs.reads());
        reads.add(complete.inputs());
        if (selection != null) reads.add(selection.complete().inputs());
        var ids = new TreeSet<Long>();
        reads.forEach(read -> ids.addAll(read.assetIds()));
        return nativeTransaction -> {
          if (execution.isInterrupted() || Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException(
                "Consequence query admission cancelled");
          if (!queryProof.completeAt(System.currentTimeMillis()))
            throw new IllegalStateException("Existing query semantic receipt expired");
          nativeTransaction.lockConsequenceInputs(ids);
          for (var read : reads) nativeTransaction.validateConsequenceInputs(read);
        };
      }
      if (result == null || result.isEmpty() || result.getId() <= 0)
        throw new IllegalStateException("Existing consequence target is not durable");
      var current = captureSource(sourceId, List.of(result.getId()), execution);
      var target = current.observation(result.getId());
      if (result.getGeometry() == null
          || target.getGeometry() == null
          || !org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
              .persistedObservationProperties(result)
              .equals(
                  org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
                      .persistedObservationProperties(target))
          || !Objects.equals(result.getGeometry().encode(), target.getGeometry().encode()))
        throw new IllegalStateException("Existing consequence target changed before admission");
      boolean nested = boundRoot != null && root(execution.getCurrentTransaction()) == boundRoot;
      var expected =
          cycle.reasoner().resolveObservable(nested ? result.getObservable().getUrn() : targetUrn);
      long parent =
          execution.getContextObservation() == null
              ? RuntimeAsset.CONTEXT_ASSET_ID
              : execution.getContextObservation().getId();
      if (expected == null
          || target.getParentId() != parent
          || !Objects.equals(
              target.getObservable().asConcept().getUrn(), expected.asConcept().getUrn())
          || org.integratedmodelling.klab.runtime.ObserverResolution.preference(
                  target.getObservable(), expected, execution)
              < 0)
        throw new IllegalStateException(
            "Existing consequence target has incompatible meaning, parent or observer");
      var support = ContextScope.getResolutionGeometry(execution);
      if (support == null) support = result.getGeometry();
      var coverage =
          org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(target, support);
      if (coverage == null || !coverage.isComplete())
        throw new IllegalStateException("Existing consequence target has incomplete coverage");
      var capabilities = cycle.reasoner().capabilities(execution);
      if (capabilities == null
          || cycle
                  .reasoner()
                  .semanticDistance(
                      target.getObservable(), expected, null, capabilities.getKnowledgeRevision())
              < 0)
        throw new IllegalStateException(
            "Existing consequence target has no compatible semantic receipt");
      org.integratedmodelling.klab.runtime.storage.StorageReads.validateSpatialReuse(
          target, support, execution);
      if (trigger.is(SemanticType.ROLE))
        ConsequenceInputEvidence.requireAttribution(inputs, sourceId);
      var terms =
          new TreeSet<String>(
              ConsequenceInputEvidence.inherit(
                  inputs,
                  trigger.is(SemanticType.ROLE) ? List.of() : List.of(sourceId),
                  inputs.observations().keySet(),
                  cycle.reasoner(),
                  origin));
      terms.addAll(
          ConsequenceInputEvidence.inherit(
              current,
              List.of(result.getId()),
              current.observations().keySet(),
              cycle.reasoner(),
              execution));
      terms.add(targetUrn);
      terms.add(trigger.getUrn());
      if (selection != null) terms.addAll(selection.complete().semanticEvidence().obligations());
      if (entry != null) {
        if (entry.rule().within() != null) terms.add(urn(entry.rule().within()));
        if (entry.rule().secondaryInherency() != null)
          terms.add(urn(entry.rule().secondaryInherency()));
      }
      var epoch = cycle.epoch();
      var existingRequest =
          new SemanticDependencyRequest(
              epoch.serviceId(),
              epoch.knowledgeRevision(),
              epoch.semanticFingerprint(),
              List.copyOf(terms));
      var existingProof = cycle.reasoner().semanticDependencies(existingRequest, origin);
      witness(existingProof, cycle);
      if (!existingRequest.inputHash().equals(existingProof.inputHash()))
        throw new IllegalStateException(
            "Existing consequence receipt covers different semantic inputs");
      var reads = new ArrayList<KnowledgeGraph.ConsequenceReadSet>();
      reads.add(inputs.reads());
      reads.add(current.reads());
      if (selection != null) reads.add(selection.complete().inputs());
      var ids = new TreeSet<Long>();
      reads.forEach(read -> ids.addAll(read.assetIds()));
      return nativeTransaction -> {
        if (execution.isInterrupted() || Thread.currentThread().isInterrupted())
          throw new java.util.concurrent.CancellationException(
              "Consequence existing-result admission cancelled");
        if (!existingProof.completeAt(System.currentTimeMillis()))
          throw new IllegalStateException(
              "Existing consequence semantic receipt expired before native admission");
        nativeTransaction.lockConsequenceInputs(ids);
        for (var read : reads) nativeTransaction.validateConsequenceInputs(read);
      };
    }

    @Override
    public SemanticEvidenceBinding semanticEvidence() {
      var admitted = rootProof.get();
      if (admitted == null) return null;
      return new SemanticEvidenceBinding(
          SemanticEvidenceBinding.VERSION,
          true,
          admitted.request().obligations(),
          admitted.witness());
    }

    @Override
    public void committed(long commitId) {
      if (commitId <= 0)
        throw new IllegalArgumentException("Consequence request commit must be durable");
      committed = commitId;
    }
  }

  @Override
  public Effect characterize(Consequence.Cause cause, Arrival arrival, ServiceContextScope scope)
      throws Exception {
    var cycle = cycle(scope);
    scope = cycle.scope();
    var inputs = captureSource(arrival.recipientId(), List.of(), scope);
    var recipient = inputs.observation(arrival.recipientId());
    if (!cycle.reasoner().hasRole(recipient.getObservable(), arrival.role()))
      return effect(
          Consequence.Outcome.DEFERRED,
          "Conferred role is no longer accessible",
          Resolution.UNRESOLVED);
    var role = cycle.reasoner().resolveObservable(arrival.role().getUrn());
    if (role == null)
      return effect(
          Consequence.Outcome.DEFERRED,
          "Conferred role semantics are unavailable",
          Resolution.UNRESOLVED);
    var requestScope = scope.within(recipient);
    var request =
        cycle.reasoner().inherent(role) == null
            ? role.builder(requestScope).of(meaning(recipient)).buildObservable()
            : role;
    var admitted =
        requestScope.withConsequenceSubmissionAdmission(
            new RequestAdmission(
                cycle,
                inputs,
                arrival.recipientId(),
                arrival.role(),
                null,
                null,
                request,
                requestScope,
                null));
    return switch (localRuntime.characterizeConsequence(recipient, request, admitted)) {
      case NO_MODEL ->
          effect(
              Consequence.Outcome.NO_ACTION,
              "No optional characterization model",
              Resolution.NO_MODEL);
      case DEFERRED ->
          effect(
              Consequence.Outcome.DEFERRED,
              "Optional characterization is unresolved",
              Resolution.UNRESOLVED);
      case FAILED ->
          effect(Consequence.Outcome.FAILED, "Optional characterization failed", Resolution.FAILED);
      case RESOLVED ->
          effect(
              Consequence.Outcome.APPLIED,
              "Optional characterization resolved",
              Resolution.RESOLVED,
              recipient.getId());
    };
  }

  @Override
  public Effect implications(Consequence.Cause cause, Arrival arrival, ServiceContextScope scope)
      throws Exception {
    active(scope);
    var cycle = cycle(scope);
    scope = cycle.scope();
    var inputs = captureSource(arrival.recipientId(), List.of(), scope);
    var recipient = inputs.observation(arrival.recipientId());
    if (!cycle.reasoner().hasRole(recipient.getObservable(), arrival.role()))
      return effect(
          Consequence.Outcome.DEFERRED,
          "Role trigger is no longer accessible",
          Resolution.UNRESOLVED);
    return imply(
        query(cycle, arrival.role(), KimConsequenceRule.Kind.IMPLIES),
        inputs,
        recipient,
        cycle,
        scope,
        false,
        arrival.role());
  }

  @Override
  public Effect sourceImplications(
      Consequence.Cause cause, KnowledgeGraph.Commit commit, ServiceContextScope scope)
      throws Exception {
    active(scope);
    var cycle = cycle(scope);
    scope = cycle.scope();
    var effects = new ArrayList<Effect>();
    boolean configuration = false;
    var ids = new TreeSet<Long>(commit.getAddedObservations());
    ids.addAll(commit.getModifiedAssets());
    for (var id : ids) {
      try {
        // Modified assets also contain Activities. Do not ask the observation cache to load an
        // unrelated asset (its loader correctly rejects a null observation); route from native
        // type.
        var current =
            scope
                .getDigitalTwin()
                .getKnowledgeGraph()
                .query(Observation.class, scope)
                .id(id)
                .peek(scope)
                .orElse(null);
        if (current == null || current.getObservable() == null) continue;
        if (current.getObservable().is(SemanticType.CONFIGURATION)) {
          configuration = true;
          effects.add(configurationImplications(cause, id, scope));
          continue;
        }
        if (!current.getObservable().is(SemanticType.RELATIONSHIP)) continue;
        var inputs = captureSource(id, List.of(), scope);
        var source = inputs.observation(id);
        effects.add(
            imply(
                query(cycle, meaning(source), KimConsequenceRule.Kind.IMPLIES),
                inputs,
                source,
                cycle,
                scope,
                true,
                meaning(source)));
      } catch (InterruptedException cancellation) {
        throw cancellation;
      } catch (Exception failure) {
        effects.add(
            effect(
                Consequence.Outcome.FAILED,
                "Source implication failed: " + failure.getClass().getSimpleName(),
                Resolution.FAILED));
      }
    }
    if (configuration && configurations != null)
      effects.add(configurations.implications(cause, commit, scope));
    return combine(effects, "Source implications");
  }

  /** Consume a durably detected/resolved configuration; detection/materialization is separate. */
  public Effect configurationImplications(
      Consequence.Cause cause, long sourceId, ServiceContextScope scope) throws Exception {
    active(scope);
    var cycle = cycle(scope);
    scope = cycle.scope();
    var inputs = captureSource(sourceId, List.of(), scope);
    var source = inputs.observation(sourceId);
    if (!source.getObservable().is(SemanticType.CONFIGURATION))
      throw new IllegalStateException("Configuration implication source is not a configuration");
    var candidates = query(cycle, meaning(source), KimConsequenceRule.Kind.IMPLIES);
    var effects = new ArrayList<Effect>();
    for (var entry : candidates.rules()) {
      try {
        if (entry.rule().kind() != KimConsequenceRule.Kind.IMPLIES
            || !cycle.reasoner().is(meaning(source), entry.owner())) continue;
        var role = cycle.reasoner().resolveConcept(urn(entry.rule().target()));
        if (role == null || !role.is(SemanticType.ROLE) || role.is(SemanticType.NOTHING))
          throw new IllegalStateException(
              "Configuration implication must attribute an available role");
        var proof =
            new Discovered(
                issuer,
                sourceId,
                perspective(scope),
                cycle,
                inputs,
                candidates,
                entry,
                identity(entry),
                role.getUrn(),
                roleSnapshot(role));
        var rule =
            new Rule(
                entry.ruleId(),
                roleSnapshot(role),
                selector(entry.rule()),
                syntaxSnapshot(recipientExpression(entry.rule())),
                proof);
        if (rule.selector() == ConsequenceRecipients.Selector.EXPRESSION) {
          var selected = resolveRecipients(source, rule, scope);
          if (selected.status() != ConsequenceRecipients.Status.COMPLETE) {
            effects.add(
                effect(
                    selected.status() == ConsequenceRecipients.Status.FAILED
                        ? Consequence.Outcome.FAILED
                        : Consequence.Outcome.DEFERRED,
                    selected.reason(),
                    Resolution.UNRESOLVED));
            continue;
          }
          effects.add(attributeSelected(cause, source, rule, selected, scope));
        } else {
          var defaults =
              defaultRecipients(inputs, sourceId, ConsequenceRecipients.Selector.DEFAULT).stream()
                  .map(inputs::observation)
                  .toList();
          effects.add(attribute(cause, source, rule, defaults, scope));
        }
      } catch (InterruptedException cancellation) {
        throw cancellation;
      } catch (Exception failure) {
        effects.add(
            effect(
                Consequence.Outcome.FAILED,
                "Configuration role implication failed: " + failure.getClass().getSimpleName(),
                Resolution.FAILED));
      }
    }
    return combine(effects, "Configuration role implications");
  }

  private Effect imply(
      ConsequenceRuleResponse response,
      ConsequenceNativeInputs.Snapshot inputs,
      Observation bearer,
      Cycle cycle,
      ServiceContextScope scope,
      boolean relationship,
      Concept queryOwner)
      throws Exception {
    var effects = new ArrayList<Effect>();
    for (var entry : response.rules()) {
      active(scope);
      if (entry.rule().kind() != KimConsequenceRule.Kind.IMPLIES) continue;
      try {
        if (!relationship && !cycle.reasoner().hasRole(bearer.getObservable(), entry.owner()))
          continue;
        if (relationship && !cycle.reasoner().is(meaning(bearer), entry.owner())) continue;
        if (!relationship && !within(cycle, entry.rule(), bearer)) continue;
        var target = cycle.reasoner().resolveObservable(urn(entry.rule().target()));
        if (target == null) {
          effects.add(
              effect(
                  Consequence.Outcome.DEFERRED,
                  "Implied target semantics are unavailable",
                  Resolution.UNRESOLVED));
          continue;
        }
        if (relationship && target.is(SemanticType.ROLE)) {
          effects.add(
              effect(
                  Consequence.Outcome.DEFERRED,
                  "Relationship roles must use confers",
                  Resolution.UNRESOLVED));
          continue;
        }
        if (!target.is(SemanticType.QUALITY)
            && !target.is(SemanticType.PROCESS)
            && !target.is(SemanticType.RELATIONSHIP)
            && !target.is(SemanticType.PREDICATE)) {
          effects.add(
              effect(
                  Consequence.Outcome.DEFERRED,
                  "Implied target is not a dependent, relationship or predicate",
                  Resolution.UNRESOLVED));
          continue;
        }
        List<Observation> recipients;
        var requestInputs = inputs;
        SelectionProof selectedProof = null;
        var requestCandidates = response;
        var requestTrigger = queryOwner;
        var requestEntry = entry;
        if (entry.rule().secondaryInherency() != null) {
          var selected =
              discover(
                  entry.rule().secondaryInherency(),
                  cycle,
                  inputs,
                  bearer.getId(),
                  queryOwner,
                  response,
                  entry,
                  scope);
          if (selected.status() != ConsequenceRecipients.Status.COMPLETE) {
            effects.add(
                effect(
                    selected.status() == ConsequenceRecipients.Status.FAILED
                        ? Consequence.Outcome.FAILED
                        : Consequence.Outcome.DEFERRED,
                    selected.reason(),
                    selected.status() == ConsequenceRecipients.Status.FAILED
                        ? Resolution.FAILED
                        : Resolution.UNRESOLVED));
            continue;
          }
          var selectedIds = selected.recipients().stream().map(Observation::getId).toList();
          if (!(selected.evidence() instanceof SelectionProof selection))
            throw new IllegalStateException(
                "Secondary inherency has no complete native selection evidence");
          selectedProof = selection;
          selection.complete().requireExactMembers(selected.recipients());
          var fresh = captureSource(bearer.getId(), selectedIds, scope);
          // This separate request starts after discovery's own committed focus/perception changes.
          var freshSource = fresh.observation(bearer.getId());
          if (relationship
              ? !cycle.reasoner().is(meaning(freshSource), entry.owner())
              : !cycle.reasoner().hasRole(freshSource.getObservable(), entry.owner()))
            throw new IllegalStateException(
                "Implication trigger no longer applies after recipient discovery");
          requestTrigger = relationship ? meaning(freshSource) : queryOwner;
          requestCandidates = query(cycle, requestTrigger, KimConsequenceRule.Kind.IMPLIES);
          var authoredIdentity = identity(entry);
          requestEntry =
              requestCandidates.rules().stream()
                  .filter(rule -> identity(rule).equals(authoredIdentity))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Authored implication changed during recipient discovery"));
          recipients = selectedIds.stream().map(fresh::observation).toList();
          requestInputs = fresh;
        } else if (relationship) {
          var inherent = cycle.reasoner().inherent(target);
          if (inherent == null) {
            effects.add(
                effect(
                    Consequence.Outcome.DEFERRED,
                    "Relationship implication requires unambiguous inherency or explicit for",
                    Resolution.UNRESOLVED));
            continue;
          }
          recipients =
              inputs.observations().values().stream()
                  .filter(candidate -> cycle.reasoner().is(meaning(candidate), inherent))
                  .toList();
          if (recipients.size() != 1) {
            effects.add(
                effect(
                    Consequence.Outcome.DEFERRED,
                    "Relationship implication inherency is ambiguous",
                    Resolution.UNRESOLVED));
            continue;
          }
        } else recipients = List.of(bearer);
        for (var recipient : recipients) {
          try {
            if (!within(
                cycle,
                entry.rule(),
                relationship ? recipient : requestInputs.observation(bearer.getId()))) continue;
            var requestScope = scope.within(recipient);
            var request = target;
            // The selected focus supplies secondary inherency. An authored native inherent such
            // as Height of Tree must survive a `for Region` clause unchanged.
            if (!target.is(SemanticType.RELATIONSHIP) && cycle.reasoner().inherent(target) == null)
              request = target.builder(requestScope).of(meaning(recipient)).buildObservable();
            var admitted =
                requestScope.withConsequenceSubmissionAdmission(
                    new RequestAdmission(
                        cycle,
                        requestInputs,
                        bearer.getId(),
                        requestTrigger,
                        requestCandidates,
                        requestEntry,
                        request,
                        requestScope,
                        selectedProof));
            effects.add(submit(request, admitted).effect());
          } catch (InterruptedException cancellation) {
            throw cancellation;
          } catch (Exception failure) {
            effects.add(
                effect(
                    Consequence.Outcome.FAILED,
                    "Optional target request failed: " + failure.getClass().getSimpleName(),
                    Resolution.FAILED));
          }
        }
      } catch (InterruptedException cancellation) {
        throw cancellation;
      } catch (Exception failure) {
        effects.add(
            effect(
                Consequence.Outcome.FAILED,
                "Implication preparation failed: " + failure.getClass().getSimpleName(),
                Resolution.FAILED));
      }
    }
    return combine(effects, "Implied targets");
  }

  private static Effect combine(List<Effect> effects, String label) {
    var outcome = Consequence.Outcome.NO_ACTION;
    var resolution = Resolution.NOT_REQUESTED;
    var ids = new TreeSet<Long>();
    var reasons = new ArrayList<String>();
    for (var effect : effects) {
      ids.addAll(effect.observations());
      reasons.add(effect.resolution() + ": " + effect.reason());
      if (effect.outcome() == Consequence.Outcome.FAILED) outcome = Consequence.Outcome.FAILED;
      else if (effect.outcome() == Consequence.Outcome.DEFERRED
          && outcome != Consequence.Outcome.FAILED) outcome = Consequence.Outcome.DEFERRED;
      else if (effect.outcome() == Consequence.Outcome.APPLIED
          && outcome == Consequence.Outcome.NO_ACTION) outcome = Consequence.Outcome.APPLIED;
      if (effect.resolution().ordinal() > resolution.ordinal()) resolution = effect.resolution();
    }
    return new Effect(
        outcome, List.copyOf(ids), label + ": " + String.join("; ", reasons), resolution);
  }
}
