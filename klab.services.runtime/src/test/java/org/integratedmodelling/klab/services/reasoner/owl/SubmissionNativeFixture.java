package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;


import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;

class SubmissionOntologyFixture implements AutoCloseable {
    final OWL owl = new OWL(mock(Scope.class));
    final Ontology ontology;
    final OWLDataFactory f;
    OWLReasoner dl;
    SubmissionOntologyFixture() {
      owl.manager = OWLManager.createOWLOntologyManager(); f = owl.manager.getOWLDataFactory();
      ontology = owl.requireOntology("audit");
      for (var kind : List.of(SemanticType.SUBJECT, SemanticType.QUALITY, SemanticType.PROCESS))
        declare(kind.name(), kind, SemanticType.OBSERVABLE);
      // Self-identification tests use substantial heads; dependent targeting has its own suite.
      declare("SecondSubject", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      declare("ThirdSubject", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      for (var name : List.of("RootA", "RootB", "A", "B", "Both", "Wrong"))
        declare(name, SemanticType.IDENTITY, SemanticType.PREDICATE);
      concept("RootA").setAbstract(true); concept("RootB").setAbstract(true);
      sub("A", "RootA"); sub("B", "RootB"); sub("Both", "RootA"); sub("Both", "RootB");
    }
    ConceptImpl concept(String name) { return (ConceptImpl) ontology.getConcept(name); }
    OWLClass type(String name) { return owl.getOWLClass(concept(name)); }
    void declare(String name, SemanticType... flags) {
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.copyOf(List.of(flags)))));
    }
    void sub(String child, String parent) { add(f.getOWLSubClassOfAxiom(type(child), type(parent))); }
    void add(OWLAxiom axiom) { owl.manager.addAxiom(ontology.getOWLOntology(), axiom); }
    void require(String owner, OWLClassExpression filler) { restriction(owner, "requiresIdentity", filler); }
    void restriction(String owner, String property, OWLClassExpression filler) {
      add(f.getOWLSubClassOfAxiom(type(owner), f.getOWLObjectSomeValuesFrom(
          f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#" + property)), filler)));
    }
    void ready() throws Exception {
      dl = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createNonBufferingReasoner(ontology.getOWLOntology());
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, dl);
    }
    KimConceptImpl syntax(String head, String... identities) {
      var syntax = leaf(head); for (var identity : identities) syntax.getTraits().add(leaf(identity)); return syntax;
    }
    KimConceptImpl leaf(String name) {
      var syntax = new KimConceptImpl(); syntax.setName("audit:" + name);
      syntax.setType(EnumSet.copyOf(concept(name).getType())); return syntax;
    }
    ConceptImpl project(String head, String... identities) {
      return new IdentityRequirementSupport(owl).project(concept(head), syntax(head, identities));
    }
    public void close() { if (dl != null) dl.dispose(); }
  }

public class SubmissionNativeFixture extends SubmissionOntologyFixture {
  static void field(Object target,Class<?> owner,String name,Object value)throws Exception {var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}

    public final ReasonerService reasoner=mock(ReasonerService.class,CALLS_REAL_METHODS);
    final ServiceScope scope=mock(ServiceScope.class);
    public final ResourcesService resources=mock(ResourcesService.class);
    final Map<String,KimConcept> syntax=new HashMap<>();
    public SubmissionNativeFixture()throws Exception { this(false); }
    public SubmissionNativeFixture(boolean categoryOnInput)throws Exception{
      owl.requireOntology("odo","http://integratedmodelling.org").define(List.of(
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("isInherentTo"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("appliesTo"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasAttribute"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasRole"),
          org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasPurpose")));
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
      owl.setApplicableObservables(concept("Category"),categoryOnInput?List.of(concept("Region"),concept("Input")):List.of(concept("Region")),ontology);
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
    public void otherIsRegionSubtype() { sub("Other", "Region"); }
    public KimConceptImpl register(KimConceptImpl c){c.setNamespace("audit");c.finalizeDefinition();syntax.put(c.getUrn(),c);return c;}
    public KimConceptImpl bearer(String predicate){var c=leaf("Region");c.getTraits().add(leaf(predicate));return register(c);}
    public KimObservableImpl request(String head,KimConcept bearer){var c=leaf(head);c.setInherent(bearer);register(c);var o=new KimObservableImpl();o.setNamespace("audit");o.setSemantics(c);var bare=new KimConceptImpl();bare.setName("audit:"+head);bare.setType(c.getType());bare.setInherent(leaf("Region"));register(bare);return o;}
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(KimObservable out,KimObservable input){
      return validate(out,List.of(input),true);
    }
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(KimObservable out,List<KimObservable> inputs,boolean executable){
      var m=new KimModelImpl();m.setNamespace("models");m.setUrn("models.parent");m.getObservables().add(out);m.getDependencies().addAll(inputs);
      var ns=new KimNamespaceImpl();ns.setUrn("models");ns.setSourceCode("frozen native fixture");ns.getStatements().add(m);
      var q=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(ns,"1");q.setExecutableModels(executable);q.setKnowledgeRevision(3);
      return reasoner.validateDocument(q,scope);
    }

  public KimConceptImpl named(String name){return leaf(name);}
  public KimObservableImpl observerVocabulary() {
    declare("Observer",SemanticType.AGENT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
    var syntax = new KimObservableImpl();syntax.setSemantics(register(leaf("Observer")));return syntax;
  }
  public void cropVocabulary() {
    for(var name:List.of("HarvestCashValue","TotalCashValue")) {
      declare(name,SemanticType.QUALITY,SemanticType.MONETARY_VALUE,SemanticType.QUANTIFIABLE,SemanticType.OBSERVABLE);
      restriction(name,"isInherentTo",type("Region"));register(leaf(name));
    }
    for(var name:List.of("Crop","Wheat","Maize")) { declare(name,SemanticType.ATTRIBUTE,SemanticType.PREDICATE); }
    concept("Crop").setAbstract(true);concept("Crop").getType().add(SemanticType.ABSTRACT);
    concept("Crop").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.BASE_DECLARATION,"true");
    concept("Crop").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasAttribute");
    sub("Wheat","Crop");sub("Maize","Crop");
    owl.setApplicableObservables(concept("Crop"),List.of(concept("Region"),concept("HarvestCashValue")),ontology);
    for(var name:List.of("Crop","Wheat","Maize"))register(leaf(name));
  }
  public void seasonVocabulary() {
    for(var name:List.of("Season","Warm","Cool"))declare(name,SemanticType.ATTRIBUTE,SemanticType.PREDICATE);
    concept("Season").setAbstract(true);concept("Season").getType().add(SemanticType.ABSTRACT);
    concept("Season").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.BASE_DECLARATION,"true");
    concept("Season").getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY,"odo:hasAttribute");
    sub("Warm","Season");sub("Cool","Season");
    owl.setApplicableObservables(concept("Season"),List.of(concept("Region"),concept("HarvestCashValue")),ontology);
    for(var name:List.of("Season","Warm","Cool"))register(leaf(name));
  }
  public void installMatcher()throws Exception{
    field(owl,OWL.class,"reasonerActive",true);
    field(reasoner,ReasonerService.class,"authorityBindings",new org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings());
    field(reasoner,ReasonerService.class,"semanticMatcher",new org.integratedmodelling.klab.services.reasoner.SemanticMatcher(reasoner));
    field(reasoner,ReasonerService.class,"conceptSyntax",new org.integratedmodelling.klab.services.reasoner.internal.ConceptSyntaxCache());
  }
}
