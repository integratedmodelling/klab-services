package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.junit.jupiter.api.*;

/** Existing abstract-dependency context boundary and portable typed identity guards.
 * Concrete collective selection does not broaden the old singleton abstract-classifier route. */
class CollectiveSelectionNativeContextTest {
  @BeforeAll static void configure() { SelectedModelClassifierSubmissionTest.configure(); }

  @Test void fullResolverPreservesTheExistingObservedSingularContextBoundary() throws Exception {
    try (var f = new SelectedModelClassifierSubmissionTest.Fixture()) {
      f.semantic.otherIsRegionSubtype();
      var nativeSyntax = f.semantic.named("Other"); nativeSyntax.setCollective(true); f.semantic.register(nativeSyntax);
      var nativeObservable = f.observable(nativeSyntax); f.remember(nativeObservable);
      var discovery = new ContextualizableImpl(); discovery.setServiceCall(new ServiceCallImpl("fixture.discover"));
      f.model("dependent_instantiator", nativeObservable, List.of(f.original), discovery);
      var execution = f.scope.executing(Activity.of(Activity.Type.SUBMISSION, null, "Native context boundary"));
      var requested = new ObservationImpl(); requested.setObservable(f.reasoner.declareObservable(nativeObservable)); requested.setGeometry(f.geometry);
      var target = f.runtime.register(requested, execution);
      assertTrue(target.getId() < -1);
      var incoming = f.resolver.resolve(target, execution).get(30, TimeUnit.SECONDS);
      assertEquals(Dataflow.ResolutionOutcome.FAILED, incoming.getResolutionOutcome());
      assertTrue(incoming.getNotifications().stream().anyMatch(note -> note.getMessage().contains(
          "Classifier dependency requires an already observed singular native context")), incoming.getNotifications().toString());
      assertEquals(0, f.classifier.discoveries.get(), "A required ordinary abstract classifier cannot run on this unsupported collective context");
      assertEquals(0, f.rootCommits.get());
      execution.fail(new IllegalStateException("Test attempt cleanup")); assertEquals(-1, execution.commit());
    }
  }

  @Test void sameProvisionalIdCannotSubstituteAnotherTransportedContext() throws Exception {
    var q = new CollectiveSelectionExecutionTest(); q.setup();
    var context = q.f.producer;
    var witness = ClassifierDependencyPlan.ContextWitness.capture(context, q.f.scope.getId());
    var plan = new ClassifierDependencyPlan("model", null, 0, null, 0, "input", null,
        context.getId(), context.getObservable().getSemantics().getUrn(), context.getGeometry().encode(),
        "semantic-test", 7, List.of(), witness);
    assertDoesNotThrow(() -> SelectedModelClassifierContinuation.requireContext(plan, context, q.f.scope));
    var copy = Observation.forTransport(context);
    assertDoesNotThrow(() -> SelectedModelClassifierContinuation.requireContext(plan, copy, q.f.scope));
    ((ObservationImpl) copy).setTransientId(context.getTransientId() + 1);
    assertThrows(IllegalStateException.class, () -> SelectedModelClassifierContinuation.requireContext(plan, copy, q.f.scope));
    var legacy = new ClassifierDependencyPlan("model", null, 0, null, 0, "input", null,
        context.getId(), context.getObservable().getUrn(), context.getGeometry().encode(), "semantic-test", 7, List.of());
    assertThrows(IllegalStateException.class, () -> SelectedModelClassifierContinuation.requireContext(legacy, context, q.f.scope));
  }
}
