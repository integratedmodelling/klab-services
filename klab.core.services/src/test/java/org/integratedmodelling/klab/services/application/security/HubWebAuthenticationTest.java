package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.server.ResponseStatusException;

class HubWebAuthenticationTest {
  private final Instant now = Instant.parse("2026-09-16T12:00:00Z");
  private final Clock clock = mock(Clock.class);

  private String token(String username, String issuer, String audience, long expires, List<String> roles) throws Exception {
    var claims = new JwtClaims();
    claims.setSubject(username);
    claims.setIssuer(issuer);
    claims.setAudience(audience);
    claims.setIssuedAt(NumericDate.fromSeconds(now.getEpochSecond()));
    claims.setExpirationTime(NumericDate.fromSeconds(expires));
    claims.setStringListClaim("roles", roles);
    claims.setStringListClaim("perms", List.of("researchers"));
    var signature = new JsonWebSignature();
    signature.setPayload(claims.toJson());
    signature.setAlgorithmHeaderValue("RS256");
    signature.setKey(RsaJwkGenerator.generateJwk(2048).getPrivateKey());
    return signature.getCompactSerialization();
  }

  private ServiceInstance<?> instance(boolean local) {
    var instance = mock(ServiceInstance.class);
    var service = mock(BaseService.class);
    when(instance.klabService()).thenReturn(service);
    when(service.isLocal()).thenReturn(local);
    when(service.serviceId()).thenReturn("test-service");
    var owner = new UserIdentityImpl();
    owner.setUsername("alice");
    owner.setAnonymous(false);
    owner.setAuthenticated(true);
    when(instance.getServiceOwner()).thenReturn(owner);
    return instance;
  }

  private HubWebAuthentication bridge(String jwt, String name) {
    when(clock.instant()).thenReturn(now);
    var bridge = new HubWebAuthentication(new StandardEnvironment(), clock, (endpoint, accessToken) -> {
      assertEquals("https://hub.example/hub/api/v2/users/me?remote=true", endpoint.toString());
      assertEquals("keycloak-access-token", accessToken);
      return new ObjectMapper().valueToTree(Map.of("jwtToken", jwt, "name", name, "email", "a@example.org"));
    });
    bridge.configureHub("https://hub.example/hub/", "im");
    return bridge;
  }

  @Test void localOwnerReceivesServiceAdministrationWithoutSecretOrScopeReuse() throws Exception {
    String jwt = token("alice", "im", "engine", now.plusSeconds(900).getEpochSecond(), List.of("ROLE_USER"));
    var bridge = bridge(jwt, "alice");
    var login = bridge.exchange("keycloak-access-token", instance(true));
    assertTrue(login.token().startsWith("webui_"));
    assertNotEquals(jwt, login.token());
    assertEquals(now.plusSeconds(300).toEpochMilli(), login.expiresAt());
    var auth = bridge.authorize(login.token(), Map.of());
    assertTrue(auth.isAuthenticated());
    assertFalse(auth.isLocal());
    assertTrue(auth.isAdministrator());
    assertEquals(jwt, auth.getToken());
    assertEquals("researchers", auth.getGroups().iterator().next().getName());
    var scope = (ServiceUserScope) auth.getScope();
    assertFalse(scope.isLocal());
    assertEquals(EnumSet.allOf(CRUDOperation.class), scope.getPermissions());
    assertTrue(org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant
        .from(scope).getRoles().contains(
            org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole.ADMIN));
    assertNull(bridge.authorize(jwt, Map.of()));
    assertNull(bridge.authorize("webui_forged", Map.of()));
    assertNull(bridge.authorize(login.token(), Map.of(ServicesAPI.SCOPE_HEADER, "alice")));
    assertSame(scope, bridge.authorize(login.token(), Map.of()).getScope());
  }

  @Test void rejectsOtherLocalUsersButAllowsThemOnNetworkService() throws Exception {
    var bridge = bridge(token("bob", "im", "engine", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_USER")), "bob");
    var rejected = assertThrows(ResponseStatusException.class,
        () -> bridge.exchange("keycloak-access-token", instance(true)));
    assertEquals(403, rejected.getStatusCode().value());
    var login = bridge.exchange("keycloak-access-token", instance(false));
    assertEquals(now.plusSeconds(90).toEpochMilli(), login.expiresAt());
    var authorization = bridge.authorize(login.token(), Map.of());
    assertFalse(authorization.isAdministrator());
    assertEquals(EnumSet.of(CRUDOperation.READ),
        ((ServiceUserScope) authorization.getScope()).getPermissions());
  }

  @Test void expirationAndLogoutInvalidateCredentials() throws Exception {
    var bridge = bridge(token("alice", "im", "engine", now.plusSeconds(900).getEpochSecond(), List.of("ROLE_USER")), "alice");
    var first = bridge.exchange("keycloak-access-token", instance(true));
    var second = bridge.exchange("keycloak-access-token", instance(true));
    assertNotSame(bridge.authorize(first.token(), Map.of()).getScope(), bridge.authorize(second.token(), Map.of()).getScope());
    bridge.revoke(first.token());
    assertNull(bridge.authorize(first.token(), Map.of()));
    when(clock.instant()).thenReturn(now.plusSeconds(300));
    assertNull(bridge.authorize(second.token(), Map.of()));
  }

  @Test void rejectsWrongIssuerAudienceExpiredTokenAndMismatchedProfile() throws Exception {
    for (String jwt : List.of(
        token("alice", "other", "engine", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_USER")),
        token("alice", "im", "account", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_USER")),
        token("alice", "im", "engine", now.getEpochSecond(), List.of("ROLE_USER")),
        token("bob", "im", "engine", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_USER")))) {
      var bridge = bridge(jwt, "alice");
      assertEquals(502, assertThrows(ResponseStatusException.class,
          () -> bridge.exchange("keycloak-access-token", instance(true))).getStatusCode().value());
    }
  }

  @Test void rejectsMissingUserRoleAndUntrustedHubConfiguration() throws Exception {
    var bridge = bridge(token("alice", "im", "engine", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_ADMINISTRATOR")), "alice");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> bridge.exchange("keycloak-access-token", instance(true))).getStatusCode().value());
    for (String url : Arrays.asList(null, "http://hub.example", "https://user@hub.example", "https://hub.example?url=x")) {
      assertThrows(ResponseStatusException.class, () -> HubWebAuthentication.profileEndpoint(url));
    }
  }

  @Test void serviceManagerRoutesBrowserCredentialsWithoutLegacyOrSecretFallback() throws Exception {
    var bridge = bridge(token("alice", "im", "engine", now.plusSeconds(90).getEpochSecond(), List.of("ROLE_USER")), "alice");
    var login = bridge.exchange("keycloak-access-token", instance(true));
    var manager = new ServiceAuthorizationManager();
    var field = ServiceAuthorizationManager.class.getDeclaredField("webAuthentication");
    field.setAccessible(true);
    field.set(manager, bridge);
    // No service certificate, legacy verifier, or service-secret supplier is installed.
    assertTrue(manager.validateToken(login.token(), Map.of()).isAuthenticated());
    assertNull(manager.validateToken("webui_forged", Map.of(ServicesAPI.SERVER_KEY_HEADER, "local-key")));
  }
}
