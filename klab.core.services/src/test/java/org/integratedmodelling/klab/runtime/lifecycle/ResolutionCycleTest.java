package org.integratedmodelling.klab.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResolutionCycleTest {
  record Epoch(String session,long generation){}

  @Test void contextRevocationReleasesSemanticsButDoesNotPretendRunningWorkHasDrained() {
    var releases = new AtomicInteger();
    var cycle = new ResolutionCycle<>("context", true, new Epoch("one", 1), releases::incrementAndGet);
    var child = cycle.retain();
    cycle.revoke(); cycle.revoke();
    assertEquals(1, releases.get());
    assertThrows(IllegalStateException.class, cycle::retain);
    assertFalse(cycle.quiescence().toCompletableFuture().isDone());
    cycle.close();
    assertFalse(cycle.quiescence().toCompletableFuture().isDone());
    child.close(); child.close();
    cycle.quiescence().toCompletableFuture().join();
    assertEquals(1, releases.get());
  }

  @Test void concurrentCompletionWaitsForRevocationAcknowledgement() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var acknowledge = new java.util.concurrent.CountDownLatch(1);
    var releases = new AtomicInteger();
    var cycle = new ResolutionCycle<>("context", true, new Epoch("one", 1), () -> {
      releases.incrementAndGet(); entered.countDown();
      assertTrue(acknowledge.await(5, java.util.concurrent.TimeUnit.SECONDS));
    });
    var revoke = java.util.concurrent.CompletableFuture.runAsync(cycle::revoke);
    assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
    var finish = java.util.concurrent.CompletableFuture.runAsync(cycle::close);
    try { assertFalse(cycle.quiescence().toCompletableFuture().isDone()); }
    finally { acknowledge.countDown(); }
    revoke.get(5, java.util.concurrent.TimeUnit.SECONDS);
    finish.get(5, java.util.concurrent.TimeUnit.SECONDS);
    cycle.quiescence().toCompletableFuture().join();
    assertEquals(1, releases.get());
  }

  @Test void queuedAndRecursiveOwnersRetainTheSameFrozenCycleUntilAllWorkFinishes() {
    var releases=new AtomicInteger();var epoch=new Epoch("old",7);
    var cycle=new ResolutionCycle<>("context",true,epoch,releases::incrementAndGet);
    var queued=cycle.retain();cycle.close();
    assertFalse(cycle.quiescence().toCompletableFuture().isDone());assertEquals(0,releases.get());
    // Recursive work can begin after the initiating request returned, while a pass still owns it.
    var recursive=cycle.retain();queued.close();queued.close();
    assertFalse(cycle.quiescence().toCompletableFuture().isDone());
    assertSame(epoch,cycle.semantics());assertTrue(cycle.consequencesEnabled());
    recursive.close();recursive.close();cycle.close();
    cycle.quiescence().toCompletableFuture().join();assertEquals(1,releases.get());
    assertThrows(IllegalStateException.class,cycle::retain);
    var next=new ResolutionCycle<>("context",false,new Epoch("new",8),()->{});
    assertNotEquals(cycle.id(),next.id());assertFalse(next.consequencesEnabled());
    assertNotEquals(cycle.semantics(),next.semantics());next.close();
  }

  @Test void externalCompletionOrCancellationCannotReleaseOwnedSemanticState() {
    var releases=new AtomicInteger();var cycle=new ResolutionCycle<>("context",true,new Epoch("one",1),releases::incrementAndGet);
    var external=cycle.quiescence().toCompletableFuture();external.cancel(true);
    assertFalse(cycle.quiescence().toCompletableFuture().isDone());assertEquals(0,releases.get());
    cycle.close();assertEquals(1,releases.get());cycle.quiescence().toCompletableFuture().join();
  }

  @Test void failedSemanticReleaseIsVisibleAtTheQuiescenceBarrierWithoutDoubleClose() {
    var releases=new AtomicInteger();var failure=new IllegalStateException("semantic lease release failed");
    var cycle=new ResolutionCycle<>("context",true,new Epoch("one",1),()->{releases.incrementAndGet();throw failure;});
    assertSame(failure,assertThrows(IllegalStateException.class,cycle::close));
    assertSame(failure,assertThrows(CompletionException.class,()->cycle.quiescence().toCompletableFuture().join()).getCause());
    cycle.close();assertEquals(1,releases.get());assertThrows(IllegalStateException.class,cycle::retain);
  }
}
