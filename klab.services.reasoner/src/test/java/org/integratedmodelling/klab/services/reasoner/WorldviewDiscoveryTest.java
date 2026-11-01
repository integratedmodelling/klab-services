package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.junit.jupiter.api.Test;

class WorldviewDiscoveryTest {
  @Test
  void emptyDiscoveryReturnsToSchedulerWithoutSleepingOrRepeatedRequests() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var scope = mock(ServiceScope.class);
    var resources = mock(ResourcesService.class);
    var capabilities = mock(ResourcesService.Capabilities.class);
    var status = new ServiceStatusImpl();
    status.setAvailable(true);
    when(reasoner.serviceScope()).thenReturn(scope);
    when(reasoner.getUrl()).thenReturn(URI.create("http://localhost:8091/reasoner").toURL());
    when(resources.getUrl()).thenReturn(URI.create("http://localhost:8092/resources").toURL());
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    when(resources.status()).thenReturn(status);
    when(resources.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.isWorldviewProvider()).thenReturn(true);
    when(resources.list(Worldview.class, null)).thenReturn(List.of());

    assertTimeout(Duration.ofSeconds(1), () -> assertFalse(reasoner.checkWorldview()));
    verify(resources, times(1)).list(Worldview.class, null);
    verify(reasoner, never()).loadKnowledge(any(), any());
  }
}
