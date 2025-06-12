package org.integratedmodelling.klab.services.reasoner.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reasoner Assist API", description = "API for semantic search and assistance operations")
public class AssistController {

  @Autowired ReasonerServer reasoner;

  /**
   * Perform guided semantic search, composing a valid logical expression incrementally
   */
  @Operation(summary = "Perform semantic search", 
            description = "Performs a guided semantic search, composing a valid logical expression incrementally")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Search completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.SEMANTIC_SEARCH)
  SemanticSearchResponse semanticSearch(@Parameter(description = "Semantic search parameters") SemanticSearchRequest request) {
    return reasoner.klabService().semanticSearch(request);
  }
}
