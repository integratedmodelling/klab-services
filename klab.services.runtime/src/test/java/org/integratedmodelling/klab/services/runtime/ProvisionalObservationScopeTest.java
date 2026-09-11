package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ProvisionalObservationScopeTest {
  @Test
  void registeredCandidateRoundTripsAsContextWithoutEnteringCommitGraph() {
    var runtime = mock(RuntimeService.class);
    doCallRealMethod().when(runtime).register(any(), any());
    when(runtime.serviceId()).thenReturn("runtime");
    var user = mock(UserIdentity.class);
    var session = new ServiceSessionScope(new ServiceUserScope(user, runtime));
    var context = new ServiceContextScope(session,
        DigitalTwin.Configuration.builder().id("test.context").name("test").build(), user);
    var root = mock(DigitalTwin.Transaction.class);
    var child = mock(DigitalTwin.Transaction.class);
    when(root.getId()).thenReturn("root");
    when(child.getId()).thenReturn("resolution");
    when(child.getParent()).thenReturn(root);
    when(root.assets()).thenReturn(List.of());
    when(child.assets()).thenReturn(List.of());
    context.registerTransaction(root);
    context.registerTransaction(child);
    var candidate = new ObservationImpl();
    var observable = mock(Observable.class);
    var concept = mock(Concept.class);
    when(observable.getSemantics()).thenReturn(concept);
    when(concept.getType()).thenReturn(java.util.EnumSet.of(SemanticType.SUBJECT));
    when(concept.isCollective()).thenReturn(true);
    candidate.setObservable(observable);
    runtime.register(candidate, context.withTransaction(child));
    assertTrue(candidate.getId() < -1);

    var reconstructed = new ScopeManager(runtime).contextualizeScope(context,
        new ContextScope.ScopeData(Scope.Type.CONTEXT, "test.context", null, -1),
        Map.of(ServicesAPI.TRANSACTION_ID_HEADER, "resolution",
            ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER, Long.toString(candidate.getId())));
    assertSame(candidate, reconstructed.getContextObservation());
    assertNull(context.getObservation(candidate.getId()));
    verify(child, never()).add(any());
    verify(root, never()).add(any());

    context.unregisterTransaction(child);
    assertSame(candidate, context.withTransaction(root).getObservation(candidate.getId()));
    context.unregisterTransaction(root);
    assertNull(context.withTransaction(root).getObservation(candidate.getId()));
  }
}
