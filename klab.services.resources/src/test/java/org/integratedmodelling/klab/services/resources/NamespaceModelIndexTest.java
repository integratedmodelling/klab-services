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
  final Concept concept = mock(Concept.class);
  final Map<String, KimNamespaceImpl> sources = new LinkedHashMap<>();
  final Set<String> invalid = new HashSet<>();
  final Map<String, SemanticValidationResponse.Status> statuses = new HashMap<>();

  NamespaceModelIndexTest() {
    when(resources.serviceScope()).thenReturn(scope); when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(concept.getUrn()).thenReturn("w:Value");
    when(reasoner.resolveConcept("w:Value")).thenReturn(concept);
    when(reasoner.coreObservable(concept)).thenReturn(concept);
    when(reasoner.resolving(concept)).thenReturn(List.of(concept));
    when(resources.retrieve(anyString(), eq(KimNamespace.class), isNull()))
        .thenAnswer(invocation -> sources.get(invocation.getArgument(0)));
    when(reasoner.validateDocument(any(), any())).thenAnswer(invocation -> {
      SemanticValidationRequest request = invocation.getArgument(0);
      var response = SemanticValidationResponse.forRequest(request);
      response.setStatus(statuses.getOrDefault(request.getNamespace().getUrn(), SemanticValidationResponse.Status.COMPLETE));
      response.setKnowledgeRevision(request.getKnowledgeRevision() < 0 ? 7 : request.getKnowledgeRevision());
      if (invalid.contains(request.getNamespace().getUrn()))
        response.getNotifications().add(Notification.error("MODEL_DEPENDENCY_INCOMPATIBLE: " + request.getNamespace().getUrn()));
      return response;
    });
  }

  KimNamespaceImpl source(String urn) {
    var source = new KimNamespaceImpl(); source.setUrn(urn); source.setSourceCode(urn + " source");
    var model = new KimModelImpl(); model.setUrn(urn + ".model"); model.setNamespace(urn);
    model.getObservables().add(mock(KimObservable.class)); source.getStatements().add(model);
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
      index.reconcile(new ArrayList<>(sources.values()), catalog, scope, 8, 1);
      assertTrue(catalog.hasModel("good.model")); assertFalse(catalog.hasModel("pending.model"));
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
}
