package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.Test;

class SemanticSearchAvailabilityTest {
  @Test
  void failedIndexIsAnErrorAndSuccessfulRetryRestoresSearch() throws Exception {
    var provider = mock(ResourcesProvider.class);
    var scope = mock(ContextScope.class);
    var serviceScope = mock(ServiceScope.class);
    var reasoner = mock(Reasoner.class, RETURNS_DEEP_STUBS);
    var workspace = mock(WorkspaceManager.class);
    var available = new AtomicBoolean();
    for (var entry : Map.of("semanticSearchAvailable", available, "workspaceManager", workspace).entrySet()) {
      var field = ResourcesProvider.class.getDeclaredField(entry.getKey());
      field.setAccessible(true);
      field.set(provider, entry.getValue());
    }
    when(provider.serviceScope()).thenReturn(serviceScope);
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(reasoner));
    when(reasoner.status().isOperational()).thenReturn(true);
    when(workspace.getNamespaces()).thenThrow(new IllegalStateException("index failure"));
    doCallRealMethod().when(provider).checkSemanticServices(scope);
    doCallRealMethod().when(provider).resolveModels(null, null, null, List.of(), scope);

    var result = provider.resolveModels(null, null, null, List.of(), scope);
    assertFalse(available.get());
    assertTrue(Utils.Notifications.hasErrors(result.getNotifications()));
    assertTrue(result.getResults().isEmpty());

    doReturn(List.of()).when(workspace).getNamespaces();
    assertTrue(provider.checkSemanticServices(scope));
    assertTrue(available.get());
  }
}
