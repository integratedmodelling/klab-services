package org.integratedmodelling.klab.services.application.security;

import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;

class TokenAuthorizationFilterTest {
  @Test void missingExplicitScopeIs404AndNeverReachesTheController() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    when(request.getHeader("Authorization")).thenReturn("Bearer verified-bob");
    when(request.getHeader(org.integratedmodelling.klab.api.ServicesAPI.SCOPE_HEADER)).thenReturn("session.missing");
    var authorization = new EngineAuthorization("hub", "bob", "verified-bob", java.util.Map.of(), java.util.List.of(), java.util.List.of(Role.ROLE_USER));
    authorization.setAuthenticated(true);
    when(manager.validateToken(eq("verified-bob"), anyMap())).thenReturn(authorization);
    filter.doFilterInternal(request, response, chain);
    verify(response).setStatus(404);
    verifyNoInteractions(chain);
  }
  @Test void identityAssertionDenialIs403AndNeverReachesTheController() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    when(request.getHeader("Authorization")).thenReturn("Bearer verified-bob");
    when(manager.validateToken(eq("verified-bob"), anyMap())).thenThrow(
        new org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException("identity mismatch"));
    filter.doFilterInternal(request, response, chain);
    verify(response).setStatus(403);
    verifyNoInteractions(chain);
  }
  @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

  @Test void keycloakExchangeBypassesLegacyJwtValidation() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    when(request.getServletPath()).thenReturn(HubWebAuthentication.ENDPOINT);
    when(request.getHeader("Authorization")).thenReturn("Bearer keycloak");
    filter.doFilterInternal(request, response, chain);
    verifyNoInteractions(manager);
    verify(chain).doFilter(request, response);
  }

  @Test void expiredBrowserCredentialFailsWith401InsteadOfUsingAnExistingContext() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    when(request.getHeader("Authorization")).thenReturn("Bearer webui_expired");
    filter.doFilterInternal(request, response, chain);
    verify(manager).validateToken(eq("webui_expired"), anyMap());
    verify(response).setStatus(401);
    verifyNoInteractions(chain);
  }
}
