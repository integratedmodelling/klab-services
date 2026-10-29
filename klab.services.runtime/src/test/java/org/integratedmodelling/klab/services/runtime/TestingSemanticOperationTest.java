package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.MockedConstruction;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

class TestingSemanticOperationTest {
  @BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  static ObservationImpl observation(long id, SemanticType type) {
    var meaning=new ConceptImpl();meaning.setUrn("test:"+type);meaning.setType(EnumSet.of(type));
    var observable=new ObservableImpl();observable.setSemantics(meaning);observable.setUrn(meaning.getUrn());observable.setArtifactType(Artifact.Type.OBJECT);
    var result=new ObservationImpl();result.setId(id);result.setObservable(observable);result.setGeometry(Geometry.create("S2(1,1)"));return result;
  }
  static class Fixture {
    final RuntimeService runtime=mock(RuntimeService.class);
    final ServiceContextScope scope=mock(ServiceContextScope.class),attempt=mock(ServiceContextScope.class),resolution=mock(ServiceContextScope.class);
    final TestCaseBase.TestCaseScope caller=mock(TestCaseBase.TestCaseScope.class);
    final TestCaseBase agent=mock(TestCaseBase.class);
    final Resolver resolver=mock(Resolver.class);final Reasoner reasoner=mock(Reasoner.class);
    final Reasoner.Capabilities capabilities=mock(Reasoner.Capabilities.class);
    final DigitalTwinImpl.TransactionImpl transaction=mock(DigitalTwinImpl.TransactionImpl.class),resolutionTransaction=mock(DigitalTwinImpl.TransactionImpl.class);
    final ObservationImpl context=observation(41,SemanticType.SUBJECT),observer=observation(52,SemanticType.AGENT);
    final ObservationImpl producer=observation(-20,SemanticType.SUBJECT);
    final ActuatorImpl source=new ActuatorImpl();
    final ObservableImpl directive=mock(ObservableImpl.class);final Geometry support=Geometry.create("S2(1,1)");
    final DataflowImpl plan=new DataflowImpl(); final ActuatorImpl root=new ActuatorImpl();
    final AtomicReference<Activity> activity=new AtomicReference<>();final AtomicLong revision=new AtomicLong(7);
    final AtomicReference<BooleanSupplier> cancelled=new AtomicReference<>(()->false);
    Fixture() {
      when(caller.getAgent()).thenReturn(agent);when(agent.status()).thenReturn("running");
      when(caller.ownsContext(any())).thenReturn(true);
      when(directive.getSemantics()).thenReturn(context.getObservable().getSemantics());when(directive.getUrn()).thenReturn("test:Family of each test:Subject");when(directive.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
      when(scope.getContextObservation()).thenReturn(context);when(scope.getObserver()).thenReturn(observer);when(scope.getId()).thenReturn("session.twin");
      var observerView=new org.integratedmodelling.klab.api.services.runtime.objects.ObserverGeometryView();
      observerView.setObserver(Observation.forTransport(observer));
      when(runtime.getObserverGeometry(52,scope)).thenReturn(observerView);
      when(scope.getConstraint(ResolutionConstraint.Type.Provenance,Agent.class)).thenReturn(mock(Agent.class));
      when(scope.withCancellation(any())).thenAnswer(i->{cancelled.set(i.getArgument(0));return scope;});
      when(scope.executingFresh(any(),any())).thenAnswer(i->{activity.set(i.getArgument(0));return attempt;});
      when(attempt.getCurrentTransaction()).thenReturn(transaction);when(attempt.executing(any())).thenReturn(resolution);
      when(attempt.isInterrupted()).thenAnswer(i->cancelled.get().getAsBoolean());
      when(resolution.getCurrentTransaction()).thenReturn(resolutionTransaction);when(resolution.getContextObservation()).thenReturn(context);
      when(scope.getService(Resolver.class)).thenReturn(resolver);when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(reasoner.capabilities(scope)).thenReturn(capabilities);when(capabilities.getServiceId()).thenReturn("reasoner");when(capabilities.getKnowledgeRevision()).thenAnswer(i->revision.get());
      when(resolver.resolve(any(),eq(resolution))).thenReturn(CompletableFuture.completedFuture(plan));
      when(resolution.commit()).thenReturn(0L);
      when(attempt.commit()).thenAnswer(i->{((ActivityImpl)activity.get()).setId(81);return 91L;});
      root.setActuatorType(Actuator.Type.UPDATE);root.setEffect(Actuator.Effect.SEMANTIC_UPDATE);root.setContextualization(Contextualization.CLASSIFICATION);root.setOperationObservable(directive);plan.getComputation().add(root);
      ((ConceptImpl)producer.getObservable().getSemantics()).setCollective(true);
      source.setName("members");source.setActuatorType(Actuator.Type.OBSERVE);source.setObservation(producer);source.setId(producer.getId());
      root.getChildren().add(source);
      var binding=new ActuatorImpl.TargetBindingImpl();binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
      binding.setSources(new ArrayList<>(List.of("members")));root.getTargetBindings().add(binding);
      when(runtime.register(producer,resolution)).thenReturn(producer);
    }
    MockedConstruction<CompiledDataflow> compiled(boolean execute) {
      return mockConstruction(CompiledDataflow.class,(compiled,construction)->{
        assertSame(producer,construction.arguments().get(1));
        when(compiled.compile(root)).thenReturn(true);when(compiled.store(resolutionTransaction)).thenReturn(true);
        when(compiled.executeRoot(eq(support),any(),eq(attempt))).thenReturn(execute);
      });
    }
  }
  @Test void onlyNativeTestCasesCanExecute() {
    var f=new Fixture();
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,mock(RuntimeAgent.Scope.class)));
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,mock(org.integratedmodelling.klab.api.scope.ContextScope.class),f.caller));
    when(f.scope.getCurrentTransaction()).thenReturn(f.transaction);
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
    verify(f.scope,never()).executingFresh(any(),any());
  }
  @Test void successfulExecutionReturnsTypedCommittedReceiptWithoutObservationSubmission() {
    var f=new Fixture();try(var compiled=f.compiled(true)) {
      var receipt=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller).join();
      assertEquals(91,receipt.commitId());assertEquals(81,receipt.activityId());assertEquals("session.twin",receipt.contextId());
      verify(f.resolver).resolve(argThat(request->request.getId()==Observation.UNASSIGNED_ID && request.getObservable()==f.directive && request.getGeometry()==f.support),eq(f.resolution));
      assertEquals(52L,f.activity.get().getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_OBSERVER_SELECTION_ID));
      verify(f.runtime).register(f.producer,f.resolution);verify(f.runtime,never()).submit(any(),any());
      verify(f.runtime).captureSelectionBaseline(f.observer.getObservable(),f.attempt);
      verify(f.transaction).beforeCommit(any(Runnable.class));
      verify(f.attempt,never()).contextualize(any());
      var order=inOrder(f.resolution,f.transaction,compiled.constructed().getFirst(),f.attempt);
      order.verify(f.resolution).commit();order.verify(f.transaction).registerExecutors();
      order.verify(compiled.constructed().getFirst()).executeRoot(eq(f.support),any(),eq(f.attempt));order.verify(f.attempt).commit();
    }
  }
  @Test void failedExecutionRollsBackRootWithoutSuccessfulReceipt() {
    var f=new Fixture();try(var compiled=f.compiled(false)) {
      assertThrows(CompletionException.class,()->TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller).join());
      verify(f.attempt).fail(any(Throwable.class));verify(f.attempt,never()).commit();
    }
  }
  @Test void unresolvedPlanCannotBecomeSuccessfulNoop() {
    var f=new Fixture();when(f.resolver.resolve(any(),any())).thenReturn(CompletableFuture.completedFuture(Dataflow.noModel(List.of())));
    assertThrows(CompletionException.class,()->TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller).join());
    verify(f.attempt).fail(any(Throwable.class));verify(f.attempt,never()).commit();
  }
  @Test void changedSemanticGenerationCannotCommit() {
    var f=new Fixture();var pending=new CompletableFuture<Dataflow>();when(f.resolver.resolve(any(),any())).thenReturn(pending);
    var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller);f.revision.incrementAndGet();pending.complete(f.plan);
    assertThrows(CompletionException.class,result::join);verify(f.attempt,never()).commit();verify(f.attempt).fail(any(Throwable.class));
  }
  @Test void failureReportingCannotStrandTheOperationFuture() {
    var f=new Fixture();var pending=new CompletableFuture<Dataflow>();when(f.resolver.resolve(any(),any())).thenReturn(pending);
    doThrow(new IllegalStateException("ActivityFinished delivery failed")).when(f.attempt).fail(any(Throwable.class));
    var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller);
    pending.completeExceptionally(new IllegalStateException("Resolver failed"));
    assertTrue(result.isCompletedExceptionally());
    var failure=assertThrows(CompletionException.class,result::join);
    assertTrue(failure.toString().contains("Resolver failed"));
    assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(t->t.getMessage().contains("delivery failed")));
    verify(f.attempt,never()).commit();
  }
  @Test void cancellationReachesPendingOperationBeforeItCanCommit() {
    var f=new Fixture();var pending=new CompletableFuture<Dataflow>();when(f.resolver.resolve(any(),any())).thenReturn(pending);
    var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller);assertTrue(result.cancel(true));pending.complete(f.plan);
    assertTrue(result.isCancelled());verify(f.attempt,never()).commit();verify(f.attempt).fail(any(Throwable.class));
  }
  @Test void observerRecoveryDistinguishesKnownNullFromMissingProvenance() {
    var f=new Fixture();when(f.scope.getObserver()).thenReturn(null);when(f.scope.withObserver(null)).thenReturn(f.scope);
    try(var provenance=mockStatic(ObserverProvenance.class,CALLS_REAL_METHODS)) {
      provenance.when(()->ObserverProvenance.recover(f.scope,f.context)).thenReturn(new ObserverProvenance.Selection(true,null));
      assertSame(f.scope,TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
      provenance.when(()->ObserverProvenance.recover(f.scope,f.context)).thenReturn(new ObserverProvenance.Selection(false,null));
      assertThrows(IllegalStateException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
      provenance.when(()->ObserverProvenance.recover(f.scope,f.context)).thenReturn(new ObserverProvenance.Selection(true,f.observer));
      when(f.scope.withObserver(f.observer)).thenReturn(f.resolution);
      assertSame(f.resolution,TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
    }
  }
  @Test void operationStoreDoesNotResolveOrCreateAnObservation() throws Exception {
    var f=new Fixture();var twin=mock(DigitalTwin.class);var graph=mock(KnowledgeGraph.class);
    when(f.scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);when(graph.dataflow()).thenReturn(mock(Dataflow.class));
    var compiled=new CompiledDataflow(f.runtime,null,f.scope,f.plan);
    var rootField=CompiledDataflow.class.getDeclaredField("rootActuator");rootField.setAccessible(true);rootField.set(compiled,f.root);
    var graphField=CompiledDataflow.class.getDeclaredField("dependencyGraph");graphField.setAccessible(true);
    graphField.set(compiled,mock(org.jgrapht.Graph.class));
    assertTrue(compiled.store(f.transaction));
    assertTrue(mockingDetails(f.transaction).getInvocations().stream().noneMatch(invocation->
        invocation.getMethod().getName().equals("link") && (invocation.getArgument(2)==GraphModel.Relationship.RESOLVED || invocation.getArgument(2)==GraphModel.Relationship.CREATED)));
    verify(f.runtime,never()).register(any(),any());
    clearInvocations(f.transaction);
    var anchored=new CompiledDataflow(f.runtime,f.context,f.scope,f.plan);rootField.set(anchored,f.root);graphField.set(anchored,mock(org.jgrapht.Graph.class));
    assertTrue(anchored.store(f.transaction));
    assertTrue(mockingDetails(f.transaction).getInvocations().stream().anyMatch(invocation->
        invocation.getMethod().getName().equals("link") && invocation.getArgument(1)==f.context && invocation.getArgument(2)==GraphModel.Relationship.RESOLVED));
    clearInvocations(f.transaction);f.root.setActuatorType(Actuator.Type.OBSERVE);
    var ordinary=new CompiledDataflow(f.runtime,f.context,f.scope,f.plan);rootField.set(ordinary,f.root);graphField.set(ordinary,mock(org.jgrapht.Graph.class));
    assertTrue(ordinary.store(f.transaction));
    assertTrue(mockingDetails(f.transaction).getInvocations().stream().anyMatch(invocation->
        invocation.getMethod().getName().equals("link") && invocation.getArgument(1)==f.context && invocation.getArgument(2)==GraphModel.Relationship.RESOLVED));
  }
  @Test void publicSubmitAndQueryContractDoesNotGainAnOperationOverload() {
    assertFalse(org.integratedmodelling.klab.api.services.RuntimeService.Testing.class.isAssignableFrom(org.integratedmodelling.common.services.client.RuntimeClient.class));
    assertEquals(1,Arrays.stream(org.integratedmodelling.klab.api.services.RuntimeService.class.getDeclaredMethods()).filter(m->m.getName().equals("submit")).count());
    assertThrows(IllegalArgumentException.class,()->new org.integratedmodelling.klab.api.services.RuntimeService.OperationReceipt("context",0,1,"directive"));
    var runtime=mock(RuntimeService.class);doCallRealMethod().when(runtime).register(any(),any());
    var request=observation(Observation.UNASSIGNED_ID,SemanticType.SUBJECT);
    var directive=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(directive.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);request.setObservable(directive);
    assertThrows(IllegalArgumentException.class,()->runtime.register(request,mock(ServiceContextScope.class)));
  }
  static ObservationImpl checkedQuery(Fixture f,long... ids) {
    var checked=new ObservationImpl();checked.setId(Observation.QUERY_ID);checked.setObservable(f.producer.getObservable());
    checked.setGeometry(f.support);checked.setResolvedCoverage(1);
    checked.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_SOURCE_IDS,Arrays.stream(ids).boxed().toList());
    checked.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_COHORT_ID,10L);return checked;
  }
  @Test void contextlessClassificationUsesExplicitObserverGeometryAndRealNativeProducer() {
    var f=new Fixture();when(f.scope.getContextObservation()).thenReturn(null);when(f.resolution.getContextObservation()).thenReturn(null);
    try(var compiled=f.compiled(true)) {
      var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller).join();
      assertEquals(91,result.commitId());
      assertTrue(mockingDetails(f.transaction).getInvocations().stream().noneMatch(i -> i.getMethod().getName().equals("link")
          && i.getArgument(2)==GraphModel.Relationship.HAS_CONTEXT));
      verify(f.transaction).link(f.activity.get(),f.observer,GraphModel.Relationship.HAS_OBSERVER);
      verify(f.runtime).register(f.producer,f.resolution);verify(f.runtime,never()).submit(any(),any());
    }
  }
  @Test void contextlessClassificationDoesNotInferKnownNullObserverOrCharacterization() {
    var f=new Fixture();when(f.scope.getContextObservation()).thenReturn(null);when(f.scope.getObserver()).thenReturn(null);
    try(var provenance=mockStatic(ObserverProvenance.class,CALLS_REAL_METHODS)) {
      assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
      provenance.verifyNoInteractions();
    }
    when(f.scope.getObserver()).thenReturn(f.observer);
    when(f.directive.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
  }
  @Test void inactiveStoppedOrForeignTestcaseAndUnboundedSupportAreDenied() {
    var f=new Fixture();when(f.caller.isDone()).thenReturn(true);
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
    when(f.caller.isDone()).thenReturn(false);when(f.agent.status()).thenReturn("stopped");
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
    when(f.agent.status()).thenReturn("running");when(f.caller.ownsContext(f.scope)).thenReturn(false);
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,f.support,f.scope,f.caller));
    when(f.caller.ownsContext(f.scope)).thenReturn(true);
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.validate(f.directive,Geometry.UNIVERSAL,f.scope,f.caller));
    verify(f.scope,never()).executingFresh(any(),any());
  }
  @Test void testcaseFinishingWhileResolutionIsPendingPreventsCommit() {
    var f=new Fixture();var pending=new CompletableFuture<Dataflow>();when(f.resolver.resolve(any(),any())).thenReturn(pending);
    var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller);
    when(f.caller.isDone()).thenReturn(true);pending.complete(f.plan);
    assertThrows(CompletionException.class,result::join);verify(f.attempt,never()).commit();verify(f.attempt).fail(any(Throwable.class));
  }
  @Test void queryOwnershipRechecksRequestedSupportBeforeReadingItsContributingProducer() {
    var f=new Fixture();f.producer.setId(71);var input=checkedQuery(f,71);
    f.source.setObservation(input);f.source.setId(Observation.QUERY_ID);f.source.setActuatorType(Actuator.Type.REFERENCE);
    when(f.runtime.completedCohortReference(any(),eq(f.resolution))).thenReturn(checkedQuery(f,71));
    when(f.resolution.getObservation(71)).thenReturn(f.producer);
    var checks=new ArrayList<Runnable>();
    assertSame(f.producer,TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,checks));
    var order=inOrder(f.runtime,f.resolution);
    order.verify(f.runtime).completedCohortReference(argThat(q -> q.getId()==Observation.QUERY_ID && q.getGeometry()==f.support),eq(f.resolution));
    order.verify(f.resolution).getObservation(71);
    checks.forEach(Runnable::run);verify(f.runtime,never()).register(any(),any());
    when(f.runtime.completedCohortReference(any(),eq(f.resolution))).thenReturn(checkedQuery(f,72));
    assertThrows(IllegalStateException.class,()->checks.forEach(Runnable::run));
  }
  @Test void partialUnknownAndForgedNativeQuerySourcesCannotOwnAnOperation() {
    var f=new Fixture();f.producer.setId(71);var input=checkedQuery(f,71);
    f.source.setObservation(input);f.source.setId(Observation.QUERY_ID);f.source.setActuatorType(Actuator.Type.REFERENCE);
    var partial=checkedQuery(f,71);partial.setResolvedCoverage(.5);
    when(f.runtime.completedCohortReference(any(),eq(f.resolution))).thenReturn(partial);
    assertThrows(IllegalStateException.class,()->TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,new ArrayList<>()));
    verify(f.resolution,never()).getObservation(anyLong());
    for (double coverage : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      partial.setResolvedCoverage(coverage);
      assertThrows(IllegalStateException.class,()->TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,new ArrayList<>()));
    }
    when(f.runtime.completedCohortReference(any(),eq(f.resolution))).thenReturn(checkedQuery(f,999));
    f.source.setObservation(checkedQuery(f,999));
    assertThrows(IllegalStateException.class,()->TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,new ArrayList<>()));
    when(f.resolution.getObservation(71)).thenReturn(f.producer);
    when(f.runtime.completedCohortReference(any(),eq(f.resolution))).thenReturn(checkedQuery(f,71));
    f.source.setObservation(checkedQuery(f,999));
    assertThrows(IllegalStateException.class,()->TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,new ArrayList<>()));
  }
  @Test void ownershipCannotBorrowTheWithinContextOrAnUnboundInput() {
    var f=new Fixture();f.root.getTargetBindings().clear();
    assertThrows(IllegalArgumentException.class,()->TestingSemanticOperation.ownership(f.runtime,f.root,f.support,f.resolution,new ArrayList<>()));
    verify(f.runtime,never()).register(any(),any());
  }

  @Test void observerMeaningAndGeometryArePinnedIndependentlyOfTheObservedAsWrapper() {
    var f=new Fixture();var guard=TestingSemanticOperation.observerWitness(f.scope);
    guard.run();
    var original=(ConceptImpl)f.observer.getObservable().getSemantics();
    original.setUrn("test:AnotherAgent");
    assertThrows(IllegalStateException.class,guard::run);
    original.setUrn("test:AGENT");guard.run();
    original.setType(EnumSet.of(SemanticType.SUBJECT));
    assertThrows(IllegalStateException.class,guard::run);
    original.setType(EnumSet.of(SemanticType.AGENT));guard.run();
    f.observer.setName("changed after capture");
    assertThrows(IllegalStateException.class,guard::run);
    f.observer.setName(null);guard.run();
    f.observer.setGeometry(Geometry.create("S2(2,1)"));
    assertThrows(IllegalStateException.class,guard::run);
  }

  @Test void sameIdObserverMeaningChangeDuringPlanningRollsBackEvenWithoutMemberEffects() {
    var f=new Fixture();var pending=new CompletableFuture<Dataflow>();
    when(f.resolver.resolve(any(),any())).thenReturn(pending);
    var result=TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller);
    ((ConceptImpl)f.observer.getObservable().getSemantics()).setUrn("test:OtherAgent");
    pending.complete(f.plan);
    assertThrows(CompletionException.class,result::join);
    verify(f.attempt,never()).commit();verify(f.attempt).fail(any(Throwable.class));
    verify(f.runtime).captureSelectionBaseline(f.observer.getObservable(),f.attempt);
  }
  @Test void detachedForeignOrStaleObserverCannotBeginPlanning() {
    var f=new Fixture();
    when(f.runtime.getObserverGeometry(52,f.scope)).thenThrow(new IllegalArgumentException("Agent is not in this twin"));
    assertThrows(CompletionException.class,()->TestingSemanticOperation.apply(f.runtime,f.directive,f.support,f.scope,f.caller).join());
    verify(f.resolver,never()).resolve(any(),any());verify(f.attempt,never()).commit();
    var stale=new Fixture();stale.observer.setName("unpersisted payload");
    assertThrows(CompletionException.class,()->TestingSemanticOperation.apply(stale.runtime,stale.directive,stale.support,stale.scope,stale.caller).join());
    verify(stale.resolver,never()).resolve(any(),any());verify(stale.attempt,never()).commit();
  }

}
