package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.storage.*;
import org.integratedmodelling.klab.services.resources.persistence.ModelCatalog;
import org.integratedmodelling.klab.services.resources.workflow.ReviewNotificationWorker;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewNotificationProviderTest {
  @TempDir Path directory;
  void field(ResourcesProvider service, String name, Object value) throws Exception {
    var field = ResourcesProvider.class.getDeclaredField(name); field.setAccessible(true); field.set(service, value);
  }
  @Test void providerAlsoEnforcesAuthenticatedServiceAdministration() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS); var workspace = mock(WorkspaceManager.class);
    field(service, "workspaceManager", workspace); when(workspace.getConfiguration()).thenReturn(new ResourcesConfiguration());
    var scope = mock(ServiceUserScope.class); var identity = mock(UserIdentity.class); when(scope.getUser()).thenReturn(identity);
    when(identity.isAuthenticated()).thenReturn(true);
    assertThrows(KlabResourceAccessException.class, () -> service.reviewNotificationHealth(50, scope));
    when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(true);
    assertEquals("NOT_INITIALIZED", service.reviewNotificationHealth(50, scope).reason());
    when(identity.isAnonymous()).thenReturn(true);
    assertThrows(KlabResourceAccessException.class, () -> service.reviewNotificationHealth(50, scope));
    when(identity.isAnonymous()).thenReturn(false); when(identity.isAuthenticated()).thenReturn(false);
    assertThrows(KlabResourceAccessException.class, () -> service.reviewNotificationHealth(50, scope));
  }
  @Test void providerShutdownStopsWorkerBeforeClosingResourceStores() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS); var store = mock(ResourcesKBox.class);
    var catalog = mock(ModelCatalog.class); var observed = new CountDownLatch(1);
    when(store.listFlows()).thenAnswer(invocation -> { observed.countDown(); return List.of(); });
    var worker = new ReviewNotificationWorker(store, directory); field(service, "reviewNotificationWorker", worker);
    field(service, "resourcesKbox", store); field(service, "kbox", catalog);
    doAnswer(invocation -> { assertEquals("STOPPED", worker.health(1).state()); return null; }).when(catalog).close();
    worker.start(); assertTrue(observed.await(5, TimeUnit.SECONDS)); assertTrue(service.shutdown(1));
    var order = inOrder(catalog, store); order.verify(catalog).close(); order.verify(store).shutdown();
  }
  @Test void providerInitializationStartsOneReplayWorkerUnderItsOwnDataDirectory() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS); var store = mock(ResourcesKBox.class);
    var observed = new CountDownLatch(1); when(store.listFlows()).thenAnswer(invocation -> { observed.countDown(); return List.of(); });
    field(service, "resourcesKbox", store);
    var options = org.integratedmodelling.common.services.ServiceStartupOptions.create(
        org.integratedmodelling.klab.api.services.KlabService.Type.RESOURCES, new String[]{"-dataDir", directory.toString()});
    var startup = org.integratedmodelling.klab.services.base.BaseService.class.getDeclaredField("startupOptions");
    startup.setAccessible(true); startup.set(service, options);
    assertTrue(service.initializeService());
    var field = ResourcesProvider.class.getDeclaredField("reviewNotificationWorker"); field.setAccessible(true);
    var worker = (ReviewNotificationWorker) field.get(service);
    try {
      assertTrue(service.initializeService()); assertSame(worker, field.get(service));
      assertTrue(observed.await(5, TimeUnit.SECONDS));
    } finally { worker.close(); }
    assertFalse(worker.health(1).deliveryEnabled());
    assertTrue(java.nio.file.Files.isRegularFile(directory.resolve("services/resources/private/review-notifications/review-notifications.lock")));
  }
}
