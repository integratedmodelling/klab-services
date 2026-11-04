package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode;
import org.junit.jupiter.api.Test;

class FrozenCatalogReasonerRoutingTest {
  static final String RECIPE="semantic-content-v2:"+"a".repeat(64);
  @Test void documentQueryKeepsOldActorForExpansionDecodeAdmissionAndMatchingAfterPublication() { query(false); }
  @Test void sqlQueryKeepsOldActorForExpansionDecodeAdmissionAndMatchingAfterPublication() { query(true); }
  @Test void sqlPublicQueryCannotReturnNoModelBeforeCheckingAnUnavailableFrozenCatalog() {
    var provider=mock(org.integratedmodelling.klab.services.resources.ResourcesProvider.class);
    when(provider.serviceName()).thenReturn("empty-frozen-"+UUID.randomUUID());
    when(provider.serviceScope()).thenReturn(mock(org.integratedmodelling.klab.api.scope.ServiceScope.class));
    var scope=mock(ContextScope.class);
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,RECIPE,"lease")));
    when(provider.captureModelQueryView(scope)).thenThrow(new IllegalStateException("unavailable frozen catalog"));
    var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);when(observable.getUrn()).thenReturn("test:Height");
    try(var catalog=ModelKbox.create(provider)) {
      assertThrows(RuntimeException.class,()->catalog.query(observable,null,null,List.of(),scope,ModelDiscoveryMode.EXECUTABLE));
      verify(provider).captureModelQueryView(scope);
    }
  }
  private void query(boolean sql) {
    var f=new DocumentModelKboxTest();var old=f.reasoner;var caps=mock(Reasoner.Capabilities.class);
    when(f.service.serviceName()).thenReturn("frozen-query-"+UUID.randomUUID());
    when(old.capabilities(any())).thenReturn(caps);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(7L);
    when(caps.getSemanticContentFingerprint()).thenReturn(RECIPE);when(caps.getSemanticSessionId()).thenReturn("session");
    doAnswer(call->{
      var response=SemanticValidationResponse.forRequest(call.getArgument(0));response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(7);response.setSemanticContentFingerprint(RECIPE);response.setSemanticSessionId("session");return response;
    }).when(old).validateDocument(any(),any());
    var source=f.service.retrieve("ns",KimNamespace.class,null);((org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl)source).setSourceCode("saved source");
    var head=mock(Concept.class);when(head.getUrn()).thenReturn("test:Height");when(old.coreObservable(f.concept)).thenReturn(head);
    var requested=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(requested.getContextualization()).thenReturn(Contextualization.QUANTIFICATION);
    when(old.coreObservable(requested)).thenReturn(head);when(old.resolving(head)).thenReturn(List.of(head));
    var scope=mock(ContextScope.class);when(scope.getService(Reasoner.class)).thenReturn(old);
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,RECIPE,"lease")));
    var scale=mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
    var descriptor=new ModelReference();descriptor.setName("ns.o'hare");descriptor.setNamespaceId("ns");descriptor.setScope(KlabStatement.Scope.PUBLIC);
    descriptor.setObservable(f.concept.getUrn());descriptor.setObservableConcept(f.concept);
    descriptor.setPermissions(org.integratedmodelling.klab.api.authentication.ResourcePrivileges.create("*"));
    try(ModelCatalog box=sql?ModelKbox.create(f.service):new DocumentModelKbox(f.service,new NitriteKboxStore(null))) {
      box.store(descriptor,f.scope);
      var current=mock(Reasoner.class,invocation->{throw new AssertionError("Current actor queried: "+invocation.getMethod().getName());});
      when(f.scope.getService(Reasoner.class)).thenReturn(current);clearInvocations(old);
      assertEquals(1,box.queryModels(requested,scale,null,List.of(),scope,ModelDiscoveryMode.EXECUTABLE).size());
      verify(old).coreObservable(requested);verify(old).semanticDistance(any(),same(requested),isNull(),eq(7L));
      verifyNoInteractions(current);
    }
  }
}
