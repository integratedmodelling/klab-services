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
  void duplicateChangeEventUsesIngestionGateWithoutInvalidatingCandidate() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var scope = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var runtime = mock(org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime.class);
    var source = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    source.setUrn("w"); source.setWorldviewId("w");
    var runtimeField = ReasonerService.class.getDeclaredField("candidateRuntime");
    runtimeField.setAccessible(true); runtimeField.set(reasoner, runtime);
    var worldviewField = ReasonerService.class.getDeclaredField("worldview");
    worldviewField.setAccessible(true); worldviewField.set(reasoner, source);
    doReturn(org.integratedmodelling.klab.api.services.resources.ResourceSet.empty())
        .when(reasoner).loadKnowledge(any(), same(scope));
    var changes = org.integratedmodelling.klab.api.services.resources.ResourceSet.empty();
    assertSame(changes, reasoner.updateKnowledge(changes, scope));
    verify(reasoner).loadKnowledge(any(), same(scope));
    verify(runtime, never()).invalidate();
    verify(runtime, never()).submit(any(Worldview.class));
    assertTrue(changes.getNotifications().isEmpty());
  }

  @Test
  void submittedWorldviewStillPollsForChangesWithoutReloadingUnchangedInput() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var scope = mock(ServiceScope.class);
    var resources = mock(ResourcesService.class);
    var capabilities = mock(ResourcesService.Capabilities.class);
    var runtime = mock(org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime.class);
    var source = mock(Worldview.class);
    var runtimeField = ReasonerService.class.getDeclaredField("candidateRuntime");
    runtimeField.setAccessible(true);
    runtimeField.set(reasoner, runtime);
    var worldviewField = ReasonerService.class.getDeclaredField("worldview");
    worldviewField.setAccessible(true);
    worldviewField.set(reasoner, source);
    when(runtime.status()).thenReturn(new org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.Status(
        1, 1,
        org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.BeanStatus.VALID,
        org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.LogicalStatus.VALID,
        java.util.Map.of(), java.util.Map.of(), List.of(), List.of()));
    when(reasoner.serviceScope()).thenReturn(scope);
    when(reasoner.getUrl()).thenReturn(URI.create("http://localhost:8091/reasoner").toURL());
    doNothing().when(reasoner).setOperational(anyBoolean());
    when(resources.getUrl()).thenReturn(URI.create("http://localhost:8092/resources").toURL());
    var remote = mock(ResourcesService.class);
    when(remote.getUrl()).thenReturn(URI.create("https://remote.example/resources").toURL());
    // Deliberately advertise the remote first: an unchanged local worldview still wins.
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(remote, resources));
    var status = new ServiceStatusImpl(); status.setAvailable(true);
    when(resources.status()).thenReturn(status);
    when(resources.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.isWorldviewProvider()).thenReturn(true);
    when(resources.list(Worldview.class, null)).thenReturn(List.of(source));
    when(runtime.shouldRetryPreflight(source, scope)).thenReturn(false);
    for (int i = 0; i < 3; i++) assertTrue(reasoner.checkWorldview());
    verify(resources, times(3)).list(Worldview.class, null);
    verify(reasoner, never()).loadKnowledge(any(), any());
    verify(scope, never()).info(any());
    verify(remote, never()).list(eq(Worldview.class), any());

    when(runtime.shouldRetryPreflight(source, scope)).thenReturn(true);
    doReturn(org.integratedmodelling.klab.api.services.resources.ResourceSet.empty())
        .when(reasoner).loadKnowledge(source, scope);
    assertTrue(reasoner.checkWorldview());
    verify(reasoner, times(1)).loadKnowledge(source, scope);
  }

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

    // The same service-certificate discovery works remotely when no local source exists.
    when(resources.getUrl()).thenReturn(URI.create("https://remote.example/resources").toURL());
    assertFalse(reasoner.checkWorldview());
    verify(resources, times(2)).list(Worldview.class, null);
  }
}
