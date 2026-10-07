package org.integratedmodelling.klab.services.reasoner.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Authenticated publication of documentation belonging to configured authority identities. */
@RestController
public class AuthorityDocumentationController {
  @Autowired private ReasonerServer reasoner;

  @Operation(summary = "Get identity documentation URLs keyed by media type",
      description = "Preserves upstream HTTP URLs and publishes host-local files through the Reasoner. "
          + "Content URLs require the same authentication as this request.")
  @GetMapping(value = ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> documentation(
      @RequestParam("authority") String authority, @RequestParam("identity") String identity,
      Principal principal, HttpServletRequest request) {
    var published = new LinkedHashMap<String, String>();
    documents(authority, identity, principal).forEach((type, source) -> {
      parseMediaType(type);
      URI uri = supportedUri(source);
      if ("file".equals(uri.getScheme())) {
        // URI variables encode reserved characters in IDs and MIME types without changing them.
        String content = ServletUriComponentsBuilder.fromRequestUri(request)
            .path("/content").replaceQuery(null)
            .queryParam("authority", "{authority}")
            .queryParam("identity", "{identity}")
            .queryParam("mediaType", "{mediaType}")
            .encode().buildAndExpand(authority, identity, type).toUriString();
        published.put(type, content);
      } else {
        published.put(type, uri.toString());
      }
    });
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(published);
  }

  @Operation(summary = "Read an identity documentation resource",
      description = "Streams a provider-declared local file in its media type, or redirects to an upstream HTTP URL. "
          + "Accepts only an authority, identity and advertised media type; never a file path or source URL.")
  @GetMapping(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT)
  public ResponseEntity<Resource> content(
      @RequestParam("authority") String authority, @RequestParam("identity") String identity,
      @RequestParam("mediaType") String mediaType, Principal principal) {
    var source = documents(authority, identity, principal).get(mediaType);
    if (source == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documentation media type is unavailable");
    MediaType type = parseMediaType(mediaType);
    URI uri = supportedUri(source);
    if (!"file".equals(uri.getScheme())) {
      return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore()).location(uri).build();
    }
    final Path file;
    try {
      file = Path.of(uri);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid provider documentation resource");
    }
    if (!Files.isRegularFile(file) || !Files.isReadable(file))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documentation resource is unavailable");
    return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        // Provider HTML/SVG must not execute scripts in the Reasoner's origin.
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(new FileSystemResource(file));
  }

  private Map<String, URL> documents(String authority, String identity, Principal principal) {
    if (!(principal instanceof EngineAuthorization authorization) || authorization.getScope() == null)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authority documentation requires an authorized scope");
    try {
      return reasoner.klabService().getAuthorityDocumentation(authority, identity, authorization.getScope());
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (KlabValidationException | IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Authority documentation lookup failed");
    }
  }

  private static MediaType parseMediaType(String value) {
    try {
      var type = MediaType.parseMediaType(value);
      if (type.isWildcardType() || type.isWildcardSubtype()) throw new IllegalArgumentException();
      return type;
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid provider documentation media type");
    }
  }

  private static URI supportedUri(URL source) {
    try {
      URI uri = source.toURI();
      boolean file = "file".equals(uri.getScheme()) && (uri.getAuthority() == null || uri.getAuthority().isEmpty())
          && uri.getQuery() == null && uri.getFragment() == null;
      boolean web = ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
          && uri.getHost() != null && uri.getUserInfo() == null;
      if (!file && !web) throw new IllegalArgumentException();
      return uri;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unsupported provider documentation URL");
    }
  }
}
