package org.integratedmodelling.klab.services.reasoner.controllers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cycle-marked callers cannot accidentally use an endpoint that dispatches to current semantics.
 */
@Component
public final class SemanticCycleRequestFilter extends OncePerRequestFilter {
  private static final Set<String> PROTOCOL =
      Set.of(
          ServicesAPI.REASONER.SEMANTIC_CYCLE_BEGIN,
          ServicesAPI.REASONER.SEMANTIC_CYCLE_RELEASE,
          ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    boolean semantic =
        path.equals(ServicesAPI.API_BASE)
            || path.startsWith(ServicesAPI.API_BASE + "/")
            || path.equals(ServicesAPI.CAPABILITIES);
    if (request.getHeader(ServicesAPI.RESOLUTION_CYCLE_HEADER) != null
        && semantic
        && !PROTOCOL.contains(path)) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      response.setContentType("application/json");
      response
          .getWriter()
          .write(
              "{\"detail\":\"Cycle-bound semantic requests require the retained invocation endpoint\"}");
      return;
    }
    // Protocol endpoints still perform their normal authenticated owner/lease checks.
    chain.doFilter(request, response);
  }
}
