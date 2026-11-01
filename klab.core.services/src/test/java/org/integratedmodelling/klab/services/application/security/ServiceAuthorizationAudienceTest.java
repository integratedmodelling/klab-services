package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real RSA-signed credentials; no HTTP service, production key, or external issuer. */
class ServiceAuthorizationAudienceTest {
  private static RsaJsonWebKey signingKey;
  private ServiceAuthorizationManager manager;
  private ScopeManager scopes;
  private BaseService service;
  private ServiceInstance<BaseService> instance;

  @BeforeAll static void signingKey() throws Exception {
    signingKey = RsaJwkGenerator.generateJwk(2048);
  }

  @BeforeEach void setup() throws Exception {
    manager = new ServiceAuthorizationManager();
    instance = mock(ServiceInstance.class);
    service = mock(BaseService.class);
    scopes = mock(ScopeManager.class);
    when(instance.klabService()).thenReturn(service);
    when(service.getScopeManager()).thenReturn(scopes);
    when(scopes.getOrCreateUserScope(any(EngineAuthorization.class))).thenAnswer(invocation -> {
      var authorization = invocation.getArgument(0, EngineAuthorization.class);
      var identity = mock(UserIdentity.class);
      when(identity.getUsername()).thenReturn(authorization.getUsername());
      var scope = mock(ServiceUserScope.class);
      when(scope.getUser()).thenReturn(identity);
      return scope;
    });
    manager.setKlabService(() -> instance);
    set("serviceAuthenticated", true);
    set("preValidationExtractor", new JwtConsumerBuilder().setSkipAllValidators()
        .setDisableRequireSignature().setSkipSignatureVerification().build());
    set("jwksVerifiers", Map.of("test-hub", ServiceAuthorizationManager.engineTokenVerifier(
        signingKey.getPublicKey(), "test-hub")));
  }

  @Test void validSignatureWrongAudienceIsRejected() throws Exception {
    var claims = claims();
    claims.setAudience("another-service");
    assertRejected(claims, signingKey);
  }

  @Test void wrongAudienceCannotCarryAdministratorAuthority() throws Exception {
    var claims = claims();
    claims.setAudience("another-service");
    claims.setStringListClaim("roles", List.of("ROLE_ADMINISTRATOR"));
    assertRejected(claims, signingKey);
  }

  @Test void assertedUsernameCannotRescueWrongAudience() throws Exception {
    var claims = claims();
    claims.setAudience("another-service");
    var signed = sign(claims, signingKey);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
        () -> manager.validateToken(signed, Map.of(
            org.integratedmodelling.klab.api.ServicesAPI.USERNAME_HEADER, "alice")));
    verify(scopes, never()).getOrCreateUserScope(argThat(auth -> "alice".equals(auth.getUsername())));
  }

  @Test void wrongAudienceStopsTheRealFilterBeforeControllerDispatch() throws Exception {
    var claims = claims();
    claims.setAudience("another-service");
    var request = mock(jakarta.servlet.http.HttpServletRequest.class);
    var response = mock(jakarta.servlet.http.HttpServletResponse.class);
    var chain = mock(jakarta.servlet.FilterChain.class);
    when(request.getHeader("Authorization")).thenReturn("Bearer " + sign(claims, signingKey));
    var filter = new TokenAuthorizationFilter(mock(
        org.springframework.security.authentication.AuthenticationManager.class), manager);
    try {
      org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(mock(EngineAuthorization.class));
      filter.doFilterInternal(request, response, chain);
      verify(response).setStatus(403);
      verifyNoInteractions(chain);
      assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test void validEngineAudienceAuthenticates() throws Exception {
    var result = manager.validateToken(sign(claims(), signingKey), Map.of());
    assertTrue(result.isAuthenticated());
    assertEquals("alice", result.getUsername());
    assertFalse(result.isAdministrator());
  }

  @Test void validEngineCredentialContinuesThroughTheRealFilter() throws Exception {
    var request = mock(jakarta.servlet.http.HttpServletRequest.class);
    var response = mock(jakarta.servlet.http.HttpServletResponse.class);
    var chain = mock(jakarta.servlet.FilterChain.class);
    when(request.getHeader("Authorization")).thenReturn("Bearer " + sign(claims(), signingKey));
    var filter = new TokenAuthorizationFilter(mock(
        org.springframework.security.authentication.AuthenticationManager.class), manager);
    try {
      filter.doFilterInternal(request, response, chain);
      verify(chain).doFilter(request, response);
      verifyNoInteractions(response);
      var principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(principal);
      assertTrue(principal.isAuthenticated());
      assertEquals("alice", ((EngineAuthorization) principal).getUsername());
      assertFalse(((EngineAuthorization) principal).isAdministrator());
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test void multipleAudiencesIncludingEngineAuthenticate() throws Exception {
    var claims = claims();
    claims.setAudience("another-service", "engine");
    assertTrue(manager.validateToken(sign(claims, signingKey), Map.of()).isAuthenticated());
  }

  @Test void missingAudienceIsRejected() throws Exception {
    var claims = claims();
    claims.unsetClaim("aud");
    assertRejected(claims, signingKey);
  }

  @Test void missingSubjectIsRejected() throws Exception {
    var claims = claims();
    claims.unsetClaim("sub");
    assertRejected(claims, signingKey);
  }

  @Test void blankSubjectIsRejected() throws Exception {
    var claims = claims();
    claims.setSubject(" ");
    assertRejected(claims, signingKey);
  }

  @Test void nonStringSubjectIsRejected() throws Exception {
    var claims = claims();
    claims.setClaim("sub", 42);
    assertRejected(claims, signingKey);
  }

  @Test void emptyAudienceIsRejected() throws Exception {
    var claims = claims();
    claims.setClaim("aud", List.of());
    assertRejected(claims, signingKey);
  }

  @Test void nullAudienceIsRejected() throws Exception {
    var claims = claims();
    claims.setClaim("aud", null);
    assertRejected(claims, signingKey);
  }

  @Test void nonStringAudienceIsRejected() throws Exception {
    var claims = claims();
    claims.setClaim("aud", 42);
    assertRejected(claims, signingKey);
  }

  @Test void audienceListWithoutEngineIsRejected() throws Exception {
    var claims = claims();
    claims.setAudience("first", "second");
    assertRejected(claims, signingKey);
  }

  @Test void unknownIssuerWithTrustedKeyIsRejected() throws Exception {
    var claims = claims();
    claims.setIssuer("untrusted-hub");
    assertRejected(claims, signingKey);
  }

  @Test void missingIssuerIsRejected() throws Exception {
    var claims = claims();
    claims.unsetClaim("iss");
    assertRejected(claims, signingKey);
  }

  @Test void deliberatelyAuthorizedLocalKeyRemainsIndependentOfJwtAudience() throws Exception {
    when(service.getServiceSecret()).thenReturn("test-private-service-key");
    when(service.isLocal()).thenReturn(true);
    var owner = mock(UserIdentity.class);
    when(owner.getUsername()).thenReturn("local-owner");
    when(owner.getId()).thenReturn("local-owner-id");
    when(owner.getGroups()).thenReturn(List.of());
    when(instance.getServiceOwner()).thenReturn(owner);
    var claims = claims();
    claims.setAudience("another-service");
    var result = manager.validateToken(sign(claims, signingKey), Map.of(
        org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER, "test-private-service-key"));
    assertTrue(result.isAuthenticated());
    assertTrue(result.isLocal());
    assertTrue(result.isAdministrator());
    assertEquals("local-owner", result.getUsername());
    verify(scopes, never()).getOrCreateUserScope(argThat(auth -> "alice".equals(auth.getUsername())));
  }

  @Test void matchingKeyOnNonlocalServiceCannotRescueWrongAudience() throws Exception {
    when(service.getServiceSecret()).thenReturn("test-private-service-key");
    when(service.isLocal()).thenReturn(false);
    var claims = claims();
    claims.setAudience("another-service");
    assertRejected(claims, signingKey, Map.of(
        org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER, "test-private-service-key"));
  }

  @Test void incorrectLocalKeyCannotRescueWrongAudience() throws Exception {
    when(service.getServiceSecret()).thenReturn("test-private-service-key");
    when(service.isLocal()).thenReturn(true);
    var claims = claims();
    claims.setAudience("another-service");
    assertRejected(claims, signingKey, Map.of(
        org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER, "wrong-fixture-key"));
  }

  @Test void issuerWhitespaceIsNotAnAliasForTrustedIssuer() throws Exception {
    var claims = claims();
    claims.setIssuer("test-hub ");
    assertRejected(claims, signingKey);
  }

  @Test void wrongSignatureWithCorrectAudienceIsRejected() throws Exception {
    assertRejected(claims(), RsaJwkGenerator.generateJwk(2048));
  }

  @Test void expiredEngineTokenIsRejected() throws Exception {
    var claims = claims();
    claims.setExpirationTime(NumericDate.fromSeconds(NumericDate.now().getValue() - 120));
    assertRejected(claims, signingKey);
  }

  @Test void missingExpirationCannotLeaveAnIdentifiedPartialAuthorization() throws Exception {
    var claims = claims();
    claims.unsetClaim("exp");
    assertRejected(claims, signingKey);
  }

  @Test void malformedExpirationCannotLeaveAnIdentifiedPartialAuthorization() throws Exception {
    var claims = claims();
    claims.setClaim("exp", "not-a-date");
    assertRejected(claims, signingKey);
  }

  @Test void missingIssuedAtCannotLeaveAnIdentifiedPartialAuthorization() throws Exception {
    var claims = claims();
    claims.unsetClaim("iat");
    assertRejected(claims, signingKey);
  }

  @Test void malformedIssuedAtCannotLeaveAnIdentifiedPartialAuthorization() throws Exception {
    var claims = claims();
    claims.setClaim("iat", "not-a-date");
    assertRejected(claims, signingKey);
  }

  private JwtClaims claims() {
    var claims = new JwtClaims();
    claims.setIssuer("test-hub");
    claims.setSubject("alice");
    claims.setAudience("engine");
    claims.setIssuedAtToNow();
    claims.setExpirationTimeMinutesInTheFuture(5);
    claims.setStringListClaim("roles", List.of("ROLE_USER"));
    claims.setStringListClaim("perms", List.of());
    return claims;
  }

  private String sign(JwtClaims claims, RsaJsonWebKey key) throws Exception {
    var signature = new JsonWebSignature();
    signature.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    signature.setKey(key.getPrivateKey());
    signature.setPayload(claims.toJson());
    return signature.getCompactSerialization();
  }

  private void assertRejected(JwtClaims claims, RsaJsonWebKey key) throws Exception {
    assertRejected(claims, key, Map.of());
  }

  private void assertRejected(JwtClaims claims, RsaJsonWebKey key, Map<String, String> headers) throws Exception {
    var result = manager.validateToken(sign(claims, key), headers);
    assertFalse(result.isAuthenticated());
    assertEquals("anonymous", result.getUsername());
    assertTrue(result.getAuthorities().isEmpty());
    verify(scopes, never()).getOrCreateUserScope(argThat(auth -> "alice".equals(auth.getUsername())));
  }

  private void set(String name, Object value) throws Exception {
    var field = ServiceAuthorizationManager.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(manager, value);
  }
}
