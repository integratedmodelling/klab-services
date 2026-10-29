package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Query/view contract over real transaction-local cohort membership and completion provenance. */
class CollectiveSelectionExecutionTest {
  static final String FINGERPRINT = "semantic-content-v2:" + "a".repeat(64);
  final CollectiveCompletionQueryTest f = new CollectiveCompletionQueryTest();
  Reasoner reasoner;
  Reasoner.Capabilities capabilities;
  ObservableImpl qualified;
  ObservationImpl request;
  ConceptImpl beech, oak, young, mature, species, age;
  ActuatorImpl lastNativePlan;
  final Map<String, Concept> concepts = new HashMap<>();
  final Map<String, Set<String>> ancestors = new HashMap<>();
  final Map<String, List<Concept>> traits = new HashMap<>();

  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }

  @BeforeEach void setup() throws Exception {
    f.setup();
    when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(f.runtime);
    reasoner = mock(Reasoner.class); capabilities = mock(Reasoner.Capabilities.class);
    when(f.scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(f.scope.getObservation(anyLong())).thenAnswer(call -> {
      long id = call.getArgument(0);
      if (f.scope.getCurrentTransaction() != null)
        for (var asset : f.root.assets()) if (asset instanceof Observation observation && observation.getId() == id) return observation;
      return f.durable.get(id) instanceof Observation observation ? observation : null;
    });
    when(reasoner.capabilities(f.scope)).thenReturn(capabilities);
    when(capabilities.getServiceId()).thenReturn("semantic-test");
    when(capabilities.getKnowledgeRevision()).thenReturn(7L);
    when(capabilities.getSemanticContentFingerprint()).thenReturn(FINGERPRINT);
    when(capabilities.getSemanticSessionId()).thenReturn("selection-test-owner");
    when(reasoner.semanticDependencies(any(), eq(f.scope))).thenAnswer(call -> {
      var dependency = call.getArgument(0, org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class);
      return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.VERSION,
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE,
          dependency.expectedServiceId(), dependency.expectedKnowledgeRevision(), dependency.expectedBaseFingerprint(),
          dependency.inputHash(), List.of(), null);
    });
    when(reasoner.satisfiable(any())).thenReturn(true);
    species = predicate("test:Species", SemanticType.IDENTITY, true);
    age = predicate("test:Age", SemanticType.ATTRIBUTE, true);
    beech = predicate("test:Beech", SemanticType.IDENTITY, false);
    oak = predicate("test:Oak", SemanticType.IDENTITY, false);
    young = predicate("test:Young", SemanticType.ATTRIBUTE, false);
    mature = predicate("test:Mature", SemanticType.ATTRIBUTE, false);
    ancestors.put(beech.getUrn(), Set.of(species.getUrn()));
    ancestors.put(oak.getUrn(), Set.of(species.getUrn()));
    ancestors.put(young.getUrn(), Set.of(age.getUrn()));
    ancestors.put(mature.getUrn(), Set.of(age.getUrn()));
    when(reasoner.is(any(), any())).thenAnswer(call -> {
      String a = call.getArgument(0, Semantics.class).asConcept().getUrn();
      String b = call.getArgument(1, Semantics.class).asConcept().getUrn();
      return a.equals(b) || ancestors.getOrDefault(a, Set.of()).contains(b);
    });
    when(reasoner.lexicalRoot(any())).thenAnswer(call -> {
      var predicate = call.getArgument(0, Semantics.class).asConcept();
      return predicate.is(SemanticType.IDENTITY) ? species : age;
    });
    when(reasoner.resolveConcept(anyString())).thenAnswer(call -> concepts.get(call.getArgument(0)));
    when(reasoner.directTraits(any())).thenAnswer(call -> traits.getOrDefault(
        call.getArgument(0, Semantics.class).asConcept().getUrn(), List.of()));
    when(reasoner.directRoles(any())).thenReturn(List.of());
    qualified = observable("each test:Beech test:Stream", true);
    request = new ObservationImpl(); request.setId(Observation.QUERY_ID);
    request.setObservable(qualified); request.setGeometry(f.producer.getGeometry());
    traits.put(qualified.asConcept().getUrn(), List.of(beech));
    when(reasoner.buildObservable(any(), eq(f.scope))).thenAnswer(call -> {
      var build = call.getArgument(0, ObservableBuildStrategy.class);
      if (build.getBaseObservable() != null && Objects.equals(build.getBaseObservable().getUrn(), qualified.getUrn()))
        return f.producer.getObservable();
      var family = build.getBaseConcept();
      return directive(family.getUrn());
    });
    when(reasoner.resolveObservable(anyString())).thenAnswer(call -> {
      var syntax = call.getArgument(0, String.class);
      if (syntax.equals(f.producer.getObservable().getUrn())) return f.producer.getObservable();
      for (var family : List.of(species, age)) if (syntax.equals(family.getUrn() + " of " + f.producer.getObservable().getUrn()))
        return directive(family.getUrn());
      return null;
    });
  }

  ConceptImpl predicate(String urn, SemanticType kind, boolean isAbstract) {
    var concept = new ConceptImpl(); concept.setUrn(urn);
    var types = EnumSet.of(SemanticType.PREDICATE, kind);
    if (isAbstract) types.add(SemanticType.ABSTRACT);
    concept.setType(types); concept.setAbstract(isAbstract); concepts.put(urn, concept); return concept;
  }

  ObservableImpl observable(String urn, boolean collective) {
    var concept = new ConceptImpl(); concept.setUrn(urn); concept.setCollective(collective);
    concept.setReferenceName(urn.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT));
    concept.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    concept.setDescriptionType(collective ? Contextualization.INSTANTIATION : Contextualization.ACKNOWLEDGEMENT);
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(urn);
    result.setArtifactType(Artifact.Type.OBJECT);
    result.setDescriptionType(concept.getDescriptionType()); return result;
  }

  Observable directive(String family) {
    var result = new ObservableImpl(); result.setSemantics(concepts.get(family));
    result.setUrn(family + " of " + f.producer.getObservable().getUrn());
    result.setDescriptionType(Contextualization.CLASSIFICATION); return result;
  }

  ObservationImpl member(long id, String name, Concept... values) {
    var result = new ObservationImpl(); result.setId(id);
    result.setObservable(observable("test:" + name + " test:Stream", false));
    result.setGeometry(request.getGeometry());
    traits.put(result.getObservable().asConcept().getUrn(), List.of(values));
    ancestors.put(result.getObservable().asConcept().getUrn(), Set.of(f.producer.getObservable().getSemantics().singular().getUrn()));
    f.root.link(f.cohort, result, GraphModel.Relationship.HAS_MEMBER);
    f.producer.setChildrenCount(f.producer.getChildrenCount() + 1);
    return result;
  }

  void complete() {
    complete(request.getGeometry(), f.scope.getObserver() == null ? 0L : f.scope.getObserver().getId());
  }

  void complete(Geometry support, long observerId) {
    var execution = f.execution(Activity.Type.INSTANTIATION, f.producer, support);
    RuntimeService.recordCollectiveSemanticRevision(execution.getActivity(), f.scope);
    // Query-contract fixture: a real Tx activity and Runtime collector, with a controlled admitted
    // empty native model. This does not claim actual producer contextualizer execution.
    var model = new org.integratedmodelling.common.knowledge.ModelImpl();
    model.setUrn("fixture.native"); model.setNamespace("fixture"); model.getObservables().add(f.producer.getObservable());
    var snapshot = org.integratedmodelling.klab.runtime.language.ModelValidationGate.snapshot(model);
    var terms = org.integratedmodelling.klab.runtime.language.ModelValidationGate.declaredSemanticObligations(snapshot);
    var dependency = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
        capabilities.getServiceId(), capabilities.getKnowledgeRevision(), capabilities.getSemanticContentFingerprint(), terms);
    var admission = new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(
        model.getUrn(), model.getNamespace(), "1", "a".repeat(64), snapshot.executableHash(), capabilities.getServiceId(),
        capabilities.getKnowledgeRevision(), List.of(), capabilities.getSemanticContentFingerprint(), terms, true,
        reasoner.semanticDependencies(dependency, f.scope), capabilities.getSemanticSessionId());
    var nativePlan = new ActuatorImpl(); nativePlan.setActuatorType(Actuator.Type.OBSERVE); nativePlan.setObservation(f.producer);
    nativePlan.getData().put(Actuator.PRODUCING_MODELS_KEY, List.of(model.getUrn()));
    nativePlan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_ADMISSIONS,
        List.of(Utils.Json.asString(admission)));
    nativePlan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_SNAPSHOTS,
        List.of(Utils.Json.asString(snapshot)));
    lastNativePlan = nativePlan;
    var members = f.root.outgoing(f.cohort).stream().filter(link -> link.type() == GraphModel.Relationship.HAS_MEMBER)
        .map(link -> (Observation) link.target()).toList();
    SemanticExecutionEvidence.record(execution.getActivity(), nativePlan, f.producer, members, List.of(), List.of(), true, f.scope);
    execution.getActivity().getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, observerId);
    if (f.scope.getObserver() != null)
      execution.getActivity().getMetadata().put(RuntimeService.COLLECTIVE_OBSERVER_CLASS,
          f.scope.getObserver().getObservable().getSemantics().getUrn());
    assertTrue(execution.commit() >= 0);
  }

  Observation query() { return CollectiveSelectionExecution.query(f.runtime, request, f.scope); }
  /** Controlled source-admission boundary for handcrafted plans; not executable validation. */
  void admitPlan(ActuatorImpl plan) {
    var model = new org.integratedmodelling.common.knowledge.ModelImpl();
    model.setUrn("fixture." + (plan.getName() == null ? "operation" : plan.getName()));
    model.setNamespace("fixture");
    model.getObservables().add(plan.getOperationObservable() == null
        ? plan.getObservation().getObservable() : plan.getOperationObservable());
    for (var call : plan.getComputation()) {
      var computation = new org.integratedmodelling.common.lang.ContextualizableImpl();
      computation.setServiceCall(call); model.getComputation().add(computation);
    }
    var snapshot = org.integratedmodelling.klab.runtime.language.ModelValidationGate.snapshot(model);
    var terms = org.integratedmodelling.klab.runtime.language.ModelValidationGate.declaredSemanticObligations(snapshot);
    var request = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
        capabilities.getServiceId(), capabilities.getKnowledgeRevision(), capabilities.getSemanticContentFingerprint(), terms);
    var receipt = new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(
        model.getUrn(), model.getNamespace(), "1", "a".repeat(64), snapshot.executableHash(), capabilities.getServiceId(),
        capabilities.getKnowledgeRevision(), List.of(), capabilities.getSemanticContentFingerprint(), terms, true,
        reasoner.semanticDependencies(request, f.scope), capabilities.getSemanticSessionId());
    plan.getData().put(Actuator.PRODUCING_MODELS_KEY, List.of(model.getUrn()));
    plan.getData().put("modelAdmissionRevision", capabilities.getKnowledgeRevision());
    plan.getData().put("modelAdmissionReasonerId", capabilities.getServiceId());
    plan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_ADMISSIONS,
        List.of(Utils.Json.asString(receipt)));
    plan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_SNAPSHOTS,
        List.of(Utils.Json.asString(snapshot)));
  }

  /** Actual staging and Runtime proof collection, with a controlled empty semantic operation. */
  void recordAttribution(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl tx,
      Observation member, Concept family, Concept value, Observable original) {
    var plan = new ActuatorImpl(); plan.setActuatorType(Actuator.Type.UPDATE);
    plan.setOperationObservable(directive(family.getUrn())); admitPlan(plan);
    SemanticExecutionEvidence.record(tx.getActivity(), plan, member, List.of(), List.of(),
        List.of(family.getUrn(), value.getUrn(), original.getSemantics().getUrn()), true, f.scope);
  }
  CollectiveSelectionView proof(Observation view) {
    return Utils.Json.parseObject(view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(), CollectiveSelectionView.class);
  }

  @Test void completedEmptyIsSuccessfulButUnknownDiscoveryIsNotEmptySelection() {
    assertTrue(query().isEmpty(), "Registration is not discovery completion");
    complete();
    var view = query();
    assertFalse(view.isEmpty()); assertEquals(Observation.QUERY_ID, view.getId());
    assertEquals(0, view.getChildrenCount()); assertEquals(1.0, view.getResolvedCoverage());
    assertTrue(proof(view).members().isEmpty()); assertTrue(proof(view).selectedIds().isEmpty());
  }

  @Test void existingDifferentClassificationIsExcludedWithoutCreatingAnyUpdateOrMember() {
    var member = member(-30, "Oak", oak); complete();
    var oldObservable = member.getObservable(); var before = Set.copyOf(f.root.assets());
    var view = query();
    assertFalse(view.isEmpty()); assertEquals(0, view.getChildrenCount());
    assertEquals(List.of(-30L), proof(view).members().stream().map(CollectiveSelectionView.Member::id).toList());
    assertTrue(proof(view).selectedIds().isEmpty()); assertSame(oldObservable, member.getObservable());
    assertEquals(before, Set.copyOf(f.root.assets()), "Read-only selection must not add an UPDATE audit");
    assertTrue(f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .noneMatch(activity -> activity.getType() == Activity.Type.CLASSIFICATION));
  }

  @Test void selectionConjoinsDistinctFamiliesAndUsesReasonerSubtypeDirection() {
    traits.put(qualified.asConcept().getUrn(), List.of(beech, mature));
    var subBeech = predicate("test:SubBeech", SemanticType.IDENTITY, false);
    ancestors.put(subBeech.getUrn(), Set.of(beech.getUrn(), species.getUrn()));
    member(-30, "MatureBeech", beech, mature);
    member(-31, "YoungBeech", beech, young);
    member(-32, "MatureOak", oak, mature);
    member(-33, "MatureSubBeech", subBeech, mature); complete();
    var view = query();
    assertEquals(2, view.getChildrenCount()); assertEquals(List.of(-33L, -30L), proof(view).selectedIds());
    assertEquals(4, proof(view).members().size());
    assertEquals(Set.of(species.getUrn(), age.getUrn()), Set.copyOf(proof(view).families()));
  }

  @Test void missingFamilyKnowledgeCannotProduceExhaustiveNegativeSelection() {
    traits.put(qualified.asConcept().getUrn(), List.of(beech, mature));
    member(-30, "UnknownAge", oak); complete();
    assertTrue(query().isEmpty(), "Known nonmatching Species does not certify missing Age knowledge");
  }

  @Test void invalidAttributionInAnOtherwiseKnownFamilyCannotCertifySelection() {
    member(-30, "UnresolvedSpecies", beech, species); complete();
    assertTrue(query().isEmpty(), "A concrete sibling does not conceal an abstract attribution in the same family");
  }

  @Test void partialDiscoveryCannotBecomeCompleteSelectionAndDifferentObserverCannotFillItsGap() {
    member(-30, "Beech", beech);
    complete(f.period(0, 5), 0L);
    assertTrue(query().isEmpty());
    complete(f.period(5, 10), 44L);
    assertTrue(query().isEmpty(), "Support belonging to another observer cannot complete this selection");
    complete(f.period(5, 10), 0L);
    var completed = query();
    assertFalse(completed.isEmpty()); assertEquals(1.0, completed.getResolvedCoverage());
    assertEquals(List.of(-30L), proof(completed).selectedIds());
  }

  @Test void cancellationRemainsFailureRatherThanSuccessfulEmptySelection() {
    complete(); when(f.scope.isInterrupted()).thenReturn(true);
    assertThrows(java.util.concurrent.CancellationException.class, this::query);
  }

  @Test void wrongObserverOrSemanticGenerationCannotWitnessNativeCompletion() {
    member(-30, "Beech", beech); complete();
    assertFalse(query().isEmpty());
    when(capabilities.getKnowledgeRevision()).thenReturn(8L);
    assertTrue(query().isEmpty());
    when(capabilities.getKnowledgeRevision()).thenReturn(7L);
    var observer = observer("test:ObserverA");
    when(f.scope.getObserver()).thenReturn(observer);
    assertTrue(query().isEmpty(), "Another observer's successful discovery is not this selected observer's receipt");
  }

  ObservationImpl observer(String semantics) {
    var meaning = observable(semantics, false);
    ((ConceptImpl) meaning.asConcept()).setType(EnumSet.of(SemanticType.AGENT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    var observer = new ObservationImpl(); observer.setId(44); observer.setUrn("context.44");
    observer.setObservable(meaning); observer.setGeometry(request.getGeometry()); return observer;
  }

  @Test void sameObserverIdAndUrnCannotReuseDiscoveryForAChangedAgentSemanticClass() {
    member(-30, "Beech", beech);
    var observer = observer("test:ObserverA"); when(f.scope.getObserver()).thenReturn(observer); complete();
    var view = query(); assertFalse(view.isEmpty()); assertEquals("test:ObserverA", proof(view).observerClass());
    observer.setObservable(observer("test:ObserverB").getObservable());
    assertEquals(44, observer.getId()); assertEquals("context.44", observer.getUrn());
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope),
        "Observer identity alone does not certify discovery under another semantic class");
  }

  @Test void durableCompletionAndViewReplaySurviveUnchangedContentWithDifferentNumericGeneration() {
    member(-30, "Beech", beech); complete();
    assertTrue(f.root.commit() > 0);
    when(f.scope.getCurrentTransaction()).thenReturn(null);
    var view = query(); assertFalse(view.isEmpty());
    var oldBytes = view.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    when(capabilities.getKnowledgeRevision()).thenReturn(8L);
    var replay = CollectiveSelectionExecution.query(f.runtime, view, f.scope);
    assertFalse(replay.isEmpty()); assertEquals(proof(view).selectedIds(), proof(replay).selectedIds());
    assertEquals(7L, proof(view).knowledgeRevision()); assertEquals(8L, proof(replay).knowledgeRevision());
    assertEquals(oldBytes, view.getMetadata().get(CollectiveSelectionView.DATA_KEY), "Replaying must not rewrite its stored audit bytes");
  }

  @Test void repeatedGenerationWithChangedContentCannotReuseDurableCompletion() {
    member(-30, "Beech", beech); complete(); assertTrue(f.root.commit() > 0);
    when(f.scope.getCurrentTransaction()).thenReturn(null);
    assertFalse(query().isEmpty());
    var before = new HashMap<>(f.durable);
    when(capabilities.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:" + "b".repeat(64));
    assertTrue(query().isEmpty(), "The same service ID and repeated counter cannot certify changed content");
    assertEquals(before, f.durable, "Rejected evidence must preserve all observations/provenance");
  }

  @Test void legacyCompletionRequiresOrdinaryExecutionRevalidationAndIsNeverStampedOnRead() {
    member(-30, "Beech", beech); complete(); assertTrue(f.root.commit() > 0);
    when(f.scope.getCurrentTransaction()).thenReturn(null);
    for (var asset : f.durable.values()) if (asset instanceof Activity activity)
      { activity.getMetadata().remove(RuntimeService.COLLECTIVE_FINGERPRINT);
        activity.getMetadata().remove(SemanticEvidenceBinding.DATA_KEY); }
    var before = new HashMap<>(f.durable);
    assertTrue(query().isEmpty(), "Legacy execution cannot certify exhaustive discovery");
    for (var asset : f.durable.values()) if (asset instanceof Activity activity)
      assertNull(activity.getMetadata().get(RuntimeService.COLLECTIVE_FINGERPRINT));
    assertEquals(before, f.durable);
    var oldMemberIds = f.durable.values().stream().filter(Observation.class::isInstance)
        .map(Observation.class::cast).map(Observation::getId).sorted().toList();
    // The ordinary execution route may produce new, successful completion evidence. It does
    // not edit or retroactively certify any legacy activity or observation.
    // A read capture has its own transaction. Closing it must not replay the fixture's
    // previous write buffer or restore the pre-legacy activity metadata.
    var capture = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Transaction.class);
    when(f.graph.createTransaction(f.scope)).thenReturn(capture);
    when(capture.captureClassificationMembers(f.cohort.getId())).thenAnswer(call ->
        new org.integratedmodelling.klab.api.data.KnowledgeGraph.ClassificationReadSet(f.scope.getId(),
            f.cohort.getId(), f.cohort.getObservable().getUrn(), f.durableLinks.stream()
                .filter(link -> link.get(2) == GraphModel.Relationship.HAS_MEMBER
                    && ((org.integratedmodelling.klab.api.data.RuntimeAsset) link.getFirst()).getId() == f.cohort.getId())
                .map(link -> (Observation) f.durable.get(((org.integratedmodelling.klab.api.data.RuntimeAsset) link.get(1)).getId()))
                .distinct().map(member -> new org.integratedmodelling.klab.api.data.KnowledgeGraph.ClassificationMemberSnapshot(
                    member.getId(), member.getObservable().getUrn(), member.getGeometry().encode(), 0L)).toList()));
    when(f.graph.materializeClassificationMember(any(), eq(f.scope))).thenAnswer(call ->
        f.durable.get(call.getArgument(0, org.integratedmodelling.klab.api.data.KnowledgeGraph.ClassificationMemberSnapshot.class).id()));
    f.root = f.twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), f.scope,
        org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);
    when(f.scope.getCurrentTransaction()).thenReturn(f.root);
    f.root.link(f.producer, f.cohort, GraphModel.Relationship.CONTRIBUTED_TO);
    complete();
    assertFalse(query().isEmpty());
    assertEquals(oldMemberIds, f.durable.values().stream().filter(Observation.class::isInstance)
        .map(Observation.class::cast).map(Observation::getId).sorted().toList());
    for (var asset : before.values()) if (asset instanceof Activity activity)
      assertNull(activity.getMetadata().get(RuntimeService.COLLECTIVE_FINGERPRINT));
  }

  @Test void importingLegacyProvenanceIntoCurrentOverlayCannotMasqueradeAsFreshExecution() {
    when(capabilities.getSemanticContentFingerprint()).thenReturn(null);
    member(-30, "Beech", beech);
    var old = new org.integratedmodelling.klab.api.provenance.impl.ActivityImpl();
    old.setId(999); old.setType(Activity.Type.INSTANTIATION); old.setOutcome(Activity.Outcome.SUCCESS);
    old.getMetadata().put(RuntimeService.COLLECTIVE_REASONER, "semantic-test");
    old.getMetadata().put(RuntimeService.COLLECTIVE_REVISION, 7L);
    old.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L);
    old.getMetadata().put(Metadata.IM_EXECUTION_GEOMETRY, request.getGeometry().encode());
    f.root.link(old, f.producer, GraphModel.Relationship.forContextualization(Contextualization.INSTANTIATION));
    assertFalse(f.root.ownsExecutionActivity(old));
    assertTrue(query().isEmpty());
    assertNull(old.getMetadata().get(RuntimeService.COLLECTIVE_FINGERPRINT));
  }

  @Test void uncertifiedCurrentExecutionCannotPublishExhaustiveSelection() {
    when(capabilities.getSemanticContentFingerprint()).thenReturn(null);
    member(-30, "Beech", beech); complete();
    for (var asset : f.root.assets()) if (asset instanceof Activity activity)
      { activity.getMetadata().remove(RuntimeService.COLLECTIVE_FINGERPRINT);
        activity.getMetadata().remove(SemanticEvidenceBinding.DATA_KEY); }
    var view = query();
    assertTrue(view.isEmpty(), "An active execution fence is not a scoped completeness proof");
    assertNull(view.getMetadata().get(CollectiveSelectionView.DATA_KEY));
  }

  @Test void legacyViewCannotBeSilentlyUpgradedDespiteCurrentNativeCompletion() {
    member(-30, "Beech", beech); complete();
    var view = query(); var old = proof(view);
    var legacy = new CollectiveSelectionView(1, old.requested(), old.nativeMembers(), old.predicates(), old.families(),
        old.reasonerId(), old.knowledgeRevision(), old.observerId(), old.observerUrn(), old.geometry(),
        old.cohortId(), old.sourceIds(), old.members(), old.selectedIds(), old.scopeId(), old.context(), old.observerClass());
    view.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(legacy));
    var bytes = view.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope));
    assertEquals(bytes, view.getMetadata().get(CollectiveSelectionView.DATA_KEY));
  }

  @Test void generationChangeDuringMemberInspectionCannotPublishMixedRevisionReceipt() {
    var member = member(-30, "Beech", beech); complete();
    doAnswer(call -> {
      doReturn(8L).when(capabilities).getKnowledgeRevision();
      return List.of(beech);
    }).when(reasoner).directTraits(member.getObservable());
    assertThrows(IllegalStateException.class, this::query);
  }

  @Test void concurrentCohortArrivalCannotPublishAnExhaustiveSelectionFromAnOlderRead() {
    var member = member(-30, "Beech", beech); complete();
    var arrival = new ObservationImpl(); arrival.setId(-31);
    arrival.setObservable(observable("test:OakArrival test:Stream", false)); arrival.setGeometry(request.getGeometry());
    traits.put(arrival.getObservable().asConcept().getUrn(), List.of(oak));
    ancestors.put(arrival.getObservable().asConcept().getUrn(), Set.of(f.producer.getObservable().getSemantics().singular().getUrn()));
    var injected = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(call -> {
      if (injected.compareAndSet(false, true)) f.root.link(f.cohort, arrival, GraphModel.Relationship.HAS_MEMBER);
      return List.of(beech);
    }).when(reasoner).directTraits(member.getObservable());
    assertThrows(IllegalStateException.class, this::query);
  }

  @Test void durableGraphRevisionChangeDuringInspectionCannotPublishAReceipt() {
    var member = member(-30, "Beech", beech); complete();
    doAnswer(call -> {
      doReturn(1L).when(f.graph).getSemanticRevision();
      return List.of(beech);
    }).when(reasoner).directTraits(member.getObservable());
    assertThrows(IllegalStateException.class, this::query);
  }

  @Test void replayRejectsChangedNativeMemberMeaningAndGeometry() {
    var member = member(-30, "Beech", beech); complete();
    var view = query(); assertFalse(view.isEmpty());
    var original = member.getGeometry();
    member.setGeometry(f.period(0, 5));
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope));
    member.setGeometry(original);
    traits.put(member.getObservable().asConcept().getUrn(), List.of(oak));
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope));
  }

  @Test void narrowedPriorViewMetadataCannotHideActualClassifierAuthorityDependencies() {
    var member = member(-30, "Beech", beech); complete();
    var view = query(); var actual = proof(view);
    // Controlled semantic boundary: a real classifier activity has an unversioned authority
    // dependency. The old/prior view contains a valid but narrower (authority-free) witness.
    when(reasoner.semanticDependencies(any(), eq(f.scope))).thenAnswer(call -> {
      var request = call.getArgument(0, org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class);
      boolean affected = request.obligations().contains("authority:Mutable");
      return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.VERSION,
          affected ? org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.REVALIDATION_REQUIRED
              : org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE,
          request.expectedServiceId(), request.expectedKnowledgeRevision(), request.expectedBaseFingerprint(), request.inputHash(),
          affected ? List.of(new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.AuthorityUse(
              "mutable", org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Kind.UNVERSIONED, null, 0)) : List.of(), null);
    });
    var dependency = new SemanticExecutionEvidence.Collector(); dependency.term("authority:Mutable");
    var operation = f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION), f.scope, member);
    operation.getActivity().getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(dependency.finish(f.scope)));
    assertTrue(operation.commit() >= 0); assertTrue(f.root.commit() > 0);
    when(f.scope.getCurrentTransaction()).thenReturn(null);
    assertTrue(query().isEmpty(), "Actual authority-affected provenance must block fresh completeness");
    var narrowed = new SemanticExecutionEvidence.Collector();
    actual.semanticEvidence().obligations().forEach(narrowed::term);
    var fakeProof = narrowed.finish(f.scope);
    assertTrue(fakeProof.complete()); assertTrue(SemanticExecutionEvidence.verified(fakeProof, f.scope),
        "The forgery uses a valid proof of its narrower signature, not a broken hash");
    var fakeReceipt = new CollectiveSelectionView(3, actual.requested(), actual.nativeMembers(), actual.predicates(),
        actual.families(), actual.reasonerId(), actual.knowledgeRevision(), actual.observerId(), actual.observerUrn(),
        actual.geometry(), actual.cohortId(), actual.sourceIds(), actual.members(), actual.selectedIds(), actual.scopeId(),
        actual.context(), actual.observerClass(), actual.semanticFingerprint(), fakeProof);
    view.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(fakeReceipt));
    var originalBytes = view.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope),
        "Actual classifier provenance cannot be replaced by narrower caller view metadata");
    assertEquals(originalBytes, view.getMetadata().get(CollectiveSelectionView.DATA_KEY));
  }

  @Test void replayRejectsForgedSourcesAndDuplicateOrExtraSelectedIds() {
    member(-30, "Beech", beech); complete();
    var view = query(); var original = proof(view);
    for (var replacement : List.of(
        new CollectiveSelectionView(3, original.requested(), original.nativeMembers(), original.predicates(), original.families(),
            original.reasonerId(), original.knowledgeRevision(), original.observerId(), original.observerUrn(), original.geometry(),
            original.cohortId(), List.of(999L), original.members(), original.selectedIds(), original.scopeId(), original.context(), original.observerClass(), original.semanticFingerprint(), original.semanticEvidence()),
        new CollectiveSelectionView(3, original.requested(), original.nativeMembers(), original.predicates(), original.families(),
            original.reasonerId(), original.knowledgeRevision(), original.observerId(), original.observerUrn(), original.geometry(),
            original.cohortId(), original.sourceIds(), original.members(), List.of(-30L, -30L), original.scopeId(), original.context(), original.observerClass(), original.semanticFingerprint(), original.semanticEvidence()),
        new CollectiveSelectionView(3, original.requested(), original.nativeMembers(), original.predicates(), original.families(),
            original.reasonerId(), original.knowledgeRevision(), original.observerId(), original.observerUrn(), original.geometry(),
            original.cohortId(), original.sourceIds(), original.members(), List.of(-30L, 999L), original.scopeId(), original.context(), original.observerClass(), original.semanticFingerprint(), original.semanticEvidence()))) {
      view.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(replacement));
      assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(f.runtime, view, f.scope));
    }
  }

  @Test void replayRetainsExactMemberIdentityAndCanBeReadWithoutAnyMutation() {
    var selected = member(-30, "Beech", beech); member(-31, "Oak", oak); complete();
    var view = query(); var before = Set.copyOf(f.root.assets());
    var replay = CollectiveSelectionExecution.query(f.runtime, view, f.scope);
    assertEquals(proof(view), proof(replay)); assertEquals(List.of(selected.getId()), proof(replay).selectedIds());
    assertEquals(before, Set.copyOf(f.root.assets()));
  }

  @Test void enumerationCountAndReceiptUseTheSameSelectedNativeIds() {
    var selected = member(-30, "Beech", beech); member(-31, "Oak", oak); complete();
    var view = query();
    var actual = f.runtime.getMembers(view, f.scope);
    assertEquals(view.getChildrenCount(), actual.size());
    assertEquals(proof(view).selectedIds(), actual.stream().map(Observation::getId).sorted().toList());
    assertEquals(List.of(selected.getId()), actual.stream().map(Observation::getId).toList());
    assertEquals(selected.getObservable().getSemantics().getUrn(), actual.getFirst().getObservable().getSemantics().getUrn());
  }

  @Test void sameBaseCohortDoesNotAdmitMembersOutsideRetainedNativeClause() {
    var nativeClause = observable("each test:Stream of test:CatchmentA", true);
    f.producer.setObservable(nativeClause);
    when(reasoner.resolveObservable(nativeClause.getUrn())).thenReturn(nativeClause);
    qualified = observable("each test:Beech test:Stream of test:CatchmentA", true);
    request.setObservable(qualified); traits.put(qualified.asConcept().getUrn(), List.of(beech));
    var matching = member(-30, "BeechInA", beech);
    var foreign = member(-31, "BeechInB", beech);
    ancestors.put(foreign.getObservable().asConcept().getUrn(), Set.of("test:Stream", "test:Stream of test:CatchmentB"));
    complete();
    var view = query();
    assertEquals(List.of(matching.getId()), proof(view).selectedIds());
    assertEquals(List.of(matching.getId()), proof(view).members().stream().map(CollectiveSelectionView.Member::id).toList());
    assertEquals(1, view.getChildrenCount());
  }

  @Test void coincidentGraphIdsAndSemanticsCannotAuthorizeReceiptInAnotherContext() throws Exception {
    member(-30, "Beech", beech); complete();
    var view = query(); var original = proof(view);
    var other = new CollectiveSelectionExecutionTest(); other.setup();
    when(other.f.scope.getId()).thenReturn("another-twin-context");
    other.member(-30, "Beech", other.beech); other.complete();
    var otherProof = other.proof(other.query());
    // All IDs, sources, geometry, member semantics and the Reasoner generation deliberately match.
    // These are separate DigitalTwinImpl/TransactionImpl and persistence-boundary fixtures.
    assertEquals(original.sourceIds(), otherProof.sourceIds()); assertEquals(original.members(), otherProof.members());
    assertEquals(original.cohortId(), otherProof.cohortId()); assertEquals(original.reasonerId(), otherProof.reasonerId());
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.query(other.f.runtime, view, other.f.scope));
    assertEquals("completion-test", original.scopeId());
  }

  DataflowImpl knownFamilyPlan() {
    var binding = CollectiveSelectionBinding.capture(request, f.scope, reasoner);
    var plan = new DataflowImpl();
    plan.setCollectiveSelection(new Dataflow.CollectiveSelection(qualified, f.producer.getObservable(),
        List.of(beech), List.of(species), List.of("species")));
    plan.getMetadata().put(CollectiveSelectionBinding.DATA_KEY, Utils.Json.asString(binding));
    var nativeReference = new ActuatorImpl(); nativeReference.setActuatorType(Actuator.Type.REFERENCE);
    nativeReference.setObservation(f.producer); nativeReference.setId(f.producer.getId());
    nativeReference.setCoverage(request.getGeometry());
    var check = new ActuatorImpl(); check.setName("species"); check.setActuatorType(Actuator.Type.UPDATE);
    check.setEffect(Actuator.Effect.SEMANTIC_UPDATE);
    check.setContextualization(Contextualization.CLASSIFICATION); check.setOperationObservable(directive(species.getUrn()));
    check.getData().put(Actuator.CLASSIFICATION_CHECK_ONLY, true);
    plan.getComputation().add(nativeReference); plan.getComputation().add(check);
    return plan;
  }

  @Test void universalAndOutOfBoundsNativeFragmentsAreRejectedBeforeClassification() {
    member(-30, "Beech", beech); complete();
    for (var fragment : List.of(Geometry.UNIVERSAL, f.period(0, 20))) {
      var plan = knownFamilyPlan();
      ((ActuatorImpl) plan.getComputation().getFirst()).setCoverage(fragment);
      assertThrows(IllegalArgumentException.class, () -> CollectiveSelectionExecution.execute(
          f.runtime, plan, request, f.scope, new ArrayList<>()));
    }
    assertTrue(f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .noneMatch(activity -> activity.getType() == Activity.Type.CLASSIFICATION));
  }

  @Test void knownFamilyNoopBindsTheCompletedNativeViewBeforeSealing() {
    var member=member(-30,"Beech",beech);complete();
    var plan=knownFamilyPlan();var operation=(ActuatorImpl)plan.getComputation().getLast();
    var placeholder=new ActuatorImpl();placeholder.setActuatorType(Actuator.Type.REFERENCE);
    placeholder.setObservation(f.query);placeholder.setId(Observation.QUERY_ID);
    placeholder.getData().put(Actuator.COMPLETE_COHORT_REFERENCE,true);
    operation.getChildren().add(placeholder);
    var view=CollectiveSelectionExecution.execute(f.runtime,plan,request,f.scope,new ArrayList<>());
    assertEquals(List.of(member.getId()),proof(view).selectedIds());
    assertNotSame(f.query,placeholder.getObservation());
    assertNotNull(placeholder.getObservation().getMetadata().get(SemanticEvidenceBinding.DATA_KEY));
    assertTrue(f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .noneMatch(activity->activity.getType()==Activity.Type.CLASSIFICATION),"Known values create no classifier execution");
  }

  @Test void distinctNativeContributionsUseTheirCoverageAndRetainProducerIdentity() {
    member(-30, "Beech", beech);
    var plan = knownFamilyPlan();
    plan.getComputation().removeFirst();
    var left = new ActuatorImpl(); left.setActuatorType(Actuator.Type.OBSERVE);
    left.setObservation(f.producer); left.setCoverage(f.period(0, 5));
    var right = new ActuatorImpl(); right.setActuatorType(Actuator.Type.OBSERVE);
    right.setObservation(f.producer); right.setCoverage(f.period(5, 10));
    plan.getComputation().addFirst(right); plan.getComputation().addFirst(left);
    var calls = new ArrayList<String>();
    doAnswer(call -> {
      var root = call.getArgument(0, Actuator.class);
      var support = call.getArgument(1, Geometry.class);
      assertSame(f.producer, root.getObservation());
      assertSame(request.getGeometry(), f.producer.getGeometry(), "Producer identity keeps its declared full geometry");
      calls.add(Geometry.forTransport(support).encode());
      complete(support, 0L);
      // The fake producer boundary above records the controlled admitted model; the selected
      // actuator must carry that same source-backed plan, as the real planner does.
      ((ActuatorImpl) root).getData().putAll(lastNativePlan.getData());
      return f.producer;
    }).when(f.runtime).executeSelectionProducer(any(), any(), eq(f.scope));
    var view = CollectiveSelectionExecution.execute(f.runtime, plan, request, f.scope, new ArrayList<>());
    assertEquals(List.of(Geometry.forTransport(left.getCoverage()).encode(),
        Geometry.forTransport(right.getCoverage()).encode()), calls);
    assertEquals(List.of(-30L), proof(view).selectedIds());
    // A second stale plan in this attempt sees normal completion proofs and performs no discovery.
    CollectiveSelectionExecution.execute(f.runtime, plan, request, f.scope, new ArrayList<>());
    assertEquals(2, calls.size());
  }

  @Test void observeContributionOutsideRequestedSupportIsRejectedBeforeExecution() {
    member(-30, "Beech", beech);
    var plan = knownFamilyPlan();
    var root = (ActuatorImpl) plan.getComputation().getFirst();
    root.setActuatorType(Actuator.Type.OBSERVE); root.setCoverage(f.period(0, 20));
    assertThrows(IllegalArgumentException.class, () -> CollectiveSelectionExecution.execute(
        f.runtime, plan, request, f.scope, new ArrayList<>()));
    verify(f.runtime, never()).executeSelectionProducer(any(), any(), any());
  }

  @Test void knownFamilyExecutionPublishesDurableIdsWithoutClassificationAudit() {
    var selected = member(-30, "Beech", beech); member(-31, "Oak", oak); complete();
    var plan = knownFamilyPlan();
    var validations = new ArrayList<Runnable>();
    var view = CollectiveSelectionExecution.execute(f.runtime, plan, request, f.scope, validations);
    assertEquals(List.of(-30L), proof(view).selectedIds());
    validations.forEach(Runnable::run);
    assertTrue(f.root.commit() > 0);
    var published = proof(view);
    assertEquals(List.of(selected.getId()), published.selectedIds()); assertTrue(selected.getId() > 0);
    assertTrue(published.members().stream().allMatch(member -> member.id() > 0));
    assertEquals(List.of(f.producer.getId()), published.sourceIds()); assertTrue(f.producer.getId() > 0);
    assertEquals(f.cohort.getId(), published.cohortId()); assertTrue(published.cohortId() > 0);
    assertTrue(f.durable.values().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .noneMatch(activity -> activity.getType() == Activity.Type.CLASSIFICATION));
  }
}
