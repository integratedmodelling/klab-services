package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;

class ServiceClientCatalogTest {

  @Test
  void monitorRetiresAfterItsLastRequestClientIsCollected() throws Exception {
    // Do not spy on this monitor: Mockito's invocation log would retain registerClient's argument.
    var monitor = ServiceClientCatalog.INSTANCE.new ClientMonitor(
        URI.create("http://localhost:8091").toURL(), "orphan", null,
        KlabService.Type.REASONER, new AtomicReference<>(), false);
    var queue = new ReferenceQueue<BaseServiceClient>();
    var discarded = registerDiscardedClient(monitor, queue);
    Reference<? extends BaseServiceClient> collected = null;
    for (int attempt = 0; attempt < 20 && collected == null; attempt++) {
      System.gc();
      collected = queue.remove(100);
    }
    assertEquals(discarded, collected);
    monitor.timedTasks();
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,
        () -> monitor.registerClient(mock(BaseServiceClient.class)));
  }

  @Test
  void orphanedMonitorStopsWithoutPollingAndCannotBeReused() throws Exception {
    var monitor = spy(ServiceClientCatalog.INSTANCE.new ClientMonitor(
        URI.create("http://localhost:8091").toURL(), null, null,
        KlabService.Type.REASONER, new AtomicReference<>(), false));
    monitor.timedTasks();
    verify(monitor, never()).readStatus();
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,
        () -> monitor.registerClient(mock(BaseServiceClient.class)));
  }

  @Test
  void releasingUnidentifiedMonitorIsSafe() throws Exception {
    var monitor = ServiceClientCatalog.INSTANCE.new ClientMonitor(
        URI.create("http://localhost:8091").toURL(), null, null,
        KlabService.Type.REASONER, new AtomicReference<>(), false);
    var client = mock(BaseServiceClient.class);
    monitor.registerClient(client);
    assertEquals(0, monitor.release(client));
  }

  @Test
  void throwingListenerDoesNotStarveOtherClients() throws Exception {
    var monitor = spy(ServiceClientCatalog.INSTANCE.new ClientMonitor(
        URI.create("http://localhost:8091").toURL(), "listeners", null,
        KlabService.Type.REASONER, new AtomicReference<>(), false));
    var first = mock(BaseServiceClient.class);
    var second = mock(BaseServiceClient.class);
    var delivered = new java.util.concurrent.atomic.AtomicInteger();
    first.statusListeners = java.util.List.of((status, changed) -> { throw new IllegalStateException(); });
    second.statusListeners = java.util.List.of((status, changed) -> delivered.incrementAndGet());
    monitor.registerClient(first);
    monitor.registerClient(second);
    doReturn(false).when(monitor).readStatus();
    monitor.timedTasks();
    assertEquals(1, delivered.get());
    monitor.release(first);
    monitor.release(second);
  }

  @Test
  void monitoringDoesNotRetainDiscardedClients() throws Exception {
    var monitor =
        ServiceClientCatalog.INSTANCE.new ClientMonitor(
            URI.create("http://localhost:8091").toURL(),
            "retention-test",
            null,
            KlabService.Type.REASONER,
            new AtomicReference<>(),
            false);
    var active = mock(BaseServiceClient.class);
    monitor.registerClient(active);
    var queue = new ReferenceQueue<BaseServiceClient>();
    var discarded = registerDiscardedClient(monitor, queue);

    Reference<? extends BaseServiceClient> collected = null;
    for (int attempt = 0; attempt < 20 && collected == null; attempt++) {
      System.gc();
      collected = queue.remove(100);
    }
    assertEquals(discarded, collected, "Monitoring must not own a discarded client");
    // Trigger removal of the stale registration while another scope still owns its client.
    assertEquals(1, monitor.release(mock(BaseServiceClient.class)));
    Reference.reachabilityFence(active);
  }

  private WeakReference<BaseServiceClient> registerDiscardedClient(
      ServiceClientCatalog.ClientMonitor monitor, ReferenceQueue<BaseServiceClient> queue) {
    var client = mock(BaseServiceClient.class);
    monitor.registerClient(client);
    return new WeakReference<>(client, queue);
  }

  @Test
  void clientMonitorClassifiesLocalityFromItsServiceUrl() throws Exception {
    var local =
        ServiceClientCatalog.INSTANCE.new ClientMonitor(
            URI.create("http://localhost:8091").toURL(),
            "local",
            null,
            KlabService.Type.RESOURCES,
            new AtomicReference<>(),
            false);
    var remote =
        ServiceClientCatalog.INSTANCE.new ClientMonitor(
            URI.create("https://192.0.2.1").toURL(),
            "remote",
            null,
            KlabService.Type.RESOURCES,
            new AtomicReference<>(),
            false);

    assertTrue(local.isLocal());
    assertFalse(remote.isLocal());
  }
}
