package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Production persistence and transport boundaries; observer distance itself has reasoner tests. */
class ObserverModelDiscoveryTest {
  @TempDir Path directory;
  @org.junit.jupiter.api.BeforeAll static void configureGeometry() {
    DocumentModelKboxTest.configureGeometry();
  }

  static Observable observable(String observer) {
    var base = new ConceptImpl();
    base.setUrn("test:Height of test:Tree");
    base.setType(EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE));
    var value = new ObservableImpl();
    value.setSemantics(base);
    value.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.QUANTIFICATION);
    value.setUrn(base.getUrn());
    if (observer != null) {
      var agent = new ConceptImpl(); agent.setUrn("test:" + observer);
      agent.setType(EnumSet.of(SemanticType.AGENT, SemanticType.OBSERVABLE));
      value.setObserverSemantics(agent);
      value.setUrn(base.getUrn() + " observed as " + agent.getUrn());
    }
    return value;
  }

  @Test void interfaceTransportRetainsObserverSeparatelyFromIndexedConcept() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    org.integratedmodelling.common.data.jackson.JacksonConfiguration.configureObjectMapperForKlabTypes(mapper);
    Observable source = observable("X");
    Observable recovered = mapper.readValue(mapper.writeValueAsString(source), Observable.class);
    assertEquals(source.getUrn(), recovered.getUrn());
    assertEquals("test:X", recovered.getObserverSemantics().getUrn());
    assertEquals("test:Height of test:Tree", recovered.getSemantics().getUrn());
  }

  @Test void reopenedCatalogPassesFullWrappersAndValidatedRevisionToDistance() {
    var fixture = new DocumentModelKboxTest();
    var base = observable(null); var x = observable("X"); var y = observable("Y");
    var head = fixture.concept;
    when(fixture.reasoner.coreObservable(any())).thenReturn(head);
    when(fixture.reasoner.resolving(head)).thenReturn(List.of(head));
    var candidates = Map.of(base.getUrn(), base, x.getUrn(), x, y.getUrn(), y);
    candidates.forEach((urn, wrapper) -> when(fixture.reasoner.resolveObservable(urn)).thenReturn(wrapper));
    // A deliberately discriminating reasoner boundary: passing the shared base Concept loses X/Y.
    doAnswer(call -> {
      assertTrue(call.getArgument(0) instanceof Observable, "Catalog must not match an indexed Concept");
      Observable candidate = call.getArgument(0), query = call.getArgument(1);
      assertEquals(7L, (long) call.getArgument(3));
      return query.getObserverSemantics() == null || Objects.equals(
          query.getObserverSemantics(), candidate.getObserverSemantics()) ? 0 : -1;
    }).when(fixture.reasoner).semanticDistance(any(), any(), any(), anyLong());
    var file = directory.resolve("observer-models.db");
    try (var box = new DocumentModelKbox(fixture.service, new NitriteKboxStore(file))) {
      for (var wrapper : candidates.values()) {
        var descriptor = fixture.descriptor(); descriptor.setObservable(wrapper.getUrn());
        // All variants intentionally share this coarse semantic index.
        box.store(descriptor, fixture.scope);
      }
    }
    var scale = mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
    try (var box = new DocumentModelKbox(fixture.service, new NitriteKboxStore(file))) {
      assertEquals(List.of(x.getUrn()), box.queryModels(x, scale, null, List.of(), null)
          .stream().map(ModelReference::getObservable).toList());
      assertEquals(candidates.keySet(), new HashSet<>(box.queryModels(base, scale, null, List.of(), null)
          .stream().map(ModelReference::getObservable).toList()));
      clearInvocations(fixture.reasoner);
      when(fixture.reasoner.resolveObservable(x.getUrn())).thenReturn(null);
      assertTrue(box.queryModels(x, scale, null, List.of(), null).isEmpty());
      verify(fixture.reasoner, never()).semanticDistance(same(x), any(), any(), anyLong());
      // Source rejection precedes full-wrapper reconstruction as well as distance.
      clearInvocations(fixture.reasoner);
      doReturn(null).when(fixture.reasoner).validateDocument(any(), any());
      assertTrue(box.queryModels(base, scale, null, List.of(), null).isEmpty());
      verify(fixture.reasoner, never()).resolveObservable(anyString());
      verify(fixture.reasoner, never()).semanticDistance(any(), any(), any(), anyLong());
    }
  }
  @Test void namedSecondaryDescriptorRetainsFullObservedAsWrapper() {
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    ModelAdmissionFixtures.valid(reasoner);
    var main = observable(null); var secondary = observable("X");
    when(reasoner.resolveObservable(main.getUrn())).thenReturn(main);
    when(reasoner.resolveObservable(secondary.getUrn())).thenReturn(secondary);
    var namespace = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    namespace.setUrn("ns");
    when(resources.retrieve("ns", org.integratedmodelling.klab.api.lang.kim.KimNamespace.class, scope))
        .thenReturn(namespace);
    var model = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();
    model.setNamespace("ns"); model.setUrn("ns.model");
    for (var wrapper : List.of(main, secondary)) {
      var syntax = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
      var head = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
      head.setType(EnumSet.of(SemanticType.QUALITY));
      syntax.setSemantics(head); syntax.setUrn(wrapper.getUrn());
      if (wrapper == secondary) syntax.setFormalName("height_x");
      model.getObservables().add(syntax);
    }
    var descriptors = new ModelDescriptorFactory(resources).inferModels(model, scope);
    var derived = descriptors.stream().filter(d -> "height_x".equals(d.getDereifyingAttribute())).findFirst().orElseThrow();
    assertEquals(secondary.getUrn(), derived.getObservable());
    assertEquals(main.getSemantics().getUrn(), derived.getObservableConcept().getUrn());
  }

  @Test void legacySqlPayloadAndQueryRetainFullWrapperDespiteSharedTypeIndex() {
    var fixture = new DocumentModelKboxTest();
    when(fixture.service.serviceName()).thenReturn("observer-sql-" + UUID.randomUUID());
    var base = observable(null); var x = observable("X");
    var head = fixture.concept;
    when(fixture.reasoner.coreObservable(any())).thenReturn(head);
    when(fixture.reasoner.resolving(head)).thenReturn(List.of(head));
    when(fixture.reasoner.resolveObservable(base.getUrn())).thenReturn(base);
    when(fixture.reasoner.resolveObservable(x.getUrn())).thenReturn(x);
    var context = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(context.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(fixture.reasoner);
    var scale = mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
    var descriptor = fixture.descriptor(); descriptor.setObservable(x.getUrn());
    doAnswer(call -> {
      assertSame(x, call.getArgument(0));
      assertEquals(7L, (long) call.getArgument(3));
      return 0;
    }).when(fixture.reasoner).semanticDistance(any(), same(x), any(), anyLong());
    try (var box = ModelKbox.create(fixture.service)) {
      long id = box.store(descriptor, fixture.scope);
      var recovered = box.retrieveModel(id, fixture.scope);
      assertEquals(x.getUrn(), recovered.getObservable());
      assertEquals(base.getSemantics().getUrn(), recovered.getObservableConcept().getUrn());
      assertEquals(List.of(x.getUrn()), box.queryModels(x, scale, null, List.of(), context)
          .stream().map(ModelReference::getObservable).toList());
      verify(fixture.reasoner).semanticDistance(same(x), same(x), isNull(), eq(7L));
    }
  }

  @Test void instantiatorSecondaryInherencyBuildRetainsObserverWrapper() {
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    ModelAdmissionFixtures.valid(reasoner);
    var main = (ObservableImpl) observable(null);
    var tree = new ConceptImpl(); tree.setUrn("test:Tree");
    tree.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    main.setSemantics(tree); main.setUrn("each test:Tree");
    main.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION);
    var secondary = spy((ObservableImpl) observable("X"));
    var specialized = new ObservableImpl(secondary);
    var specializedConcept = new ConceptImpl((ConceptImpl) secondary.getSemantics());
    specializedConcept.setUrn("test:SpecializedHeight of test:Tree");
    specialized.setSemantics(specializedConcept);
    specialized.setUrn(specializedConcept.getUrn() + " observed as test:X");
    var builder = mock(Observable.Builder.class);
    doReturn(builder).when(secondary).builder(scope);
    when(builder.of(tree)).thenReturn(builder);
    when(builder.buildObservable()).thenReturn(specialized);
    when(reasoner.resolveObservable(main.getUrn())).thenReturn(main);
    when(reasoner.resolveObservable(secondary.getUrn())).thenReturn(secondary);
    var namespace = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    namespace.setUrn("ns");
    when(resources.retrieve("ns", org.integratedmodelling.klab.api.lang.kim.KimNamespace.class, scope))
        .thenReturn(namespace);
    var model = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();
    model.setNamespace("ns"); model.setUrn("ns.instantiate");
    for (var wrapper : List.of(main, secondary)) {
      var syntax = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
      var head = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
      head.setType(EnumSet.of(wrapper == main ? SemanticType.SUBJECT : SemanticType.QUALITY));
      head.setCollective(wrapper == main);
      syntax.setSemantics(head); syntax.setUrn(wrapper.getUrn());
      if (wrapper == secondary) syntax.setFormalName("height_x");
      model.getObservables().add(syntax);
    }
    var descriptors = new ModelDescriptorFactory(resources).inferModels(model, scope);
    var derived = descriptors.stream().filter(d -> "height_x".equals(d.getDereifyingAttribute())).findFirst().orElseThrow();
    assertEquals(specialized.getUrn(), derived.getObservable());
    assertSame(specializedConcept, derived.getObservableConcept());
    assertEquals("test:X", specialized.getObserverSemantics().getUrn());
    verify(builder).buildObservable();
    verify(builder, never()).buildConcept();
  }

}
