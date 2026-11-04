package org.integratedmodelling.klab.services.reasoner.owl;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
class IdentityHierarchyCacheTest {
 static class Counted extends org.semanticweb.HermiT.Reasoner {
  int global;
  Counted(OWLOntology root){super(new org.semanticweb.HermiT.Configuration(),root);}
  @Override public NodeSet<OWLClass> getSuperClasses(OWLClassExpression c,boolean d){global++;return super.getSuperClasses(c,d);}
  @Override public Node<OWLClass> getEquivalentClasses(OWLClassExpression c){global++;return super.getEquivalentClasses(c);}
 }
 static Counted ready(IdentityRequirementSupportTest.Fixture t)throws Exception{
  var dl=new Counted(t.ontology.getOWLOntology());var f=OWL.class.getDeclaredField("reasoner");f.setAccessible(true);f.set(t.owl,dl);t.dl=dl;return dl;
 }
 static IdentityRequirementSupport.StructuralClosure cached(IdentityRequirementSupportTest.Fixture t,String head){
  synchronized(t.owl){return t.owl.identityRequirementCache().resolve(t.owl,t.type(head),()->{throw new AssertionError("Expected retained answer");});}
 }
 static IdentityRequirementSupport.StructuralClosure reference(IdentityRequirementSupportTest.Fixture t,String head)throws Exception{
  var method=IdentityRequirementSupport.class.getDeclaredMethod("collect",OWLClassExpression.class,Set.class,Set.class,Set.class);method.setAccessible(true);
  var support=new IdentityRequirementSupport(t.owl);var req=new LinkedHashSet<OWLClassExpression>();var ids=new LinkedHashSet<OWLClassExpression>();var seen=new HashSet<OWLClass>();
  t.owl.flush();method.invoke(support,t.type(head),req,ids,seen);
  var parents=new LinkedHashSet<>(t.owl.getSuperClasses(t.type(head),false).getFlattened());parents.addAll(t.owl.getEquivalentClasses(t.type(head)).getEntities());
  for(var p:parents)if(!p.isOWLThing()&&!p.isOWLNothing())method.invoke(support,p,req,ids,seen);
  return new IdentityRequirementSupport.StructuralClosure(req,ids);
 }
 void differential(Consumer<IdentityRequirementSupportTest.Fixture> setup,String...heads)throws Exception{
  try(var next=new IdentityRequirementSupportTest.Fixture();var old=new IdentityRequirementSupportTest.Fixture()){
   setup.accept(next);setup.accept(old);var dl=ready(next);ready(old);
   for(var head:heads){next.project(head,"A");assertEquals(reference(old,head),cached(next,head),head);}
   assertEquals(0,dl.global,"Unclassified discovery must avoid hierarchy enumeration");
  }
 }
 @Test void inferredRequirementsAndInheritedEvidenceMatchFullHierarchy()throws Exception{
  differential(t->{t.declare("Left",SemanticType.SUBJECT,SemanticType.OBSERVABLE);t.declare("Right",SemanticType.SUBJECT,SemanticType.OBSERVABLE);t.sub("SecondSubject","Left");t.sub("SecondSubject","Right");t.add(t.f.getOWLEquivalentClassesAxiom(t.type("ThirdSubject"),t.f.getOWLObjectIntersectionOf(t.type("Left"),t.type("Right"))));t.require("ThirdSubject",t.type("RootA"));t.restriction("ThirdSubject","hasIdentity",t.type("A"));},"SecondSubject","ThirdSubject","SUBJECT");
 }
 @Test void cyclicUnionAndCompleteFillersMatchFullHierarchy()throws Exception{
  differential(t->{t.require("SUBJECT",t.type("RootA"));t.sub("SUBJECT","ThirdSubject");t.add(t.f.getOWLSubClassOfAxiom(t.type("ThirdSubject"),t.f.getOWLObjectUnionOf(t.type("SUBJECT"),t.type("SecondSubject"))));t.require("SecondSubject",t.f.getOWLObjectUnionOf(t.type("RootA"),t.type("RootB")));},"SUBJECT","ThirdSubject","SecondSubject");
 }
 @Test void harmlessCyclicUnionDoesNotInventRequirement()throws Exception{
  differential(t->{t.require("SUBJECT",t.type("RootA"));t.sub("SUBJECT","ThirdSubject");t.add(t.f.getOWLSubClassOfAxiom(t.type("ThirdSubject"),t.f.getOWLObjectUnionOf(t.type("SUBJECT"),t.type("SecondSubject"))));},"SUBJECT","ThirdSubject");
 }
 @Test void unsatisfiableHeadDoesNotImportUnrelatedManagerOwners()throws Exception{
  differential(t->{try{var outside=t.owl.manager.createOntology(IRI.create("urn:test:outside"));var owner=t.f.getOWLClass(IRI.create("urn:test:outside#Owner"));t.owl.manager.addAxiom(outside,t.f.getOWLSubClassOfAxiom(owner,t.f.getOWLObjectSomeValuesFrom(t.f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#requiresIdentity")),t.type("RootA"))));t.add(t.f.getOWLSubClassOfAxiom(t.type("SecondSubject"),t.f.getOWLNothing()));}catch(Exception e){throw new RuntimeException(e);}},"SecondSubject","SUBJECT");
 }
 @Test void repeatedStructureIsCachedButEligibilityRemainsFresh()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture()){
   t.require("SecondSubject",t.type("RootA"));var dl=ready(t);var eligible=new AtomicBoolean(true);var s=new IdentityRequirementSupport(t.owl,(r,i)->eligible.get());
   assertFalse(s.project(t.concept("SecondSubject"),t.syntax("SecondSubject","A")).isAbstract());long count=t.owl.identityRequirementCache().metrics().get("computations");eligible.set(false);
   assertTrue(s.project(t.concept("SecondSubject"),t.syntax("SecondSubject","A")).isAbstract());assertEquals(count,t.owl.identityRequirementCache().metrics().get("computations"));assertEquals(0,dl.global);
  }
 }
 @Test void quarantinedClosureCannotReuseEmptyAnswer()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture()){
   t.require("SUBJECT",t.type("RootA"));ready(t);assertFalse(t.project("SecondSubject").isAbstract());assertTrue(cached(t,"SecondSubject").requirements().isEmpty());t.owl.quarantineDerivedClosure();assertTrue(t.project("SecondSubject").isAbstract());
  }
 }
 @Test void bufferedMutationAfterClassificationUsesCurrentTargetedDiscovery()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture()){
   t.require("SUBJECT",t.type("RootA"));var dl=ready(t);dl.precomputeInferences(InferenceType.CLASS_HIERARCHY);
   assertFalse(t.project("SecondSubject").isAbstract());int globals=dl.global;
   t.require("SecondSubject",t.type("RootA"));assertTrue(t.project("SecondSubject").isAbstract());assertEquals(globals,dl.global,"Pending changes must flush before isPrecomputed");
  }
 }
 @Test void flushAndReasonerReplacementRetireAnswers()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture()){
   t.require("SUBJECT",t.type("RootA"));var old=ready(t);assertFalse(t.project("SecondSubject").isAbstract());long count=t.owl.identityRequirementCache().metrics().get("computations");t.owl.flush();assertFalse(t.project("SecondSubject").isAbstract());assertTrue(t.owl.identityRequirementCache().metrics().get("computations")>count);
   count=t.owl.identityRequirementCache().metrics().get("computations");ready(t);old.dispose();assertFalse(t.project("SecondSubject").isAbstract());assertTrue(t.owl.identityRequirementCache().metrics().get("computations")>count);
  }
 }
 @Test void importsAndManagerReplacementRetireAnswers()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture();var replacement=new IdentityRequirementSupportTest.Fixture()){
   t.require("SUBJECT",t.type("RootA"));ready(t);assertFalse(t.project("SecondSubject").isAbstract());var imported=t.owl.manager.createOntology(IRI.create("urn:test:imported"));var link=t.f.getOWLImportsDeclaration(IRI.create("urn:test:imported"));t.owl.manager.addAxiom(imported,t.f.getOWLSubClassOfAxiom(t.type("SecondSubject"),t.f.getOWLObjectSomeValuesFrom(t.f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#requiresIdentity")),t.type("RootA"))));t.owl.manager.applyChange(new AddImport(t.ontology.getOWLOntology(),link));assertTrue(t.project("SecondSubject").isAbstract());
   t.owl.manager.applyChange(new RemoveImport(t.ontology.getOWLOntology(),link));t.owl.manager.removeOntology(imported);assertFalse(t.project("SecondSubject").isAbstract());
   replacement.require("SecondSubject",replacement.type("RootB"));ready(replacement);var f=OWL.class.getDeclaredField("reasoner");f.setAccessible(true);var old=t.dl;t.owl.manager=replacement.owl.manager;f.set(t.owl,replacement.dl);t.dl=null;assertTrue(t.project("SecondSubject").isAbstract());old.dispose();
  }
 }
 @Test void concurrentMutationCannotPublishAndCacheIsBounded()throws Exception{
  try(var t=new IdentityRequirementSupportTest.Fixture()){
   ready(t);var entered=new CountDownLatch(1);var changed=new CountDownLatch(1);
   var mutation=t.f.getOWLSubClassOfAxiom(t.type("SUBJECT"),t.type("SecondSubject"));
   var future=CompletableFuture.runAsync(()->{synchronized(t.owl){t.owl.identityRequirementCache().resolve(t.owl,t.type("SUBJECT"),()->{entered.countDown();try{if(!changed.await(5,TimeUnit.SECONDS))throw new AssertionError("Mutation timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return new IdentityRequirementSupport.StructuralClosure(Set.of(),Set.of());});}});
   assertTrue(entered.await(5,TimeUnit.SECONDS));t.owl.manager.addAxiom(t.ontology.getOWLOntology(),mutation);changed.countDown();assertInstanceOf(ConcurrentModificationException.class,assertThrows(ExecutionException.class,()->future.get(5,TimeUnit.SECONDS)).getCause());assertEquals(0L,t.owl.identityRequirementCache().metrics().get("entries"));
   synchronized(t.owl){for(int i=0;i<600;i++)t.owl.identityRequirementCache().resolve(t.owl,t.f.getOWLClass(IRI.create("urn:test:key:"+i)),()->new IdentityRequirementSupport.StructuralClosure(Set.of(),Set.of()));}assertTrue(t.owl.identityRequirementCache().metrics().get("entries")<=IdentityRequirementSupport.Cache.CAPACITY);
  }
 }
}
