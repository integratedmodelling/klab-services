package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Secured(Role.ADMINISTRATOR)
public class RuntimeServerAdminController {

    @Autowired
    private RuntimeService runtimeService;


    public String createContext() {
        // TODO
        return null;
    }

}
