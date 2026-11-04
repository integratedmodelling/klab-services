package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class SemanticCycleFacadeTest {
  @Test void pureCallsTransportIdentityAndLeaseWithoutNativePerspective() {
    var scope=scope();var focus=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    when(focus.getId()).thenReturn(42L);when(scope.getContextObservation()).thenReturn(focus);
    when(scope.getSourceObservation()).thenReturn(focus);when(scope.getTargetObservation()).thenReturn(focus);
    when(scope.getObserverId()).thenReturn(7L);when(scope.getTransactionId()).thenReturn("transaction");
    var binding=new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
        1,"cycle","context","runtime",true,request("cycle").binding());
    when(scope.getResolutionCycleBinding()).thenReturn(binding);
    var call=new SemanticCycleInvocation(request("cycle"),"resolveConcept",List.of(String.class.getName()),List.of("\"test:Tree\""));
    var lean=(ContextScope)SemanticCycleFacade.transportScope(call,scope);
    assertEquals("context",ContextScope.getScopeId(lean));assertEquals("runtime",lean.getHostServiceId());
    assertSame(binding,lean.getResolutionCycleBinding());
    assertNull(lean.getSourceObservation());assertNull(lean.getTargetObservation());assertNull(lean.getTransactionId());
    assertEquals("context.42#7",ContextScope.getScopeId(scope));
    var scoped=new SemanticCycleInvocation(request("cycle"),"buildConcept",
        List.of(ObservableBuildStrategy.class.getName(),Scope.class.getName()),List.of("null","null"));
    assertSame(scope,SemanticCycleFacade.transportScope(scoped,scope));
    var capabilities=new SemanticCycleInvocation(request("cycle"),"capabilities",List.of(Scope.class.getName()),List.of("null"));
    assertNull(((ContextScope)SemanticCycleFacade.transportScope(capabilities,scope)).getObserverId());
    var validation=new SemanticCycleInvocation(request("cycle"),"validateDocument",
        List.of(SemanticValidationRequest.class.getName(),Scope.class.getName()),List.of("null","null"));
    assertSame(scope,SemanticCycleFacade.transportScope(validation,scope));
  }

  @Test void concurrentCodecReadersKeepResultsDetached() throws Exception {
    var type=Reasoner.class.getMethod("resolving",Semantics.class).getGenericReturnType();
    var json=SemanticCycleCodec.encode(List.of(concept("test:Tree")),type);
    try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var work=new ArrayList<java.util.concurrent.Future<Concept>>();
      for(int i=0;i<32;i++)work.add(pool.submit(()->{
        var values=(List<Concept>)SemanticCycleCodec.decode(json,type);
        assertEquals("test:Tree",values.getFirst().getUrn());return values.getFirst();
      }));
      var values=new java.util.IdentityHashMap<Concept,Boolean>();
      for(var task:work)values.put(task.get(5,java.util.concurrent.TimeUnit.SECONDS),true);
      assertEquals(32,values.size());
    }
  }
  static Concept concept(String urn) {
    var c=new ConceptImpl();c.setUrn(urn);c.setType(EnumSet.of(SemanticType.SUBJECT));return c;
  }
  static SemanticCycleRequest request(String id) {
    return new SemanticCycleRequest("runtime","context",id,new SemanticCycleBinding(
        SemanticCycleBinding.VERSION,"reasoner","session",3,"fingerprint",id));
  }
  static ContextScope scope() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    when(scope.getHostServiceId()).thenReturn("runtime");return scope;
  }
  static SemanticCycleResult invoke(SemanticCycleInvocation invocation,Reasoner actor) {
    try {
      var method=SemanticCycleCodec.method(invocation.method(),invocation.parameterTypes());
      var types=method.getGenericParameterTypes();var arguments=new Object[types.length];
      for(int i=0;i<types.length;i++)arguments[i]=Scope.class.isAssignableFrom(method.getParameterTypes()[i])
          ?null:SemanticCycleCodec.decode(invocation.arguments().get(i),types[i]);
      var result=method.invoke(actor,arguments);var outputs=new ArrayList<String>();
      for(int i=0;i<types.length;i++)outputs.add(SemanticCycleCodec.encode(arguments[i],types[i]));
      return new SemanticCycleResult(SemanticCycleCodec.encode(result,method.getGenericReturnType()),outputs);
    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
  }
  @Test void concreteConceptAndObservableSurviveGenericSemanticsRoundTrip() throws Exception {
    var method=Reasoner.class.getMethod("concretize",Semantics.class,Map.class);
    var observable=new ObservableImpl();observable.setUrn("test:Tree");observable.setSemantics(concept("test:Tree"));
    for(var meaning:List.of(concept("test:Tree"),observable)) {
      var result=SemanticCycleCodec.decode(SemanticCycleCodec.encode(meaning,method.getGenericReturnType()),method.getGenericReturnType());
      assertEquals(meaning.getUrn(),((Semantics)result).getUrn());
      assertEquals(meaning instanceof Observable,result instanceof Observable);
    }
    var mapping=Map.of(concept("test:Abstract"),concept("test:Concrete"));
    var decoded=(Map<?,?>)SemanticCycleCodec.decode(SemanticCycleCodec.encode(mapping,method.getGenericParameterTypes()[1]),method.getGenericParameterTypes()[1]);
    assertEquals("test:Abstract",((Concept)decoded.keySet().iterator().next()).getUrn());
    assertEquals("test:Concrete",((Concept)decoded.values().iterator().next()).getUrn());
  }
  @Test void capabilitiesRetainPortableRevisionSessionAndIdentity() {
    assertEquals(Reasoner.Capabilities.class,SemanticCycleCodec.method("capabilities",List.of(Scope.class.getName())).getReturnType());
    var caps=new org.integratedmodelling.common.services.ReasonerCapabilitiesImpl();
    caps.setKnowledgeRevision(17);caps.setServiceId("reasoner");caps.setSemanticSessionId("session");
    var json=SemanticCycleCodec.encode(caps,Reasoner.Capabilities.class);
    var result=(Reasoner.Capabilities)SemanticCycleCodec.decode(json,Reasoner.Capabilities.class);
    assertEquals(17,result.getKnowledgeRevision(),json);assertEquals("session",result.getSemanticSessionId());
    assertEquals("reasoner",result.getServiceId());
  }
  @Test void outputMatchesAndOverloadedGenericConcretizeAreNotLost() {
    var actor=mock(Reasoner.class);var context=scope();
    when(actor.match(any(),any(),any())).thenAnswer(i->{((Map<Concept,Concept>)i.getArgument(2)).put(concept("test:A"),concept("test:B"));return true;});
    when(actor.concretize(any(Semantics.class),anyMap())).thenAnswer(i->i.getArgument(0));
    when(actor.concretize(any(Semantics.class),anyList())).thenAnswer(i->i.getArgument(0));
    var facade=SemanticCycleFacade.create(mock(Reasoner.class),request("one"),context,(q,s)->invoke(q,actor));
    var output=new LinkedHashMap<Concept,Concept>();
    assertTrue(facade.match(concept("test:A"),concept("test:B"),output));
    assertEquals("test:B",output.values().iterator().next().getUrn());
    assertEquals("test:A",facade.concretize(concept("test:A"),output).getUrn());
    var observable=new ObservableImpl();observable.setUrn("test:Tree");observable.setSemantics(concept("test:Tree"));
    assertInstanceOf(Observable.class,facade.concretize(observable,new ArrayList<>(output.values())));
  }
  @Test void noScopeCallsUseTheirFixedBindingWithoutSharedClientCaches() {
    var base=mock(Reasoner.class);var seen=new ArrayList<SemanticCycleBinding>();var calls=new AtomicInteger();
    java.util.function.BiFunction<SemanticCycleInvocation,Scope,SemanticCycleResult> transport=(q,s)->{
      seen.add(q.cycle().binding());calls.incrementAndGet();
      return new SemanticCycleResult(SemanticCycleCodec.encode(concept("test:"+q.cycle().cycleId()),Concept.class),q.arguments());
    };
    var first=SemanticCycleFacade.create(base,request("one"),scope(),transport);
    var second=SemanticCycleFacade.create(base,request("two"),scope(),transport);
    assertEquals("test:one",first.resolveConcept("same").getUrn());
    assertEquals("test:two",second.resolveConcept("same").getUrn());
    first.resolveConcept("same");assertEquals(3,calls.get());
    assertEquals(List.of(request("one").binding(),request("two").binding(),request("one").binding()),seen);
    verifyNoInteractions(base);
  }
  @Test void explicitUserScopeCannotErasePinAndOtherContextCannotBorrowIt() {
    var original=scope();var user=mock(UserScope.class);var seen=new ArrayList<Scope>();
    var facade=SemanticCycleFacade.create(mock(Reasoner.class),request("one"),original,(q,s)->{
      seen.add(s);return new SemanticCycleResult("null",q.arguments());
    });
    facade.baseSubstantialType(concept("test:Tree"),user);assertSame(original,seen.getFirst());
    var other=mock(ContextScope.class);when(other.getId()).thenReturn("other");
    assertThrows(SecurityException.class,()->facade.baseSubstantialType(concept("test:Tree"),other));
  }
  @Test void administrativeAndSessionOperationsCannotEscapeThePin() {
    var facade=SemanticCycleFacade.create(mock(Reasoner.class),request("one"),scope(),(q,s)->{throw new AssertionError();});
    assertThrows(UnsupportedOperationException.class,()->facade.configureAuthority(null,scope()));
    assertThrows(UnsupportedOperationException.class,()->facade.semanticSearch(null));
    assertThrows(SecurityException.class,()->SemanticCycleCodec.method("configureAuthority",List.of()));
    assertThrows(SecurityException.class,()->SemanticCycleCodec.decode("{}",Scope.class));
  }
  @Test void serviceMetadataNestedScopesAndAllReasonerSelectorsStayBound() {
    var base=mock(Reasoner.class);var metadata=mock(ServiceScope.class);var nested=scope();
    when(base.serviceScope()).thenReturn(metadata);when(metadata.getParentScope()).thenReturn(nested);
    when(metadata.getService(Reasoner.class)).thenReturn(base);
    when(metadata.getServices(org.integratedmodelling.klab.api.services.KlabService.class)).thenReturn(List.of(base));
    var bound=SemanticCycleFacade.create(base,request("one"),scope(),(q,s)->{throw new AssertionError();});
    var wrapped=bound.serviceScope();assertInstanceOf(ServiceScope.class,wrapped);
    assertSame(bound,wrapped.getService(Reasoner.class));
    assertEquals(List.of(bound),wrapped.getServices(Reasoner.class));
    assertSame(bound,wrapped.findService(Reasoner.class,candidate->candidate==bound).orElseThrow());
    assertTrue(wrapped.findService(Reasoner.class,candidate->candidate==base).isEmpty());
    assertEquals(List.of(bound),wrapped.getServices(org.integratedmodelling.klab.api.services.KlabService.class));
    assertSame(bound,wrapped.findService(org.integratedmodelling.klab.api.services.KlabService.class,candidate->candidate==bound).orElseThrow());
    assertSame(bound,wrapped.getParentScope().getService(Reasoner.class));
    assertThrows(UnsupportedOperationException.class,()->wrapped.getService(ReasonerClient.class));
    var concrete=mock(ReasonerClient.class);
    when(metadata.getServices(BaseServiceClient.class)).thenReturn(List.of(concrete));
    assertThrows(UnsupportedOperationException.class,()->wrapped.findService(BaseServiceClient.class,candidate->{throw new AssertionError("Unbound selector was called");}));
    assertEquals(wrapped,wrapped);verify(metadata,never()).getService(Reasoner.class);
  }
  @Test void advertisedActiveSessionAndContextCollectionsKeepTheirScopeTypesAndBoundDiscovery() {
    var base=mock(Reasoner.class);var metadata=mock(UserScope.class);var session=mock(SessionScope.class);var context=scope();
    when(base.serviceScope()).thenReturn(metadata);when(metadata.getActiveSessions()).thenReturn(List.of(session));
    when(session.getActiveContexts()).thenReturn(List.of(context));when(context.getService(Reasoner.class)).thenReturn(base);
    var bound=SemanticCycleFacade.create(base,request("one"),scope(),(q,s)->{throw new AssertionError();});
    var sessions=((UserScope)bound.serviceScope()).getActiveSessions();assertInstanceOf(List.class,sessions);
    var contexts=sessions.getFirst().getActiveContexts();assertInstanceOf(List.class,contexts);
    assertInstanceOf(ContextScope.class,contexts.getFirst());
    assertSame(bound,contexts.getFirst().getService(Reasoner.class));
    assertSame(bound,sessions.getFirst().getService(Reasoner.class));
    verify(context,never()).getService(Reasoner.class);
  }
  @Test void everySemanticInterfaceOperationIsExplicitlyClassified() {
    var excluded=Set.of("configureAuthority","semanticSearch","beginSemanticCycle","beginSemanticCycleResult","releaseSemanticCycle","bindSemanticCycle");
    for(Method method:Reasoner.class.getDeclaredMethods())
      if(java.lang.reflect.Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
        assertEquals(!excluded.contains(method.getName()),SemanticCycleCodec.allowed(method),method.toString());
  }
}
