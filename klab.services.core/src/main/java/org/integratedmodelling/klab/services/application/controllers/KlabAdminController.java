package org.integratedmodelling.klab.services.application.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.NoHandlerFoundException;

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

    @Autowired
    ServiceAuthorizationManager scopeManager;

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
    @PostMapping(ServicesAPI.ADMIN.SET_HOST_CREDENTIALS)
    public @ResponseBody ExternalAuthenticationCredentials.CredentialInfo setCredentials(@PathVariable(
            "host") String host, @RequestBody ExternalAuthenticationCredentials credentials,
                                                                                         Principal principal) {
        var scope = scopeManager.resolveScope(principal);
        credentials.setId(Utils.Names.shortUUID());
        credentials.setPrivileges(Authentication.INSTANCE.getDefaultPrivileges(scope));
        return Authentication.INSTANCE.addExternalCredentials(host, credentials, scope);
    }

    @DeleteMapping(ServicesAPI.ADMIN.CREDENTIALS)
    public boolean removeCredentials(String scheme, String host) {
        // TODO remove credentials, report true if done
        return false;
    }

    @GetMapping(ServicesAPI.ADMIN.CREDENTIALS)
    public @ResponseBody List<ExternalAuthenticationCredentials.CredentialInfo> listCredentials(Principal principal) {
        return instance.klabService().getCredentialInfo(scopeManager.resolveScope(principal));
    }

}
