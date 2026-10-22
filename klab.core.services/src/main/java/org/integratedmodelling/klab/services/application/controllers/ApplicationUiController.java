package org.integratedmodelling.klab.services.application.controllers;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.ui.ApplicationUiManager;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ui/instances")
public class ApplicationUiController {
  private final ApplicationUiManager manager;

  public ApplicationUiController(ApplicationUiManager manager) {
    this.manager = manager;
  }

  private EngineAuthorization user(Principal principal) {
    if (!(principal instanceof EngineAuthorization user))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return user;
  }

  @PostMapping
  public ResponseEntity<?> launch(
      @RequestBody ApplicationUiManager.Request request, Principal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(manager.launch(request, user(principal)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> state(@PathVariable String id, Principal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(manager.state(id, user(principal)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> stop(@PathVariable String id, Principal principal) {
    manager.stop(id, user(principal));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/{id}/ticket")
  public ResponseEntity<?> ticket(
      @PathVariable String id, Principal principal, HttpServletRequest request) {
    var url = URI.create(request.getRequestURL().toString());
    String origin = url.getScheme() + "://" + url.getRawAuthority();
    if (request.getHeader("Origin") != null && !origin.equals(request.getHeader("Origin")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    String credential = request.getHeader("Authorization");
    if (credential != null && credential.regionMatches(true, 0, "Bearer ", 0, 7))
      credential = credential.substring(7).trim();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(manager.ticket(id, origin, user(principal), credential));
  }
}
