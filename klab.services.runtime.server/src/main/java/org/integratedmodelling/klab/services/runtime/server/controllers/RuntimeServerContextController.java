package org.integratedmodelling.klab.services.runtime.server.controllers;

import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Provides the GraphQL support for context access, modification and inspection. The GraphQL
 * endpoint is unsecured, but the result depend on the authorized identity and scope.
 */
@Controller
public class RuntimeServerContextController {

  @Autowired private RuntimeServer runtimeService;
  @Autowired ServiceAuthorizationManager authorizationManager;
  @Autowired private HttpServletRequest request;

  /**
   * Return the authorization, including the scope referenced in the request.
   *
   * @return
   */
  private EngineAuthorization getAuthorization() {
    var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    var observerToken = request.getHeader(ServicesAPI.SCOPE_HEADER);
    var serverKey = request.getHeader(ServicesAPI.SERVER_KEY_HEADER);
    var brokerUrl = request.getHeader(ServicesAPI.MESSAGING_URL_HEADER);
    var federationId = request.getHeader(ServicesAPI.FEDERATION_ID_HEADER);
    return authorizationManager.validateToken(
        authHeader, serverKey, observerToken, brokerUrl, federationId);
  }

  @QueryMapping
  public List<GraphModel.Observation> observations() {
    return List.of();
  }

}
