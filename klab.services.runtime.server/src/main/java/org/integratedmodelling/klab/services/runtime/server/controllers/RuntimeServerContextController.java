package org.integratedmodelling.klab.services.runtime.server.controllers;

import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.runtime.server.objects.Context;
import org.integratedmodelling.klab.services.runtime.server.objects.ObservationInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the GraphQL support for context access, modification and inspection. The GraphQL endpoint is
 * unsecured, but the result depend on the authorized identity and scope.
 */
@Controller
public class RuntimeServerContextController {

    @Autowired
    private RuntimeServer runtimeService;
    @Autowired
    ServiceAuthorizationManager authorizationManager;
    @Autowired
    private HttpServletRequest request;

    private List<Context> demoContexts = new ArrayList<>();

    public RuntimeServerContextController() {
        demoContexts.add(new Context("dio1", "dio uno", null, null));
        demoContexts.add(new Context("dio2", "dio due", null, null));
        demoContexts.add(new Context("dio3", "dio tre", null, null));
        demoContexts.add(new Context("dio4", "dio quattro", null, null));
        demoContexts.add(new Context("dio5", "dio cinque", null, null));
        demoContexts.add(new Context("dio6", "dio sei", null, null));
    }

    /**
     * Return the authorization including the scope referenced in the request
     *
     * @return
     */
    private EngineAuthorization getAuthorization() {
        var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        var observerToken = request.getHeader(ServicesAPI.SCOPE_HEADER);
        var serverKey = request.getHeader(ServicesAPI.SERVER_KEY_HEADER);
        return authorizationManager.validateToken(authHeader, serverKey, observerToken);
    }

    @QueryMapping("contexts")
    public List<Context> contexts() {
        return demoContexts;
    }

    @QueryMapping("context")
    public Context context() {
        return demoContexts.get(2);
    }

    @MutationMapping
    public String observe(@Argument(name = "observation") ObservationInput observation) {
        return null;
    }

}
