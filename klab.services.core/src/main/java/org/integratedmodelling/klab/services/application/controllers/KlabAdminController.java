package org.integratedmodelling.klab.services.application.controllers;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Unsecured API endpoints common to all controllers.
 */
@RestController
@Secured(Role.ADMINISTRATOR)
public class KlabAdminController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    @PutMapping(ServicesAPI.SHUTDOWN)
    public void shutdown() {
        Logging.INSTANCE.info("Shutting down service instance " + instance.klabService().getLocalName());
        instance.shutdown();
    }

}
