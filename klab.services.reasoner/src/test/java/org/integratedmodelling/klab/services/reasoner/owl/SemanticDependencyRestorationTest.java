package org.integratedmodelling.klab.services.reasoner.owl;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.runtime.language.WorldviewValidationScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
class SemanticDependencyRestorationTest {
 @TempDir Path directory;
 @Test void coldDependencyProofRestoresCanonicalCompoundThroughAcceptedCompiler()throws Exception {
  var root=LexicalRootAuthoringTest.parse("ontology imod in domain root version 1.0.0; thing Tree; abstract realm Habitat has children Forest;",new WorldviewValidationScope());
  var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("instance");world.setEmpty(false);world.setOntologies(new ArrayList<>(List.of(root)));
  var core=new TreeMap<String,byte[]>();for(var name:List.of("odo.owl","klab.owl","dc.owl"))try(var in=getClass().getResourceAsStream("/knowledge/"+name)){assertNotNull(in);core.put(name,in.readAllBytes());}
  var factory=new SemanticEnvironmentFactory(directory.resolve("actor"),core);var built=factory.build(factory.capture(world),()->false);
  assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());
  try(var state=built.state()) {
   var actor=state.actor();var caps=actor.capabilities(actor.serviceScope());
   var request=new SemanticDependencyRequest(caps.getServiceId(),caps.getKnowledgeRevision(),caps.getSemanticContentFingerprint(),List.of("imod:Forest imod:Tree","each imod:Tree","count of (each imod:Tree)","imod:Habitat of each imod:Tree"));
   var after=actor.semanticDependencies(request,actor.serviceScope());assertEquals(SemanticDependencyWitness.Status.COMPLETE,after.status(),after.toString());
   assertEquals(caps.getSemanticContentFingerprint(),after.baseFingerprint());assertEquals(caps.getKnowledgeRevision(),after.knowledgeRevision());assertEquals(request.inputHash(),after.inputHash());
   for(var invalid:List.of("imod:Missing imod:Tree","imod:Tree named local","any imod:Tree")) {
     var rejected=actor.semanticDependencies(new SemanticDependencyRequest(caps.getServiceId(),caps.getKnowledgeRevision(),caps.getSemanticContentFingerprint(),List.of(invalid)),actor.serviceScope());
     assertEquals(SemanticDependencyWitness.Status.UNKNOWN,rejected.status(),invalid+": "+rejected);
   }
  }
 }
 @Test void restorationDisablesInteractiveAnswersAndRestoresPolicyAfterNestedOrFailedCompilation() {
  var policy=new SemanticProofRestoration();assertTrue(policy.interactiveAllowed());
  assertNull(policy.restore(List.of("w:Tree"),term->{
    assertFalse(policy.interactiveAllowed());
    assertNull(policy.restore(List.of(term),inner->{assertFalse(policy.interactiveAllowed());var c=new org.integratedmodelling.common.knowledge.ConceptImpl();c.setUrn(inner);return c;}));
    assertFalse(policy.interactiveAllowed());var c=new org.integratedmodelling.common.knowledge.ConceptImpl();c.setUrn(term);return c;
  }));assertTrue(policy.interactiveAllowed());
  assertNotNull(policy.restore(List.of("w:Unavailable"),term->{assertFalse(policy.interactiveAllowed());throw new IllegalStateException("uncaptured provider answer");}));
  assertTrue(policy.interactiveAllowed());
  assertNotNull(policy.restore(List.of("w:Tree named local"),term->{var c=new org.integratedmodelling.common.knowledge.ConceptImpl();c.setUrn("w:Tree");return c;}));
 }
}
