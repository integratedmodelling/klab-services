package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class OWLConcurrencyTest {
  @Test void inferenceAndFlushShareTheSameMonitor() throws Exception {
    var owl = new OWL(mock(Scope.class));
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    var delegate = mock(OWLReasoner.class, invocation -> {
      int concurrent = active.incrementAndGet();
      maximum.accumulateAndGet(concurrent, Math::max);
      try { Thread.sleep(2); return invocation.getMethod().getReturnType() == boolean.class ? true : null; }
      finally { active.decrementAndGet(); }
    });
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true); field.set(owl, delegate);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      var futures = new java.util.ArrayList<Future<?>>();
      for (int worker = 0; worker < 8; worker++) futures.add(pool.submit(() -> {
        start.await();
        for (int i = 0; i < 20; i++) {
          owl.flush(); owl.isConsistent(); owl.getSuperClasses(null, false);
        }
        return null;
      }));
      start.countDown();
      for (var future : futures) future.get(15, TimeUnit.SECONDS);
    }
    assertEquals(1, maximum.get(), "HermiT must never process overlapping queries or flushes");
  }
}
