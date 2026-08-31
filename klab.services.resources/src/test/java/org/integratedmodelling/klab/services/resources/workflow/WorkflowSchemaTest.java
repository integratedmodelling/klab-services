package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.LinkedHashSet;
import java.util.Set;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.junit.jupiter.api.Test;

class WorkflowSchemaTest {

  private Workflow schema() throws Exception {
    var mapper = new ObjectMapper(new YAMLFactory());
    JacksonConfiguration.configureObjectMapperForKlabTypes(mapper);
    try (var stream =
        getClass().getClassLoader().getResourceAsStream("workflows/asset-review.yaml")) {
      return mapper.readValue(stream, Workflow.class);
    }
  }

  @Test
  void bundledSchemaIsValidAndClientExecutable() throws Exception {
    var workflow = schema();
    assertTrue(workflow.validate().isEmpty());

    var editor = participant(WorkflowRole.EDITOR, false);
    var state = new Flow.State();
    state.setId("draft-1");
    state.setSchemaId("editing");
    var flow = new Flow();
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

    var flow = new Flow();
    flow.setId("flow-1");
    flow.setAssetUrn("project/namespace");
    flow.setAssetType(KlabAsset.KnowledgeClass.NAMESPACE);
    flow.setPermissionsOwnerUrn("project");
    flow.setPublicRead(true);
    flow.getMetadata().put("purpose", "transport-test");
    var state = new Flow.State();
    state.setFlowId(flow.getId());
    state.setId("state-2");
    state.setOwner("editor@example.org");
    flow.getStates().put(state.getId(), state);
    var transaction = new Flow.Transaction();
    transaction.setFlowId(flow.getId());
    transaction.setId("transaction-3");
    flow.getHistory().add(transaction);
    var attachment = new Flow.Attachment();
    attachment.setFlowId(flow.getId());
    attachment.setId("attachment-4");
    state.getAttachments().add(attachment);

    assertEquals("urn:klab:flow:flow-1", flow.getUrn());
    assertEquals("urn:klab:flow-transition:flow-1:transaction-3", transaction.getUrn());
    assertEquals("urn:klab:flow-attachment:flow-1:attachment-4", attachment.getUrn());

    var serialized = org.integratedmodelling.common.utils.Utils.Json.asString(flow);
    var reconstructed =
        org.integratedmodelling.common.utils.Utils.Json.parseObject(serialized, Flow.class);
    assertEquals(flow.getUrn(), reconstructed.getUrn());
    assertEquals("transport-test", reconstructed.getMetadata().get("purpose"), serialized);
    assertEquals("project/namespace", reconstructed.getAssetUrn());
    assertEquals("project", reconstructed.getPermissionsOwnerUrn());
    assertTrue(reconstructed.isPublicRead());
    assertEquals("editor@example.org", reconstructed.getStates().get("state-2").getOwner());
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
