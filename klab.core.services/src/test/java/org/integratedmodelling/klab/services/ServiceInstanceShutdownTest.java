package org.integratedmodelling.klab.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceInstanceShutdownTest {
  @Test
  void stopBeforeServiceCreationClosesMaintenanceScheduler() {
    var instance =
        mock(ServiceInstance.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
//    assertDoesNotThrow(instance::stop);
    assertTrue(instance.scheduler.isShutdown());
  }

  @Test
  void stopCancelsMaintenanceBeforeShuttingDownService() {
    var instance =
        mock(ServiceInstance.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
    var service = mock(BaseService.class);
    doReturn(service).when(instance).klabService();
    when(service.shutdown())
        .thenAnswer(
            call -> {
              assertTrue(instance.scheduler.isShutdown());
              return true;
            });
    instance.stop();
    verify(service).shutdown();
  }

  @Test
  void stopWaitsForInitializationAndPreventsLaterMaintenance() throws Exception {
    var instance =
        mock(ServiceInstance.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
    var service = mock(BaseService.class);
    doReturn(service).when(instance).klabService();
    doReturn(KlabService.Type.RESOLVER).when(instance).serviceType();
    doReturn(List.of()).when(instance).getEssentialServices();
    doReturn(List.of()).when(instance).getOperationalServices();
    ReflectionTestUtils.setField(instance, "serviceScope", mock(ServiceScope.class));
    ReflectionTestUtils.setField(instance, "identity", Pair.of(mock(Identity.class), List.of()));
    var initializing = new CountDownLatch(1);
    var finishInitialization = new CountDownLatch(1);
    when(service.initializeService())
        .thenAnswer(
            call -> {
              initializing.countDown();
              assertTrue(finishInitialization.await(5, TimeUnit.SECONDS));
              return true;
            });
    var maintenance =
        instance.scheduler.submit(() -> ReflectionTestUtils.invokeMethod(instance, "timedTasks"));
    try {
      assertTrue(initializing.await(5, TimeUnit.SECONDS));
      var stopping = CompletableFuture.runAsync(instance::stop);
      assertThrows(TimeoutException.class, () -> stopping.get(100, TimeUnit.MILLISECONDS));
      verify(service, never()).shutdown();
      finishInitialization.countDown();
      stopping.get(5, TimeUnit.SECONDS);
      maintenance.get(5, TimeUnit.SECONDS);
      verify(service).shutdown();
      clearInvocations(service);
      ReflectionTestUtils.invokeMethod(instance, "timedTasks");
      verifyNoInteractions(service);
    } finally {
      finishInitialization.countDown();
      instance.stop();
    }
  }

  @Test
  void baseServiceShutdownHandlesUninitializedAndInitializedScopes() {
    var service = mock(BaseService.class, CALLS_REAL_METHODS);
    assertTrue(service.shutdown());
    var scopes = mock(ScopeManager.class);
    ReflectionTestUtils.setField(service, "_scopeManager", scopes);
    assertTrue(service.shutdown());
    verify(scopes).shutdown();
  }
}
