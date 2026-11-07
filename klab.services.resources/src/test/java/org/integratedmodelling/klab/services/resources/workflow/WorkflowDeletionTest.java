package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;

/** Permanent aggregate deletion is explicitly administrative, not a review-stage mutation. */
class WorkflowDeletionTest {
  @Test void adminCanRemoveBootstrapReviewCoalescingAndTerminatedAggregatesButEditorsCannot()
      throws Exception {
    var identities = new ProposalReviewProtocolTest();
    var admin = identities.scope("administrator", "ADMIN");
    for (int phase = 0; phase < 4; phase++) {
      var f = new OntologyReviewBootstrapActionTest().new Fixture("# Private bootstrap reference");
      var flow = f.prepare();
      var proposal = flow.getStates().get(f.stage).getAttachments().stream()
          .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
      if (phase > 0) {
        var view = f.manager.reviewCycle(flow.getId(),
            new Command(flow.getRevision(), Operation.OPEN_CYCLE, f.stage,
                null, null, null, null, null, false, proposal.getId(), null), f.editor);
        view = f.manager.reviewCycle(flow.getId(),
            new Command(view.revision(), Operation.OPEN_ROUND, null,
                view.cycles().getFirst().id(), null, Audience.REVIEWER, null, null, false), f.editor);
        if (phase > 1) {
          view = f.manager.reviewCycle(flow.getId(),
              new Command(view.revision(), Operation.ENTER_COALESCING, null,
                  null, null, null, null, null, false), f.editor);
          if (phase > 2) f.manager.reviewCycle(flow.getId(),
              new Command(view.revision(), Operation.TERMINATE, null,
                  null, null, null, null, null, false), f.editor);
        }
      }
      var before = Utils.Json.asString(f.store.getFlow(flow.getId()));
      int payloads = f.store.attachments.size();
      for (var denied : List.of(f.editor, identities.scope("other", "EDITOR"),
          identities.scope("reviewer", "REVIEWER"))) {
        assertThrows(KlabResourceAccessException.class,
            () -> f.manager.deleteFlow(flow.getId(), denied));
      }
      // A privileged whole-flow operation does not enable individual evidence removal.
      assertThrows(KlabIllegalStateException.class,
          () -> f.manager.deleteState(flow.getId(), f.stage, admin));
      assertEquals(before, Utils.Json.asString(f.store.getFlow(flow.getId())));
      assertEquals(payloads, f.store.attachments.size());
      assertTrue(f.manager.deleteFlow(flow.getId(), admin));
      assertNull(f.store.getFlow(flow.getId()));
      assertTrue(f.store.attachments.isEmpty());
    }
  }

  @Test void serviceAdministratorPermissionWorksWithoutAnAdminWorkflowGroup() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null);
    var browser = new org.integratedmodelling.klab.services.scopes.ServiceUserScope(
        f.editor.getUser(), f.resources);
    browser.setPermissions(java.util.EnumSet.of(
        org.integratedmodelling.klab.api.authentication.CRUDOperation.READ));
    assertThrows(KlabResourceAccessException.class,
        () -> f.manager.deleteFlow(f.flow.getId(), browser));
    browser.setPermissions(java.util.EnumSet.of(
        org.integratedmodelling.klab.api.authentication.CRUDOperation.READ,
        org.integratedmodelling.klab.api.authentication.CRUDOperation.ADMINISTER));
    assertTrue(f.manager.deleteFlow(f.flow.getId(), browser));
    assertNull(f.store.getFlow(f.flow.getId()));
  }

  @Test void vetoRunsBeforeDeletingAnyAggregateOrPayload() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null);
    var flow = f.prepare();
    var manager = new WorkflowManager(f.store, new WorkflowStageLifecycleHandler() {
      @Override public void beforeStageDeleted(Context context) {
        assertNotNull(f.store.getFlow(context.flow().getId()));
        assertFalse(f.store.attachments.isEmpty());
        throw new IllegalStateException("Administrative deletion veto");
      }
    });
    var before = Utils.Json.asString(f.store.getFlow(flow.getId()));
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    assertThrows(KlabIllegalStateException.class, () -> manager.deleteFlow(flow.getId(), admin));
    assertEquals(before, Utils.Json.asString(f.store.getFlow(flow.getId())));
    assertFalse(f.store.attachments.isEmpty());
  }

  @Test void failedAggregateDeletionNeverDestroysItsAttachmentPayloads() throws Exception {
    var store = new WorkflowManagerAuthorizationTest.MemoryStore();
    var guarded = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {WorkflowStore.class}, (proxy, method, args) -> {
          if (method.getName().equals("deleteFlow")) return false;
          return method.invoke(store, args);
        });
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null, guarded);
    var flow = f.prepare();
    var before = Utils.Json.asString(store.getFlow(flow.getId()));
    int count = store.attachments.size();
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    assertThrows(KlabIllegalStateException.class, () -> f.manager.deleteFlow(flow.getId(), admin));
    assertEquals(before, Utils.Json.asString(store.getFlow(flow.getId())));
    assertEquals(count, store.attachments.size());
  }

  @Test void cleanupFailureIsReportedAfterAggregateDisappearsWithoutFalseSuccess() throws Exception {
    var store = new WorkflowManagerAuthorizationTest.MemoryStore();
    var guarded = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {WorkflowStore.class}, (proxy, method, args) -> {
          if (method.getName().equals("deleteWorkflowAttachment")) return false;
          return method.invoke(store, args);
        });
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null, guarded);
    var flow = f.prepare();
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    var failure = assertThrows(KlabIllegalStateException.class,
        () -> f.manager.deleteFlow(flow.getId(), admin));
    assertTrue(failure.getMessage().contains("Flow was deleted"));
    assertNull(store.getFlow(flow.getId()));
    assertFalse(store.attachments.isEmpty());
  }

  @Test void aggregateDeletionDoesNotDeleteAnUnrelatedFlowOrItsPayloads() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null);
    var flow = f.prepare();
    var state = Flow.State.create();
    state.setSchemaId("bootstrap");
    state.setAssetUrn("another.ontology");
    state.setAssetType(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY);
    var other = f.manager.createFlow("ontology-review-pilot", state, f.editor);
    f.store.putWorkflowAttachment("unrelated-file", other.getId(),
        other.getCurrentStateIds().iterator().next(), new byte[] {1, 2, 3});
    assertTrue(f.manager.deleteFlow(flow.getId(),
        new ProposalReviewProtocolTest().scope("administrator", "ADMIN")));
    assertNotNull(f.store.getFlow(other.getId()));
    assertArrayEquals(new byte[] {1, 2, 3}, f.store.getWorkflowAttachment("unrelated-file"));
    assertEquals(1, f.store.attachments.size());
  }
}
