package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.resources.persistence.*;
import org.junit.jupiter.api.Test;

class NamespaceModelIndexTest {
  final ServiceScope scope = mock(ServiceScope.class);
  final ResourcesService resources = mock(ResourcesService.class);
  final Reasoner reasoner = mock(Reasoner.class);
  final Reasoner.Capabilities capabilities=mock(Reasoner.Capabilities.class);
  final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong(7);
  final Concept concept = mock(Concept.class);
  final Map<String, KimNamespaceImpl> sources = new LinkedHashMap<>();
  final Set<String> invalid = new HashSet<>();
  final Map<String, SemanticValidationResponse.Status> statuses = new HashMap<>();

  NamespaceModelIndexTest() {
    when(reasoner.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.getServiceId()).thenReturn("reasoner");
    when(capabilities.getKnowledgeRevision()).thenAnswer(call->revision.get());
    when(capabilities.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"a".repeat(64));
    when(reasoner.semanticDependencies(any(),same(scope))).thenAnswer(call -> proof(reasoner.capabilities(scope),call.getArgument(0)));
    when(resources.serviceScope()).thenReturn(scope); when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(concept.getUrn()).thenReturn("w:Value");
    when(reasoner.resolveConcept("w:Value")).thenReturn(concept);
    var fullObservable = mock(Observable.class);
    when(fullObservable.getUrn()).thenReturn("w:Value");
    when(fullObservable.getSemantics()).thenReturn(concept);
    when(reasoner.resolveObservable("w:Value")).thenReturn(fullObservable);
    when(reasoner.coreObservable(concept)).thenReturn(concept);
    when(reasoner.resolving(concept)).thenReturn(List.of(concept));
    when(resources.retrieve(anyString(), eq(KimNamespace.class), isNull()))
        .thenAnswer(invocation -> sources.get(invocation.getArgument(0)));
    when(reasoner.validateDocument(any(), any())).thenAnswer(invocation -> {
      SemanticValidationRequest request = invocation.getArgument(0);
      var response = SemanticValidationResponse.forRequest(request);
      var caps=reasoner.capabilities(scope);
      response.setSemanticContentFingerprint(caps==null?null:caps.getSemanticContentFingerprint());
      response.setSemanticSessionId(caps==null?null:caps.getSemanticSessionId());
      response.setSemanticObligations(List.of("w:Value"));response.setSemanticObligationsComplete(true);
      if(caps!=null)response.setSemanticWitness(proof(caps,new SemanticDependencyRequest(caps.getServiceId(),caps.getKnowledgeRevision(),caps.getSemanticContentFingerprint(),response.getSemanticObligations())));
      response.setStatus(statuses.getOrDefault(request.getNamespace().getUrn(), SemanticValidationResponse.Status.COMPLETE));
      response.setKnowledgeRevision(request.getKnowledgeRevision() < 0 ? 7 : request.getKnowledgeRevision());
      if (invalid.contains(request.getNamespace().getUrn()))
        response.getNotifications().add(Notification.error("MODEL_DEPENDENCY_INCOMPATIBLE: " + request.getNamespace().getUrn()));
      return response;
    });
  }

  private SemanticDependencyWitness proof(Reasoner.Capabilities caps,SemanticDependencyRequest request) {
    return caps==null?null:new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        caps.getServiceId(),caps.getKnowledgeRevision(),caps.getSemanticContentFingerprint(),request.inputHash(),List.of(),null);
  }

  KimNamespaceImpl source(String urn) {
    var source = new KimNamespaceImpl(); source.setUrn(urn); source.setSourceCode(urn + " source");
    var model = new KimModelImpl(); model.setUrn(urn + ".model"); model.setNamespace(urn);
    var observable=new KimObservableImpl();observable.setUrn("w:Value");
    model.getObservables().add(observable); source.getStatements().add(model);
    sources.put(urn, source); return source;
  }

  DocumentModelKbox catalog() {
    var catalog = spy(new DocumentModelKbox(resources, new NitriteKboxStore(null)));
    for (var source : sources.values()) {
      var descriptor = new ModelReference(); descriptor.setName(source.getUrn() + ".model");
      descriptor.setNamespaceId(source.getUrn()); descriptor.setScope(KlabStatement.Scope.PUBLIC);
      descriptor.setObservable("w:Value"); descriptor.setObservableConcept(concept);
      descriptor.setPermissions(ResourcePrivileges.create("*"));
      doReturn(List.of(descriptor)).when(catalog).inferModels((KimModel) source.getStatements().getFirst(), scope);
    }
    return catalog;
  }

  @Test void successfulPublicationReceiptIsDetachedExactAndCannotBeAuthored() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var source=source("published");
    var syntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();syntax.setUrn("w:Value");
    var model=(KimModel)source.getStatements().getFirst();model.getObservables().clear();model.getObservables().add(syntax);
    var semantics=new org.integratedmodelling.common.knowledge.ConceptImpl();semantics.setUrn("w:Value");
    semantics.setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));
    var observable=new org.integratedmodelling.common.knowledge.ObservableImpl();observable.setUrn("w:Value");observable.setSemantics(semantics);
    when(reasoner.resolveObservable("w:Value")).thenReturn(observable);
    var capabilities=mock(Reasoner.Capabilities.class);when(reasoner.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"a".repeat(64));
    when(capabilities.getKnowledgeRevision()).thenReturn(7L);when(capabilities.getServiceId()).thenReturn("reasoner");
    assertDoesNotThrow(() -> org.integratedmodelling.klab.runtime.language.SourceModelMapper.load(model,source,scope));
    var index=new NamespaceModelIndex(5);
    try(var catalog=catalog()) {
      index.reconcile(List.of(source),catalog,scope,7,0);
      var portable=index.forTransport(source);
      var key=org.integratedmodelling.klab.api.services.ResourcesService.NAMESPACE_ADMISSION_METADATA;
      assertNotSame(source,portable);assertFalse(source.getMetadata().containsKey(key));
      assertInstanceOf(String.class,portable.getMetadata().get(key));
      var admission=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
          portable.getMetadata().get(key,String.class),org.integratedmodelling.klab.runtime.language.ModelValidationGate.NamespaceAdmission.class);
      assertEquals("reasoner",admission.reasonerId());assertTrue(admission.modelHashes().containsKey(model.getUrn()));
      verify(reasoner,times(1)).validateDocument(any(),any());
      when(scope.getService(ResourcesService.class)).thenReturn(resources);
      doReturn(portable).when(resources).retrieve(eq(source.getUrn()),eq(KimNamespace.class),isNull());
      var executable=org.integratedmodelling.klab.runtime.language.SourceModelMapper.load(model,source,scope);
      clearInvocations(reasoner);
      assertNotNull(org.integratedmodelling.klab.runtime.language.ModelValidationGate
          .validatedPreparableAdmission(executable,scope,null));
      verify(reasoner,never()).validateDocument(any(),any());
      source.setSourceCode("source changed after publication");source.getMetadata().put(key,"authored fake receipt");
      assertFalse(index.forTransport(source).getMetadata().containsKey(key));
      invalid.add(source.getUrn());index.reconcile(List.of(source),catalog,scope,7,10);
      assertFalse(index.forTransport(source).getMetadata().containsKey(key));
    }
  }

  @Test void mixedNamespacesAreIsolatedWithdrawnAndReadmittedWithoutLosingSourcesOrDiagnostics() {
    var good = source("good"); var bad = source("bad"); invalid.add("bad");
    var index = new NamespaceModelIndex(5);
    try (var catalog = catalog()) {
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 7, 0);
      assertTrue(catalog.hasModel("good.model")); assertFalse(catalog.hasModel("bad.model"));
      var request = mock(Observable.class); when(request.getUrn()).thenReturn("w:Value");
      when(reasoner.coreObservable(request)).thenReturn(concept);
      var geometry = mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
      assertEquals(List.of("good.model"), catalog.queryModels(request, geometry, null, List.of(), null)
          .stream().map(ModelReference::getName).toList());
      assertEquals(Set.of("bad"), index.diagnostics().keySet());
      assertTrue(index.diagnostics().get("bad").getFirst().getMessage().contains("MODEL_DEPENDENCY_INCOMPATIBLE"));
      assertTrue(good.getNotifications().isEmpty()); assertTrue(bad.getNotifications().isEmpty());
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 7, 100);
      verify(catalog, times(1)).replaceNamespace(good, scope);
      verify(catalog, never()).replaceNamespace(bad, scope);

      invalid.add("good"); good.setSourceCode("good changed to invalid");
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 7, 101);
      assertEquals(0, catalog.count()); assertNull(catalog.retrieveModel("good.model", scope));
      invalid.remove("bad"); bad.setSourceCode("bad corrected");
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 7, 102);
      assertEquals(1, catalog.count()); assertTrue(catalog.hasModel("bad.model"));
      assertEquals(List.of("bad.model"), catalog.queryModels(request, geometry, null, List.of(), null)
          .stream().map(ModelReference::getName).toList());
      assertEquals(Set.of("good"), index.diagnostics().keySet());
      assertSame(good, sources.get("good")); assertSame(bad, sources.get("bad"));
    }
  }

  @Test void unavailableAndStaleNamespacesWithdrawOldEntriesAndRetryWithoutAContentChange() {
    var good = source("good"); var pending = source("pending");
    var index = new NamespaceModelIndex(5);
    try (var catalog = catalog()) {
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 7, 0);
      assertEquals(2, catalog.count());
      statuses.put("pending", SemanticValidationResponse.Status.UNAVAILABLE);
      revision.set(8);
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 1);
      assertTrue(catalog.hasModel("good.model"),()->index.diagnostics().toString()); assertFalse(catalog.hasModel("pending.model"));
      statuses.clear();
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 2);
      assertFalse(catalog.hasModel("pending.model"));
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 6);
      assertTrue(catalog.hasModel("pending.model")); assertTrue(index.diagnostics().isEmpty());
      statuses.put("pending", SemanticValidationResponse.Status.STALE_KNOWLEDGE);
      pending.setSourceCode("pending edited");
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 7);
      assertFalse(catalog.hasModel("pending.model")); assertTrue(catalog.hasModel("good.model"));
      statuses.clear();
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 12);
      assertTrue(catalog.hasModel("pending.model"));
    }
  }

  @Test void unchangedReconciliationUsesTwoFreshSemanticSnapshotsRegardlessOfNamespaceCount() {
    for(int i=0;i<20;i++)source("source"+i);
    var catalog=mock(ModelCatalog.class);var index=new NamespaceModelIndex(5);
    index.reconcile(()->new ArrayList<>(sources.values()),catalog,scope,7,0);
    clearInvocations(reasoner,catalog);
    index.reconcile(()->new ArrayList<>(sources.values()),catalog,scope,7,10);
    verify(reasoner,times(2)).capabilities(scope);
    verify(reasoner,never()).validateDocument(any(),any());
    verify(catalog,never()).replaceNamespace(any(),any());
  }

  @Test void incompleteRuntimeFootprintPublishesOnlyExplicitStaticAcceptance() {
    var source=source("static");var catalog=mock(ModelCatalog.class);var index=new NamespaceModelIndex(5);
    doAnswer(call->{
      var request=call.getArgument(0,SemanticValidationRequest.class);var response=SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);
      response.setSemanticContentFingerprint(capabilities.getSemanticContentFingerprint());
      response.setSourceSemanticsComplete(true);response.setSemanticObligations(List.of("w:Value"));
      response.setSemanticWitness(SemanticDependencyWitness.unknown(new SemanticDependencyRequest("reasoner",7,
          capabilities.getSemanticContentFingerprint(),response.getSemanticObligations()),"Runtime footprint incomplete"));return response;
    }).when(reasoner).validateDocument(any(),same(scope));
    index.reconcile(List.of(source),catalog,scope,7,0);
    var portable=index.forTransport(source);
    var admission=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        portable.getMetadata().get(ResourcesService.NAMESPACE_ADMISSION_METADATA).toString(),
        org.integratedmodelling.klab.runtime.language.ModelValidationGate.NamespaceAdmission.class);
    assertEquals(org.integratedmodelling.klab.runtime.language.ModelValidationGate.SOURCE_ADMISSION_CONTRACT,admission.sourceContract());
    assertTrue(admission.modelHashes().isEmpty());
    var response=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(admission.response(),SemanticValidationResponse.class);
    assertFalse(response.isSemanticObligationsComplete());assertTrue(response.isSourceSemanticsComplete());
    assertEquals(SemanticDependencyWitness.Status.UNKNOWN,response.getSemanticWitness().status());
    verify(reasoner,never()).semanticDependencies(any(),any());
  }

  @Test void generationChangeDuringPublishWithdrawsEveryAffectedEntryAndDiscardsClaims() {
    var first=source("first");var second=source("second");
    var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);var visible=new HashSet<String>();
    doAnswer(call->{visible.add(call.getArgument(0,KimNamespace.class).getUrn());revision.set(8);return null;})
        .when(catalog).replaceNamespace(any(),same(scope));
    doAnswer(call->{visible.remove(call.getArgument(0,String.class));return null;}).when(catalog).withholdNamespace(anyString(),same(scope));
    assertThrows(IllegalStateException.class,()->index.reconcile(()->new ArrayList<>(sources.values()),catalog,scope,7,0));
    assertTrue(visible.isEmpty());verify(catalog).withholdNamespace("first",scope);verify(catalog).withholdNamespace("second",scope);
    assertFalse(index.forTransport(first).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    assertFalse(index.forTransport(second).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    verify(catalog,never()).replaceNamespace(same(second),same(scope));
  }

  @Test void mismatchedInitialGenerationCannotPublishAndWithdrawalFailuresPropagate() {
    var source=source("source");var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
    revision.set(8);
    assertThrows(IllegalStateException.class,()->index.reconcile(List.of(source),catalog,scope,7,0));
    verify(catalog,never()).replaceNamespace(any(),any());
    doThrow(new IllegalStateException("withdraw unavailable")).when(catalog).withholdNamespace("source",scope);
    assertEquals("withdraw unavailable",assertThrows(IllegalStateException.class,
        ()->index.reconcile(List.of(source),catalog,scope,7,0)).getMessage());
  }

  @Test void generationChangeWhileReadingFinalManifestIsFencedBeforeClaimsCommit() {
    var source=source("source");var catalog=mock(ModelCatalog.class);var index=new NamespaceModelIndex(5);
    var reads=new java.util.concurrent.atomic.AtomicInteger();
    assertThrows(IllegalStateException.class,()->index.reconcile(()->{
      if(reads.incrementAndGet()==2)revision.set(8);return List.of(source);
    },catalog,scope,7,0));
    verify(catalog).withholdNamespace("source",scope);
    assertFalse(index.forTransport(source).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
  }

  @Test void failedWithdrawalStillDiscardsAllClaimsAndAttemptsEveryNamespace() {
    var first=source("first");var second=source("second");var catalog=mock(ModelCatalog.class);var index=new NamespaceModelIndex(5);
    index.reconcile(List.of(first,second),catalog,scope,7,0);
    assertTrue(index.forTransport(first).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    doThrow(new IllegalStateException("withdraw unavailable")).when(catalog).withholdNamespace("first",scope);
    revision.set(8);clearInvocations(catalog);
    assertThrows(IllegalStateException.class,()->index.reconcile(List.of(first,second),catalog,scope,7,10));
    verify(catalog).withholdNamespace("first",scope);verify(catalog).withholdNamespace("second",scope);
    assertFalse(index.forTransport(first).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    assertFalse(index.forTransport(second).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
  }

  @Test void authoritativeManifestReplacementRemovalAdditionAndOrderChangesAbortPublication() {
    for(String change:List.of("replacement","removal","addition","order")) {
      var first=source("first");var second=source("second");
      var manifest=new java.util.concurrent.atomic.AtomicReference<Collection<KimNamespace>>(List.of(first,second));
      var catalog=mock(ModelCatalog.class);var index=new NamespaceModelIndex(5);var once=new java.util.concurrent.atomic.AtomicBoolean();
      doAnswer(call->{
        if(once.compareAndSet(false,true))switch(change) {
          case "replacement" -> {var replacement=source("first");replacement.setSourceCode("changed source");manifest.set(List.of(replacement,second));}
          case "removal" -> manifest.set(List.of(first));
          case "addition" -> manifest.set(List.of(first,second,source("third")));
          case "order" -> manifest.set(List.of(second,first));
        }
        return null;
      }).when(catalog).replaceNamespace(any(),same(scope));
      assertThrows(IllegalStateException.class,()->index.reconcile(manifest::get,catalog,scope,7,0),change);
      assertFalse(index.forTransport(first).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA),change);
      verify(catalog).withholdNamespace("first",scope);verify(catalog).withholdNamespace("second",scope);
      sources.clear();
    }
  }

  @Test void unchangedSourceWithInactiveOrDeferredConfigurationIsWithdrawn() {
    for(String configuration:List.of("inactive","deferred","error")) {
      var source=source("configured");var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
      index.reconcile(List.of(source),catalog,scope,7,0);clearInvocations(catalog);
      switch(configuration) {
        case "inactive" -> source.setInactive(true);
        case "deferred" -> source.getMetadata().put(ResourcesService.VALIDATION_DEFERRED_METADATA,true);
        case "error" -> source.getNotifications().add(Notification.error("invalid source"));
      }
      index.reconcile(List.of(source),catalog,scope,7,10);
      verify(catalog).withholdNamespace("configured",scope);verify(catalog,never()).replaceNamespace(any(),any());
      assertFalse(index.forTransport(source).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));sources.clear();
    }
  }

  @Test void namespaceConfigurationChangesInvalidateDetachedClaimsAndReindexWithoutTextChanges() {
    for(String configuration:List.of("scenario","scope","criteria","disjoint","imports")) {
      var source=source("configured");var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
      index.reconcile(List.of(source),catalog,scope,7,0);
      assertTrue(index.forTransport(source).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
      switch(configuration) {
        case "scenario" -> source.setScenario(true);
        case "scope" -> source.setScope(KlabStatement.Scope.PRIVATE);
        case "criteria" -> source.getResolutionCriteria().put("lexical",1);
        case "disjoint" -> source.getDisjointNamespaces().add("other");
        case "imports" -> source.getImports().put("other",null);
      }
      assertFalse(index.forTransport(source).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA),configuration);
      index.reconcile(List.of(source),catalog,scope,7,10);
      verify(catalog,times(2)).replaceNamespace(source,scope);
      assertTrue(index.forTransport(source).getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA),configuration);sources.clear();
    }
  }
}
