package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    private RuntimeServer runtimeService;

}
