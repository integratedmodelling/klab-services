package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminController {

    @Autowired
    private ReasonerServer reasoner;

    public boolean loadKnowledge(Worldview resources, Scope scope) {
        return reasoner.klabService().loadKnowledge(resources, scope);
    }

    public Concept defineConcept(KimConceptStatement statement, Scope scope) {
        return reasoner.klabService().defineConcept(statement, scope);
    }
    
}
