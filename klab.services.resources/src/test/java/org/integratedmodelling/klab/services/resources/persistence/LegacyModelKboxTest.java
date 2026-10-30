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
  @Test void sqlPrivateScopeCannotBeBypassedBySelectedScenariosOrSameProject() throws Exception {
    var service = mock(ResourcesService.class);
    when(service.serviceName()).thenReturn("subjective-sql-" + UUID.randomUUID());
    when(service.serviceScope()).thenReturn(mock(ServiceScope.class));
    var query = ModelKbox.class.getDeclaredMethod("scopeQuery", List.class, Observable.class);
    query.setAccessible(true);
    try (var box = ModelKbox.create(service);
        var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:subjective-" + UUID.randomUUID());
        var sql = connection.createStatement()) {
      sql.execute("CREATE TABLE model(scope VARCHAR, inscenario BOOLEAN, namespaceid VARCHAR, projectid VARCHAR)");
      for (boolean scenario : List.of(false, true)) {
        sql.execute("DELETE FROM model");
        sql.execute("INSERT INTO model VALUES ('PRIVATE', " + scenario + ", 'owner', 'project')");
        for (String namespace : List.of("owner", "outside", "")) {
          var constraints = new ArrayList<org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint>();
          for (var entry : Map.of(
              org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionNamespace, namespace,
              org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionProject, "project",
              org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.Scenarios, "owner").entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            var constraint = mock(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.class);
            when(constraint.getType()).thenReturn(entry.getKey());
            when(constraint.payload(String.class)).thenReturn(List.of(entry.getValue()));
            constraints.add(constraint);
          }
          String filter = (String) query.invoke(box, constraints, mock(Observable.class));
          try (var rows = sql.executeQuery("SELECT COUNT(*) FROM model WHERE " + filter)) {
            assertTrue(rows.next());
            assertEquals(namespace.equals("owner") ? 1 : 0, rows.getInt(1));
          }
        }
      }
    }
  }

  @Test void sqlRoundTripPreservesPermissionsAndQuotedValuesAndRemovesWithoutNamespaceMarker() {
    var service = mock(ResourcesService.class);
    var scope = mock(ServiceScope.class);
    var reasoner = mock(Reasoner.class);
    var concept = mock(Concept.class);
    var observable = mock(Observable.class);
    when(service.serviceName()).thenReturn("kbox-test-" + UUID.randomUUID());
    when(service.serviceScope()).thenReturn(scope);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    ModelAdmissionFixtures.valid(reasoner);
    var source = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    source.setUrn("test'namespace");
    var sourceModel = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();
    sourceModel.setUrn("test.o'hare"); source.getStatements().add(sourceModel);
    when(service.retrieve(eq("test'namespace"), eq(org.integratedmodelling.klab.api.lang.kim.KimNamespace.class), isNull()))
        .thenReturn(source);
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
      box.store(model, scope);
      box.withholdNamespace(model.getNamespaceId(), scope);
      assertEquals(0, box.count());
      assertFalse(box.hasModel(model.getName()));
      assertSame(source, service.retrieve(model.getNamespaceId(),
          org.integratedmodelling.klab.api.lang.kim.KimNamespace.class, null));
      box.store(model, scope);
      assertTrue(box.hasModel(model.getName()));
    }
  }
}
