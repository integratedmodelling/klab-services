package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full private factory/compiler/OWL reconciliation with real cleanup and accepted leases. */
class ReconciliationPrivateCompilationTest {
  @TempDir Path directory;
  private Worldview world(String text) {
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setProjectName("fixture");
    ontology.setSourceCode(" ".repeat(100)+text);
    var subject=new KimConceptStatementImpl();subject.setUrn("Thing");subject.setNamespace("w");
    subject.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    subject.setUpperConceptDefined("odo:Subject");subject.setOffsetInDocument(1);
    var quality=new KimConceptStatementImpl();quality.setUrn("Value");quality.setNamespace("w");
    quality.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
    quality.setUpperConceptDefined("odo:Quality");quality.setOffsetInDocument(2);
    var concrete=new KimConceptStatementImpl();concrete.setUrn("Q");concrete.setNamespace("w");
    concrete.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));concrete.setOffsetInDocument(3);
    var parent=new KimConceptImpl();parent.setName("w:Value");parent.setUrn("w:Value");concrete.setDeclaredParent(parent);
    var bearer=new KimConceptImpl();bearer.setName("w:Thing");bearer.setUrn("w:Thing");concrete.setDeclaredInherent(bearer);
    ontology.getStatements().addAll(List.of(subject,quality,concrete));
    var result=new WorldviewImpl();result.setUrn("w");result.setWorldviewId("w");result.getOntologies().add(ontology);
    return result;
  }
  private long actors(Path root) throws Exception {
    if(!Files.exists(root))return 0;
    try(var paths=Files.list(root)){return paths.count();}
  }
  private boolean innerCompatibility() {
    return StackWalker.getInstance().walk(frames->frames.anyMatch(f->
        f.getClassName().endsWith(".OWLSemanticClauseSupport")&&f.getMethodName().equals("specializes")));
  }

  @Test void latestCandidateStartsAfterCancelledRealReconciliationCleanupAndPreservesAcceptedLease() throws Exception {
    var assets=directory.resolve("actors");
    var factory=new SemanticEnvironmentFactory(assets,Map.of("odo.owl",Files.readAllBytes(
        Path.of(System.getProperty("klab.test.core.directory"),"odo.owl"))));
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var blocked=new AtomicBoolean();var cancellationSeen=new AtomicReference<Throwable>();
    var original=new CancellationException("same obsolete reconciliation cancellation");
    var builds=new AtomicInteger();var observations=new AtomicInteger();var cleanupBeforeLatest=new AtomicBoolean();
    try(var coordinator=new CandidatePublicationCoordinator<Worldview,SemanticEnvironment>((input,cancellation)->{
      int build=builds.incrementAndGet();
      if(build==3)cleanupBeforeLatest.set(actors(assets)==1);
      CandidatePublicationCoordinator.Cancellation observed=new CandidatePublicationCoordinator.Cancellation() {
        public boolean cancelled(){return cancellation.cancelled();}
        public void checkpoint(){
          observations.incrementAndGet();
          if(build==2 && innerCompatibility() && blocked.compareAndSet(false,true)) {
            entered.countDown();
            try {assertTrue(release.await(30,TimeUnit.SECONDS));}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
          }
          if(cancellation.cancelled())throw original;
        }
      };
      try{return factory.build(input,observed);}
      catch(CancellationException cancelled){cancellationSeen.set(cancelled);throw cancelled;}
    },input->true)) {
      coordinator.submit(factory.capture(world("accepted-p")));
      assertTrue(coordinator.awaitIdle(Duration.ofSeconds(30)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,coordinator.status().logicalStatus());
      try(var lease=coordinator.accepted().orElseThrow()) {
        var acceptedActor=lease.state().actor();
        coordinator.submit(factory.capture(world("obsolete-a")));
        assertTrue(entered.await(30,TimeUnit.SECONDS),"Real reconciliation reached recursive compatibility");
        assertEquals(1,coordinator.status().acceptedGeneration());
        coordinator.submit(factory.capture(world("latest-b")));
        assertSame(acceptedActor,lease.state().actor());assertNotNull(acceptedActor.semanticCapabilitiesSnapshot());
        assertEquals(2,builds.get(),"Latest build waits for owned obsolete cleanup");
        release.countDown();
        assertTrue(coordinator.awaitIdle(Duration.ofSeconds(30)));
        assertSame(original,cancellationSeen.get());assertTrue(cleanupBeforeLatest.get());
        assertEquals(3,builds.get());assertEquals(2,coordinator.status().acceptedGeneration());
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,coordinator.status().logicalStatus());
        assertEquals(SemanticEnvironmentFactory.sources(world("latest-b")),coordinator.status().acceptedSources());
        assertTrue(coordinator.status().cleanupFailures().isEmpty());
        assertSame(acceptedActor,lease.state().actor());assertEquals(2,actors(assets));
      }
      assertEquals(1,actors(assets),"Old accepted actor retires only after its last lease");
    } finally {release.countDown();}
    assertEquals(0,actors(assets));assertTrue(observations.get()>0);
  }
}
