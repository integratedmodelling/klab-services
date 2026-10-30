package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RescalingObservableTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void rescalingDeclarationSurvivesParsingAndServiceAdaptation() {
    var parser = new org.integratedmodelling.languages.WorldviewStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(org.eclipse.xtext.parser.IParser.class);
    var parsed = parser.parse(new java.io.StringReader(
        "ontology test in domain root version 1.0; abstract rescaling attribute Transformed has children Normalized;"));
    assertFalse(parsed.hasSyntaxErrors());
    var syntax = new org.integratedmodelling.languages.OntologySyntaxImpl(
        (org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement(),
        new org.integratedmodelling.languages.validation.BasicObservableValidationScope()) {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject p,
          org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject p,
          org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {
        fail(m);
      }
    };
    var adapted = LanguageAdapter.INSTANCE.adaptOntology(syntax, "test", List.of(), 1L);
    assertTrue(adapted.getStatements().getFirst().getType().contains(SemanticType.RESCALING));
    assertTrue(adapted.getStatements().getFirst().getChildren().getFirst().getType().contains(SemanticType.RESCALING));
  }

  @Test void inheritedRescalingTraitRemovesUnitsWithoutChangingTheNativeQuality() throws Exception {
    var scope = mock(Scope.class);
    var reasoner = mock(ReasonerService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(mock(ResourcesService.class));
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    var owl = new OWL(scope);
    owl.manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("test");
    ontology.define(List.of(
        Axiom.ClassAssertion("Elevation", EnumSet.of(SemanticType.QUALITY, SemanticType.LENGTH, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Normalized", EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE)),
        Axiom.ClassAssertion("Transformed", EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE, SemanticType.RESCALING))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("hasAttribute")));
    when(reasoner.owl()).thenReturn(owl);
    when(reasoner.serviceScope()).thenReturn(mock(org.integratedmodelling.klab.api.scope.ServiceScope.class));
    when(reasoner.resolveConcept(anyString())).thenAnswer(i -> owl.getConcept(i.getArgument(0)));
    var normalized = ontology.getConcept("Normalized");
    when(reasoner.parents(normalized)).thenReturn(List.of(ontology.getConcept("Transformed")));
    when(reasoner.traits(any())).thenAnswer(i -> ((Semantics)i.getArgument(0)).getUrn().contains("Normalized")
        ? List.of(normalized) : List.of());
    var head = syntax("Elevation", SemanticType.QUALITY, SemanticType.LENGTH, SemanticType.OBSERVABLE);
    head.getTraits().add(syntax("Normalized", SemanticType.ATTRIBUTE, SemanticType.PREDICATE));
    var transformed = SemanticsBuilder.create(head, reasoner, scope)
        .withUnit(new UnitImpl("m")).buildObservable();
    assertNull(transformed.getUnit());
    assertFalse(transformed.getUrn().contains(" in m"));
    assertEquals(true, transformed.getSemantics().getMetadata().get(Metadata.IM_IS_RESCALED));
    var units = new UnitServiceImpl();
    transformed.getSemantics().getMetadata().put("odo:unit", "m");
    assertNull(units.getDefaultUnitFor(transformed.asConcept()), "Native default units must not be restored");
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported = mapper.readValue(mapper.writeValueAsString(transformed),
        org.integratedmodelling.klab.api.knowledge.Observable.class);
    assertNull(transported.getUnit());
    assertNull(units.getDefaultUnitFor(transported.asConcept()));
    var nativeQuality = SemanticsBuilder.create(
        syntax("Elevation", SemanticType.QUALITY, SemanticType.LENGTH, SemanticType.OBSERVABLE), reasoner, scope)
        .withUnit(new UnitImpl("m")).buildObservable();
    assertNotNull(nativeQuality.getUnit());
    assertFalse(nativeQuality.asConcept().getMetadata().containsKey(Metadata.IM_IS_RESCALED));
  }

  private static KimConceptImpl syntax(String name, SemanticType... types) {
    var syntax = new KimConceptImpl(); syntax.setName("test:" + name); syntax.setUrn("test:" + name);
    syntax.setType(EnumSet.copyOf(List.of(types))); return syntax;
  }
}
