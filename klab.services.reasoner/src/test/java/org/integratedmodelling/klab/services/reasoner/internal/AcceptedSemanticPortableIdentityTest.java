package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.*;

class AcceptedSemanticPortableIdentityTest {
  @BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private static void field(Object target,String name,Object value) throws Exception {
    var f=ReasonerService.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  /** The infrastructure is mocked; capability publication and lexical lease methods are real. */
  private static ReasonerService actor() throws Exception {
    var actor=mock(ReasonerService.class,CALLS_REAL_METHODS);
    var validation=new SemanticValidationState();validation.settle(422,true);
    field(actor,"semanticOnly",true);field(actor,"knowledgeRevision",new AtomicLong(422));
    field(actor,"semanticValidationState",validation);
    field(actor,"semanticSessionId",UUID.randomUUID().toString());
    var config=new ReasonerConfiguration();config.setServiceId("private-compiler-id");field(actor,"configuration",config);
    field(actor,"publishedSemanticCapabilities",SemanticCapabilitiesSnapshot.capture(0,"w","w",true,List.of(),null));
    doReturn(null).when(actor).getComponentRegistry();
    return actor;
  }
  @Test void portableCapabilityScopeIsLexicalThreadLocalAndLeavesInternalRevisionUntouched() throws Exception {
    var actor=actor();
    assertEquals("private-compiler-id",actor.capabilities(null).getServiceId());
    try(var outer=actor.portableCapabilities("deployed-reasoner",7)) {
      assertEquals("deployed-reasoner",actor.capabilities(null).getServiceId());
      assertEquals(7,actor.capabilities(null).getKnowledgeRevision());assertEquals(422,actor.knowledgeRevision());
      try(var inner=actor.portableCapabilities("deployed-reasoner",9)) {
        assertEquals(9,actor.capabilities(null).getKnowledgeRevision());
      }
      var other=actor();
      try(var inner=other.portableCapabilities("another-deployment",13)) {
        assertEquals("another-deployment",other.capabilities(null).getServiceId());
        assertEquals(13,other.capabilities(null).getKnowledgeRevision());
        assertEquals("deployed-reasoner",actor.capabilities(null).getServiceId());
        assertEquals(7,actor.capabilities(null).getKnowledgeRevision());
      }
      assertEquals("private-compiler-id",other.capabilities(null).getServiceId());
      assertEquals(7,actor.capabilities(null).getKnowledgeRevision());
      try(var executor=Executors.newSingleThreadExecutor()) {
        var caps=executor.submit(()->actor.capabilities(null)).get(2,TimeUnit.SECONDS);
        assertEquals("private-compiler-id",caps.getServiceId());assertEquals(422,caps.getKnowledgeRevision());
      }
      assertThrows(IllegalStateException.class,()->{
        try(var failing=actor.portableCapabilities("deployed-reasoner",11)) {throw new IllegalStateException("test");}
      });
      assertEquals(7,actor.capabilities(null).getKnowledgeRevision());
    }
    assertEquals("private-compiler-id",actor.capabilities(null).getServiceId());
    assertEquals(422,actor.capabilities(null).getKnowledgeRevision());
    assertThrows(IllegalArgumentException.class,()->actor.portableCapabilities("deployed-reasoner",0));
  }

  private static ObservableImpl observable(String urn,SemanticType... types) {
    var c=new ConceptImpl();c.setUrn(urn);c.setType(EnumSet.copyOf(List.of(types)));
    var o=new ObservableImpl();o.setSemantics(c);o.setUrn(urn);return o;
  }
  @Test void bindingCapturedInsideActualInvocationMatchesFacadeAndRejectsOtherGenerations() throws Exception {
    var actor=actor();var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("scope");
    var selected=spy(observable("each test:Reference test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE));
    ((ConceptImpl)selected.asConcept()).setCollective(true);selected.setDescriptionType(Contextualization.INSTANTIATION);
    var nativeMembers=observable("each test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE);
    ((ConceptImpl)nativeMembers.asConcept()).setCollective(true);
    var builder=mock(Observable.Builder.class,RETURNS_SELF);doReturn(builder).when(selected).builder(scope);
    when(builder.buildObservable()).thenReturn(nativeMembers);
    var predicate=observable("test:Reference",SemanticType.ROLE,SemanticType.PREDICATE).asConcept();
    var directive=observable("any test:Reference of each test:Tree",SemanticType.ROLE,SemanticType.PREDICATE);
    directive.setDescriptionType(Contextualization.CLASSIFICATION);
    doReturn(List.of()).when(actor).directTraits(selected);doReturn(List.of(predicate)).when(actor).directRoles(selected);
    doReturn(predicate).when(actor).lexicalRoot(predicate);
    doReturn(directive).when(actor).resolveObservable(directive.getUrn());
    var request=new ObservationImpl();request.setObservable(selected);request.setGeometry(Geometry.create("S2(2,2)"));
    var captured=new AtomicReference<CollectiveSelectionBinding>();
    doAnswer(i->{captured.set(CollectiveSelectionBinding.capture(request,scope,actor));return true;})
        .when(actor).derived(null);
    var state=mock(SemanticEnvironment.class);when(state.actor()).thenReturn(actor);
    var reference=new AtomicReference<>(state);
    var factory=AcceptedSemanticObserverScopeTest.factory(reference);
    try(var runtime=new AcceptedSemanticRuntime(factory,"deployed-reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertTrue(runtime.<Boolean>invoke("derived",new Class<?>[]{Semantics.class},new Object[]{null}));
      var binding=captured.get();assertNotNull(binding);
      assertEquals("deployed-reasoner",binding.reasonerId());assertEquals(runtime.status().acceptedGeneration(),binding.revision());
      assertEquals(422,actor.knowledgeRevision());assertEquals("private-compiler-id",actor.capabilities(null).getServiceId());
      var facade=mock(Reasoner.class);var caps=mock(Reasoner.Capabilities.class);
      when(scope.getService(Reasoner.class)).thenReturn(facade);when(facade.capabilities(scope)).thenReturn(caps);
      when(caps.getServiceId()).thenReturn("deployed-reasoner");when(caps.getKnowledgeRevision()).thenReturn(binding.revision());
      when(caps.getSemanticSessionId()).thenReturn(binding.semanticSessionId());
      when(caps.getSemanticContentFingerprint()).thenReturn(binding.semanticFingerprint());
      assertDoesNotThrow(()->binding.require(scope,selected,request.getGeometry()));
      when(caps.getKnowledgeRevision()).thenReturn(binding.revision()+1);
      assertThrows(IllegalStateException.class,()->binding.require(scope,selected,request.getGeometry()));
      doThrow(new IllegalStateException("deliberate actor failure")).when(actor).derived(null);
      assertThrows(IllegalStateException.class,()->runtime.invoke("derived",new Class<?>[]{Semantics.class},new Object[]{null}));
      assertEquals("private-compiler-id",actor.capabilities(null).getServiceId());
      when(caps.getKnowledgeRevision()).thenReturn(binding.revision());when(caps.getServiceId()).thenReturn("another-deployment");
      assertThrows(IllegalStateException.class,()->binding.require(scope,selected,request.getGeometry()));
    }
  }

  @Test void continuedSearchKeepsItsLeasedPortableGenerationAfterPublication() throws Exception {
    var first=actor();var second=actor();var observed=new ArrayList<Long>();
    for(var actor:List.of(first,second)) doAnswer(i->{
      observed.add(actor.capabilities(i.getArgument(1)).getKnowledgeRevision());
      return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse(17,0);
    }).when(actor).semanticSearch(any(),any());
    var state1=mock(SemanticEnvironment.class);when(state1.actor()).thenReturn(first);
    var state2=mock(SemanticEnvironment.class);when(state2.actor()).thenReturn(second);
    var reference=new AtomicReference<>(state1);
    var scope=mock(org.integratedmodelling.klab.api.scope.Scope.class);
    try(var runtime=new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(reference),"deployed-reasoner")) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long old=runtime.status().acceptedGeneration();
      var request=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      var response=runtime.search(request,scope);assertTrue(response.getSearchId()>0);
      reference.set(state2);var changed=AcceptedSemanticObserverScopeTest.world();
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // changed");
      runtime.submit(changed);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long current=runtime.status().acceptedGeneration();assertTrue(current>old);
      request.setSearchId(response.getSearchId());runtime.search(request,scope);
      runtime.search(new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest(),scope);
      assertEquals(List.of(old,old,current),observed);
      assertEquals("private-compiler-id",first.capabilities(null).getServiceId());
      assertEquals("private-compiler-id",second.capabilities(null).getServiceId());
    }
  }
}
