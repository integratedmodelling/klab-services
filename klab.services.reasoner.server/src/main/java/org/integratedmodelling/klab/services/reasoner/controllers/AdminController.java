package org.integratedmodelling.klab.services.reasoner.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reasoner Administration API", description = "Administrative operations for the reasoner service")
public class AdminController implements ServicesAPI.REASONER.ADMIN {

  @Autowired private ReasonerServer reasoner;

  /**
   * Load knowledge resources into the system
   */
  @Operation(summary = "Load knowledge resources", 
            description = "Loads knowledge resources from a worldview into the system")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Knowledge resources loaded successfully"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role")
  })
  @PostMapping(LOAD_KNOWLEDGE)
  public @ResponseBody ResourceSet loadKnowledge(
      @Parameter(description = "Worldview containing knowledge resources") @RequestBody Worldview resources, 
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var userScope = authorization.getScope(UserScope.class);
      return reasoner.klabService().loadKnowledge(resources, userScope);
    }
    return ResourceSet.empty();
  }

  /**
   * Update knowledge resources in the system
   */
  @Operation(summary = "Update knowledge resources", 
            description = "Updates existing knowledge resources in the system")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Knowledge resources updated successfully"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role")
  })
  @PostMapping(UPDATE_KNOWLEDGE)
  public @ResponseBody ResourceSet loadKnowledge(
      @Parameter(description = "Resource changes to apply") @RequestBody ResourceSet changes, 
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var userScope = authorization.getScope(UserScope.class);
      return reasoner.klabService().updateKnowledge(changes, userScope);
    }
    return ResourceSet.empty();
  }

  /**
   * Define a new concept in the system
   */
  @Operation(summary = "Define a new concept", 
            description = "Creates a new concept definition in the system")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Concept defined successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid concept statement"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role")
  })
  @PostMapping(DEFINE_CONCEPT)
  public @ResponseBody Concept defineConcept(
      @Parameter(description = "Concept statement to define") @RequestBody KimConceptStatement statement, 
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var userScope = authorization.getScope(UserScope.class);
      return reasoner.klabService().defineConcept(statement, userScope);
    }
    return null;
  }
}
