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
  @Test void catalogPreparationRetriesInBackgroundWhenTheReasonerBecomesAvailable() {
    var provider=mock(ResourcesProvider.class);
    var scope=mock(ServiceScope.class);
    var reasoner=mock(Reasoner.class,RETURNS_DEEP_STUBS);
    when(provider.serviceScope()).thenReturn(scope);
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(reasoner));
    doCallRealMethod().when(provider).runAdditionalTimedTasks();
    provider.runAdditionalTimedTasks();
    verify(provider,never()).checkSemanticServices(scope);
    when(reasoner.status().isOperational()).thenReturn(true);
    provider.runAdditionalTimedTasks();
    verify(provider).checkSemanticServices(scope);
  }

  @Test void rejectedNamespaceDoesNotDisableValidNamespacesAndCorrectionRecovers() throws Exception {
    var provider = mock(ResourcesProvider.class);
    var scope = mock(ContextScope.class);
    var serviceScope = mock(ServiceScope.class);
    var reasoner = mock(Reasoner.class, RETURNS_DEEP_STUBS);
    var workspace = mock(WorkspaceManager.class);
    var catalog = mock(org.integratedmodelling.klab.services.resources.persistence.ModelCatalog.class);
    var available = new AtomicBoolean();
    for (var entry : Map.of("semanticSearchAvailable", available, "workspaceManager", workspace, "kbox", catalog).entrySet()) {
      var field = ResourcesProvider.class.getDeclaredField(entry.getKey());
      field.setAccessible(true); field.set(provider, entry.getValue());
    }
    when(provider.serviceScope()).thenReturn(serviceScope);
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(reasoner));
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.status().isOperational()).thenReturn(true);
    when(reasoner.capabilities(scope).getKnowledgeRevision()).thenReturn(7L);
    var good = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl(); good.setUrn("good");
    var bad = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl(); bad.setUrn("bad");
    var invalid = new AtomicBoolean(true);
    when(workspace.getNamespaces()).thenReturn(List.of(good, bad));
    when(reasoner.validateDocument(any(), same(scope))).thenAnswer(invocation -> {
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request = invocation.getArgument(0);
      var response = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request);
      response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(7);
      if (invalid.get() && request.getNamespace() == bad)
        response.getNotifications().add(org.integratedmodelling.klab.api.services.runtime.Notification.error("invalid dependency"));
      return response;
    });
    doCallRealMethod().when(provider).checkSemanticServices(scope);
    doCallRealMethod().when(provider).getNamespaceModelDiagnostics();
    assertTrue(provider.checkSemanticServices(scope)); assertTrue(available.get());
    verify(catalog).replaceNamespace(good, scope); verify(catalog).withholdNamespace("bad", scope);
    verify(catalog, never()).replaceNamespace(bad, scope);
    assertEquals(java.util.Set.of("bad"), provider.getNamespaceModelDiagnostics().keySet());
    assertTrue(bad.getNotifications().isEmpty());
    invalid.set(false); bad.setSourceCode("corrected source");
    assertTrue(provider.checkSemanticServices(scope));
    verify(catalog).replaceNamespace(bad, scope);
    verify(catalog, times(1)).replaceNamespace(good, scope);
    assertTrue(provider.getNamespaceModelDiagnostics().isEmpty());
  }

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
    doCallRealMethod().when(provider).resolveModels(null, null, null, List.of(), scope,
        org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode.EXECUTABLE);

    var result = provider.resolveModels(null, null, null, List.of(), scope);
    assertFalse(available.get());
    assertTrue(Utils.Notifications.hasErrors(result.getNotifications()));
    assertTrue(result.getResults().isEmpty());

    doReturn(List.of()).when(workspace).getNamespaces();
    assertTrue(provider.checkSemanticServices(scope));
    assertTrue(available.get());
    var capabilities = reasoner.capabilities(scope);
    doThrow(new IllegalStateException("revision unavailable")).when(reasoner).capabilities(scope);
    assertFalse(provider.checkSemanticServices(scope));
    assertFalse(available.get());
    doReturn(capabilities).when(reasoner).capabilities(scope);
    assertTrue(provider.checkSemanticServices(scope));
  }
}
