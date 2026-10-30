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
  static Observation receivedRegions;
  public static boolean consume(@org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction.Input(name="regions") Observation regions) {
    receivedRegions=regions; return true;
  }
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
  @Test void actualRuntimeCompletesExplicitSatisfactionWithoutClassifierModel() throws Exception {
    classificationLifecycle(false,true,false,true);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift) throws Exception {
    classificationLifecycle(failCharacterization,reuse,generationDrift,false);
  }
  @Test void qualifiedObservationPortBindsExactProducerAfterClassification() throws Exception {
    classificationLifecycle(false,true,false,false,true);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift,boolean checkOnly) throws Exception {
    classificationLifecycle(failCharacterization,reuse,generationDrift,checkOnly,false);
  }
  @Test void compiledPartialQualifiedPlanReachesConsumerWithoutDuplicatingProducer() throws Exception {
    classificationLifecycle(false,true,false,false,true,true);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift,boolean checkOnly,boolean expose) throws Exception {
    classificationLifecycle(failCharacterization,reuse,generationDrift,checkOnly,expose,false);
  }
  @Test void partialReferenceRejectsLateUnclassifiedMemberBeforeConsumer() throws Exception {
    classificationLifecycle(false,true,false,false,true,true,1);
  }
  @Test void partialReferenceRejectsMismatchedTransportedMemberDomain() throws Exception {
    classificationLifecycle(false,true,false,false,true,true,2);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift,boolean checkOnly,boolean expose,boolean mixed) throws Exception {
    classificationLifecycle(failCharacterization,reuse,generationDrift,checkOnly,expose,mixed,0);
  }
  private void classificationLifecycle(boolean failCharacterization,boolean reuse,boolean generationDrift,boolean checkOnly,boolean expose,boolean mixed,int invalidView) throws Exception {
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
    when(runtime.shardExecution()).thenReturn(new ShardExecution(()->1));
    when(scope.getDigitalTwin()).thenReturn(twin);
    var knowledgeGraph =
        mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
    when(twin.getKnowledgeGraph()).thenReturn(knowledgeGraph);
    when(knowledgeGraph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
    var activityQuery=mock(KnowledgeGraph.Query.class,RETURNS_SELF);
    when(activityQuery.run(any())).thenReturn(List.of());
    when(knowledgeGraph.query(eq(Activity.class),any())).thenReturn(activityQuery);
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
    if (checkOnly) {
      f.actuator.getComputation().clear(); f.actuator.getData().put(Actuator.CLASSIFICATION_CHECK_ONLY,true);
      when(runtime.classificationMembersForCheck(any(),any(),any())).thenReturn(List.of(f.member));
    }
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
    var completedMembers=new ArrayList<Observation>(List.of(f.member));
    if(mixed) {
      var existing=new ObservationImpl();existing.setId(43);existing.setObservable(f.member.getObservable());completedMembers.add(existing);
      if(invalidView==1) {
        existing.setObservable(mock(Observable.class));
        when(existing.getObservable().getUrn()).thenReturn("test:Unclassified test:Region");
        when(f.reasoner.directTraits(any(Observable.class))).thenAnswer(call->
            "test:Unclassified test:Region".equals(((Observable)call.getArgument(0)).getUrn())?List.of():List.of(f.result));
      }
      // Actual strategy matching, resolver, compiler and JSON transport; runtime services below
      // retain controlled registry/discovery/scheduler boundaries, as in the lifecycle tests.
      var resolved=org.integratedmodelling.klab.services.resolver.ClassificationPlanFixture.mixed();
      root=(ActuatorImpl)resolved.flow().getComputation().getFirst(); parent=(ObservationImpl)root.getObservation();
      var effect=(ActuatorImpl)root.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.UPDATE).findFirst().orElseThrow();
      var directive=spy(effect.getOperationObservable());doReturn(operationBuilder).when(directive).builder(any(org.integratedmodelling.klab.api.scope.Scope.class));
      effect.setOperationObservable(directive);
      producer=(ObservationImpl)effect.getChildren().getFirst().getObservation();
      when(f.reasoner.directInherent(directive)).thenReturn(invalidView==2?null:producer.getObservable().asConcept());
      var cohort=mock(Cohort.class);when(runtime.getCohortFor(any(),eq(scope),eq(true))).thenReturn(cohort);
      var port=root.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.REFERENCE).findFirst().orElseThrow();
      when(runtime.completedCohortReference(eq(port.getObservation()),eq(scope))).thenReturn(port.getObservation());
      when(runtime.classificationMembersForCheck(eq(port.getObservation()),any(),eq(scope))).thenReturn(completedMembers);
      var scheduler=mock(Scheduler.class);when(twin.getScheduler()).thenReturn(scheduler);
      when(scheduler.executeDependency(any(),any(),any(),any())).thenReturn(true);
    }
    if(expose) {
      receivedRegions=null;
      if(!mixed) {var port=new ActuatorImpl();port.setObservation(producer);port.setId(producer.getId());port.setName("regions");port.setActuatorType(Actuator.Type.REFERENCE);root.getChildren().add(port);}
      var info=new org.integratedmodelling.common.lang.ServiceInfoImpl();info.setName("test.consume");
      var input=new org.integratedmodelling.common.lang.ServiceInfoImpl.ArgumentImpl();input.setName("regions");info.getInputs().add(input);
      var consumer=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();consumer.staticMethod=true;consumer.serviceInfo=info;
      var impl=new ComponentRegistry.ServiceImplementation();impl.method=getClass().getMethod("consume",Observation.class);
      when(registry.implementation(consumer)).thenReturn(impl);
      when(registry.getFunctionDescriptor(argThat(call->call!=null && "test.consume".equals(call.getUrn())),any(org.integratedmodelling.klab.api.scope.Scope.class))).thenReturn(List.of(consumer));
      root.getComputation().add(new org.integratedmodelling.common.lang.ServiceCallImpl("test.consume"));
    }
    when(runtime.classificationMembers(eq(producer), any(), any())).thenReturn(invalidView==1?List.of(f.member):completedMembers);
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
              when(executing.getDigitalTwin()).thenReturn(twin);
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
    if(invalidView!=0) {
      assertFalse(executor.run(Geometry.UNIVERSAL,f.event,scope));assertNull(receivedRegions);
      assertEquals(0,f.classifier.calls.get());return;
    }
    assertTrue(executor.run(Geometry.UNIVERSAL, f.event, scope), ()->String.valueOf(executionFailure.get()));
    var classification=rootTransaction.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
        .filter(a->a.getType()==Activity.Type.CLASSIFICATION).findFirst().orElseThrow();
    var evidence=(Map<?,?>)classification.getMetadata().get(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS);
    assertNotNull(evidence);assertEquals(rootTransaction.getId(),evidence.get("rootAttempt"));
    var memberResults=(List<?>)evidence.get("memberResults");assertEquals(mixed?2:1,memberResults.size());
    var memberResult=(Map<?,?>)memberResults.getFirst();
    assertEquals(reuse?"EXISTING":"CLASSIFIER",memberResult.get("origin"));assertEquals(List.of("test:Concrete"),memberResult.get("concretePredicates"));
    if(reuse){
      if(expose) {
        if(mixed) {assertEquals(Observation.QUERY_ID,receivedRegions.getId());
          verify(twin.getScheduler(),times(1)).executeDependency(any(),any(),any(),any());
          verify(runtime).classificationMembersForCheck(eq(receivedRegions),any(),eq(scope));
        } else {assertSame(producer,receivedRegions);assertEquals(producer.getId(),receivedRegions.getId());}
      }
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
