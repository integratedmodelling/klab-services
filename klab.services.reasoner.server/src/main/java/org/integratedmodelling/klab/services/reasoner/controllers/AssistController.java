package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Reasoner.Capabilities;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

@RestController
public class AssistController {

    @Autowired
    Reasoner reasoner;

    /**
     * GET /capabilities
     * 
     * @return
     */
    @ApiOperation(value = "Obtain the reasoner service capabilities")
    @GetMapping(ServicesAPI.CAPABILITIES)
    public Capabilities getCapabilities() {
        return reasoner.capabilities();
    }

    @ApiOperation(value = "Perform guided semantic search, composing a valid logical expression incrementally")
    @PostMapping(ServicesAPI.REASONER.SEMANTIC_SEARCH)
    SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        return reasoner.semanticSearch(request);
    }
}
