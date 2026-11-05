package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Actual compiled classifier/consumer calls and atomic persistence; discovery is precompleted.
 * Characterization is deliberately stubbed, so these do not certify that separate lifecycle. */
class CollectiveSelectionAtomicExecutionTest implements AutoCloseable {
  final CollectiveSelectionExecutionTest q = new CollectiveSelectionExecutionTest();
  final List<String> calls = new ArrayList<>();
  boolean failAge;
  Observation received;
  ComponentRegistry registry;
  org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles cycles;
  org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles.Admission admission;
  org.mockito.MockedStatic<SemanticExecutionFootprints> fixtureFootprint;

  @AfterEach public void close() { if(admission!=null)admission.close(); if(cycles!=null)cycles.close(); if (fixtureFootprint != null) fixtureFootprint.close(); }

  public Concept classify(Observable directive, Observation member, ContextScope scope, ServiceCall call) {
    calls.add(call.getUrn());
    if (call.getUrn().equals("test.age") && failAge) throw new IllegalStateException("deliberate age failure");
    return call.getUrn().equals("test.species") ? q.beech : q.mature;
  }

  public boolean failConsumer(@org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction.Input(name="selected") Observation selected) {
    received = selected;
    return false;
  }

  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }

  @BeforeEach void setup() throws Exception {
    q.setup();
    doReturn("atomic-fixture-runtime").when(q.f.runtime).serviceId();
    doReturn(mock(org.integratedmodelling.klab.api.configuration.Settings.class)).when(q.f.runtime).settings();
    var user=mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
    when(user.getUsername()).thenReturn("atomic-fixture-user");when(q.f.scope.getUser()).thenReturn(user);
    when(q.f.scope.withCycleSubscriberCancellation(any())).thenReturn(q.f.scope);
    when(q.f.scope.withCancellation(any())).thenReturn(q.f.scope);
    when(q.f.scope.withoutConsequenceSubmissionAdmission()).thenReturn(q.f.scope);
    org.integratedmodelling.klab.services.runtime.digitaltwin.CycleFixtureSupport.transparentCycleOverlay(q.f.scope);
    when(q.f.scope.withResolutionCycle(any(),any())).thenAnswer(call -> {
      doReturn(call.getArgument(0)).when(q.f.scope).getResolutionCycle();
      doReturn(call.getArgument(1)).when(q.f.scope).getResolutionCycleBinding();
      return q.f.scope;
    });
    when(q.f.scope.getServices(Reasoner.class)).thenReturn(List.of(q.reasoner));
    when(q.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(call ->
        new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult(
            org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult.Status.BOUND,
            new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding(
                org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding.VERSION,
                q.capabilities.getServiceId(),q.capabilities.getSemanticSessionId(),q.capabilities.getKnowledgeRevision(),
                q.capabilities.getSemanticContentFingerprint(),"fixture-lease-"+call.getArgument(2)),null));
    when(q.reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(q.reasoner);
    cycles=new org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles(q.f.runtime,q.f.scope.getId());
    when(q.f.twin.getResolutionCycles()).thenReturn(cycles);
    // Admit the root before exposing its causal transaction, as normal submission does.
    when(q.f.scope.getCurrentTransaction()).thenReturn(null);
    admission=cycles.admit(q.f.scope,true).join();
    when(q.f.scope.getCurrentTransaction()).thenReturn(q.f.root);
    q.traits.put(q.qualified.getSemantics().getUrn(), List.of(q.beech, q.mature));
    registry = mock(ComponentRegistry.class);
    doReturn(registry).when(q.f.runtime).getComponentRegistry();
    doReturn(new ShardExecution(() -> 1)).when(q.f.runtime).shardExecution();
    doNothing().when(q.f.runtime).characterizePending(anyList(), any());
    doReturn(q.f.cohort).when(q.f.runtime).getCohortFor(any(), any(), eq(false));
    when(q.f.graph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
    // Compiled collective consumers reuse the fixture's existing cohort.
    doReturn(q.f.cohort).when(q.f.runtime).getCohortFor(any(),any(),eq(true));
    // The query-only persistence fixture did not allocate identities for compiled Actuators.
    // A real compiled classification plan persists these alongside its observations/activities.
    var ids = new java.util.concurrent.atomic.AtomicLong(2000);
    doAnswer(call -> {
      RuntimeAsset asset = call.getArgument(0);
      long id = ids.incrementAndGet();
      if (asset instanceof ObservationImpl observation) observation.setId(id);
      if (asset instanceof org.integratedmodelling.common.knowledge.CohortImpl cohort) cohort.setId(id);
      if (asset instanceof org.integratedmodelling.klab.api.provenance.impl.ActivityImpl activity) activity.setId(id);
      if (asset instanceof ActuatorImpl actuator) actuator.setId(id);
      q.f.staged.put(asset.getId(), asset);
      return null;
    }).when(q.f.storage).store(any());
    when(q.reasoner.capabilities(any())).thenReturn(q.capabilities);
    when(q.reasoner.buildConcept(any(), any())).thenAnswer(call -> {
      var strategy = call.getArgument(0, ObservableBuildStrategy.class);
      return strategy.getBaseObservable() == null ? strategy.getBaseConcept() : strategy.getBaseObservable().getSemantics();
    });
    when(q.reasoner.buildObservable(any(), any())).thenAnswer(call -> build(call.getArgument(0)));
    when(q.f.scope.executing(any(Activity.class), any(Object[].class))).thenAnswer(call -> child(call.getArgument(0)));
    var classifier = new Extensions.FunctionDescriptor();
    var impl = new ComponentRegistry.ServiceImplementation();
    impl.mainClassInstance = this;
    impl.method = getClass().getMethod("classify", Observable.class, Observation.class, ContextScope.class, ServiceCall.class);
    when(registry.getFunctionDescriptor(any(ServiceCall.class), any())).thenReturn(List.of(classifier));
    when(registry.implementation(classifier)).thenReturn(impl);
    // This test controls the semantic-footprint contract of its exact in-file classifier.
    // Production footprint admission and actual gated core fixture execution are tested
    // separately; no production whitelist or successful completion receipt is fabricated.
    fixtureFootprint = mockStatic(SemanticExecutionFootprints.class, CALLS_REAL_METHODS);
    fixtureFootprint.when(() -> SemanticExecutionFootprints.function(any(), eq(impl.method), same(this), any(), any()))
        .thenReturn(true);
    when(q.reasoner.semanticDependencies(any(), any())).thenAnswer(call -> {
      var request = call.getArgument(0, org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class);
      return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.VERSION,
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE,
          request.expectedServiceId(), request.expectedKnowledgeRevision(), request.expectedBaseFingerprint(),
          request.inputHash(), List.of(), null);
    });
  }

  private Observable build(ObservableBuildStrategy strategy) {
    var base = strategy.getBaseObservable();
    if (base == null) return q.directive(strategy.getBaseConcept().getUrn());
    if (base.getUrn().equals(q.qualified.getUrn())) return q.f.producer.getObservable();
    var values = new ArrayList<>(q.traits.getOrDefault(base.getSemantics().getUrn(), List.of()));
    for (var operation : strategy.getOperations()) {
      if (operation.getType() == ObservableBuildStrategy.OperationType.WITH_TRAITS
          || operation.getType() == ObservableBuildStrategy.OperationType.WITH_ROLE)
        values.addAll(operation.getConcepts());
    }
    String urn = values.stream().map(Concept::getUrn).sorted().reduce("", (a,b) -> a + b + " ") + "test:Stream";
    var result = q.observable(urn, false);
    q.traits.put(result.getSemantics().getUrn(), List.copyOf(values));
    q.ancestors.put(result.getSemantics().getUrn(), Set.of(q.f.producer.getObservable().getSemantics().singular().getUrn()));
    return result;
  }

  private ServiceContextScope child(Activity activity) {
    var scopeId = q.f.scope.getId();
    var scope = mock(ServiceContextScope.class);
    when(scope.getId()).thenReturn(scopeId);
    var transaction = q.f.root.getChild(activity, scope);
    when(scope.getDigitalTwin()).thenReturn(q.f.twin);
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    when(scope.getActivity()).thenReturn(activity);
    when(scope.getContextObservation()).thenAnswer(call -> q.f.scope.getContextObservation());
    when(scope.getObserver()).thenAnswer(call -> q.f.scope.getObserver());
    when(scope.getService(Reasoner.class)).thenReturn(q.reasoner);
    when(scope.getService(RuntimeService.class)).thenReturn(q.f.runtime);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(q.f.runtime);
    when(scope.within(any(Observation.class))).thenReturn(scope);
    when(scope.commit()).thenAnswer(call -> transaction.commit());
    doAnswer(call -> { transaction.fail(call.getArgument(0)); return null; }).when(scope).fail(any(Throwable.class));
    return scope;
  }

  private ActuatorImpl operation(String name, String family) {
    var node = new ActuatorImpl();
    node.setName(name); node.setActuatorType(Actuator.Type.UPDATE); node.setEffect(Actuator.Effect.SEMANTIC_UPDATE);
    node.setContextualization(Contextualization.CLASSIFICATION); node.setOperationObservable(q.directive(family));
    node.setRequestedSupport(q.request.getGeometry());
    node.getComputation().add(new ServiceCallImpl("test." + name));
    var members = new ActuatorImpl(); members.setName("members_0"); members.setActuatorType(Actuator.Type.REFERENCE);
    members.setObservation(q.f.query); members.setId(Observation.QUERY_ID);
    members.getData().put(Actuator.COMPLETE_COHORT_REFERENCE, true);
    node.getChildren().add(members);
    var binding = new ActuatorImpl.TargetBindingImpl(); binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
    binding.setSources(new ArrayList<>(List.of("members_0"))); node.getTargetBindings().add(binding);
    q.admitPlan(node);
    return node;
  }

  DataflowImpl plan() {
    var binding = CollectiveSelectionBinding.capture(q.request, q.f.scope, q.reasoner);
    assertNotNull(binding);
    var plan = new DataflowImpl();
    plan.setCollectiveSelection(new Dataflow.CollectiveSelection(q.qualified, q.f.producer.getObservable(),
        List.of(q.beech, q.mature), List.of(q.species, q.age), List.of("species", "age")));
    plan.getMetadata().put(CollectiveSelectionBinding.DATA_KEY, Utils.Json.asString(binding));
    var nativeReference = new ActuatorImpl(); nativeReference.setActuatorType(Actuator.Type.REFERENCE);
    nativeReference.setObservation(q.f.producer); nativeReference.setId(q.f.producer.getId());
    nativeReference.setCoverage(q.request.getGeometry());
    plan.getComputation().add(nativeReference);
    plan.getComputation().add(operation("species", q.species.getUrn()));
    plan.getComputation().add(operation("age", q.age.getUrn()));
    return plan;
  }

  @Test void secondActualClassifierFailureRollsBackFirstFamilyAndDoesNotPersistSelectedProducer() {
    var member = q.member(-30, "Unclassified"); member.setUrn("member-original"); q.complete();
    var before = member.getObservable(); failAge = true;
    assertThrows(IllegalStateException.class, () -> CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>()));
    assertEquals(List.of("test.species", "test.age"), calls, "Both actual compiled classifier methods must have run");
    assertSame(before, member.getObservable(), "Tentative first-family result must not change the original member");
    assertTrue(q.f.root.isFailed()); assertEquals(-1, q.f.root.commit()); assertTrue(q.f.durable.isEmpty());
    verify(q.f.storage, never()).store(any());
  }

  @Test void successfulFamiliesShareTheOriginalMemberAndPublishOneConjunctiveView() {
    var member = q.member(-30, "Unclassified"); member.setUrn("member-original"); q.complete();
    var before = member.getObservable();
    var view = CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>());
    assertEquals(List.of("test.species", "test.age"), calls);
    assertEquals(List.of(-30L), q.proof(view).selectedIds()); assertEquals(1, view.getChildrenCount());
    assertSame(before, member.getObservable(), "Pending attribution is not publication");
    assertEquals(Set.of(q.beech.getUrn(), q.mature.getUrn()), q.reasoner.directTraits(q.f.root.readObservation(member).getObservable()).stream().map(Concept::getUrn).collect(java.util.stream.Collectors.toSet()));
    assertTrue(q.f.root.commit() > 0, () -> "Commit failed: " + mockingDetails(q.f.scope).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("error"))
        .flatMap(invocation -> Arrays.stream(invocation.getArguments())).map(String::valueOf).toList());
    assertEquals(List.of(member.getId()), q.proof(view).selectedIds()); assertTrue(member.getId() > 0);
    assertEquals(1, q.f.durable.values().stream().filter(Observation.class::isInstance).map(Observation.class::cast)
        .filter(o -> o.getId() == member.getId()).count());
    assertTrue(q.f.durable.values().stream().filter(Observation.class::isInstance).map(Observation.class::cast)
        .noneMatch(o -> q.qualified.getUrn().equals(o.getObservable().getUrn())));
  }

  @Test void selectedInputAssociationRequiresOriginalReceiptAndExactCompiledPort() {
    q.member(-30, "Unclassified").setUrn("member-original"); q.complete();
    var selected = CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>());
    var consumer = new ObservationImpl(); consumer.setId(-50);
    var parent = new ActuatorImpl(); parent.setObservation(consumer);
    var child = new ActuatorImpl(); child.setActuatorType(Actuator.Type.REFERENCE);
    child.setObservation(selected); child.setName("selected"); parent.getChildren().add(child);
    var cohort = q.f.runtime.getCohortFor(selected.getObservable().getSemantics().singular(), q.f.scope, false);
    var association = SelectedQueryInput.capture(q.f.root, cohort, consumer, selected, parent, 0, q.f.scope);
    var encoded = association.require(q.f.root, cohort, consumer, q.f.scope);
    assertSame(selected, SelectedQueryInput.requireBinding(encoded, parent));

    var copy = Observation.forTransport(selected); child.setObservation(copy);
    assertThrows(IllegalStateException.class,
        () -> SelectedQueryInput.capture(q.f.root, cohort, consumer, copy, parent, 0, q.f.scope));
    child.setObservation(selected); child.setName("forged");
    assertThrows(IllegalStateException.class, () -> SelectedQueryInput.requireBinding(encoded, parent));
    assertThrows(IllegalStateException.class,
        () -> association.require(q.f.root, cohort, consumer, q.f.scope));
    child.setName("selected");
    var count = selected.getChildrenCount();
    ((ObservationImpl) selected).setChildrenCount(count + 1);
    assertThrows(IllegalStateException.class,
        () -> SelectedQueryInput.capture(q.f.root, cohort, consumer, selected, parent, 0, q.f.scope));
    ((ObservationImpl) selected).setChildrenCount(count);
    var second = new ActuatorImpl(); second.setActuatorType(Actuator.Type.REFERENCE);
    second.setObservation(selected); second.setName("second"); parent.getChildren().add(second);
    var secondAssociation = SelectedQueryInput.capture(q.f.root, cohort, consumer, selected, parent, 1, q.f.scope);
    assertNotEquals(encoded, secondAssociation.require(q.f.root, cohort, consumer, q.f.scope));
    second.setName("selected");
    assertThrows(IllegalStateException.class,
        () -> SelectedQueryInput.capture(q.f.root, cohort, consumer, selected, parent, 0, q.f.scope));
    parent.getChildren().remove(second);
    var foreign = mock(DigitalTwinImpl.TransactionImpl.class);
    assertThrows(IllegalStateException.class,
        () -> association.require(foreign, cohort, consumer, q.f.scope));
    when(q.f.scope.getCurrentTransaction()).thenReturn(foreign);
    assertThrows(IllegalStateException.class,
        () -> SelectedQueryInput.capture(foreign, cohort, consumer, selected, parent, 0, q.f.scope));
    when(q.f.scope.getCurrentTransaction()).thenReturn(q.f.root);
    q.f.root.fail(new IllegalStateException("rollback"));
    assertThrows(IllegalStateException.class,
        () -> association.require(q.f.root, cohort, consumer, q.f.scope));
  }

  @Test void selectedInputStorageUsesTheActiveChildScopeRatherThanItsRootsStorageScope() {
    q.member(-30, "Unclassified").setUrn("member-original"); q.complete();
    var selected = CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>());
    var consumer = new ObservationImpl(); consumer.setId(-50); q.f.root.add(consumer);
    var parent = new ActuatorImpl(); parent.setObservation(consumer);
    var reference = new ActuatorImpl(); reference.setActuatorType(Actuator.Type.REFERENCE);
    reference.setObservation(selected); reference.setName("selected"); parent.getChildren().add(reference);
    var cohort = q.f.runtime.getCohortFor(selected.getObservable().getSemantics().singular(), q.f.scope, false);
    var execution = child(Activity.of(Activity.Type.RESOLUTION));
    var transaction = (DigitalTwinImpl.TransactionImpl) execution.getCurrentTransaction();
    assertSame(q.f.root, q.f.scope.getCurrentTransaction());
    assertNotSame(q.f.root, transaction);
    var input = SelectedQueryInput.capture(transaction, cohort, consumer, selected, parent, 0, execution);
    assertThrows(IllegalStateException.class,
        () -> transaction.linkSelectedQueryInput(cohort, consumer, input, q.f.scope));
    assertDoesNotThrow(() -> transaction.linkSelectedQueryInput(cohort, consumer, input, execution));
    assertTrue(q.f.root.outgoing(cohort).stream().anyMatch(link ->
        link.target() == consumer && link.type() == GraphModel.Relationship.AFFECTS));
    var other = child(Activity.of(Activity.Type.RESOLUTION));
    assertThrows(IllegalStateException.class, () ->
        ((DigitalTwinImpl.TransactionImpl) other.getCurrentTransaction())
            .linkSelectedQueryInput(cohort, consumer, input, other));
    q.f.root.fail(new IllegalStateException("rollback"));
    assertThrows(IllegalStateException.class,
        () -> transaction.linkSelectedQueryInput(cohort, consumer, input, execution));
  }

  @Test void actualConsumerFailureReceivesSelectedAliasAndRootRollbackDiscardsClassification() throws Exception {
    var member = q.member(-30, "Unclassified"); member.setUrn("member-original");
    q.member(-31, "KnownNonmatching", q.oak, q.mature).setUrn("other-original");
    q.f.producer.setUrn("native-focus");
    when(q.f.scope.getContextObservation()).thenReturn(q.f.producer);
    q.complete();
    var before = member.getObservable();
    var selected = CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>());
    var parent = new ObservationImpl(); parent.setId(-50); parent.setUrn("consumer");
    parent.setObservable(q.observable("test:Consumer", false)); parent.setGeometry(q.request.getGeometry()); q.f.root.add(parent);
    // Public submission stages the consumer after its selected prerequisites have completed.
    // This increments the enclosing focus's child count without changing the selection context.
    int focusChildren = q.f.producer.getChildrenCount();
    q.f.root.link(q.f.producer, parent, GraphModel.Relationship.HAS_CHILD);
    assertEquals(focusChildren + 1, q.f.producer.getChildrenCount());
    var root = new ActuatorImpl(); root.setActuatorType(Actuator.Type.OBSERVE); root.setObservation(parent); root.setId(parent.getId());
    root.getComputation().add(new ServiceCallImpl("test.consumer"));
    var input = new ActuatorImpl(); input.setActuatorType(Actuator.Type.REFERENCE); input.setObservation(selected); input.setId(Observation.QUERY_ID); input.setName("selected"); root.getChildren().add(input);
    var descriptor = new Extensions.FunctionDescriptor(); descriptor.staticMethod = false;
    var info = new ServiceInfoImpl(); info.setName("test.consumer"); var argument = new ServiceInfoImpl.ArgumentImpl(); argument.setName("selected"); info.getInputs().add(argument); descriptor.serviceInfo = info;
    var impl = new ComponentRegistry.ServiceImplementation(); impl.mainClassInstance = this; impl.method = getClass().getMethod("failConsumer", Observation.class);
    when(registry.getFunctionDescriptor(argThat(call -> call != null && "test.consumer".equals(call.getUrn())),
        any(org.integratedmodelling.klab.api.scope.Scope.class))).thenReturn(List.of(descriptor));
    when(registry.implementation(descriptor)).thenReturn(impl);
    var compiled = new CompiledDataflow(q.f.runtime, parent, q.f.scope);
    assertTrue(compiled.compile(root)); assertTrue(compiled.store(q.f.root));
    assertFalse(compiled.executeRoot(parent.getGeometry(), org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.initialization(), q.f.scope));
    assertNotNull(received); assertEquals(Observation.QUERY_ID, received.getId());
    assertEquals(q.proof(selected), q.proof(received)); assertEquals(1, received.getChildrenCount());
    assertEquals(2, q.proof(received).members().size(), "Consumer input must be a proper selection of the two-member native cohort");
    q.f.root.fail(new IllegalStateException("Consumer failed, as ordinary submission propagates"));
    assertEquals(-1, q.f.root.commit()); assertSame(before, member.getObservable()); assertTrue(q.f.durable.isEmpty());
    verify(q.f.storage, never()).store(any());
  }

  @Test void classifyingTheNativeFocusPublishesItsProjectedMeaningInBothViewAndSourceReceipt() {
    var focus = q.member(-30, "UnclassifiedFocus"); focus.setUrn("native-focus");
    when(q.f.scope.getContextObservation()).thenReturn(focus);
    q.complete();
    var discovery = q.f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .filter(activity -> activity.getType() == Activity.Type.INSTANTIATION).findFirst().orElseThrow();
    RuntimeService.recordCollectiveSemanticRevision(discovery, q.f.scope);
    var original = focus.getObservable();
    var view = CollectiveSelectionExecution.execute(q.f.runtime, plan(), q.request, q.f.scope, new ArrayList<>());
    var staged = q.f.root.readObservation(focus);
    assertSame(original, focus.getObservable(), "Admission still sees the original focus until root publication");
    assertNotEquals(original.getSemantics().getUrn(), staged.getObservable().getSemantics().getUrn());
    assertEquals(staged.getObservable().getSemantics().getUrn(), q.proof(view).context().semantics());
    assertTrue(q.f.root.commit() > 0, () -> "Commit failed: " + mockingDetails(q.f.scope).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("error"))
        .flatMap(invocation -> Arrays.stream(invocation.getArguments())).map(String::valueOf).toList());
    var published = q.proof(view).context();
    assertEquals(focus.getId(), published.id()); assertTrue(published.id() > 0);
    assertEquals(0, published.transientId());
    assertEquals(focus.getObservable().getSemantics().getUrn(), published.semantics());
    var source = Utils.Json.parseObject(discovery.getMetadata().get(RuntimeService.COLLECTIVE_CONTEXT).toString(), CollectiveSelectionBinding.Context.class);
    assertEquals(published, source, "Source completion and returned view must describe the same final focus");
  }
}
