package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.resources.persistence.ModelCatalog;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.*;

class NamespaceModelQueryViewTest {
  static final String RECIPE="semantic-content-v2:"+"a".repeat(64);
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  static NamespaceModelIndexTest fixture() {
    var f=new NamespaceModelIndexTest();when(f.capabilities.getSemanticSessionId()).thenReturn("session");
    var original=mockingDetails(f.reasoner).getStubbings().stream().filter(s->s.getInvocation().getMethod().getName().equals("validateDocument")).findFirst().orElseThrow();
    doAnswer(call->{var response=(SemanticValidationResponse)original.answer(call);response.setSourceSemanticsComplete(true);return response;})
        .when(f.reasoner).validateDocument(any(),any());return f;
  }
  static ModelValidationGate.SemanticSnapshot selected(long revision) {return new ModelValidationGate.SemanticSnapshot("reasoner",revision,RECIPE,"session");}
  @Test void unchangedReceiptWithdrawalDoesNotInvalidateStableCatalogButActualReplacementDoes() {
    var f=fixture();var source=f.source("source");var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
    index.reconcile(List.of(source),catalog,f.scope,7,0);var original=index.queryView(List.of(source),selected(7));
    index.reconcile(List.of(source),catalog,f.scope,7,10);
    assertSame(original,index.queryView(List.of(source),selected(7)));index.requireQueryView(original,List.of(source),selected(7));
    f.revision.set(8);index.reconcile(List.of(source),catalog,f.scope,8,20);
    assertThrows(RuntimeException.class,()->index.requireQueryView(original,List.of(source),selected(7)));
    assertNotNull(index.queryView(List.of(source),selected(7))); // Same recipe, old actor remains selected.
  }
  @Test void differentUnknownRecipeSessionOrSourceDriftAreExplicitlyUnavailable() {
    var f=fixture();var source=f.source("source");var index=new NamespaceModelIndex(5);
    index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,7,0);var view=index.queryView(List.of(source),selected(7));
    for(var other:List.of(new ModelValidationGate.SemanticSnapshot("reasoner",7,"semantic-content-v2:"+"b".repeat(64),"session"),
        new ModelValidationGate.SemanticSnapshot("reasoner",7,null,"session"),new ModelValidationGate.SemanticSnapshot("reasoner",7,"semantic-content-v1:"+"a".repeat(64),"session"),
        new ModelValidationGate.SemanticSnapshot("other",7,RECIPE,"session"),
        new ModelValidationGate.SemanticSnapshot("reasoner",7,RECIPE,"other")))
      assertTrue(assertThrows(RuntimeException.class,()->index.queryView(List.of(source),other)).getMessage().contains("MODEL_CATALOG_VIEW_UNAVAILABLE"));
    source.setSourceCode("changed source");
    assertThrows(RuntimeException.class,()->index.requireQueryView(view,List.of(source),selected(7)));
  }
  @Test void viewIsWithdrawnBeforeMutationAndReaderDoesNotWaitForWriterSemanticWork() throws Exception {
    var f=fixture();var source=f.source("source");var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
    index.reconcile(List.of(source),catalog,f.scope,7,0);var captured=index.queryView(List.of(source),selected(7));
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;}).when(catalog).replaceNamespace(source,f.scope);
    f.revision.set(8);
    try(var executor=Executors.newSingleThreadExecutor()) {
      var writer=executor.submit(()->index.reconcile(List.of(source),catalog,f.scope,8,20));assertTrue(entered.await(3,TimeUnit.SECONDS));
      assertThrows(RuntimeException.class,()->index.requireQueryView(captured,List.of(source),selected(7)));
      release.countDown();writer.get(3,TimeUnit.SECONDS);
    }finally{release.countDown();}
  }
  @Test void oldEpochCannotTreatWithheldOrIncompleteCatalogAsSuccessfulEmpty() {
    var f=fixture();var source=f.source("source");f.invalid.add("source");var index=new NamespaceModelIndex(5);
    index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,7,0);
    assertThrows(RuntimeException.class,()->index.queryView(List.of(source),selected(6)));
  }
  @Test void providerBoundReadinessUsesOnlyPinnedActorAndNeverReconcilesThroughCurrentActor() throws Exception {
    var f=fixture();var source=f.source("source");var index=new NamespaceModelIndex(5);
    f.revision.set(8);index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,8,0);
    var provider=mock(ResourcesProvider.class,CALLS_REAL_METHODS);var workspace=mock(WorkspaceManager.class);
    when(workspace.getNamespaces()).thenReturn(List.of(source));
    for(var entry:Map.of("namespaceModelIndex",(Object)index,"workspaceManager",workspace).entrySet()) {
      var field=ResourcesProvider.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(provider,entry.getValue());
    }
    var scope=mock(ContextScope.class);var old=mock(Reasoner.class);var caps=mock(Reasoner.Capabilities.class);
    when(scope.getService(Reasoner.class)).thenReturn(old);when(old.capabilities(scope)).thenReturn(caps);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(7L);
    when(caps.getSemanticContentFingerprint()).thenReturn(RECIPE);when(caps.getSemanticSessionId()).thenReturn("session");
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,RECIPE,"lease")));
    clearInvocations(f.reasoner);
    assertTrue(provider.checkSemanticServices(scope));var captured=provider.captureModelQueryView(scope);provider.requireModelQueryView(captured,scope);
    verifyNoInteractions(f.reasoner);verify(provider,never()).serviceScope();verify(old,times(3)).capabilities(scope);
    when(caps.getKnowledgeRevision()).thenReturn(8L);
    assertTrue(assertThrows(RuntimeException.class,()->provider.requireModelQueryView(captured,scope)).getMessage().contains("selected semantic actor"));
    when(caps.getKnowledgeRevision()).thenReturn(7L);
    when(f.capabilities.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"b".repeat(64));
    index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,8,20);clearInvocations(f.reasoner);
    var unavailable=assertThrows(RuntimeException.class,()->provider.resolveModels(null,null,null,List.of(),scope,
        org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode.PREPARABLE));
    assertTrue(unavailable.getMessage().contains("MODEL_CATALOG_VIEW_UNAVAILABLE"));verifyNoInteractions(f.reasoner);
  }
}
