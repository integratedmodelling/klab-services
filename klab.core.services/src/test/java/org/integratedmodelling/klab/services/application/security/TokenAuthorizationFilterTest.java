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

  @Test void authenticatedUnresolvedExplicitScopeReturns404BeforeController() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    var token = mock(EngineAuthorization.class);
    when(request.getHeader("Authorization")).thenReturn("issued-token");
    when(request.getHeader(org.integratedmodelling.klab.api.ServicesAPI.SCOPE_HEADER)).thenReturn("s.c");
    when(manager.validateToken(eq("issued-token"), anyMap())).thenReturn(token);
    when(token.isAuthenticated()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(mock(EngineAuthorization.class));
    filter.doFilterInternal(request, response, chain);
    verify(response).setStatus(404);
    verifyNoInteractions(chain);
    org.junit.jupiter.api.Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test void authenticatedResolvedScopeContinuesToController() throws Exception {
    var manager = mock(ServiceAuthorizationManager.class);
    var filter = new TokenAuthorizationFilter(mock(AuthenticationManager.class), manager);
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    var token = mock(EngineAuthorization.class);
    when(request.getHeader("Authorization")).thenReturn("issued-token");
    when(request.getHeader(org.integratedmodelling.klab.api.ServicesAPI.SCOPE_HEADER)).thenReturn("s.c");
    when(manager.validateToken(eq("issued-token"), anyMap())).thenReturn(token);
    when(token.isAuthenticated()).thenReturn(true);
    when(token.getScope()).thenReturn(mock(org.integratedmodelling.klab.api.scope.ContextScope.class));
    filter.doFilterInternal(request, response, chain);
    verify(chain).doFilter(request, response);
    verifyNoInteractions(response);
  }
}
