package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    private RuntimeServer runtimeService;

    /**
     * Observations are set into the digital twin by the context after creating them in an unresolved state.
     * The return long ID is the handle to the resolution; according to the messaging protocol, the
     * observation tasks should monitor resolution until completion.
     *
     * @return
     */
    @PostMapping(ServicesAPI.RUNTIME.OBSERVE)
    public @ResponseBody long observe(@RequestBody ResolutionRequest resolutionRequest, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            var contextScope =
                    authorization.getScope(ContextScope.class).withResolutionConstraints(resolutionRequest.getResolutionConstraints().toArray(new ResolutionConstraint[0]));
            var task = contextScope.observe(resolutionRequest.getObservation());
            return task.trackingKey();
        }
        throw new KlabInternalErrorException("Unexpected implementation of request authorization");
    }
}
