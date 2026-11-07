package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.junit.jupiter.api.Test;

class ReviewPreparationEditingTest {
  private Flow.InitializationRequest initialization(Flow.AttachmentUpload... uploads) {
    var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setAssetUrn("test.ontology");
    initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY); initial.setTitle("Wetland terminology");
    initial.setDescription("Clarify the proposed terminology and collect references.");
    var request = Flow.InitializationRequest.create(); request.setInitialState(initial); request.setAttachments(List.of(uploads));
    return request;
  }

  private Flow.AttachmentUpload upload(String type, String media, String content) {
    var upload = Flow.AttachmentUpload.create(); upload.setType(type); upload.setMediaType(media);
    upload.setFileName(type + ".json"); upload.setContent(content.getBytes(StandardCharsets.UTF_8)); return upload;
  }

  @Test void atomicBootstrapPreservesReadyProposalAndReferencesWithoutRunningPreparation() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null, null, false);
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    ontology.setUrn("test.ontology"); ontology.setProjectName("test-project");
    ontology.setSourceCode("ontology test.ontology;\nthing Sample;\n");
    var authored = OntologyReviewBootstrap.prepare(ontology, null, new OntologyReviewBootstrap.Options(
        "authored", "authored-r1", "wetlands", "Clarify wetland terms", 1, "ecologists", java.time.LocalDate.of(2026, 11, 7)));
    var proposal = upload("bootstrap-proposal", "application/vnd.klab.proposal+yaml", authored.proposalJson());
    var source = upload("candidate-ontology", "application/vnd.klab.ontology", ontology.getSourceCode());
    var reference = upload("supporting-material", "text/markdown", "# Author reference");
    var request = initialization(proposal, source, reference); request.setPublicRead(true);
    request.getInitialState().setOwner("forged"); request.getInitialState().setMetadata(Map.of("phase", "terminated"));
    var flow = f.manager.initializeFlow("ontology-review-pilot", request, f.editor);
    var stage = flow.getStates().values().iterator().next();
    assertEquals("0.4", flow.getWorkflowVersion()); assertEquals(1, flow.getRevision());
    assertEquals("Wetland terminology", stage.getTitle()); assertEquals("editor", stage.getOwner());
    assertEquals(Flow.StateStatus.OPEN, stage.getStatus()); assertEquals(3, stage.getAttachments().size());
    assertTrue(stage.getMetadata().isEmpty()); assertEquals(0, f.store.catalogWrites);
    var original = stage.getAttachments().stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    assertArrayEquals(proposal.getContent(), f.manager.getAttachment(flow.getId(), original.getId(), f.editor));
    assertThrows(IllegalStateException.class, () -> f.manager.executeAction(flow.getId(), stage.getId(),
        "prepare-review-draft", f.request(flow.getRevision()), f.editor));
    assertEquals(3, f.store.attachments.size());
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    assertThrows(KlabResourceAccessException.class, () -> f.manager.getFlow(flow.getId(), admin));
    assertEquals(ReviewCycles.Phase.BOOTSTRAP, f.manager.getReviewCycles(flow.getId(), f.editor).phase());
    var sourceId = stage.getAttachments().stream().filter(a -> "candidate-ontology".equals(a.getType())).findFirst().orElseThrow();
    assertEquals(KlabAsset.KnowledgeClass.ONTOLOGY, sourceId.getAssetType());
    var opened = f.manager.reviewCycle(flow.getId(), new ReviewCycles.Command(flow.getRevision(), ReviewCycles.Operation.OPEN_CYCLE,
        stage.getId(), null, null, null, null, null, false, original.getId(), sourceId.getId()), f.editor);
    assertEquals(ReviewCycles.Phase.REVIEW, opened.phase());
    assertEquals("authored-r1", opened.cycles().getFirst().candidate().revisionId());
  }

  @Test void failedAtomicInitializationLeavesNoFlowOrBlobsAndOrdinaryWorkflowStillRequiresTransition() throws Exception {
    for (boolean aggregate : List.of(false, true)) {
      var f = new OntologyReviewBootstrapActionTest().new Fixture(null, null, false);
      f.store.failFlowWrites = aggregate; f.store.failAttachmentWriteAt = aggregate ? -1 : 2;
      var request = initialization(upload("supporting-material", "text/plain", "one"), upload("supporting-material", "text/plain", "two"));
      assertThrows(KlabIllegalStateException.class, () -> f.manager.initializeFlow("ontology-review-pilot", request, f.editor));
      assertTrue(f.store.attachments.isEmpty()); assertTrue(f.store.listFlows().isEmpty());
    }
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null, null, false);
    assertThrows(KlabIllegalArgumentException.class, () -> f.manager.initializeFlow("asset-review", initialization(), f.editor));
    var bad = initialization(upload("not-a-rule", "text/plain", "invalid"));
    assertThrows(KlabIllegalArgumentException.class, () -> f.manager.initializeFlow("ontology-review-pilot", bad, f.editor));
    assertTrue(f.store.listFlows().isEmpty()); assertTrue(f.store.attachments.isEmpty());
  }

  @Test void revisionCheckedDetailsPreserveMetadataAndRejectOtherEditorsStaleUploadsAndFrozenStages() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null); var prepared = f.prepare();
    var before = f.store.getFlow(prepared.getId()).getStates().get(f.stage);
    var update = new Flow.StateDetailsUpdate(prepared.getRevision(), "Named bootstrap", "An editorial description");
    var other = new ProposalReviewProtocolTest().scope("other", "EDITOR");
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    for (var denied : List.of(other, admin)) assertThrows(KlabResourceAccessException.class,
        () -> f.manager.updateStateDetails(prepared.getId(), f.stage, update, denied));
    var updated = f.manager.updateStateDetails(prepared.getId(), f.stage, update, f.editor);
    var state = updated.getStates().get(f.stage);
    assertEquals("Named bootstrap", state.getTitle()); assertEquals(before.getMetadata(), state.getMetadata());
    assertEquals(before.getAttachments().stream().map(Flow.Attachment::getId).toList(), state.getAttachments().stream().map(Flow.Attachment::getId).toList()); assertEquals("editor", state.getOwner());
    assertThrows(KlabIllegalStateException.class, () -> f.manager.updateStateDetails(prepared.getId(), f.stage, update, f.editor));
    var upload = upload("supporting-material", "text/plain", "stale reference"); upload.setExpectedRevision(prepared.getRevision());
    int payloads = f.store.attachments.size();
    assertThrows(KlabIllegalStateException.class, () -> f.manager.addAttachment(prepared.getId(), f.stage, upload, f.editor));
    assertEquals(payloads, f.store.attachments.size());
    var proposal = state.getAttachments().stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var view = f.manager.reviewCycle(updated.getId(), new ReviewCycles.Command(updated.getRevision(), ReviewCycles.Operation.OPEN_CYCLE,
        f.stage, null, null, null, null, null, false, proposal.getId(), null), f.editor);
    assertThrows(KlabIllegalStateException.class, () -> f.manager.updateStateDetails(updated.getId(), f.stage,
        new Flow.StateDetailsUpdate(view.revision(), "Frozen", "must not change"), f.editor));
  }

  @Test void discoveryIsLatestOnlyButPinnedHistoryRemainsReadableAndRetiredCreationIsRejected() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null, null, false);
    var latest = f.manager.getWorkflow("ontology-review-pilot");
    var older = Utils.Json.parseObject(Utils.Json.asString(latest), Workflow.class); older.setVersion("0.3"); f.store.putWorkflow(older);
    var newest = Utils.Json.parseObject(Utils.Json.asString(latest), Workflow.class); newest.setVersion("0.10"); f.store.putWorkflow(newest);
    var prerelease = Utils.Json.parseObject(Utils.Json.asString(latest), Workflow.class); prerelease.setVersion("0.10-beta"); f.store.putWorkflow(prerelease);
    assertTrue(WorkflowVersions.compare("0.10", "0.9") > 0);
    assertTrue(WorkflowVersions.compare("0.10", "0.10-beta") > 0);
    assertTrue(WorkflowVersions.compare("0.10-beta", "0.10") < 0);
    var found = f.manager.list(KlabAsset.KnowledgeClass.WORKFLOW, f.editor).stream().map(w -> (Workflow) w)
        .filter(w -> w.getId().equals(latest.getId())).toList();
    assertEquals(1, found.size()); assertEquals("0.10", found.getFirst().getVersion());
    assertEquals("0.3", f.manager.getWorkflow(older.getUrn(), f.editor).getVersion());
    assertThrows(KlabIllegalStateException.class, () -> f.manager.initializeFlow(older.getUrn(), initialization(), f.editor));
    var flow = f.manager.initializeFlow(newest.getUrn(), initialization(), f.editor);
    assertEquals("0.10", flow.getWorkflowVersion()); assertEquals(1, f.store.listFlows().size());
  }
}
