package org.integratedmodelling.common.services.client.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.junit.jupiter.api.Test;

class ServiceMonitorTest {
  @Test
  void blockingStatusRefreshDoesNotUseContextualizationCarriers() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(1);
    var worker = new AtomicReference<Thread>();
    var client = mock(BaseServiceClient.class);
    when(client.refreshStatus()).thenAnswer(invocation -> {
      worker.set(Thread.currentThread());
      entered.countDown();
      try {
        release.await();
        return null;
      } finally {
        finished.countDown();
      }
    });
    var monitor = new ServiceMonitor(null, null, false, List.of(), null, null);
    try {
      monitor.refreshClientStatusAsync(client);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertFalse(worker.get().isVirtual());
      assertTrue(worker.get().isDaemon());
      var dependency = new CountDownLatch(1);
      Thread.startVirtualThread(dependency::countDown);
      assertTrue(dependency.await(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
    }
    assertTrue(finished.await(5, TimeUnit.SECONDS));
  }
}
