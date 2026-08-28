package org.integratedmodelling.klab.services.application.controllers;

import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** Serves the public dashboard shell and its service-specific configuration. */
@Controller
public class KlabWebUiController {

  private final ServiceNetworkedInstance<?> instance;

  public KlabWebUiController(ServiceNetworkedInstance<?> instance) {
    this.instance = instance;
  }

  @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
  public String dashboard() {
    return "forward:/index.html";
  }

  /** Lets a user enter a compiled full-page extension directly without authenticating first. */
  @GetMapping(
      value = {
        "/ui/{componentName:[a-z0-9][a-z0-9-]*}",
        "/ui/{componentName:[a-z0-9][a-z0-9-]*}/"
      },
      produces = MediaType.TEXT_HTML_VALUE)
  public String fullPageComponent() {
    return "forward:/index.html";
  }

  @ResponseBody
  @GetMapping(value = "/public/ui/config", produces = MediaType.APPLICATION_JSON_VALUE)
  public WebUiConfiguration configuration() {
    return instance.webUiConfiguration();
  }

  /** Serve a prebuilt ESM file explicitly declared by an installed k.LAB component. */
  @ResponseBody
  @GetMapping("/public/ui/components/{componentId}/{version}/{*resourcePath}")
  public ResponseEntity<byte[]> componentModule(
      @PathVariable String componentId,
      @PathVariable String version,
      @PathVariable String resourcePath) {
    var registry = instance.klabService().getComponentRegistry();
    if (registry == null) {
      return ResponseEntity.notFound().build();
    }
    var normalizedResourcePath =
        resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    var resource = registry.getWebUiResource(componentId, version, normalizedResourcePath);
    if (resource.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    var mediaType =
        resource.get().filename().endsWith(".js")
                || resource.get().filename().endsWith(".mjs")
            ? MediaType.valueOf("text/javascript")
            : MediaTypeFactory.getMediaType(resource.get().filename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(mediaType)
        .header("X-Content-Type-Options", "nosniff")
        .body(resource.get().content());
  }
}
