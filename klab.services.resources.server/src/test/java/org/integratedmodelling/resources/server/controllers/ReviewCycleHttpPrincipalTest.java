package org.integratedmodelling.resources.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real EngineAuthorization objects at the HTTP controller boundary, not a live JWT/login test. */
class ReviewCycleHttpPrincipalTest {
  @Test void creationOptionsAreServerDerivedAndDoNotCreateAFlow() throws Exception {
    var service = mock(ResourcesProvider.class); var server = mock(ResourcesServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new WorkflowController(); ReflectionTestUtils.setField(controller, "resourcesServer", server);
    var mapper = JacksonConfiguration.newObjectMapper();
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    var workflow = mock(org.integratedmodelling.klab.api.services.resources.workflow.Workflow.class);
    when(workflow.getId()).thenReturn("ontology-review-pilot"); when(workflow.getVersion()).thenReturn("0.3");
    var transition = mock(org.integratedmodelling.klab.api.services.resources.workflow.Workflow.TransitionSchema.class);
    when(transition.getId()).thenReturn("initialize"); when(transition.getSourceStates()).thenReturn(Set.of("INIT"));
    when(transition.getTargetState()).thenReturn("bootstrap");
    when(transition.getRoles()).thenReturn(Set.of(org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole.EDITOR));
    when(workflow.getTransitions()).thenReturn(Map.of("initialize", transition));
    var schema = mock(org.integratedmodelling.klab.api.services.resources.workflow.Workflow.StateSchema.class);
    when(workflow.getStates()).thenReturn(Map.of("bootstrap", schema)); when(workflow.canAccess(eq(schema), any())).thenReturn(true);
    var path = "/api/v1/workflows/ontology-review-pilot/creation";
    mvc.perform(get(path)).andExpect(status().isForbidden());
    var editor = principal("editor", true); var scope = (UserScope) editor.getScope();
    var group = mock(org.integratedmodelling.klab.api.identities.Group.class); when(group.getId()).thenReturn("editors");
    var roles = new org.integratedmodelling.klab.api.authentication.CustomProperty(); roles.setKey("workflow.roles"); roles.setValue("EDITOR");
    var permitted = new org.integratedmodelling.klab.api.authentication.CustomProperty(); permitted.setKey("workflow.permitted"); permitted.setValue("ontology-review-pilot");
    when(group.getCustomProperties()).thenReturn(List.of(roles, permitted)); when(scope.getUser().getGroups()).thenReturn(List.of(group));
    when(service.getWorkflow("ontology-review-pilot", scope)).thenReturn(workflow);
    var result = mvc.perform(get(path).principal(editor)).andExpect(status().isOk()).andReturn();
    assertEquals("private, no-store", result.getResponse().getHeader("Cache-Control"));
    var json = mapper.readTree(result.getResponse().getContentAsString()); assertTrue(json.path("canCreate").asBoolean());
    assertEquals("bootstrap", json.path("initialStateIds").get(0).asText());
    when(scope.getUser().getGroups()).thenReturn(List.of());
    result = mvc.perform(get(path).principal(editor)).andExpect(status().isOk()).andReturn();
    assertFalse(mapper.readTree(result.getResponse().getContentAsString()).path("canCreate").asBoolean());
    verify(service, never()).createFlow(any(), any(), any());
  }

  @Test void archiveStatusAndRetryRequireAuthenticatedPrincipalAndPreserveItsScope() throws Exception {
    var service = mock(ResourcesProvider.class); var server = mock(ResourcesServer.class); when(server.klabService()).thenReturn(service);
    var controller = new WorkflowController(); ReflectionTestUtils.setField(controller, "resourcesServer", server);
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler()).build();
    String path = "/api/v1/flows/flow-1/review-cycles/cycle-1/archive";
    mvc.perform(get(path)).andExpect(status().isForbidden());
    mvc.perform(post(path).principal(principal("editor", false))).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    var editor = principal("editor", true); var scope = (UserScope) editor.getScope();
    when(service.reviewArchiveStatus("flow-1", "cycle-1", scope)).thenReturn(
        new org.integratedmodelling.klab.services.resources.workflow.ReviewArchiveWorker.Status("PENDING", false, null, null, null));
    var result = mvc.perform(get(path).principal(editor)).andExpect(status().isOk()).andReturn();
    assertEquals("private, no-store", result.getResponse().getHeader("Cache-Control"));
    when(service.archiveReviewCycle("flow-1", "cycle-1", scope)).thenReturn(
        new org.integratedmodelling.klab.services.resources.workflow.ReviewArchiveWorker.Status("BLOCKED", false, null, null, null));
    mvc.perform(post(path).principal(editor)).andExpect(status().isOk());
    verify(service).archiveReviewCycle("flow-1", "cycle-1", scope);
    var citizen = principal("citizen", true);
    when(service.archiveReviewCycle("flow-1", "cycle-1", (UserScope) citizen.getScope())).thenThrow(
        new org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException("Editor required"));
    mvc.perform(post(path).principal(citizen)).andExpect(status().isForbidden());
  }
  private EngineAuthorization principal(String name, boolean authenticated) {
    var identity = mock(UserIdentity.class); when(identity.getUsername()).thenReturn(name);
    when(identity.isAuthenticated()).thenReturn(authenticated);
    var scope = mock(UserScope.class); when(scope.getUser()).thenReturn(identity);
    var principal = new EngineAuthorization("fixture", name, "fixture-token", Map.of(), List.of(), List.of(Role.ROLE_USER));
    principal.setScope(scope); principal.setAuthenticated(authenticated); return principal;
  }

  @Test void unauthenticatedPrincipalsAreDeniedAndDistinctIdentitiesStayScopedAndUncached() throws Exception {
    var service = mock(ResourcesProvider.class); var server = mock(ResourcesServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new WorkflowController(); ReflectionTestUtils.setField(controller, "resourcesServer", server);
    var mapper = JacksonConfiguration.newObjectMapper();
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler())
        .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8),
            new MappingJackson2HttpMessageConverter(mapper)).build();
    var path = "/api/v1/flows/flow-1/review-cycles";
    mvc.perform(get(path)).andExpect(status().isForbidden());
    mvc.perform(get(path).principal(() -> "untrusted-name")).andExpect(status().isForbidden());
    mvc.perform(get(path).principal(principal("alice", false))).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    when(service.getReviewCycles(eq("flow-1"), any(UserScope.class))).thenAnswer(invocation -> {
      UserScope scope = invocation.getArgument(1);
      return new ReviewCycles.View(3, List.of(), false, scope.getUser().getUsername(), List.of());
    });
    for (String name : List.of("alice", "bob")) {
      var principal = principal(name, true);
      var result = mvc.perform(get(path).principal(principal)).andExpect(status().isOk()).andReturn();
      assertEquals(name, mapper.readTree(result.getResponse().getContentAsString()).path("identity").asText());
      assertEquals("private, no-store", result.getResponse().getHeader("Cache-Control"));
      verify(service).getReviewCycles("flow-1", (UserScope) principal.getScope());
    }
    var alice = principal("alice", true);
    when(service.getReviewArtifact("flow-1", "cycle-1", "proposal-1", (UserScope) alice.getScope())).thenReturn(new byte[]{1, 2, 3});
    var artifact = mvc.perform(get(path + "/cycle-1/attachments/proposal-1").principal(alice)).andExpect(status().isOk()).andReturn();
    assertEquals("private, no-store", artifact.getResponse().getHeader("Cache-Control"));
    assertArrayEquals(new byte[]{1, 2, 3}, Base64.getDecoder().decode(artifact.getResponse().getContentAsString()));
    when(service.getReviewCycles("flow-1", (UserScope) alice.getScope())).thenThrow(new org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException("private reason"));
    mvc.perform(get(path).principal(alice)).andExpect(status().isForbidden());
    when(service.addFlowAttachment(eq("flow-1"), eq("stage-1"), any(), same((UserScope) alice.getScope())))
        .thenAnswer(invocation -> {
          org.integratedmodelling.klab.api.services.resources.workflow.Flow.AttachmentUpload upload = invocation.getArgument(2);
          assertArrayEquals(new byte[]{1, 2, 3}, upload.getContent()); assertEquals("r2.json", upload.getFileName());
          var attachment = new org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl.AttachmentImpl();
          attachment.setId("uploaded"); attachment.setFlowId("flow-1"); attachment.setStateId("stage-1"); return attachment;
        });
    mvc.perform(post("/api/v1/flows/flow-1/states/stage-1/attachments").principal(alice)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content("{\"type\":\"bootstrap-proposal\",\"fileName\":\"r2.json\",\"mediaType\":\"application/vnd.klab.proposal+yaml\",\"content\":\"AQID\"}"))
        .andExpect(status().isOk());
  }
}
