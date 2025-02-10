package org.integratedmodelling.klab.services.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class TokenAuthorizationFilter extends BasicAuthenticationFilter {

  ServiceAuthorizationManager authorizationManager;

  public TokenAuthorizationFilter(
      AuthenticationManager authManager, ServiceAuthorizationManager authorizationManager) {
    super(authManager);
    this.authorizationManager = authorizationManager;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {

    String tokenString = req.getHeader(HttpHeaders.AUTHORIZATION);
    String observerToken = req.getHeader(ServicesAPI.SCOPE_HEADER);
    String serverKey = req.getHeader(ServicesAPI.SERVER_KEY_HEADER);

    if (tokenString != null) {
      try {
        EngineAuthorization token =
            authorizationManager.validateToken(tokenString, serverKey, observerToken);
        if (token != null && token.isAuthenticated()) {
          SecurityContextHolder.getContext().setAuthentication(token);
        }
      } catch (Throwable e) {
        logger.error("Failed to extract JWT token: ", e);
        throw e;
      }
    }
    chain.doFilter(req, res);
  }
}
