package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.resources.CredentialsRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Common administration endpoints. Secured with administrator role.
 */
@RestController
@Secured(Role.ADMINISTRATOR)
@Tag(name="General administration")
public class KlabAdminController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    @Autowired
    ServiceAuthorizationManager scopeManager;

    /**
     * Shut down the service. Returns status code (true/false) before the actual shutdown begins.
     *
     * @return true unless anything strange happened
     */
    @PutMapping(ServicesAPI.ADMIN.SHUTDOWN)
    public boolean shutdown() {
        Logging.INSTANCE.info("Shutting down service instance " + instance.klabService().getLocalName());
        instance.shutdown();
        return true;
    }

    /**
     * Check if we have credentials for the passed scheme and host.
     *
     * @param scheme one of the supported k.LAB authentication schemes
     * @param host   the host name (possibly with port and path)
     * @return true if the service has the requested credentials
     */
    @GetMapping(ServicesAPI.ADMIN.CHECK_CREDENTIALS)
    public boolean checkCredentials(String scheme, String host) {
        // TODO check if we have credentials for the passed scheme/host, return true if we do
        return false;
    }

    /**
     * Set credentials for a specified host.
     *
     * @param request   contains the host and the credential data
     * @param principal
     * @return the information relative to the added credentials, including the credential identifier
     */
    @PostMapping(ServicesAPI.ADMIN.CREDENTIALS)
    public @ResponseBody ExternalAuthenticationCredentials.CredentialInfo setCredentials(@RequestBody CredentialsRequest request, Principal principal) {
        var scope = scopeManager.resolveScope(principal, Scope.class, null);
        request.getCredentials().setId(Utils.Names.shortUUID());
        request.getCredentials().setPrivileges(Authentication.INSTANCE.getDefaultPrivileges(scope));
        return Authentication.INSTANCE.addExternalCredentials(request.getHost(), request.getCredentials(),
                scope);
    }

    /**
     * Delete a set of credentials.
     *
     * @param id the credential ID obtained through one of the inspection endpoints.
     * @return true if the credentials were there and were deleted, false otherwise
     */
    @DeleteMapping(ServicesAPI.ADMIN.CREDENTIALS)
    public boolean removeCredentials(@RequestParam("id") String id) {
        return Authentication.INSTANCE.removeCredentials(id);
    }

    /**
     * Return all the credentials known to the service in the form of a list of credential information
     * objects.
     *
     * @param principal
     * @return the list of credentials, possibly empty
     */
    @GetMapping(ServicesAPI.ADMIN.CREDENTIALS)
    public @ResponseBody List<ExternalAuthenticationCredentials.CredentialInfo> listCredentials(Principal principal) {
        return instance.klabService().getCredentialInfo(scopeManager.resolveScope(principal, Scope.class, null));
    }

}
