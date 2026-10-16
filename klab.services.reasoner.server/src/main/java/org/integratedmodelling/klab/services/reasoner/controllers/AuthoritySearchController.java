package org.integratedmodelling.klab.services.reasoner.controllers;

import java.security.Principal;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthoritySearchController {
  @Autowired private ReasonerServer reasoner;

  @PostMapping(value = ServicesAPI.REASONER.AUTHORITY_HIERARCHY, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<org.integratedmodelling.klab.api.knowledge.Concept> hierarchy(
      @RequestBody AuthorityHierarchyRequest request, Principal principal) {
    var scope = authorizedScope(principal);
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
          reasoner.klabService().resolveAuthorityHierarchy(request.authority(), request.identity(), scope));
    } catch (SecurityException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authority access denied");
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hierarchy request");
    }
  }

  static Scope authorizedScope(Principal principal) {
    if (!(principal instanceof EngineAuthorization authorization) || authorization.getScope() == null)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authority operations require an authorized scope");
    return authorization.getScope();
  }

  @PostMapping(value = ServicesAPI.REASONER.AUTHORITY_SEARCH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthoritySearchResponse> search(@RequestBody AuthoritySearchRequest request, Principal principal) {
    var scope = authorizedScope(principal);
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .body(reasoner.klabService().searchAuthority(request, scope));
    } catch (SecurityException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authority access denied");
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authority search request");
    }
  }
}
