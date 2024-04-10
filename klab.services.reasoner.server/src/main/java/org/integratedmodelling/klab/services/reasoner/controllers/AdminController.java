package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Secured(Role.ADMINISTRATOR)
public class AdminController {

    @Autowired
    private ReasonerServer reasoner;

    @PostMapping(ServicesAPI.REASONER.ADMIN.LOAD_KNOWLEDGE)
    public @ResponseBody boolean loadKnowledge(@RequestBody Worldview resources) {
        return reasoner.klabService().loadKnowledge(resources);
    }

    @PostMapping(ServicesAPI.REASONER.ADMIN.DEFINE_CONCEPT)
    public @ResponseBody Concept defineConcept(@RequestBody KimConceptStatement statement) {
        return reasoner.klabService().defineConcept(statement);
    }


}
