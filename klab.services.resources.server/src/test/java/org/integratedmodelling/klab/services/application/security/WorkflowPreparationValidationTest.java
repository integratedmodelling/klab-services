package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Invalid preparation inputs must be rejected before actor execution or publication. */
class WorkflowPreparationValidationTest {
  @Test void nonPositiveTierReturnsActionableBadRequestWithoutMutatingEitherScope() throws Exception {
    for (boolean advertised : List.of(true, false)) {
      var fixture = new WorkflowPreparationRuntimeTest.Fixture(advertised);
      var flow = fixture.create();
      var stage = flow.getCurrentStateIds().iterator().next();
      String path = path(flow, stage);
      String before = Utils.Json.asString(fixture.store.getFlow(flow.getId()));
      for (int tier : List.of(0, -1)) {
        var parameters = parameters(); parameters.put("requested_tier", tier);
        var response = fixture.mvc.perform(post(path).principal(fixture.principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision(), parameters))))
            .andReturn().getResponse();
        assertEquals(400, response.getStatus(), response.getContentAsString());
        var problem = Utils.Json.parseObject(response.getContentAsString(), Map.class);
        assertEquals("Requested tier must be an integer of 1 or greater", problem.get("detail"));
        assertEquals("INVALID_REVIEW_PREPARATION_INPUT", problem.get("code"));
        assertEquals("requested_tier", problem.get("field"));
        assertEquals(before, Utils.Json.asString(fixture.store.getFlow(flow.getId())));
        assertTrue(fixture.store.blobs.isEmpty());
      }
      var corrected = fixture.mvc.perform(post(path).principal(fixture.principal)
          .contentType(MediaType.APPLICATION_JSON)
          .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision(), parameters()))))
          .andReturn().getResponse();
      assertEquals(200, corrected.getStatus(), corrected.getContentAsString());
      var prepared = Utils.Json.parseObject(corrected.getContentAsString(), Flow.class);
      assertEquals(flow.getRevision() + 1, prepared.getRevision());
      assertEquals(5, prepared.getStates().get(stage).getAttachments().size());
      assertEquals(5, fixture.store.blobs.size());
    }
  }

  @Test void malformedOptionsReturnOnlyFixedFieldDiagnosticsWithoutPublication() throws Exception {
    var fixture = new WorkflowPreparationRuntimeTest.Fixture(true);
    var flow = fixture.create();
    var stage = flow.getCurrentStateIds().iterator().next();
    String before = Utils.Json.asString(fixture.store.getFlow(flow.getId()));
    var invalid = new ArrayList<Map.Entry<String, Object>>();
    for (Object tier : List.of(1.5, 2147483648L, "1", true, List.of(1), Map.of("private", "value")))
      invalid.add(Map.entry("requested_tier", tier));
    invalid.add(new AbstractMap.SimpleEntry<>("requested_tier", null));
    invalid.add(Map.entry("proposal_id", "private-value-must-not-leak/"));
    invalid.add(Map.entry("revision_id", "private-value-must-not-leak/"));
    invalid.add(Map.entry("domain", "   "));
    invalid.add(Map.entry("purpose", List.of("private-value-must-not-leak")));
    invalid.add(Map.entry("target_community", " "));
    for (var entry : invalid) {
      var parameters = parameters(); parameters.put(entry.getKey(), entry.getValue());
      var response = fixture.mvc.perform(post(path(flow, stage)).principal(fixture.principal)
          .contentType(MediaType.APPLICATION_JSON)
          .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision(), parameters))))
          .andReturn().getResponse();
      assertEquals(400, response.getStatus(), entry.getKey() + ": " + response.getContentAsString());
      var problem = Utils.Json.parseObject(response.getContentAsString(), Map.class);
      assertEquals("INVALID_REVIEW_PREPARATION_INPUT", problem.get("code"));
      assertEquals(entry.getKey(), problem.get("field"));
      assertFalse(response.getContentAsString().contains("private-value-must-not-leak"));
      assertFalse(response.getContentAsString().contains("RuntimeAgentBase"));
      assertEquals(before, Utils.Json.asString(fixture.store.getFlow(flow.getId())));
      assertTrue(fixture.store.blobs.isEmpty());
    }
    var missing = parameters(); missing.remove("requested_tier");
    assertEquals(400, fixture.mvc.perform(post(path(flow, stage)).principal(fixture.principal)
        .contentType(MediaType.APPLICATION_JSON)
        .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision(), missing))))
        .andReturn().getResponse().getStatus());
    // Revision checks still take precedence over input validation.
    assertEquals(409, fixture.mvc.perform(post(path(flow, stage)).principal(fixture.principal)
        .contentType(MediaType.APPLICATION_JSON)
        .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision() - 1, missing))))
        .andReturn().getResponse().getStatus());
    var other = new org.integratedmodelling.common.authentication.UserIdentityImpl();
    other.setUsername("other-editor"); other.setAuthenticated(true); other.setAnonymous(false);
    other.getGroups().addAll(fixture.principal.getGroups());
    var principal = new EngineAuthorization("fixture", "other-editor", "synthetic-other-token", Map.of(),
        fixture.principal.getGroups(), List.of(Role.ROLE_USER));
    principal.setAuthenticated(true);
    var managed = new org.integratedmodelling.klab.services.scopes.ServiceUserScope(other, fixture.provider);
    principal.setScope(managed.forAuthorization(principal));
    var rejected = parameters(); rejected.put("requested_tier", 0);
    // Other editors must still be denied before learning input-validation details.
    assertEquals(403, fixture.mvc.perform(post(path(flow, stage)).principal(principal)
        .contentType(MediaType.APPLICATION_JSON)
        .content(Utils.Json.asString(new WorkflowBehavior.ActionRequest(flow.getRevision(), rejected))))
        .andReturn().getResponse().getStatus());
    assertEquals(before, Utils.Json.asString(fixture.store.getFlow(flow.getId())));
    assertTrue(fixture.store.blobs.isEmpty());
  }

  @Test void realNativeClientRejectsBadTierThenPreparesAgainstTheSameRevision() throws Exception {
    var fixture = new WorkflowPreparationRuntimeTest.Fixture(true);
    var flow = fixture.create();
    var stage = flow.getCurrentStateIds().iterator().next();
    try (var bridge = new WorkflowPreparationClientHttpTest.ControllerBridge(fixture)) {
      var client = bridge.client(fixture.scope);
      var parameters = parameters(); parameters.put("requested_tier", 0);
      var failure = assertThrows(RuntimeException.class, () -> client.executeFlowAction(
          flow.getId(), stage, "prepare-review-draft",
          new WorkflowBehavior.ActionRequest(flow.getRevision(), parameters), fixture.scope));
      assertTrue(failure.getMessage().contains("HTTP 400"), failure.getMessage());
      assertEquals(flow.getRevision(), fixture.store.getFlow(flow.getId()).getRevision());
      assertTrue(fixture.store.blobs.isEmpty());
      var corrected = client.executeFlowAction(flow.getId(), stage, "prepare-review-draft",
          new WorkflowBehavior.ActionRequest(flow.getRevision(), parameters()), fixture.scope);
      assertEquals(flow.getRevision() + 1, corrected.getRevision());
      assertEquals(5, corrected.getStates().get(stage).getAttachments().size());
      assertEquals(5, fixture.store.blobs.size());
      assertEquals(List.of(400, 200), bridge.preparations().stream().map(call -> call.status()).toList());
    }
  }

  static Map<String, Object> parameters() {
    return new LinkedHashMap<>(Map.of("proposal_id", "test-proposal", "revision_id", "test-r1",
        "domain", "test domain", "purpose", "Review ontology", "requested_tier", 1,
        "target_community", "test community"));
  }
  static String path(Flow flow, String stage) {
    return ServicesAPI.RESOURCES.FLOW_ACTION.replace("{flowId}", flow.getId())
        .replace("{stateId}", stage).replace("{actionId}", "prepare-review-draft");
  }
}
