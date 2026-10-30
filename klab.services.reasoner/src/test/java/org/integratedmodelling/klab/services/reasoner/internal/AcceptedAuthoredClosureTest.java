package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

class AcceptedAuthoredClosureTest {
  static Worldview source(String provider, String content) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("audit"); ontology.setServiceId(provider);
    ontology.setSourceCode(content);
    var world = new WorldviewImpl(); world.setUrn("audit"); world.setWorldviewId("audit"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology))); return world;
  }
  static ConceptImpl concept(String urn) {
    var c = new ConceptImpl(); c.setUrn(urn); c.setType(EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE)); return c;
  }
  @Test void queryBindsAndEvaluatesWithinOneAcceptedActorAcrossGenerationReplacement() throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    var state = new AtomicReference<SemanticEnvironment>();
    when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "test", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenAnswer(i -> new CandidatePublicationCoordinator.Result<>(state.get(),
        CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of()));
    var caller = concept("audit:Category");
    var root = mock(ReasonerService.class, CALLS_REAL_METHODS);
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      var field = ReasonerService.class.getDeclaredField("candidateRuntime"); field.setAccessible(true); field.set(root, runtime);
      assertThrows(IllegalStateException.class, () -> root.authoredClosure(caller));
      long previous = -1;
      for (var provider : List.of("provider-A", "provider-B")) {
        var environment = mock(SemanticEnvironment.class); var actor = mock(ReasonerService.class);
        var rebound = concept("audit:Category"); var candidate = concept("audit:" + (provider.endsWith("A") ? "A" : "B"));
        when(environment.actor()).thenReturn(actor); when(actor.resolveConcept(caller.getUrn())).thenReturn(rebound);
        when(actor.authoredClosure(rebound)).thenAnswer(i -> {
          assertTrue(Thread.holdsLock(environment)); assertSame(rebound, i.getArgument(0)); return List.of(candidate);
        });
        state.set(environment); runtime.submit(source(provider, "ontology audit; // " + provider));
        assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertTrue(runtime.status().acceptedGeneration() > previous); previous = runtime.status().acceptedGeneration();
        var result = root.authoredClosure(caller);
        assertEquals(List.of(candidate.getUrn()), result.stream().map(Concept::getUrn).toList());
        assertNotSame(candidate, result.iterator().next());
        verify(actor).authoredClosure(rebound); verify(actor).resolveConcept(caller.getUrn());
      }
    }
  }
}
