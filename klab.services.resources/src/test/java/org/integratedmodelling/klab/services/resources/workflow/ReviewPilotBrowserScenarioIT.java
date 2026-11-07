package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.base.*;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in IT: real browser UI -> loopback fixture protocol -> production manager/native store.
 * Explicit synthetic authenticated scopes replace login. Never bind outside loopback or deploy. */
class ReviewPilotBrowserScenarioIT {
  @TempDir Path directory;
  final Map<String, UserScope> actors = new LinkedHashMap<>();
  ResourcesKBox store;
  WorkflowManager manager;
  OntologyReviewBootstrapActionTest.Fixture fixture;
  int restarts;
  String createdFlowId;
  int creations;

  ResourcesKBox open() {
    var service = mock(BaseService.class); when(service.serviceType()).thenReturn(KlabService.Type.RESOURCES);
    return new ResourcesKBox(mock(Scope.class), ServiceStartupOptions.create(KlabService.Type.RESOURCES,
        new String[]{"-dataDir", directory.toString()}), service);
  }
  @Test void boundedFourPrincipalBrowserScenario() throws Exception {
    store = open();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool(); Process browser = null;
    try {
      fixture = new OntologyReviewBootstrapActionTest().new Fixture("# Fixture context\nReview containment and attribution.", store, false);
      manager = fixture.manager; actors.put("editor", fixture.editor);
      assertTrue(manager.listFlows(true, fixture.editor).isEmpty(), "Browser must begin with no pre-created flow");
      var identities = new ProposalReviewProtocolTest();
      actors.put("reviewer-a", identities.scope("reviewer-a", "REVIEWER"));
      actors.put("reviewer-b", identities.scope("reviewer-b", "REVIEWER"));
      actors.put("community", new ReviewCycleProtocolTest().community("community", true, false));
      server.setExecutor(executor); server.createContext("/", this::handle); server.start();
      Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
      while (root != null && !Files.exists(root.resolve("klab.core.services/src/main/webui/playwright.review.config.ts"))) root = root.getParent();
      assertNotNull(root, "Run from the isolated Services reactor");
      Path ui = root.resolve("klab.core.services/src/main/webui");
      assertTrue(Files.exists(ui.resolve("node_modules/@playwright/test/cli.js")), "Isolated UI dependencies required");
      var process = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test", "--config", "playwright.review-e2e.config.ts")
          .directory(ui.toFile()).redirectErrorStream(true).redirectOutput(root.getParent().resolve("editor-journey-e2e-browser.log").toFile());
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      process.environment().put("VITE_REVIEW_E2E_API", base); process.environment().put("KLAB_REVIEW_E2E_API", base);
      process.environment().put("KLAB_REVIEW_E2E_REPORT", root.getParent().resolve("editor-journey-e2e-scenario.json").toString());
      process.environment().put("KLAB_TEST_BROWSER_CHANNEL", "msedge");
      browser = process.start(); assertTrue(browser.waitFor(180, TimeUnit.SECONDS), "Bounded browser scenario timed out");
      assertEquals(0, browser.exitValue(), "See ../editor-journey-e2e-browser.log");
      assertNotNull(createdFlowId, "The browser must create its own bootstrap through the form");
      assertEquals(1, creations, "Successful creation must not leave an accidental repeated-create action");
      assertEquals(1, manager.listFlows(true, fixture.editor).size());
      var flow = store.getFlow(createdFlowId); assertEquals(2, flow.getReviewCycles().size());
      var bootstrap = flow.getStates().values().stream().filter(s -> "bootstrap".equals(s.getSchemaId())).findFirst().orElseThrow();
      assertEquals("Native ontology editor preparation", bootstrap.getTitle());
      assertEquals("Edited bootstrap scope, preserved through preparation and reload.", bootstrap.getDescription());
      var reference = bootstrap.getAttachments().stream().filter(a -> "review-reference.md".equals(a.getFileName())).findFirst().orElseThrow();
      assertEquals("# Native editor reference\nReference evidence uploaded from the bootstrap form.",
          new String(manager.getAttachment(flow.getId(), reference.getId(), fixture.editor), StandardCharsets.UTF_8));
      var note = bootstrap.getAttachments().stream().filter(a -> a.getFileName().startsWith("preparation-note-")).findFirst().orElseThrow();
      assertEquals("Bootstrap editor rationale from native form",
          new String(manager.getAttachment(flow.getId(), note.getId(), fixture.editor), StandardCharsets.UTF_8));
      assertTrue(bootstrap.getAttachments().stream().anyMatch(a -> "editor-proposal.json".equals(a.getFileName())),
          "User proposal file uploaded through the actual editor survives review and restart");
      assertTrue(flow.getReviewCycles().stream().allMatch(c -> c.closedAt() != null)); assertEquals(1, restarts);
      assertTrue(manager.getReviewCycles(flow.getId(), actors.get("community")).cycles().stream()
          .flatMap(c -> c.rounds().stream()).allMatch(r -> r.audience() == ReviewCycles.Audience.COMMUNITY));
      assertEquals(Flow.Status.CLOSED, flow.getStatus(), "Explicit termination closes the flow without publication");
      assertTrue(flow.getCurrentStateIds().isEmpty());
      assertTrue(flow.getStates().values().stream().anyMatch(s -> "terminated".equals(s.getSchemaId()) && s.getStatus() == Flow.StateStatus.CLOSED));
      assertEquals(ReviewCycles.Phase.TERMINATED, manager.getReviewCycles(flow.getId(), fixture.editor).phase());
      // The same private flat-key/string ledger must survive the real Nitrite/RocksDB restart.
      try (var archives = new org.integratedmodelling.klab.resources.ReviewArchiveOutbox(directory.resolve("archive-replay"), 10)) {
        assertEquals(2, ReviewWorkflowArchives.replayCommitted(store, archives));
        assertEquals(0, ReviewWorkflowArchives.replayCommitted(store, archives));
        assertTrue(archives.items().stream().allMatch(i -> i.status() == org.integratedmodelling.klab.resources.ReviewArchiveOutbox.Status.PENDING));
      }
      try (var archives = new org.integratedmodelling.klab.resources.ReviewArchiveOutbox(directory.resolve("archive-replay"), 10)) {
        assertEquals(0, ReviewWorkflowArchives.replayCommitted(store, archives)); assertEquals(2, archives.items().size());
      }
    } finally {
      if (browser != null && browser.isAlive()) {
        browser.descendants().forEach(ProcessHandle::destroyForcibly); browser.destroyForcibly();
      }
      server.stop(0); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
      if (store != null) store.shutdown();
    }
  }
  private synchronized void handle(HttpExchange exchange) throws java.io.IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    if (origin != null && !origin.equals("http://127.0.0.1:4233")) { respond(exchange, 403, "Forbidden origin", true); return; }
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1:4233");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Review-Fixture-Principal");
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
    exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
    if (exchange.getRequestMethod().equals("OPTIONS")) { respond(exchange, 204, "", true); return; }
    String name = exchange.getRequestHeaders().getFirst("X-Review-Fixture-Principal");
    var actor = actors.get(name);
    if (actor == null) { respond(exchange, 403, "Fixture identity required", true); return; }
    try {
      String path = exchange.getRequestURI().getPath(); String method = exchange.getRequestMethod();
      if (path.equals("/fixture/restart") && method.equals("POST") && name.equals("editor")) {
        store.shutdown(); store = open(); manager = new WorkflowManager(store);
        manager.setBehaviorBridge(new WorkflowBehaviorBridge(() -> new AgentCompiler.Resolver() {}, owner -> null, () -> fixture.behavior));
        restarts++; respond(exchange, 200, Map.of("restarts", restarts), false); return;
      }
      if (path.equals("/fixture/notices") && method.equals("POST") && name.equals("editor")) {
        try (var box = new ReviewNotificationOutbox(directory.resolve("fake-notices"), Clock.systemUTC(), 1000, 3, Duration.ofSeconds(1))) {
          int replayed = ReviewWorkflowNotifications.replayCommitted(store, box);
          var policy = new ReviewRecipientPolicy(store, principal -> actors.values().stream()
              .filter(scope -> ReviewWorkflowNotifications.recipientId(scope.getUser().getUsername()).equals(principal)).findFirst()
              .map(scope -> new ReviewRecipientPolicy.Account(scope, "fixture-" + principal.substring(10, 18) + "@example.invalid",
                  true, ReviewRecipientPolicy.Consent.GRANTED, Instant.now())), Clock.systemUTC());
          int dispatched = box.dispatch(100, policy, (mailbox, subject, body) -> {
            assertEquals("Ontology review update", subject); assertFalse(body.contains("Reviewer A"));
            assertFalse(body.contains("Community contribution")); return ReviewNotificationOutbox.Result.ACCEPTED;
          });
          respond(exchange, 200, Map.of("replayed", replayed, "dispatched", dispatched,
              "accepted", box.deliveries().stream().filter(d -> d.status() == ReviewNotificationOutbox.Status.ACCEPTED).count()), false); return;
        }
      }
      String body = "";
      if (method.equals("POST") || method.equals("PATCH")) {
        byte[] bytes = exchange.getRequestBody().readNBytes(8 * 1024 * 1024 + 1);
        if (bytes.length > 8 * 1024 * 1024) throw new KlabIllegalArgumentException("Fixture body limit");
        body = new String(bytes, StandardCharsets.UTF_8);
      }
      if (path.equals("/api/v1/workflows") && method.equals("GET")) {
        respond(exchange, 200, manager.list(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.WORKFLOW, actor), false); return;
      }
      if (path.equals("/api/v1/workflows/ontology-review-pilot") && method.equals("GET")) {
        respond(exchange, 200, manager.getWorkflow("ontology-review-pilot", actor), false); return;
      }
      if (path.equals("/api/v1/workflows/ontology-review-pilot/creation") && method.equals("GET")) {
        var workflow = manager.getWorkflow("ontology-review-pilot", actor);
        var participant = WorkflowParticipant.from(actor);
        var initialStates = participant.isAuthenticated() && participant.isWorkflowPermitted(workflow)
            ? workflow.getTransitions().values().stream()
                .filter(t -> t.getSourceStates().contains(Workflow.INIT)
                    && participant.hasAnyRole(t.getRoles()) && !participant.getDisallowedTransitions().contains(t.getId()))
                .map(Workflow.TransitionSchema::getTargetState)
                .filter(id -> workflow.canAccess(workflow.getStates().get(id), participant)).distinct().toList()
            : List.<String>of();
        respond(exchange, 200, Map.of("canCreate", !initialStates.isEmpty(), "initialStateIds", initialStates, "version", workflow.getVersion()), false); return;
      }
      if (path.equals("/api/v1/flows") && method.equals("GET")) { respond(exchange, 200, manager.listFlows(true, actor), false); return; }
      if (path.equals("/api/v1/flows/initialize") && method.equals("POST")) {
        if (!"workflowId=ontology-review-pilot".equals(exchange.getRequestURI().getQuery()))
          throw new KlabIllegalArgumentException("Fixture workflow required");
        var created = manager.initializeFlow("ontology-review-pilot", Utils.Json.parseObject(body, Flow.InitializationRequest.class), actor);
        createdFlowId = created.getId(); creations++;
        respond(exchange, 200, created, false); return;
      }
      var segments = path.substring(1).split("/");
      if (segments.length < 4 || !path.startsWith("/api/v1/flows/") || !Objects.equals(createdFlowId, segments[3]))
        throw new KlabResourceAccessException("Fixture route not admitted");
      String flow = segments[3]; Object result; boolean plain = false;
      if (segments.length == 4 && method.equals("GET")) result = manager.getFlow(flow, actor);
      else if (segments.length == 5 && segments[4].equals("review-cycles")) result = method.equals("GET")
          ? manager.getReviewCycles(flow, actor) : manager.reviewCycle(flow, Utils.Json.parseObject(body, ReviewCycles.Command.class), actor);
      else if (segments.length == 8 && segments[4].equals("review-cycles") && segments[6].equals("attachments")) {
        result = Base64.getEncoder().encodeToString(manager.getReviewArtifact(flow, segments[5], segments[7], actor)); plain = true;
      } else if (segments.length == 6 && segments[4].equals("attachments")) {
        result = Base64.getEncoder().encodeToString(manager.getAttachment(flow, segments[5], actor)); plain = true;
      } else if (segments.length == 6 && segments[4].equals("states") && method.equals("PATCH"))
        result = manager.updateStateDetails(flow, segments[5], Utils.Json.parseObject(body, Flow.StateDetailsUpdate.class), actor);
      else if (segments.length == 7 && segments[4].equals("states") && segments[6].equals("actions"))
        result = manager.getActions(flow, segments[5], actor);
      else if (segments.length == 8 && segments[4].equals("states") && segments[6].equals("actions"))
        result = manager.executeAction(flow, segments[5], segments[7], Utils.Json.parseObject(body, WorkflowBehavior.ActionRequest.class), actor);
      else if (segments.length == 7 && segments[4].equals("states") && segments[6].equals("attachments"))
        result = manager.addAttachment(flow, segments[5], Utils.Json.parseObject(body, Flow.AttachmentUpload.class), actor);
      else throw new KlabResourceAccessException("Fixture route not admitted");
      respond(exchange, 200, result, plain);
    } catch (KlabResourceAccessException | KlabAuthorizationException denied) { respond(exchange, 403, "Forbidden", true); }
    catch (KlabIllegalStateException | IllegalStateException conflict) { respond(exchange, 409, "Conflict", true); }
    catch (KlabIllegalArgumentException invalid) { respond(exchange, 400, "Invalid request: " + invalid.getMessage(), true); }
    catch (Exception error) {
      error.printStackTrace(System.err); // Test-only diagnostics; no request payloads or real identities.
      respond(exchange, 500, "Fixture error: " + error.getClass().getSimpleName(), true);
    }
  }
  private static void respond(HttpExchange exchange, int status, Object value, boolean plain) throws java.io.IOException {
    byte[] bytes = (plain ? String.valueOf(value) : Utils.Json.asString(value)).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", plain ? "text/plain; charset=utf-8" : "application/json");
    exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
    try (var output = exchange.getResponseBody()) { if (status != 204) output.write(bytes); }
  }
}
