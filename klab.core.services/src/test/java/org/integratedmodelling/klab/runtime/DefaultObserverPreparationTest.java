package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultObserverPreparationTest {
  ContextScope scope = mock(ContextScope.class);
  KnowledgeGraph graph = mock(KnowledgeGraph.class);
  Reasoner reasoner = mock(Reasoner.class);
  Observable observable = mock(Observable.class);
  Concept concept = mock(Concept.class);
  WorldviewImpl worldview = new WorldviewImpl();
  Geometry geometry = mock(Geometry.class);

  @BeforeEach
  void setup() {
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("alice");
    when(user.getGroups()).thenReturn(List.of());
    when(scope.getUser()).thenReturn(user);
    when(scope.getId()).thenReturn("session.twin");
    var twin = mock(DigitalTwin.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveObservable("people:User")).thenReturn(observable);
    when(observable.getSemantics()).thenReturn(concept);
    when(observable.getUrn()).thenReturn("people:User");
    when(concept.is(SemanticType.AGENT)).thenReturn(true);
    worldview.getMetadata().put(Worldview.USER_OBSERVER_SEMANTICS, "people:User");
  }

  @Test
  void absentDefaultDoesNotTouchGraphOrReasoner() {
    worldview.getMetadata().clear();
    assertNull(DefaultObserverPreparation.prepare(worldview, scope, null).join());
    verifyNoInteractions(graph, reasoner);
  }

  @Test
  void existingAgentIsReusedWithoutSubmissionOrGeometry() {
    var existing = mock(Observation.class);
    when(existing.getObservable()).thenReturn(observable);
    when(graph.getAsset(anyString(), eq(scope), eq(Observation.class))).thenReturn(existing);
    assertSame(existing, DefaultObserverPreparation.prepare(worldview, scope, null).join());
    verify(scope, never()).observation(any(Observable.class));
  }

  @Test
  void missingAgentUsesSubmissionFutureAndStableIdentity() {
    var builder = mock(Observation.Builder.class, RETURNS_SELF);
    var result = new CompletableFuture<Observation>();
    when(scope.observation(observable)).thenReturn(builder);
    when(builder.submit()).thenReturn(result);
    assertSame(result, DefaultObserverPreparation.prepare(worldview, scope, geometry));
    verify(builder).identity(argThat(urn -> urn.getUrn().startsWith("klab.user:")));
    verify(builder).geometry(geometry);
    verify(builder).submit();
  }

  @Test
  void conflictingStoredSemanticsAndInvalidAgentDoNotCreateDuplicates() {
    var existing = mock(Observation.class);
    when(existing.getObservable()).thenReturn(mock(Observable.class));
    when(graph.getAsset(anyString(), eq(scope), eq(Observation.class))).thenReturn(existing);
    assertTrue(DefaultObserverPreparation.prepare(worldview, scope, geometry).isCompletedExceptionally());
    when(concept.is(SemanticType.ABSTRACT)).thenReturn(true);
    assertTrue(DefaultObserverPreparation.prepare(worldview, scope, geometry).isCompletedExceptionally());
    verify(scope, never()).observation(any(Observable.class));
  }
}
