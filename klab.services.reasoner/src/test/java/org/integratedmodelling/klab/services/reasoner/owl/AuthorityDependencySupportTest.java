package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class AuthorityDependencySupportTest {
  static final class World implements AutoCloseable {
    final OWL owl; final Ontology ontology; final OWLDataFactory factory;
    final org.semanticweb.owlapi.reasoner.OWLReasoner reasoner;
    World(java.util.function.BiConsumer<World,Set<OWLAxiom>> extra) throws Exception {
      Scope scope=(Scope)java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),new Class<?>[]{Scope.class},(p,m,a)->null);
      owl=new OWL(scope);owl.manager=OWLManager.createOWLOntologyManager();ontology=owl.requireOntology("w");factory=owl.manager.getOWLDataFactory();
      for(var name:List.of("Tree","Special","Family","AuthorityRoot","OtherRoot","Code","Negative","Combined","BoundaryA","BoundaryB","BoundaryC"))
        ontology.define(List.of(Axiom.ClassAssertion(name,
            Set.of("Family","AuthorityRoot","OtherRoot","Code","BoundaryA","BoundaryB","BoundaryC").contains(name)
                ?EnumSet.of(SemanticType.IDENTITY,SemanticType.PREDICATE)
                :EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE))));
      var axioms=new HashSet<OWLAxiom>();
      axioms.add(factory.getOWLSubClassOfAxiom(c("Code"),c("AuthorityRoot")));
      extra.accept(this,axioms);owl.manager.addAxioms(ontology.getOWLOntology(),axioms);
      reasoner=new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createNonBufferingReasoner(ontology.getOWLOntology());
      var merged=OWL.class.getDeclaredField("mergedReasonerOntology");merged.setAccessible(true);merged.set(owl,ontology);
      var field=OWL.class.getDeclaredField("reasoner");field.setAccessible(true);field.set(owl,reasoner);
    }
    OWLClass c(String name){return owl.getOWLClass(ontology.getConcept(name));}
    AuthorityBindings.Binding binding(String name,String root) {
      return new AuthorityBindings.Binding(new Authority.ConfigurationRequest("w",name,"w:"+root,Map.of("urn","provider")),
          org.mockito.Mockito.mock(Authority.class),"bridge");
    }
    AuthorityDependencySupport.Closure closure(String...terms){return new AuthorityDependencySupport(owl).closure(List.of(terms),List.of(binding("TAXA","AuthorityRoot")));}
    public void close(){reasoner.dispose();}
  }
  @Test void disconnectedAuthorityDoesNotTaintUnrelatedTermsAndUnknownIsNotIndependent() throws Exception {
    try(var w=new World((world,axioms)->{})) {
      assertTrue(w.closure("w:Tree").known());assertTrue(w.closure("w:Tree").authorities().isEmpty());
      assertFalse(w.closure("missing:Tree").known());assertFalse(w.closure().known());
    }
  }
  @Test void collectiveConceptUsesTheExistingOwlClassWithoutAcceptingObservableWrappers() throws Exception {
    try(var w=new World((world,axioms)->{})) {
      assertEquals(w.closure("w:Tree"),w.closure("each w:Tree"));
      assertEquals(w.closure("w:AuthorityRoot"),w.closure("each w:AuthorityRoot"));
      for(var invalid:List.of("each missing:Tree","each each w:Tree","any w:Tree","w:Tree named local"))
        assertFalse(w.closure(invalid).known(),invalid);
    }
  }
  @Test void inheritedBooleanAndNegativeSemanticConstraintsRetainAuthorityDependency() throws Exception {
    try(var w=new World((world,axioms)->{
      var p=world.factory.getOWLObjectProperty(IRI.create("urn:test:hasIdentity"));
      axioms.add(world.factory.getOWLSubClassOfAxiom(world.c("Tree"),world.factory.getOWLObjectSomeValuesFrom(p,world.c("Code"))));
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Negative"),world.factory.getOWLObjectComplementOf(world.c("Code"))));
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Combined"),world.factory.getOWLObjectIntersectionOf(world.c("Tree"),world.c("Negative"))));
    })) {
      for(var term:List.of("w:Tree","w:Negative","w:Combined")) {
        assertTrue(w.closure(term).known());assertEquals(Set.of("TAXA"),w.closure(term).authorities(),term);
      }
    }
  }
  @Test void enumerationIncludesPotentialAuthorityChildrenEvenWhenBotModuleWouldDropIncomingSubclass() throws Exception {
    try(var w=new World((world,axioms)->axioms.add(world.factory.getOWLSubClassOfAxiom(world.c("AuthorityRoot"),world.c("Family"))))) {
      assertEquals(Set.of("TAXA"),w.closure("w:Family").authorities());
      assertTrue(w.closure("w:Tree").authorities().isEmpty());
    }
  }
  @Test void combinedBoundaryExtensionCannotBeCertifiedIndependentBeforeAnyQualifyingIdentityExists() throws Exception {
    try(var w=new World((world,axioms)-> {
      axioms.add(world.factory.getOWLSubClassOfAxiom(
          world.factory.getOWLObjectIntersectionOf(world.c("BoundaryA"),world.c("BoundaryB")),world.c("Family")));
    })) {
      var binding=new AuthorityBindings.Binding(new Authority.ConfigurationRequest("w","TAXA","w:AuthorityRoot",
          Map.of("urn","provider","semanticBoundaries",Map.of("A","w:BoundaryA","B","w:BoundaryB"))),
          org.mockito.Mockito.mock(Authority.class),"bridge");
      assertFalse(w.owl.getSuperClasses(w.c("BoundaryA"),false).containsEntity(w.c("Family")));
      assertFalse(w.owl.getSuperClasses(w.c("BoundaryB"),false).containsEntity(w.c("Family")));
      assertTrue(w.owl.getSubClasses(w.c("Family"),false).entities().filter(c->!c.isOWLNothing()).findAny().isEmpty());
      var before=new AuthorityDependencySupport(w.owl).closure(List.of("w:Family"),List.of(binding));
      assertTrue(before.known());
      // These are the exact allowed additions used by AuthorityIdentityResolver: a fresh class
      // receives both mapped boundary parents plus the configured root (no external axiom kind).
      var generated=w.owl.requireOntology(AuthorityBindings.ontologyId(binding),OWL.INTERNAL_ONTOLOGY_PREFIX);
      generated.define(List.of(Axiom.ClassAssertion("FutureCode",EnumSet.of(SemanticType.IDENTITY,SemanticType.PREDICATE,
          SemanticType.AUTHORITY_IDENTITY)),Axiom.SubClass("w:AuthorityRoot","FutureCode"),
          Axiom.SubClass("w:BoundaryA","FutureCode"),Axiom.SubClass("w:BoundaryB","FutureCode")));
      w.owl.registerWithReasoner(generated);w.owl.flushReasoner();
      var future=w.owl.getOWLClass(generated.getConcept("FutureCode"));
      assertTrue(w.owl.getSuperClasses(future,false).containsEntity(w.c("Family")),
          "A real valid future provider identity changes the enumerated family");
      assertEquals(Set.of("TAXA"),before.authorities(),
          "Before materialization, combined mapped boundaries must already taint enumeration");
    }
  }

  @Test void inconsistentMaximumCannotCertifyIndependenceForAValidSmallerBoundaryCombination() throws Exception {
    try(var w=new World((world,axioms)-> {
      axioms.add(world.factory.getOWLDisjointClassesAxiom(world.c("BoundaryA"),world.c("BoundaryB")));
      axioms.add(world.factory.getOWLSubClassOfAxiom(
          world.factory.getOWLObjectIntersectionOf(world.c("BoundaryA"),world.c("BoundaryC")),world.c("Family")));
    })) {
      var binding=new AuthorityBindings.Binding(new Authority.ConfigurationRequest("w","TAXA","w:AuthorityRoot",
          Map.of("urn","provider","semanticBoundaries",Map.of("A","w:BoundaryA","B","w:BoundaryB","C","w:BoundaryC"))),
          org.mockito.Mockito.mock(Authority.class),"bridge");
      assertFalse(w.owl.isSatisfiable(w.factory.getOWLObjectIntersectionOf(w.c("BoundaryA"),w.c("BoundaryB"),w.c("BoundaryC"))));
      assertTrue(w.owl.isSatisfiable(w.factory.getOWLObjectIntersectionOf(w.c("AuthorityRoot"),w.c("BoundaryA"),w.c("BoundaryC"))));
      var before=new AuthorityDependencySupport(w.owl).closure(List.of("w:Family"),List.of(binding));
      assertTrue(before.known());assertEquals(Set.of("TAXA"),before.authorities(),
          "Inconsistent maximum parents cannot erase valid qualifying subsets");
    }
  }

  @Test void multipleKnownAuthorityParentsCanCombineIntoAFutureMatchingIdentity() throws Exception {
    try(var w=new World((world,axioms)-> {
      var binding=world.binding("TAXA","AuthorityRoot");
      var generated=world.owl.requireOntology(AuthorityBindings.ontologyId(binding),OWL.INTERNAL_ONTOLOGY_PREFIX);
      generated.define(List.of(Axiom.ClassAssertion("ParentA",EnumSet.of(SemanticType.IDENTITY,SemanticType.AUTHORITY_IDENTITY)),
          Axiom.ClassAssertion("ParentB",EnumSet.of(SemanticType.IDENTITY,SemanticType.AUTHORITY_IDENTITY)),
          Axiom.SubClass("w:AuthorityRoot","ParentA"),Axiom.SubClass("w:AuthorityRoot","ParentB")));
      world.ontology.addImport(generated);
      axioms.add(world.factory.getOWLSubClassOfAxiom(world.factory.getOWLObjectIntersectionOf(
          world.owl.getOWLClass(generated.getConcept("ParentA")),world.owl.getOWLClass(generated.getConcept("ParentB"))),world.c("Family")));
    })) {
      var before=w.closure("w:Family");assertTrue(before.known());assertEquals(Set.of("TAXA"),before.authorities());
      var generated=w.owl.getOntology(AuthorityBindings.ontologyId(w.binding("TAXA","AuthorityRoot")));
      generated.define(List.of(Axiom.ClassAssertion("FutureCode",EnumSet.of(SemanticType.IDENTITY,SemanticType.AUTHORITY_IDENTITY)),
          Axiom.SubClass("ParentA","FutureCode"),Axiom.SubClass("ParentB","FutureCode")));
      w.owl.flushReasoner();
      assertTrue(w.owl.getSuperClasses(w.owl.getOWLClass(generated.getConcept("FutureCode")),false).containsEntity(w.c("Family")));
    }
  }

  @Test void importedAuthorityIriBeforeProviderMaterializationCannotBeMistakenForIndependence() throws Exception {
    try(var w=new World((world,axioms)-> {
      var binding=world.binding("TAXA","AuthorityRoot");
      String base=OWL.INTERNAL_ONTOLOGY_PREFIX+"/"+AuthorityBindings.ontologyId(binding)+"#Unmaterialized";
      var future=world.factory.getOWLClass(IRI.create(base));
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Negative"),world.factory.getOWLObjectComplementOf(future)));
    })) {
      assertNull(w.owl.getOntology(AuthorityBindings.ontologyId(w.binding("TAXA","AuthorityRoot"))));
      assertEquals(Set.of("TAXA"),w.closure("w:Negative").authorities());
    }
  }

  @Test void equivalentRootsAndNegativeConstraintsRemainExplicitSemanticDependencies() throws Exception {
    try(var w=new World((world,axioms)-> {
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Family"),world.c("AuthorityRoot")));
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Negative"),world.factory.getOWLObjectComplementOf(world.c("AuthorityRoot"))));
    })) {
      assertEquals(Set.of("TAXA"),w.closure("w:Family").authorities());
      assertEquals(Set.of("TAXA"),w.closure("w:Negative").authorities());
    }
  }

  @Test void knownMembersAndMultipleFamiliesArePartOfTheProofAndClosureQuarantineFailsClosed() throws Exception {
    try(var w=new World((world,axioms)->{})) {
      var support=new AuthorityDependencySupport(w.owl);
      var result=support.closure(List.of("w:Code","w:OtherRoot"),List.of(w.binding("TAXA","AuthorityRoot"),w.binding("OTHER","OtherRoot")));
      assertTrue(result.known());assertEquals(Set.of("TAXA","OTHER"),result.authorities());
      w.owl.quarantineDerivedClosure();assertFalse(w.closure("w:Tree").known());
    }
  }
}
