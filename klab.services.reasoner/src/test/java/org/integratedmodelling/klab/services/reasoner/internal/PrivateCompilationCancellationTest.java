package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.owl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

class PrivateCompilationCancellationTest {
  @TempDir Path directory;
  PrivateCompilationCancellationTest() { ServiceConfiguration.INSTANCE.getMainService(); }

  private Worldview world(String text,int declarations) {
    var ontology=new KimOntologyImpl(); ontology.setUrn("w"); ontology.setProjectName("fixture");
    ontology.setSourceCode(text);
    for(int i=0;i<declarations;i++) {
      var statement=new KimConceptStatementImpl(); statement.setUrn("Subject"+i); statement.setNamespace("w");
      statement.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
      ontology.getStatements().add(statement);
    }
    var result=new WorldviewImpl(); result.setUrn("w"); result.setWorldviewId("w");
    result.getOntologies().add(ontology); return result;
  }
  private CandidatePublicationCoordinator.Input<Worldview> input(Worldview world) {
    return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
        SemanticEnvironmentFactory.sourceOrder(world),"fixture",SemanticEnvironmentFactory.CONFIGURATION_REVISION,
        world,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());
  }
  private Map<String,Long> metrics() {
    return Map.of("reasonerCreations",0L,"flushRequests",0L,"actualFlushes",0L,
        "satisfiabilityQueries",0L,"entailmentQueries",0L);
  }
  private SemanticEnvironmentScope scope() { return new SemanticEnvironmentScope(mock(ResourcesService.class)); }
  private ReasonerService actor(SemanticEnvironmentScope scope,Path assets) throws Exception {
    return new ReasonerService(scope,Files.createDirectories(assets.resolve("knowledge")),assets,CapturedAuthorityCatalog.empty());
  }
  private void minimalOwl(OWL owl) throws Exception {
    when(owl.compilationMetrics()).thenReturn(metrics());
    var inherent=mock(org.integratedmodelling.klab.services.reasoner.owl.Property.class);
    when(inherent.getOWLEntity()).thenReturn(org.semanticweb.owlapi.apibinding.OWLManager.getOWLDataFactory()
        .getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#isInherentTo")));
    when(owl.getProperty("odo:isInherentTo")).thenReturn(inherent);
    var manager=mock(org.semanticweb.owlapi.model.OWLOntologyManager.class);
    when(manager.getOWLDataFactory()).thenReturn(org.semanticweb.owlapi.apibinding.OWLManager.getOWLDataFactory());
    var managerField=OWL.class.getDeclaredField("manager"); managerField.setAccessible(true); managerField.set(owl,manager);
    when(owl.stageDeclarations()).thenAnswer(i -> (AutoCloseable)()->{});
  }

  @Test void supersededPrivateLoadStopsBeforeTheNextDeclarationAndUnwindsStaging() throws Exception {
    var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
    var cancelled=new AtomicBoolean(); var entries=new AtomicInteger(); var depth=new AtomicInteger();
    var stagingCloses=new AtomicInteger(); var ontology=mock(Ontology.class);
    try(var construction=mockConstruction(OWL.class,(owl,context)->{
      minimalOwl(owl);
      when(owl.stageDeclarations()).thenAnswer(i->{depth.incrementAndGet();return (AutoCloseable)()->{
        depth.decrementAndGet(); stagingCloses.incrementAndGet();};});
      when(owl.requireOntology("w")).thenAnswer(i->{
        if(entries.incrementAndGet()==1) { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); }
        return ontology;
      });
    })) {
      var assets=Files.createDirectories(directory.resolve("private")); var scope=scope();
      var actor=actor(scope,assets); var environment=new SemanticEnvironment(actor,assets);
      var executor=Executors.newSingleThreadExecutor();
      try {
        var job=executor.submit(()->{
          try { actor.compilePrivateKnowledge(world("saved a",3),scope,cancelled::get); }
          finally { environment.close(); }
          return null;
        });
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        cancelled.set(true); long released=System.nanoTime(); release.countDown();
        var failure=assertThrows(ExecutionException.class,()->job.get(5,TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class,failure.getCause());
        assertEquals(1,entries.get()); assertEquals(0,depth.get()); assertEquals(1,stagingCloses.get());
        verify(construction.constructed().getFirst(),never()).flushReasoner();
        verify(construction.constructed().getFirst(),times(1)).reset();
        environment.close(); verify(construction.constructed().getFirst(),times(1)).reset();
        assertFalse(Files.exists(assets));
        System.out.println("CANCELLATION_MEASURE postReleaseToPrivateCleanupNanos="+(System.nanoTime()-released)
            +" namedDeclarationsExecuted=1 namedDeclarationsSkipped=2");
      } finally { release.countDown(); executor.shutdownNow(); }
    }
  }

  @Test void generationSupersessionKeepsAcceptedLeaseAliveAndStartsLatestAfterPrivateCleanup() throws Exception {
    var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var latestStarted=new CountDownLatch(1);
    var named=new AtomicInteger(); var initialCancelled=new AtomicBoolean(); var initialReads=new AtomicInteger();
    var depth=new AtomicInteger(); var ontology=mock(Ontology.class);
    try(var construction=mockConstruction(OWL.class,(owl,context)->{
      minimalOwl(owl);
      when(owl.stageDeclarations()).thenAnswer(i->{depth.incrementAndGet();return (AutoCloseable)depth::decrementAndGet;});
      when(owl.requireOntology("w")).thenAnswer(i->{
        if(named.incrementAndGet()==1) { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); }
        return ontology;
      });
    })) {
      var pScope=scope(); var pAssets=Files.createDirectories(directory.resolve("accepted-p")); var p=actor(pScope,pAssets);
      p.compilePrivateKnowledge(world("p",0),pScope,()->{initialReads.incrementAndGet();return initialCancelled.get();});
      var accepted=new SemanticEnvironment(p,pAssets);
      var aScope=scope(); var aAssets=Files.createDirectories(directory.resolve("obsolete-a")); var a=actor(aScope,aAssets);
      var obsolete=new SemanticEnvironment(a,aAssets);
      var bScope=scope(); var bAssets=Files.createDirectories(directory.resolve("latest-b")); var b=actor(bScope,bAssets);
      var latest=new SemanticEnvironment(b,bAssets); var builds=new AtomicInteger();
      var lastStarted=new AtomicLong();
      try(var coordinator=new CandidatePublicationCoordinator<Worldview,SemanticEnvironment>((in,cancellation)->{
        int number=builds.incrementAndGet();
        if(number==1) return new CandidatePublicationCoordinator.Result<>(accepted,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
        if(number==2) {
          try { a.compilePrivateKnowledge(world("a",3),aScope,cancellation); }
          finally { obsolete.close(); }
          throw new AssertionError("Obsolete private compile must not finish");
        }
        lastStarted.set(System.nanoTime()); latestStarted.countDown();
        b.compilePrivateKnowledge(world("b",0),bScope,cancellation);
        return new CandidatePublicationCoordinator.Result<>(latest,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
      }, in -> true)) {
        coordinator.submit(input(world("p",0))); assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
        try(var lease=coordinator.accepted().orElseThrow()) {
          int checksBeforePublication=initialReads.get(); initialCancelled.set(true);
          assertSame(p,lease.state().actor()); assertNotNull(p.semanticCapabilitiesSnapshot());
          assertEquals(checksBeforePublication,initialReads.get(),"Accepted reads retain no compile predicate");
          assertThrows(IllegalStateException.class,()->p.compilePrivateKnowledge(world("p2",0),pScope,()->false));
          coordinator.submit(input(world("a",3))); assertTrue(entered.await(5,TimeUnit.SECONDS));
          coordinator.submit(input(world("b",0)));
          assertFalse(latestStarted.await(20,TimeUnit.MILLISECONDS),"One indivisible operation is not interrupted");
          long released=System.nanoTime(); release.countDown();
          assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
          assertEquals(2,coordinator.status().acceptedGeneration());
          assertEquals(SemanticEnvironmentFactory.sources(world("b",0)),coordinator.status().acceptedSources());
          assertEquals(1,named.get()); assertEquals(0,depth.get()); assertFalse(Files.exists(aAssets));
          assertTrue(Files.exists(pAssets)); assertSame(p,lease.state().actor());
          verify(construction.constructed().get(0),never()).reset();
          verify(construction.constructed().get(1),times(1)).reset();
          System.out.println("CANCELLATION_MEASURE postReleaseToLatestStartNanos="+(lastStarted.get()-released));
        }
        assertFalse(Files.exists(pAssets));
      } finally { release.countDown(); obsolete.close(); latest.close(); accepted.close(); }
      verify(construction.constructed().get(0),times(1)).reset();
      verify(construction.constructed().get(1),times(1)).reset();
      verify(construction.constructed().get(2),times(1)).reset();
    }
  }

  @Test void privateEntryRejectsRootFacadeAndForeignScopeBeforeReadingCancellation() throws Exception {
    var root=mock(ReasonerService.class,CALLS_REAL_METHODS); var reads=new AtomicInteger();
    assertThrows(IllegalStateException.class,()->root.compilePrivateKnowledge(world("a",0),scope(),()->{reads.incrementAndGet();return false;}));
    try(var construction=mockConstruction(OWL.class,(owl,context)->minimalOwl(owl))) {
      var ownedScope=scope(); var assets=Files.createDirectories(directory.resolve("foreign")); var actor=actor(ownedScope,assets);
      try {
        assertThrows(IllegalStateException.class,()->actor.compilePrivateKnowledge(world("a",0),scope(),()->{reads.incrementAndGet();return false;}));
        assertEquals(0,reads.get());
      } finally { new SemanticEnvironment(actor,assets).close(); }
    }
  }

  @Test void cancellationBeforePrivateLoadIsOneShotAndNotConvertedToSourceError() throws Exception {
    try(var construction=mockConstruction(OWL.class,(owl,context)->minimalOwl(owl))) {
      var scope=scope(); var assets=Files.createDirectories(directory.resolve("early")); var actor=actor(scope,assets);
      try {
        assertThrows(CancellationException.class,()->actor.compilePrivateKnowledge(world("a",0),scope,()->true));
        verify(construction.constructed().getFirst(),never()).initialize(any());
        assertThrows(IllegalStateException.class,()->actor.compilePrivateKnowledge(world("b",0),scope,()->false));
      } finally { new SemanticEnvironment(actor,assets).close(); }
    }
  }

  @Test void privateDisposalFailureCannotReplacePrimaryCancellationAndAssetsStillDisappear() throws Exception {
    var assets=Files.createDirectories(directory.resolve("cleanup")); Files.writeString(assets.resolve("owned"),"data");
    var actor=mock(ReasonerService.class); var cleanup=new IllegalStateException("owned actor cleanup failed");
    doThrow(cleanup).when(actor).disposeSemanticEnvironment(); var environment=new SemanticEnvironment(actor,assets);
    var original=new CancellationException("primary cancellation");
    assertSame(original,assertThrows(CancellationException.class,()->{
      try { throw original; } finally { SemanticEnvironment.closeAfterFailure(environment,original); }
    }));
    assertArrayEquals(new Throwable[]{cleanup},original.getSuppressed()); assertFalse(Files.exists(assets));
    environment.close(); verify(actor,times(1)).disposeSemanticEnvironment();
  }

  @Test void assetCleanupFailureIsPreservedAndStandaloneCleanupStillThrows() throws Exception {
    var assets=directory.resolve("cleanup-failure"); var cleanup=new IOException("owned asset removal failed");
    var primary=new CancellationException("primary");
    try(var mocked=mockStatic(SemanticEnvironment.class,CALLS_REAL_METHODS)) {
      mocked.when(()->SemanticEnvironment.removeAssets(assets)).thenThrow(cleanup);
      SemanticEnvironment.removeAssetsAfterFailure(assets,primary);
      assertSame(cleanup,primary.getSuppressed()[0]);
      assertSame(cleanup,assertThrows(IOException.class,()->SemanticEnvironment.removeAssetsAfterFailure(assets,null)));
    }
  }

  @Test void builderOwnedSuppressedCleanupIsVisibleWithoutInvalidatingAcceptedState() throws Exception {
    var actor=mock(ReasonerService.class); var assets=Files.createDirectories(directory.resolve("accepted"));
    var environment=new SemanticEnvironment(actor,assets); var builds=new AtomicInteger();
    try(var coordinator=new CandidatePublicationCoordinator<Worldview,SemanticEnvironment>((in,c)->{
      if(builds.incrementAndGet()==1) return new CandidatePublicationCoordinator.Result<>(environment,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
      var primary=new CancellationException("primary"); var cleanup=new IOException("owned cleanup failure");
      primary.addSuppressed(cleanup); throw primary;
    }, in -> true)) {
      coordinator.submit(input(world("p",0))); assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
      try(var lease=coordinator.accepted().orElseThrow()) {
        coordinator.submit(input(world("a",0))); assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(1,coordinator.status().acceptedGeneration());
        assertTrue(coordinator.status().cleanupFailures().stream().anyMatch(m->m.contains("owned cleanup failure")));
        assertSame(actor,lease.state().actor()); verify(actor,never()).disposeSemanticEnvironment();
      }
    }
    verify(actor,times(1)).disposeSemanticEnvironment();
  }

  @Test void sourceIdentityStillChangesForWhitespaceAndComments() {
    var plain=SemanticEnvironmentFactory.sources(world("ontology w",0));
    assertNotEquals(plain,SemanticEnvironmentFactory.sources(world("ontology w ",0)));
    assertNotEquals(plain,SemanticEnvironmentFactory.sources(world("ontology w // comment",0)));
  }

  @Test void cancellationAtStructureEntryRestoresFlagsAndBalancesStaging() throws Exception {
    var depth=new AtomicInteger();
    try(var construction=mockConstruction(OWL.class,(owl,context)->{
      minimalOwl(owl);
      when(owl.stageDeclarations()).thenAnswer(i->{depth.incrementAndGet();return (AutoCloseable)depth::decrementAndGet;});
      when(owl.requireOntology("w")).thenReturn(mock(Ontology.class));
    })) {
      var scope=scope(); var assets=Files.createDirectories(directory.resolve("structure")); var actor=actor(scope,assets);
      var flag=ReasonerService.class.getDeclaredField("stagingDeclarationStructure"); flag.setAccessible(true);
      try {
        assertThrows(CancellationException.class,()->actor.compilePrivateKnowledge(world("a",1),scope,()->{
          try { return flag.getBoolean(actor); } catch(IllegalAccessException failure) { throw new AssertionError(failure); }
        }));
        assertFalse(flag.getBoolean(actor)); assertEquals(0,depth.get());
        verify(construction.constructed().getFirst(),never()).flushReasoner();
      } finally { new SemanticEnvironment(actor,assets).close(); }
    }
  }

  private SemanticEnvironmentFactory factory() throws Exception {
    return new SemanticEnvironmentFactory(directory.resolve("actors"),Map.of("odo.owl",
        Files.readAllBytes(Path.of(System.getProperty("klab.test.core.directory"),"odo.owl"))));
  }

  @Test void cancellationAfterConsistencySkipsClassificationAndPreservesRealFactoryCleanupFailure() throws Exception {
    var factory=factory(); var cancelled=new AtomicBoolean(); var cleanup=new IllegalStateException("owned reset failed");
    try(var construction=mockConstruction(OWL.class,(owl,context)->{
      minimalOwl(owl); when(owl.hasReasoner()).thenReturn(true);
      when(owl.isConsistent()).thenAnswer(i->{cancelled.set(true);return true;});
      doThrow(cleanup).when(owl).reset();
    })) {
      var failure=assertThrows(CancellationException.class,()->factory.build(factory.capture(world("final",0)),cancelled::get));
      assertTrue(Arrays.asList(failure.getSuppressed()).contains(cleanup));
      var owl=construction.constructed().getFirst(); verify(owl,times(1)).isConsistent();
      verify(owl,never()).getUnsatisfiableClasses(); verify(owl,times(1)).reset();
      try(var children=Files.list(directory.resolve("actors"))) { assertEquals(0,children.count()); }
    }
  }

  @Test void currentPrivateFactoryStillRunsFinalClassification() throws Exception {
    var factory=factory();
    try(var construction=mockConstruction(OWL.class,(owl,context)->{
      minimalOwl(owl); when(owl.hasReasoner()).thenReturn(true); when(owl.isConsistent()).thenReturn(true);
      @SuppressWarnings("unchecked") var unsatisfiable=(org.semanticweb.owlapi.reasoner.Node<org.semanticweb.owlapi.model.OWLClass>)
          mock(org.semanticweb.owlapi.reasoner.Node.class);
      when(unsatisfiable.entities()).thenAnswer(i->java.util.stream.Stream.empty());
      when(owl.getUnsatisfiableClasses()).thenReturn(unsatisfiable);
    })) {
      var result=factory.build(factory.capture(world("current",0)),()->false);
      try(var environment=result.state()) {
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
        verify(construction.constructed().getFirst(),times(1)).isConsistent();
        verify(construction.constructed().getFirst(),times(1)).getUnsatisfiableClasses();
      }
      verify(construction.constructed().getFirst(),times(1)).reset();
    }
  }
}
