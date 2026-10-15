package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
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

/** Translation and DL checks use the exact externally supplied, private ODO catalog. */
class BroadDescriptionContractTest {
  private final OWL owl;
  private final Ontology ontology;
  private final OWLDataFactory f;
  BroadDescriptionContractTest() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{Scope.class}, (self, method, args) -> null);
    owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    var directory = System.getProperty("klab.test.core.directory");
    assertNotNull(directory, "A pinned private core is required; never use the packaged default");
    owl.load(Path.of(directory).toFile());
    ontology = owl.requireOntology("broadtest"); f = owl.manager.getOWLDataFactory();
    declare("Q", "Quality", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    declare("ChildQ", "Quality", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    declare("Pattern", "Configuration", SemanticType.OBSERVABLE, SemanticType.CONFIGURATION);
    declare("PatternTwo", "Configuration", SemanticType.OBSERVABLE, SemanticType.CONFIGURATION);
    declare("Flow", "Process", SemanticType.OBSERVABLE, SemanticType.PROCESS);
    declare("Tree", "Subject", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    declare("Oak", "Subject", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    declare("Rock", "Subject", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    declare("Actor", "Agent", SemanticType.OBSERVABLE, SemanticType.AGENT, SemanticType.COUNTABLE);
    declare("ActorChild", "Agent", SemanticType.OBSERVABLE, SemanticType.AGENT, SemanticType.COUNTABLE);
    declare("OtherActor", "Agent", SemanticType.OBSERVABLE, SemanticType.AGENT, SemanticType.COUNTABLE);
    declare("Happening", "Event", SemanticType.OBSERVABLE, SemanticType.EVENT, SemanticType.COUNTABLE);
    declare("Connection", "Relationship", SemanticType.OBSERVABLE, SemanticType.RELATIONSHIP, SemanticType.COUNTABLE);
    declare("Generic", "Observable", SemanticType.OBSERVABLE);
    declare("Space", "Extent", SemanticType.OBSERVABLE, SemanticType.EXTENT);
    declare("Identity", "Identity", SemanticType.PREDICATE, SemanticType.IDENTITY);
    declare("Domain", "Domain", SemanticType.PREDICATE, SemanticType.DOMAIN);
    ontology.define(List.of(Axiom.SubClass("Tree", "Oak"), Axiom.SubClass("Actor", "ActorChild"),
        Axiom.DisjointClasses(new String[]{"broadtest:Tree", "broadtest:Rock"}),
        Axiom.DisjointClasses(new String[]{"broadtest:Actor", "broadtest:OtherActor"})));
  }
  private void declare(String name, String core, SemanticType... types) {
    ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.copyOf(List.of(types))),
        Axiom.SubClass("odo:" + core, name)));
  }
  private Concept c(String name) { return owl.getConcept("broadtest:" + name); }
  private OWLClass type(String name) { return owl.getOWLClass(c(name)); }
  private KimConcept syntax(String name) { var k = new KimConceptImpl(); k.setName("broadtest:" + name); return k; }
  private OWLObjectProperty property(String name) { return owl.getProperty("odo:" + name).getOWLEntity().asOWLObjectProperty(); }
  private KimConceptStatementImpl description(String target, KimConceptStatement.DescriptionType kind) {
    var s = new KimConceptStatementImpl(); s.getObservablesDescribed().add(new PairImpl<>(syntax(target), kind)); return s;
  }
  private KimConceptStatementImpl description(String target) { return description(target, KimConceptStatement.DescriptionType.DESCRIBES); }
  private void compile(String source, KimConceptStatement statement) {
    WorldviewDeclarationSupport.compile(owl, ontology, c(source), statement, k -> owl.getConcept(k.getName()));
  }
  private void bound(String owner, String bearer) {
    OWLSemanticClauseSupport.markUniversalBearer(owl, c(owner), owl.getProperty("odo:isInherentTo"), c(bearer), ontology);
  }
  private Set<OWLAxiom> axioms() { return new HashSet<>(ontology.getOWLOntology().getAxioms()); }
  private void hasDescription(String source, String target) {
    assertTrue(ontology.getOWLOntology().containsAxiom(f.getOWLSubClassOfAxiom(type(source),
        f.getOWLObjectSomeValuesFrom(property("describes"), type(target)))));
    assertFalse(ontology.getOWLOntology().containsAxiom(f.getOWLSubClassOfAxiom(type(source),
        f.getOWLObjectSomeValuesFrom(property("describesQuality"), type(target)))));
  }
  private void withDl(java.util.function.Consumer<org.semanticweb.owlapi.reasoner.OWLReasoner> check) throws Exception {
    var merged = owl.manager.createOntology(owl.manager.ontologies().flatMap(o -> o.axioms()).collect(java.util.stream.Collectors.toSet()));
    var reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    try { check.accept(reasoner); } finally { reasoner.dispose(); owl.manager.removeOntology(merged); }
  }

  @Test void everyObservableKindUsesGeneralDescriptionWithoutUnintendedTyping() throws Exception {
    // Separate source qualities: direct descriptions of disjoint substantials must
    // not accidentally require one source to have incompatible universal bearers.
    int index=0;
    for (var target : List.of("Pattern", "Flow", "Tree", "Actor", "Happening", "Connection", "ChildQ", "Space", "Generic")) {
      var source="AllKindsQ"+(index++);declare(source,"Quality",SemanticType.OBSERVABLE,SemanticType.QUALITY);
      compile(source, description(target)); hasDescription(source, target);
    }
    withDl(r -> {
      assertTrue(r.isConsistent());
      assertEquals(0, r.getUnsatisfiableClasses().entities().filter(c -> !c.isOWLNothing()).count());
      var quality = owl.getOWLClass(owl.getConcept("odo:Quality"));
      for (var name : List.of("Pattern", "Flow", "Tree", "Actor", "Happening", "Connection"))
        assertFalse(r.isEntailed(f.getOWLSubClassOfAxiom(type(name), quality)), name);
      assertFalse(r.isEntailed(f.getOWLSubClassOfAxiom(type("Q"), owl.getOWLClass(owl.getConcept("odo:Domain")))));
    });
  }

  @Test void domainStillDescribesConfigurationWithoutInherencyAndIsAPredicate() throws Exception {
    bound("Pattern", "Tree"); compile("Domain", description("Pattern")); hasDescription("Domain", "Pattern");
    assertTrue(new OWLSemanticClauseSupport(owl).bearerConstraints(c("Domain")).isEmpty());
    withDl(r -> {
      assertTrue(r.isConsistent()); assertTrue(r.isSatisfiable(type("Domain")));
      assertTrue(r.isEntailed(f.getOWLSubClassOfAxiom(owl.getOWLClass(owl.getConcept("odo:Domain")),
          owl.getOWLClass(owl.getConcept("odo:Predicate")))));
    });
  }

  @Test void invalidMissingBottomAndNonObservableTargetsRejectBeforeWrites() {
    for (var target : List.of("Identity", "Missing")) {
      var before = axioms();
      assertThrows(KlabValidationException.class, () -> compile("Q", description(target)));
      assertEquals(before, axioms());
    }
    var statement = new KimConceptStatementImpl();
    statement.getObservablesDescribed().add(new PairImpl<>(null, KimConceptStatement.DescriptionType.DESCRIBES));
    var before = axioms(); assertThrows(KlabValidationException.class, () -> compile("Q", statement));
    assertEquals(before, axioms());
    assertThrows(KlabValidationException.class, () -> WorldviewDeclarationSupport.compile(owl, ontology,
        c("Q"), description("Generic"), k -> null));
  }

  @Test void specializedClausesKeepTheirTargetAndSourceBounds() {
    for (var relation : List.of(KimConceptStatement.DescriptionType.MARKS,
        KimConceptStatement.DescriptionType.INCREASES_WITH, KimConceptStatement.DescriptionType.DECREASES_WITH,
        KimConceptStatement.DescriptionType.CLASSIFIES, KimConceptStatement.DescriptionType.DISCRETIZES)) {
      var before = axioms(); var source = relation == KimConceptStatement.DescriptionType.CLASSIFIES ? "Identity" : "Q";
      assertThrows(KlabValidationException.class, () -> compile(source, description("Pattern", relation)), relation.toString());
      assertEquals(before, axioms());
    }
    assertThrows(KlabValidationException.class, () -> compile("Flow", description("Q")));
  }

  @Test void universalBearerInferenceAndRefinementWorkForConfigurationProcessAndQuality() {
    for (var target : List.of("Pattern", "Flow", "ChildQ")) bound(target, "Tree");
    bound("Q", "Oak");
    for (var target : List.of("Pattern", "Flow", "ChildQ")) compile("Q", description(target));
    assertTrue(ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("Q")).stream()
        .anyMatch(a -> a.getSuperClass() instanceof OWLObjectAllValuesFrom all
            && all.getProperty().equals(property("isInherentTo"))
            && all.getFiller().containsConjunct(type("Oak")) && all.getFiller().containsConjunct(type("Tree"))));
  }

  @Test void conflictingBearersForBroadDependentTargetsRejectBeforeAnyWrites() {
    bound("Q", "Tree");
    for (var target : List.of("Pattern", "Flow", "ChildQ")) {
      bound(target, "Rock"); var before = axioms();
      var error = assertThrows(KlabValidationException.class, () -> compile("Q", description(target)));
      assertTrue(error.getMessage().contains("DESCRIPTION_BEARER_CONFLICT")); assertEquals(before, axioms());
    }
  }

  @Test void targetWithoutUniversalBearerIsFlaggedNotGuessedFromItsIdentity() {
    for (var target : List.of("Happening", "Connection", "Generic")) compile("Q", description(target));
    var flags = WorldviewDeclarationSupport.unresolvedDescriptionBearers(owl, c("Q"));
    assertEquals(3, flags.size()); assertTrue(flags.stream().allMatch(s -> s.contains("UNRESOLVED")));
    assertFalse(ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("Q")).stream()
        .anyMatch(a -> a.getSuperClass() instanceof OWLObjectAllValuesFrom all
            && all.getProperty().equals(property("isInherentTo"))));
  }

  @Test void importedExistentialRemainsUnresolvedButIsNotSilentlyDropped() {
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("Pattern"),
        f.getOWLObjectSomeValuesFrom(property("isInherentTo"), type("Rock"))));
    bound("Q", "Tree"); compile("Q", description("Pattern"));
    assertEquals(1, WorldviewDeclarationSupport.unresolvedDescriptionBearers(owl, c("Q")).size());
    assertTrue(ontology.getOWLOntology().getSubClassAxiomsForSubClass(type("Q")).stream()
        .flatMap(OWLAxiom::annotations)
        .anyMatch(a -> a.getProperty().getIRI().equals(DescriptionBearerSupport.COMPATIBILITY)
            && a.getValue() instanceof OWLLiteral value && value.getLiteral().equals("UNRESOLVED")));
  }

  @Test void impossibleTargetRestrictionsStillRejectEvenWhenContributionIsUnresolved() {
    owl.manager.addAxioms(ontology.getOWLOntology(), Set.of(
        f.getOWLSubClassOfAxiom(type("Pattern"), f.getOWLObjectSomeValuesFrom(property("isInherentTo"), type("Tree"))),
        f.getOWLSubClassOfAxiom(type("Pattern"), f.getOWLObjectAllValuesFrom(property("isInherentTo"), type("Rock")))));
    var before = axioms(); assertThrows(KlabValidationException.class, () -> compile("Q", description("Pattern")));
    assertEquals(before, axioms());
  }

  @Test void separateBearersMayBeDifferentIndividualsDespiteUniversalTypeAlignment() throws Exception {
    bound("Q", "Tree"); bound("Pattern", "Tree"); compile("Q", description("Pattern"));
    var q=f.getOWLNamedIndividual(IRI.create("urn:broad-test:q")); var p=f.getOWLNamedIndividual(IRI.create("urn:broad-test:p"));
    var b1=f.getOWLNamedIndividual(IRI.create("urn:broad-test:b1")); var b2=f.getOWLNamedIndividual(IRI.create("urn:broad-test:b2"));
    owl.manager.addAxioms(ontology.getOWLOntology(), Set.of(
        f.getOWLClassAssertionAxiom(type("Q"),q), f.getOWLClassAssertionAxiom(type("Pattern"),p),
        f.getOWLClassAssertionAxiom(type("Tree"),b1), f.getOWLClassAssertionAxiom(type("Tree"),b2),
        f.getOWLObjectPropertyAssertionAxiom(property("describes"),q,p),
        f.getOWLObjectPropertyAssertionAxiom(property("isInherentTo"),q,b1),
        f.getOWLObjectPropertyAssertionAxiom(property("isInherentTo"),p,b2), f.getOWLDifferentIndividualsAxiom(b1,b2)));
    withDl(r -> assertTrue(r.isConsistent()));
  }

  @Test void independentDescriptionTargetsDoNotBecomeOneWitness() throws Exception {
    ontology.define(List.of(Axiom.DisjointClasses(new String[]{"broadtest:Pattern", "broadtest:PatternTwo"})));
    var statement=description("Pattern");
    statement.getObservablesDescribed().add(new PairImpl<>(syntax("PatternTwo"),KimConceptStatement.DescriptionType.DESCRIBES));
    compile("Q",statement); withDl(r -> {assertTrue(r.isConsistent());assertTrue(r.isSatisfiable(type("Q")));});
  }

  @Test void knowledgeQualityAcceptsValidInheritedBroadDescriptionWithoutPhysicalDomainRestriction() {
    var knowledge=owl.getConcept("odo:KnowledgeDomain");
    var q=new KimConceptStatementImpl();q.setNamespace("broadtest");q.setUrn("Q");
    WorldviewDomainSupport.bind(owl,"broadtest",q,knowledge);
    var target=new KimConceptStatementImpl();target.setNamespace("broadtest");target.setUrn("Pattern");
    WorldviewDomainSupport.bind(owl,"broadtest",target,knowledge);
    compile("Q",description("Pattern"));
    ontology.define(List.of(Axiom.SubClass("Q","ChildQ")));
    var child=new KimConceptStatementImpl();child.setNamespace("broadtest");child.setUrn("ChildQ");
    WorldviewDomainSupport.bind(owl,"broadtest",child,knowledge);
    assertTrue(new OWLSemanticClauseSupport(owl).hasDescription(c("ChildQ")));
    assertDoesNotThrow(() -> compile("ChildQ",child));
  }

  @Test void inheritedBogusBroadRestrictionDoesNotSatisfyKnowledgeGrounding() {
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(type("Q"),
        f.getOWLObjectSomeValuesFrom(property("describes"),type("Identity"))));
    assertFalse(new OWLSemanticClauseSupport(owl).hasDescription(c("Q")));
    var q=new KimConceptStatementImpl();q.setNamespace("broadtest");q.setUrn("Q");
    WorldviewDomainSupport.bind(owl,"broadtest",q,owl.getConcept("odo:KnowledgeDomain"));
    assertTrue(assertThrows(KlabValidationException.class,()->compile("Q",q)).getMessage()
        .contains("KNOWLEDGE_QUALITY_GROUNDING_REQUIRED"));
  }

  @Test void documentValidationReportsUnsettledBearerContributionAsWarning() {
    compile("Q",description("Generic"));
    var service=org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    org.mockito.Mockito.when(service.owl()).thenReturn(owl);
    org.mockito.Mockito.when(service.resolveConcept("broadtest:Q")).thenReturn(c("Q"));
    org.mockito.Mockito.when(service.satisfiable(c("Q"))).thenReturn(true);
    var document=new KimOntologyImpl();document.setUrn("broadtest");
    var q=new KimConceptStatementImpl();q.setNamespace("broadtest");q.setUrn("Q");document.getStatements().add(q);
    var notifications=new org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator(service).validate(document);
    assertTrue(notifications.stream().anyMatch(n -> n.getLevel()==org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning
        && n.getMessage().contains("DESCRIPTION_BEARER_UNRESOLVED")));
  }

  @Test void subjectAndAgentTargetsInferTheirOwnBearerTypesWithoutLocalBounds() {
    for (var target:List.of("Tree","Actor")) {
      var source="Inferred"+target;declare(source,"Quality",SemanticType.OBSERVABLE,SemanticType.QUALITY);
      compile(source,description(target));
      assertTrue(ontology.getOWLOntology().containsAxiomIgnoreAnnotations(f.getOWLSubClassOfAxiom(type(source),
          f.getOWLObjectAllValuesFrom(property("isInherentTo"),type(target)))));
      assertTrue(WorldviewDeclarationSupport.unresolvedDescriptionBearers(owl,c(source)).isEmpty());
    }
  }

  @Test void subjectAndAgentTargetsPreserveNarrowerBearersAndRefineBroaderOnes() throws Exception {
    for(var names:List.of(List.of("Tree","Oak"),List.of("Actor","ActorChild"))) {
      var parent=names.get(0);var child=names.get(1);
      for(boolean narrower:List.of(true,false)) {
        var source="Refined"+parent+narrower;declare(source,"Quality",SemanticType.OBSERVABLE,SemanticType.QUALITY);
        bound(source,narrower?child:parent);compile(source,description(narrower?parent:child));
        assertTrue(ontology.getOWLOntology().getSubClassAxiomsForSubClass(type(source)).stream()
            .anyMatch(a->a.getSuperClass() instanceof OWLObjectAllValuesFrom all
                && all.getProperty().equals(property("isInherentTo"))
                && all.getFiller().containsConjunct(type(child))));
      }
    }
    withDl(r->assertTrue(r.isConsistent()));
  }

  @Test void disjointSubjectAndAgentTargetsRejectEvenWithoutTargetBearerRestrictions() {
    for(var names:List.of(List.of("Tree","Rock"),List.of("Actor","OtherActor"))) {
      var source="Conflict"+names.get(0);declare(source,"Quality",SemanticType.OBSERVABLE,SemanticType.QUALITY);
      bound(source,names.get(0));var before=axioms();
      var error=assertThrows(KlabValidationException.class,()->compile(source,description(names.get(1))));
      assertTrue(error.getMessage().contains("DESCRIPTION_BEARER_CONFLICT"));assertEquals(before,axioms());
    }
  }

  @Test void domainConfigurationTargetBearerContradictionRejectsWithoutDomainInherency() {
    bound("Pattern","Tree");bound("Pattern","Rock");var before=axioms();
    assertThrows(KlabValidationException.class,()->compile("Domain",description("Pattern")));
    assertEquals(before,axioms());
    assertTrue(new OWLSemanticClauseSupport(owl).bearerConstraints(c("Domain")).isEmpty());
  }

  @Test void substantialTargetHostIsCheckedIndependentlyNotSubstitutedAsSourceBearer() {
    bound("Tree","Rock");compile("Q",description("Tree"));
    assertTrue(ontology.getOWLOntology().containsAxiomIgnoreAnnotations(f.getOWLSubClassOfAxiom(type("Q"),
        f.getOWLObjectAllValuesFrom(property("isInherentTo"),type("Tree")))));
    assertFalse(ontology.getOWLOntology().containsAxiomIgnoreAnnotations(f.getOWLSubClassOfAxiom(type("Q"),
        f.getOWLObjectAllValuesFrom(property("isInherentTo"),type("Rock")))));
  }

  @Test void domainConfigurationRetainsIndependentWitnessesAndUnresolvedTargetEvidence() throws Exception {
    owl.manager.addAxioms(ontology.getOWLOntology(),Set.of(
        f.getOWLSubClassOfAxiom(type("Pattern"),f.getOWLObjectSomeValuesFrom(property("isInherentTo"),type("Tree"))),
        f.getOWLSubClassOfAxiom(type("Pattern"),f.getOWLObjectSomeValuesFrom(property("isInherentTo"),type("Rock")))));
    compile("Domain",description("Pattern"));
    assertTrue(new OWLSemanticClauseSupport(owl).bearerConstraints(c("Domain")).isEmpty());
    assertTrue(WorldviewDeclarationSupport.unresolvedDescriptionBearers(owl,c("Domain")).stream()
        .anyMatch(message->message.contains("INDEPENDENT_TARGET_UNRESOLVED")));
    withDl(r->{assertTrue(r.isConsistent());assertTrue(r.isSatisfiable(type("Domain")));});
  }

  @Test void domainConfigurationRejectsInheritedZeroCardinalityWithoutTransferringBearers() {
    owl.manager.addAxiom(ontology.getOWLOntology(),f.getOWLSubClassOfAxiom(type("PatternTwo"),
        f.getOWLObjectMaxCardinality(0,property("isInherentTo"),type("Tree"))));
    ontology.define(List.of(Axiom.SubClass("PatternTwo","Pattern")));
    bound("Pattern","Tree");var before=axioms();
    assertThrows(KlabValidationException.class,()->compile("Domain",description("Pattern")));
    assertEquals(before,axioms());
  }

  @Test void domainConfigurationUsesExistingReasonerForDlOnlyCardinalityConflict() throws Exception {
    owl.manager.addAxioms(ontology.getOWLOntology(),Set.of(
        f.getOWLSubClassOfAxiom(type("Pattern"),f.getOWLObjectMaxCardinality(1,property("isInherentTo"),f.getOWLThing())),
        f.getOWLSubClassOfAxiom(type("Pattern"),f.getOWLObjectSomeValuesFrom(property("isInherentTo"),type("Tree"))),
        f.getOWLSubClassOfAxiom(type("Pattern"),f.getOWLObjectSomeValuesFrom(property("isInherentTo"),type("Rock")))));
    withDl(r->{
      assertFalse(r.isSatisfiable(type("Pattern")));
      try {
        var field=OWL.class.getDeclaredField("reasoner");field.setAccessible(true);field.set(owl,r);
        var before=axioms();assertThrows(KlabValidationException.class,()->compile("Domain",description("Pattern")));
        assertEquals(before,axioms());
      } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
    });
  }
}
