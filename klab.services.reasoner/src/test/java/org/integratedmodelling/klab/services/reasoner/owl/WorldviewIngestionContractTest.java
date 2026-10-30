package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class WorldviewIngestionContractTest {
  @Test void unresolvedNestedValueCannotCertifyDocumentValidity() {
    var service = org.mockito.Mockito.mock(
        org.integratedmodelling.klab.services.reasoner.ReasonerService.class,
        org.mockito.Mockito.CALLS_REAL_METHODS);
    org.mockito.Mockito.doReturn(1L).when(service).knowledgeRevision();
    var document = new KimOntologyImpl();
    document.setUrn("audit");
    var parent = new KimConceptStatementImpl();
    var child = new KimConceptStatementImpl();
    child.setDescriptionValue("10 to 20");
    parent.getChildren().add(child);
    document.getStatements().add(parent);
    var response = service.validateDocument(
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(document, "1"), null);
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE,
        response.getStatus());
    assertFalse(response.valid());
    assertTrue(response.getReason().contains("DESCRIPTION_VALUE_UNRESOLVED"));
  }
  private final OWL owl;
  private final Ontology ontology;
  private final OWLDataFactory f;
  WorldviewIngestionContractTest() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{Scope.class}, (self, method, args) -> null);
    owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    f = owl.manager.getOWLDataFactory();
    ontology = owl.requireOntology("audit");
    for (String property : List.of("isInherentTo", "describes", "describesQuality", "increasesWith", "decreasesWith",
        "marksQuality", "classifiesQuality", "discretizesQuality", "appliesTo"))
      owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion(property)));
    for (String name : List.of("X", "Y", "Z", "Parent"))
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.PRESENCE))));
    for (String name : List.of("Tree", "Oak", "Rock", "Habitat"))
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE, SemanticType.SUBJECT))));
    ontology.define(List.of(Axiom.SubClass("Tree", "Oak"), Axiom.DisjointClasses(new String[]{"audit:Tree", "audit:Rock"})));
  }
  private Concept c(String name) { return ontology.getConcept(name); }
  private OWLClass type(String name) { return owl.getOWLClass(c(name)); }
  private KimConcept syntax(String name) { var value = new KimConceptImpl(); value.setName("audit:"+name); return value; }
  private OWLObjectProperty bearer() { return f.getOWLObjectProperty(IRI.create(owl.getProperty("odo:isInherentTo").getURI())); }
  private void bound(String owner, OWLClassExpression filler) {
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type(owner), f.getOWLObjectAllValuesFrom(bearer(), filler), Set.of(semanticMarker())));
  }
  private OWLAnnotation semanticMarker() {
    return f.getOWLAnnotation(f.getOWLAnnotationProperty(OWLSemanticClauseSupport.UNIVERSAL_BEARER), f.getOWLLiteral(true));
  }

  private KimConceptStatementImpl description(String target, KimConceptStatement.DescriptionType relation) {
    var statement = new KimConceptStatementImpl();
    statement.getObservablesDescribed().add(new PairImpl<>(syntax(target), relation));
    return statement;
  }
  private void compile(String name, KimConceptStatement statement) {
    WorldviewDeclarationSupport.compile(owl, ontology, c(name), statement, value -> owl.getConcept(value.getName()));
  }
  private Set<OWLAxiom> axioms() { return new HashSet<>(ontology.getOWLOntology().getAxioms()); }
  private boolean hasNecessaryBearer(String owner, OWLClassExpression filler) {
    var expected = f.getOWLSubClassOfAxiom(type(owner), f.getOWLObjectAllValuesFrom(bearer(), filler));
    return axioms().stream().anyMatch(a -> a.getAxiomWithoutAnnotations().equals(expected));
  }
  @Test void infersMissingBearerOnlyOnOwnerAndRetainsUnresolvedProvenance() {
    bound("Y", type("Tree"));
    var targetBefore = ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("Y"));
    compile("X", description("Y", KimConceptStatement.DescriptionType.DESCRIBES));
    assertTrue(hasNecessaryBearer("X", type("Tree")));
    assertEquals(targetBefore, ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("Y")));
    assertTrue(axioms().stream().flatMap(a -> a.annotations()).anyMatch(a -> a.toString().contains("UNRESOLVED")));
    assertTrue(axioms().stream().flatMap(a -> a.annotations()).anyMatch(a -> a.toString().contains("derived:description")));
    assertTrue(ontology.getOWLOntology().getAxioms(AxiomType.SAME_INDIVIDUAL).isEmpty());
  }
  @Test void narrowerAndUnknownBearersAreRetainedByIntersection() {
    bound("X", type("Oak")); bound("Y", type("Tree")); bound("Z", type("Habitat"));
    var statement = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
    statement.getObservablesDescribed().add(new PairImpl<>(syntax("Z"), KimConceptStatement.DescriptionType.MARKS));
    compile("X", statement);
    assertTrue(hasNecessaryBearer("X", f.getOWLObjectIntersectionOf(type("Oak"), type("Tree"), type("Habitat"))));
  }
  @Test void rejectsInheritedEquivalentConflictBeforeAnyClauseWrites() {
    bound("Parent", type("Tree"));
    ontology.define(List.of(Axiom.SubClass("Parent", "X")));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLEquivalentClassesAxiom(type("Y"),
        f.getOWLObjectAllValuesFrom(bearer(), type("Rock"))).getAnnotatedAxiom(Set.of(semanticMarker())));
    var before = axioms();
    var failure = assertThrows(KlabValidationException.class,
        () -> compile("X", description("Y", KimConceptStatement.DescriptionType.DESCRIBES)));
    assertTrue(failure.getMessage().contains("DESCRIPTION_BEARER_CONFLICT"));
    assertEquals(before, axioms());
  }
  @Test void unionRemainsOneBoundAndEveryIndependentBoundIsChecked() {
    bound("X", f.getOWLObjectUnionOf(type("Tree"), type("Rock")));
    bound("Y", type("Oak"));
    compile("X", description("Y", KimConceptStatement.DescriptionType.DESCRIBES));
    bound("Z", type("Rock"));
    var before = axioms();
    assertThrows(KlabValidationException.class, () -> compile("X", description("Z", KimConceptStatement.DescriptionType.DESCRIBES)));
    assertEquals(before, axioms());
  }
  @Test void authoredWithinIsValidatedWithDescriptions() {
    bound("Y", type("Tree"));
    var statement = description("Y", KimConceptStatement.DescriptionType.INCREASES_WITH);
    statement.setDeclaredInherent(syntax("Rock"));
    var before = axioms();
    assertThrows(KlabValidationException.class, () -> compile("X", statement));
    assertEquals(before, axioms());
    statement.setDeclaredInherent(syntax("Oak"));
    compile("X", statement);
    assertTrue(hasNecessaryBearer("X", f.getOWLObjectIntersectionOf(type("Tree"), type("Oak"))));
  }
  @Test void legalValueSourceIsPreservedAsUnresolvedAndUnsupportedPartitionsDoNotMutate() {
    var value = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
    value.setDescriptionValue("0 to 10");
    assertDoesNotThrow(() -> compile("X", value));
    assertTrue(WorldviewDeclarationSupport.hasDeferredValue(value));
    var annotations = ontology.getOWLOntology().getAnnotationAssertionAxioms(type("X").getIRI());
    assertTrue(annotations.stream().anyMatch(a -> a.getProperty().getIRI().equals(WorldviewDeclarationSupport.VALUE_SOURCE)
        && a.getValue().equals(f.getOWLLiteral("0 to 10"))));
    assertTrue(annotations.stream().anyMatch(a -> a.getProperty().getIRI().equals(WorldviewDeclarationSupport.VALUE_STATUS)
        && a.getValue().equals(f.getOWLLiteral("UNRESOLVED"))));
    var before = axioms();
    assertTrue(assertThrows(KlabValidationException.class, () -> compile("X",
        description("Y", KimConceptStatement.DescriptionType.DISCRETIZES)))
        .getMessage().contains("DISCRETIZATION_PARTITION_UNSUPPORTED"));
    assertEquals(before, axioms());
  }

  @Test void allExistingValueFormsRemainUnresolvedRatherThanBeingRejectedAsInvalid() {
    for (var raw : List.of("3", "3 to 5", "true", "false", "audit:Tree")) {
      var value = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
      value.setDescriptionValue(raw);
      assertDoesNotThrow(() -> compile("X", value));
      assertTrue(WorldviewDeclarationSupport.hasDeferredValue(value));
    }
    var absent = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
    assertFalse(WorldviewDeclarationSupport.hasDeferredValue(absent));
    absent.setDescriptionValue(" ");
    assertFalse(WorldviewDeclarationSupport.hasDeferredValue(absent));
  }
  @Test void eachSupportedDescriptionRelationUsesTheSameBearerValidation() {
    bound("X", type("Tree")); bound("Y", type("Rock"));
    ontology.define(List.of(Axiom.ClassAssertion("Classifier", EnumSet.of(SemanticType.PREDICATE, SemanticType.ORDERING))));
    bound("Classifier", type("Tree"));
    for (var relation : KimConceptStatement.DescriptionType.values()) {
      var before = axioms();
      var owner = relation == KimConceptStatement.DescriptionType.CLASSIFIES ? "Classifier" : "X";
      assertThrows(KlabValidationException.class, () -> compile(owner, description("Y", relation)));
      assertEquals(before, axioms());
    }
  }
  @Test void owlReasonerProvesCompatibleOrImpossibleIntersections() throws Exception {
    var merged = owl.manager.createOntology(owl.manager.ontologies().flatMap(o -> o.axioms())
        .collect(java.util.stream.Collectors.toSet()));
    var reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    try {
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, reasoner);
      var support = new OWLSemanticClauseSupport(owl);
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE, support.bearerCompatibility(List.of(type("Oak"), type("Tree"))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE, support.bearerCompatibility(List.of(type("Oak"), type("Rock"))));
    } finally { reasoner.dispose(); }
  }
  @Test void domainOwnershipIncludesNestedDeclarationsAndDoesNotRebindReferences() {
    ontology.define(List.of(Axiom.ClassAssertion("Physical", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("Knowledge", EnumSet.of(SemanticType.DOMAIN))));
    var source = new KimOntologyImpl(); source.setUrn("audit"); source.setDomain(syntax("Physical"));
    var parent = new KimConceptStatementImpl(); parent.setUrn("X"); parent.setNamespace("audit");
    var child = new KimConceptStatementImpl(); child.setUrn("Y"); child.setNamespace("audit");
    parent.getChildren().add(child); parent.getDeclaredReferences().add(syntax("Z")); source.getStatements().add(parent);
    var domain = WorldviewDomainSupport.validate(source, k -> owl.getConcept(k.getName()));
    WorldviewDomainSupport.bind(owl, source, domain);
    assertEquals(c("Physical"), WorldviewDomainSupport.domain(owl, c("X")));
    assertEquals(c("Physical"), WorldviewDomainSupport.domain(owl, c("Y")));
    assertNull(WorldviewDomainSupport.domain(owl, c("Z")));
    source.setDomain(syntax("Knowledge")); child.setAlias(true);
    WorldviewDomainSupport.bind(owl, source, WorldviewDomainSupport.validate(source, k -> owl.getConcept(k.getName())));
    assertEquals(c("Knowledge"), WorldviewDomainSupport.domain(owl, c("X")));
    assertEquals(c("Physical"), WorldviewDomainSupport.domain(owl, c("Y")));
    source.setDomain(syntax("Tree"));
    assertThrows(KlabValidationException.class, () -> WorldviewDomainSupport.validate(source, k -> owl.getConcept(k.getName())));
  }
  private void domainKinds() {
    owl.requireOntology("odo").define(List.of(
        Axiom.ClassAssertion("KnowledgeDomain", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("PhysicalDomain", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ObjectPropertyAssertion("interpretsPhysicalDomain")));
    ontology.define(List.of(Axiom.ClassAssertion("Knowledge", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("Physical", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.SubClass("odo:KnowledgeDomain", "Knowledge"),
        Axiom.SubClass("odo:PhysicalDomain", "Physical")));
  }
  @Test void knowledgeDomainsRequireAuthoredPhysicalLinksEvenWithExistentialInference() throws Exception {
    domainKinds();
    owl.restrictSome(c("Knowledge"), owl.getProperty("odo:interpretsPhysicalDomain"), c("Physical"), ontology);
    var statement = new KimConceptStatementImpl();
    var before = axioms();
    assertTrue(assertThrows(KlabValidationException.class, () -> compile("Knowledge", statement))
        .getMessage().contains("KNOWLEDGE_DOMAIN_INTERPRETATION_REQUIRED"));
    assertEquals(before, axioms());
    statement.getInterpretedPhysicalDomains().add(syntax("Tree"));
    assertThrows(KlabValidationException.class, () -> compile("Knowledge", statement));
    assertEquals(before, axioms());
    statement.getInterpretedPhysicalDomains().clear();
    statement.getInterpretedPhysicalDomains().add(syntax("Physical"));
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var wire = mapper.readValue(mapper.writeValueAsBytes(statement), KimConceptStatement.class);
    assertEquals(1, wire.getInterpretedPhysicalDomains().size());
    assertDoesNotThrow(() -> compile("Knowledge", wire));
    assertThrows(KlabValidationException.class, () -> compile("Physical", wire));
  }
  @Test void knowledgeQualityGroundingMayBeInheritedAndAbstractRootsMayDefer() {
    domainKinds();
    var declaration = new KimConceptStatementImpl(); declaration.setUrn("X"); declaration.setNamespace("audit");
    WorldviewDomainSupport.bind(owl, "audit", declaration, c("Knowledge"));
    assertTrue(assertThrows(KlabValidationException.class, () -> compile("X", declaration))
        .getMessage().contains("KNOWLEDGE_QUALITY_GROUNDING_REQUIRED"));
    declaration.setAbstract(true);
    assertDoesNotThrow(() -> compile("X", declaration));
    declaration.setAbstract(false);
    compile("Parent", description("Y", KimConceptStatement.DescriptionType.DESCRIBES));
    ontology.define(List.of(Axiom.SubClass("Parent", "X")));
    assertDoesNotThrow(() -> compile("X", declaration));
    // Describes remains valid for qualities outside KnowledgeDomain.
    assertDoesNotThrow(() -> compile("Z", description("Y", KimConceptStatement.DescriptionType.DESCRIBES)));
  }

  @Test void inheritedAuthoredInterpretationsCountAndRemainAdditiveWithSourceProvenance() throws Exception {
    domainKinds();
    for (String name : List.of("SpecializedKnowledge", "PhysicalTwo", "AnonymousKnowledge"))
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.DOMAIN))));
    ontology.define(List.of(Axiom.SubClass("Knowledge", "SpecializedKnowledge"),
        Axiom.SubClass("odo:KnowledgeDomain", "AnonymousKnowledge"),
        Axiom.SubClass("odo:PhysicalDomain", "PhysicalTwo")));
    var parent = new KimConceptStatementImpl(); parent.getInterpretedPhysicalDomains().add(syntax("Physical"));
    compile("Knowledge", parent);
    var child = new KimConceptStatementImpl();
    assertDoesNotThrow(() -> compile("SpecializedKnowledge", child));
    child.getInterpretedPhysicalDomains().add(syntax("PhysicalTwo"));
    compile("SpecializedKnowledge", child);
    var effective = WorldviewDomainSupport.effectiveInterpretations(owl, c("SpecializedKnowledge"));
    assertEquals(2, effective.size());
    assertTrue(effective.stream().anyMatch(g -> g.target().equals(c("Physical")) && g.inherited()
        && g.declaringConcept().equals(type("Knowledge").getIRI().toString())));
    assertTrue(effective.stream().anyMatch(g -> g.target().equals(c("PhysicalTwo")) && !g.inherited()));
    assertTrue(new OWLSemanticClauseSupport(owl).bearerConstraints(c("SpecializedKnowledge")).isEmpty());
    owl.restrictSome(c("AnonymousKnowledge"), owl.getProperty("odo:interpretsPhysicalDomain"), c("Physical"), ontology);
    assertThrows(KlabValidationException.class, () -> compile("AnonymousKnowledge", new KimConceptStatementImpl()));
    // The named parent obligation survives serialization independently of inference.
    var bytes = new java.io.ByteArrayOutputStream(); owl.manager.saveOntology(ontology.getOWLOntology(), bytes);
    var manager = OWLManager.createOWLOntologyManager();
    manager.createOntology(owl.getOntology("odo").getOWLOntology().getAxioms(),
        owl.getOntology("odo").getOWLOntology().getOntologyID().getOntologyIRI().orElseThrow());
    var reloaded = manager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(bytes.toByteArray()));
    assertEquals(2, reloaded.axioms().flatMap(OWLAxiom::annotations)
        .filter(a -> a.getProperty().getIRI().equals(WorldviewDomainSupport.AUTHORED_INTERPRETATION)).count());
  }

  @Test void importedExistentialsKeepSeparateWitnessesAndTheirQuantifiers() throws Exception {
    var tree = f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectSomeValuesFrom(bearer(), type("Tree")));
    var rock = f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectSomeValuesFrom(bearer(), type("Rock")));
    owl.manager.addAxioms(ontology.getOWLOntology(), Set.of(tree, rock));
    assertDoesNotThrow(() -> compile("X", new KimConceptStatementImpl()));
    assertTrue(ontology.getOWLOntology().containsAxiom(tree));
    assertTrue(ontology.getOWLOntology().containsAxiom(rock));
    assertFalse(ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("X")).stream()
        .anyMatch(a -> a.getSuperClass() instanceof OWLObjectAllValuesFrom));
    var constraints = new OWLSemanticClauseSupport(owl).bearerConstraints(c("X"));
    assertEquals(2, constraints.size());
    assertTrue(constraints.stream().allMatch(b -> b.restriction() instanceof OWLObjectSomeValuesFrom && !b.semantic()));
    assertSatisfiable("X");
    // Only explicit k.LAB mapping establishes universal bearer semantics.
    OWLSemanticClauseSupport.markUniversalBearer(owl, c("X"), owl.getProperty("odo:isInherentTo"), type("Tree"), ontology);
    assertThrows(KlabValidationException.class, () -> compile("X", new KimConceptStatementImpl()));
  }
  @Test void importedSubpropertyUniversalDoesNotConstrainEverySuperpropertyWitness() throws Exception {
    var sub = f.getOWLObjectProperty(IRI.create("urn:imported:particularBearer"));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubObjectPropertyOfAxiom(sub, bearer()));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectAllValuesFrom(sub, type("Tree"))));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectSomeValuesFrom(bearer(), type("Rock"))));
    assertDoesNotThrow(() -> compile("X", new KimConceptStatementImpl()));
    assertFalse(hasNecessaryBearer("X", type("Tree")));
    assertTrue(new OWLSemanticClauseSupport(owl).bearerConstraints(c("X")).stream()
        .anyMatch(b -> b.property().equals(sub) && b.restriction() instanceof OWLObjectAllValuesFrom));
    assertSatisfiable("X");
  }
  private void assertSatisfiable(String name) throws Exception {
    var merged = owl.manager.createOntology(owl.manager.ontologies().flatMap(o -> o.axioms())
        .collect(java.util.stream.Collectors.toSet()));
    var reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    try { assertTrue(reasoner.isSatisfiable(type(name))); }
    finally { reasoner.dispose(); owl.manager.removeOntology(merged); }
  }
  @Test void authoredApplicabilityParticipatesAsAUnionOnlyInDependentBearerScope() {
    bound("Y", type("Tree"));
    var statement = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
    statement.getAppliesTo().add(new ApplicableConceptImpl(null, syntax("Rock")));
    var before = axioms();
    assertThrows(KlabValidationException.class, () -> compile("X", statement));
    assertEquals(before, axioms());
    statement.getAppliesTo().add(new ApplicableConceptImpl(null, syntax("Tree")));
    assertDoesNotThrow(() -> compile("X", statement));
    ontology.define(List.of(Axiom.ClassAssertion("Predicate", EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE))));
    var predicate = description("Y", KimConceptStatement.DescriptionType.DESCRIBES);
    predicate.getAppliesTo().add(new ApplicableConceptImpl(null, syntax("Rock")));
    assertDoesNotThrow(() -> compile("Predicate", predicate));
  }
  @Test void inheritedOnlyAndEquivalentUniversalBearerConflictsRejectWithoutMutation() {
    bound("Parent", type("Tree")); bound("Y", type("Rock"));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLEquivalentClassesAxiom(type("X"),
        f.getOWLObjectIntersectionOf(type("Parent"), type("Y"))));
    var before = axioms();
    assertThrows(KlabValidationException.class, () -> compile("X", new KimConceptStatementImpl()));
    assertEquals(before, axioms());
  }

  @Test void stagedBearerRejectsDirectMaximumAndInheritedExactZeroBeforeEmission() {
    bound("Y", type("Tree"));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("X"),
        f.getOWLObjectMaxCardinality(0, bearer(), type("Tree"))));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("Parent"),
        f.getOWLObjectExactCardinality(0, bearer(), type("Tree"))));
    ontology.define(List.of(Axiom.SubClass("Parent", "Z")));
    var before = axioms();
    for (String owner : List.of("X", "Z")) {
      assertThrows(KlabValidationException.class,
          () -> compile(owner, description("Y", KimConceptStatement.DescriptionType.DESCRIBES)));
      assertEquals(before, axioms());
    }
  }

  @Test void maximumUsesPropertyScopeWithoutInventingSharedWitnesses() {
    var sub = f.getOWLObjectProperty(IRI.create("urn:imported:specificBearer"));
    var sup = f.getOWLObjectProperty(IRI.create("urn:imported:participation"));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubObjectPropertyOfAxiom(sub, bearer()));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubObjectPropertyOfAxiom(bearer(), sup));
    bound("Y", type("Tree"));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("X"),
        f.getOWLObjectMaxCardinality(0, sub, type("Tree"))));
    assertDoesNotThrow(() -> compile("X", description("Y", KimConceptStatement.DescriptionType.DESCRIBES)));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("Parent"),
        f.getOWLObjectMaxCardinality(0, sup, type("Tree"))));
    ontology.define(List.of(Axiom.SubClass("Parent", "Z")));
    var before = axioms();
    assertThrows(KlabValidationException.class,
        () -> compile("Z", description("Y", KimConceptStatement.DescriptionType.DESCRIBES)));
    assertEquals(before, axioms());
  }

  @Test void fullConjunctionChecksCardinalityWithDlAndRetainsBootstrapUncertainty() throws Exception {
    owl.manager.addAxioms(ontology.getOWLOntology(), Set.of(
        f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectMaxCardinality(1, bearer(), f.getOWLThing())),
        f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectSomeValuesFrom(bearer(), type("Tree"))),
        f.getOWLSubClassOfAxiom(type("X"), f.getOWLObjectSomeValuesFrom(bearer(), type("Rock")))));
    assertEquals(OWLSemanticClauseSupport.Compatibility.UNRESOLVED,
        new OWLSemanticClauseSupport(owl).bearerCompatibility(List.of(type("X"))));
    assertDoesNotThrow(() -> compile("X", new KimConceptStatementImpl()));
    assertTrue(axioms().stream().anyMatch(a -> a.toString().contains("UNRESOLVED")));
    var merged = owl.manager.createOntology(owl.manager.ontologies().flatMap(o -> o.axioms())
        .collect(java.util.stream.Collectors.toSet()));
    var reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    try {
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, reasoner);
      var before = axioms();
      assertThrows(KlabValidationException.class, () -> compile("X", new KimConceptStatementImpl()));
      assertEquals(before, axioms());
    } finally { reasoner.dispose(); owl.manager.removeOntology(merged); }
  }
}
