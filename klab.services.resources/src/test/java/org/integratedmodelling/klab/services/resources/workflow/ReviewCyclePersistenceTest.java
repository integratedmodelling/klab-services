package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual production Nitrite/RocksDB store, close/reopen, and scoped projection; no live identity provider. */
class ReviewCyclePersistenceTest {
  @TempDir Path directory;
  private ResourcesKBox open() {
    var service = mock(BaseService.class); when(service.serviceType()).thenReturn(KlabService.Type.RESOURCES);
    var options = ServiceStartupOptions.create(KlabService.Type.RESOURCES, new String[]{"-dataDir", directory.toString()});
    return new ResourcesKBox(mock(Scope.class), options, service);
  }

  @Test void closedAttributedFeedbackAndExactArtifactsSurviveProductionStoreReopen() throws Exception {
    var fixtures = new ProposalReviewProtocolTest(); var editor = fixtures.scope("editor", "EDITOR");
    var reviewer = fixtures.scope("reviewer", "REVIEWER"); String flowId; String cycleId; Cycle closed; byte[] proposal;
    var store = open();
    try {
      var manager = fixtures.manager(store);
      var flow = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, fixtures.initialization(true), editor);
      flowId = flow.getId(); var source = fixtures.current(flow);
      var view = manager.reviewCycle(flowId, new Command(flow.getRevision(), Operation.OPEN_CYCLE, source.getId(), null, null, null, null, null, false), editor);
      cycleId = view.cycles().getFirst().id();
      view = manager.reviewCycle(flowId, new Command(view.revision(), Operation.OPEN_ROUND, null, cycleId, null, Audience.REVIEWER, null, null, false), editor);
      String round = view.cycles().getFirst().rounds().getFirst().id();
      view = manager.reviewCycle(flowId, new Command(view.revision(), Operation.SAVE_CONTRIBUTION, null, cycleId, round, null, null, "Persistent reviewer feedback", true), reviewer);
      view = manager.reviewCycle(flowId, new Command(view.revision(), Operation.CLOSE_CYCLE, null, cycleId, null, null, null, null, false), editor);
      closed = view.cycles().getFirst();
      proposal = manager.getReviewArtifact(flowId, cycleId, closed.candidate().proposal().attachmentId(), reviewer);
    } finally { store.shutdown(); }
    store = open();
    try {
      var manager = fixtures.manager(store); var view = manager.getReviewCycles(flowId, reviewer);
      assertEquals(closed, view.cycles().getFirst()); assertFalse(view.canManage());
      assertTrue(view.contributableRoundIds().isEmpty());
      var saved = view.cycles().getFirst().rounds().getFirst().contributions().getFirst();
      assertEquals("reviewer", saved.owner()); assertEquals("Persistent reviewer feedback", saved.text());
      assertArrayEquals(proposal, manager.getReviewArtifact(flowId, cycleId, closed.candidate().proposal().attachmentId(), reviewer));
    } finally { store.shutdown(); }
  }
}
