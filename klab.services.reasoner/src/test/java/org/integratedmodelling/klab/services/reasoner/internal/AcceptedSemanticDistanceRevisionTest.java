package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

class AcceptedSemanticDistanceRevisionTest {
  private Worldview world() {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setProjectName("test");
    ontology.setSourceCode("ontology w in domain root;");
    var world = new WorldviewImpl(); world.setUrn("w"); world.setWorldviewId("w"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology))); return world;
  }

  @Test void staleOrUnavailableGenerationNeverInvokesMatcherAndCurrentGenerationHoldsLeaseMonitor() throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    var state = mock(SemanticEnvironment.class);
    var actor = mock(ReasonerService.class);
    var output = mock(Concept.class); var requested = mock(Concept.class);
    when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "test", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,
        CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of()));
    when(state.actor()).thenReturn(actor);
    when(actor.semanticDistance(output, requested, null)).thenAnswer(i -> {
      assertTrue(Thread.holdsLock(state), "Matching must stay within the pinned state monitor"); return 17;
    });
    when(actor.assertedDistance(output, requested)).thenAnswer(i -> {
      assertTrue(Thread.holdsLock(state), "Observer hierarchy must stay within the pinned state monitor"); return 2;
    });
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      assertTrue(runtime.semanticDistanceAtRevision(output, requested, null, 1) < 0);
      assertTrue(runtime.assertedDistanceAtRevision(output, requested, 1) < 0);
      verifyNoInteractions(actor);
      runtime.submit(world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long generation = runtime.status().acceptedGeneration(); assertTrue(generation > 0);
      assertTrue(runtime.semanticDistanceAtRevision(output, requested, null, generation + 1) < 0);
      assertTrue(runtime.semanticDistanceAtRevision(output, requested, null, -1) < 0);
      assertTrue(runtime.assertedDistanceAtRevision(output, requested, generation + 1) < 0);
      verifyNoInteractions(actor);
      assertEquals(17, runtime.semanticDistanceAtRevision(output, requested, null, generation));
      verify(actor, times(1)).semanticDistance(output, requested, null);
      assertEquals(2, runtime.assertedDistanceAtRevision(output, requested, generation));
      verify(actor, times(1)).assertedDistance(output, requested);
    }
  }
}
