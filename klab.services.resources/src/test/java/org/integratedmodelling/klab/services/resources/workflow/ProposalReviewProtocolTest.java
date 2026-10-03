package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;

class ProposalReviewProtocolTest {
  static final String WORKFLOW = "ontology-expert-review";
  // Explicit test-only validator: proves protocol gating, not scientific/parser validity of fixtures.
  static final ProposalCandidateValidator PASS = (candidate, proposal, ontology) -> List.of(
      new Check(CheckKind.IMPORT_CONTEXT, CheckStatus.PASS, List.of("test fixture context")),
      new Check(CheckKind.DOCUMENT_SCHEMA, CheckStatus.PASS, List.of("test fixture schema")),
      new Check(CheckKind.PARSER, CheckStatus.PASS, List.of("test fixture parser")),
      new Check(CheckKind.ADAPTATION, CheckStatus.PASS, List.of("test fixture adaptation")),
      new Check(CheckKind.REASONER, CheckStatus.PASS, List.of("test fixture reasoner")));
  WorkflowStore store() throws Exception {
    var constructor = Class.forName(WorkflowManagerAuthorizationTest.class.getName() + "$MemoryStore").getDeclaredConstructor();
    constructor.setAccessible(true);
    return (WorkflowStore) constructor.newInstance();
  }
  UserScope scope(String name, String role) throws Exception {
    var method = WorkflowManagerAuthorizationTest.class.getDeclaredMethod("scope", String.class, String.class, String.class);
    method.setAccessible(true);
    return (UserScope) method.invoke(new WorkflowManagerAuthorizationTest(), name, role, "*");
  }
  WorkflowManager manager(WorkflowStore store) { return new WorkflowManager(store, WorkflowStageLifecycleHandler.NO_OP, PASS); }
  Flow.AttachmentUpload upload(String type, String media, String content) {
    var u = Flow.AttachmentUpload.create(); u.setType(type); u.setFileName(type + ".yaml");
    u.setMediaType(media); u.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
    u.setContent(content.getBytes(StandardCharsets.UTF_8)); return u;
  }
  Flow.AttachmentUpload proposal(String revision, String previous) {
    return upload("bootstrap-proposal", "application/vnd.klab.proposal+yaml", """
        proposal_schema: classpath:/schemas/llm/domain-context-proposal.schema.json
        context_pack_version: "1.3"
        proposal:
          id: domain-proposal
          revision_id: %s
          supersedes_revision: %s
          existing_ontologies: []
          actions: [{action_id: action-1}]
        """.formatted(revision, previous == null ? "null" : previous));
  }
  Flow.InitializationRequest initialization(boolean ontology) {
    var initial = Flow.State.create(); initial.setId("initial"); initial.setSchemaId("editing");
    initial.setAssetUrn("test:ontology"); initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
    var r = Flow.InitializationRequest.create(); r.setInitialState(initial);
    var uploads = new ArrayList<Flow.AttachmentUpload>(); uploads.add(proposal("r1", null));
    uploads.add(upload("bootstrap-comments", "application/vnd.klab.comments+json", "{}"));
    if (ontology) uploads.add(upload("candidate-ontology", "application/vnd.klab.ontology", "test candidate"));
    r.setAttachments(uploads);
    var transition = Flow.TransitionRequest.create(); transition.setTransitionId("submit");
    transition.setProposalReview(new Command(1, null, "Initial review", null)); r.setTransition(transition); return r;
  }
  Flow.State current(Flow f) { return f.getStates().get(f.getCurrentStateIds().iterator().next()); }
  Flow.TransitionRequest command(Flow f, String operation, Candidate c) {
    var r = Flow.TransitionRequest.create(); r.setSourceStateId(current(f).getId());
    r.setTransitionId(operation); r.setExpectedRevision(f.getRevision());
    r.setProposalReview(new Command(1, c, "Human rationale", null)); return r;
  }
  @Test void submitRequestChangesResubmitRejectsOldDecisionAndAcceptsExactCandidate() throws Exception {
    var store = store(); var manager = manager(store); var editor = scope("editor", "EDITOR");
    var flow = manager.initializeFlow(WORKFLOW, initialization(true), editor);
    var old = current(flow).getProposalReview().candidate();
    flow = manager.transition(flow.getId(), command(flow, "request-changes", old), editor);
    assertEquals("editing", current(flow).getSchemaId());
    assertEquals(Status.CHANGES_REQUESTED, current(flow).getProposalReview().status());
    var p = manager.addAttachment(flow.getId(), current(flow).getId(), proposal("r2", "r1"), editor);
    manager.addAttachment(flow.getId(), current(flow).getId(), upload("bootstrap-comments", "application/vnd.klab.comments+json", "{}"), editor);
    var candidate = ProposalReviewProtocol.readCandidate(new Artifact(p.getId(), p.getChecksum()), old.ontology(), store::getWorkflowAttachment);
    flow = manager.getFlow(flow.getId(), editor);
    flow = manager.transition(flow.getId(), command(flow, "submit", candidate), editor);
    var stale = command(flow, "accept-peer-review", old); var id = flow.getId();
    assertThrows(KlabIllegalStateException.class, () -> manager.transition(id, stale, editor));
    assertEquals(flow.getRevision(), manager.getFlow(id, editor).getRevision());
    var result = manager.transition(id, command(flow, "accept-peer-review", candidate), editor);
    assertEquals(Flow.Status.CLOSED, result.getStatus());
    var terminal = result.getStates().values().stream().filter(s -> "accepted".equals(s.getSchemaId())).findFirst().orElseThrow();
    assertEquals(Status.ACCEPTED, terminal.getProposalReview().status());
    assertEquals(Set.of("final-proposal", "accepted-ontology"), new HashSet<>(terminal.getAttachments().stream().map(Flow.Attachment::getType).toList()));
    assertArrayEquals(proposal("r2", "r1").getContent(), manager.getAttachment(id, candidate.proposal().attachmentId(), editor));
    assertTrue(terminal.getProposalReview().validation().stream().anyMatch(c -> c.kind() == CheckKind.APPLICATION && c.status() == CheckStatus.BLOCKED));
    assertEquals(candidate, Utils.Json.parseObject(Utils.Json.asString(result), Flow.class).getStates().get(terminal.getId()).getProposalReview().candidate());
  }
  @Test void defaultValidatorNeverPretendsAcceptanceSucceeded() throws Exception {
    var s = store(); var m = new WorkflowManager(s); var user = scope("editor", "EDITOR");
    var flow = m.initializeFlow(WORKFLOW, initialization(true), user);
    var r = command(flow, "accept-peer-review", current(flow).getProposalReview().candidate());
    assertThrows(KlabIllegalStateException.class, () -> m.transition(flow.getId(), r, user));
    assertEquals(Flow.Status.ACTIVE, s.getFlow(flow.getId()).getStatus());
  }
  @Test void missingOntologyDoesNotCloseFlowEvenWithPassingValidator() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(false),user);
    assertThrows(KlabIllegalStateException.class, () -> m.transition(f.getId(),command(f,"accept-peer-review",current(f).getProposalReview().candidate()),user));
    assertEquals(f.getRevision(),s.getFlow(f.getId()).getRevision());
  }
  @Test void changedImportsAreRevalidatedAtDecisionTime() throws Exception {
    var s=store(); var user=scope("editor","EDITOR"); var changed=new java.util.concurrent.atomic.AtomicBoolean();
    var m=new WorkflowManager(s,WorkflowStageLifecycleHandler.NO_OP,(c,p,o)-> changed.get()
        ? List.of(new Check(CheckKind.IMPORT_CONTEXT,CheckStatus.FAIL,List.of("Import revision changed"))) : PASS.validate(c,p,o));
    var f=m.initializeFlow(WORKFLOW,initialization(true),user);changed.set(true);
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",current(f).getProposalReview().candidate()),user));
  }
  @Test void mutatedStoredPayloadIsRejectedBeforeDecision() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);
    var c=current(f).getProposalReview().candidate();s.putWorkflowAttachment(c.proposal().attachmentId(),f.getId(),"initial",new byte[]{1});
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",c),user));
  }
  @Test void forgedActionsAndSkippedRevisionCheckAreRejected() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);
    var c=current(f).getProposalReview().candidate();var forged=new Candidate(c.proposalId(),c.revisionId(),c.supersedesRevision(),c.proposal(),c.ontology(),List.of("other-action"),c.contextDigest());
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",forged),user));
    var r=command(f,"accept-peer-review",c);r.setExpectedRevision(-1);
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),r,user));
  }
  @Test void staleFlowRevisionFailsEvenWithExactCandidate() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);
    var r=command(f,"accept-peer-review",current(f).getProposalReview().candidate());r.setExpectedRevision(f.getRevision()-1);
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),r,user));
  }
  @Test void evidenceAndLifecycleCannotBeDeletedOrForgedThroughCrud() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);
    assertThrows(KlabIllegalStateException.class,()->m.deleteAttachment(f.getId(),current(f).getProposalReview().candidate().proposal().attachmentId(),user));
    assertThrows(KlabIllegalStateException.class,()->m.deleteFlow(f.getId(),user));
    var update=current(f); update.setStatus(Flow.StateStatus.CLOSED);
    assertThrows(KlabIllegalStateException.class,()->m.updateState(f.getId(),update.getId(),update,user));
  }
  @Test void publicReadDoesNotGrantDecisionAuthority() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var init=initialization(true);init.setPublicRead(true);
    var f=m.initializeFlow(WORKFLOW,init,user);var reader=scope("reader","REVIEWER");
    assertEquals(current(f).getProposalReview(),current(m.getFlow(f.getId(),reader)).getProposalReview());
    assertThrows(KlabResourceAccessException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",current(f).getProposalReview().candidate()),reader));
  }
  @Test void failedAggregateWriteDoesNotPublishTerminalOrDropEvidence() throws Exception {
    var original=store();var fail=new java.util.concurrent.atomic.AtomicBoolean();
    var s=(WorkflowStore)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{WorkflowStore.class},(p,m,a)->{
      if(m.getName().equals("putFlow")&&fail.get())return false;return m.invoke(original,a);
    });
    var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);fail.set(true);
    var c=current(f).getProposalReview().candidate();
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",c),user));
    assertEquals(f.getRevision(),original.getFlow(f.getId()).getRevision());
    assertEquals(Flow.Status.ACTIVE,original.getFlow(f.getId()).getStatus());
    assertNotNull(original.getWorkflowAttachment(c.proposal().attachmentId()));
  }
  @Test void missingCommandAndInvalidIdentityInitializationPersistNothing() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var init=initialization(false);
    init.getTransition().setProposalReview(null);
    assertThrows(KlabIllegalStateException.class,()->m.initializeFlow(WORKFLOW,init,user));assertTrue(s.listFlows().isEmpty());
    var invalid=initialization(false);invalid.getAttachments().getFirst().setContent("placeholder: true".getBytes(StandardCharsets.UTF_8));
    assertThrows(KlabIllegalStateException.class,()->m.initializeFlow(WORKFLOW,invalid,user));assertTrue(s.listFlows().isEmpty());
  }
  @Test void duplicateYamlKeysAreNotAcceptedAsCandidateIdentity() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var init=initialization(false);
    var p=init.getAttachments().getFirst();p.setContent((new String(p.getContent(),StandardCharsets.UTF_8)+"  revision_id: forged\n").getBytes(StandardCharsets.UTF_8));
    assertThrows(KlabIllegalStateException.class,()->m.initializeFlow(WORKFLOW,init,user));assertTrue(s.listFlows().isEmpty());
  }
  @Test void dossierReferencesAreValidatedButCoverageTargetsAreNotTruthGates() {
    var empty=new BootstrapDossier(List.of(),List.of(),List.of(),List.of(),List.of("Evidence missing"),List.of("No evidence yet for requested counts"));
    assertTrue(BootstrapDossierValidator.errors(empty).isEmpty());assertEquals(0,BootstrapDossierValidator.coverage(empty).get("QUESTIONS"));
    var invalid=new BootstrapDossier(List.of(),List.of(),List.of(new Question("q1","Question?","Precise intent",List.of(),List.of("missing"),List.of(),List.of(),List.of())),List.of(),List.of(),List.of());
    assertFalse(BootstrapDossierValidator.errors(invalid).isEmpty());
  }
  @Test void privateReviewerCanDownloadBoundArtifactThroughVisibleReviewStage() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var reviewer=scope("reviewer","REVIEWER");
    var f=m.initializeFlow(WORKFLOW,initialization(true),editor);
    assertArrayEquals(proposal("r1",null).getContent(),m.getAttachment(f.getId(),current(f).getProposalReview().candidate().proposal().attachmentId(),reviewer));
    assertThrows(KlabIllegalStateException.class,()->m.addAttachment(f.getId(),current(f).getId(),proposal("r2","r1"),editor));
  }
  @Test void clientCannotForgeTerminalDescriptors() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),editor);
    var r=command(f,"accept-peer-review",current(f).getProposalReview().candidate());var target=Flow.State.create();
    var fake=new org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl.AttachmentImpl();fake.setType("accepted-ontology");
    target.getAttachments().add(fake);r.setTargetState(target);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,()->m.transition(f.getId(),r,editor));
    assertEquals(Flow.Status.ACTIVE,s.getFlow(f.getId()).getStatus());
  }
  @Test void incorrectSupersedesRevisionCannotBeResubmitted() throws Exception {
    var s=store();var m=manager(s);var user=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),user);
    f=m.transition(f.getId(),command(f,"request-changes",current(f).getProposalReview().candidate()),user);
    var p=m.addAttachment(f.getId(),current(f).getId(),proposal("r2","wrong-base"),user);
    m.addAttachment(f.getId(),current(f).getId(),upload("bootstrap-comments","application/vnd.klab.comments+json","{}"),user);
    f=m.getFlow(f.getId(),user);var id=f.getId();
    var candidate=ProposalReviewProtocol.readCandidate(new Artifact(p.getId(),p.getChecksum()),null,s::getWorkflowAttachment);
    var r=command(f,"submit",candidate);
    assertThrows(KlabIllegalStateException.class,()->m.transition(id,r,user));
  }

  @Test void reviewEvidenceIsCarriedToPrivateReviewersAndThroughAdvancement() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var reviewer=scope("reviewer","REVIEWER");
    var init=initialization(true);var uploads=new ArrayList<>(init.getAttachments());
    uploads.add(upload("supporting-material","text/plain","source evidence"));init.setAttachments(uploads);
    var f=m.initializeFlow(WORKFLOW,init,editor);
    for (String type : List.of("bootstrap-comments","supporting-material")) {
      var a=current(f).getAttachments().stream().filter(x->type.equals(x.getType())).findFirst().orElseThrow();
      assertNotNull(m.getAttachment(f.getId(),a.getId(),reviewer));
    }
    f=m.transition(f.getId(),command(f,"open-input",current(f).getProposalReview().candidate()),scope("admin","ADMIN"));
    assertTrue(current(f).getAttachments().stream().anyMatch(a->"supporting-material".equals(a.getType())));
    assertTrue(current(f).getAttachments().stream().anyMatch(a->"bootstrap-comments".equals(a.getType())));
  }
  @Test void assignedReviewerMayRequestChangesButDoesNotAcquireEditorCrudRights() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var reviewer=scope("reviewer","REVIEWER");
    var init=initialization(true);var target=Flow.State.create();target.setAssignees(Set.of("reviewer"));init.getTransition().setTargetState(target);
    var created=m.initializeFlow(WORKFLOW,init,editor);
    var f=m.getFlow(created.getId(),reviewer);
    assertThrows(KlabResourceAccessException.class,()->m.updateState(f.getId(),current(f).getId(),current(f),reviewer));
    assertThrows(KlabResourceAccessException.class,()->m.addAttachment(f.getId(),current(f).getId(),upload("supporting-material","text/plain","x"),reviewer));
    assertThrows(KlabResourceAccessException.class,()->m.transition(f.getId(),command(f,"accept-peer-review",current(f).getProposalReview().candidate()),reviewer));
    var result=m.transition(f.getId(),command(f,"request-changes",current(f).getProposalReview().candidate()),reviewer);
    var returned=m.getFlow(result.getId(),editor);
    assertEquals("editing",current(returned).getSchemaId());assertEquals("editor",current(returned).getOwner());
  }
  @Test void failedAttachmentRemovalKeepsPersistedDescriptorAndBlob() throws Exception {
    var original=store();var fail=new java.util.concurrent.atomic.AtomicBoolean();
    var s=(WorkflowStore)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{WorkflowStore.class},(p,m,a)->{
      if(m.getName().equals("putFlow")&&fail.get())return false;return m.invoke(original,a);
    });
    var m=manager(s);var editor=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),editor);
    var a=m.addAttachment(f.getId(),current(f).getId(),upload("supporting-material","text/plain","retained evidence"),editor);fail.set(true);
    assertThrows(KlabIllegalStateException.class,()->m.deleteAttachment(f.getId(),a.getId(),editor));
    assertNotNull(original.getWorkflowAttachment(a.getId()));
    assertTrue(original.getFlow(f.getId()).getStates().values().stream().flatMap(st->st.getAttachments().stream()).anyMatch(x->a.getId().equals(x.getId())));
  }
  @Test void removingOneSharedDescriptorDoesNotGarbageCollectItsPayload() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),editor);
    var a=m.addAttachment(f.getId(),current(f).getId(),upload("supporting-material","text/plain","shared evidence"),editor);
    var duplicate=Flow.State.create();duplicate.setId("another-reference");duplicate.setSchemaId("peer-review");duplicate.getAttachments().add(a);
    s.getFlow(f.getId()).getStates().put(duplicate.getId(),duplicate);
    assertTrue(m.deleteAttachment(f.getId(),a.getId(),editor));assertNotNull(s.getWorkflowAttachment(a.getId()));
    assertTrue(s.getFlow(f.getId()).getStates().get(duplicate.getId()).getAttachments().stream().anyMatch(x->a.getId().equals(x.getId())));
  }
  @Test void extraYamlDocumentsAndJsonRootsAreRejected() {
    var text=new String(proposal("r1",null).getContent(),StandardCharsets.UTF_8);
    for (String trailing : List.of("---\nproposal: {revision_id: other}\n","---\n","...\n---\n{}")) {
      var bytes=(text+trailing).getBytes(StandardCharsets.UTF_8);
      assertThrows(KlabIllegalStateException.class,()->org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(new Artifact("a","hash"),null,bytes));
    }
    var bytes="""
        {"proposal_schema":"classpath:/schemas/llm/domain-context-proposal.schema.json",
         "context_pack_version":"1.3","proposal":{"id":"p","revision_id":"r",
         "actions":[],"existing_ontologies":[]}} {}
        """.getBytes(StandardCharsets.UTF_8);
    assertThrows(KlabIllegalStateException.class,()->org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(new Artifact("a","hash"),null,bytes));
  }
  @Test void unsupportedVersionsFailAndUnknownFieldsNeverGrantAuthority() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var init=initialization(true);
    init.getTransition().setProposalReview(new Command(2,null,"Unsupported",null));
    assertThrows(KlabIllegalStateException.class,()->m.initializeFlow(WORKFLOW,init,editor));assertTrue(s.listFlows().isEmpty());
    // The shared transport intentionally ignores unknown fields for forward-compatible reads.
    var decoded=Utils.Json.parseObject("{\"version\":1,\"candidate\":null,\"rationale\":\"review\",\"dossier\":null,\"approved\":true}",Command.class);
    assertEquals(new Command(1,null,"review",null),decoded);
    var ordinary=new WorkflowManager(s);var f=ordinary.initializeFlow(WORKFLOW,initialization(true),editor);
    var r=command(f,"accept-peer-review",current(f).getProposalReview().candidate());r.getMetadata().put("approved",true);
    assertThrows(KlabIllegalStateException.class,()->ordinary.transition(f.getId(),r,editor));
  }
  @Test void proposalUploadActionDossierAndRationaleLimitsAreEnforced() throws Exception {
    var bytes=new byte[ProposalReview.MAX_PROPOSAL_BYTES+1];
    assertThrows(KlabIllegalStateException.class,()->org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(new Artifact("x","x"),null,bytes));
    var text=new String(proposal("r1",null).getContent(),StandardCharsets.UTF_8);
    var actions=new StringBuilder("actions: [");
    for(int i=0;i<=ProposalReview.MAX_ACTIONS;i++)actions.append("{action_id: a").append(i).append("},");
    actions.append("]");var excessive=text.replace("actions: [{action_id: action-1}]",actions.toString()).getBytes(StandardCharsets.UTF_8);
    assertThrows(KlabIllegalStateException.class,()->org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(new Artifact("x","x"),null,excessive));
    var dossier=new BootstrapDossier(List.of(),List.of(),List.of(),List.of(),Collections.nCopies(ProposalReview.MAX_DOSSIER_RECORDS+1,"gap"),List.of());
    assertFalse(BootstrapDossierValidator.errors(dossier).isEmpty());
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var f=m.initializeFlow(WORKFLOW,initialization(true),editor);
    var r=command(f,"request-changes",current(f).getProposalReview().candidate());r.setProposalReview(new Command(1,current(f).getProposalReview().candidate(),"x".repeat(ProposalReview.MAX_RATIONALE_CHARS+1),null));
    assertThrows(KlabIllegalStateException.class,()->m.transition(f.getId(),r,editor));
    var upload=upload("supporting-material","text/plain","x");upload.setContent(new byte[ProposalReview.MAX_UPLOAD_BYTES+1]);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,()->m.addAttachment(f.getId(),current(f).getId(),upload,editor));
  }

  @Test void stageAttachmentCountAndAggregateBytesAreBoundedBeforePersistence() throws Exception {
    var s=store();var m=manager(s);var editor=scope("editor","EDITOR");var init=initialization(false);
    init.setAttachments(Collections.nCopies(ProposalReview.MAX_STAGE_ATTACHMENTS+1,upload("supporting-material","text/plain","x")));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,()->m.initializeFlow(WORKFLOW,init,editor));
    assertTrue(s.listFlows().isEmpty());
    var large=upload("supporting-material","application/octet-stream","x");large.setContent(new byte[ProposalReview.MAX_UPLOAD_BYTES]);
    var total=initialization(false);total.setAttachments(List.of(large,large,upload("supporting-material","text/plain","x")));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,()->m.initializeFlow(WORKFLOW,total,editor));
    assertTrue(s.listFlows().isEmpty());
  }

}
