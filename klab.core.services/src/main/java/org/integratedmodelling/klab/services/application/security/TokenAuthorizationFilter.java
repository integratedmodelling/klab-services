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
    ServicesAPI.USERNAME_HEADER,
    ServicesAPI.FEDERATION_HEADER,
    ServicesAPI.SERVER_KEY_HEADER,
    ServicesAPI.SERVICE_ID_HEADER,
    ServicesAPI.RESOLUTION_NAMESPACE_HEADER,
    ServicesAPI.RESOLUTION_PROJECT_HEADER,
    ServicesAPI.KLAB_VERSION_HEADER,
    ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER,
    ServicesAPI.TRANSACTION_ID_HEADER,
    ServicesAPI.SOURCE_OBSERVATION_ID_HEADER,
    ServicesAPI.TARGET_OBSERVATION_ID_HEADER
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

    // The exchange controller sends the Keycloak credential only to the trusted hub. It is not
    // a legacy k.LAB JWT, and logout must remain possible after a browser credential expires.
    if (HubWebAuthentication.ENDPOINT.equals(req.getServletPath())) {
      chain.doFilter(req, res);
      return;
    }

    String tokenString = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (tokenString != null && tokenString.regionMatches(true, 0, "Bearer ", 0, 7)) {
      tokenString = tokenString.substring(7).trim();
    }
    String serverKey = req.getHeader(ServicesAPI.SERVER_KEY_HEADER);
    String runtimeId = req.getHeader(ServicesAPI.SERVICE_ID_HEADER);

    var requestHeaders = new HashMap<String, String>();
    for (var header : HEADERS_OF_INTEREST) {
      requestHeaders.put(header, req.getHeader(header));
    }

    if (tokenString != null) {
      try {
        EngineAuthorization token = authorizationManager.validateToken(tokenString, requestHeaders);
        if (token != null && token.isAuthenticated()) {
          // Never pass an authenticated request with an unresolved explicit scope
          // to controllers: absent/inaccessible scopes are not internal failures.
          if (requestHeaders.get(ServicesAPI.SCOPE_HEADER) != null && token.getScope() == null) {
            SecurityContextHolder.clearContext();
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
          SecurityContextHolder.getContext().setAuthentication(token);
        } else if (tokenString.startsWith(HubWebAuthentication.PREFIX)) {
          SecurityContextHolder.clearContext();
          res.setHeader("Cache-Control", "no-store");
          res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          return;
        } else {
          SecurityContextHolder.clearContext();
          // An explicit invalid credential must not inherit an earlier principal or
          // silently reach a public controller as though no credential was supplied.
          if (!ServicesAPI.ANONYMOUS_TOKEN.equals(tokenString)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
          }
        }
      } catch (org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException denied) {
        SecurityContextHolder.clearContext();
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      } catch (Throwable e) {
        SecurityContextHolder.clearContext();
        logger.error("Credential validation failed (" + e.getClass().getSimpleName() + ")");
        if (e instanceof Error fatal) throw fatal;
        // Keep unexpected failures distinct from permission denials without returning
        // arbitrary verifier messages or sensitive causes to error handlers.
        throw new ServletException("Credential validation unavailable");
      }
    }
    chain.doFilter(req, res);
  }
}
