package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.resources.persistence.ModelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NamespaceModelIndexConcurrencyTest {
  @BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(5,TimeUnit.SECONDS),"latch budget exhausted");
  }
  private static void stop(ExecutorService executor) throws InterruptedException {
    executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
  }
  private static boolean claimed(KimNamespace source) {
    return source.getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA);
  }

  @Test void unrelatedTransportAndDiagnosticsCompleteWhileAdmissionIsBlocked() throws Exception {
    var f=new NamespaceModelIndexTest();var first=f.source("first");var unrelated=f.source("unrelated");
    var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
    index.reconcile(List.of(first,unrelated),catalog,f.scope,7,0);
    assertTrue(claimed(index.forTransport(first)));assertTrue(claimed(index.forTransport(unrelated)));
    var original=mockingDetails(f.reasoner).getStubbings().stream()
        .filter(s->s.getInvocation().getMethod().getName().equals("validateDocument")).findFirst().orElseThrow();
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    first.setSourceCode("edited source");
    doAnswer(call->{entered.countDown();await(release);return original.answer(call);})
        .when(f.reasoner).validateDocument(any(),same(f.scope));
    var executor=Executors.newFixedThreadPool(2);
    try {
      var writer=executor.submit(()->index.reconcile(List.of(first,unrelated),catalog,f.scope,7,10));
      await(entered);
      var reads=executor.submit(()->{
        assertFalse(claimed(index.forTransport(first)));
        assertFalse(claimed(index.forTransport(unrelated)));
        assertTrue(index.diagnostics().isEmpty());
      });
      reads.get(5,TimeUnit.SECONDS);assertFalse(writer.isDone());
      release.countDown();writer.get(5,TimeUnit.SECONDS);
      assertTrue(claimed(index.forTransport(first)));assertTrue(claimed(index.forTransport(unrelated)));
      verify(catalog,times(1)).replaceNamespace(unrelated,f.scope);
    } finally {release.countDown();stop(executor);}
  }

  @Test void admissionCanWaitForCrossThreadNamespaceCallback() throws Exception {
    var f=new NamespaceModelIndexTest();var source=f.source("source");var index=new NamespaceModelIndex(5);
    var original=mockingDetails(f.reasoner).getStubbings().stream()
        .filter(s->s.getInvocation().getMethod().getName().equals("validateDocument")).findFirst().orElseThrow();
    var callbacks=Executors.newSingleThreadExecutor();
    var writers=Executors.newSingleThreadExecutor();
    doAnswer(call->{
      var callback=callbacks.submit(()->index.forTransport(source));
      assertFalse(claimed(callback.get(5,TimeUnit.SECONDS)));
      return original.answer(call);
    }).when(f.reasoner).validateDocument(any(),same(f.scope));
    try {
      writers.submit(()->index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,7,0)).get(5,TimeUnit.SECONDS);
      assertTrue(claimed(index.forTransport(source)));
    } finally {stop(writers);stop(callbacks);}
  }

  @Test void reconciliationWritersRemainSerialized() throws Exception {
    var f=new NamespaceModelIndexTest();var source=f.source("source");var index=new NamespaceModelIndex(5);
    var original=mockingDetails(f.reasoner).getStubbings().stream()
        .filter(s->s.getInvocation().getMethod().getName().equals("validateDocument")).findFirst().orElseThrow();
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);
    var secondThread=new AtomicReference<Thread>();var secondManifestReads=new AtomicInteger();
    doAnswer(call->{entered.countDown();await(release);return original.answer(call);})
        .when(f.reasoner).validateDocument(any(),same(f.scope));
    var executor=Executors.newFixedThreadPool(2);var catalog=mock(ModelCatalog.class);
    try {
      var first=executor.submit(()->index.reconcile(List.of(source),catalog,f.scope,7,0));await(entered);
      var second=executor.submit(()->{
        secondThread.set(Thread.currentThread());secondStarted.countDown();
        index.reconcile(()->{secondManifestReads.incrementAndGet();return List.of(source);},catalog,f.scope,7,10);
      });
      await(secondStarted);
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean blocked=false;
      while(System.nanoTime()<deadline) {
        var info=ManagementFactory.getThreadMXBean().getThreadInfo(secondThread.get().threadId());
        if(info!=null && info.getThreadState()==Thread.State.BLOCKED && info.getLockInfo()!=null
            && info.getLockInfo().getIdentityHashCode()==System.identityHashCode(index)) {blocked=true;break;}
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(blocked);assertEquals(0,secondManifestReads.get());
      release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
      assertEquals(2,secondManifestReads.get());verify(catalog,times(1)).replaceNamespace(source,f.scope);
      assertTrue(claimed(index.forTransport(source)));
    } finally {release.countDown();stop(executor);}
  }

  @Test void sourceDriftAfterCatalogPublicationWithdrawsAllClaims() throws Exception {
    var f=new NamespaceModelIndexTest();var first=f.source("first");var second=f.source("second");
    var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
    var visible=ConcurrentHashMap.<String>newKeySet();var published=new CountDownLatch(1);var release=new CountDownLatch(1);
    doAnswer(call->{visible.add("first");published.countDown();await(release);return null;})
        .when(catalog).replaceNamespace(same(first),same(f.scope));
    doAnswer(call->{visible.add("second");return null;}).when(catalog).replaceNamespace(same(second),same(f.scope));
    doAnswer(call->{visible.remove(call.getArgument(0,String.class));return null;})
        .when(catalog).withholdNamespace(anyString(),same(f.scope));
    var executor=Executors.newSingleThreadExecutor();
    try {
      var writer=executor.submit(()->index.reconcile(List.of(first,second),catalog,f.scope,7,0));await(published);
      assertEquals(Set.of("first"),visible);assertFalse(claimed(index.forTransport(first)));
      first.setSourceCode("changed after catalog publication");release.countDown();
      assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->writer.get(5,TimeUnit.SECONDS)).getCause());
      assertTrue(visible.isEmpty());assertFalse(claimed(index.forTransport(first)));assertFalse(claimed(index.forTransport(second)));
      verify(catalog).withholdNamespace("first",f.scope);verify(catalog).withholdNamespace("second",f.scope);
    } finally {release.countDown();stop(executor);}
  }

  @Test void admissionMatchesDetachedCopyRatherThanOriginalReadAfterSerialization() {
    var f=new NamespaceModelIndexTest();var source=f.source("source");var index=new NamespaceModelIndex(5);
    index.reconcile(List.of(source),mock(ModelCatalog.class),f.scope,7,0);
    assertTrue(claimed(index.forTransport(source)));
    var changing=spy(source);var admittedSource=source.getSourceCode();
    changing.setSourceCode("unadmitted detached source");
    // JSON copies fields. Model the original getter advancing to the admitted text after
    // the serialized snapshot: only the detached copy may authorize its attached claim.
    doReturn(admittedSource).when(changing).getSourceCode();
    var detached=index.forTransport(changing);
    assertEquals("unadmitted detached source",detached.getSourceCode());
    assertFalse(claimed(detached));assertEquals(admittedSource,changing.getSourceCode());
  }

  @Test void semanticIdentityDriftAtSameRevisionCannotPublishClaims() {
    for(String field:List.of("service","fingerprint","session")) {
      var f=new NamespaceModelIndexTest();var source=f.source("source");
      var index=new NamespaceModelIndex(5);var catalog=mock(ModelCatalog.class);
      doAnswer(call->{
        switch(field) {
          case "service" -> when(f.capabilities.getServiceId()).thenReturn("replacement-reasoner");
          case "fingerprint" -> when(f.capabilities.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"b".repeat(64));
          case "session" -> when(f.capabilities.getSemanticSessionId()).thenReturn("replacement-session");
        }
        return null;
      }).when(catalog).replaceNamespace(same(source),same(f.scope));
      assertThrows(IllegalStateException.class,()->index.reconcile(List.of(source),catalog,f.scope,7,0),field);
      verify(catalog).replaceNamespace(source,f.scope);
      verify(catalog).withholdNamespace("source",f.scope);
      assertFalse(claimed(index.forTransport(source)),field);
    }
  }
}
