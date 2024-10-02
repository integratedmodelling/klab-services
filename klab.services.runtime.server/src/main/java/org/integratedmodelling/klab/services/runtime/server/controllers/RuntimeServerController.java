package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.AssetRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

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
            if (resolutionRequest.isStartResolution()) {
                var task = contextScope.observe(resolutionRequest.getObservation());
                return task.trackingKey();
            } else if (contextScope instanceof ServiceContextScope serviceContextScope) {
                return serviceContextScope.insertIntoKnowledgeGraph(resolutionRequest.getObservation());
            }
        }
        throw new KlabInternalErrorException("Unexpected implementation of request authorization");
    }

    @GetMapping(ServicesAPI.RUNTIME.GET_SESSION_INFO)
    public @ResponseBody List<SessionInfo> getSessionInfo(Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            return runtimeService.klabService().getSessionInfo(authorization.getScope());
        }
        return List.of();
    }

    public @ResponseBody List<? extends RuntimeAsset> queryKnowledgeGraph(@RequestBody AssetRequest request
            , Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            var contextScope =
                    authorization.getScope(ContextScope.class);
            List<Object> queryParameters = new ArrayList<>();
            if (request.getId() != Observation.UNASSIGNED_ID) queryParameters.add(request.getId());
            if (request.getObservable() != null) queryParameters.add(request.getObservable());
            if (request.getGeometry() != null) queryParameters.add(request.getGeometry());
            if (request.getContextObservation() != null) queryParameters.add(request.getContextObservation());
            if (!request.getMetadata().isEmpty()) queryParameters.add(request.getMetadata());
            if (request.getName() != null) queryParameters.add(request.getName());
            return runtimeService.klabService().retrieveAssets(contextScope, request.getKnowledgeClass().assetClass, queryParameters.toArray());
        }
        throw new KlabInternalErrorException("Unexpected implementation of request authorization");
    }
}
