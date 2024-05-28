package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * These endpoints are unsecured information endpoints common to all controllers to inquire about status and
 * capabilities. If authorization is included in the request the capabilities may differ.
 */
@RestController
@Tag(name = "Basic inspection")
public class KlabServiceController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    @GetMapping(ServicesAPI.CAPABILITIES)
    public KlabService.ServiceCapabilities capabilities(Principal principal) {
        return instance.klabService().capabilities(principal == null ? null :
                                                   authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.STATUS)
    public KlabService.ServiceStatus status() {
        return instance.klabService().status();
    }

}
