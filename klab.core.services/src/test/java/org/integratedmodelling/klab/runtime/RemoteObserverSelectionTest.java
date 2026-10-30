package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class RemoteObserverSelectionTest {
  private ObservationImpl agent(long id) {
    var concept = new ConceptImpl();
    concept.setUrn("test:Agent");
    concept.setType(EnumSet.of(SemanticType.AGENT));
    var observable = new ObservableImpl();
    observable.setSemantics(concept);
    var result = new ObservationImpl();
    result.setId(id);
    result.setObservable(observable);
    return result;
  }

  private ServiceContextScope scope(RuntimeClient runtime) {
    var user = mock(UserIdentity.class);
    when(user.getId()).thenReturn("alice");
    var session = new ServiceSessionScope(new ServiceUserScope(user, mock(RuntimeService.class)));
    var result = new ServiceContextScope(session,
        DigitalTwin.Configuration.builder().id("session.twin").name("Twin").build(), user);
    result.setId("session.twin");
    var twin = mock(DigitalTwin.class);
    when(twin.getKnowledgeGraph()).thenReturn(new ClientKnowledgeGraph(result, runtime));
    result.setDigitalTwin(twin);
    return result;
  }

  private void reply(RuntimeClient runtime, List<Observation> observations) {
    reply(runtime, observations, "session.twin");
  }

  private void reply(RuntimeClient runtime, List<Observation> observations, String scopeId) {
    doAnswer(call -> {
      var requestScope = call.getArgument(1, ContextScope.class);
      // RuntimeClient encodes the scope before sending the HTTP query. This used to recurse.
      assertEquals(scopeId, ContextScope.getScopeId(requestScope));
      assertNull(requestScope.getConstraint(ResolutionConstraint.Type.Observer, Long.class));
      assertEquals("alice", requestScope.getUser().getId());
      return observations;
    }).when(runtime).queryKnowledgeGraph(any(), any());
  }

  @Test
  void remoteRefreshEncodesScopeWithoutRecursingAndRetainsSelection() {
    var runtime = mock(RuntimeClient.class);
    var selected = agent(12);
    var refreshed = agent(12);
    var root = scope(runtime);
    var connection = root.withObserver(selected);
    reply(runtime, List.of(refreshed));

    assertSame(refreshed, connection.getObserver());
    assertEquals("session.twin#12", ContextScope.getScopeId(connection));
    assertNull(root.getObserver());
    verify(runtime, times(1)).queryKnowledgeGraph(any(), any());
  }

  @Test
  void remoteSelectionCanReplaceAnInheritedObserver() {
    var runtime = mock(RuntimeClient.class);
    var connection = scope(runtime).withObserver(agent(11));
    var replacement = agent(12);
    reply(runtime, List.of(replacement));

    var selected = connection.withResolutionConstraints(
        ResolutionConstraint.of(ResolutionConstraint.Type.Observer, 12L));
    assertSame(replacement, selected.getObserver());
    assertEquals(11L, connection.getConstraint(ResolutionConstraint.Type.Observer, Long.class));
    assertEquals("session.twin#12", ContextScope.getScopeId(selected));
  }

  @Test
  void remoteRefreshPreservesContextFocus() {
    var runtime = mock(RuntimeClient.class);
    var context = new ObservationImpl();
    context.setId(23);
    var connection = scope(runtime).withObserver(agent(12)).within(context);
    var refreshed = agent(12);
    reply(runtime, List.of(refreshed), "session.twin.23");

    assertSame(refreshed, connection.getObserver());
    assertSame(context, connection.getContextObservation());
    assertEquals("session.twin.23#12", ContextScope.getScopeId(connection));
  }

  @Test
  void remoteRefreshStillRejectsMissingOrNonAgentObservers() {
    var runtime = mock(RuntimeClient.class);
    var connection = scope(runtime).withObserver(agent(12));
    reply(runtime, List.of());
    assertThrows(IllegalStateException.class, connection::getObserver);

    var wrongKind = agent(12);
    ((ConceptImpl) wrongKind.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.SUBJECT));
    reply(runtime, List.of(wrongKind));
    assertThrows(IllegalStateException.class, connection::getObserver);
  }
}
