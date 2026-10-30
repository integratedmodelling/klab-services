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

  @PostMapping(value = "/{id}/files/{nodeId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<?> upload(@PathVariable String id, @PathVariable String nodeId,
      @RequestParam String name, @RequestParam(defaultValue = "application/octet-stream") String mediaType,
      Principal principal, HttpServletRequest request) throws java.io.IOException {
    // Bound the stream before allocating a body; instance and node validation remain server-owned.
    var owner = user(principal);
    manager.state(id, owner);
    byte[] bytes = request.getInputStream().readNBytes(4194305);
    if (bytes.length > 4194304) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(manager.upload(id, nodeId, name, mediaType, bytes, owner));
  }

  @GetMapping("/{id}/files/{handle}")
  public ResponseEntity<?> file(@PathVariable String id, @PathVariable String handle, Principal principal) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(manager.file(id, handle, user(principal)));
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
    var owner = user(principal);
    var headers = new java.util.HashMap<String, String>();
    if (owner.isLocal() && loopback(request.getRemoteAddr())) {
      for (String header : java.util.List.of(org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER,
          org.integratedmodelling.klab.api.ServicesAPI.USERNAME_HEADER,
          org.integratedmodelling.klab.api.ServicesAPI.FEDERATION_HEADER)) {
        String value = request.getHeader(header);
        if (value != null && !value.isBlank()) headers.put(header, value);
      }
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(manager.ticket(id, origin, owner, credential, headers));
  }
  private static boolean loopback(String address) {
    if (address == null) return false;
    try { return java.net.InetAddress.getByName(address).isLoopbackAddress(); }
    catch (java.net.UnknownHostException invalid) { return false; }
  }
}
