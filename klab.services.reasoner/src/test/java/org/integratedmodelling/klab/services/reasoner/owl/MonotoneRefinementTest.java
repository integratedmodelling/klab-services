package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.junit.jupiter.api.Test;

class MonotoneRefinementTest {
  static class Fixture extends ClassifierNativeAdmissionTest.Fixture {
    Fixture()throws Exception{
      owl.requireOntology("odo","http://integratedmodelling.org").define(List.of(
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasCompresent")));
      ClassifierNativeAdmissionTest.field(reasoner,ReasonerService.class,"conceptSyntax",new org.integratedmodelling.klab.services.reasoner.internal.ConceptSyntaxCache());
    }
    Concept build(String attribute){return reasoner.declareConcept(bearer(attribute));}
    boolean entails(Concept child,Concept parent){return owl.isEntailed(f.getOWLSubClassOfAxiom(owl.getOWLClass(child),owl.getOWLClass(parent)));}
    long owned(){return owl.manager.ontologies().flatMap(o->o.axioms()).filter(a->a.annotations().anyMatch(x->x.getProperty().getIRI().equals(MonotoneRefinementSupport.PROVENANCE))).count();}
    void attribute(String name,String parent){declare(name,SemanticType.ATTRIBUTE,SemanticType.PREDICATE);sub(name,parent);register(leaf(name));}
  }
  @Test void independentConstructionInEitherOrderCreatesOnlyDirectedExportableLinks()throws Exception{
    for(boolean specificFirst:List.of(false,true))try(var t=new Fixture()){
      long revision=t.reasoner.knowledgeRevision();Concept child,parent;
      if(specificFirst){child=t.build("Concrete");parent=t.build("Category");}
      else{parent=t.build("Category");child=t.build("Concrete");}
      assertTrue(t.entails(child,parent));assertFalse(t.entails(parent,child));
      assertEquals(1,t.owned());assertEquals(revision,t.reasoner.knowledgeRevision());
      t.build("Concrete");t.build("Category");assertEquals(1,t.owned());
      assertTrue(t.owl.getOntology(OWL.INTERNAL_ONTOLOGY_ID).getOWLOntology().axioms()
          .anyMatch(a->a instanceof org.semanticweb.owlapi.model.OWLSubClassOfAxiom && a.annotations().anyMatch(x->x.getProperty().getIRI().equals(MonotoneRefinementSupport.PROVENANCE))));
      assertFalse(t.owl.getOntology(OWL.INTERNAL_ONTOLOGY_ID).getOWLOntology().axioms()
          .anyMatch(a->a instanceof org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom));
    }
  }
  @Test void declarationStagingDefersProofAndClearsCachedNegativeHierarchyAnswers()throws Exception{
    try(var t=new Fixture()){
      var matcher=new SemanticMatcher(t.reasoner);ClassifierNativeAdmissionTest.field(t.reasoner,ReasonerService.class,"semanticMatcher",matcher);
      Concept child,parent;
      try(var stage=t.owl.stageDeclarations()){child=t.build("Concrete");parent=t.build("Category");assertEquals(0,t.owned());}
      assertFalse(t.reasoner.is(child,parent));assertEquals(-1,matcher.assertedDistance(child,parent));
      long revision=t.reasoner.knowledgeRevision();t.owl.monotoneRefinements().refresh(t.reasoner);
      assertTrue(t.reasoner.is(child,parent));assertTrue(matcher.assertedDistance(child,parent)>=0);
      assertEquals(revision,t.reasoner.knowledgeRevision());
    }
  }
  @Test void subtypeChainsWorkWhileSiblingAndUnrelatedAttributesDoNotRefineEachOther()throws Exception{
    try(var t=new Fixture()){
      t.attribute("Middle","Category");t.sub("Concrete","Middle");t.attribute("Sibling","Category");
      var specific=t.build("Concrete");var sibling=t.build("Sibling");var broad=t.build("Category");var middle=t.build("Middle");
      assertTrue(t.entails(specific,middle));assertTrue(t.entails(middle,broad));assertTrue(t.entails(specific,broad));
      assertFalse(t.entails(sibling,specific));assertFalse(t.entails(specific,sibling));
    }
  }
  @Test void changedOrRemovedClausesAndRemovedAttributesDoNotAcquireRefinementEdges()throws Exception{
    try(var t=new Fixture()){
      var general=t.bearer("Category");general.setCompresent(t.leaf("Other"));general.resetDefinition();t.register(general);
      var required=t.reasoner.declareConcept(general);
      var child=t.build("Concrete");assertFalse(t.entails(child,required));
      var changed=t.bearer("Concrete");changed.setCompresent(t.leaf("Region"));changed.resetDefinition();t.register(changed);
      assertFalse(t.entails(t.reasoner.declareConcept(changed),required));
      assertFalse(t.entails(t.reasoner.declareConcept(t.leaf("Region")),required));
      var unchanged=t.bearer("Concrete");unchanged.setCompresent(t.leaf("Other"));unchanged.resetDefinition();t.register(unchanged);
      assertTrue(t.entails(t.reasoner.declareConcept(unchanged),required));
    }
  }
  @Test void excludedOperatorsAndRelationshipPositionsNeverRegisterProfiles()throws Exception{
    try(var t=new Fixture()){
      var child=t.build("Concrete");var parent=t.build("Category");t.owl.monotoneRefinements().reset(true);
      var variants=new ArrayList<KimConceptImpl>();
      var negated=t.bearer("Concrete");negated.setNegated(true);variants.add(negated);
      var transformed=t.bearer("Concrete");transformed.setSemanticModifier(org.integratedmodelling.klab.api.lang.UnarySemanticOperator.COUNT);variants.add(transformed);
      var any=t.bearer("Concrete");any.getType().add(SemanticType.ANY);variants.add(any);
      var endpoint=t.bearer("Concrete");endpoint.setRelationshipSource(t.leaf("Region"));variants.add(endpoint);
      for(var variant:variants)t.owl.monotoneRefinements().register(child,variant,t.reasoner);
      t.owl.monotoneRefinements().register(parent,t.bearer("Category"),t.reasoner);
      assertEquals(0,t.owned());assertFalse(t.entails(child,parent));
    }
  }
  @Test void authoritativeDenialCannotBeOverriddenByAnOwlAttributeParentAndRefreshRetractsOldProof()throws Exception{
    try(var t=new Fixture()){
      doReturn(false).when(t.reasoner).is(eq(t.concept("Concrete")),eq(t.concept("Category")));
      var child=t.build("Concrete");var parent=t.build("Category");assertFalse(t.entails(child,parent));
      doReturn(true).when(t.reasoner).is(eq(t.concept("Concrete")),eq(t.concept("Category")));
      var accepted=ReasonerService.class.getDeclaredMethod("authorityKnowledgeChanged");accepted.setAccessible(true);accepted.invoke(t.reasoner);
      t.owl.monotoneRefinements().refresh(t.reasoner);assertTrue(t.entails(child,parent));
      doReturn(false).when(t.reasoner).is(eq(t.concept("Concrete")),eq(t.concept("Category")));
      var changed=ReasonerService.class.getDeclaredMethod("authorityKnowledgeChanged");changed.setAccessible(true);changed.invoke(t.reasoner);
      assertFalse(t.entails(child,parent));t.owl.monotoneRefinements().refresh(t.reasoner);
      assertFalse(t.entails(child,parent));assertEquals(0,t.owned());
    }
  }
  @Test void contradictoryDerivedBatchIsRetractedBeforeReturningEligibility()throws Exception{
    try(var t=new Fixture()){
      Concept child,parent;
      try(var stage=t.owl.stageDeclarations()){child=t.build("Concrete");parent=t.build("Category");}
      t.add(t.f.getOWLDisjointClassesAxiom(t.owl.getOWLClass(child),t.owl.getOWLClass(parent)));
      for(var ontology:t.owl.getOntologies(true))if(ontology!=t.ontology)t.owl.registerWithReasoner(ontology);
      assertTrue(t.owl.isSatisfiable(t.owl.getOWLClass(child)));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,()->t.owl.monotoneRefinements().refresh(t.reasoner));
      assertEquals(0,t.owned());assertTrue(t.owl.isSatisfiable(t.owl.getOWLClass(child)));assertFalse(t.entails(child,parent));
    }
  }
  @Test void ordinaryCachedStringResolutionCannotRetainAnEarlierNegativeAnswer()throws Exception{
    try(var t=new Fixture()){
      var specific=t.bearer("Concrete");var general=t.bearer("Category");
      var child=t.reasoner.resolveConcept(specific.getUrn());
      var parent=t.reasoner.resolveConcept(general.getUrn());
      assertTrue(t.reasoner.is(child,parent));
      assertEquals(child.getUrn(),t.reasoner.resolveConcept(specific.getUrn()).getUrn());
      assertTrue(t.entails(child,parent));
    }
  }
  @Test void multipleIndependentAttributeReplacementsPreserveTheirPositionsAndOtherClauses()throws Exception{
    try(var t=new Fixture()){
      t.declare("Kind",SemanticType.ATTRIBUTE,SemanticType.PREDICATE);t.concept("Kind").setAbstract(true);
      t.concept("Kind").getType().add(SemanticType.ABSTRACT);
      t.concept("Kind").getMetadata().put(CoreOntology.NS.BASE_DECLARATION,"true");
      t.concept("Kind").getMetadata().put(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasAttribute");
      t.owl.setApplicableObservables(t.concept("Kind"),List.of(t.concept("Region")),t.ontology);
      t.register(t.leaf("Kind"));t.attribute("Kind1","Kind");
      var general=t.bearer("Category");general.getTraits().add(t.leaf("Kind"));general.resetDefinition();t.register(general);
      var specific=t.bearer("Concrete");specific.getTraits().add(t.leaf("Kind1"));specific.resetDefinition();t.register(specific);
      var child=t.reasoner.declareConcept(specific);var parent=t.reasoner.declareConcept(general);
      assertTrue(t.entails(child,parent));assertFalse(t.entails(parent,child));
      var removed=t.bearer("Concrete");assertFalse(t.entails(t.reasoner.declareConcept(removed),parent));
    }
  }

  @Test void aFirstFlushFailureRollsBackInsertedAndImportedLinks()throws Exception{
    try(var t=new Fixture()){
      Concept child,parent;
      try(var stage=t.owl.stageDeclarations()){child=t.build("Concrete");parent=t.build("Category");}
      var failing=spy(t.owl);var helper=new MonotoneRefinementSupport(failing);
      helper.register(child,t.bearer("Concrete"),t.reasoner);
      doThrow(new IllegalStateException("injected first flush")).doCallRealMethod().when(failing).flushReasoner();
      assertThrows(IllegalStateException.class,()->helper.register(parent,t.bearer("Category"),t.reasoner));
      assertEquals(0,t.owned());assertFalse(t.entails(child,parent));
      helper.reset(true);assertEquals(0,t.owned());
    }
  }
  @Test void lifecycleAndHelperResetShareOneLockOrder()throws Exception{
    try(var t=new Fixture()){
      var helper=t.owl.monotoneRefinements();var gate=new java.util.concurrent.CountDownLatch(1);
      var pool=java.util.concurrent.Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
      try {
        var lifecycle=pool.submit(()->{gate.await();for(int i=0;i<200;i++)synchronized(t.owl){helper.reset(false);}return null;});
        var concurrent=pool.submit(()->{gate.await();for(int i=0;i<200;i++)helper.reset(false);return null;});
        gate.countDown();lifecycle.get(5,java.util.concurrent.TimeUnit.SECONDS);concurrent.get(5,java.util.concurrent.TimeUnit.SECONDS);
      } finally {pool.shutdownNow();}
    }
  }

  @Test void unchangedIdentityAndRolePredicatesRemainConstraintsRatherThanReplacementSites()throws Exception{
    try(var t=new Fixture()){
      t.owl.requireOntology("odo","http://integratedmodelling.org").define(List.of(
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasIdentity")));
      for(String root:List.of("RootA","RootB")){
        t.concept(root).getMetadata().put(CoreOntology.NS.BASE_DECLARATION,"true");
        t.concept(root).getMetadata().put(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasIdentity");
      }
      t.declare("Role",SemanticType.ROLE,SemanticType.PREDICATE);
      t.owl.setApplicableObservables(t.concept("Role"),List.of(t.concept("Region")),t.ontology);
      for(String name:List.of("A","B","Role"))t.register(t.leaf(name));
      var general=t.bearer("Category");general.getTraits().add(t.leaf("A"));general.getRoles().add(t.leaf("Role"));general.resetDefinition();t.register(general);
      var specific=t.bearer("Concrete");specific.getTraits().add(t.leaf("A"));specific.getRoles().add(t.leaf("Role"));specific.resetDefinition();t.register(specific);
      var parent=t.reasoner.declareConcept(general);var child=t.reasoner.declareConcept(specific);assertTrue(t.entails(child,parent));
      var changed=t.bearer("Concrete");changed.getTraits().add(t.leaf("B"));changed.getRoles().add(t.leaf("Role"));changed.resetDefinition();t.register(changed);
      assertFalse(t.entails(t.reasoner.declareConcept(changed),parent));
    }
  }

  @Test void authorityRetractionFlushFailureCannotServeAWarmedPositive()throws Exception{
    try(var t=new Fixture()){
      var child=t.build("Concrete");var parent=t.build("Category");
      assertTrue(t.reasoner.is(child,parent));long before=t.reasoner.knowledgeRevision();
      var failing=spy(t.owl);
      ClassifierNativeAdmissionTest.field(t.reasoner,ReasonerService.class,"owl",failing);
      // The spy shares the actual native manager. Give it a separately owned proof registry.
      var helper=new MonotoneRefinementSupport(failing);
      helper.register(child,t.bearer("Concrete"),t.reasoner);helper.register(parent,t.bearer("Category"),t.reasoner);
      ClassifierNativeAdmissionTest.field(failing,OWL.class,"monotoneRefinements",helper);
      doReturn(false).when(t.reasoner).is(eq(t.concept("Concrete")),eq(t.concept("Category")));
      doThrow(new IllegalStateException("injected authority reset flush")).when(failing).flushReasoner();
      var changed=ReasonerService.class.getDeclaredMethod("authorityKnowledgeChanged");changed.setAccessible(true);
      assertThrows(java.lang.reflect.InvocationTargetException.class,()->changed.invoke(t.reasoner));
      assertTrue(t.reasoner.knowledgeRevision()>before);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->t.reasoner.is(child,parent));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->failing.isEntailed(t.f.getOWLSubClassOfAxiom(t.owl.getOWLClass(child),t.owl.getOWLClass(parent))));
      // Retired actor is deliberately not repaired in place.
    }
  }

  @Test void healthyAcceptedReplacementRoutesBeforeRetiredHostClosure()throws Exception{
    try(var t=new Fixture()){
      var child=t.build("Concrete");var parent=t.build("Category");
      var replacement=mock(org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime.class);
      when(replacement.semanticDistanceAtRevision(child,parent,null,3)).thenReturn(7);
      when(replacement.assertedDistanceAtRevision(child,parent,3)).thenReturn(2);
      ClassifierNativeAdmissionTest.field(t.reasoner,ReasonerService.class,"candidateRuntime",replacement);
      t.owl.quarantineDerivedClosure();
      assertEquals(7,t.reasoner.semanticDistance(child,parent,null,3));
      assertEquals(2,t.reasoner.assertedDistanceAtRevision(child,parent,3));
    }
  }

}
