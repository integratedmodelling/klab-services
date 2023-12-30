package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminController {

    @Autowired
    private Reasoner.Admin reasoner;

    public boolean loadKnowledge(List<KimOntology> resources, Scope scope) {
        return reasoner.loadKnowledge(resources, scope);
    }

    public Concept defineConcept(KimConceptStatement statement, Scope scope) {
        return reasoner.defineConcept(statement, scope);
    }
    
}
