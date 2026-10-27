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
    var observable=new ObservableImpl();observable.setSemantics(meaning);observable.setUrn(meaning.getUrn());
    var result=new ObservationImpl();result.setId(id);result.setObservable(observable);result.setGeometry(Geometry.create("S2(1,1)"));return result;
  }
  static class Fixture {
    final RuntimeService runtime=mock(RuntimeService.class);
    final ServiceContextScope scope=mock(ServiceContextScope.class),attempt=mock(ServiceContextScope.class),resolution=mock(ServiceContextScope.class);
    final TestCaseBase.TestCaseScope caller=mock(TestCaseBase.TestCaseScope.class);
    final Resolver resolver=mock(Resolver.class);final Reasoner reasoner=mock(Reasoner.class);
    final Reasoner.Capabilities capabilities=mock(Reasoner.Capabilities.class);
    final DigitalTwinImpl.TransactionImpl transaction=mock(DigitalTwinImpl.TransactionImpl.class),resolutionTransaction=mock(DigitalTwinImpl.TransactionImpl.class);
    final ObservationImpl context=observation(41,SemanticType.SUBJECT),observer=observation(52,SemanticType.AGENT);
    final ObservableImpl directive=mock(ObservableImpl.class);final Geometry support=Geometry.create("S2(1,1)");
    final DataflowImpl plan=new DataflowImpl(); final ActuatorImpl root=new ActuatorImpl();
    final AtomicReference<Activity> activity=new AtomicReference<>();final AtomicLong revision=new AtomicLong(7);
    final AtomicReference<BooleanSupplier> cancelled=new AtomicReference<>(()->false);
    Fixture() {
      when(directive.getSemantics()).thenReturn(context.getObservable().getSemantics());when(directive.getUrn()).thenReturn("test:Family of each test:Subject");when(directive.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
      when(scope.getContextObservation()).thenReturn(context);when(scope.getObserver()).thenReturn(observer);when(scope.getId()).thenReturn("session.twin");
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
    }
    MockedConstruction<CompiledDataflow> compiled(boolean execute) {
      return mockConstruction(CompiledDataflow.class,(compiled,construction)->{
        assertNull(construction.arguments().get(1));
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
      verify(f.runtime,never()).register(any(),any());verify(f.runtime,never()).submit(any(),any());
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
}
