package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleRequest;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;

/** Exercise Runtime ownership against the real Reasoner lease registry, without restarting it. */
class SemanticLeaseCleanupTest {
  private static AcceptedSemanticRuntime accepted() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var ontology = new KimOntologyImpl();
    ontology.setUrn("w"); ontology.setProjectName("test");
    ontology.setSourceCode("ontology w in domain root;");
    var world = new WorldviewImpl();
    world.setUrn("w"); world.setWorldviewId("w"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));
    var state = mock(SemanticEnvironment.class);
    when(state.semanticContentFingerprint()).thenReturn("fingerprint");
    var factory = mock(SemanticEnvironmentFactory.class);
    when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any())).thenAnswer(call -> {
      Worldview input = call.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(
          SemanticEnvironmentFactory.sources(input), SemanticEnvironmentFactory.sourceOrder(input),
          "test", SemanticEnvironmentFactory.CONFIGURATION_REVISION, input,
          CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenReturn(new CandidatePublicationCoordinator.Result<>(
        state, CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of()));
    var accepted = new AcceptedSemanticRuntime(factory, "reasoner");
    accepted.submit(world);
    assertTrue(accepted.awaitIdle(Duration.ofSeconds(5)));
    return accepted;
  }

  private static void connect(ResolutionCyclesTest.Fixture f, AcceptedSemanticRuntime accepted) {
    var authorized = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var identity = f.scope.getUser();
    when(authorized.getId()).thenReturn("context");
    when(authorized.getUser()).thenReturn(identity);
    when(authorized.getService(RuntimeService.class)).thenReturn(f.runtime);
    var cleanupOwner = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    when(cleanupOwner.getUser()).thenReturn(identity);
    when(f.reasoner.beginSemanticCycleResult(anyString(), anyString(), anyString(), any()))
        .thenAnswer(call -> accepted.beginCycleResult(new SemanticCycleRequest(
            call.getArgument(0), call.getArgument(1), call.getArgument(2), null), authorized));
    doAnswer(call -> {
      accepted.releaseCycle(new SemanticCycleRequest(call.getArgument(1), call.getArgument(2),
          call.getArgument(3), call.getArgument(0)), cleanupOwner);
      return null;
    }).when(f.reasoner).releaseSemanticCycle(any(), anyString(), anyString(), anyString(), any());
  }

  @Test void completedCyclesReturnActualReasonerLeaseCountToZeroWithoutRestart() throws Exception {
    try (var accepted = accepted(); var f = new ResolutionCyclesTest.Fixture()) {
      connect(f, accepted);
      for (int i = 0; i < 3; i++) {
        var root = f.cycles.admit(f.scope, true).get(5, TimeUnit.SECONDS);
        var child = f.cycles.admit(root.scope(), true).get(5, TimeUnit.SECONDS);
        assertEquals(1, accepted.activeCycleCount());
        root.close();
        assertEquals(1, accepted.activeCycleCount(), "Real causal work still owns the pin");
        child.close();
        f.cycles.awaitQuiescence(5, TimeUnit.SECONDS);
        assertEquals(0, accepted.activeCycleCount());
      }
    }
  }

  @Test void closedContextsReturnActualReasonerLeaseCountToZeroBeforeLateOwnersFinish() throws Exception {
    try (var accepted = accepted()) {
      for (int i = 0; i < 3; i++) {
        try (var f = new ResolutionCyclesTest.Fixture()) {
          connect(f, accepted);
          var root = f.cycles.admit(f.scope, true).get(5, TimeUnit.SECONDS);
          var child = root.scope().getResolutionCycle().retain();
          assertEquals(1, accepted.activeCycleCount());
          f.cycles.close();
          assertEquals(0, accepted.activeCycleCount(), "Closing must release the remote pin now");
          assertFalse(root.scope().getResolutionCycle().quiescence().toCompletableFuture().isDone());
          root.close(); child.close();
          f.cycles.awaitQuiescence(5, TimeUnit.SECONDS);
          assertEquals(0, accepted.activeCycleCount());
        }
      }
    }
  }
}
