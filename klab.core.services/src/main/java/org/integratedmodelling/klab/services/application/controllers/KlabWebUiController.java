package org.integratedmodelling.klab.services.application.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Serves the public dashboard shell and its service-specific configuration. */
@Controller
public class KlabWebUiController {

  private final ServiceNetworkedInstance<?> instance;
  private final String dashboardHtml;

  @Autowired
  public KlabWebUiController(ServiceNetworkedInstance<?> instance) {
    this(instance, loadDashboardHtml());
  }

  /**
   * Used by tests
   * @param instance
   * @param dashboardHtml
   */
  KlabWebUiController(ServiceNetworkedInstance<?> instance, String dashboardHtml) {
    this.instance = instance;
    this.dashboardHtml = dashboardHtml;
  }

  private static String loadDashboardHtml() {
    try (var input = new ClassPathResource("static/index.html").getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(
              "Cannot load the Web UI index.html from the classpath", e);
    }
  }

  @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> dashboard(HttpServletRequest request) {
    return dashboardResponse(request);
  }

  /** Lets a user enter a compiled full-page extension directly without authenticating first. */
  @GetMapping(
      value = {
        "/ui/{componentName:[a-z0-9][a-z0-9-]*}",
        "/ui/{componentName:[a-z0-9][a-z0-9-]*}/"
      },
      produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> fullPageComponent(HttpServletRequest request) {
    return dashboardResponse(request);
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

  private ResponseEntity<String> dashboardResponse(HttpServletRequest request) {

    var contextPath = request.getContextPath();
    var baseHref = contextPath == null || contextPath.isBlank() ? "/" : contextPath + "/";
    var html = dashboardHtml.replace("__KLAB_BASE_HREF__", baseHref);
    return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.TEXT_HTML)
            .body(html);
  }
}
