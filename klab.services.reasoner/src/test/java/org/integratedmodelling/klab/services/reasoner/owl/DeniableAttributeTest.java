package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class DeniableAttributeTest {
  private OWL owl() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),
        new Class<?>[] {Scope.class}, (proxy, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("klab").define(List.of(
        Axiom.AnnotationPropertyAssertion("conceptDefinition"),
        Axiom.AnnotationPropertyAssertion("referenceName"),
        Axiom.AnnotationPropertyAssertion("displayLabel"),
        Axiom.AnnotationPropertyAssertion("baseDeclaration"),
        Axiom.AnnotationPropertyAssertion("localAlias")));
    owl.requireOntology("odo").define(List.of(Axiom.AnnotationPropertyAssertion("isDeniable")));
    owl.requireOntology("physical").define(List.of(Axiom.ClassAssertion("Feasible",
        EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE, SemanticType.DENIABLE))));
    return owl;
  }

  private KimConceptStatementImpl statement(String alias) {
    var statement = new KimConceptStatementImpl();
    statement.setUrn("Feasible"); statement.setNamespace("physical");
    statement.setType(EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE));
    statement.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause(
        "deniabilityClause", "deniable as " + alias, 0, 20));
    return statement;
  }

  @Test void complementHasStableIdentityProvenanceAndDoubleNegation() throws Exception {
    var owl = owl(); var ontology = owl.getOntology("physical");
    var feasible = ontology.getConcept("Feasible");
    feasible.getMetadata().put(Metadata.DC_LABEL, "An editable label");
    var statement = statement("Unfeasible");
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported = mapper.readValue(mapper.writeValueAsBytes(statement), KimConceptStatement.class);
    WorldviewDeclarationSupport.compileDeniability(owl, ontology, feasible, transported);
    var negated = ontology.getConcept("NotFeasible");
    assertNotNull(negated);
    assertSame(negated, ontology.getConcept("Unfeasible"));
    assertEquals(feasible.getUrn(), negated.getMetadata().get(NS.IS_NEGATION_OF));
    assertSame(feasible, owl.makeNegation(negated));
    var f = owl.manager.getOWLDataFactory();
    var equivalence = f.getOWLEquivalentClassesAxiom(owl.getOWLClass(negated),
        f.getOWLObjectComplementOf(owl.getOWLClass(feasible)));
    assertTrue(ontology.getOWLOntology().containsAxiom(equivalence));
    // A DL reasoner must establish disjointness, not merely recognize a source annotation.
    var reasoner = new org.semanticweb.HermiT.ReasonerFactory().createReasoner(ontology.getOWLOntology());
    try {
      assertTrue(reasoner.isSatisfiable(owl.getOWLClass(negated)));
      assertFalse(reasoner.isSatisfiable(f.getOWLObjectIntersectionOf(
          owl.getOWLClass(feasible), owl.getOWLClass(negated))));
    } finally { reasoner.dispose(); }
    long count = ontology.getOWLOntology().getAxiomCount();
    WorldviewDeclarationSupport.compileDeniability(owl, ontology, feasible, transported);
    assertEquals(count, ontology.getOWLOntology().getAxiomCount());
    var bytes = new java.io.ByteArrayOutputStream();
    owl.manager.saveOntology(ontology.getOWLOntology(), bytes);
    var loaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
        new java.io.ByteArrayInputStream(bytes.toByteArray()));
    assertTrue(loaded.containsAxiom(equivalence));
    assertEquals(feasible.getUrn(), new OWLMetadata(owl.getOWLClass(negated), loaded).get(NS.IS_NEGATION_OF));
  }

  @Test void matchingNameAndForeignOntologyUseCanonicalConcept() {
    var owl = owl(); var physical = owl.getOntology("physical");
    var feasible = physical.getConcept("Feasible");
    WorldviewDeclarationSupport.compileDeniability(owl, physical, feasible, statement("NotFeasible"));
    var canonical = physical.getConcept("NotFeasible");
    var other = owl.requireOntology("other");
    WorldviewDeclarationSupport.compileDeniability(owl, other, feasible, statement("NotFeasible"));
    assertSame(canonical, other.getConcept("NotFeasible"));
    WorldviewDeclarationSupport.compileDeniability(owl, other, feasible, statement("Unfeasible"));
    assertSame(canonical, other.getConcept("Unfeasible"));
  }

  @Test void bareDeniabilityAndNotOperatorShareTheSameConcept() {
    var owl = owl(); var ontology = owl.getOntology("physical");
    var statement = statement("Unfeasible");
    statement.getDeclarationClauses().clear();
    statement.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause(
        "deniabilityClause", "deniable", 0, 8));
    assertNull(statement.deniedAlias());
    assertTrue(statement.deniable());
    var owner = ontology.getConcept("Feasible");
    WorldviewDeclarationSupport.compileDeniability(owl, ontology, owner, statement);
    assertSame(ontology.getConcept("NotFeasible"), owl.makeNegation(owner));
    assertTrue(owner.is(SemanticType.DENIABLE));
    assertNull(ontology.getConcept("Unfeasible"));
  }

  @Test void sourceDeniabilitySurvivesParsingAndServiceAdaptation() {
    var parser = new org.integratedmodelling.languages.WorldviewStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(org.eclipse.xtext.parser.IParser.class);
    for (boolean alias : List.of(false, true)) {
      var parsed = parser.parse(new java.io.StringReader("ontology physical in domain root version 1.0; "
          + "attribute Feasible deniable" + (alias ? " as Unfeasible" : "") + "; "
          + "attribute Impossible is physical:" + (alias ? "Unfeasible" : "NotFeasible") + ";"));
      assertFalse(parsed.hasSyntaxErrors());
      var errors = new ArrayList<String>();
      var syntax = new org.integratedmodelling.languages.OntologySyntaxImpl(
          (org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement(),
          new org.integratedmodelling.languages.validation.BasicObservableValidationScope()) {
        @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject p,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {}
        @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject p,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {
          errors.add(m);
        }
      };
      assertTrue(errors.isEmpty(), errors.toString());
      var bean = org.integratedmodelling.klab.runtime.language.LanguageAdapter.INSTANCE
          .adaptOntology(syntax, "test", List.of(), 1L).getStatements().getFirst();
      assertTrue(bean.getType().contains(SemanticType.DENIABLE));
      assertEquals(alias ? "Unfeasible" : null, bean.deniedAlias());
      var transported = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
          .convertValue(bean, KimConceptStatement.class);
      assertEquals(alias ? "Unfeasible" : null, transported.getDeniedAlias());
      var document = new KimOntologyImpl(); document.setUrn("physical");
      document.getStatements().add(transported);
      var restoredScope = new org.integratedmodelling.klab.runtime.language.WorldviewValidationScope();
      restoredScope.addNamespace(document);
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.ATTRIBUTE,
          restoredScope.getConceptDescriptor("physical:NotFeasible").mainType());
      if (alias) assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.ATTRIBUTE,
          restoredScope.getConceptDescriptor("physical:Unfeasible").mainType());
      var owl = owl(); var ontology = owl.getOntology("physical");
      WorldviewDeclarationSupport.compileDeniability(owl, ontology, ontology.getConcept("Feasible"), bean);
      assertSame(ontology.getConcept("NotFeasible"), owl.makeNegation(ontology.getConcept("Feasible")));
      if (alias) assertSame(ontology.getConcept("NotFeasible"), ontology.getConcept("Unfeasible"));
    }
  }

  @Test void occupiedNamesAreRejectedInsteadOfReusingUnrelatedClasses() {
    var owl = owl(); var ontology = owl.getOntology("physical");
    ontology.define(List.of(Axiom.ClassAssertion("NotFeasible", EnumSet.of(SemanticType.ATTRIBUTE))));
    assertThrows(RuntimeException.class, () -> owl.makeNegation(ontology.getConcept("Feasible")));
  }

  @Test void registryOrdersReferencesToGeneratedAliasesAndRejectsCollisions() {
    var source = new KimOntologyImpl(); source.setUrn("physical");
    var owner = statement("Unfeasible");
    var child = new KimConceptStatementImpl(); child.setUrn("Impossible"); child.setNamespace("physical");
    child.setType(EnumSet.of(SemanticType.ATTRIBUTE));
    var parent = new KimConceptImpl(); parent.setName("physical:Unfeasible"); child.setDeclaredParent(parent);
    source.getStatements().addAll(List.of(child, owner));
    var registry = new WorldviewDeclarationRegistry(List.of(source));
    assertTrue(registry.errors().isEmpty(), registry.errors().toString());
    assertSame(owner, registry.ordered().getFirst().statement());
    child.setUrn("NotFeasible");
    assertFalse(new WorldviewDeclarationRegistry(List.of(source)).errors().isEmpty());
  }
}
