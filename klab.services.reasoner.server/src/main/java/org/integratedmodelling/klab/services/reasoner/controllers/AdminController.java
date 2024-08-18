package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@Secured(Role.ADMINISTRATOR)
public class AdminController implements ServicesAPI.REASONER.ADMIN {

    @Autowired
    private ReasonerServer reasoner;

    @PostMapping(LOAD_KNOWLEDGE)
    public @ResponseBody ResourceSet loadKnowledge(@RequestBody Worldview resources, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            var userScope = authorization.getScope(UserScope.class);
            return reasoner.klabService().loadKnowledge(resources, userScope);
        }
        return ResourceSet.empty();
    }

    @PostMapping(UPDATE_KNOWLEDGE)
    public @ResponseBody ResourceSet loadKnowledge(@RequestBody ResourceSet changes, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            var userScope = authorization.getScope(UserScope.class);
            return reasoner.klabService().updateKnowledge(changes, userScope);
        }
        return ResourceSet.empty();
    }

    @PostMapping(DEFINE_CONCEPT)
    public @ResponseBody Concept defineConcept(@RequestBody KimConceptStatement statement, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            var userScope = authorization.getScope(UserScope.class);
            return reasoner.klabService().defineConcept(statement, userScope);
        }
        return null;
    }


}
