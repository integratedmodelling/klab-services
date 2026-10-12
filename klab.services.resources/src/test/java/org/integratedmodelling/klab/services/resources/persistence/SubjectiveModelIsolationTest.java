package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;

class SubjectiveModelIsolationTest {
  @Test void expandedCandidateStatusControlsCatalogNamespacePrivacy() {
    for (boolean subjective : new boolean[] {false, true}) {
      var source = new KimConceptImpl();
      source.setName("test:Source");
      source.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE, SemanticType.ANY));
      source.setDeclaredSubjective(!subjective);
      var candidate = new KimConceptImpl();
      candidate.setName("test:Candidate");
      candidate.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE));
      candidate.setDeclaredSubjective(subjective);
      var original = new KimObservableImpl(); original.setSemantics(source);
      original.setNamespace("owner");
      var expanded = DependencyExpansion.expand(original, name -> source, request ->
          new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, List.of(candidate))).getFirst();
      var resolved = mock(Concept.class);
      when(resolved.is(SemanticType.SUBJECTIVE)).thenReturn(ExpressionStatus.isSubjective(expanded));
      var model = new ModelReference();
      model.setNamespaceId(expanded.getNamespace());
      model.setScope(KlabStatement.Scope.PUBLIC);
      model.setObservableConcept(resolved);
      assertTrue(ModelVisibility.accepts(model, mock(Observable.class), List.of(
          constraint(ResolutionConstraint.Type.ResolutionNamespace, "owner"))));
      assertEquals(!subjective, ModelVisibility.accepts(model, mock(Observable.class), List.of(
          constraint(ResolutionConstraint.Type.ResolutionNamespace, "outside"),
          constraint(ResolutionConstraint.Type.Scenarios, "owner"))));
      assertEquals(!subjective, source.getDeclaredSubjective());
    }
  }

  @Test void directCatalogQueriesHonorProviderIndexReadiness() {
    var provider = mock(org.integratedmodelling.klab.services.resources.ResourcesProvider.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(provider.serviceName()).thenReturn("readiness-" + UUID.randomUUID());
    when(provider.serviceScope()).thenReturn(mock(org.integratedmodelling.klab.api.scope.ServiceScope.class));
    try (var documents = new DocumentModelKbox(provider, new NitriteKboxStore(null));
         var legacy = ModelKbox.create(provider)) {
      for (ModelCatalog catalog : List.of(documents, legacy)) {
        when(provider.checkSemanticServices(scope)).thenReturn(false);
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabStorageException.class,
            () -> catalog.queryModels(mock(Observable.class), null, null, List.of(), scope));
        when(provider.checkSemanticServices(scope)).thenReturn(true);
        assertTrue(catalog.queryModels(mock(Observable.class), null, null, List.of(), scope).isEmpty());
      }
    }
  }
  @Test void everyOutputAndDependencyPrivatizesEveryDescriptorAndSurvivesSerialization() {
    for (int subjectivePosition : new int[] {-1, 0, 1, 2}) {
      var service = mock(ResourcesService.class);
      var scope = mock(UserScope.class);
      var reasoner = mock(Reasoner.class);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      var namespace = new KimNamespaceImpl();
      namespace.setUrn("owner");
      when(service.retrieve("owner", KimNamespace.class, scope)).thenReturn(namespace);
      var model = new KimModelImpl();
      model.setUrn("owner.model");
      model.setNamespace("owner");
      model.setScope(KlabStatement.Scope.PUBLIC);
      for (int i = 0; i < 3; i++) {
        var syntax = new KimObservableImpl();
        var semantics = new KimConceptImpl();
        semantics.setName("test:Value" + i);
        semantics.setType(EnumSet.of(SemanticType.QUALITY));
        syntax.setSemantics(semantics);
        syntax.setUrn("test:Value" + i);
        if (i == 1) syntax.setFormalName("secondary");
        var resolved = mock(Observable.class);
        var concept = mock(Concept.class);
        when(resolved.getUrn()).thenReturn(syntax.getUrn());
        when(resolved.getSemantics()).thenReturn(concept);
        when(concept.getUrn()).thenReturn(syntax.getUrn());
        when(resolved.getContextualization()).thenReturn(Contextualization.QUANTIFICATION);
        when(resolved.is(SemanticType.SUBJECTIVE)).thenReturn(i == subjectivePosition);
        when(reasoner.resolveObservable(syntax.getUrn())).thenReturn(resolved);
        if (i < 2) model.getObservables().add(syntax);
        else model.getDependencies().add(syntax);
      }
      var descriptors = new ModelDescriptorFactory(service).inferModels(model, scope);
      assertEquals(3, descriptors.size());
      for (var descriptor : descriptors) {
        var restored = ModelReferenceCodec.decode(ModelReferenceCodec.encode(descriptor.copy()), null);
        assertEquals(subjectivePosition < 0 ? KlabStatement.Scope.PUBLIC : KlabStatement.Scope.PRIVATE, restored.getScope());
        assertEquals("owner", restored.getNamespaceId());
        assertTrue(ModelVisibility.accepts(restored, mock(Observable.class), List.of(constraint(ResolutionConstraint.Type.ResolutionNamespace, "owner"))));
        assertEquals(subjectivePosition < 0, ModelVisibility.accepts(restored, mock(Observable.class), List.of(
            constraint(ResolutionConstraint.Type.ResolutionNamespace, "outside"),
            constraint(ResolutionConstraint.Type.Scenarios, "owner"))));
      }
      assertEquals(KlabStatement.Scope.PUBLIC, model.getScope());
    }
  }

  @Test void privateAndSubjectiveModelsCannotEscapeViaProjectOrSelectedScenario() {
    for (boolean subjective : new boolean[] {false, true}) {
      var model = new ModelReference();
      model.setNamespaceId("owner");
      model.setProjectId("project");
      model.setScope(subjective ? KlabStatement.Scope.PUBLIC : KlabStatement.Scope.PRIVATE);
      var concept = mock(Concept.class);
      when(concept.is(SemanticType.SUBJECTIVE)).thenReturn(subjective);
      model.setObservableConcept(concept);
      for (boolean scenario : new boolean[] {false, true}) {
        model.setInScenario(scenario);
        for (String namespace : List.of("owner", "outside")) {
          assertEquals(namespace.equals("owner"), ModelVisibility.accepts(model, mock(Observable.class), List.of(
              constraint(ResolutionConstraint.Type.ResolutionNamespace, namespace),
              constraint(ResolutionConstraint.Type.ResolutionProject, "project"),
              constraint(ResolutionConstraint.Type.Scenarios, "owner"))));
        }
        assertFalse(ModelVisibility.accepts(model, mock(Observable.class), null));
      }
    }
  }

  private ResolutionConstraint constraint(ResolutionConstraint.Type type, String value) {
    var constraint = mock(ResolutionConstraint.class);
    when(constraint.getType()).thenReturn(type);
    when(constraint.payload(String.class)).thenReturn(List.of(value));
    return constraint;
  }
}
