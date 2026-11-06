package org.integratedmodelling.resources.server.controllers;

import java.security.Principal;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.resources.workflow.ReviewNotificationWorker;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

/** Read-only operational API. No replay trigger, SMTP enable switch or reconciliation action. */
@RestController
@Secured(Role.ADMINISTRATOR)
public class ReviewNotificationAdminController {
  @Autowired private ResourcesServer resourcesServer;
  @GetMapping("/api/v1/admin/review-notifications")
  public ResponseEntity<ReviewNotificationWorker.Health> health(@RequestParam(defaultValue = "50") int limit,
      Principal principal) {
    if (!(principal instanceof EngineAuthorization authorization) || !authorization.isAuthenticated()
        || !authorization.getRoles().contains(Role.ROLE_ADMINISTRATOR)
        || !(authorization.getScope() instanceof UserScope scope))
      throw new KlabAuthorizationException("Service administrator required");
    if (limit < 1 || limit > 100) return ResponseEntity.badRequest().build();
    return ResponseEntity.ok().header("Cache-Control", "private, no-store")
        .body(resourcesServer.klabService().reviewNotificationHealth(limit, scope));
  }
  @ExceptionHandler(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class)
  public ResponseEntity<Void> denied() { return ResponseEntity.status(403).build(); }
}
