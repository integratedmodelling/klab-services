package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

/** Real OWL/HermiT and public declaration/validation; Resources only supplies frozen syntax. */
class ClassifierNativeAdmissionTest {
  static void field(Object target,Class<?> owner,String name,Object value)throws Exception{
    var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  static class Fixture extends IdentityRequirementSupportTest.Fixture {
    final ReasonerService reasoner=mock(ReasonerService.class,CALLS_REAL_METHODS);
    final ServiceScope scope=mock(ServiceScope.class);
    final ResourcesService resources=mock(ResourcesService.class);
    final Map<String,KimConcept> syntax=new HashMap<>();
    Fixture()throws Exception{
      owl.requireOntology("odo","http://integratedmodelling.org").define(List.of(
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("isInherentTo"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("appliesTo"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasAttribute"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasRole")));
      declare("Region",SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
      declare("Other",SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
      for(var name:List.of("Input","Result")){
        declare(name,SemanticType.QUALITY,SemanticType.NUMEROSITY,SemanticType.QUANTIFIABLE,SemanticType.OBSERVABLE);
        restriction(name,"isInherentTo",type("Region"));
      }
      for(var name:List.of("Category","Concrete"))declare(name,SemanticType.ATTRIBUTE,SemanticType.PREDICATE);
      concept("Category").setAbstract(true);concept("Category").getType().add(SemanticType.ABSTRACT);
      concept("Category").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.BASE_DECLARATION,"true");
      concept("Category").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasAttribute");
      sub("Concrete","Category");
      owl.setApplicableObservables(concept("Category"),List.of(concept("Region")),ontology);
      ready();field(owl,OWL.class,"mergedReasonerOntology",ontology);field(owl,OWL.class,"scope",scope);
      field(reasoner,ReasonerService.class,"owl",owl);
      field(reasoner,ReasonerService.class,"knowledgeRevision",new java.util.concurrent.atomic.AtomicLong(3));
      field(reasoner,ReasonerService.class,"knowledgeReady",true);
      field(reasoner,ReasonerService.class,"worldview",mock(Worldview.class));
      for(var name:List.of("concepts","observables","subsumption"))field(reasoner,ReasonerService.class,name,com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      field(reasoner,ReasonerService.class,"coreConceptPeers",new HashMap<>());
      field(reasoner,ReasonerService.class,"declarationDomains",new HashMap<>());
      field(reasoner,ReasonerService.class,"loadedOntologyDiagnostics",new HashMap<>());
      var state=new org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState();state.settle(3,true);
      field(reasoner,ReasonerService.class,"semanticValidationState",state);
      doReturn(scope).when(reasoner).serviceScope();when(scope.getService(ResourcesService.class)).thenReturn(resources);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(resources.declareConcept(anyString())).thenAnswer(i->{var c=syntax.get(i.getArgument(0));return c==null?null:(KimConcept)KimConceptImpl.copyValue(c);});
      for(var name:List.of("Region","Other","Input","Result","Category","Concrete"))register(leaf(name));
    }
    KimConceptImpl register(KimConceptImpl c){c.setNamespace("audit");c.finalizeDefinition();syntax.put(c.getUrn(),c);return c;}
    KimConceptImpl bearer(String predicate){var c=leaf("Region");c.getTraits().add(leaf(predicate));return register(c);}
    KimObservableImpl request(String head,KimConcept bearer){var c=leaf(head);c.setInherent(bearer);register(c);var o=new KimObservableImpl();o.setNamespace("audit");o.setSemantics(c);var bare=new KimConceptImpl();bare.setName("audit:"+head);bare.setType(c.getType());bare.setInherent(leaf("Region"));register(bare);return o;}
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(KimObservable out,KimObservable input){
      return validate(out,List.of(input),true);
    }
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(KimObservable out,List<KimObservable> inputs,boolean executable){
      var m=new KimModelImpl();m.setNamespace("models");m.setUrn("models.parent");m.getObservables().add(out);m.getDependencies().addAll(inputs);
      var ns=new KimNamespaceImpl();ns.setUrn("models");ns.setSourceCode("frozen native fixture");ns.getStatements().add(m);
      var q=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(ns,"1");q.setExecutableModels(executable);q.setKnowledgeRevision(3);
      return reasoner.validateDocument(q,scope);
    }
  }
  @Test void concreteNativeBearerIsAdmittedWhileUnbridgedDifferentBearerIsRejected()throws Exception{
    try(var t=new Fixture()){
      var nativeBearer=t.bearer("Concrete");var output=t.request("Result",nativeBearer);var input=t.request("Input",nativeBearer);
      var declared=t.reasoner.declareObservable(input);assertNotNull(declared);assertFalse(declared.isAbstract());
      var valid=t.validate(output,input);assertTrue(valid.valid(),valid.getReason()+" "+valid.getNotifications());
      var invalid=t.validate(t.request("Result",t.register(t.leaf("Other"))),input);assertFalse(invalid.valid());
    }
  }
  @Test void nativeSubtypeProofIsSeparateFromAbstractStatusOfClauseFillers()throws Exception{
    try(var t=new Fixture()){
      var response=t.validate(t.request("Result",t.bearer("Concrete")),t.request("Input",t.bearer("Category")));
      assertFalse(response.valid(),"An unproved native subtype remains unresolved: "+response.getNotifications());
      var unsupported=t.validate(t.request("Result",t.bearer("Concrete")),t.request("Input",t.bearer("Category")));
      assertFalse(unsupported.valid(),"Language qualification is not yet a native subtype certificate");
      // Test-only authored native subtype axiom. This does not claim a general generated-expression
      // implication rule, and it is not a computational cross-bearer bridge.
      var concrete=t.reasoner.declareConcept(t.bearer("Concrete"));
      var category=t.reasoner.declareConcept(t.bearer("Category"));
      t.add(t.f.getOWLSubClassOfAxiom(t.owl.getOWLClass(concrete),t.owl.getOWLClass(category)));
      assertTrue(t.validate(t.request("Result",t.bearer("Concrete")),t.request("Input",t.bearer("Category"))).valid(),"An already established concrete native bearer discharges the detached obligation");
      var proved=t.validate(t.request("Result",t.bearer("Concrete")),t.request("Input",t.bearer("Category")));
      assertTrue(proved.valid(),proved.getReason()+" "+proved.getNotifications());

      var wrong=t.validate(t.request("Result",t.register(t.leaf("Other"))),t.request("Input",t.bearer("Category")));
      assertFalse(wrong.valid());
    }
  }
  @Test void oneDeferredOccurrenceCannotMakeUnrelatedAuthoringObligationsTemplates()throws Exception{
    try(var t=new Fixture()){
      var output=t.request("Result",t.bearer("Concrete"));
      var deferred=t.request("Input",t.bearer("Category"));
      var wrong=t.request("Input",t.register(t.leaf("Other")));
      var response=t.validate(output,List.of(deferred,wrong),false);
      assertFalse(response.valid(),response.getNotifications().toString());
      assertTrue(response.getNotifications().stream().anyMatch(n->n.getMessage().contains("MODEL_DEPENDENCY_")),response.getNotifications().stream().map(n->n.getMessage()).toList().toString());
    }
  }
  @Test void independentlyAbstractQualityHeadRemainsIneligible()throws Exception{
    try(var t=new Fixture()){
      t.concept("Input").setAbstract(true);t.concept("Input").getType().add(SemanticType.ABSTRACT);t.register(t.leaf("Input"));
      var input=t.request("Input",t.bearer("Category"));
      assertTrue(t.reasoner.declareConcept(input.getSemantics()).isAbstract());
      assertFalse(t.validate(t.request("Result",t.bearer("Concrete")),input).valid());
    }
  }

  @Test void generatedQualificationRestrictionsAreNecessaryAndDoNotEstablishExpressionSubsumption()throws Exception{
    try(var t=new Fixture()){
      var candidate=t.reasoner.declareConcept(t.bearer("Concrete"));
      var required=t.reasoner.declareConcept(t.bearer("Category"));
      // Include generated ontology axioms in HermiT's import closure: failure must not be a
      // missing-import artifact of the fixture.
      for(var ontology:t.owl.getOntologies(true))if(ontology!=t.ontology)t.owl.registerWithReasoner(ontology);
      t.dl.flush();
      var child=t.owl.getOWLClass(candidate);var parent=t.owl.getOWLClass(required);
      assertTrue(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("Concrete"),t.type("Category"))));
      assertTrue(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(child,t.type("Region"))));
      assertFalse(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(child,parent)),"Necessary restrictions alone are insufficient");
      System.out.println("NATIVE_PROOF_EXPRESSIONS: "+candidate.getUrn()+" -> "+required.getUrn());
      t.owl.manager.ontologies().flatMap(o->o.axioms()).filter(a->a instanceof org.semanticweb.owlapi.model.OWLSubClassOfAxiom)
          .filter(a->a.signature().anyMatch(e->e.equals(child)||e.equals(parent)))
          .map(Object::toString).sorted().forEach(a->System.out.println("NATIVE_PROOF_AXIOM: "+a));
    }
  }

  @Test void incrementalBuilderAndOrdinarySyntaxConstructionShareTheSameMissingCompoundParentLink()throws Exception{
    try(var t=new Fixture()){
      var original=t.reasoner.declareConcept(t.bearer("Category"));
      var rebuilt=org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder.create(original,t.reasoner,t.scope)
          .withTrait(t.concept("Concrete")).buildConcept();
      var ordinary=t.reasoner.declareConcept(t.bearer("Concrete"));
      assertEquals(ordinary.getUrn(),rebuilt.getUrn());
      assertEquals(t.owl.getOWLClass(ordinary),t.owl.getOWLClass(rebuilt));
      for(var ontology:t.owl.getOntologies(true))if(ontology!=t.ontology)t.owl.registerWithReasoner(ontology);
      t.dl.flush();
      assertFalse(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.owl.getOWLClass(rebuilt),t.owl.getOWLClass(original))));
    }
  }
  @Test void classifierGeneratedTypedRedeclarationUsesTheOrdinaryConceptBuilder()throws Exception{
    try(var t=new Fixture()){
      var source=t.request("Input",t.bearer("Category"));
      var prepared=DependencyExpansion.prepare(source,t.syntax::get);
      assertEquals(1,prepared.requests().size());
      var occurrence=prepared.requests().getFirst().identity();
      var expanded=prepared.apply(Map.of(occurrence,new DependencyExpansion.Classification(
          DependencyExpansion.Status.SUCCESS,List.of(t.leaf("Concrete")))));
      assertEquals(1,expanded.size());
      assertNull(expanded.getFirst().getSemantics().getUrn());
      // Resources is the parser boundary: provide its ordinary finalized parse without changing
      // the deliberately invalidated caller syntax.
      var parsed=((KimConceptImpl)expanded.getFirst().getSemantics()).mapSyntax(node->{
        node.finalizeDefinition();t.syntax.put(node.getUrn(),node);
      });
      var actual=t.reasoner.declareObservable(expanded.getFirst());
      assertNull(expanded.getFirst().getSemantics().getUrn());
      var expected=t.reasoner.declareObservable(t.request("Input",t.bearer("Concrete")));
      assertEquals(expected.getSemantics().getUrn(),actual.getSemantics().getUrn());
      assertEquals(t.owl.getOWLClass(expected.getSemantics()),t.owl.getOWLClass(actual.getSemantics()));
      assertEquals(source.isOptional(),prepared.requests().getFirst().originalObservable().isOptional());
      assertTrue(expanded.getFirst().isOptional());assertTrue(actual.isOptional());
    }
  }

  @Test void typedConceptDeclarationNormalizesItsDetachedMappedTree()throws Exception{
    try(var t=new Fixture()){
      var source=t.bearer("Category");var sourceUrn=source.getUrn();
      var mapped=source.mapSyntax(node->{if("audit:Category".equals(node.getName())){
        node.setName("audit:Concrete");node.setType(t.leaf("Concrete").getType());
      }});
      assertNull(mapped.getUrn());
      var parsed=mapped.mapSyntax(node->{node.finalizeDefinition();t.syntax.put(node.getUrn(),node);});
      var declared=t.reasoner.declareConcept(mapped);
      assertNotNull(declared);assertEquals(parsed.getUrn(),declared.getUrn());
      assertNull(mapped.getUrn());assertEquals(sourceUrn,source.getUrn());
    }
  }

}
