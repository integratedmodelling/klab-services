package org.integratedmodelling.common.services.client.engine;

import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.common.services.client.ResolverClient;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import java.util.List;
import java.util.Set;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.junit.jupiter.api.Test;

class EngineImplTest {

  @Test
  void newlyOperationalRuntimeIsAdvertisedToExistingResolver() throws Exception {
    var engine = new EngineImpl(null, null);
    var user = Mockito.mock(UserScope.class);
    var identity = Mockito.mock(UserIdentity.class);
    Mockito.when(user.getUser()).thenReturn(identity);
    Mockito.when(identity.getData()).thenReturn(
        Parameters.create());
    Mockito.when(identity.getGroups()).thenReturn(Set.of());
    var monitor = Mockito.mock(ServiceMonitor.class);
    var resolver = Mockito.mock(ResolverClient.class);
    var runtime = Mockito.mock(RuntimeClient.class);
    var peers = List.<KlabService>of(resolver, runtime);
    for (var peer : peers) {
      var type = peer == resolver
          ? KlabService.Type.RESOLVER
          : KlabService.Type.RUNTIME;
      var status = new ServiceStatusImpl();
      status.setServiceId(type.name());
      status.setServiceType(type);
      status.setOperational(true);
      Mockito.when(peer.serviceId()).thenReturn(type.name());
      Mockito.when(peer.status()).thenReturn(status);
      Mockito.when(peer.getUrl()).thenReturn(
          URI.create("http://localhost/" + type.name()).toURL());
      Mockito.when(((BaseServiceClient) peer)
          .notifyScope(ArgumentMatchers.any())).thenReturn(true);
    }
    Mockito.when(user.getServices(KlabService.class))
        .thenReturn(peers);
    Mockito.when(monitor.getAllServices(KlabService.class))
        .thenReturn(peers);
    setField(engine, "defaultUser", user);
    setField(engine, "serviceMonitor", monitor);
    setField(engine, "onlineStatusNotified", true);
    engine.getUsers().add(user);
    try {
      var notify = EngineImpl.class.getDeclaredMethod("notifyLocalService",
          KlabService.class,
          KlabService.ServiceStatus.class);
      notify.setAccessible(true);
      notify.invoke(engine, runtime, runtime.status());
      Mockito.verify(resolver, Mockito.timeout(5000)).notifyScope(
          ArgumentMatchers.argThat(n -> n.getServices().stream()
              .anyMatch(s -> "RUNTIME".equals(s.getId()))));
      Mockito.verify(runtime, Mockito.timeout(5000))
          .notifyScope(ArgumentMatchers.any());
    } finally {
      var field = EngineImpl.class.getDeclaredField("scopeAdvertisements");
      field.setAccessible(true);
      ((ScopeAdvertisements) field.get(engine)).close();
    }
  }

  private static void setField(EngineImpl engine, String name, Object value) throws Exception {
    var field = EngineImpl.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(engine, value);
  }

  @Test
  void roundTripsPhysicalDistributionIdentity() {
    var tag = Stack.Tag.of(Version.create("1.2.3-alpha1"), "develop", "202608061030", true, false);

    var restored =
        EngineImpl.deserializeDistributionTag(EngineImpl.serializeDistributionTag(tag));

    assertEquals(tag.version(), restored.version());
    assertEquals(tag.release(), restored.release());
    assertEquals(tag.build(), restored.build());
  }

  @Test
  void roundTripsHeadAndNullableSegments() {
    var tag = Stack.Tag.of(Version.HEAD, null, "source", true, false);

    var restored =
        EngineImpl.deserializeDistributionTag(EngineImpl.serializeDistributionTag(tag));

    assertEquals(Version.HEAD, restored.version());
    assertNull(restored.release());
    assertEquals(tag.build(), restored.build());
  }

  @Test
  void rejectsInvalidPersistedTags() {
    assertNull(EngineImpl.deserializeDistributionTag(""));
    assertNull(EngineImpl.deserializeDistributionTag("not-a-tag"));
    assertNull(EngineImpl.deserializeDistributionTag("1.2.3|invalid*base64|also-invalid"));
  }
}
