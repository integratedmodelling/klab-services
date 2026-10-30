package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** Bounded observation result path. Normal discovery and UPDATE execution remain transactional. */
final class CollectiveSelectionExecution {
  private CollectiveSelectionExecution() {}

  static boolean candidate(Observation request, ServiceContextScope scope) {
    return CollectiveSelectionBinding.capture(request, scope, scope.getService(Reasoner.class)) != null;
  }

  static boolean activityMatches(Activity activity, CollectiveSelectionBinding binding, boolean currentExecution, ServiceContextScope scope) {
    return activityMatches(activity,binding,currentExecution,scope,null);
  }

  static boolean activityMatches(Activity activity,CollectiveSelectionBinding binding,boolean currentExecution,
      ServiceContextScope scope,Map<String,Object> diagnostic) {
    var observer = activity.getMetadata().get(Metadata.IM_OBSERVER_SELECTION_ID);
    var encodedContext = activity.getMetadata().get(RuntimeService.COLLECTIVE_CONTEXT);
    var context = encodedContext == null ? null : Utils.Json.parseObject(encodedContext.toString(), CollectiveSelectionBinding.Context.class);
    // Current activities retain their admitted focus until the root commit projects it.
    // Durable activities and new admissions then describe that same published focus.
    var actualContext=CollectiveSelectionBinding.context(scope);
    boolean scopeMatches=Objects.equals(binding.context(),actualContext);
    boolean contextMatches=Objects.equals(binding.context(),context);
    boolean observerClassMatches=Objects.equals(binding.observerClass(),activity.getMetadata().get(RuntimeService.COLLECTIVE_OBSERVER_CLASS));
    if(diagnostic!=null){diagnostic.put("bindingContext",binding.context());diagnostic.put("actualContext",actualContext);diagnostic.put("recordedContext",context);diagnostic.put("scopeMatches",scopeMatches);diagnostic.put("contextMatches",contextMatches);diagnostic.put("observerClassMatches",observerClassMatches);}
    if(!scopeMatches||!contextMatches||!observerClassMatches)return false;
    var semantic=diagnostic==null?null:new LinkedHashMap<String,Object>();
    boolean matches=SemanticEvidence.matches(activity,binding.reasonerId(),binding.revision(),binding.semanticFingerprint(),binding.semanticSessionId(),currentExecution,scope,semantic);
    boolean observerMatches=observer instanceof Number id&&id.longValue()==(binding.observerId()==null?0:binding.observerId());
    if(diagnostic!=null){diagnostic.put("semantic",semantic);diagnostic.put("observerMatches",observerMatches);diagnostic.put("actualObserver",observer);diagnostic.put("expectedObserver",binding.observerId());}
    return matches&&observerMatches;
  }

  static Observation execute(RuntimeService runtime, Dataflow plan, Observation requested,
      ServiceContextScope scope, List<Runnable> checks) {
    return execute(runtime, plan, requested, scope, checks, false);
  }

  static Observation execute(RuntimeService runtime, Dataflow plan, Observation requested,
      ServiceContextScope scope, List<Runnable> checks, boolean deferSeal) {
    if (!(scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl transaction))
      throw new IllegalStateException("Selection requires a native root transaction");
    var descriptor = Objects.requireNonNull(plan.getCollectiveSelection());
    Object encoded = plan.getMetadata().get(CollectiveSelectionBinding.DATA_KEY);
    if (encoded == null) throw new IllegalArgumentException("Selection plan has no semantic admission");
    var binding = Utils.Json.parseObject(encoded.toString(), CollectiveSelectionBinding.class);
    Runnable generation = () -> require(binding, requested, scope);
    generation.run();
    checks.add(generation);
    // Capture the earliest durable member payload before a query or classifier can install
    // a transaction-local canonical copy. Final sealing must never bless a newer baseline.
    runtime.captureSelectionBaseline(descriptor.nativeMembers(), scope);
    if (scope.getObserver() != null) runtime.captureSelectionBaseline(scope.getObserver().getObservable(), scope);
    if (!Objects.equals(descriptor.requested().getUrn(), binding.requested())
        || !Objects.equals(descriptor.nativeMembers().getUrn(), binding.nativeMembers())
        || !descriptor.predicates().stream().map(Concept::getUrn).toList().equals(binding.predicates())
        || !descriptor.families().stream().map(Concept::getUrn).toList().equals(binding.families()))
      throw new IllegalArgumentException("Selection descriptor differs from its admission");
    var operations = new LinkedHashSet<>(descriptor.classificationOperations());
    if (operations.size() != binding.families().size())
      throw new IllegalArgumentException("Selection requires one operation per family");
    var seen = new ArrayList<String>();
    boolean updatesStarted = false;
    for (var root : plan.getComputation()) {
      generation.run();
      if (root.getActuatorType() != Actuator.Type.UPDATE) {
        if (updatesStarted || root.getObservation() == null
            || !Objects.equals(root.getObservation().getObservable().getSemantics(), descriptor.nativeMembers().getSemantics()))
          throw new IllegalArgumentException("Selection native discovery must precede family operations");
        var fragment = root.getCoverage() == null
            ? root.getObservation().getGeometry() : root.getCoverage();
        if (fragment == null || !RuntimeService.sameSupportDimensions(requested.getGeometry(), fragment)
            || RuntimeService.coverage(fragment, RuntimeService.intersection(fragment, requested.getGeometry())) < 1.0 - 1.0e-9)
          throw new IllegalArgumentException("Selection discovery fragment exceeds the requested support");
        // Another selected input may already have completed this same native domain in the
        // parent attempt. Recheck the exact fragment before executing a stale discovery plan.
        var fragmentRequest = new Observation.NaiveBuilder(descriptor.nativeMembers(), scope).geometry(fragment).query().build();
        var existing = nativeQuery(runtime, fragmentRequest, binding, scope);
        boolean complete = !existing.isEmpty() && existing.getResolvedCoverage() >= 1.0 - 1.0e-9;
        if (!complete) {
          if (root.getActuatorType() == Actuator.Type.REFERENCE)
            throw new IllegalStateException("Selection native reference no longer covers its fragment");
          runtime.executeSelectionProducer(root, fragment, scope);
        }
      } else {
        updatesStarted = true;
        if (!operations.contains(root.getName()) || seen.contains(root.getName())
            || root.getContextualization() != Contextualization.CLASSIFICATION)
          throw new IllegalArgumentException("Unexpected selection operation");
        var index = seen.size();
        if (index >= binding.directives().size() || root.getOperationObservable() == null
            || !Objects.equals(root.getOperationObservable().getUrn(), binding.directives().get(index)))
          throw new IllegalArgumentException("Selection classifier differs from its declared family");
        seen.add(root.getName());
        var nativeView = completed(runtime, requested, binding, scope);
        var family = scope.getService(Reasoner.class).resolveConcept(binding.families().get(index));
        var currentMembers = runtime.classificationMembers(nativeView, requested.getGeometry(), scope);
        // A successful known-family no-op still consumes this completed native view. Bind
        // its references before skipping execution, so sealing never inspects a placeholder.
        bindNativeReferences(root, nativeView, binding.nativeMembers());
        // Already-known classifications are facts, including values excluded by the final filter.
        // Do not manufacture another classification activity merely to confirm that knowledge.
        if (currentMembers.stream().allMatch(member -> satisfiesFamily(member, family, scope.getService(Reasoner.class))))
          continue;
        if (Boolean.TRUE.equals(root.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY)))
          throw new IllegalStateException("Missing classifier for unknown selection family");
        var nativeSources = nativeView.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class);
        if (nativeSources.isEmpty()) throw new IllegalStateException("Selection has no native ownership witness");
        var compiled = new CompiledDataflow(runtime, identity(nativeSources.getFirst(), scope), scope, plan);
        if (!compiled.compile(root) || !compiled.store(transaction))
          throw new IllegalStateException("Selection classifier compilation failed");
        transaction.registerExecutors();
        if (!compiled.executeRoot(requested.getGeometry(), Scheduler.Event.initialization(), scope))
          throw new IllegalStateException("Selection classification failed");
      }
    }
    if (!seen.equals(descriptor.classificationOperations()))
      throw new IllegalArgumentException("Incomplete selection family plan");
    generation.run();
    var nativeView = completed(runtime, requested, binding, scope);
    // This captures durable and transaction-overlay read sets and guards them through root commit.
    var members = deferSeal ? runtime.classificationMembers(nativeView, requested.getGeometry(), scope)
        : runtime.classificationMembersForCheck(nativeView, requested.getGeometry(), scope);
    return result(runtime, requested, binding, nativeView, members, scope, !deferSeal, plan);
  }

  static Observation seal(RuntimeService runtime, Dataflow plan, Observation requested, ServiceContextScope scope) {
    var binding = Utils.Json.parseObject(plan.getMetadata().get(CollectiveSelectionBinding.DATA_KEY).toString(), CollectiveSelectionBinding.class);
    require(binding, requested, scope);
    var nativeView = completed(runtime, requested, binding, scope);
    var members = runtime.classificationMembersForCheck(nativeView, requested.getGeometry(), scope);
    return result(runtime, requested, binding, nativeView, members, scope, true, plan);
  }

  private static void bindNativeReferences(Actuator node, Observation completed, String nativeUrn) {
    if (Boolean.TRUE.equals(node.getData().get(Actuator.COMPLETE_COHORT_REFERENCE))) {
      if (node.getActuatorType() != Actuator.Type.REFERENCE || node.getObservation() == null
          || !Objects.equals(node.getObservation().getObservable().getUrn(), nativeUrn))
        throw new IllegalArgumentException("Invalid native selection reference");
      ((ActuatorImpl) node).setObservation(completed);
      ((ActuatorImpl) node).setId(Observation.QUERY_ID);
    }
    for (var child : node.getChildren()) bindNativeReferences(child, completed, nativeUrn);
  }

  static void require(CollectiveSelectionBinding binding, Observation requested, ServiceContextScope scope) {
    binding.require(scope, requested.getObservable(), requested.getGeometry());
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new java.util.concurrent.CancellationException("Selection cancelled");
    var current = CollectiveSelectionBinding.capture(requested, scope, scope.getService(Reasoner.class));
    if (!Objects.equals(binding, current)) throw new IllegalStateException("Selection semantic generation or family changed");
  }

  private static Observation completed(RuntimeService runtime, Observation request,
      CollectiveSelectionBinding binding, ServiceContextScope scope) {
    var completed = nativeQuery(runtime, request, binding, scope);
    if (completed.isEmpty() || completed.getResolvedCoverage() < 1.0 - 1.0e-9)
      throw new IllegalStateException("Selection native discovery is incomplete or has no current observer proof; inner="
          + Utils.Json.asString(completed.getNotifications()));
    return completed;
  }

  private static Observation nativeQuery(RuntimeService runtime, Observation request,
      CollectiveSelectionBinding binding, ServiceContextScope scope) {
    var nativeObservable = scope.getService(Reasoner.class).resolveObservable(binding.nativeMembers());
    if (nativeObservable == null) throw new IllegalStateException("Selection native meaning unavailable");
    var query = new Observation.NaiveBuilder(nativeObservable, scope).geometry(request.getGeometry()).query().build();
    return runtime.queryCollective(query, scope, null, null, binding);
  }

  /** Read-only satisfaction/replay; no discovery, UPDATE or provenance activity is synthesized. */
  static Observation query(RuntimeService runtime, Observation request, ServiceContextScope scope) {
    try(var observerUse=org.integratedmodelling.klab.runtime.ObserverSemanticUse.capture(scope)) {
    CollectiveSelectionContinuation.refreshDraft(runtime, request, scope);
    long graphRevision = scope.getDigitalTwin().getKnowledgeGraph().getSemanticRevision();
    long overlayRevision = scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl tx
        ? tx.classificationMutationStamp() : -1;
    var reasoner = scope.getService(Reasoner.class);
    var binding = CollectiveSelectionBinding.capture(request, scope, reasoner);
    if (binding == null) return Observation.empty();
    require(binding, request, scope);
    Observation nativeView;
    try { nativeView = completed(runtime, request, binding, scope); }
    catch (IllegalStateException incomplete) {
      if (request.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY)) throw incomplete;
      return Observation.empty(Notification.info(incomplete.getMessage()));
    }
    var members = runtime.classificationMembers(nativeView, request.getGeometry(), scope);
    final Observation result;
    try { result = result(runtime, request, binding, nativeView, members, scope, false, null); }
    catch (IllegalStateException unclassified) {
      if (request.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY)) throw unclassified;
      return Observation.empty(Notification.info(unclassified.getMessage()));
    }
    var encoded = request.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    if (encoded != null) {
      var previous = Utils.Json.parseObject(encoded.toString(), CollectiveSelectionView.class);
      var actual = Utils.Json.parseObject(result.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(), CollectiveSelectionView.class);
      if (!durablyEquivalent(previous, actual)) throw new IllegalStateException("Selection view sources or member state changed; submit again");
    }
    require(binding, request, scope);
    if (graphRevision != scope.getDigitalTwin().getKnowledgeGraph().getSemanticRevision()
        || scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl tx
            && overlayRevision != tx.classificationMutationStamp())
      throw new IllegalStateException("Selection graph changed during inspection");
    return result;
    }
  }

  /** Historical generation counters are audit data; every durable semantic/native guard remains. */
  static boolean durablyEquivalent(CollectiveSelectionView previous, CollectiveSelectionView actual) {
    return previous.version() == 3 && actual.version() == 3
        && previous.semanticEvidence() != null && actual.semanticEvidence() != null
        && previous.semanticEvidence().complete() && actual.semanticEvidence().complete()
        && Objects.equals(previous.semanticEvidence().obligations(), actual.semanticEvidence().obligations())
        && SemanticExecutionEvidence.sameProof(previous.semanticEvidence().witness(), actual.semanticEvidence().witness())
        && Objects.equals(previous.semanticFingerprint(), actual.semanticFingerprint())
        && Objects.equals(previous.requested(), actual.requested())
        && Objects.equals(previous.nativeMembers(), actual.nativeMembers())
        && Objects.equals(previous.predicates(), actual.predicates())
        && Objects.equals(previous.families(), actual.families())
        && Objects.equals(previous.reasonerId(), actual.reasonerId())
        && previous.observerId() == actual.observerId()
        && Objects.equals(previous.observerUrn(), actual.observerUrn())
        && Objects.equals(previous.observerClass(), actual.observerClass())
        && Objects.equals(previous.geometry(), actual.geometry())
        && previous.cohortId() == actual.cohortId()
        && Objects.equals(previous.sourceIds(), actual.sourceIds())
        && Objects.equals(previous.members(), actual.members())
        && Objects.equals(previous.selectedIds(), actual.selectedIds())
        && Objects.equals(previous.scopeId(), actual.scopeId())
        && Objects.equals(previous.context(), actual.context());
  }

  private record MemberWitness(Observation identity, String observable, String geometry) {
    CollectiveSelectionView.Member snapshot() { return new CollectiveSelectionView.Member(identity.getId(), observable, geometry); }
  }

  private static Observation result(RuntimeService runtime, Observation requested,
      CollectiveSelectionBinding binding, Observation nativeView, List<Observation> members,
      ServiceContextScope scope, boolean transactional, Dataflow plan) {
    var reasoner = scope.getService(Reasoner.class);
    var predicates = binding.predicates().stream().map(reasoner::resolveConcept).toList();
    var families = binding.families().stream().map(reasoner::resolveConcept).toList();
    var selected = new ArrayList<Observation>();
    var witnesses = new ArrayList<MemberWitness>();
    var ids = new HashSet<Long>();
    for (var member : members) {
      if (!ids.add(member.getId())) throw new IllegalStateException("Duplicate native selection member");
      var attributions = new ArrayList<Concept>(reasoner.directTraits(member.getObservable()));
      attributions.addAll(reasoner.directRoles(member.getObservable()));
      for (var family : families)
        if (!satisfiesFamily(member, family, reasoner))
          throw new IllegalStateException("Selection has unclassified native members");
      var identity = identity(member.getId(), scope);
      witnesses.add(new MemberWitness(identity, member.getObservable().getUrn(), member.getGeometry().encode()));
      if (predicates.stream().allMatch(predicate -> predicate != null
          && attributions.stream().anyMatch(p -> valid(p, reasoner) && reasoner.is(p, predicate)))) selected.add(identity);
    }
    var cohort = runtime.getCohortFor(nativeView.getObservable().getSemantics().singular(), scope, false);
    if (cohort == null) throw new IllegalStateException("Selection native cohort disappeared");
    var sourceIds = nativeView.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class);
    if (sourceIds.isEmpty() || new HashSet<>(sourceIds).size() != sourceIds.size())
      throw new IllegalStateException("Selection requires unique native source witnesses");
    var sources = sourceIds.stream().map(id -> identity(id, scope)).toList();
    // Discovery has proved the complete requested support. Retain its portable representation:
    // intersections promote Time extents and add derived fields, which must not change replay.
    var result = RuntimeService.queryResult(requested, Geometry.create(binding.support()),
        Geometry.create(binding.support()), 1.0);
    result.setChildrenCount(selected.size());
    var semanticEvidence = SemanticExecutionEvidence.selection(plan, binding, nativeView, members, requested, scope);
    if (!semanticEvidence.complete() || !SemanticExecutionEvidence.completeWitness(semanticEvidence.witness()))
      throw new IllegalStateException("Selection semantic completeness requires successful normal revalidation; no exhaustive result was published; diagnostic="
          + SemanticExecutionEvidence.failureDiagnostic(semanticEvidence,nativeView,members,scope));
    Runnable receipt = () -> {
      var proof = new CollectiveSelectionView(3, binding.requested(), binding.nativeMembers(),
          binding.predicates(), binding.families(), binding.reasonerId(), binding.revision(),
          binding.observerId() == null ? 0 : binding.observerId(), binding.observerUrn(), binding.support(),
          cohort.getId(), sources.stream().map(Observation::getId).sorted().toList(),
          witnesses.stream().map(w -> new CollectiveSelectionView.Member(durableId(w.identity(), scope), w.observable(), w.geometry()))
              .sorted(Comparator.comparingLong(CollectiveSelectionView.Member::id)).toList(),
          selected.stream().map(o -> durableId(o, scope)).sorted().toList(), scope.getId(), projectedContext(scope), binding.observerClass(), binding.semanticFingerprint(), semanticEvidence);
      result.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(proof));
      result.getMetadata().put(Metadata.IM_QUERY_COHORT_ID, cohort.getId());
      result.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, proof.sourceIds());
    };
    receipt.run();
    SemanticExecutionEvidence.issue(result, semanticEvidence, scope);
    for (var witness : witnesses) {
      var actual = scope.getCurrentTransaction() == null ? identity(witness.identity().getId(), scope)
          : scope.getCurrentTransaction().readObservation(witness.identity());
      if (!Objects.equals(witness.observable(), actual.getObservable().getUrn())
          || !Objects.equals(witness.geometry(), actual.getGeometry().encode()))
        throw new IllegalStateException("Selection member changed during inspection");
    }
    if (transactional) scope.getCurrentTransaction().beforeCommit(receipt);
    return result;
  }

  private static long durableId(Observation observation, ServiceContextScope scope) {
    return scope.getCurrentTransaction() == null ? observation.getId()
        : scope.getCurrentTransaction().readObservation(observation).getId();
  }

  /** Publication follows this attempt's own attributed focus, while admission uses its baseline. */
  static CollectiveSelectionBinding.Context projectedContext(ServiceContextScope scope) {
    var focus = scope.getContextObservation();
    if (focus == null) return null;
    if (scope.getCurrentTransaction() != null) focus = scope.getCurrentTransaction().readObservation(focus);
    return new CollectiveSelectionBinding.Context(focus.getId(), focus.getId() > 0 ? 0 : focus.getTransientId(),
        org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.logicalUrn(focus.getUrn()),
        focus.getObservable().getSemantics().getUrn(), Geometry.forTransport(focus.getGeometry()).encode());
  }

  private static boolean valid(Concept predicate, Reasoner reasoner) {
    return predicate != null && predicate.is(SemanticType.PREDICATE)
        && !predicate.isAbstract() && !predicate.is(SemanticType.ABSTRACT)
        && !predicate.is(SemanticType.NOTHING) && reasoner.satisfiable(predicate);
  }

  private static boolean satisfiesFamily(Observation member, Concept family, Reasoner reasoner) {
    if (family == null) return false;
    var attributions = new ArrayList<Concept>(reasoner.directTraits(member.getObservable()));
    attributions.addAll(reasoner.directRoles(member.getObservable()));
    boolean known = false;
    for (var predicate : attributions) {
      if (predicate == null) throw new IllegalStateException("Invalid native predicate");
      if (!reasoner.is(predicate, family)) continue;
      if (!valid(predicate, reasoner)) throw new IllegalStateException("Invalid native attribution in selection family");
      known = true;
    }
    return known;
  }

  private static Observation identity(long id, ServiceContextScope scope) {
    for (var transaction = scope.getCurrentTransaction(); transaction != null; transaction = transaction.getParent())
      for (var asset : transaction.assets())
        if (asset instanceof Observation observation && observation.getId() == id) return observation;
    var observation = scope.getObservation(id);
    if (observation == null || observation.isEmpty()) throw new IllegalStateException("Selection identity unavailable: " + id);
    return observation;
  }
}
