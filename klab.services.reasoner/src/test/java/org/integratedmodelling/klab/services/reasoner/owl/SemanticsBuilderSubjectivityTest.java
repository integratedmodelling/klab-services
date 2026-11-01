package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;

class SemanticsBuilderSubjectivityTest {
  final Scope scope = mock(Scope.class);
  final ReasonerService reasoner = mock(ReasonerService.class);
  final ResourcesService resources = mock(ResourcesService.class);
  final OWL owl = new OWL(scope);
  final ConceptImpl canonical;
  final KimConceptImpl syntax = new KimConceptImpl();
  SemanticsBuilderSubjectivityTest() {
    owl.manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("test");
    var types = EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
    ontology.define(List.of(org.integratedmodelling.common.lang.Axiom.ClassAssertion("Entity", types)));
    canonical = (ConceptImpl) ontology.getConcept("Entity");
    syntax.setName("test:Entity"); syntax.setType(types);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(reasoner.owl()).thenReturn(owl);
    when(reasoner.resolveConcept(anyString())).thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    when(resources.declareConcept("test:Entity")).thenReturn(syntax);
  }
  @Test void projectedSubjectivitySurvivesObservableBuilderWithoutTaintingCanonical() {
    var projected = new ConceptImpl(canonical);
    projected.getType().add(SemanticType.SUBJECTIVE);
    var input = ObservableImpl.promote(projected, null);
    var rebuilt = SemanticsBuilder.create(input, reasoner, scope).buildObservable();
    assertTrue(rebuilt.is(SemanticType.SUBJECTIVE));
    assertFalse(canonical.is(SemanticType.SUBJECTIVE));
    assertFalse(syntax.is(SemanticType.SUBJECTIVE));
  }
  @Test void builderRetainsDefaultValueAndItsSubjectivity() {
    var input = ObservableImpl.promote(canonical, null);
    var value = new ConceptImpl(canonical);
    value.getType().add(SemanticType.SUBJECTIVE);
    input.setDefaultValue(value);
    var rebuilt = SemanticsBuilder.create(input, reasoner, scope).buildObservable();
    assertNotNull(rebuilt.getDefaultValue());
    assertTrue(rebuilt.is(SemanticType.SUBJECTIVE));
    assertFalse(canonical.is(SemanticType.SUBJECTIVE));
    var objective = SemanticsBuilder.create(ObservableImpl.promote(canonical, null), reasoner, scope).buildObservable();
    assertFalse(objective.is(SemanticType.SUBJECTIVE));
  }

  @Test void descriptionValuesSurviveTransportCopyAndBuilderWithoutTaintingCanonical() throws Exception {
    for (boolean subjective : List.of(false, true)) {
      var input = ObservableImpl.promote(canonical, null);
      var value = new ConceptImpl(canonical);
      if (subjective) value.getType().add(SemanticType.SUBJECTIVE);
      input.setValue(List.of(value));
      var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      var restored = mapper.readValue(mapper.writerFor(org.integratedmodelling.klab.api.knowledge.Observable.class)
          .writeValueAsString(new ObservableImpl(input)), org.integratedmodelling.klab.api.knowledge.Observable.class);
      var rebuilt = SemanticsBuilder.create(restored, reasoner, scope).buildObservable();
      assertNotNull(rebuilt.getValue());
      assertEquals(subjective, rebuilt.is(SemanticType.SUBJECTIVE));
      assertFalse(canonical.is(SemanticType.SUBJECTIVE));
    }
  }

  @Test void removingTheOnlySubjectiveComponentRecomputesBuilderStatus() {
    var filler = new KimConceptImpl();
    filler.setName("test:View");
    filler.setType(EnumSet.of(SemanticType.SUBJECTIVE, SemanticType.SUBJECT, SemanticType.OBSERVABLE, SemanticType.COUNTABLE));
    var view = new ConceptImpl(canonical);
    view.getType().add(SemanticType.SUBJECTIVE);
    when(reasoner.resolveConcept("test:View")).thenReturn(view);
    syntax.setDeclaredSubjective(false);
    syntax.setInherent(filler);
    syntax.resetDefinition();
    var projected = new ConceptImpl(canonical);
    projected.setUrn(syntax.getUrn());
    projected.getType().add(SemanticType.SUBJECTIVE);
    when(resources.declareConcept(projected.getUrn())).thenReturn(syntax);
    var result = SemanticsBuilder.create(ObservableImpl.promote(projected, null), reasoner, scope)
        .without(SemanticRole.INHERENT).buildObservable();
    assertFalse(result.is(SemanticType.SUBJECTIVE));
    assertTrue(syntax.is(SemanticType.SUBJECTIVE));
    assertNotNull(syntax.getInherent());
    assertFalse(canonical.is(SemanticType.SUBJECTIVE));
  }
}
