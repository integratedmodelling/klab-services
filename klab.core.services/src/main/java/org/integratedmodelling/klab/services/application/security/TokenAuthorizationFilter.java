package org.integratedmodelling.klab.services.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.Service;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class TokenAuthorizationFilter extends BasicAuthenticationFilter {

  ServiceAuthorizationManager authorizationManager;

  private static final String[] HEADERS_OF_INTEREST = {
    HttpHeaders.AUTHORIZATION,
    ServicesAPI.SCOPE_HEADER,
    ServicesAPI.SERVER_KEY_HEADER,
    ServicesAPI.SERVICE_ID_HEADER,
    ServicesAPI.RESOLUTION_NAMESPACE_HEADER,
    ServicesAPI.RESOLUTION_PROJECT_HEADER,
    ServicesAPI.KLAB_VERSION_HEADER
  };

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
    String serverKey = req.getHeader(ServicesAPI.SERVER_KEY_HEADER);
    String runtimeId = req.getHeader(ServicesAPI.SERVICE_ID_HEADER);

    var requestHeaders = new HashMap<String, String>();
    for (var header : HEADERS_OF_INTEREST) {
      requestHeaders.put(header, req.getHeader(header));
    }

    if (tokenString != null) {
      try {
        EngineAuthorization token =
            authorizationManager.validateToken(tokenString, requestHeaders);
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
