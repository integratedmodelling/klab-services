package org.integratedmodelling.klab.services.application.controllers;

import java.util.Map;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.HubWebAuthentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Exchanges an identity-provider credential without putting either upstream token in a cookie. */
@RestController
public class WebAuthenticationController {
  private final HubWebAuthentication authentication;
  private final ServiceNetworkedInstance<?> instance;

  public WebAuthenticationController(HubWebAuthentication authentication, ServiceNetworkedInstance<?> instance) {
    this.authentication = authentication;
    this.instance = instance;
  }

  @PostMapping(HubWebAuthentication.ENDPOINT)
  public ResponseEntity<?> login(@RequestHeader(value = "Authorization", required = false) String header) {
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .body(authentication.exchange(bearer(header), instance));
    } catch (ResponseStatusException e) {
      return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore())
          .body(Map.of("message", e.getReason()));
    }
  }

  @DeleteMapping(HubWebAuthentication.ENDPOINT)
  public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
    authentication.revoke(bearer(header));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String bearer(String header) {
    return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
        ? header.substring(7).trim() : null;
  }
}
