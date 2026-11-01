package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.services.application.controllers.KlabScopeController;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;

class UserScopeAdvertisementTest {
  @Test
  void repeatedAdvertisementsDoNotAccumulateServices() {
    var service = mock(Reasoner.class);
    when(service.serviceId()).thenReturn("reasoner");
    var user = new UserIdentityImpl();
    user.setUsername("alice");
    var managed = new ServiceUserScope(user, service);
    for (int i = 0; i < 1000; i++) {
      var request = managed.forAuthorization(authorization("token-" + i));
      var topology = new UserScopeNotification();
      var info = new UserScopeNotification.ServiceInfo();
      info.setId("reasoner");
      topology.getServices().add(info);
      topology.getServices().add(info);
      request.advertiseServices(topology);
      assertEquals(1, request.getServices(KlabService.class).size());
    }
    assertEquals(1, managed.forAuthorization(authorization("last")).getServices(KlabService.class).size());
  }

  @Test
  void shutdownDisposesForeignTwinsWithoutDisposingHostedTwins() {
    var service = mock(Reasoner.class);
    when(service.serviceId()).thenReturn("reasoner");
    var user = new UserIdentityImpl();
    user.setUsername("alice");
    var parent = new ServiceSessionScope(new ServiceUserScope(user, service));
    var manager = new ScopeManager(service);
    var foreignTwin = mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    when(foreignTwin.isClient()).thenReturn(true);
    var hostedTwin = mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    var config = org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration.builder()
        .id("session.foreign").build();
    var foreign = new ServiceContextScope(parent, config, user);
    foreign.setId("session.foreign");
    foreign.setDigitalTwin(foreignTwin);
    var hosted = new ServiceContextScope(parent, config, user);
    hosted.setId("session.hosted");
    hosted.setDigitalTwin(hostedTwin);
    manager.registerScope(foreign);
    manager.registerScope(hosted);
    manager.shutdown();
    verify(foreignTwin).dispose();
    verify(hostedTwin, never()).dispose();
    assertNull(manager.getScope("session.foreign", ServiceContextScope.class));
  }

  @Test
  void requestAdvertisementSurvivesAndReplacementRemovesWithdrawnServices() {
    var service = mock(Reasoner.class);
    when(service.serviceId()).thenReturn("reasoner");
    var user = new UserIdentityImpl();
    user.setUsername("alice");
    var managed = new ServiceUserScope(user, service);
    managed.setId("alice");
    var first = managed.forAuthorization(authorization("first-token"));
    var notification = new UserScopeNotification();
    notification.setLocalFederation(true);
    var info = new UserScopeNotification.ServiceInfo();
    info.setId("reasoner");
    notification.getServices().add(info);
    new KlabScopeController().setupUserScope(first, notification, service);
    // The caller may mutate its original notification after publication.
    notification.getServices().clear();

    var second = managed.forAuthorization(authorization("second-token"));
    assertEquals(List.of(service), List.copyOf(second.getServices(KlabService.class)));
    assertEquals("second-token", second.getIdentity().getId());
    assertEquals("first-token", first.getIdentity().getId());
    assertEquals(Federation.LOCAL_FEDERATION_ID, Klab.INSTANCE.getFederationData(second.getUser()).getId());

    second.advertiseServices(new UserScopeNotification());
    var third = managed.forAuthorization(authorization("third-token"));
    assertTrue(third.getServices(KlabService.class).isEmpty());
    assertEquals(List.of(service), List.copyOf(first.getServices(KlabService.class)));
  }

  private EngineAuthorization authorization(String token) {
    return new EngineAuthorization("partner", "alice", token, Map.of(), List.of(), List.of());
  }
}
