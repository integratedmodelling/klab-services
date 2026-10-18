package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class DomainInterpretationSupportTest {
  private OWL fixture() {
    var scope = (org.integratedmodelling.klab.api.scope.Scope) java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[]{org.integratedmodelling.klab.api.scope.Scope.class},
        (self, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("odo");
    var axioms = new ArrayList<Axiom>();
    for (var name : List.of("KnowledgeDomain", "PhysicalDomain", "Knowledge", "Earth", "Ocean"))
      axioms.add(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE)));
    axioms.add(Axiom.ObjectPropertyAssertion("interpretsPhysicalDomain"));
    axioms.add(Axiom.SubClass("odo:KnowledgeDomain", "Knowledge"));
    axioms.add(Axiom.SubClass("odo:PhysicalDomain", "Earth"));
    axioms.add(Axiom.SubClass("odo:PhysicalDomain", "Ocean"));
    ontology.define(axioms);
    return owl;
  }

  private KimConceptImpl syntax(String name) {
    var result = new KimConceptImpl(); result.setName("odo:" + name);
    result.setType(EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE));
    result.finalizeDefinition(); return result;
  }

  @Test void repeatedAboutKeepsIndependentRestrictionsAndBaseIdentity() {
    var owl = fixture(); var expression = syntax("Knowledge");
    expression.getAbout().add(syntax("Earth")); expression.getAbout().add(syntax("Ocean"));
    expression.resetDefinition();
    var base = owl.getConcept("odo:Knowledge");
    var result = DomainInterpretationSupport.compose(owl, base, expression, x -> owl.getConcept(x.getName()));
    assertNotEquals(base, result);
    assertTrue(result.is(SemanticType.DOMAIN)); assertTrue(result.is(SemanticType.PREDICATE));
    assertFalse(result.is(SemanticType.OBSERVABLE));
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, base).isEmpty());
    assertEquals(2, WorldviewDomainSupport.effectiveInterpretations(owl, result).size());
    var restrictions = owl.getOntology(result.getNamespace()).getOWLOntology()
        .getSubClassAxiomsForSubClass(owl.getOWLClass(result)).stream()
        .filter(a -> a.getSuperClass() instanceof org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom).toList();
    assertEquals(2, restrictions.size());
    assertTrue(restrictions.stream().allMatch(a ->
        ((org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom)a.getSuperClass()).getFiller().isOWLClass()));
    var intersection = owl.getIntersection(List.of(owl.getConcept("odo:Earth"), owl.getConcept("odo:Ocean")),
        owl.getOntology("odo"), base.getType());
    var target = syntax("Earth"); target.getOperands().add(syntax("Ocean"));
    target.setExpressionType(org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression.INTERSECTION);
    target.resetDefinition();
    var oneTarget = syntax("Knowledge"); oneTarget.getAbout().add(target); oneTarget.resetDefinition();
    var oneRestriction = DomainInterpretationSupport.compose(owl, base, oneTarget, ignored -> intersection);
    assertNotEquals(result, oneRestriction);
    var compositeGrounding = WorldviewDomainSupport.effectiveInterpretations(owl, oneRestriction);
    assertEquals(1, compositeGrounding.size());
    assertEquals(intersection, compositeGrounding.getFirst().target());
  }

  @Test void childAddsObligationsAndRetainsInheritedProvenance() {
    var owl = fixture(); var parent = syntax("Knowledge"); parent.getAbout().add(syntax("Earth")); parent.resetDefinition();
    var composed = DomainInterpretationSupport.compose(owl, owl.getConcept(parent.getName()), parent, x -> owl.getConcept(x.getName()));
    var child = owl.makeSubclass(composed, "test:Specialized");
    WorldviewDomainSupport.markInterpretation(owl, owl.getOntology(child.getNamespace()), child, owl.getConcept("odo:Ocean"));
    var grounding = WorldviewDomainSupport.effectiveInterpretations(owl, child);
    assertEquals(2, grounding.size());
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, child));
    assertTrue(grounding.stream().anyMatch(g -> g.inherited() && g.declaringConcept().equals(owl.getOWLClass(composed).getIRI().toString())
        && g.target().equals(owl.getConcept("odo:Earth"))));
    assertTrue(new OWLSemanticClauseSupport(owl).specializes(child, composed));
  }

  @Test void bareExistentialNeverCountsAndInvalidEndpointsDoNotMutateBase() {
    var owl = fixture(); var base = owl.getConcept("odo:Knowledge");
    owl.restrictSome(base, owl.getProperty(DomainInterpretationSupport.PROPERTY),
        owl.getConcept("odo:PhysicalDomain"), owl.getOntology("odo"));
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, base).isEmpty());
    assertThrows(KlabValidationException.class, () -> DomainInterpretationSupport.requireEffectiveGrounding(owl, base));
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, owl.getConcept("odo:Earth")));
    var invalid = syntax("Knowledge"); invalid.getAbout().add(syntax("Earth")); invalid.getAbout().add(syntax("Knowledge")); invalid.resetDefinition();
    assertThrows(KlabValidationException.class, () -> DomainInterpretationSupport.compose(owl, base, invalid, x -> owl.getConcept(x.getName())));
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, base).isEmpty());
    assertThrows(KlabValidationException.class, () -> DomainInterpretationSupport.compose(owl, owl.getConcept("odo:Earth"), invalid, x -> owl.getConcept(x.getName())));
  }

  @Test void subjectMatterRestrictsTheCompleteCompositeWithoutChangingItsBranches() {
    var owl = fixture(); var ontology = owl.getOntology("odo");
    ontology.define(List.of(Axiom.ClassAssertion("Other", EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE)),
        Axiom.SubClass("odo:KnowledgeDomain", "Other")));
    var first = owl.getConcept("odo:Knowledge"); var second = owl.getConcept("odo:Other");
    var union = owl.getUnion(List.of(first, second), ontology, first.getType());
    var head = syntax("Knowledge"); head.getOperands().add(syntax("Other"));
    head.setExpressionType(org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression.UNION);
    head.resetDefinition();
    var expression = new KimConceptImpl(); expression.setObservable(head); expression.setType(head.getType());
    expression.getAbout().add(syntax("Earth")); expression.finalizeDefinition();
    var result = DomainInterpretationSupport.compose(owl, union, expression, x -> owl.getConcept(x.getName()));
    assertNotEquals(union, result);
    assertNotEquals(union.getUrn(), result.getUrn());
    assertTrue(new OWLSemanticClauseSupport(owl).specializes(result, union));
    assertEquals(1, WorldviewDomainSupport.effectiveInterpretations(owl, result).size());
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, first).isEmpty());
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, second).isEmpty());
  }

  @Test void compositeOwnershipDoesNotRebindAliasesOrReferences() {
    var owl = fixture(); var base = owl.getConcept("odo:Knowledge");
    var expression = syntax("Knowledge"); expression.getAbout().add(syntax("Earth")); expression.resetDefinition();
    var composed = DomainInterpretationSupport.compose(owl, base, expression, x -> owl.getConcept(x.getName()));
    var source = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    source.setUrn("odo");
    var statements = new ArrayList<org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl>();
    for (var name : List.of("Owned", "Alias", "Reference")) {
      owl.getOntology("odo").define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.QUALITY))));
      var statement = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
      statement.setUrn(name); statement.setNamespace("odo");
      source.getStatements().add(statement); statements.add(statement);
    }
    WorldviewDomainSupport.bind(owl, source, base);
    statements.get(1).setAlias(true);
    statements.get(2).setUpperConceptDefined("external:Original");
    WorldviewDomainSupport.bind(owl, source, composed);
    assertEquals(composed, WorldviewDomainSupport.domain(owl, owl.getConcept("odo:Owned")));
    assertEquals(base, WorldviewDomainSupport.domain(owl, owl.getConcept("odo:Alias")));
    assertEquals(base, WorldviewDomainSupport.domain(owl, owl.getConcept("odo:Reference")));
  }

  @Test void compilerKeepsBranchInterpretationOutOfTheWholeUnion() throws Exception {
    var owl = fixture();
    owl.getOntology("odo").define(List.of(
        Axiom.ClassAssertion("Other", EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE)),
        Axiom.SubClass("odo:KnowledgeDomain", "Other")));
    var service = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    var scope = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class);
    var resources = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    org.mockito.Mockito.when(service.owl()).thenReturn(owl);
    org.mockito.Mockito.when(service.resolveConcept(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    org.mockito.Mockito.when(resources.declareConcept(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
      String urn = call.getArgument(0); var known = owl.getConcept(urn);
      if (known == null) known = java.util.stream.Stream.concat(
          owl.requireOntology(OWL.INTERNAL_ONTOLOGY_ID).getConcepts().stream(), owl.getOntology("odo").getConcepts().stream())
          .filter(c -> urn.equals(c.getUrn())).findFirst().orElseThrow(() -> new AssertionError("Missing fixture concept " + urn));
      var leaf = new KimConceptImpl(); leaf.setName(known.getNamespace() + ":" + known.getName()); leaf.setType(new HashSet<>(known.getType()));
      leaf.finalizeDefinition(); return leaf;
    });
    var serviceClass = org.integratedmodelling.klab.services.reasoner.ReasonerService.class;
    var owlField = serviceClass.getDeclaredField("owl"); owlField.setAccessible(true); owlField.set(service, owl);
    // Exercise the real expression-status projection. An unstubbed Mockito return
    // is null and falsely makes the valid PhysicalDomain target unresolved.
    org.mockito.Mockito.doCallRealMethod().when(service).projectExpressionStatus(
        org.mockito.ArgumentMatchers.any(org.integratedmodelling.common.knowledge.ConceptImpl.class),
        org.mockito.ArgumentMatchers.any(org.integratedmodelling.klab.api.lang.kim.KimConcept.class));
    var compile = serviceClass.getDeclaredMethod("compileInternal",
        org.integratedmodelling.klab.api.lang.kim.KimConcept.class, Ontology.class,
        org.integratedmodelling.klab.api.scope.Scope.class);
    compile.setAccessible(true);
    var modified = syntax("Knowledge"); modified.getAbout().add(syntax("Earth")); modified.resetDefinition();
    var branch = new KimConceptImpl(); branch.setObservable(modified);
    branch.setType(EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE, SemanticType.UNION));
    branch.setExpressionType(org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression.UNION);
    branch.getOperands().add(syntax("Other")); branch.finalizeDefinition();
    var head = syntax("Knowledge"); head.getOperands().add(syntax("Other"));
    head.getType().add(SemanticType.UNION);
    head.setExpressionType(org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression.UNION); head.resetDefinition();
    var whole = new KimConceptImpl(); whole.setObservable(head); whole.setType(new HashSet<>(head.getType()));
    whole.getAbout().add(syntax("Earth")); whole.finalizeDefinition();
    var branchResult = (Concept) compile.invoke(service, branch, owl.getOntology("odo"), scope);
    var wholeResult = (Concept) compile.invoke(service, whole, owl.getOntology("odo"), scope);
    assertNotNull(branchResult); assertNotNull(wholeResult); assertNotEquals(branchResult, wholeResult);
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, branchResult).isEmpty());
    assertEquals(1, WorldviewDomainSupport.effectiveInterpretations(owl, wholeResult).size());
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, owl.getConcept("odo:Other")).isEmpty());
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, owl.getConcept("odo:Knowledge")).isEmpty());
    org.mockito.Mockito.verify(service, org.mockito.Mockito.atLeastOnce()).projectExpressionStatus(
        org.mockito.ArgumentMatchers.any(org.integratedmodelling.common.knowledge.ConceptImpl.class),
        org.mockito.ArgumentMatchers.any(org.integratedmodelling.klab.api.lang.kim.KimConcept.class));
  }
}
