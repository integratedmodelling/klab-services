package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ObserverInspectorTest {
  static class Fixture {
    final ContextScope scope = mock(ContextScope.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final Map<Long, Observation> assets = new HashMap<>();
    final Map<Long, List<Activity>> creators = new HashMap<>();
    final Map<Long, List<Observation>> selections = new HashMap<>();
    final Observation root = observation(10), observer = observation(20);
    final Activity submission = activity(100, Activity.Type.SUBMISSION, 20);
    final Activity resolution = activity(101, Activity.Type.RESOLUTION, 20);
    final Activity observerSubmission = activity(200, Activity.Type.SUBMISSION, 0);
    final Activity observerResolution = activity(201, Activity.Type.RESOLUTION, 0);
    Fixture() {
      var twin = mock(DigitalTwin.class); when(scope.getDigitalTwin()).thenReturn(twin);
      when(twin.getKnowledgeGraph()).thenReturn(graph);
      creators.put(10L, new ArrayList<>(List.of(submission, resolution)));
      creators.put(20L, new ArrayList<>(List.of(observerSubmission, observerResolution)));
      selections.put(100L, List.of(observer)); selections.put(101L, List.of(observer));
      when(graph.getAsset(anyLong(), eq(scope), eq(Observation.class)))
          .thenAnswer(call -> assets.get(call.getArgument(0)));
      when(graph.query(any(Class.class), eq(scope))).thenAnswer(call ->
          new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(call.getArgument(0, Class.class))));
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Activity.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getTarget() == null ? List.of()
            : creators.getOrDefault(query.getTarget().getId(), List.of());
      });
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Observation.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getSource() == null ? List.of()
            : selections.getOrDefault(query.getSource().getId(), List.of());
      });
    }
    Observation observation(long id) {
      var result = mock(Observation.class); var observable = mock(Observable.class);
      var concept = mock(Concept.class);
      when(result.getId()).thenReturn(id); when(result.getObservable()).thenReturn(observable);
      when(observable.getSemantics()).thenReturn(concept);
      when(observable.is(SemanticType.AGENT)).thenReturn(true);
      assets.put(id, result); return result;
    }
    Activity activity(long id, Activity.Type type, long selected) {
      var result = mock(Activity.class); when(result.getId()).thenReturn(id);
      when(result.getType()).thenReturn(type); when(result.getOutcome()).thenReturn(Activity.Outcome.SUCCESS);
      when(result.getMetadata()).thenReturn(Metadata.create(Metadata.IM_OBSERVER_SELECTION_ID, selected));
      return result;
    }
    boolean check() { return ObservationInspector.observerCheck(scope, root, observer); }
  }

  @Test void actualObserverRequiresCommittedIdentityAndSuccessfulSubmissionAndResolution() {
    var f = new Fixture(); assertTrue(f.check());
    f.creators.get(20L).remove(f.observerResolution); assertFalse(f.check());
    f.creators.get(20L).add(f.observerResolution);
    when(f.observerResolution.getOutcome()).thenReturn(Activity.Outcome.FAILURE); assertFalse(f.check());
  }

  @Test void unknownAndContradictorySelectionsCannotPassAsKnownNull() {
    var f = new Fixture(); f.creators.remove(10L);
    assertFalse(ObservationInspector.observerCheck(f.scope, f.root, null));
    f.creators.put(10L, List.of(f.submission));
    f.submission.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L);
    assertThrows(IllegalStateException.class, () -> ObservationInspector.observerCheck(f.scope, f.root, null));
    f.selections.clear(); assertTrue(ObservationInspector.observerCheck(f.scope, f.root, null));
  }

  @Test void wrongObserverOrInaccessibleIdentityCannotPass() {
    var f = new Fixture(); var other = f.observation(30);
    assertFalse(ObservationInspector.observerCheck(f.scope, f.root, other));
    f.assets.remove(20L); assertFalse(f.check());
  }

  @Test void abstractCollectiveAndForgedSemanticObserverCannotPass() {
    var f = new Fixture(); when(f.observer.getObservable().is(SemanticType.ABSTRACT)).thenReturn(true);
    assertThrows(IllegalArgumentException.class, f::check);
    when(f.observer.getObservable().is(SemanticType.ABSTRACT)).thenReturn(false);
    when(f.observer.getObservable().getSemantics().isCollective()).thenReturn(true);
    assertThrows(IllegalArgumentException.class, f::check);
    when(f.observer.getObservable().getSemantics().isCollective()).thenReturn(false);
    var forged = f.observation(20); f.assets.put(20L, f.observer);
    assertFalse(ObservationInspector.observerCheck(f.scope, f.root, forged));
  }

  @Test void duplicateCreationEvidenceAndMissingObserverLinksCannotPass() {
    var f = new Fixture(); f.creators.get(20L).add(f.observerSubmission); assertFalse(f.check());
    f.creators.get(20L).removeLast(); f.selections.remove(101L);
    assertThrows(IllegalStateException.class, f::check);
  }
  @Test void failedAndDuplicateObservedCreatorsCannotCertifyPositiveOrKnownNullSelection() {
    var f = new Fixture(); when(f.submission.getOutcome()).thenReturn(Activity.Outcome.FAILURE);
    assertFalse(f.check());
    when(f.submission.getOutcome()).thenReturn(Activity.Outcome.SUCCESS);
    f.creators.get(10L).add(f.submission); assertFalse(f.check());
    f.creators.get(10L).removeLast(); f.submission.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L);
    f.resolution.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L); f.selections.clear();
    assertTrue(ObservationInspector.observerCheck(f.scope, f.root, null));
    f.creators.get(10L).add(f.submission);
    assertFalse(ObservationInspector.observerCheck(f.scope, f.root, null));
    f.creators.get(10L).removeLast(); when(f.submission.getOutcome()).thenReturn(Activity.Outcome.FAILURE);
    assertFalse(ObservationInspector.observerCheck(f.scope, f.root, null));
  }

  @Test void inaccessibleOrForgedObservedIdentityCannotPass() {
    var f = new Fixture(); var forged = f.observation(10); f.assets.put(10L, f.root);
    assertFalse(ObservationInspector.observerCheck(f.scope, forged, f.observer));
    f.assets.put(10L, f.observer); assertFalse(f.check());
    f.assets.remove(10L); assertFalse(f.check());
  }
}
