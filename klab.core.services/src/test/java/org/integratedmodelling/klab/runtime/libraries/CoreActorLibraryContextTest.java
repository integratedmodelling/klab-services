package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CoreActorLibraryContextTest {

  @Test
  void leavesNewContextIdForRuntimeToAssign() {
    var agentScope = mock(AgentScope.class);
    var agent = mock(RuntimeAgent.class);
    var sessionScope = mock(SessionScope.class);
    var user = mock(UserIdentity.class);
    var runtime = mock(RuntimeService.class);

    when(agentScope.getAgent()).thenReturn(agent);
    when(agent.getCreationScope()).thenReturn(sessionScope);
    when(sessionScope.getUser()).thenReturn(user);
    when(sessionScope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.serviceId()).thenReturn("runtime-id");
    when(sessionScope.createContext(any())).thenReturn(mock(ContextScope.class));

    CoreActorLibrary.Context.createContext(agentScope);

    var configuration = ArgumentCaptor.forClass(DigitalTwin.Configuration.class);
    verify(sessionScope).createContext(configuration.capture());
    assertNull(configuration.getValue().getId());
  }
}
