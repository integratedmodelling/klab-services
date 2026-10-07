package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class LegacyModelKboxTest {
  @Test void sqlRoundTripPreservesPermissionsAndQuotedValuesAndRemovesWithoutNamespaceMarker() {
    var service = mock(ResourcesService.class);
    var scope = mock(ServiceScope.class);
    var reasoner = mock(Reasoner.class);
    var concept = mock(Concept.class);
    var observable = mock(Observable.class);
    when(service.serviceName()).thenReturn("kbox-test-" + UUID.randomUUID());
    when(service.serviceScope()).thenReturn(scope);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(concept.getUrn()).thenReturn("test:Height");
    when(reasoner.coreObservable(concept)).thenReturn(concept);
    when(reasoner.resolveConcept("test:Height")).thenReturn(concept);
    when(reasoner.resolveObservable("test:Height")).thenReturn(observable);
    when(observable.asConcept()).thenReturn(concept);
    var model = new ModelReference();
    model.setName("test.o'hare");
    model.setNamespaceId("test'namespace");
    model.setScope(KlabStatement.Scope.PUBLIC);
    model.setObservableConcept(concept);
    model.setObservable(concept.getUrn());
    model.setObservationType("QUANTIFICATION");
    model.setMetadata(Map.of("owner's", "value's"));
    model.setPermissions(ResourcePrivileges.create("alice"));
    model.setPrimaryObservable(true);
    model.setMediation(ModelReference.Mediation.DEREIFY_QUALITY);
    try (var box = ModelKbox.create(service)) {
      long id = box.store(model, scope);
      assertTrue(box.hasModel(model.getName()));
      assertEquals(ModelReferenceCodec.encode(model), ModelReferenceCodec.encode(box.retrieveModel(id, scope)));
      assertEquals(ModelReferenceCodec.encode(model), ModelReferenceCodec.encode(box.retrieveModel(model.getName(), scope)));
      assertNull(box.retrieveModel(-1, scope));
      box.remove(model.getNamespaceId(), scope);
      assertEquals(0, box.count());
      var namespace = mock(org.integratedmodelling.klab.api.lang.kim.KimNamespace.class);
      when(namespace.getUrn()).thenReturn(model.getNamespaceId());
      box.store(model, scope);
      assertEquals(1, box.removeIfOlder(namespace, scope));
      assertEquals(0, box.count());
      box.store(namespace, scope);
      assertEquals(0, box.removeIfOlder(namespace, scope));
    }
  }
}
