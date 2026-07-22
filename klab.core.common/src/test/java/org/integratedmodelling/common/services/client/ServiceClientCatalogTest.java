package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;

class ServiceClientCatalogTest {

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
