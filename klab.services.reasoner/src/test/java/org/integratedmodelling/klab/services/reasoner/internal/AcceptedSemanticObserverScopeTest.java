package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.*;

class AcceptedSemanticObserverScopeTest {
  @BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  static ObservationImpl observer(long id,String meaning) {
    var concept=new ConceptImpl();concept.setUrn(meaning);concept.setType(EnumSet.of(SemanticType.AGENT));
    var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn(meaning);
    var observation=new ObservationImpl();observation.setId(id);observation.setUrn("test:observer"+id);
    observation.setObservable(observable);observation.setName("original");return observation;
  }
  static Worldview world() {
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setProjectName("test");
    ontology.setSourceCode("ontology w in domain root;");
    var world=new WorldviewImpl();world.setUrn("w");world.setWorldviewId("w");world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));return world;
  }
  static SemanticEnvironmentFactory factory(java.util.concurrent.atomic.AtomicReference<SemanticEnvironment> state) throws Exception {
    var factory=mock(SemanticEnvironmentFactory.class);when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any())).thenAnswer(i->{Worldview w=i.getArgument(0);return new CandidatePublicationCoordinator.Input<>(
        SemanticEnvironmentFactory.sources(w),SemanticEnvironmentFactory.sourceOrder(w),"test",
        SemanticEnvironmentFactory.CONFIGURATION_REVISION,w,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());});
    when(factory.build(any(),any())).thenAnswer(i->new CandidatePublicationCoordinator.Result<>(state.get(),
        CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
    return factory;
  }
  @Test void observerRuntimeCallbackCompletesBeforeTheSemanticMonitorIsAcquired() throws Exception {
    var factory=mock(SemanticEnvironmentFactory.class);var state=mock(SemanticEnvironment.class);
    var actor=mock(ReasonerService.class);when(state.actor()).thenReturn(actor);
    when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any())).thenAnswer(i->{Worldview w=i.getArgument(0);return new CandidatePublicationCoordinator.Input<>(
        SemanticEnvironmentFactory.sources(w),SemanticEnvironmentFactory.sourceOrder(w),"test",
        SemanticEnvironmentFactory.CONFIGURATION_REVISION,w,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());});
    when(factory.build(any(),any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,
        CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
    var original=mock(ContextScope.class);var selected=observer(7,"test:Agent");
    selected.getMetadata().put("test:marker", "retained");
    var derived=mock(ContextScope.class);when(original.within(null)).thenReturn(derived);
    when(original.withObserver(any(Observation.class))).thenReturn(derived);
    when(derived.getObserver()).thenThrow(new AssertionError("Derived scope must not lazily reload its observer"));
    try(var executor=Executors.newSingleThreadExecutor();var runtime=new AcceptedSemanticRuntime(factory)) {
      runtime.submit(world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      when(original.getObserver()).thenAnswer(i->{
        assertFalse(Thread.holdsLock(state));
        // Model Runtime's callback into the same semantic environment on another HTTP thread.
        var callback=executor.submit(()->runtime.invoke("resolveObservable",new Class<?>[]{String.class},
            new Object[]{"test:Agent"}));
        assertNotNull(callback.get(2,TimeUnit.SECONDS));return selected;
      });
      when(actor.resolveObservable("test:Agent")).thenAnswer(i->{
        assertTrue(Thread.holdsLock(state));return selected.getObservable();
      });
      when(actor.resolveConcept("test:Agent")).thenReturn(selected.getObservable().getSemantics());
      when(actor.computeObservationStrategies(isNull(),any())).thenAnswer(i->{
        assertTrue(Thread.holdsLock(state));ContextScope bound=i.getArgument(1);
        selected.setName("changed after hydration");
        assertEquals("original",bound.getObserver().getName());
        assertSame(selected.getObservable().getSemantics(),bound.getObserver().getObservable().getSemantics());
        var transported=Observation.forTransport(bound.getObserver());
        assertEquals("retained",transported.getMetadata().get("test:marker"));
        assertEquals(7,bound.withObserver(bound.getObserver()).getObserver().getId());
        assertEquals("retained",bound.withObserver(bound.getObserver()).getObserver().getMetadata().get("test:marker"));
        assertEquals("original",bound.within(null).getObserver().getName());return List.of();
      });
      assertEquals(List.of(),runtime.invoke("computeObservationStrategies",
          new Class<?>[]{Observation.class,ContextScope.class},new Object[]{null,original}));
      verify(original,times(1)).getObserver();verify(derived,never()).getObserver();
    }
  }
  @Test void rootsAndDerivedBranchesKeepTheirOwnObserverPolicyWithoutReloading() {
    var request=mock(ContextScope.class);var root=mock(ContextScope.class);var child=mock(ContextScope.class);
    var replacement=mock(ContextScope.class);var noObserver=mock(ContextScope.class);
    var first=observer(7,"test:Agent");var other=observer(8,"test:OtherAgent");
    when(request.getObserver()).thenReturn(first);when(request.getRootContextScope()).thenReturn(root);
    when(request.within(null)).thenReturn(child);when(request.withObserver(other)).thenReturn(replacement);
    when(request.withObserver(null)).thenReturn(noObserver);
    var prepared=(ContextScope)AcceptedSemanticRuntime.prepareScopes(request);
    ((ObservationImpl)prepared.getObserver()).setName("caller copy");
    assertEquals("original",prepared.getObserver().getName());
    first.setName("changed");
    assertEquals("original",prepared.within(null).getObserver().getName());
    assertNull(prepared.getRootContextScope().getObserver());
    assertNull(prepared.withObserver(null).getObserver());
    assertEquals(8,prepared.withObserver(other).getObserver().getId());
    verify(root,times(1)).getObserver();verify(child,never()).getObserver();
    verify(replacement,never()).getObserver();verify(noObserver,never()).getObserver();
    // Preparing a new request refreshes the selection; snapshots are not a cross-call cache.
    assertEquals("changed",((ContextScope)AcceptedSemanticRuntime.prepareScopes(request)).getObserver().getName());
  }
  @Test void redundantSelectorsUseTheSnapshotAndConflictingSelectorsFailBeforeDelegation() {
    var request=mock(ContextScope.class);var derived=mock(ContextScope.class);var selected=observer(7,"test:Agent");
    when(request.getObserver()).thenReturn(selected);when(request.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(derived);
    var prepared=(ContextScope)AcceptedSemanticRuntime.prepareScopes(request);
    assertEquals(7,prepared.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.Observer,7L))
        .getObserver().getId());
    verify(request).withResolutionConstraints();
    clearInvocations(request);
    assertEquals(7,prepared.withResolutionConstraints(
        ResolutionConstraint.of(ResolutionConstraint.Type.Observer,99L),
        ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,selected.getUrn())).getObserver().getId());
    clearInvocations(request);
    assertThrows(IllegalArgumentException.class,()->prepared.withResolutionConstraints(
        ResolutionConstraint.of(ResolutionConstraint.Type.Observer,8L)));
    assertThrows(IllegalArgumentException.class,()->prepared.withResolutionConstraints(
        ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"test:another")));
    verify(request,never()).withResolutionConstraints(any(ResolutionConstraint[].class));
  }
  @Test void aPreparedObserverIsReboundToEachAcceptedActorRatherThanRetainingAnOldConcept() {
    var original=mock(ContextScope.class);var selected=observer(7,"test:Agent");
    when(original.getObserver()).thenReturn(selected);
    var prepared=(ContextScope)AcceptedSemanticRuntime.prepareScopes(original);
    var first=mock(ReasonerService.class);var next=mock(ReasonerService.class);
    var oldMeaning=observer(7,"test:Agent").getObservable().getSemantics();
    var newMeaning=observer(7,"test:Agent").getObservable().getSemantics();
    when(first.resolveConcept("test:Agent")).thenReturn(oldMeaning);
    when(next.resolveConcept("test:Agent")).thenReturn(newMeaning);
    try(var runtime=new AcceptedSemanticRuntime(mock(SemanticEnvironmentFactory.class))) {
      var oldScope=(ContextScope)runtime.scope(prepared,first);
      var newScope=(ContextScope)runtime.scope(prepared,next);
      assertSame(oldMeaning,oldScope.getObserver().getObservable().getSemantics());
      assertSame(newMeaning,newScope.getObserver().getObservable().getSemantics());
      when(next.resolveConcept("test:Agent")).thenReturn(null);
      assertThrows(IllegalStateException.class,()->newScope.getObserver().getObservable().getSemantics());
    }
    verify(original,times(1)).getObserver();
  }
  @Test void generationPublicationDuringObserverHydrationCannotEnterEitherActor() throws Exception {
    var oldState=mock(SemanticEnvironment.class);var nextState=mock(SemanticEnvironment.class);
    var oldActor=mock(ReasonerService.class);var nextActor=mock(ReasonerService.class);
    when(oldState.actor()).thenReturn(oldActor);when(nextState.actor()).thenReturn(nextActor);
    var current=new java.util.concurrent.atomic.AtomicReference<>(oldState);
    try(var runtime=new AcceptedSemanticRuntime(factory(current))) {
      runtime.submit(world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long initial=runtime.status().acceptedGeneration();var original=mock(ContextScope.class);
      when(original.getObserver()).thenAnswer(i->{
        assertFalse(Thread.holdsLock(oldState));current.set(nextState);
        var changed=world();((KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // changed");
        runtime.submit(changed);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertTrue(runtime.status().acceptedGeneration()>initial);return observer(7,"test:Agent");
      });
      var failure=assertThrows(IllegalStateException.class,()->runtime.invoke("computeObservationStrategies",
          new Class<?>[]{Observation.class,ContextScope.class},new Object[]{null,original}));
      assertTrue(failure.getMessage().contains("generation changed during observer preparation"));
      verify(oldActor,never()).computeObservationStrategies(any(),any());
      verify(nextActor,never()).computeObservationStrategies(any(),any());
    }
  }
  @Test void observerEligibilityFailureAndReleaseDuringHydrationFailClosed() throws Exception {
    var state=mock(SemanticEnvironment.class);var actor=mock(ReasonerService.class);when(state.actor()).thenReturn(actor);
    var runtime=new AcceptedSemanticRuntime(factory(new java.util.concurrent.atomic.AtomicReference<>(state)));
    try {
      runtime.submit(world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var original=mock(ContextScope.class);
      when(original.getObserver()).thenThrow(new IllegalStateException("Selected observer is no longer an accessible Agent"));
      assertThrows(IllegalStateException.class,()->runtime.invoke("computeObservationStrategies",
          new Class<?>[]{Observation.class,ContextScope.class},new Object[]{null,original}));
      doAnswer(i->{runtime.close();return observer(7,"test:Agent");}).when(original).getObserver();
      assertThrows(IllegalStateException.class,()->runtime.invoke("computeObservationStrategies",
          new Class<?>[]{Observation.class,ContextScope.class},new Object[]{null,original}));
      verify(actor,never()).computeObservationStrategies(any(),any());
    } finally {runtime.close();}
  }
  @Test void publicationOrCloseWhileWaitingForTheMonitorCannotEnterTheActor() throws Exception {
    for (boolean close : List.of(false,true)) {
      var state=mock(SemanticEnvironment.class);var next=mock(SemanticEnvironment.class);
      var actor=mock(ReasonerService.class);when(state.actor()).thenReturn(actor);
      var current=new java.util.concurrent.atomic.AtomicReference<>(state);
      var thread=new java.util.concurrent.atomic.AtomicReference<Thread>();
      var hydrated=new CountDownLatch(1);var original=mock(ContextScope.class);
      when(original.getObserver()).thenAnswer(i->{hydrated.countDown();return observer(7,"test:Agent");});
      try(var runtime=new AcceptedSemanticRuntime(factory(current));var executor=Executors.newSingleThreadExecutor()) {
        runtime.submit(world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        Future<Object> call;
        synchronized(state) {
          call=executor.submit(()->{thread.set(Thread.currentThread());return runtime.invoke("computeObservationStrategies",
              new Class<?>[]{Observation.class,ContextScope.class},new Object[]{null,original});});
          assertTrue(hydrated.await(2,TimeUnit.SECONDS));
          assertTimeoutPreemptively(Duration.ofSeconds(2),()->{
            while(thread.get().getState()!=Thread.State.BLOCKED) Thread.sleep(1);
          });
          if(close) runtime.close();
          else {
            current.set(next);var changed=world();
            ((KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // monitor race");
            runtime.submit(changed);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
          }
        }
        var failure=assertThrows(ExecutionException.class,()->call.get(2,TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class,failure.getCause());
        verify(actor,never()).computeObservationStrategies(any(),any());
      }
    }
  }
}
