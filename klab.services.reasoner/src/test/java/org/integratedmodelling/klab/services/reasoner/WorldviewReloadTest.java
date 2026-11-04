package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.junit.jupiter.api.Test;

class WorldviewReloadTest {
  private final ReasonerService reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
  private final ServiceScope scope = mock(ServiceScope.class);
  private final ResourcesService resources = mock(ResourcesService.class);
  private final Worldview source = mock(Worldview.class);
  private final AcceptedSemanticRuntime runtime = mock(AcceptedSemanticRuntime.class);

  WorldviewReloadTest() throws Exception {
    var field = ReasonerService.class.getDeclaredField("candidateRuntime");
    field.setAccessible(true);
    field.set(reasoner, runtime);
    when(reasoner.serviceScope()).thenReturn(scope);
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    when(resources.getUrl()).thenReturn(URI.create("http://localhost:8092/resources").toURL());
    var available = new ServiceStatusImpl();
    available.setAvailable(true);
    when(resources.status()).thenReturn(available);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(resources.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.isWorldviewProvider()).thenReturn(true);
    when(resources.list(Worldview.class, null)).thenReturn(List.of(source));
    doNothing().when(reasoner).setOperational(anyBoolean());
    loaded(ResourceSet.empty());
    when(runtime.status()).thenReturn(status(10, CandidatePublicationCoordinator.LogicalStatus.PENDING));
  }

  private CandidatePublicationCoordinator.Status status(
      long input, CandidatePublicationCoordinator.LogicalStatus logical, String... diagnostics) {
    // Publication generations and input generations intentionally have different counters.
    return new CandidatePublicationCoordinator.Status(input, 2,
        CandidatePublicationCoordinator.BeanStatus.VALID, logical,
        Map.of(), Map.of(), List.of(diagnostics), List.of());
  }

  private List<Notification> notifications(Map<String, Object> result) {
    return (List<Notification>) result.get("notifications");
  }

  private void loaded(ResourceSet result) {
    doAnswer(invocation -> {
      invocation.<java.util.function.LongConsumer>getArgument(4).accept(10);
      return result;
    }).when(reasoner).loadKnowledgeEntry(eq(source), eq(scope), any(), eq(true), any());
  }

  @Test void reloadPrefersLocalSourcesAndWaitsWithoutHoldingServiceMonitor() throws Exception {
    var remote = mock(ResourcesService.class);
    when(remote.getUrl()).thenReturn(URI.create("https://remote.example/resources").toURL());
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(remote, resources));
    when(runtime.awaitGeneration(10, Duration.ofMinutes(5))).thenAnswer(i -> {
      assertFalse(Thread.holdsLock(reasoner));
      return status(10, CandidatePublicationCoordinator.LogicalStatus.VALID);
    });
    assertEquals(true, reasoner.reloadWorldview().get("result"));
    verify(reasoner).loadKnowledgeEntry(eq(source), eq(scope), any(), eq(true), any());
    verify(remote, never()).list(eq(Worldview.class), any());
  }

  @Test void reportsBackgroundAuthorityErrorsEvenWithPriorAcceptedGeneration() throws Exception {
    when(runtime.awaitGeneration(10, Duration.ofMinutes(5)))
        .thenReturn(status(10, CandidatePublicationCoordinator.LogicalStatus.FAILED,
            "Authority synchronization failed: codelist unavailable"));
    var result = reasoner.reloadWorldview();
    assertEquals(false, result.get("result"));
    assertTrue(notifications(result).stream()
        .anyMatch(n -> n.getMessage().contains("codelist unavailable")));
  }

  @Test void returnsLexicalIngestionDiagnosticsToCaller() throws Exception {
    var error = Notification.error("Invalid ontology statement");
    var loaded = ResourceSet.empty();
    var ontology = new ResourceSet.Resource();
    ontology.getNotifications().add(error);
    loaded.getOntologies().add(ontology);
    loaded(loaded);
    when(runtime.awaitGeneration(10, Duration.ofMinutes(5)))
        .thenReturn(status(10, CandidatePublicationCoordinator.LogicalStatus.VALID));
    var result = reasoner.reloadWorldview();
    assertEquals(false, result.get("result"));
    assertTrue(notifications(result).contains(error));
  }

  @Test void reportsTimeoutAndSupersession() throws Exception {
    when(runtime.awaitGeneration(10, Duration.ofMinutes(5)))
        .thenReturn(status(10, CandidatePublicationCoordinator.LogicalStatus.PENDING));
    var timeout = reasoner.reloadWorldview();
    assertEquals(false, timeout.get("result"));
    assertTrue(notifications(timeout).stream().anyMatch(n -> n.getMessage().contains("timed out")));
    when(runtime.awaitGeneration(10, Duration.ofMinutes(5)))
        .thenReturn(status(11, CandidatePublicationCoordinator.LogicalStatus.VALID));
    var superseded = reasoner.reloadWorldview();
    assertEquals(false, superseded.get("result"));
    assertTrue(notifications(superseded).stream().anyMatch(n -> n.getMessage().contains("superseded")));
  }

  @Test void missingWorldviewAndDiscoveryExceptionsReturnFailures() {
    when(resources.list(Worldview.class, null)).thenReturn(List.of());
    assertEquals(false, reasoner.reloadWorldview().get("result"));
    when(resources.list(Worldview.class, null)).thenThrow(new IllegalStateException("Resources unavailable"));
    var result = reasoner.reloadWorldview();
    assertEquals(false, result.get("result"));
    assertTrue(notifications(result).stream()
        .anyMatch(n -> n.getMessage().contains("Resources unavailable")));
  }

  @Test void forcedPreflightReportsAuthoritySelectionFailuresBeforeSubmission() {
    when(source.getOntologies()).thenReturn(List.of(
        mock(org.integratedmodelling.klab.api.lang.kim.KimOntology.class)));
    doCallRealMethod().when(reasoner)
        .loadKnowledgeEntry(eq(source), eq(scope), any(), eq(true), any());
    when(runtime.beginPreflight(source, scope, true))
        .thenThrow(new IllegalStateException("Exact authorized provider metadata unavailable"));
    var result = reasoner.reloadWorldview();
    assertEquals(false, result.get("result"));
    assertTrue(notifications(result).stream().anyMatch(n ->
        n.getMessage().contains("Exact authorized provider metadata unavailable")));
    verify(runtime).beginPreflight(source, scope, true);
  }
}
