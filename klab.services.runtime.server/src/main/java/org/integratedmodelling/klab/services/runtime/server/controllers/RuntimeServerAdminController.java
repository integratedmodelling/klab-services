package org.integratedmodelling.klab.services.runtime.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Secured(Role.ADMINISTRATOR)
@Tag(name = "Runtime administration", description = "Administrative operations for runtime scopes and digital twins")
public class RuntimeServerAdminController {

    @Autowired
    private RuntimeServer runtimeService;

}
