package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ClassificationExecutionTest {
  @Test void scopeCacheReloadsAfterAnotherContextCommitsSemantics() throws Exception {
    var scope = mock(ServiceContextScope.class);
    var twin = mock(DigitalTwin.class);
    var graph = mock(KnowledgeGraph.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var twinField = ServiceContextScope.class.getDeclaredField("digitalTwin");
    twinField.setAccessible(true); twinField.set(scope, twin);
    var cacheField = ServiceContextScope.class.getDeclaredField("observationCache");
    cacheField.setAccessible(true); cacheField.set(scope, com.google.common.cache.CacheBuilder.newBuilder().build());
    when(scope.getObservation(42L)).thenCallRealMethod();
    var before = new ObservationImpl(); before.setId(42);
    var after = new ObservationImpl(); after.setId(42);
    when(graph.getAsset(42L, scope, Observation.class)).thenReturn(before, after);
    assertSame(before, scope.getObservation(42L));
    assertSame(before, scope.getObservation(42L));
    when(graph.getSemanticRevision()).thenReturn(1L);
    assertSame(after, scope.getObservation(42L));
    verify(graph, times(2)).getAsset(42L, scope, Observation.class);
  }

  @Test void emptyParentComputationStillRunsClassifierDependencyWithoutAResultObservation() throws Exception {
    classificationLifecycle(false);
  }

  @Test void failedCharacterizationDiscardsPendingAttributions() throws Exception {
    classificationLifecycle(true);
  }

  private void classificationLifecycle(boolean failCharacterization) throws Exception {
    var f = new MemberClassifierExecutorTest.Fixture();
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class);
    var twin = mock(DigitalTwinImpl.class);
    var registry = mock(ComponentRegistry.class);
    when(runtime.getComponentRegistry()).thenReturn(registry);
    when(scope.getDigitalTwin()).thenReturn(twin);
    var knowledgeGraph = mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
    when(twin.getKnowledgeGraph()).thenReturn(knowledgeGraph);
    when(knowledgeGraph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
    when(scope.getService(Reasoner.class)).thenReturn(f.reasoner);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.within(any(Observation.class))).thenReturn(scope);
    var operationBuilder = f.observable.builder(f.scope);
    when(f.observable.builder(any(ContextScope.class))).thenReturn(operationBuilder);
    var after = mock(Observable.class);
    when(after.getUrn()).thenReturn("test:Concrete test:Region");
    when(after.getSemantics()).thenReturn(mock(Concept.class));
    when(f.result.is(SemanticType.TRAIT)).thenReturn(true);
    var memberBuilder = mock(Observable.Builder.class);
    when(f.member.getObservable().builder(any(ContextScope.class))).thenReturn(memberBuilder);
    when(f.member.getObservable().getUrn()).thenReturn("test:Region");
    when(memberBuilder.buildObservable()).thenReturn(after);
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = MemberClassifierExecutorTest.Classifier.class.getMethod("classify",
        Observable.class, Observation.class, ContextScope.class, ServiceCall.class);
    implementation.mainClassInstance = f.classifier;
    when(registry.getFunctionDescriptor(any(ServiceCall.class))).thenReturn(List.of(descriptor));
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var parent = new ObservationImpl(); parent.setId(-10); parent.setGeometry(Geometry.UNIVERSAL);
    var parentObservable = mock(Observable.class);
    when(parentObservable.getContextualization()).thenReturn(Contextualization.ACKNOWLEDGEMENT);
    when(parentObservable.getSemantics()).thenReturn(mock(Concept.class));
    parent.setObservable(parentObservable);
    var root = new ActuatorImpl(); root.setId(-10); root.setObservation(parent); root.setActuatorType(Actuator.Type.OBSERVE);
    f.actuator.setId(0);
    var producer = new ObservationImpl(); producer.setId(0); producer.setGeometry(Geometry.UNIVERSAL);
    producer.setObservable(parentObservable);
    var reference = new ActuatorImpl(); reference.setObservation(producer); reference.setActuatorType(Actuator.Type.REFERENCE);
    reference.setName("cohort"); reference.setId(0);
    f.actuator.getChildren().add(reference); root.getChildren().add(f.actuator);
    when(runtime.classificationMembers(eq(producer), any(), any())).thenReturn(List.of(f.member));
    var rootTransaction = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope, RuntimeAsset.PROVENANCE_ASSET);
    when(scope.getCurrentTransaction()).thenReturn(rootTransaction);
    when(scope.getActivity()).thenReturn(rootTransaction.getActivity());
    when(scope.executing(any(Activity.class), any(Object[].class))).thenAnswer(call -> {
      Activity activity = call.getArgument(0);
      var executing = mock(ServiceContextScope.class);
      var tx = rootTransaction.getChild(activity, executing);
      when(executing.getCurrentTransaction()).thenReturn(tx);
      when(executing.getService(Reasoner.class)).thenReturn(f.reasoner);
      when(executing.getService(RuntimeService.class)).thenReturn(runtime);
      when(executing.within(any(Observation.class))).thenReturn(executing);
      when(executing.commit()).thenAnswer(ignored -> tx.commit());
      doAnswer(failure -> { tx.fail(failure.getArgument(0)); return null; }).when(executing).fail(any(Throwable.class));
      return executing;
    });
    var plan = new CompiledDataflow(runtime, parent, scope);
    assertTrue(plan.compile(root));
    var store = mock(DigitalTwinImpl.TransactionImpl.class);
    when(store.getActivity()).thenReturn(rootTransaction.getActivity());
    assertTrue(plan.store(store));
    verify(store).resolveWith(eq(parent), any(DigitalTwin.Executor.class));
    verify(store, never()).resolveWith(isNull(), any());
    // Execute the compiled parent directly: its empty computation must not skip its UPDATE child.
    var executor = plan.new ExecutorImpl(root);
    if (failCharacterization) {
      doThrow(new IllegalStateException("Characterization failed"))
          .when(runtime).characterizePending(anyList(), any(ServiceContextScope.class));
      assertFalse(executor.run(Geometry.UNIVERSAL, f.event, scope));
      assertNotSame(after, f.member.getObservable());
      assertFalse(rootTransaction.assets().stream().anyMatch(asset -> asset instanceof Observation o
          && o.getObservable() == after));
      return;
    }
    assertTrue(executor.run(Geometry.UNIVERSAL, f.event, scope));
    verify(runtime).characterizePending(argThat(pending -> pending.size() == 1
        && pending.getFirst().member().getId() == 42), any(ServiceContextScope.class));
    assertEquals(1, f.classifier.calls.get());
    assertNotSame(after, f.member.getObservable());
    assertTrue(rootTransaction.assets().stream().anyMatch(asset -> asset instanceof Observation o
        && o.getId() == 42 && o.getObservable() == after));
    assertFalse(rootTransaction.assets().stream().anyMatch(asset -> asset instanceof Observation o
        && o.getObservable() == f.observable));
    assertTrue(rootTransaction.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .anyMatch(a -> a.getType() == Activity.Type.CLASSIFICATION));
  }
}
