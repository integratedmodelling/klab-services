package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class KlabScopeControllerTest {

  @Test
  void propagatesLocalFederationToServiceUserScope() {
    var user = new UserIdentityImpl();
    user.setUsername("anonymous");
    user.setAnonymous(true);
    var ownerService = mock(KlabService.class);
    when(ownerService.serviceId()).thenReturn("resolver-id");
    var userScope = new ServiceUserScope(user, ownerService);
    var notification = new UserScopeNotification();
    notification.setLocalFederation(true);

    var result = new KlabScopeController().setupUserScope(userScope, notification, ownerService);

    assertTrue(result);
    assertEquals(
        Federation.LOCAL_FEDERATION_ID, Klab.INSTANCE.getFederationData(user).getId());
    assertTrue(notification.withoutLocalServices().isLocalFederation());
  }
}
