package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.services.client.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.*;

class AcceptedSemanticCycleTest {
  @Test void deletedContextCleanupUsesRecordedOwnershipAndExactRetriesAreIdempotent() throws Exception {
    var actor = actor("fingerprint", "test:Subject");
    var current = new AtomicReference<>(state(actor, "fingerprint"));
    var context = scope("alice", "context", "runtime");
    var owner = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var ownerIdentity = context.getUser();
    when(owner.getUser()).thenReturn(ownerIdentity);
    var foreign = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var foreignIdentity = scope("bob", "context", "runtime").getUser();
    when(foreign.getUser()).thenReturn(foreignIdentity);
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var binding = runtime.beginCycle(request(null, "closed-context"), context);
      var release = request(binding, "closed-context");
      assertThrows(SecurityException.class, () -> runtime.releaseCycle(release, foreign));
      assertThrows(SecurityException.class, () -> runtime.releaseCycle(
          new SemanticCycleRequest("other", "context", "closed-context", binding), owner));
      assertThrows(SecurityException.class, () -> runtime.releaseCycle(
          new SemanticCycleRequest("runtime", "other", "closed-context", binding), owner));
      assertThrows(SecurityException.class, () -> runtime.invokeCycle(new SemanticCycleInvocation(
          release, "resolveConcept", List.of(String.class.getName()), List.of("\"test:Subject\"")), owner));
      assertEquals(1, runtime.activeCycleCount());
      runtime.releaseCycle(release, owner);
      runtime.releaseCycle(release, owner);
      assertEquals(0, runtime.activeCycleCount());
      assertThrows(SecurityException.class, () -> runtime.releaseCycle(release, foreign));
      assertThrows(SecurityException.class, () -> runtime.beginCycle(request(null, "new"), owner));
      assertThrows(SecurityException.class, () -> runtime.releaseCycle(request(binding, "forged"), owner));
      // A closed cycle cannot be resurrected, and its retry cannot retire a new cycle.
      assertThrows(IllegalStateException.class, () -> runtime.beginCycle(request(null, "closed-context"), context));
      var next = runtime.beginCycle(request(null, "next"), context);
      runtime.releaseCycle(release, owner);
      assertEquals(1, runtime.activeCycleCount());
      runtime.releaseCycle(request(next, "next"), owner);
      assertEquals(0, runtime.activeCycleCount());
      for (int i = 0; i < 1030; i++) {
        String id = "history-" + i;
        runtime.releaseCycle(request(runtime.beginCycle(request(null, id), context), id), owner);
      }
      var history = AcceptedSemanticRuntime.class.getDeclaredField("releasedCycles");
      history.setAccessible(true);
      assertTrue(((Map<?, ?>) history.get(runtime)).size() <= 1024);
      assertEquals(0, runtime.activeCycleCount());
      verify(owner, never()).getService(any());
    }
  }
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  static void field(Object target,String name,Object value) throws Exception {
    var f=ReasonerService.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  static ReasonerService actor(String fingerprint,String answer) throws Exception {
    var actor=mock(ReasonerService.class,CALLS_REAL_METHODS);
    var validation=new SemanticValidationState();validation.settle(422,true);
    field(actor,"semanticOnly",true);field(actor,"knowledgeRevision",new AtomicLong(422));
    field(actor,"semanticValidationState",validation);field(actor,"semanticSessionId",UUID.randomUUID().toString());
    field(actor,"semanticContentFingerprint",fingerprint);
    var config=new ReasonerConfiguration();config.setServiceId("private");field(actor,"configuration",config);
    field(actor,"publishedSemanticCapabilities",SemanticCapabilitiesSnapshot.capture(0,"w","w",true,List.of(),null));
    doReturn(null).when(actor).getComponentRegistry();doReturn(fingerprint).when(actor).semanticContentFingerprint();
    var concept=new ConceptImpl();concept.setUrn(answer);concept.setType(EnumSet.of(SemanticType.SUBJECT));
    doReturn(concept).when(actor).resolveConcept(anyString());
    return actor;
  }
  static ContextScope scope(String username,String context,String runtimeId) {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn(context);
    var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn(username);when(scope.getUser()).thenReturn(user);
    var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn(runtimeId);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);return scope;
  }
  static SemanticEnvironment state(ReasonerService actor,String fingerprint) {
    var state=mock(SemanticEnvironment.class);when(state.actor()).thenReturn(actor);
    when(state.semanticContentFingerprint()).thenReturn(fingerprint);return state;
  }
  static SemanticCycleRequest request(SemanticCycleBinding binding,String cycle) {
    return new SemanticCycleRequest("runtime","context",cycle,binding);
  }
  static Reasoner facade(AcceptedSemanticRuntime runtime,SemanticCycleBinding binding,String cycle,ContextScope scope) {
    var base=mock(Reasoner.class);when(base.serviceScope()).thenReturn(scope);
    var session=mock(org.integratedmodelling.klab.api.scope.SessionScope.class);
    when(scope.getActiveSessions()).thenReturn(List.of(session));when(session.getActiveContexts()).thenReturn(List.of(scope));
    return SemanticCycleFacade.create(base,request(binding,cycle),scope,runtime::invokeCycle);
  }
  @Test void annotatedObservationSurvivesBindingStrategyComputationAndResponseTransport() throws Exception {
    var actor=actor("fingerprint","test:Subject");
    var current=new AtomicReference<>(state(actor,"fingerprint"));
    var scope=scope("alice","context","runtime");
    var concept=actor.resolveConcept("test:Subject");
    var observable=new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setUrn(concept.getUrn());observable.setSemantics(concept);
    var observation=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    observation.setObservable(observable);
    observation.mergeAnnotations(List.of(org.integratedmodelling.klab.api.lang.Annotation.of(
        "label","value","Cotechino","meaning",concept)),
        org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.DEFINITION_ANNOTATIONS);
    var strategy=new org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl();
    strategy.setUrn("test:strategy");
    var operation=new org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl.OperationImpl();
    operation.setType(ObservationStrategy.Operation.Type.OBSERVE);operation.setObservable(observable);
    strategy.getOperations().add(operation);
    doAnswer(call->{
      org.integratedmodelling.klab.api.knowledge.observation.Observation bound=call.getArgument(0);
      var annotation=bound.getAnnotations().iterator().next();
      assertEquals("label",annotation.getName());
      assertEquals("Cotechino",annotation.get("value"));
      assertSame(concept,annotation.get("meaning"));
      return List.of(strategy);
    }).when(actor).computeObservationStrategies(any(),any());
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(0,runtime.activeCycleCount());
      var binding=runtime.beginCycle(request(null,"annotations"),scope);
      assertEquals(1,runtime.activeCycleCount());
      var result=facade(runtime,binding,"annotations",scope).computeObservationStrategies(observation,scope);
      assertEquals("test:strategy",result.getFirst().getUrn());
      assertEquals(concept.getUrn(),result.getFirst().getOperations().getFirst().getObservable().getUrn());
      assertEquals("Cotechino",observation.getAnnotations().getFirst().get("value"));
      runtime.releaseCycle(request(binding,"annotations"),scope);
      assertEquals(0,runtime.activeCycleCount());
    }
  }
  @Test void retainedSubsumptionIsReflexiveAcrossConceptAndObservableWrappers() throws Exception {
    var actor=actor("fingerprint","test:StreamJunction");
    var junction=actor.resolveConcept("test:StreamJunction");
    doReturn(junction.collective()).when(actor).resolveConcept("each test:StreamJunction");
    var current=new AtomicReference<>(state(actor,"fingerprint"));
    var scope=scope("alice","context","runtime");
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var binding=runtime.beginCycle(request(null,"endpoint-types"),scope);
      try {
        var pinned=facade(runtime,binding,"endpoint-types",scope);
        var observable=org.integratedmodelling.common.knowledge.ObservableImpl.promote(junction,null);
        var detached=org.integratedmodelling.common.utils.Utils.Json.parseObject(
            org.integratedmodelling.common.utils.Utils.Json.asString(observable),org.integratedmodelling.klab.api.knowledge.Observable.class);
        assertTrue(pinned.is(observable,junction));
        assertTrue(pinned.is(junction,detached));
        assertTrue(pinned.is(observable,detached));
        assertTrue(pinned.is(junction,junction));
        assertFalse(pinned.is(observable,junction.collective()));
        assertFalse(pinned.is(org.integratedmodelling.common.knowledge.ObservableImpl.promote(junction.collective(),null),junction));
      } finally {runtime.releaseCycle(request(binding,"endpoint-types"),scope);}
    }
  }

  @Test void oldCycleRetainsActorCapabilitiesAndWitnessAcrossPublicationWhileNewCycleSeesNewActor() throws Exception {
    var first=actor("first-fingerprint","test:First");var second=actor("second-fingerprint","test:Second");
    var oldState=state(first,"first-fingerprint");var newState=state(second,"second-fingerprint");
    var current=new AtomicReference<>(oldState);var scope=scope("alice","context","runtime");
    for(var actor:List.of(first,second))doAnswer(i->{
      var caps=actor.capabilities(i.getArgument(1));
      SemanticDependencyRequest request=i.getArgument(0);
      return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.UNKNOWN,
          caps.getServiceId(),caps.getKnowledgeRevision(),caps.getSemanticContentFingerprint(),request.inputHash(),List.of(),"proof unavailable");
    }).when(actor).semanticDependencies(any(),any());
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var binding=runtime.beginCycle(request(null,"old"),scope);assertEquals(binding,runtime.beginCycle(request(null,"old"),scope));
      var pinned=facade(runtime,binding,"old",scope);
      assertEquals("test:First",pinned.resolveConcept("same").getUrn());
      current.set(newState);var changed=AcceptedSemanticObserverScopeTest.world();
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // changed");
      runtime.submit(changed);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      verify(oldState,never()).close();
      assertEquals("test:First",pinned.resolveConcept("same").getUrn());
      var oldCaps=pinned.capabilities(scope);assertEquals(binding.knowledgeRevision(),oldCaps.getKnowledgeRevision(),
          SemanticCycleCodec.encode(oldCaps,Reasoner.Capabilities.class));
      assertEquals(binding.reasonerServiceId(),oldCaps.getServiceId());assertEquals(binding.reasonerSessionId(),oldCaps.getSemanticSessionId());
      assertEquals(binding.semanticFingerprint(),oldCaps.getSemanticContentFingerprint());
      assertEquals("test:First",pinned.serviceScope().getService(Reasoner.class).resolveConcept("same").getUrn());
      var active=((ContextScope)pinned.serviceScope()).getActiveSessions().getFirst().getActiveContexts().getFirst();
      assertEquals("test:First",active.getService(Reasoner.class).resolveConcept("same").getUrn());
      var witness=pinned.semanticDependencies(new SemanticDependencyRequest(binding.reasonerServiceId(),binding.knowledgeRevision(),binding.semanticFingerprint(),List.of("test:First")),scope);
      assertEquals(binding.knowledgeRevision(),witness.knowledgeRevision());assertEquals(binding.reasonerServiceId(),witness.serviceId());
      assertEquals(SemanticDependencyWitness.Status.UNKNOWN,witness.status()); // A pin never invents an external-authority proof.
      var nextBinding=runtime.beginCycle(request(null,"new"),scope);assertTrue(nextBinding.knowledgeRevision()>binding.knowledgeRevision());
      assertEquals("test:Second",facade(runtime,nextBinding,"new",scope).resolveConcept("same").getUrn());
      var validation=SemanticValidationRequest.of(changed.getOntologies().getFirst(),"changed");
      validation.setKnowledgeRevision(binding.knowledgeRevision());
      var unavailable=pinned.validateDocument(validation,scope);
      assertEquals(SemanticValidationResponse.Status.UNAVAILABLE,unavailable.getStatus());
      assertEquals(binding.knowledgeRevision(),unavailable.getKnowledgeRevision());
      assertEquals(binding.reasonerSessionId(),unavailable.getSemanticSessionId());
      assertNull(unavailable.getSemanticContentFingerprint());assertNull(unavailable.getSemanticWitness());
      runtime.releaseCycle(request(binding,"old"),scope);verify(oldState).close();
      assertThrows(SecurityException.class,()->pinned.resolveConcept("same"));
      assertThrows(SecurityException.class,()->pinned.serviceScope().getService(Reasoner.class).resolveConcept("same"));
      assertThrows(SecurityException.class,()->active.getService(Reasoner.class).resolveConcept("same"));
      runtime.releaseCycle(request(nextBinding,"new"),scope);
    }
    verify(newState).close();
  }

  @Test void scopeFreeCycleCallsNeverLoadObserverFromRuntimeGraph() throws Exception {
    var actor=actor("fingerprint","test:Subject");
    var current=new AtomicReference<>(state(actor,"fingerprint"));
    var scope=scope("alice","context","runtime");
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var binding=runtime.beginCycle(request(null,"scope-free"),scope);
      clearInvocations(scope);
      doThrow(new AssertionError("Unexpected Runtime graph callback")).when(scope).getObserver();
      var pinned=facade(runtime,binding,"scope-free",scope);
      assertEquals("test:Subject",pinned.resolveConcept("test:Subject").getUrn());
      verify(scope,never()).getObserver();
      runtime.releaseCycle(request(binding,"scope-free"),scope);
    }
  }
  @Test void exactOwnerContextRuntimeCycleAndBindingAreRequiredAndShutdownReleasesLeases() throws Exception {
    var actor=actor("fingerprint","test:Old");var state=state(actor,"fingerprint");
    var current=new AtomicReference<>(state);var scope=scope("alice","context","runtime");
    var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner");
    runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
    var binding=runtime.beginCycle(request(null,"one"),scope);
    var mutation=new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.CREATE,"test",null,null,null,null,0,null,null,null,null);
    assertThrows(SecurityException.class,()->facade(runtime,binding,"one",scope).authorityCodelists(mutation,scope));
    verify(actor,never()).authorityCodelists(any(),any());
    for(var invalid:List.of(scope("bob","context","runtime"),scope("alice","other","runtime"),scope("alice","context","other")))
      assertThrows(SecurityException.class,()->runtime.releaseCycle(request(binding,"one"),invalid));
    assertThrows(SecurityException.class,()->runtime.releaseCycle(request(binding,"other"),scope));
    var forged=new SemanticCycleBinding(binding.contractVersion(),binding.reasonerServiceId(),"other-session",binding.knowledgeRevision(),binding.semanticFingerprint(),binding.leaseId());
    assertThrows(SecurityException.class,()->runtime.releaseCycle(request(forged,"one"),scope));
    assertEquals("test:Old",facade(runtime,binding,"one",scope).resolveConcept("still valid").getUrn());
    runtime.close();verify(state).close();
    assertThrows(IllegalStateException.class,()->facade(runtime,binding,"one",scope).resolveConcept("closed"));
  }
  @Test void inFlightCallRetainsRetiredActorUntilItsOwnCompletionEvenAfterCycleRelease() throws Exception {
    var actor=actor("fingerprint","test:Old");var state=state(actor,"fingerprint");
    var current=new AtomicReference<>(state);var scope=scope("alice","context","runtime");
    var entered=new CountDownLatch(1);var resume=new CountDownLatch(1);
    doAnswer(i->{entered.countDown();assertTrue(resume.await(5,TimeUnit.SECONDS));return null;}).when(actor).resolveConcept("blocked");
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner");var executor=Executors.newSingleThreadExecutor()) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var binding=runtime.beginCycle(request(null,"one"),scope);var pinned=facade(runtime,binding,"one",scope);
      var call=executor.submit(()->pinned.resolveConcept("blocked"));assertTrue(entered.await(3,TimeUnit.SECONDS));
      current.set(state(actor("next-fingerprint","test:Next"),"next-fingerprint"));
      var changed=AcceptedSemanticObserverScopeTest.world();
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // retire blocked actor");
      runtime.submit(changed);assertTrue(runtime.awaitIdle(Duration.ofSeconds(3)));
      runtime.releaseCycle(request(binding,"one"),scope);verify(state,never()).close();
      resume.countDown();assertNull(call.get(3,TimeUnit.SECONDS));
      verify(state).close();
      assertThrows(SecurityException.class,()->pinned.resolveConcept("again"));
    }finally{resume.countDown();}
    verify(state).close();
  }
  @Test void explicitNoAcceptedViewIsFrozenUntilReleaseAndNeverSwallowsOwnershipOrShutdownFailures() throws Exception {
    var current=new AtomicReference<>(state(actor("first-fingerprint","test:First"),"first-fingerprint"));
    var scope=scope("alice","context","runtime");
    var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current),"reasoner");
    var request=request(null,"empty");var unavailable=runtime.beginCycleResult(request,scope);
    assertEquals(SemanticCycleBeginResult.Status.NO_ACCEPTED_VIEW,unavailable.status());assertNull(unavailable.binding());
    assertThrows(IllegalStateException.class,()->runtime.beginCycle(request,scope));
    assertThrows(SecurityException.class,()->runtime.beginCycleResult(request,scope("alice","other","runtime")));
    assertThrows(SecurityException.class,()->runtime.releaseCycle(request,scope("bob","context","runtime")));
    runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
    assertEquals(unavailable,runtime.beginCycleResult(request,scope)); // No late binding in this cycle.
    assertEquals(SemanticCycleBeginResult.Status.BOUND,runtime.beginCycleResult(request(null,"fresh"),scope).status());
    runtime.releaseCycle(request,scope);
    assertDoesNotThrow(()->runtime.releaseCycle(request,scope)); // Lost acknowledgement is retryable.
    runtime.close();assertThrows(IllegalStateException.class,()->runtime.beginCycleResult(request(null,"closed"),scope));
  }
}
