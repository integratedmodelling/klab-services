package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.utilities.Utils;

/** Committed, read-only identity/semantic assertions; snapshots do not retain mutable observations. */
public final class ObservationInspector {
  private ObservationInspector() {}

  public record MemberState(String urn, String observable, String semantics, String geometry, List<String> attributions) {
    public MemberState { attributions = List.copyOf(attributions); }
  }

  public record MemberSnapshot(String context, long producer, long cohort,
      Map<Long, MemberState> members, Set<Long> classificationActivities) {
    public MemberSnapshot {
      members = Map.copyOf(members); classificationActivities = Set.copyOf(classificationActivities);
    }
  }

  /** Immutable committed native graph state. Empty member sets remain meaningful evidence. */
  public record NativeCohort(String urn, String semantics, String geometry,
      Set<Long> members, Set<Long> contributors) {
    public NativeCohort { members = Set.copyOf(members); contributors = Set.copyOf(contributors); }
  }

  public record NativeSnapshot(String context, Map<Long, MemberState> observations,
      Map<Long, NativeCohort> cohorts, Map<Long, String> contextualizations) {
    public NativeSnapshot {
      observations = Map.copyOf(observations); cohorts = Map.copyOf(cohorts);
      contextualizations = Map.copyOf(contextualizations);
    }
  }

  /** Capture native state, not query receipts, before a no-work or rollback assertion. */
  public static NativeSnapshot nativeSnapshot(ContextScope scope) {
    var graph = graph(scope);
    var observations = new TreeMap<Long, MemberState>();
    for (var reference : graph.query(Observation.class, scope).run(scope)) {
      var observation = graph.getAsset(reference.getId(), scope, Observation.class);
      if (observation == null || observation.getId() <= 0 || observation.getId() != reference.getId()
          || observation.getObservable() == null || observation.getObservable().getSemantics() == null
          || observations.containsKey(observation.getId()))
        throw new IllegalStateException("Invalid or duplicate persisted native observation");
      observations.put(observation.getId(), new MemberState(observation.getUrn(),
          observation.getObservable().getUrn(), observation.getObservable().getSemantics().getUrn(),
          observation.getGeometry() == null ? null : observation.getGeometry().encode(), attributions(scope, observation)));
    }
    var cohorts = new TreeMap<Long, NativeCohort>();
    for (var cohort : graph.query(Cohort.class, scope).run(scope)) {
      if (cohort.getId() <= 0 || cohort.getObservable() == null || cohort.getObservable().getSemantics() == null
          || cohorts.containsKey(cohort.getId())) throw new IllegalStateException("Invalid or duplicate native cohort");
      var memberIds = new TreeSet<Long>();
      for (var member : members(scope, cohort)) {
        if (!observations.containsKey(member.getId()) || !memberIds.add(member.getId()))
          throw new IllegalStateException("Native cohort contains missing or duplicate members");
      }
      var contributorIds = new TreeSet<Long>();
      for (var contributor : graph.query(Observation.class, scope).target(cohort)
          .along(GraphModel.Relationship.CONTRIBUTED_TO).run(scope)) {
        if (!observations.containsKey(contributor.getId()) || !contributorIds.add(contributor.getId()))
          throw new IllegalStateException("Native cohort contains missing or duplicate contributors");
      }
      cohorts.put(cohort.getId(), new NativeCohort(cohort.getUrn(), cohort.getObservable().getSemantics().getUrn(),
          cohort.getGeometry() == null ? null : cohort.getGeometry().encode(), memberIds, contributorIds));
    }
    var work = new TreeMap<Long, String>();
    for (var activity : graph.query(Activity.class, scope).run(scope)) {
      if (activity.getType() != null && activity.getType().isContextualization()) {
        if (activity.getId() <= 0 || work.putIfAbsent(activity.getId(), activity.getType() + ":" + activity.getOutcome()
            + ":" + activity.getSchedulerTime()) != null)
          throw new IllegalStateException("Invalid or duplicate contextualization activity");
      }
    }
    return new NativeSnapshot(scope.getId(), observations, cohorts, work);
  }

  /** Bookkeeping submissions/resolutions may differ; native assets and contextualization may not. */
  public static boolean nativeUnchanged(ContextScope scope, NativeSnapshot before) {
    if (before == null || !Objects.equals(before.context(), scope.getId()))
      throw new IllegalArgumentException("Native snapshots belong to their original digital twin");
    return before.equals(nativeSnapshot(scope));
  }

  /**
   * Verify a virtual selection against committed native members, not only its returned count.
   * The expected semantics is supplied by the test; the receipt is checked evidence, not an oracle.
   * A completed empty selection is valid. Missing discovery or unknown family knowledge is not.
   */
  /** Diagnostic only: preserve the assertion result and report its first failed invariant. */
  private static boolean selectionRejected(ContextScope scope, Observation view, String guard) {
    var diagnostic = org.integratedmodelling.common.runtime.SubmissionDiagnostics.summary(
        "inspector." + guard, scope, null, view, null, null);
    scope.warn("Selection inspector rejected: guard=" + guard + "; scope=" + scope.getId()
        + "; viewId=" + (view == null ? "null" : view.getId())
        + (diagnostic == null ? "" : "; diagnostic=" + diagnostic));
    return false;
  }
  public static boolean selectionCheck(ContextScope scope, Observation view, String expectedDefinition) {
    if (view == null || view.isEmpty() || view.getId() != Observation.QUERY_ID
        || view.getObservable() == null || view.getObservable().getSemantics() == null
        || !view.getObservable().getSemantics().isCollective() || view.getGeometry() == null
        || !Double.isFinite(view.getResolvedCoverage()) || Math.abs(view.getResolvedCoverage() - 1) > 1e-9)
      return selectionRejected(scope, view, "G01");
    var reasoner = scope.getService(Reasoner.class);
    var expected = concept(scope, expectedDefinition);
    if (!expected.equals(view.getObservable().getSemantics())) return selectionRejected(scope, view, "G02");
    var encoded = view.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    if (!(encoded instanceof String text)) return selectionRejected(scope, view, "G03");
    var proof = Utils.Json.parseObject(text, CollectiveSelectionView.class);
    if (!Objects.equals(proof.scopeId(), scope.getId())
        || !Objects.equals(proof.context(), CollectiveSelectionBinding.context(scope))) return selectionRejected(scope, view, "G04");
    var capabilities = reasoner.capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic) || !certified(proof, semantic, scope)
        || !Objects.equals(proof.requested(), view.getObservable().getUrn())
        || !Objects.equals(proof.geometry(), Geometry.forTransport(view.getGeometry()).encode())
        || proof.cohortId() <= 0 || proof.sourceIds().isEmpty()
        || proof.sourceIds().stream().anyMatch(id -> id == null || id <= 0)
        || new HashSet<>(proof.sourceIds()).size() != proof.sourceIds().size()
        || new HashSet<>(proof.selectedIds()).size() != proof.selectedIds().size()) return selectionRejected(scope, view, "G05");
    var expectedPredicates = new ArrayList<>(reasoner.directTraits(expected));
    expectedPredicates.addAll(reasoner.directRoles(expected));
    if (expectedPredicates.isEmpty()
        || !new HashSet<>(proof.predicates()).equals(expectedPredicates.stream().map(Concept::getUrn).collect(java.util.stream.Collectors.toSet()))
        || proof.predicates().size() != expectedPredicates.size() || proof.families().size() != expectedPredicates.size()) return selectionRejected(scope, view, "G06");
    var families = new ArrayList<Concept>();
    for (int i = 0; i < proof.predicates().size(); i++) {
      var predicate = concept(scope, proof.predicates().get(i));
      var family = reasoner.lexicalRoot(predicate);
      if (!validPredicate(predicate, reasoner) || family == null || !Objects.equals(family.getUrn(), proof.families().get(i))) return selectionRejected(scope, view, "G07");
      families.add(family);
    }
    if (new HashSet<>(proof.families()).size() != proof.families().size()) return selectionRejected(scope, view, "G08");
    var nativeObservable = reasoner.resolveObservable(proof.nativeMembers());
    if (nativeObservable == null || !nativeObservable.getSemantics().isCollective()
        || !proof.semanticEvidence().obligations().contains(expected.getUrn())
        || !proof.semanticEvidence().obligations().contains(nativeObservable.getSemantics().getUrn())
        || !proof.semanticEvidence().obligations().containsAll(proof.predicates())
        || !proof.semanticEvidence().obligations().containsAll(proof.families())) return selectionRejected(scope, view, "G09");
    var expectedNative = view.getObservable().builder(scope).without(expectedPredicates).buildObservable();
    if (expectedNative == null || !expectedNative.getSemantics().equals(nativeObservable.getSemantics())
        || !Objects.equals(expectedNative.getObserverSemantics(), nativeObservable.getObserverSemantics())) return selectionRejected(scope, view, "G10");
    var individualNative = nativeObservable.builder(scope).collective(false).buildObservable();
    var nativeType = reasoner.baseSubstantialType(expected.singular(), scope);
    var graph = graph(scope);
    var cohort = graph.getAsset(proof.cohortId(), scope, Cohort.class);
    if (cohort == null || cohort.getObservable() == null || nativeType == null
        || !cohort.getObservable().getSemantics().equals(nativeType)) return selectionRejected(scope, view, "G11");
    if (!(view.getMetadata().get(Metadata.IM_QUERY_COHORT_ID) instanceof Number cohortId)
        || cohortId.longValue() != proof.cohortId()
        || !new HashSet<>(view.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class)).equals(new HashSet<>(proof.sourceIds()))) return selectionRejected(scope, view, "G12");
    if (proof.observerId() > 0) {
      var observer = graph.getAsset(proof.observerId(), scope, Observation.class);
      if (observer == null || observer.isEmpty() || observer.getObservable() == null
          || !observer.getObservable().is(SemanticType.AGENT) || observer.getObservable().is(SemanticType.ABSTRACT)
          || observer.getObservable().getSemantics().isCollective() || !Objects.equals(observer.getUrn(), proof.observerUrn())
          || !Objects.equals(observer.getObservable().getSemantics().getUrn(), proof.observerClass())) return selectionRejected(scope, view, "G13");
    } else if (proof.observerId() != 0 || proof.observerUrn() != null || proof.observerClass() != null) return selectionRejected(scope, view, "G14");
    for (var id : proof.sourceIds()) {
      var producer = graph.getAsset(id, scope, Observation.class);
      if (producer == null || producer.isEmpty() || producer.getObservable() == null
          || !producer.getObservable().getSemantics().equals(nativeObservable.getSemantics())) return selectionRejected(scope, view, "G15");
      var contributions = graph.query(Cohort.class, scope).source(producer).along(GraphModel.Relationship.CONTRIBUTED_TO).run(scope);
      if (contributions.size() != 1 || contributions.getFirst().getId() != proof.cohortId()) return selectionRejected(scope, view, "G16");
      boolean completed = false;
      for (var activity : graph.query(Activity.class, scope).target(producer).along(GraphModel.Relationship.INSTANTIATED).run(scope)) {
        var observer = activity.getMetadata().get(Metadata.IM_OBSERVER_SELECTION_ID);
        var support = activity.getMetadata().get(Metadata.IM_EXECUTION_GEOMETRY);
        var encodedContext = activity.getMetadata().get("collectiveNativeContext");
        var activityContext = encodedContext == null ? null : Utils.Json.parseObject(encodedContext.toString(), CollectiveSelectionBinding.Context.class);
        completed |= activity.getId() > 0 && activity.getType() == Activity.Type.INSTANTIATION
            && activity.getOutcome() == Activity.Outcome.SUCCESS
            && Objects.equals(activity.getMetadata().get("collectiveSemanticFingerprint"), proof.semanticFingerprint())
            && sourceEvidence(activity,proof,semantic,scope)
            && Objects.equals(activity.getMetadata().get("collectiveReasonerId"), proof.reasonerId())
            && observer instanceof Number selected && selected.longValue() == proof.observerId()
            && Objects.equals(activity.getMetadata().get("collectiveObserverClass"), proof.observerClass())
            && Objects.equals(activityContext, proof.context())
            && support instanceof String geometry && overlaps(Geometry.create(geometry), view.getGeometry());
      }
      if (!completed) return selectionRejected(scope, view, "G17");
    }
    var states = new TreeMap<Long, CollectiveSelectionView.Member>();
    var selected = new TreeSet<Long>();
    for (var reference : members(scope, cohort)) {
      var member = graph.getAsset(reference.getId(), scope, Observation.class);
      if (member == null || member.getId() <= 0 || member.isEmpty() || member.getObservable() == null
          || member.getGeometry() == null || member.getObservable().getSemantics().isCollective()) return selectionRejected(scope, view, "G18");
      if (!overlaps(member.getGeometry(), view.getGeometry())) continue;
      if(!proof.semanticEvidence().obligations().contains(member.getObservable().getSemantics().getUrn()))return selectionRejected(scope, view, "G19");
      if (!is(member.getObservable().getSemantics(), individualNative.getSemantics(), reasoner)
          || org.integratedmodelling.klab.runtime.ObserverResolution.preference(member.getObservable(), individualNative, scope) < 0) continue;
      if (states.putIfAbsent(member.getId(), new CollectiveSelectionView.Member(member.getId(),
          member.getObservable().getUrn(), member.getGeometry().encode())) != null) return selectionRejected(scope, view, "G20");
      var attributes = new ArrayList<>(reasoner.directTraits(member.getObservable())); attributes.addAll(reasoner.directRoles(member.getObservable()));
      for (var family : families)
        if (attributes.stream().noneMatch(p -> validPredicate(p, reasoner) && is(p, family, reasoner))) return selectionRejected(scope, view, "G21");
      if (expectedPredicates.stream().allMatch(p -> attributes.stream().anyMatch(a -> validPredicate(a, reasoner) && is(a, p, reasoner)))) selected.add(member.getId());
    }
    var recorded = new TreeMap<Long, CollectiveSelectionView.Member>();
    for (var member : proof.members()) if (member.id() <= 0 || recorded.putIfAbsent(member.id(), member) != null) return selectionRejected(scope, view, "G22");
    if (!states.equals(recorded) || !selected.equals(new TreeSet<>(proof.selectedIds()))
        || view.getChildrenCount() != selected.size()) return selectionRejected(scope, view, "G23");
    var actualMembers = scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).getMembers(view, scope);
    var actualIds = new TreeSet<Long>();
    for (var member : actualMembers) {
      var state = states.get(member.getId());
      if (state == null || !actualIds.add(member.getId()) || member.getObservable() == null
          || !Objects.equals(state.observable(), member.getObservable().getUrn())
          || member.getGeometry() == null || !Objects.equals(state.geometry(), member.getGeometry().encode())) return selectionRejected(scope, view, "G24");
    }
    if (!actualIds.equals(selected) || actualMembers.size() != selected.size()) return selectionRejected(scope, view, "G25");
    // The native producer is real; the predicate-qualified result must remain a virtual view.
    return graph.query(Observation.class, scope).run(scope).stream().noneMatch(o -> o.getId() > 0
        && o.getObservable() != null && expected.equals(o.getObservable().getSemantics()));
  }

  private static boolean validPredicate(Concept value, Reasoner reasoner) {
    return value != null && value.is(SemanticType.PREDICATE) && !value.is(SemanticType.ABSTRACT)
        && !value.isAbstract() && !value.is(SemanticType.NOTHING) && reasoner.satisfiable(value);
  }

  /** Round-trip an actual transport observation, then use the normal read-only query path. */
  public static boolean selectionReplay(ContextScope scope, Observation view) {
    if (view == null || view.isEmpty() || view.getId() != Observation.QUERY_ID
        || !(view.getMetadata().get(CollectiveSelectionView.DATA_KEY) instanceof String encoded)) return false;
    var original = Utils.Json.parseObject(encoded, CollectiveSelectionView.class);
    if (!Objects.equals(original.scopeId(), scope.getId())
        || !Objects.equals(original.context(), CollectiveSelectionBinding.context(scope))) return false;
    var capabilities = scope.getService(Reasoner.class).capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic) || !certified(original, semantic, scope)) return false;
    var observer = scope.getObserver();
    if (original.observerId() != (observer == null ? 0 : observer.getId())
        || !Objects.equals(original.observerUrn(), observer == null ? null : observer.getUrn())
        || !Objects.equals(original.observerClass(), observer == null || observer.getObservable() == null
            || observer.getObservable().getSemantics() == null ? null : observer.getObservable().getSemantics().getUrn())) return false;
    var before = nativeSnapshot(scope);
    var portable = Utils.Json.parseObject(Utils.Json.asString(Observation.forTransport(view)), Observation.class);
    var runtime = scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    // QUERY_ID remains an observation request; Runtime validates the receipt without execution.
    var checked = runtime.submit(portable, scope).join();
    if (checked == null || checked.isEmpty() || checked.getId() != Observation.QUERY_ID
        || checked.getObservable() == null || checked.getGeometry() == null
        || !Double.isFinite(checked.getResolvedCoverage()) || Math.abs(checked.getResolvedCoverage() - 1) > 1e-9
        || !(checked.getMetadata().get(CollectiveSelectionView.DATA_KEY) instanceof String result)) return false;
    var restored = Utils.Json.parseObject(result, CollectiveSelectionView.class);
    if (!sameDurableReceipt(original, restored)
        || !Objects.equals(original.requested(), checked.getObservable().getUrn())
        || !Objects.equals(portable.getObservable().getSemantics(), checked.getObservable().getSemantics())
        || !Objects.equals(original.geometry(), Geometry.forTransport(checked.getGeometry()).encode())
        || new HashSet<>(restored.sourceIds()).size() != restored.sourceIds().size()
        || new HashSet<>(restored.selectedIds()).size() != restored.selectedIds().size()
        || checked.getChildrenCount() != restored.selectedIds().size()
        || !(checked.getMetadata().get(Metadata.IM_QUERY_COHORT_ID) instanceof Number cohort)
        || cohort.longValue() != restored.cohortId()
        || !new HashSet<>(checked.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class))
            .equals(new HashSet<>(restored.sourceIds()))) return false;
    var members = runtime.getMembers(checked, scope);
    if (members == null || members.stream().anyMatch(member -> member == null || member.getId() <= 0)) return false;
    var ids = members.stream().map(Observation::getId).toList();
    return new HashSet<>(ids).size() == ids.size()
        && new HashSet<>(ids).equals(new HashSet<>(restored.selectedIds()))
        && nativeUnchanged(scope, before);
  }

  /** Historical numeric generations are process-local audit fields, never durable content IDs. */
  private static boolean certified(CollectiveSelectionView proof,Reasoner.Capabilities semantic,ContextScope scope) {
    return proof.version()==3 && SemanticContentFingerprint.scoped(proof.semanticFingerprint())
        && Objects.equals(proof.reasonerId(),semantic.getServiceId())
        && Objects.equals(proof.semanticFingerprint(),semantic.getSemanticContentFingerprint())
        && certifiedEvidence(proof.semanticEvidence(),semantic,scope);
  }

  private static boolean certifiedEvidence(SemanticEvidenceBinding evidence,Reasoner.Capabilities semantic,ContextScope scope) {
    if(evidence==null || !evidence.complete() || evidence.witness()==null
        || !SemanticEvidenceBinding.VERSION.equals(evidence.contractVersion()))return false;
    var request=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
        semantic.getServiceId(),semantic.getKnowledgeRevision(),semantic.getSemanticContentFingerprint(),evidence.obligations());
    if(!Objects.equals(request.inputHash(),evidence.witness().inputHash()))return false;
    var current=scope.getService(Reasoner.class).semanticDependencies(request,scope);
    return org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness
        .revalidates(evidence.witness(),current);
  }

  private static boolean sourceEvidence(Activity activity,CollectiveSelectionView proof,
      Reasoner.Capabilities semantic,ContextScope scope) {
    var encoded=activity.getMetadata().get(SemanticEvidenceBinding.DATA_KEY);
    if(!(encoded instanceof String text) || proof.semanticEvidence()==null)return false;
    var source=Utils.Json.parseObject(text,SemanticEvidenceBinding.class);
    return proof.semanticEvidence().obligations().containsAll(source.obligations())
        && certifiedEvidence(source,semantic,scope);
  }

  private static boolean sameDurableReceipt(CollectiveSelectionView before, CollectiveSelectionView after) {
    return before.version() == 3 && after.version() == 3
        && SemanticContentFingerprint.scoped(before.semanticFingerprint())
        && before.semanticEvidence()!=null && after.semanticEvidence()!=null
        && before.semanticEvidence().complete() && after.semanticEvidence().complete()
        && Objects.equals(before.semanticEvidence().obligations(),after.semanticEvidence().obligations())
        && org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.revalidates(
            before.semanticEvidence().witness(),after.semanticEvidence().witness())
        && Objects.equals(before.semanticFingerprint(), after.semanticFingerprint())
        && Objects.equals(before.requested(), after.requested())
        && Objects.equals(before.nativeMembers(), after.nativeMembers())
        && Objects.equals(before.predicates(), after.predicates())
        && Objects.equals(before.families(), after.families())
        && Objects.equals(before.reasonerId(), after.reasonerId())
        && before.observerId() == after.observerId()
        && Objects.equals(before.observerUrn(), after.observerUrn())
        && Objects.equals(before.geometry(), after.geometry())
        && before.cohortId() == after.cohortId()
        && Objects.equals(before.sourceIds(), after.sourceIds())
        && Objects.equals(before.members(), after.members())
        && Objects.equals(before.selectedIds(), after.selectedIds())
        && Objects.equals(before.scopeId(), after.scopeId())
        && Objects.equals(before.context(), after.context())
        && Objects.equals(before.observerClass(), after.observerClass());
  }

  /** One numeric cell, explicitly in double precision; use celltext for exact long assertions. */
  static boolean numericCell(String text, double expected, double tolerance) {
    if (!Double.isFinite(expected) || !Double.isFinite(tolerance) || tolerance < 0)
      throw new IllegalArgumentException("Numeric cell checks require a finite expectation and nonnegative finite tolerance");
    if (text == null) return false;
    final double actual;
    try { actual = Double.parseDouble(text); }
    catch (NumberFormatException missing) { return false; }
    return Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance;
  }

  /** Recover the actual model-local collective input receipt from the committed consumer. */
  public static Observation selectionInput(ContextScope scope, Observation consumer, String alias) {
    if (consumer == null || consumer.getId() <= 0 || consumer.isEmpty() || alias == null || alias.isBlank())
      throw new IllegalArgumentException("Selection input checks require a committed consumer and local alias");
    var canonical = graph(scope).getAsset(consumer.getId(), scope, Observation.class);
    if (canonical == null || canonical.isEmpty() || canonical.getObservable() == null
        || consumer.getObservable() == null || !canonical.getObservable().getSemantics().equals(consumer.getObservable().getSemantics()))
      throw new IllegalArgumentException("Selection consumer is unavailable or changed");
    var inputs = canonical.getMetadata().get(CollectiveSelectionView.INPUTS_KEY);
    if (!(inputs instanceof String serialized))
      throw new IllegalArgumentException("Consumer selection inputs require persisted JSON metadata");
    inputs = Utils.Json.parseObject(serialized, Map.class);
    if (!(inputs instanceof Map<?, ?> byAlias) || !(byAlias.get(alias) instanceof String text))
      throw new IllegalArgumentException("Consumer has no captured selection input for " + alias);
    var proof = Utils.Json.parseObject(text, CollectiveSelectionView.class);
    if (!Objects.equals(proof.scopeId(), scope.getId())
        || !Objects.equals(proof.context(), CollectiveSelectionBinding.context(scope))
        || new HashSet<>(proof.selectedIds()).size() != proof.selectedIds().size())
      throw new IllegalStateException("Selection input receipt belongs to a different scope or has duplicate members");
    var observable = scope.getService(Reasoner.class).resolveObservable(proof.requested());
    if (observable == null || !observable.getSemantics().isCollective())
      throw new IllegalStateException("Selection input meaning is unavailable");
    var view = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    view.setId(Observation.QUERY_ID);view.setObservable(observable);view.setGeometry(Geometry.create(proof.geometry()));
    view.setName(alias);view.setChildrenCount(proof.selectedIds().size());view.setResolvedCoverage(1);
    view.getMetadata().put(CollectiveSelectionView.DATA_KEY, text);
    view.getMetadata().put(Metadata.IM_QUERY_COHORT_ID, proof.cohortId());
    view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, proof.sourceIds());
    var actual = scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).getMembers(view, scope);
    var ids = actual.stream().map(Observation::getId).toList();
    if (new HashSet<>(ids).size() != ids.size() || !new HashSet<>(ids).equals(new HashSet<>(proof.selectedIds())))
      throw new IllegalStateException("Selection input enumeration disagrees with its captured receipt");
    return view;
  }

  private static boolean overlaps(org.integratedmodelling.klab.api.geometry.Geometry first,
      org.integratedmodelling.klab.api.geometry.Geometry second) {
    var geometry = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.getIntersection(first, second,
        org.integratedmodelling.klab.api.geometry.Geometry.class);
    if (geometry == null || geometry.isEmpty() || geometry.size() == 0) return false;
    var scale = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry);
    return scale != null && !scale.isEmpty() && scale.size() > 0
        && scale.getExtents().stream().noneMatch(extent -> extent.isEmpty());
  }

  private static KnowledgeGraph graph(ContextScope scope) {
    return Objects.requireNonNull(scope.getDigitalTwin(), "Missing digital twin").getKnowledgeGraph();
  }

  /**
   * Checks recorded selection, not the scope's current default. A null expected observer requires
   * explicit known-null provenance. Positive selections also require successful submission and
   * resolution of that exact Agent; this makes no claim about computational initialization.
   */
  public static boolean observerCheck(ContextScope scope, Observation observation, Observation expected) {
    if (observation == null || observation.getId() <= 0)
      throw new IllegalArgumentException("Expected a persisted observation");
    var graph = graph(scope);
    var committed = graph.getAsset(observation.getId(), scope, Observation.class);
    if (committed == null || committed.getId() != observation.getId() || committed.isEmpty()
        || observation.getObservable() == null || observation.getObservable().getSemantics() == null
        || committed.getObservable() == null
        || !observation.getObservable().getSemantics().equals(committed.getObservable().getSemantics())) return false;
    var observedCreators = graph.query(Activity.class, scope).target(committed)
        .along(GraphModel.Relationship.CREATED).run(scope);
    if (observedCreators.isEmpty()) return false;
    var observedCreatorIds = new HashSet<Long>();
    for (var creator : observedCreators)
      if (creator.getId() <= 0 || !observedCreatorIds.add(creator.getId())
          || creator.getOutcome() != Activity.Outcome.SUCCESS) return false;
    var selection = org.integratedmodelling.common.runtime.ObserverProvenance.recover(scope, committed);
    if (!selection.known()) return false;
    if (expected == null) return selection.observer() == null;
    var observable = expected.getObservable();
    if (expected.getId() <= 0 || observable == null || observable.getSemantics() == null
        || !observable.is(SemanticType.AGENT) || observable.is(SemanticType.ABSTRACT)
        || observable.getSemantics().isCollective())
      throw new IllegalArgumentException("Expected a persisted concrete singular Agent");
    var actual = selection.observer();
    var canonical = graph.getAsset(expected.getId(), scope, Observation.class);
    if (actual == null || actual.getId() != expected.getId() || canonical == null || canonical.isEmpty()
        || canonical.getObservable() == null || !canonical.getObservable().is(SemanticType.AGENT)
        || canonical.getObservable().is(SemanticType.ABSTRACT)
        || canonical.getObservable().getSemantics() == null
        || canonical.getObservable().getSemantics().isCollective()
        || !observable.getSemantics().equals(canonical.getObservable().getSemantics())
        || actual.getObservable() == null
        || !observable.getSemantics().equals(actual.getObservable().getSemantics())) return false;
    boolean submitted = false, resolved = false;
    var ids = new HashSet<Long>();
    for (var creator : graph.query(Activity.class, scope).target(canonical)
        .along(GraphModel.Relationship.CREATED).run(scope)) {
      if (creator.getId() <= 0 || !ids.add(creator.getId())
          || creator.getOutcome() != Activity.Outcome.SUCCESS) return false;
      submitted |= creator.getType() == Activity.Type.SUBMISSION;
      resolved |= creator.getType() == Activity.Type.RESOLUTION;
    }
    return submitted && resolved;
  }

  private static boolean is(Concept concept, Concept expected, Reasoner reasoner) {
    return concept != null && expected != null && (concept.equals(expected) || reasoner.is(concept, expected));
  }

  private static Concept concept(ContextScope scope, String definition) {
    var result = scope.getService(Reasoner.class).resolveConcept(definition);
    if (result == null) throw new IllegalArgumentException("Unresolved inspector concept: " + definition);
    return result;
  }

  private static Cohort cohort(ContextScope scope, Observation producer) {
    if (producer == null || producer.getId() <= 0 || producer.isEmpty()
        || !producer.getObservable().getSemantics().isCollective())
      throw new IllegalArgumentException("Expected a committed collective producer");
    var cohorts = graph(scope).query(Cohort.class, scope).source(producer)
        .along(GraphModel.Relationship.CONTRIBUTED_TO).run(scope);
    if (cohorts.size() != 1 || cohorts.getFirst().getId() <= 0)
      throw new IllegalStateException("Collective producer must contribute to exactly one committed cohort");
    return cohorts.getFirst();
  }

  private static List<Observation> members(ContextScope scope, Cohort cohort) {
    return graph(scope).query(Observation.class, scope).source(cohort)
        .along(GraphModel.Relationship.HAS_MEMBER).run(scope);
  }

  private static List<String> attributions(ContextScope scope, Observation member) {
    var result = new ArrayList<String>();
    for (var link : graph(scope).getLinks(member, GraphModel.Relationship.Direction.INCOMING,
        scope, GraphModel.Relationship.CLASSIFIED)) {
      var properties = new TreeMap<String, Object>(); properties.putAll(link.properties());
      result.add(link.source().getId() + ":" + Utils.Json.asString(properties));
    }
    Collections.sort(result); return List.copyOf(result);
  }

  public static MemberSnapshot snapshot(ContextScope scope, Observation producer) {
    var cohort = cohort(scope, producer);
    var states = new TreeMap<Long, MemberState>();
    for (var member : members(scope, cohort)) {
      if (member.getId() <= 0 || member.isEmpty() || member.getGeometry() == null
          || member.getGeometry().isEmpty() || member.getObservable().getSemantics().isCollective()
          || states.containsKey(member.getId()))
        throw new IllegalStateException("Invalid or duplicate committed cohort member");
      states.put(member.getId(), new MemberState(member.getUrn(), member.getObservable().getUrn(), member.getObservable().getSemantics().getUrn(),
          member.getGeometry().encode(), attributions(scope, member)));
    }
    if (states.isEmpty()) throw new IllegalStateException("A member snapshot must not be empty");
    var activities = new HashSet<Long>();
    for (var activity : graph(scope).query(Activity.class, scope).run(scope))
      if (activity.getType() == Activity.Type.CLASSIFICATION && activity.getId() > 0) activities.add(activity.getId());
    return new MemberSnapshot(scope.getId(), producer.getId(), cohort.getId(), states, activities);
  }

  private static MemberSnapshot current(ContextScope scope, MemberSnapshot before) {
    if (!scope.getId().equals(before.context())) throw new IllegalArgumentException("Snapshot belongs to another context");
    var producer = graph(scope).getAsset(before.producer(), scope, Observation.class);
    var result = snapshot(scope, producer);
    if (result.cohort() != before.cohort()) throw new IllegalStateException("Producer cohort changed");
    return result;
  }

  private static List<Concept> predicates(Concept semantics, Concept family, Reasoner reasoner) {
    var result = new ArrayList<Concept>();
    var direct = new ArrayList<>(reasoner.directTraits(semantics)); direct.addAll(reasoner.directRoles(semantics));
    for (var predicate : direct)
      if (!predicate.is(SemanticType.ABSTRACT) && is(predicate, family, reasoner)) result.add(predicate);
    return result;
  }

  private static boolean sameIdentity(MemberSnapshot before, MemberSnapshot after) {
    if (!before.members().keySet().equals(after.members().keySet())) return false;
    for (var entry : before.members().entrySet()) {
      var next = after.members().get(entry.getKey());
      if (!Objects.equals(entry.getValue().urn(), next.urn())
          || !entry.getValue().geometry().equals(next.geometry())) return false;
    }
    return true;
  }

  private static boolean hasAttribution(ContextScope scope, MemberSnapshot before, long id,
      MemberState old, MemberState next, Concept family, Concept requestedPredicate,
      Set<Concept> auditedResults, Reasoner reasoner, long auditId) {
    var member = graph(scope).getAsset(id, scope, Observation.class);
    if (member == null) return false;
    for (var edge : graph(scope).getLinks(member, GraphModel.Relationship.Direction.INCOMING,
        scope, GraphModel.Relationship.CLASSIFIED)) {
      if (edge.source().getId() != auditId || before.classificationActivities().contains(auditId) || edge.target().getId() != id
          || !(edge.source() instanceof Activity activity) || activity.getType() != Activity.Type.CLASSIFICATION
          || activity.getOutcome() != Activity.Outcome.SUCCESS) continue;
      var properties = edge.properties();
      if (!Objects.equals(properties.get("before"), old.observable())
          || !Objects.equals(properties.get("after"), next.observable())
          || !(properties.get("abstractPredicate") instanceof String requested)
          || !concept(scope, requested).equals(requestedPredicate)
          || !(properties.get("predicate") instanceof String result)
          || !auditedResults.contains(concept(scope, result))) continue;
      if (predicates(concept(scope, next.semantics()), family, reasoner).stream()
          .anyMatch(predicate -> predicate.equals(concept(scope, result)))) return true;
    }
    return false;
  }

  /** Require an actual committed classification of existing identities, including typed provenance. */
  public static boolean classificationCheck(ContextScope scope, MemberSnapshot before, String familyName) {
    return classification(scope, before, familyName, false);
  }

  /** Check a concrete request independently from the lexical family whose attribution it adds. */
  public static boolean classificationCheckFor(ContextScope scope, MemberSnapshot before,
      String requestedName, String familyName) {
    requireLexicalFamily(scope, requestedName, familyName);
    return classification(scope, before, familyName, requestedName, false);
  }

  /** A failed submission is an explicit error response, not a null capture or an empty selection. */
  public static boolean submissionFailed(Observation response) {
    return response != null && response.isEmpty() && response.getId() == Observation.UNASSIGNED_ID
        && Double.isFinite(response.getResolvedCoverage()) && response.getResolvedCoverage() == 0
        && response.getNotifications() != null && response.getNotifications().stream().anyMatch(n ->
            n != null && n.getLevel() != null
                && n.getLevel().severity >= org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error.severity);
  }

  /** Require successful EXISTING-only audit evidence, not merely absence of state changes. */
  public static boolean classificationNoop(ContextScope scope, MemberSnapshot before, String familyName) {
    return classification(scope, before, familyName, true);
  }

  /** A concrete request may validly keep a different known value of the same lexical family. */
  public static boolean classificationNoopFor(ContextScope scope, MemberSnapshot before,
      String requestedName, String familyName) {
    requireLexicalFamily(scope, requestedName, familyName);
    return classification(scope, before, familyName, requestedName, true);
  }

  private static void requireLexicalFamily(ContextScope scope, String requestedName, String familyName) {
    var reasoner = scope.getService(Reasoner.class);
    var requested = concept(scope, requestedName);
    var family = concept(scope, familyName);
    if (!requested.is(SemanticType.PREDICATE) || !family.is(SemanticType.PREDICATE)
        || !Objects.equals(reasoner.lexicalRoot(requested), family))
      throw new IllegalArgumentException("Requested predicate must belong to the stated lexical family");
  }

  private static boolean classification(ContextScope scope, MemberSnapshot before, String familyName, boolean noop) {
    return classification(scope, before, familyName, familyName, noop);
  }

  private static boolean classification(ContextScope scope, MemberSnapshot before, String familyName,
      String requestedName, boolean noop) {
    var after = current(scope, before);
    if (!sameIdentity(before, after) || noop && !before.members().equals(after.members())) return false;
    var reasoner = scope.getService(Reasoner.class); var family = concept(scope, familyName);
    var requestedPredicate = concept(scope, requestedName);
    var changed = new HashSet<Long>();
    for (var entry : before.members().entrySet()) {
      var old = concept(scope, entry.getValue().semantics());
      var next = concept(scope, after.members().get(entry.getKey()).semantics());
      if (predicates(next, family, reasoner).isEmpty()) return false;
      if (predicates(old, family, reasoner).isEmpty()) {
        changed.add(entry.getKey());
        if (old.equals(next) || after.members().get(entry.getKey()).attributions().equals(entry.getValue().attributions()))
          return false;
      }
    }
    if (!noop && changed.isEmpty()) return false; // A wholly preclassified fixture cannot prove mutation.
    int successfulAudits = 0;
    for (var activity : graph(scope).query(Activity.class, scope).run(scope)) {
      if (activity.getId() <= 0 || before.classificationActivities().contains(activity.getId()) || activity.getType() != Activity.Type.CLASSIFICATION
          || activity.getOutcome() != Activity.Outcome.SUCCESS) continue;
      var raw = activity.getMetadata().get(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS);
      if (raw == null) continue;
      // Metadata can expose a Parameters wrapper implementing Map. A JSON round trip through
      // Map.class would expose the wrapper's delegate bean instead of its logical entries.
      var audit = raw instanceof Map<?, ?> map ? map : Utils.Json.parseObject(raw.toString(), Map.class);
      if (!before.context().equals(audit.get("context")) || !(audit.get("memberResults") instanceof List<?> rows)
          || rows.size() != before.members().size()) continue;
      var seen = new HashSet<Long>(); boolean valid = true;
      var auditedResults = new HashMap<Long, Set<Concept>>();
      for (var row : rows) {
        if (!(row instanceof Map<?, ?> item) || !(item.get("memberIdAtInvocation") instanceof Number id)
            || !(id instanceof Byte || id instanceof Short || id instanceof Integer || id instanceof Long)
            || id.longValue() <= 0
            || !before.members().containsKey(id.longValue()) || !seen.add(id.longValue())) { valid = false; break; }
        var old = before.members().get(id.longValue());
        if (!Objects.equals(item.get("inputObservable"), old.observable())
            || !(item.get("requestedPredicate") instanceof String requested)
            || !concept(scope, requested).equals(concept(scope, requestedName))) { valid = false; break; }
        boolean existing = !predicates(concept(scope, old.semantics()), family, reasoner).isEmpty();
        if (!Objects.equals(item.get("origin"), existing ? "EXISTING" : "CLASSIFIER")
            || noop && !existing || !(item.get("concretePredicates") instanceof List<?> values) || values.isEmpty()) {
          valid = false; break;
        }
        var actual = predicates(concept(scope, after.members().get(id.longValue()).semantics()), family, reasoner);
        var rowResults = new HashSet<Concept>();
        for (var value : values) {
          if (!(value instanceof String name) || actual.stream().noneMatch(p -> p.equals(concept(scope, name)))) {
            valid = false;
          } else if (!existing && !is(concept(scope, name), requestedPredicate, reasoner)) {
            // Newly classified knowledge must satisfy the actual operation request. Existing
            // values of a different concrete class remain valid family knowledge for no-op.
            valid = false;
          } else if (!rowResults.add(concept(scope, name))) {
            valid = false;
          }
        }
        if (!valid) break;
        auditedResults.put(id.longValue(), rowResults);
      }
      if (valid && seen.equals(before.members().keySet()) && changed.stream().allMatch(id ->
          hasAttribution(scope, before, id, before.members().get(id), after.members().get(id), family,
              requestedPredicate, auditedResults.get(id), reasoner, activity.getId())))
        successfulAudits++;
    }
    return successfulAudits == 1;
  }

  /** Check the actual directed/bond axes, endpoint meanings and durable participant edges. */
  private static boolean relationshipFailure(ContextScope scope, Observation producer, String invariant) {
    scope.warn("Relationship inspector rejected producer " + producer.getId() + ": " + invariant);
    return false;
  }

  public static boolean relationshipsCheck(ContextScope scope, Observation producer, String relationshipName) {
    var expected = concept(scope, relationshipName); var reasoner = scope.getService(Reasoner.class);
    var expectedAxes = RelationshipAxes.of(expected.getType()); expectedAxes.requireComplete(relationshipName);
    if (!is(producer.getObservable().getSemantics().singular(), expected, reasoner)) return relationshipFailure(scope, producer, "producer semantics");
    var sourceType = reasoner.relationshipSource(expected); var targetType = reasoner.relationshipTarget(expected);
    if (sourceType == null || targetType == null) throw new IllegalStateException("Relationship endpoint meaning unavailable");
    var relationships = members(scope, cohort(scope, producer));
    if (relationships.isEmpty()) return relationshipFailure(scope, producer, "empty relationship member cohort");
    var pairs = new HashSet<List<Long>>(); var identities = new HashSet<Long>();
    for (var relationship : relationships) {
      var semantics = relationship.getObservable().getSemantics();
      if (!identities.add(relationship.getId()) || relationship.getId() <= 0 || semantics.isCollective()
          || !is(semantics, expected, reasoner) || !RelationshipAxes.of(semantics.getType()).equals(expectedAxes)
          || relationship.getGeometry() == null || relationship.getGeometry().isEmpty()) return relationshipFailure(scope, producer, "member identity, semantics, axes or geometry");
      var source = new ArrayList<Observation>(); var target = new ArrayList<Observation>();
      var bond = new ArrayList<Observation>();
      for (var edge : graph(scope).getLinks(relationship, GraphModel.Relationship.Direction.OUTGOING, scope,
          GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
          GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)) {
        if (!(edge.target() instanceof Observation reference) || reference.getId() <= 0) return relationshipFailure(scope, producer, "invalid participant reference");
        // Participant edges may carry lightweight or stale references. Assert current committed
        // meaning/geometry by identity, rather than trusting fields embedded in a link response.
        var endpoint = graph(scope).getAsset(reference.getId(), scope, Observation.class);
        if (endpoint == null || endpoint.getId() != reference.getId()) return relationshipFailure(scope, producer, "missing committed endpoint");
        switch (edge.type()) {
          case HAS_RELATIONSHIP_SOURCE -> source.add(endpoint);
          case HAS_RELATIONSHIP_TARGET -> target.add(endpoint);
          case HAS_RELATIONSHIP_PARTICIPANT -> bond.add(endpoint);
          default -> { return relationshipFailure(scope, producer, "unexpected participant edge"); }
        }
      }
      if (expectedAxes.orientation() == RelationshipAxes.Orientation.DIRECTED) {
        if (source.size() != 1 || target.size() != 1 || !bond.isEmpty()) return relationshipFailure(scope, producer, "directed participant cardinality");
      } else {
        if (!source.isEmpty() || !target.isEmpty() || bond.size() != 2) return relationshipFailure(scope, producer, "bond participant cardinality");
        // A bond has no participant order. Assign endpoint roles by meaning, never numeric IDs.
        var first = bond.getFirst(); var second = bond.getLast();
        if (is(first.getObservable().getSemantics(), sourceType, reasoner)
            && is(second.getObservable().getSemantics(), targetType, reasoner)) {
          source.add(first); target.add(second);
        } else if (is(second.getObservable().getSemantics(), sourceType, reasoner)
            && is(first.getObservable().getSemantics(), targetType, reasoner)) {
          source.add(second); target.add(first);
        } else return relationshipFailure(scope, producer, "bond endpoint meanings");
      }
      var from = source.getFirst(); var to = target.getFirst();
      var pair = expectedAxes.orientation() == RelationshipAxes.Orientation.BOND
          ? List.of(Math.min(from.getId(), to.getId()), Math.max(from.getId(), to.getId()))
          : List.of(from.getId(), to.getId());
      if (from.getId() == to.getId() || !pairs.add(pair)
          || !is(from.getObservable().getSemantics(), sourceType, reasoner)
          || !is(to.getObservable().getSemantics(), targetType, reasoner)) return relationshipFailure(scope, producer, "self-link, duplicate pair or endpoint meaning");
      var line = Scale.create(relationship.getGeometry()).getSpace();
      var left = Scale.create(from.getGeometry()).getSpace(); var right = Scale.create(to.getGeometry()).getSpace();
      if (line == null || left == null || right == null || line.isEmpty() || left.isEmpty() || right.isEmpty()) return relationshipFailure(scope, producer, "missing spatial geometry");
      var geometry = ShapeImpl.promote(line.getGeometricShape());
      var leftShape = ShapeImpl.promote(left.getGeometricShape()).transform(geometry.getProjection()).getJTSGeometry();
      var rightShape = ShapeImpl.promote(right.getGeometricShape()).transform(geometry.getProjection()).getJTSGeometry();
      if (!(leftShape instanceof org.locationtech.jts.geom.Point leftPoint)
          || !(rightShape instanceof org.locationtech.jts.geom.Point rightPoint))
        throw new UnsupportedOperationException("Relationship geometry assertion currently requires single-point endpoints");
      if (!(geometry.getJTSGeometry() instanceof org.locationtech.jts.geom.LineString connection)
          || connection.getNumPoints() != 2) return relationshipFailure(scope, producer, "two-point LineString geometry");
      var first = connection.getCoordinateN(0); var last = connection.getCoordinateN(1);
      var a = leftPoint.getCoordinate(); var b = rightPoint.getCoordinate();
      if (!Double.isFinite(first.x) || !Double.isFinite(first.y) || !Double.isFinite(last.x) || !Double.isFinite(last.y)
          || !Double.isFinite(a.x) || !Double.isFinite(a.y) || !Double.isFinite(b.x) || !Double.isFinite(b.y)) return relationshipFailure(scope, producer, "nonfinite endpoint coordinates");
      // ShapeImpl.canonicalGeometryForEncoding() uses JTS norm(), which may reverse a line.
      // Spatial support is orientation-neutral; directed roles come from the typed graph links
      // validated above. Coordinates must still match exactly, without a widened tolerance.
      boolean forward = first.equals2D(a) && last.equals2D(b);
      boolean reverse = first.equals2D(b) && last.equals2D(a);
      if (!forward && !reverse) return relationshipFailure(scope, producer, "line endpoints differ from participant points");
    }
    return true;
  }
}
