package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConsequenceSubmissionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void onlyUserSubmissionsEmitNavigationCompletionAndConsequenceRequestsAreOptional() {
    for (boolean background : new boolean[] {false, true}) {
      var f = new SubmissionCancellationTest.Fixture();
      f.scope.setId("context");
      var events = new ArrayList<Message>();
      f.scope.onMessage((channel, event) -> events.add(event), Message.Queue.Events);
      var cause = new Consequence.Cause(1, "context", 20, 21, 0, null, null, "decision",
          Consequence.Kind.CHANGE_PROCESS);
      var scope = background ? f.scope.forConsequence(cause) : f.scope;
      var observable = mock(Observable.class);
      var semantics = mock(Concept.class);
      when(observable.getSemantics()).thenReturn(semantics);
      when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.PROCESS));
      when(observable.getUrn()).thenReturn("test:process");
      var builder = mock(Observable.Builder.class, RETURNS_SELF);
      when(observable.builder(scope)).thenReturn(builder);
      when(builder.buildObservable()).thenAnswer(call -> {
        when(observable.isOptional()).thenReturn(true);
        return observable;
      });
      var observation = new ObservationImpl();
      observation.setObservable(observable);
      observation.setGeometry(Geometry.create("1"));
      var submission = f.runtime.submit(observation, scope);
      f.resolution.complete(f.dataflow);
      assertFalse(submission.join().isEmpty());
      verify(f.rootTransaction).commit();
      assertEquals(background, observable.isOptional());
      assertEquals(background ? 0 : 1, events.stream()
          .filter(event -> event.getMessageType() == Message.MessageType.ObservationSubmissionFinished).count());
      assertEquals(background ? cause : null, f.resolutionScope.get().getConsequenceCause());
      assertEquals(background
          ? org.integratedmodelling.klab.api.provenance.Activity.Type.CONSEQUENCE_SUBMISSION
          : org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION,
          f.rootTransaction.getActivity().getType());
    }
  }
}
