package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;

class ServiceSessionScopeTest {

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

    ContextScope contextScope = sessionScope.createContext(requested);

    assertFalse(contextScope.isEmpty());
    assertEquals("test_user.context-id", contextScope.getId());
    assertEquals("test_user.context-id", contextScope.getConfiguration().getId());
    assertEquals(Scope.Type.CONTEXT, ContextScope.parseScopeId(contextScope.getId()).type());
    verify(ownerService).declareContextScope(contextScope, sessionScope, userScope);
  }
}
