package org.integratedmodelling.klab.services.reasoner.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Reasoner.Capabilities;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistController {

  @Autowired ReasonerServer reasoner;

  //    @ApiOperation(value = "Perform guided semantic search, composing a valid logical expression
  // incrementally")
  @PostMapping(ServicesAPI.REASONER.SEMANTIC_SEARCH)
  SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
    return reasoner.klabService().semanticSearch(request);
  }
}
