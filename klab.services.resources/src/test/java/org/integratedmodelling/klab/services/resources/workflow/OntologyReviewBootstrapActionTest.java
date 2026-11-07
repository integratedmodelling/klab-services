package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.languages.KActorsStandaloneSetup;
import org.integratedmodelling.languages.kActors.Behavior;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.BehaviorSyntaxImpl;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.junit.jupiter.api.Test;

class OntologyReviewBootstrapActionTest {
  private static final String WORKFLOW = "ontology-review-pilot";

  private KActorsBehavior behavior() throws Exception {
    String source;
    try (var input = getClass().getResourceAsStream("/workflows/ontology-review.kactor")) {
      assertNotNull(input); source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    var parser = new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new java.io.StringReader(source));
    var syntaxErrors = new ArrayList<String>();
    parsed.getSyntaxErrors().forEach(error -> syntaxErrors.add(error.getStartLine() + ": " + error.getSyntaxErrorMessage().getMessage() + " at " + error.getText()));
    assertFalse(parsed.hasSyntaxErrors(), syntaxErrors.toString());
    var syntax = new BehaviorSyntaxImpl((Behavior) parsed.getRootASTElement(), null) {
      protected void logWarning(ParsedObject p, EObject o, EStructuralFeature f, String m) {}
      protected void logError(ParsedObject p, EObject o, EStructuralFeature f, String m) { fail(m); }
    };
    return LanguageAdapter.INSTANCE.adaptBehavior(syntax, "test-project", new ArrayList<>(), 1);
  }

  final class Fixture {
    final WorkflowManagerAuthorizationTest.MemoryStore store = new WorkflowManagerAuthorizationTest.MemoryStore();
    final WorkflowManager manager;
    final KActorsBehavior behavior;
    final ResourcesService resources = mock(ResourcesService.class);
    final ResourceInfo info = mock(ResourceInfo.class);
    final UserScope editor;
    final Flow flow;
    final String stage;
    Fixture(String bootstrap) throws Exception { this(bootstrap, null); }
    Fixture(String bootstrap, org.integratedmodelling.klab.resources.WorkflowStore actualStore) throws Exception {
      this(bootstrap, actualStore, true);
    }
    Fixture(String bootstrap, org.integratedmodelling.klab.resources.WorkflowStore actualStore, boolean createInitialFlow) throws Exception {
      manager = new WorkflowManager(actualStore == null ? store : actualStore);
      var identity = new ProposalReviewProtocolTest().scope("editor", "EDITOR");
      editor = mock(UserScope.class);
      when(editor.getUser()).thenReturn(identity.getUser());
      when(editor.getServices(ResourcesService.class)).thenReturn(List.of(resources));
      when(editor.getService(ResourcesService.class)).thenReturn(resources);
      when(resources.serviceId()).thenReturn("resources");
      when(info.getKnowledgeClass()).thenReturn(KlabAsset.KnowledgeClass.PROJECT);
      when(info.getPermissions()).thenReturn(EnumSet.of(CRUDOperation.READ));
      when(resources.info("test-project", KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, editor)).thenReturn(info);
      var ontology = new KimOntologyImpl(); ontology.setUrn("test.ontology"); ontology.setProjectName("test-project");
      ontology.setSourceCode("ontology test.ontology;\nthing Sample;\n");
      when(resources.retrieve("test.ontology", KimOntology.class, editor)).thenReturn(ontology);
      if (bootstrap != null) when(resources.retrieve("test-project/BOOTSTRAP.md", ProjectMaterial.class, editor))
          .thenReturn(new ProjectMaterial("test-project", "BOOTSTRAP.md", bootstrap.getBytes(StandardCharsets.UTF_8)));
      behavior = behavior();
      manager.setBehaviorBridge(new WorkflowBehaviorBridge(() -> new AgentCompiler.Resolver() {}, owner -> null, () -> behavior));
      var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setAssetUrn("test.ontology");
      initial.setPermissionsOwnerUrn("test.ontology"); initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
      flow = createInitialFlow ? manager.createFlow(WORKFLOW, initial, editor) : null;
      stage = flow == null ? null : flow.getCurrentStateIds().iterator().next();
    }
    WorkflowBehavior.ActionRequest request(long revision) {
      return new WorkflowBehavior.ActionRequest(revision, Map.of("proposal_id", "test-proposal", "revision_id", "test-r1",
          "domain", "test domain", "purpose", "Review ontology", "requested_tier", 1, "target_community", "test community"));
    }
    Flow prepare() { return manager.executeAction(flow.getId(), stage, "prepare-review-draft", request(flow.getRevision()), editor); }
  }

  @Test void realKActorsActionExposesEditorInputsAndPublishesSchemaValidProvenanceBundle() throws Exception {
    var fixture = new Fixture("# Context\nAuthored rationale λ");
    var action = fixture.manager.getActions(fixture.flow.getId(), fixture.stage, fixture.editor).getFirst();
    assertEquals("prepare-review-draft", action.id()); assertEquals(6, action.missing().size());
    var updated = fixture.prepare(); var attachments = updated.getStates().get(fixture.stage).getAttachments();
    assertEquals(5, attachments.size()); assertEquals(fixture.flow.getRevision() + 1, updated.getRevision());
    var proposal = attachments.stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    assertEquals(ProposalReview.CheckStatus.PASS, ProposalSchemaValidator.bundled().validate(fixture.store.attachments.get(proposal.getId())).status());
    assertNotNull(updated.getStates().get(fixture.stage).getMetadata().get("ontologyReviewBootstrap"));
    assertEquals(5, fixture.store.attachments.size());
    assertThrows(IllegalStateException.class, () -> fixture.manager.executeAction(updated.getId(), fixture.stage,
        "prepare-review-draft", fixture.request(updated.getRevision()), fixture.editor));
    assertEquals(5, fixture.store.attachments.size());
  }

  @Test void deniesOtherEditorsAndStaleRequestsBeforeAnyNewPayload() throws Exception {
    var fixture = new Fixture(null);
    var other = new ProposalReviewProtocolTest().scope("other", "EDITOR");
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class, () ->
        fixture.manager.executeAction(fixture.flow.getId(), fixture.stage, "prepare-review-draft", fixture.request(fixture.flow.getRevision()), other));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class, () ->
        fixture.manager.executeAction(fixture.flow.getId(), fixture.stage, "prepare-review-draft", fixture.request(-1), fixture.editor));
    assertTrue(fixture.store.attachments.isEmpty());
  }

  @Test void failedAggregateOrPartialBlobWriteLeavesNoVisibleDraftOrOrphanPayload() throws Exception {
    for (boolean aggregate : List.of(true, false)) {
      var fixture = new Fixture(null);
      fixture.store.failFlowWrites = aggregate;
      fixture.store.failAttachmentWriteAt = aggregate ? -1 : 2;
      assertThrows(RuntimeException.class, fixture::prepare);
      assertTrue(fixture.store.attachments.isEmpty());
      var saved = fixture.store.getFlow(fixture.flow.getId());
      assertEquals(fixture.flow.getRevision(), saved.getRevision());
      assertTrue(saved.getStates().get(fixture.stage).getAttachments().isEmpty());
      assertNull(saved.getStates().get(fixture.stage).getMetadata().get("ontologyReviewBootstrap"));
    }
  }

  @Test void currentProjectReadRevocationBlocksPreparationAndExistingCommentsArePreserved() throws Exception {
    var fixture = new Fixture(null);
    when(fixture.info.getPermissions()).thenReturn(EnumSet.noneOf(CRUDOperation.class));
    assertThrows(RuntimeException.class, fixture::prepare); assertTrue(fixture.store.attachments.isEmpty());
    when(fixture.info.getPermissions()).thenReturn(EnumSet.of(CRUDOperation.READ));
    var comments = Flow.AttachmentUpload.create(); comments.setType("bootstrap-comments");
    comments.setFileName("editor-comments.json"); comments.setMediaType("application/vnd.klab.comments+json");
    comments.setContent("{\"comments\":[{\"text\":\"keep me\"}]}".getBytes(StandardCharsets.UTF_8));
    var original = fixture.manager.addAttachment(fixture.flow.getId(), fixture.stage, comments, fixture.editor);
    var current = fixture.manager.getFlow(fixture.flow.getId(), fixture.editor);
    var updated = fixture.manager.executeAction(current.getId(), fixture.stage, "prepare-review-draft", fixture.request(current.getRevision()), fixture.editor);
    assertEquals(1, updated.getStates().get(fixture.stage).getAttachments().stream().filter(a -> "bootstrap-comments".equals(a.getType())).count());
    assertArrayEquals(comments.getContent(), fixture.store.attachments.get(original.getId()));
  }
}
