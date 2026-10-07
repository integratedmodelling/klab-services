package org.integratedmodelling.klab.services.reasoner.controllers;

import java.security.Principal;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthorityCodelistController {
  @Autowired private ReasonerServer reasoner;
  @PostMapping(value = ServicesAPI.REASONER.AUTHORITY_CODELISTS, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthorityCodelistResponse> execute(@RequestBody AuthorityCodelistRequest request, Principal principal) {
    var scope = AuthoritySearchController.authorizedScope(principal);
    try {
      if ((request.operation() == AuthorityCodelistRequest.Operation.REVIEW
          || request.operation() == AuthorityCodelistRequest.Operation.DELETE
          || request.operation() == AuthorityCodelistRequest.Operation.CREATE
          || request.operation() == AuthorityCodelistRequest.Operation.UPDATE)
          && (!(principal instanceof EngineAuthorization authorization) || !authorization.isAdministrator()))
        throw new SecurityException("Codelist review requires an administrator");
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reasoner.klabService().authorityCodelists(request, scope));
    } catch (SecurityException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (java.util.ConcurrentModificationException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    } catch (java.util.NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }
}
