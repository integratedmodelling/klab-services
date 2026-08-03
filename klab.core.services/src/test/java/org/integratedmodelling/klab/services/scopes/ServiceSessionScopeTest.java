package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;

class ServiceSessionScopeTest {

  @Test
  void sharesOneObservationCacheWithoutCapturingTheUninstrumentedParent() {
    var user = mock(UserIdentity.class);
    var ownerService = mock(KlabService.class);
    var sessionScope = new ServiceSessionScope(new ServiceUserScope(user, ownerService));
    var configuration =
        DigitalTwin.Configuration.builder().id("test_user.context-id").name("test context").build();

    var uninstrumented = new ServiceContextScope(sessionScope, configuration, user);
    var instrumented = uninstrumented.withIdentity(user);
    var digitalTwin = mock(DigitalTwin.class);
    var knowledgeGraph = mock(KnowledgeGraph.class);
    var observation = mock(Observation.class);

    when(digitalTwin.getKnowledgeGraph()).thenReturn(knowledgeGraph);
    instrumented.setDigitalTwin(digitalTwin);
    var child = instrumented.within(mock(Observation.class));
    when(knowledgeGraph.getAsset(42L, child, Observation.class)).thenReturn(observation);

    assertSame(instrumented.observationCache, child.observationCache);
    assertSame(observation, child.getObservation(42L));
    assertSame(observation, instrumented.getObservation(42L));
    verify(knowledgeGraph, times(1)).getAsset(42L, child, Observation.class);
  }

  @Test
  void appliesRuntimeAssignedIdAndRegistersRemotePeer() {
    var user = mock(UserIdentity.class);
    var ownerService = mock(KlabService.class);
    var runtime = mock(RuntimeService.class);
    var runtimeStatus = mock(KlabService.ServiceStatus.class);

    when(user.getUsername()).thenReturn("test.user");
    when(ownerService.serviceId()).thenReturn("resolver-id");
    when(runtime.serviceId()).thenReturn("runtime-id");
    when(runtime.status()).thenReturn(runtimeStatus);
    when(runtimeStatus.isOperational()).thenReturn(true);

    var userScope = new ServiceUserScope(user, ownerService);
    userScope.setId("test.user");
    userScope.addService(runtime);

    var sessionScope = new ServiceSessionScope(userScope);
    sessionScope.setId("test_user");
    sessionScope.setHostServiceId("runtime-id");

    var requested = DigitalTwin.Configuration.builder().name("test context").build();
    var declared =
        DigitalTwin.Configuration.builder()
            .id("test_user.context-id")
            .name("test context")
            .serviceId("runtime-id")
            .build();
    when(runtime.declareContextScope(any(), any(), any())).thenReturn(declared);
    doAnswer(
            invocation -> {
              var peer = invocation.getArgument(0, ContextScope.class);
              assertEquals(declared.getId(), peer.getId());
              assertEquals(declared.getId(), peer.getConfiguration().getId());
              return declared;
            })
        .when(ownerService)
        .declareContextScope(any(), any(), any());

    ContextScope contextScope = sessionScope.createContext(requested);

    assertFalse(contextScope.isEmpty());
    assertEquals("test_user.context-id", contextScope.getId());
    assertEquals("test_user.context-id", contextScope.getConfiguration().getId());
    assertEquals(Scope.Type.CONTEXT, ContextScope.parseScopeId(contextScope.getId()).type());
    var declarationOrder = inOrder(runtime, ownerService);
    declarationOrder.verify(runtime).declareContextScope(contextScope, sessionScope, userScope);
    declarationOrder
        .verify(ownerService)
        .declareContextScope(contextScope, sessionScope, userScope);
  }

  @Test
  void localRuntimeDeclaresAndInstrumentsTheContextOnlyOnce() {
    var user = mock(UserIdentity.class);
    var runtime = mock(RuntimeService.class);
    var runtimeStatus = mock(KlabService.ServiceStatus.class);

    when(user.getUsername()).thenReturn("test.user");
    when(runtime.serviceId()).thenReturn("runtime-id");
    when(runtime.status()).thenReturn(runtimeStatus);
    when(runtimeStatus.isOperational()).thenReturn(true);

    var userScope = new ServiceUserScope(user, runtime);
    userScope.setId("test.user");
    userScope.addService(runtime);
    var sessionScope = new ServiceSessionScope(userScope);
    sessionScope.setId("test_user");
    sessionScope.setHostServiceId("runtime-id");
    var declared =
        DigitalTwin.Configuration.builder()
            .id("test_user.context-id")
            .name("test context")
            .serviceId("runtime-id")
            .build();
    when(runtime.declareContextScope(any(), any(), any())).thenReturn(declared);

    var contextScope =
        sessionScope.createContext(
            DigitalTwin.Configuration.builder().name("test context").build());

    assertEquals(declared.getId(), contextScope.getId());
    verify(runtime, times(1)).declareContextScope(contextScope, sessionScope, userScope);
  }
}
