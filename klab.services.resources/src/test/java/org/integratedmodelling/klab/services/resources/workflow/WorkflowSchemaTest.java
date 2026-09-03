package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.integratedmodelling.common.services.resources.workflow.FlowImpl;
import org.integratedmodelling.common.services.resources.workflow.WorkflowImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;
import org.junit.jupiter.api.Test;

class WorkflowSchemaTest {

  private Workflow schema() throws Exception {
    try (var stream =
        getClass().getClassLoader().getResourceAsStream("workflows/asset-review.yaml")) {
      return org.integratedmodelling.common.utils.Utils.YAML.load(stream, Workflow.class);
    }
  }

  @Test
  void bundledSchemaIsValidAndClientExecutable() throws Exception {
    var workflow = schema();
    assertTrue(workflow.validate().isEmpty());

    var serialized = org.integratedmodelling.common.utils.Utils.Json.asString(workflow);
    var clientMapper = new ObjectMapper();
    var json = clientMapper.readTree(serialized);
    assertEquals(WorkflowImpl.class.getName(), json.path("@CLASS").asText(), serialized);
    assertEquals(
        WorkflowImpl.StateSchemaImpl.class.getName(),
        json.path("states").path("editing").path("@CLASS").asText(),
        serialized);
    assertEquals(
        WorkflowImpl.TransitionSchemaImpl.class.getName(),
        json.path("transitions").path("submit").path("@CLASS").asText(),
        serialized);
    assertEquals(
        workflow.getUrn(),
        org.integratedmodelling.common.utils.Utils.Json.parseObject(serialized, Workflow.class)
            .getUrn());

    var editor = participant(WorkflowRole.EDITOR, false);
    var state = new FlowImpl.StateImpl();
    state.setId("draft-1");
    state.setSchemaId("editing");
    var flow = new FlowImpl();
    flow.getStates().put(state.getId(), state);
    flow.getCurrentStateIds().add(state.getId());

    assertEquals(
        Set.of("submit"),
        workflow.admittedTransitions(state, editor).stream()
            .map(Workflow.TransitionSchema::getId)
            .collect(java.util.stream.Collectors.toSet()));
    assertTrue(workflow.validateTransition(flow, state.getId(), "submit", editor).isEmpty());
    assertFalse(
        workflow.validateTransition(flow, state.getId(), "reject-peer-review", editor).isEmpty());
  }

  @Test
  void publicReviewRequiresKnownRealPersonFlag() throws Exception {
    var workflow = schema();
    workflow.validate();
    var publicState = workflow.getStates().get("community-review");
    assertTrue(workflow.canAccess(publicState, participant(WorkflowRole.REVIEWER, true)));
    assertFalse(workflow.canAccess(publicState, participant(WorkflowRole.REVIEWER, false)));
  }

  @Test
  void workflowArtifactsHaveStableCrudClassesAndUrns() throws Exception {
    var workflow = schema();
    assertTrue(workflow.validate().isEmpty());
    assertEquals("urn:klab:workflow:asset-review@1.0", workflow.getUrn());
    assertEquals(
        "urn:klab:workflow-state:asset-review@1.0:editing",
        workflow.getStates().get("editing").getUrn());
    assertEquals(
        "urn:klab:workflow-transition:asset-review@1.0:submit",
        workflow.getTransitions().get("submit").getUrn());
    assertEquals(KlabAsset.KnowledgeClass.WORKFLOW, KlabAsset.classify(workflow));
    assertEquals(
        KlabAsset.KnowledgeClass.FLOW_ATTACHMENT,
        KlabAsset.KnowledgeClass.classify(Flow.Attachment.class));

    var coordinates =
        WorkflowUrns.parse(
            "urn:klab:flow-state:flow-1:state-2", KlabAsset.KnowledgeClass.FLOW_STATE);
    assertEquals("flow-1", coordinates.ownerId());
    assertEquals("state-2", coordinates.artifactId());

    var flow = new FlowImpl();
    flow.setId("flow-1");
    flow.setAssetUrn("project/namespace");
    flow.setAssetType(KlabAsset.KnowledgeClass.NAMESPACE);
    flow.setPermissionsOwnerUrn("project");
    flow.setPublicRead(true);
    flow.getMetadata().put("purpose", "transport-test");
    var state = new FlowImpl.StateImpl();
    state.setFlowId(flow.getId());
    state.setId("state-2");
    state.setOwner("editor@example.org");
    flow.getStates().put(state.getId(), state);
    var transaction = new FlowImpl.TransactionImpl();
    transaction.setFlowId(flow.getId());
    transaction.setId("transaction-3");
    flow.getHistory().add(transaction);
    var attachment = new FlowImpl.AttachmentImpl();
    attachment.setFlowId(flow.getId());
    attachment.setId("attachment-4");
    state.getAttachments().add(attachment);

    assertEquals("urn:klab:flow:flow-1", flow.getUrn());
    assertEquals("urn:klab:flow-transition:flow-1:transaction-3", transaction.getUrn());
    assertEquals("urn:klab:flow-attachment:flow-1:attachment-4", attachment.getUrn());

    var serialized = org.integratedmodelling.common.utils.Utils.Json.asString(flow);
    var clientMapper = new ObjectMapper();
    var json = clientMapper.readTree(serialized);
    assertEquals(FlowImpl.class.getName(), json.path("@CLASS").asText(), serialized);
    assertEquals(
        FlowImpl.StateImpl.class.getName(),
        json.path("states").path("state-2").path("@CLASS").asText(),
        serialized);
    assertEquals(
        FlowImpl.TransactionImpl.class.getName(),
        json.path("history").path(0).path("@CLASS").asText(),
        serialized);
    assertEquals(
        FlowImpl.AttachmentImpl.class.getName(),
        json.path("states")
            .path("state-2")
            .path("attachments")
            .path(0)
            .path("@CLASS")
            .asText(),
        serialized);
    var reconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(serialized, Flow.class);
    assertEquals(flow.getUrn(), reconstructed.getUrn());
    assertEquals("transport-test", reconstructed.getMetadata().get("purpose"), serialized);
    assertEquals("project/namespace", reconstructed.getAssetUrn());
    assertEquals("project", reconstructed.getPermissionsOwnerUrn());
    assertTrue(reconstructed.isPublicRead());
    assertEquals("editor@example.org", reconstructed.getStates().get("state-2").getOwner());

    var legacyJson = json.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacyJson)
        .put("@CLASS", Flow.class.getName());
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacyJson.path("states").path("state-2"))
        .put("@CLASS", Flow.State.class.getName());
    var legacyReconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(
            legacyJson.toString(), Flow.class);
    assertEquals(flow.getUrn(), legacyReconstructed.getUrn());
  }

  @Test
  void flowCommandsUseTheSameInterfaceReconstructionConvention() {
    var state = new FlowImpl.StateImpl();
    state.setId("state-2");
    var request = new FlowImpl.TransitionRequestImpl();
    request.setTransactionId("transaction-3");
    request.setTransitionId("submit");
    request.setTargetState(state);

    var serialized = org.integratedmodelling.common.utils.Utils.Json.asString(request);
    var reconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(
            serialized, Flow.TransitionRequest.class);
    assertEquals("submit", reconstructed.getTransitionId());
    assertEquals("state-2", reconstructed.getTargetState().getId());

    var upload = new FlowImpl.AttachmentUploadImpl();
    upload.setFileName("review.txt");
    upload.setMediaType("text/plain");
    upload.setContent("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var uploadReconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(
            org.integratedmodelling.common.utils.Utils.Json.asString(upload),
            Flow.AttachmentUpload.class);
    assertEquals("review.txt", uploadReconstructed.getFileName());
    assertEquals(
        "ok",
        new String(
            uploadReconstructed.getContent(), java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void resourceInfoRetainsMultipleIndependentFlowReferences() {
    var info = ResourceInfo.immediate();
    info.setUrn("project/namespace");
    info.setKnowledgeClass(KlabAsset.KnowledgeClass.NAMESPACE);
    info.setPermissionsOwnerUrn("project");
    for (int i = 1; i <= 2; i++) {
      var reference = new ResourceInfo.FlowReference();
      reference.setFlowUrn("urn:klab:flow:flow-" + i);
      reference.setWorkflowUrn("urn:klab:workflow:asset-review@1.0");
      reference.setStatus(Flow.Status.ACTIVE);
      reference.getCurrentStateUrns().add("urn:klab:flow-state:flow-" + i + ":editing");
      info.getFlows().put(reference.getFlowUrn(), reference);
    }

    var reconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(
            org.integratedmodelling.common.utils.Utils.Json.asString(info), ResourceInfo.class);
    assertEquals(2, reconstructed.getFlows().size());
    assertTrue(reconstructed.getFlows().containsKey("urn:klab:flow:flow-1"));
    assertTrue(reconstructed.getFlows().containsKey("urn:klab:flow:flow-2"));
    assertEquals("project", reconstructed.getPermissionsOwnerUrn());
  }

  private WorkflowParticipant participant(WorkflowRole role, boolean known) {
    var ret = new WorkflowParticipant();
    ret.setIdentity("test-user");
    ret.setRoles(new LinkedHashSet<>(Set.of(role)));
    ret.setPermittedWorkflows(new LinkedHashSet<>(Set.of("*")));
    ret.setKnownRealPerson(known);
    return ret;
  }
}
