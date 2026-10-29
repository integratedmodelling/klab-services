package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Real precompile continuation/query binding. The cohort's classification is already known;
 * actual classifier execution and consumer failure are tested in AtomicExecutionTest. */
class CollectiveSelectionContinuationTest {
  final CollectiveSelectionExecutionTest q = new CollectiveSelectionExecutionTest();
  ObservationImpl selectedMember;

  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }
  @BeforeEach void setup() throws Exception {
    q.setup();
    selectedMember = q.member(-30, "Beech", q.beech); selectedMember.setUrn("beech");
    q.member(-31, "Oak", q.oak).setUrn("oak");
    q.complete();
  }

  private DataflowImpl selection() {
    var binding = CollectiveSelectionBinding.capture(q.request, q.f.scope, q.reasoner);
    assertNotNull(binding);
    var flow = new DataflowImpl();
    flow.setCollectiveSelection(new Dataflow.CollectiveSelection(q.qualified, q.f.producer.getObservable(),
        List.of(q.beech), List.of(q.species), List.of("species")));
    flow.getMetadata().put(CollectiveSelectionBinding.DATA_KEY, Utils.Json.asString(binding));
    var source = new ActuatorImpl(); source.setActuatorType(Actuator.Type.REFERENCE);
    source.setObservation(q.f.producer); source.setId(q.f.producer.getId()); source.setName("native");
    source.setCoverage(q.request.getGeometry());
    var operation = new ActuatorImpl(); operation.setName("species"); operation.setActuatorType(Actuator.Type.UPDATE);
    operation.setEffect(Actuator.Effect.SEMANTIC_UPDATE); operation.setContextualization(Contextualization.CLASSIFICATION);
    operation.setOperationObservable(q.directive(q.species.getUrn()));
    operation.getData().put(Actuator.CLASSIFICATION_CHECK_ONLY, true);
    flow.getComputation().add(source); flow.getComputation().add(operation);
    return flow;
  }

  private DataflowImpl parent(String... aliases) {
    var meaning = new ConceptImpl(); meaning.setUrn("test:PopulationSize"); meaning.setReferenceName("test_population_size");
    meaning.setType(EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE));
    meaning.setDescriptionType(Contextualization.QUANTIFICATION);
    var observable = new ObservableImpl(); observable.setSemantics(meaning); observable.setUrn(meaning.getUrn());
    observable.setDescriptionType(Contextualization.QUANTIFICATION);
    var observation = new ObservationImpl(); observation.setId(-90); observation.setUrn("population-size");
    observation.setObservable(observable); observation.setGeometry(q.request.getGeometry());
    var root = new ActuatorImpl(); root.setActuatorType(Actuator.Type.OBSERVE); root.setId(observation.getId());
    root.setObservation(observation); root.setOperationObservable(observable); root.setName("parent");
    for (var alias : aliases) {
      var port = new ActuatorImpl(); port.setActuatorType(Actuator.Type.REFERENCE); port.setId(Observation.QUERY_ID);
      port.setName(alias); port.setObservation(Observation.forTransport(q.request)); port.setCoverage(q.request.getGeometry());
      port.setCollectiveSelectionDependency(selection()); root.getChildren().add(port);
    }
    var flow = new DataflowImpl(); flow.getComputation().add(root); flow.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
    return flow;
  }

  @Test void ordinaryParentRetainsItsIdentityAndBothLocalAliasesReceiveTheSelectedMembers() {
    var incoming = parent("selected", "another_local_name");
    var before = Set.copyOf(q.f.root.assets()); var validations = new ArrayList<Runnable>();
    var resolved = SelectedModelClassifierContinuation.resume(q.f.runtime, incoming, q.f.scope, validations).join();
    var root = resolved.getComputation().getFirst();
    assertEquals(-90, root.getObservation().getId()); assertEquals("test:PopulationSize", root.getObservation().getObservable().getSemantics().getUrn());
    assertNull(resolved.getCollectiveSelection()); assertEquals(Dataflow.ResolutionOutcome.RESOLVED, resolved.getResolutionOutcome());
    assertEquals(List.of("selected", "another_local_name"), root.getChildren().stream().map(Actuator::getName).toList());
    for (var port : root.getChildren()) {
      assertNull(port.getCollectiveSelectionDependency()); assertEquals(Observation.QUERY_ID, port.getObservation().getId());
      var proof = q.proof(port.getObservation());
      assertEquals(List.of(selectedMember.getId()), proof.selectedIds()); assertEquals(2, proof.members().size());
      assertEquals(1, port.getObservation().getChildrenCount());
      assertEquals(List.of(selectedMember.getId()), q.f.runtime.getMembers(port.getObservation(), q.f.scope).stream().map(Observation::getId).toList());
    }
    validations.forEach(Runnable::run);
    assertEquals(before, Set.copyOf(q.f.root.assets()), "Known selections must not allocate producer/update assets");
    assertTrue(q.f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .noneMatch(a -> a.getType() == Activity.Type.CLASSIFICATION));
    verify(q.f.runtime, never()).executeClassifierProducer(any(), any(), any());
  }

  @Test void parentFailureAfterNestedPreparationCannotCommitItsDiscoveryAsAnIndependentSubmission() {
    var originalMeaning = selectedMember.getObservable();
    var resolved = SelectedModelClassifierContinuation.resume(q.f.runtime, parent("selected"), q.f.scope, new ArrayList<>()).join();
    assertEquals(1, resolved.getComputation().getFirst().getChildren().getFirst().getObservation().getChildrenCount());
    q.f.root.fail(new IllegalStateException("The downstream parent consumer failed"));
    assertEquals(-1, q.f.root.commit()); assertTrue(q.f.durable.isEmpty()); assertSame(originalMeaning, selectedMember.getObservable());
    verify(q.f.storage, never()).store(any());
  }

  @Test void rootSelectionWithNestedInputsRequiresAndRetainsItsExplicitSubmissionFinalizer() {
    // This tests finalizer ownership, not resolution of this schematic outer producer.
    var incoming = parent("selected");
    incoming.setCollectiveSelection(selection().getCollectiveSelection());
    var finalizers = new ArrayList<Runnable>();
    var result = SelectedModelClassifierContinuation.resume(q.f.runtime, incoming, q.f.scope, new ArrayList<>(), finalizers).join();
    assertEquals(1, finalizers.size(), "The outer selection must seal nested inputs after its own family effects");
    finalizers.forEach(Runnable::run);
    var port = result.getComputation().getFirst().getChildren().getFirst();
    assertEquals(List.of(selectedMember.getId()), q.proof(port.getObservation()).selectedIds());
  }
  private void attributeAge() {
    var enriched = q.observable("test:Beech test:Young test:Stream", false);
    q.traits.put(enriched.getSemantics().getUrn(), List.of(q.beech, q.young));
    q.ancestors.put(enriched.getSemantics().getUrn(),
        Set.of(q.f.producer.getObservable().getSemantics().singular().getUrn()));
    var original = selectedMember.getObservable();
    doAnswer(call -> {
      var build = call.getArgument(0, ObservableBuildStrategy.class);
      if (build.getBaseObservable() != null && build.getBaseObservable().getUrn().equals(original.getUrn()))
        return enriched;
      if (build.getBaseObservable() != null && build.getBaseObservable().getUrn().equals(q.qualified.getUrn()))
        return q.f.producer.getObservable();
      return q.directive(build.getBaseConcept().getUrn());
    }).when(q.reasoner).buildObservable(any(), eq(q.f.scope));
    var update = (org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl) q.f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION, q.f.root.getActivity(), "Other family"), q.f.scope);
    update.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(selectedMember,
        original, q.age, q.young, q.request.getGeometry(),
        org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.initialization())), q.f.scope);
    q.recordAttribution(update, selectedMember, q.age, q.young, original);
    assertTrue(update.commit() >= 0);
  }

  @Test void liveSelectedInputRefreshesAfterOwnOtherFamilyButTransportedReplayRemainsStrict() {
    var resolved = SelectedModelClassifierContinuation.resume(q.f.runtime, parent("selected"), q.f.scope, new ArrayList<>()).join();
    var view = resolved.getComputation().getFirst().getChildren().getFirst().getObservation();
    var transported = Observation.forTransport(view);
    String before = q.proof(view).members().stream().filter(m -> m.id() == selectedMember.getId()).findFirst().orElseThrow().observable();
    attributeAge();
    assertEquals(List.of(selectedMember.getId()), q.f.runtime.getMembers(view, q.f.scope).stream().map(Observation::getId).toList());
    assertNotEquals(before, q.proof(view).members().stream().filter(m -> m.id() == selectedMember.getId()).findFirst().orElseThrow().observable());
    assertThrows(IllegalStateException.class, () -> q.f.runtime.getMembers(transported, q.f.scope),
        "A transported old receipt has no live same-attempt capability");
    assertEquals(List.of(q.beech, q.young), q.reasoner.directTraits(q.f.root.readObservation(selectedMember).getObservable()));
  }

  @Test void liveSelectedInputCannotSilentlyAcceptNewMembersDuringItsConsumingAttempt() {
    var resolved = SelectedModelClassifierContinuation.resume(q.f.runtime, parent("selected"), q.f.scope, new ArrayList<>()).join();
    var view = resolved.getComputation().getFirst().getChildren().getFirst().getObservation();
    q.member(-32, "AnotherBeech", q.beech).setUrn("another-beech");
    assertThrows(IllegalStateException.class, () -> q.f.runtime.getMembers(view, q.f.scope));
  }

}
