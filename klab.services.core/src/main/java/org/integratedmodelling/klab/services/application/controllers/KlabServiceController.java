package org.integratedmodelling.klab.services.application.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unsecured API endpoints common to all controllers.
 */
@RestController
public class KlabServiceController {

    @Autowired
    ServiceNetworkedInstance instance;

    @GetMapping(ServicesAPI.CAPABILITIES)
    public KlabService.ServiceCapabilities capabilities() {
        // TODO filter for principal, including no principal
        return instance.klabService().capabilities();
    }

    @GetMapping(ServicesAPI.STATUS)
    public KlabService.ServiceStatus status() {
        return instance.klabService().status();
    }

}
