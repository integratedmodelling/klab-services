package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistController {

    @Autowired
    Reasoner reasoner;
    
    @PostMapping
    SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        return reasoner.semanticSearch(request);
    }
}
