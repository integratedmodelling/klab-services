package org.integratedmodelling.klab.services.resources.storage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;import java.time.Duration;import java.util.*;
import org.integratedmodelling.common.services.client.SemanticCycleFacade;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
/** Real source/private actor; scope mocks represent ownership only, never semantic answers. */
class RetainedRankingRevisionIntegrationTest {
 @TempDir Path directory;
 private WorldviewImpl source(String suffix)throws Exception {
  var here=Path.of("").toAbsolutePath();var root=Files.isDirectory(here.resolve("klab.services.runtime"))?here:here.getParent();
  var code=Files.readString(root.resolve("klab.services.runtime/src/test/resources/consequences/roles-minimal/src/imod.kwv"))+"\n// "+suffix;
  var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectory(directory.resolve(suffix)),Map.of("imod",code));
  var result=new WorldviewImpl();result.setUrn("imod");result.setWorldviewId("imod");result.setEmpty(false);result.setOntologies(new ArrayList<>(manager.getOntologies(false)));return result;
 }
 private ContextScope scope(String userName) {
  var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
  var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn(userName);when(scope.getUser()).thenReturn(user);
  var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(scope.getService(RuntimeService.class)).thenReturn(runtime);return scope;
 }
 private SemanticCycleRequest request(SemanticCycleBinding binding,String cycle){return new SemanticCycleRequest("runtime","context",cycle,binding);}
 private Reasoner pinned(AcceptedSemanticRuntime runtime,SemanticCycleBinding binding,String cycle,ContextScope scope){return SemanticCycleFacade.create(mock(Reasoner.class),request(binding,cycle),scope,runtime::invokeCycle);}
 @Test void actualAcceptedActorRanksWithinRetainedPublicGenerationAcrossNewPublication()throws Exception {
  org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  try(var runtime=new AcceptedSemanticRuntime(new SemanticEnvironmentFactory(directory.resolve("actors"),CoreOntologyCatalog.bundledCore()),"reasoner")) {
   var scope=scope("alice");runtime.submit(source("first"));assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
   assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
   var oldBinding=runtime.beginCycle(request(null,"old"),scope);var old=pinned(runtime,oldBinding,"old",scope);
   var oldMeaning=old.resolveObservable("imod:Bearer");assertNotNull(oldMeaning);
   int plain=old.semanticDistance(oldMeaning,oldMeaning,null);int ranked=old.semanticDistance(oldMeaning,oldMeaning,null,oldBinding.knowledgeRevision());
   int invalid=old.semanticDistance(oldMeaning,oldMeaning,null,-1);
   System.out.println("FIRST public="+oldBinding.knowledgeRevision()+" threeArg="+plain+" receiptArg="+ranked+" invalid="+invalid);
   runtime.submit(source("second"));assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
   assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
   var nextBinding=runtime.beginCycle(request(null,"new"),scope);var next=pinned(runtime,nextBinding,"new",scope);assertTrue(nextBinding.knowledgeRevision()>oldBinding.knowledgeRevision());
   int retained=old.semanticDistance(oldMeaning,oldMeaning,null,oldBinding.knowledgeRevision());
   int wrong=old.semanticDistance(oldMeaning,oldMeaning,null,nextBinding.knowledgeRevision());
   var nextMeaning=next.resolveObservable("imod:Bearer");int fresh=next.semanticDistance(nextMeaning,nextMeaning,null,nextBinding.knowledgeRevision());
   int stale=next.semanticDistance(nextMeaning,nextMeaning,null,oldBinding.knowledgeRevision());
   System.out.println("AFTER oldPublic="+oldBinding.knowledgeRevision()+" newPublic="+nextBinding.knowledgeRevision()+" retained="+retained+" oldAskedNew="+wrong+" newAskedNew="+fresh+" newAskedOld="+stale);
   assertThrows(SecurityException.class,()->pinned(runtime,oldBinding,"old",scope("bob")).semanticDistance(oldMeaning,oldMeaning,null,oldBinding.knowledgeRevision()));
   runtime.releaseCycle(request(oldBinding,"old"),scope);assertThrows(SecurityException.class,()->old.semanticDistance(oldMeaning,oldMeaning,null,oldBinding.knowledgeRevision()));runtime.releaseCycle(request(nextBinding,"new"),scope);
   assertAll(()->assertTrue(plain>=0),()->assertEquals(plain,ranked),()->assertEquals(plain,retained),()->assertTrue(invalid<0),()->assertTrue(wrong<0),()->assertTrue(fresh>=0),()->assertTrue(stale<0));
  }
 }
}
