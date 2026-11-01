package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;

class IdentityHeaderValidationTest {
  @Test void headersCannotSelectAnotherUserOrFederationAndLegacyHeadersRemainOptional() {
    var authorization = new EngineAuthorization("hub", "bob", "verified", Map.of(), List.of(), List.of());
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("bob");
    var data = Parameters.<String>create();
    data.put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("test.federation", null));
    when(user.getData()).thenReturn(data);
    var scope = mock(UserScope.class);
    when(scope.getUser()).thenReturn(user);
    authorization.validateIdentityHeaders(Map.of(), scope);
    authorization.validateIdentityHeaders(Map.of(ServicesAPI.USERNAME_HEADER, "bob",
        ServicesAPI.FEDERATION_HEADER, "test.federation"), scope);
    assertThrows(KlabAuthorizationException.class, () -> authorization.validateIdentityHeaders(
        Map.of(ServicesAPI.USERNAME_HEADER, "alice"), scope));
    assertThrows(KlabAuthorizationException.class, () -> authorization.validateIdentityHeaders(
        Map.of(ServicesAPI.FEDERATION_HEADER, "another.federation"), scope));
    assertEquals("bob", authorization.getUsername());
  }
}
