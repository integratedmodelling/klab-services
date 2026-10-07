package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentModelKboxTest {
  @org.junit.jupiter.api.BeforeAll static void configureGeometry() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  @TempDir Path directory;
  final ResourcesService service = mock(ResourcesService.class);
  final ServiceScope scope = mock(ServiceScope.class);
  final Reasoner reasoner = mock(Reasoner.class);
  final Concept concept = mock(Concept.class);

  DocumentModelKboxTest() {
    when(service.serviceScope()).thenReturn(scope);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(concept.getUrn()).thenReturn("test:Height of test:Tree");
    when(reasoner.resolveConcept(concept.getUrn())).thenReturn(concept);
  }
  ModelReference descriptor() {
    var model = new ModelReference();
    model.setName("ns.o'hare");
    model.setNamespaceId("ns");
    model.setProjectId("project");
    model.setScope(KlabStatement.Scope.PUBLIC);
    model.setObservable(concept.getUrn());
    model.setObservableConcept(concept);
    model.setPrimaryObservable(true);
    model.setMediation(ModelReference.Mediation.DEREIFY_QUALITY);
    model.setPriority(8);
    model.setAbstractObservable(true);
    model.setVersion(new Version("1.2.3"));
    model.setObservationType("QUANTIFICATION");
    model.setMetadata(Map.of("quoted'key", "quoted'value"));
    model.setPermissions(ResourcePrivileges.create("alice"));
    model.setShape(org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:4326 POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"));
    return model;
  }
  @Test void codecPreservesFieldsLostByLegacySqlAndCopies() {
    var input = descriptor();
    var output = ModelReferenceCodec.decode(ModelReferenceCodec.encode(input.copy()), reasoner);
    assertEquals(ModelReferenceCodec.encode(input), ModelReferenceCodec.encode(output));
    assertTrue(output.isPrimaryObservable());
    assertEquals(8, output.getPriority());
    assertEquals(input.getMetadata(), output.getMetadata());
    assertNotSame(input.getPermissions(), output.getPermissions());
    assertSame(concept, output.getObservableConcept());
  }
  @Test void embeddedStoreReopensWithStableIdsAndCompleteDescriptors() {
    var file = directory.resolve("models.db");
    long id;
    long type;
    try (var box = new DocumentModelKbox(service, new NitriteKboxStore(file))) {
      id = box.store(descriptor(), scope);
      type = box.getConceptId(concept);
      assertEquals(1, box.count());
      assertNotNull(box.retrieveModel(id, scope));
    }
    try (var box = new DocumentModelKbox(service, new NitriteKboxStore(file))) {
      assertEquals(type, box.requireConceptId(concept, scope));
      assertEquals(ModelReferenceCodec.encode(descriptor()), ModelReferenceCodec.encode(box.retrieveModel(id, scope)));
      assertTrue(box.hasModel("ns.o'hare"));
      assertNull(box.retrieveModel(-1, scope));
      assertEquals(1, box.clearNamespace("ns", scope));
      assertEquals(0, box.count());
      assertFalse(box.hasModel("ns.o'hare"));
    }
  }
  @Test void namespaceWithoutMarkerStillDeletesModelsAndZeroTimestampNeedsRefresh() {
    try (var box = new DocumentModelKbox(service, new NitriteKboxStore(null))) {
      box.store(descriptor(), scope);
      var namespace = mock(KimNamespace.class);
      when(namespace.getUrn()).thenReturn("ns");
      assertEquals(1, box.removeIfOlder(namespace, scope));
      assertEquals(0, box.count());
      box.store(namespace, scope);
      assertEquals(0, box.removeIfOlder(namespace, scope));
      box.store(descriptor(), scope);
      box.remove("ns", scope);
      assertEquals(0, box.count());
    }
  }
  @Test void persistedConceptsUseReasonerAndRetryUnavailableDefinitions() {
    try (var box = new DocumentModelKbox(service, new NitriteKboxStore(null))) {
      long id = box.requireConceptId(concept, scope);
      var observable = mock(Observable.class);
      var head = mock(Concept.class);
      when(head.getUrn()).thenReturn("test:Height");
      when(reasoner.coreObservable(observable)).thenReturn(head);
      when(reasoner.coreObservable(concept)).thenReturn(head);
      when(reasoner.resolving(head)).thenReturn(List.of(head));
      when(reasoner.resolveConcept(concept.getUrn())).thenReturn(null, concept);
      assertTrue(box.getCompatibleTypeIds(observable, null).isEmpty());
      assertEquals(Set.of(id), box.getCompatibleTypeIds(observable, null));
      when(reasoner.semanticDistance(concept, observable, null)).thenReturn(-1);
      assertTrue(box.getCompatibleTypeIds(observable, null).isEmpty());
    }
  }
  @Test void indexedArrayMembershipFindsModelCandidates() {
    try (var store = new NitriteKboxStore(null)) {
      store.put("m", Map.of("kind", "model", "typeIds", List.of(10L, 20L)));
      assertEquals(1, store.find("typeIds", Set.of(20L)).size());
      assertTrue(store.find("typeIds", Set.of(30L)).isEmpty());
      assertTrue(store.find("typeIds", Set.of()).isEmpty());
    }
  }
  @Test void replacingModelIsCompleteAndFailedInferenceLeavesPreviousModel() {
    try (var box = spy(new DocumentModelKbox(service, new NitriteKboxStore(null)))) {
      var model = mock(org.integratedmodelling.klab.api.lang.kim.KimModel.class);
      when(model.getUrn()).thenReturn("ns.o'hare");
      when(model.getNamespace()).thenReturn("ns");
      var first = descriptor();
      var second = descriptor();
      second.setPrimaryObservable(false);
      doReturn(List.of(first, second)).when(box).inferModels(model, scope);
      box.store(model, scope);
      assertEquals(2, box.count());
      doReturn(List.of(second)).when(box).inferModels(model, scope);
      box.store(model, scope);
      assertEquals(1, box.count());
      assertFalse(box.retrieveModel(model.getUrn(), scope).isPrimaryObservable());
      doThrow(new IllegalStateException("reasoner failure")).when(box).inferModels(model, scope);
      assertThrows(IllegalStateException.class, () -> box.store(model, scope));
      assertEquals(1, box.count());
      var invalid = descriptor();
      invalid.setScope(null);
      doReturn(List.of(first, invalid)).when(box).inferModels(model, scope);
      assertThrows(NullPointerException.class, () -> box.store(model, scope));
      assertEquals(1, box.count());
    }
  }
  @Test void projectPrivateNeedsMatchingProjectEvenForScenario() {
    var model = descriptor();
    var observable = mock(Observable.class);
    model.setScope(KlabStatement.Scope.PROJECT_PRIVATE);
    assertFalse(ModelVisibility.accepts(model, observable, null));
    var project = mock(ResolutionConstraint.class);
    when(project.getType()).thenReturn(ResolutionConstraint.Type.ResolutionProject);
    when(project.payload(String.class)).thenReturn(List.of("project"));
    assertTrue(ModelVisibility.accepts(model, observable, List.of(project)));
    var scenario = mock(ResolutionConstraint.class);
    when(scenario.getType()).thenReturn(ResolutionConstraint.Type.Scenarios);
    when(scenario.payload(String.class)).thenReturn(List.of("ns"));
    assertFalse(ModelVisibility.accepts(model, observable, List.of(scenario)));
    model.setInScenario(true);
    assertFalse(ModelVisibility.accepts(model, observable, List.of(project)));
    assertTrue(ModelVisibility.accepts(model, observable, List.of(project, scenario)));
  }
  @Test void failedNamespaceReplacementDoesNotUnpublishPreviousGeneration() {
    try (var box = spy(new DocumentModelKbox(service, new NitriteKboxStore(null)))) {
      var namespace = mock(KimNamespace.class);
      when(namespace.getUrn()).thenReturn("ns");
      when(namespace.getLastUpdateTimestamp()).thenReturn(100L);
      var model = mock(org.integratedmodelling.klab.api.lang.kim.KimModel.class);
      when(model.getUrn()).thenReturn("ns.o'hare");
      when(model.getNamespace()).thenReturn("ns");
      doReturn(List.of(model)).when(namespace).getStatements();
      doReturn(List.of(descriptor())).when(box).inferModels(model, scope);
      box.replaceNamespace(namespace, scope);
      assertEquals(1, box.count());
      assertEquals(100, box.getNamespaceTimestamp(namespace));
      when(namespace.getLastUpdateTimestamp()).thenReturn(200L);
      doThrow(new IllegalStateException("inference failed")).when(box).inferModels(model, scope);
      assertThrows(IllegalStateException.class, () -> box.replaceNamespace(namespace, scope));
      assertEquals(100, box.getNamespaceTimestamp(namespace));
      assertEquals(1, box.count());
      assertNotNull(box.retrieveModel("ns.o'hare", scope));
      doReturn(List.of()).when(namespace).getStatements();
      box.replaceNamespace(namespace, scope);
      assertEquals(200, box.getNamespaceTimestamp(namespace));
      assertEquals(0, box.count());
    }
  }
  @Test void discoveryPreservesVariantsAndAppliesCoverageAndExplicitPermissionDenials() {
    try (var box = new DocumentModelKbox(service, new NitriteKboxStore(null))) {
      var request = mock(Observable.class);
      var head = mock(Concept.class);
      when(head.getUrn()).thenReturn("test:Height");
      when(reasoner.coreObservable(concept)).thenReturn(head);
      when(reasoner.coreObservable(request)).thenReturn(head);
      when(reasoner.resolving(head)).thenReturn(List.of(head));
      var first = descriptor();
      first.setPermissions(ResourcePrivileges.create("*,!blocked"));
      var second = first.copy();
      second.setPrimaryObservable(false);
      box.store(first, scope);
      box.store(second, scope);
      var scale = mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
      var context = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
      var user = mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
      when(context.getUser()).thenReturn(user);
      when(user.getUsername()).thenReturn("blocked");
      when(user.getGroups()).thenReturn(List.of());
      assertEquals(2, box.queryModels(request, scale, null, List.of(), context).size());
      assertTrue(box.query(request, scale, null, List.of(), context).isEmpty());
      when(user.getUsername()).thenReturn("allowed");
      assertEquals(2, box.query(request, scale, null, List.of(), context).size());
      var coverage = mock(org.integratedmodelling.klab.api.services.resolver.Coverage.class);
      when(service.info(first.getName(), org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.MODEL,
          org.integratedmodelling.klab.api.services.resolver.Coverage.class, context)).thenReturn(coverage);
      assertTrue(box.query(request, scale, null, List.of(), context).isEmpty());
      when(coverage.checkConstraints(scale)).thenReturn(true);
      assertEquals(2, box.query(request, scale, null, List.of(), context).size());
      var space = mock(org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space.class);
      when(scale.getSpace()).thenReturn(space);
      when(space.getRank()).thenReturn(5);
      var remote = org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
          "EPSG:4326 POLYGON ((10 10, 10 11, 11 11, 11 10, 10 10))");
      when(space.getGeometricShape()).thenReturn(remote);
      assertTrue(box.query(request, scale, null, List.of(), context).isEmpty());
      when(space.getGeometricShape()).thenReturn(first.getShape());
      assertEquals(2, box.query(request, scale, null, List.of(), context).size());
    }
  }
}
