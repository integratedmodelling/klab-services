package org.integratedmodelling.klab.services.application.controllers;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Unsecured API endpoints common to all controllers.
 */
@RestController
@Secured(Role.ADMINISTRATOR)
public class KlabAdminController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    @PutMapping(ServicesAPI.ADMIN.SHUTDOWN)
    public boolean shutdown() {
        Logging.INSTANCE.info("Shutting down service instance " + instance.klabService().getLocalName());
        instance.shutdown();
        return true;
    }

    @GetMapping(ServicesAPI.ADMIN.CHECK_CREDENTIALS)
    public boolean checkCredentials(String scheme, String host) {
        // TODO check if we have credentials for the passed scheme/host, return true if we do
        return false;
    }

    // FIXME use a dedicated POST payload
    @PostMapping(ServicesAPI.ADMIN.SET_CREDENTIALS)
    public boolean setCredentials(String scheme, String host, List<String> parameters) {
        // TODO set credentials, report success
        return false;
    }

    @DeleteMapping(ServicesAPI.ADMIN.REMOVE_CREDENTIALS)
    public boolean removeCredentials(String scheme, String host) {
        // TODO remove credentials, report true if done
        return false;
    }

    @GetMapping(ServicesAPI.ADMIN.LIST_CREDENTIALS)
    public List<Authentication.CredentialInfo> listCredentials(Principal principal) {
        /* FIXME use the scope registered from the principal */
        return Authentication.INSTANCE.getCredentialInfo(instance.klabService().serviceScope());
    }

}
