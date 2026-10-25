package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContextActorObserverTest {
  @Test
  void submitTreatsNegatedObserverAsAnOptionInsteadOfDefinitionData() {
    var context = mock(ContextScope.class);
    var runtime = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    var twin = mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    var graph = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.class);
    var agentScope = mock(org.integratedmodelling.klab.runtime.kactors.AgentScope.class);
    var agent = mock(org.integratedmodelling.klab.api.actors.RuntimeAgent.class);
    var provenance = mock(org.integratedmodelling.klab.api.provenance.Agent.class);
    when(agentScope.getAgent()).thenReturn(agent);
    when(agent.getName()).thenReturn("test-agent");
    when(context.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(context.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(graph.requireAgent("test-agent")).thenReturn(provenance);
    when(context.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(context);
    var builder = mock(Observation.Builder.class, RETURNS_SELF);
    var observation = mock(Observation.class);
    when(builder.build()).thenReturn(observation);
    var pending = new java.util.concurrent.CompletableFuture<Observation>();
    when(runtime.submit(observation, context)).thenReturn(pending);
    var options = Metadata.create("observer", false);

    try (var observations = mockStatic(Observation.class)) {
      observations.when(() -> Observation.builder(context)).thenReturn(builder);
      assertSame(pending, new CoreActorLibrary.Context(context).submit(agentScope, options));
    }
    verify(builder).definition(isNull());
    assertEquals(false, options.get("observer"));
    verify(runtime).submit(observation, context);
  }

  @Test
  void explicitObserverUsesPersistentIdInFocusedSubmissionOnly() {
    var root = mock(ContextScope.class);
    var focused = mock(ContextScope.class);
    var selected = mock(ContextScope.class);
    var parent = mock(Observation.class);
    var observer = mock(Observation.class);
    var observable = mock(Observable.class);
    when(observer.getId()).thenReturn(12L);
    when(observer.getObservable()).thenReturn(observable);
    when(observable.is(SemanticType.AGENT)).thenReturn(true);
    when(root.within(parent)).thenReturn(focused);
    when(focused.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);

    assertSame(selected, ContextActorSupport.submissionScope(root,
        Metadata.create("within", parent, "observer", observer)));
    var constraint = ArgumentCaptor.forClass(ResolutionConstraint.class);
    verify(focused).withResolutionConstraints(constraint.capture());
    assertEquals(ResolutionConstraint.Type.Observer, constraint.getValue().getType());
    assertEquals(java.util.List.of(12L), constraint.getValue().payload(Long.class));
    verify(root, never()).withResolutionConstraints(any(ResolutionConstraint[].class));
  }

  @Test
  void negatedObserverInhibitsPreparationWithoutMutatingBorrowedScope() {
    var root = mock(ContextScope.class);
    var selected = mock(ContextScope.class);
    when(root.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);
    assertSame(selected, ContextActorSupport.submissionScope(root, Metadata.create("observer", false)));
    var constraint = ArgumentCaptor.forClass(ResolutionConstraint.class);
    verify(root).withResolutionConstraints(constraint.capture());
    assertEquals(ResolutionConstraint.Type.DoNotCreateDefaultObserver, constraint.getValue().getType());
    assertEquals(java.util.List.of(true), constraint.getValue().payload(Boolean.class));
    verify(root, never()).withObserver(any());
  }

  @Test
  void invalidObserversFailBeforeSubmitting() {
    var root = mock(ContextScope.class);
    for (var invalid : new Object[] {true, "urn:agent", mock(Observation.class)}) {
      assertThrows(IllegalArgumentException.class, () ->
          ContextActorSupport.submissionScope(root, Metadata.create("observer", invalid)));
    }
    verifyNoInteractions(root);
    assertSame(root, ContextActorSupport.submissionScope(root, Metadata.create()));
  }
}
