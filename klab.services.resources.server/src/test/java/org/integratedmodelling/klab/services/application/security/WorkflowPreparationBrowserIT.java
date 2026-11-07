package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** Opt-in loopback bridge to real Spring endpoints. Only Hub response and project/store data are synthetic. */
class WorkflowPreparationBrowserIT {
  public static void main(String[] args) throws Exception {
    new WorkflowPreparationBrowserIT().serveRealPreparationUntilBrowserFinishes();
  }
  @Test
  @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "klab.test.preparation.browser", matches = "true")
  void serveRealPreparationUntilBrowserFinishes() throws Exception {
    var browser = new WorkflowPreparationRuntimeTest.Fixture(false);
    var nativeEditor = new WorkflowPreparationRuntimeTest.Fixture(true, browser.store);
    var finished = new CountDownLatch(1);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newSingleThreadExecutor();
    try {
      server.setExecutor(executor);
      server.createContext("/", exchange -> {
        try {
          var origin = exchange.getRequestHeaders().getFirst("Origin");
          if (origin != null && !origin.equals("http://127.0.0.1:4241")) { send(exchange, 403, "Forbidden origin", "text/plain"); return; }
          exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1:4241");
          exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Review-Fixture-Principal");
          exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
          exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
          String method = exchange.getRequestMethod(), path = exchange.getRequestURI().getPath();
          if (method.equals("OPTIONS")) { send(exchange, 204, "", "text/plain"); return; }
          if (!"editor".equals(exchange.getRequestHeaders().getFirst("X-Review-Fixture-Principal"))) {
            send(exchange, 403, "Synthetic editor required", "text/plain"); return;
          }
          if (path.equals("/fixture/stop") && method.equals("POST")) {
            send(exchange, 200, "{}", "application/json"); finished.countDown(); return;
          }
          if (path.equals("/fixture/native-prepare") && method.equals("POST")) {
            var flow = nativeEditor.create();
            var stage = flow.getCurrentStateIds().iterator().next();
            var action = new WorkflowBehavior.ActionRequest(flow.getRevision(), Map.of(
                "proposal_id", "native-proposal", "revision_id", "native-r1", "domain", "Native test domain",
                "purpose", "Native preparation followed by browser review", "requested_tier", 1, "target_community", "test community"));
            String target = ServicesAPI.RESOURCES.FLOW_ACTION.replace("{flowId}", flow.getId())
                .replace("{stateId}", stage).replace("{actionId}", "prepare-review-draft");
            var result = nativeEditor.mvc.perform(post(target).principal(nativeEditor.principal)
                .contentType(MediaType.APPLICATION_JSON).content(Utils.Json.asString(action))).andReturn().getResponse();
            if (result.getStatus() != 200) {
              send(exchange, result.getStatus(), result.getContentAsString(), result.getContentType()); return;
            }
            var prepared = Utils.Json.parseObject(result.getContentAsString(), Flow.class);
            send(exchange, 200, Utils.Json.asString(Map.of("flowId", flow.getId(), "stageId", stage,
                "revision", prepared.getRevision())), "application/json"); return;
          }
          if (!path.startsWith("/api/v1/")) { send(exchange, 404, "Unknown route", "text/plain"); return; }
          byte[] body = exchange.getRequestBody().readNBytes(8 * 1024 * 1024 + 1);
          if (body.length > 8 * 1024 * 1024) { send(exchange, 413, "Body limit", "text/plain"); return; }
          var request = request(HttpMethod.valueOf(method), exchange.getRequestURI()).principal(browser.principal).content(body);
          var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
          if (contentType != null) request.contentType(contentType);
          var response = browser.mvc.perform(request).andReturn().getResponse();
          send(exchange, response.getStatus(), response.getContentAsString(), response.getContentType());
        } catch (Exception failure) {
          failure.printStackTrace(System.err);
          send(exchange, 500, "Test bridge failure: " + failure.getClass().getSimpleName(), "text/plain");
        }
      });
      server.start();
      Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
      while (root != null && !Files.isDirectory(root.resolve("klab.core.services"))) root = root.getParent();
      assertNotNull(root);
      var ready = root.getParent().resolve("preparation-http-fixture.json");
      Files.writeString(ready, Utils.Json.asString(Map.of("url", "http://127.0.0.1:" + server.getAddress().getPort(),
          "source", "real browser login, WorkflowController, ResourcesProvider, manager, bundled parser/compiler/runtime",
          "synthetic", "Hub profile response, project document/material and in-memory workflow store", "pid", ProcessHandle.current().pid())));
      System.out.println("Preparation fixture ready: " + ready);
      assertTrue(finished.await(10, TimeUnit.MINUTES), "Browser must finish within ten minutes and POST /fixture/stop");
      assertFalse(browser.store.flows.isEmpty(), "Browser must exercise a real flow");
      assertTrue(browser.store.blobs.size() >= 4, "Preparation must generate real stored artifacts");
    } finally {
      server.stop(0); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static void send(HttpExchange exchange, int status, String content, String type) throws java.io.IOException {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", type == null ? "application/json" : type);
    exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
    try (var output = exchange.getResponseBody()) { if (status != 204) output.write(bytes); }
  }
}
