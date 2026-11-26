package org.integratedmodelling.resources.server.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.resources.storage.ReviewSubmission;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Lightweight project review index. File contents remain available through additional material. */
@RestController
@Secured(Role.USER)
@RequestMapping(ServicesAPI.API_BASE + "/project-review")
public class ProjectReviewController {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ResourcesServer server;

  public ProjectReviewController(ResourcesServer server) { this.server = server; }

  public record Bundle(String path, String sha256, JsonNode manifest) {}
  public record Material(String path, long size) {}
  public record Catalogue(boolean canComment, boolean canPropose, boolean canUpload,
                          List<Bundle> bundles, List<Material> material, List<String> unreadable) {}

  @GetMapping
  public Catalogue catalogue(@RequestParam String workspace, @RequestParam String project, Principal principal) {
    var scope = readable(workspace, project, principal);
    var service = server.klabService();
    var manager = service.getWorkspaceManager();
    var stage = manager.projectReviewStaging(project, scope);
    boolean write = stage.material() && (service.canProjectOperation(project, CRUDOperation.CREATE, scope)
        || service.canProjectOperation(project, CRUDOperation.UPDATE, scope));
    boolean propose = stage.material() && (write || service.canProjectOperation(project, CRUDOperation.UPDATE_METADATA, scope));
    var bundles = new ArrayList<Bundle>();
    var materials = new ArrayList<Material>();
    var unreadable = new ArrayList<String>();
    for (String section : List.of("comments", "proposals", "bootstrap", "evidence")) {
      collect(project, "review/" + section + "/", 0, materials, bundles, unreadable,
          section.equals("comments") || section.equals("proposals"));
    }
    var root = read(project, "review/");
    if (root != null) root.getEntries().stream()
        .filter(entry -> !entry.isDirectory() && entry.getPath().equals("review/decisions.jsonl"))
        .forEach(entry -> materials.add(new Material(entry.getPath(), entry.getSize())));
    bundles.sort(Comparator.comparing((Bundle item) -> item.manifest().path("created_at").asText())
        .thenComparing(Bundle::path).reversed());
    return new Catalogue(stage.comments(), propose, write, bundles, materials, unreadable);
  }

  private void collect(String project, String path, int depth, List<Material> materials,
      List<Bundle> bundles, List<String> unreadable, boolean manifests) {
    if (depth > 8 || manifests && depth > 2) return;
    var directory = read(project, path);
    if (directory == null) return;
    for (var entry : directory.getEntries()) {
      if (entry.isDirectory()) {
        collect(project, entry.getPath() + "/", depth + 1, materials, bundles, unreadable, manifests);
      } else if (!manifests) {
        materials.add(new Material(entry.getPath(), entry.getSize()));
      } else if (depth == 2 && entry.getPath().endsWith("/manifest.json")) {
        try {
          if (entry.getSize() > 128 * 1024) throw new IOException("Manifest is too large");
          var file = read(project, entry.getPath());
          if (file == null) continue;
          byte[] bytes = file.getContent();
          var manifest = JSON.readTree(bytes);
          if (manifest == null || !project.equals(manifest.path("target").path("project").asText())
              || !manifest.path("target").path("urn").isTextual() || !manifest.path("target").path("kind").isTextual()
              || !manifest.path("id").isTextual() || !manifest.path("contributor").isTextual()
              || !manifest.path("files").isObject() || !manifest.path("base").isObject()
              || !(entry.getPath().startsWith("review/" + manifest.path("kind").asText() + "s/")))
            throw new IOException("Invalid review manifest");
          bundles.add(new Bundle(entry.getPath(), ReviewSubmission.hash(bytes), manifest));
        } catch (IOException | RuntimeException failure) {
          unreadable.add(entry.getPath());
        }
      }
    }
  }

  private ProjectMaterial read(String project, String path) {
    return server.klabService().getWorkspaceManager().readMaterial(ProjectMaterial.coordinates(project + "/" + path));
  }

  @PostMapping("/evidence")
  public ReviewSubmission.Receipt evidence(@RequestParam String workspace,
      @RequestBody ReviewSubmission.Evidence request, Principal principal) {
    if (request.target() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence target required");
    var scope = readable(workspace, request.target().project(), principal);
    try {
      return server.klabService().getWorkspaceManager().submitReviewEvidence(request, scope);
    } catch (org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException forbidden) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbidden.getMessage(), forbidden);
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
    }
  }

  private UserScope readable(String workspace, String project, Principal principal) {
    if (!(principal instanceof EngineAuthorization authorization)
        || !(authorization.getScope() instanceof UserScope scope)
        || scope.getUser() == null || scope.getUser().isAnonymous())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    if (!server.klabService().canReadWorkspaceProject(workspace, project, scope))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    return scope;
  }
}
