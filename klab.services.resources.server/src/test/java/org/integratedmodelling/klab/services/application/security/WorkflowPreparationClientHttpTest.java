package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.ResourcesClient;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.workflow.ProposalSchemaValidator;
import org.integratedmodelling.klab.services.resources.workflow.WorkflowManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A real catalog-created ResourcesClient crosses HTTP into the production Spring controller,
 * provider, workflow manager, bundled parser, compiler and preparation action. Only authentication,
 * project contents and storage use the existing synthetic runtime fixture. In particular, no client
 * method is mocked, and the HTTP bridge never manufactures workflow success or error responses.
 */
class WorkflowPreparationClientHttpTest {
  @Test
  void nativeAndBrowserScopesSaveDirtyDetailsThenPrepareAgainstTheReturnedRevision() throws Exception {
    for (boolean advertised : List.of(true, false)) {
      var fixture = new WorkflowPreparationRuntimeTest.Fixture(advertised);
      try (var bridge = new ControllerBridge(fixture)) {
        ResourcesService client = bridge.client(fixture.scope);
        assertProductionDispatch(client);
        var flow = client.initializeFlow("ontology-review-pilot", initialization(), fixture.scope);
        assertNotNull(flow, "Initialization must return the real aggregate over HTTP");
        var stageId = flow.getCurrentStateIds().iterator().next();
        var before = flow.getStates().get(stageId);
        var saved = client.updateFlowStateDetails(flow.getId(), stageId,
            new Flow.StateDetailsUpdate(flow.getRevision(), "Saved native stage", "Saved editorial scope"),
            fixture.scope);
        assertNotNull(saved, "A successful details save must not fall back to the interface default");
        assertEquals(flow.getRevision() + 1, saved.getRevision());
        var savedStage = saved.getStates().get(stageId);
        assertEquals("Saved native stage", savedStage.getTitle());
        assertEquals("Saved editorial scope", savedStage.getDescription());
        assertEquals(before.getOwner(), savedStage.getOwner());
        assertEquals(before.getMetadata(), savedStage.getMetadata());
        assertEquals(before.getSchemaId(), savedStage.getSchemaId());
        assertTrue(savedStage.getAttachments().isEmpty());

        var reloaded = client.getFlow(flow.getId(), fixture.scope);
        assertEquals(saved.getRevision(), reloaded.getRevision());
        assertEquals(savedStage.getTitle(), reloaded.getStates().get(stageId).getTitle());
        var actions = client.getFlowActions(flow.getId(), stageId, fixture.scope);
        assertEquals(1, actions.size());
        var prepared = client.executeFlowAction(flow.getId(), stageId, "prepare-review-draft",
            preparation(reloaded.getRevision()), fixture.scope);
        assertEquals(saved.getRevision() + 1, prepared.getRevision());
        assertEquals(savedStage.getTitle(), prepared.getStates().get(stageId).getTitle());
        assertFivePublishedArtifacts(client, prepared, stageId, fixture.scope);
        assertEquals(5, fixture.store.blobs.size());
        assertEquals(5, client.getFlow(flow.getId(), fixture.scope)
            .getStates().get(stageId).getAttachments().size());
        assertEquals(5, new WorkflowManager(fixture.store).getFlow(flow.getId(), fixture.scope)
            .getStates().get(stageId).getAttachments().size());

        var details = bridge.calls.stream().filter(c -> c.path().endsWith("/details")).toList();
        assertEquals(1, details.size(), "One save must produce one real controller request");
        assertEquals("POST", details.getFirst().method());
        assertEquals(200, details.getFirst().status());
        assertEquals(flow.getRevision(), Utils.Json.parseObject(details.getFirst().body(),
            Flow.StateDetailsUpdate.class).expectedRevision());
        var preparations = bridge.preparations();
        assertEquals(1, preparations.size(), "The first preparation attempt must succeed");
        assertEquals(200, preparations.getFirst().status());
        assertEquals(saved.getRevision(), Utils.Json.parseObject(preparations.getFirst().body(),
            WorkflowBehavior.ActionRequest.class).expectedRevision());
      }
    }
  }

  @Test
  void realConflictAndDeniedDetailsSavesPreservePublishedStageAndCannotDuplicatePreparation()
      throws Exception {
    var fixture = new WorkflowPreparationRuntimeTest.Fixture(true);
    try (var bridge = new ControllerBridge(fixture)) {
      ResourcesService client = bridge.client(fixture.scope);
      var flow = client.initializeFlow("ontology-review-pilot", initialization(), fixture.scope);
      var stageId = flow.getCurrentStateIds().iterator().next();
      var saved = client.updateFlowStateDetails(flow.getId(), stageId,
          new Flow.StateDetailsUpdate(flow.getRevision(), "Accepted title", "Accepted description"),
          fixture.scope);
      var prepare = preparation(saved.getRevision());
      var prepared = client.executeFlowAction(flow.getId(), stageId, "prepare-review-draft", prepare, fixture.scope);
      String persisted = Utils.Json.asString(fixture.store.getFlow(flow.getId()));
      var blobIds = Set.copyOf(fixture.store.blobs.keySet());

      var stale = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.updateFlowStateDetails(flow.getId(), stageId,
              new Flow.StateDetailsUpdate(saved.getRevision(), "Stale overwrite", "Must not persist"), fixture.scope));
      assertEquals(409, stale.getStatus(), "Conflict must propagate, not become null or unsupported");
      assertEquals(persisted, Utils.Json.asString(fixture.store.getFlow(flow.getId())));

      ResourcesService otherClient = bridge.client(bridge.otherScope);
      var denied = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> otherClient.updateFlowStateDetails(flow.getId(), stageId,
              new Flow.StateDetailsUpdate(prepared.getRevision(), "Unauthorized overwrite", "Must not persist"),
              bridge.otherScope));
      assertEquals(403, denied.getStatus(), "A real non-owner editor must be denied by the manager");
      assertEquals(persisted, Utils.Json.asString(fixture.store.getFlow(flow.getId())));
      assertEquals(blobIds, fixture.store.blobs.keySet());
      assertEquals(1, bridge.preparations().size(), "Failed saves must not submit a preparation");

      assertThrows(RuntimeException.class, () -> client.executeFlowAction(flow.getId(), stageId,
          "prepare-review-draft", prepare, fixture.scope));
      assertEquals(409, bridge.preparations().getLast().status());
      assertEquals(persisted, Utils.Json.asString(fixture.store.getFlow(flow.getId())));
      assertEquals(blobIds, fixture.store.blobs.keySet(), "Replaying preparation must not publish duplicates");
      assertFivePublishedArtifacts(client, client.getFlow(flow.getId(), fixture.scope), stageId, fixture.scope);
      assertEquals(List.of(200, 409, 403), bridge.calls.stream()
          .filter(c -> c.path().endsWith("/details")).map(Call::status).toList());
    }
  }

  private static Flow.InitializationRequest initialization() {
    var initial = Flow.State.create();
    initial.setSchemaId("bootstrap");
    initial.setAssetUrn("test.ontology");
    initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
    initial.setTitle("Unsaved stage title");
    initial.setDescription("Unsaved editorial scope");
    var request = Flow.InitializationRequest.create();
    request.setInitialState(initial);
    request.setAttachments(List.of());
    return request;
  }

  private static WorkflowBehavior.ActionRequest preparation(long revision) {
    return new WorkflowBehavior.ActionRequest(revision, Map.of(
        "proposal_id", "client-proposal", "revision_id", "client-r1", "domain", "test domain",
        "purpose", "Review ontology", "requested_tier", 1, "target_community", "test community"));
  }

  private static void assertProductionDispatch(ResourcesService client) throws Exception {
    assertEquals(ResourcesClient.class, client.getClass());
    assertFalse(mockingDetails(client).isMock());
    var signature = new Class<?>[] {String.class, String.class, Flow.StateDetailsUpdate.class, UserScope.class};
    assertEquals(ResourcesClient.class, client.getClass().getMethod("updateFlowStateDetails", signature).getDeclaringClass(),
        "A stale common JAR must not silently select ResourcesService's unsupported default");
    assertEquals(ResourcesProvider.class, ResourcesProvider.class.getMethod("updateFlowStateDetails", signature).getDeclaringClass());
    for (var type : List.of(ResourcesService.class, ResourcesClient.class, ResourcesProvider.class)) {
      System.out.println("Stage-details dispatch: " + type.getName() + " loaded from "
          + type.getProtectionDomain().getCodeSource().getLocation());
    }
  }

  private static void assertFivePublishedArtifacts(ResourcesService client, Flow flow, String stageId, UserScope scope) {
    var attachments = flow.getStates().get(stageId).getAttachments();
    assertEquals(5, attachments.size());
    assertEquals(Set.of("bootstrap-proposal:client-r1-proposal.json", "bootstrap-comments:client-r1-comments.json",
        "candidate-ontology:client-r1.kwv", "supporting-material:client-r1-inputs.json", "supporting-material:BOOTSTRAP.md"),
        attachments.stream().map(a -> a.getType() + ":" + a.getFileName()).collect(Collectors.toSet()));
    for (var attachment : attachments) {
      byte[] bytes = client.getFlowAttachment(flow.getId(), attachment.getId(), scope);
      assertNotNull(bytes);
      assertTrue(bytes.length > 0);
      if ("bootstrap-proposal".equals(attachment.getType())) {
        assertEquals(ProposalReview.CheckStatus.PASS, ProposalSchemaValidator.bundled().validate(bytes).status());
      } else if ("candidate-ontology".equals(attachment.getType())) {
        assertEquals(WorkflowPreparationRuntimeTest.SOURCE, new String(bytes, StandardCharsets.UTF_8));
      } else if ("BOOTSTRAP.md".equals(attachment.getFileName())) {
        assertEquals("# Context\nSynthetic rationale", new String(bytes, StandardCharsets.UTF_8));
      }
    }
    assertNotNull(flow.getStates().get(stageId).getMetadata().get("ontologyReviewBootstrap"));
  }

  private record Call(String method, String path, String body, int status) {}

  /** All HTTP workflow results, including failures, come from the real controller dispatch. */
  private static final class ControllerBridge implements AutoCloseable {
    private final WorkflowPreparationRuntimeTest.Fixture fixture;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<BaseServiceClient> clients = new ArrayList<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final EngineAuthorization otherPrincipal;
    private final ServiceUserScope otherScope;

    ControllerBridge(WorkflowPreparationRuntimeTest.Fixture fixture) throws Exception {
      this.fixture = fixture;
      var other = new UserIdentityImpl();
      other.setUsername("other-editor"); other.setAuthenticated(true); other.setAnonymous(false);
      other.getGroups().addAll(fixture.principal.getGroups());
      otherPrincipal = new EngineAuthorization("fixture", "other-editor", "synthetic-other-token", Map.of(),
          fixture.principal.getGroups(), List.of(Role.ROLE_USER));
      otherPrincipal.setAuthenticated(true);
      var managed = new ServiceUserScope(other, fixture.provider);
      managed.setId("test:other-editor");
      otherScope = managed.forAuthorization(otherPrincipal);
      otherPrincipal.setScope(otherScope);
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(executor);
      server.createContext("/", this::dispatch);
      server.start();
    }

    ResourcesService client(UserScope scope) throws Exception {
      var client = ServiceClientCatalog.INSTANCE.getService(
          URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toURL(), null, scope, ResourcesService.class);
      clients.add((BaseServiceClient) client);
      return client;
    }

    List<Call> preparations() {
      return calls.stream().filter(c -> c.path().endsWith("/actions/prepare-review-draft")).toList();
    }

    private void dispatch(HttpExchange exchange) throws java.io.IOException {
      try {
        var principal = "other-editor".equals(exchange.getRequestHeaders().getFirst(ServicesAPI.USERNAME_HEADER))
            ? otherPrincipal : fixture.principal;
        byte[] body = exchange.getRequestBody().readAllBytes();
        var request = request(HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI())
            .principal(principal).content(body);
        var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null) request.contentType(contentType);
        var response = fixture.mvc.perform(request).andReturn().getResponse();
        calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            new String(body, StandardCharsets.UTF_8), response.getStatus()));
        byte[] result = response.getContentAsByteArray();
        if (response.getContentType() != null) exchange.getResponseHeaders().set("Content-Type", response.getContentType());
        exchange.sendResponseHeaders(response.getStatus(), result.length);
        try (var output = exchange.getResponseBody()) { output.write(result); }
      } catch (Exception failure) {
        // Transport failure remains a test failure, rather than an invented workflow response.
        exchange.close();
        throw new java.io.IOException("Real controller dispatch failed", failure);
      }
    }

    @Override public void close() throws Exception {
      for (var client : clients) {
        // Release polling without issuing BaseServiceClient.shutdown(), which is an admin command.
        var monitor = (ServiceClientCatalog.ClientMonitor) ReflectionTestUtils.getField(client, "monitor");
        assertNotNull(monitor);
        monitor.release(client);
      }
      server.stop(0);
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
