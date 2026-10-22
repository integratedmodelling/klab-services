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
  @org.junit.jupiter.api.BeforeAll
  static void configureScaleFactory() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }

  @Test
  void scopeCacheReloadsAfterAnotherContextCommitsSemantics() throws Exception {
    var scope = mock(ServiceContextScope.class);
    var twin = mock(DigitalTwin.class);
    var graph = mock(KnowledgeGraph.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var twinField = ServiceContextScope.class.getDeclaredField("digitalTwin");
    twinField.setAccessible(true);
    twinField.set(scope, twin);
    var cacheField = ServiceContextScope.class.getDeclaredField("observationCache");
    cacheField.setAccessible(true);
    cacheField.set(scope, com.google.common.cache.CacheBuilder.newBuilder().build());
    when(scope.getObservation(42L)).thenCallRealMethod();
    var before = new ObservationImpl();
    before.setId(42);
    var after = new ObservationImpl();
    after.setId(42);
    when(graph.getAsset(42L, scope, Observation.class)).thenReturn(before, after);
    assertSame(before, scope.getObservation(42L));
    assertSame(before, scope.getObservation(42L));
    when(graph.getSemanticRevision()).thenReturn(1L);
    assertSame(after, scope.getObservation(42L));
    verify(graph, times(2)).getAsset(42L, scope, Observation.class);
  }

  @Test
  void emptyParentComputationStillRunsClassifierDependencyWithoutAResultObservation()
      throws Exception {
    classificationLifecycle(false);
  }

  @Test
  void failedCharacterizationDiscardsPendingAttributions() throws Exception {
    classificationLifecycle(true);
  }

  private void classificationLifecycle(boolean failCharacterization) throws Exception {
    classificationLifecycle(failCharacterization,false);
  }
  @Test void actualRuntimePreservesReusedMemberResultsWithoutNewEffects() throws Exception {
    classificationLifecycle(false,true);
  }
  @Test void generationDriftDuringCharacterizationCannotPublishResultEvidence() throws Exception {
    classificationLifecycle(false,false,true);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse) throws Exception {
    classificationLifecycle(failCharacterization,reuse,false);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift) throws Exception {
    var f = new MemberClassifierExecutorTest.Fixture();
    var revision=new java.util.concurrent.atomic.AtomicLong(3);
    if(generationDrift){
      var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn("reasoner");
      when(caps.getKnowledgeRevision()).thenAnswer(i->revision.get());when(f.reasoner.capabilities(any())).thenReturn(caps);
    }
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class);
    var twin = mock(DigitalTwinImpl.class);
    var registry = mock(ComponentRegistry.class);
    when(runtime.getComponentRegistry()).thenReturn(registry);
    when(scope.getDigitalTwin()).thenReturn(twin);
    var knowledgeGraph =
        mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
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
    when(f.result.is(SemanticType.PREDICATE)).thenReturn(true);
    var memberBuilder = mock(Observable.Builder.class);
    when(f.member.getObservable().builder(any(ContextScope.class))).thenReturn(memberBuilder);
    when(f.member.getObservable().getUrn()).thenReturn("test:Region");
    when(memberBuilder.buildObservable()).thenReturn(after);
    var descriptor =
        new org.integratedmodelling.klab.api.services.runtime.extension.Extensions
            .FunctionDescriptor();
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method =
        MemberClassifierExecutorTest.Classifier.class.getMethod(
            "classify", Observable.class, Observation.class, ContextScope.class, ServiceCall.class);
    implementation.mainClassInstance = f.classifier;
    when(registry.getFunctionDescriptor(any(ServiceCall.class), any(org.integratedmodelling.klab.api.scope.Scope.class))).thenReturn(List.of(descriptor));
    when(registry.implementation(descriptor)).thenReturn(implementation);
    if (reuse) when(f.reasoner.directTraits(any(Observable.class))).thenReturn(List.of(f.result));
    var parent = new ObservationImpl();
    parent.setId(-10);
    parent.setGeometry(Geometry.UNIVERSAL);
    var parentObservable = mock(Observable.class);
    when(parentObservable.getContextualization()).thenReturn(Contextualization.ACKNOWLEDGEMENT);
    when(parentObservable.getSemantics()).thenReturn(mock(Concept.class));
    parent.setObservable(parentObservable);
    var root = new ActuatorImpl();
    root.setId(-10);
    root.setObservation(parent);
    root.setActuatorType(Actuator.Type.OBSERVE);
    f.actuator.setId(0);
    var producer = new ObservationImpl();
    producer.setId(0);
    producer.setGeometry(Geometry.UNIVERSAL);
    producer.setObservable(parentObservable);
    var reference = new ActuatorImpl();
    reference.setObservation(producer);
    reference.setActuatorType(Actuator.Type.REFERENCE);
    reference.setName("cohort");
    reference.setId(0);
    f.actuator.getChildren().add(reference);
    root.getChildren().add(f.actuator);
    when(runtime.classificationMembers(eq(producer), any(), any())).thenReturn(List.of(f.member));
    var executionFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var rootTransaction =
        twin
        .new TransactionImpl(
            Activity.of(Activity.Type.SUBMISSION), scope, RuntimeAsset.PROVENANCE_ASSET);
    when(scope.getCurrentTransaction()).thenReturn(rootTransaction);
    when(scope.getActivity()).thenReturn(rootTransaction.getActivity());
    when(scope.executing(any(Activity.class), any(Object[].class)))
        .thenAnswer(
            call -> {
              Activity activity = call.getArgument(0);
              var executing = mock(ServiceContextScope.class);
              var tx = rootTransaction.getChild(activity, executing);
              when(executing.getCurrentTransaction()).thenReturn(tx);
              when(executing.getService(Reasoner.class)).thenReturn(f.reasoner);
              when(executing.getService(RuntimeService.class)).thenReturn(runtime);
              when(executing.within(any(Observation.class))).thenReturn(executing);
              when(executing.commit()).thenAnswer(ignored -> tx.commit());
              doAnswer(
                      failure -> {
                        executionFailure.set(failure.getArgument(0));
                        tx.fail(failure.getArgument(0));
                        return null;
                      })
                  .when(executing)
                  .fail(any(Throwable.class));
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
    if(generationDrift) doAnswer(i->{revision.incrementAndGet();return null;}).when(runtime).characterizePending(anyList(),any(ServiceContextScope.class));
    if (failCharacterization || generationDrift) {
      if(failCharacterization) doThrow(new IllegalStateException("Characterization failed"))
          .when(runtime)
          .characterizePending(anyList(), any(ServiceContextScope.class));
      assertFalse(executor.run(Geometry.UNIVERSAL, f.event, scope));
      assertNotSame(after, f.member.getObservable());
      assertFalse(
          rootTransaction.assets().stream()
              .anyMatch(asset -> asset instanceof Observation o && o.getObservable() == after));
      assertTrue(rootTransaction.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
          .noneMatch(a->a.getMetadata().containsKey(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS)));
      return;
    }
    assertTrue(executor.run(Geometry.UNIVERSAL, f.event, scope), ()->String.valueOf(executionFailure.get()));
    var classification=rootTransaction.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .filter(a->a.getType()==Activity.Type.CLASSIFICATION).findFirst().orElseThrow();
    var evidence=(Map<?,?>)classification.getMetadata().get(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS);
    assertNotNull(evidence);assertEquals(rootTransaction.getId(),evidence.get("rootAttempt"));
    var memberResults=(List<?>)evidence.get("memberResults");assertEquals(1,memberResults.size());
    var memberResult=(Map<?,?>)memberResults.getFirst();
    assertEquals(reuse?"EXISTING":"CLASSIFIER",memberResult.get("origin"));assertEquals(List.of("test:Concrete"),memberResult.get("concretePredicates"));
    if(reuse){
      assertEquals(0,f.classifier.calls.get());verify(runtime).characterizePending(eq(List.of()),any());
      assertFalse(rootTransaction.assets().stream().anyMatch(asset->asset instanceof Observation o&&o.getObservable()==after));
      return;
    }
    verify(runtime)
        .characterizePending(
            argThat(pending -> pending.size() == 1 && pending.getFirst().member().getId() == 42),
            any(ServiceContextScope.class));
    assertEquals(1, f.classifier.calls.get());
    assertNotSame(after, f.member.getObservable());
    assertTrue(
        rootTransaction.assets().stream()
            .anyMatch(
                asset ->
                    asset instanceof Observation o
                        && o.getId() == 42
                        && o.getObservable() == after));
    assertFalse(
        rootTransaction.assets().stream()
            .anyMatch(
                asset -> asset instanceof Observation o && o.getObservable() == f.observable));
    assertTrue(
        rootTransaction.assets().stream()
            .filter(Activity.class::isInstance)
            .map(Activity.class::cast)
            .anyMatch(a -> a.getType() == Activity.Type.CLASSIFICATION));
  }
}
