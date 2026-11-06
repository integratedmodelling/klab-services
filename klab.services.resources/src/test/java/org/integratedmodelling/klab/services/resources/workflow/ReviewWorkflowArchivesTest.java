package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewWorkflowArchivesTest {
  @TempDir Path directory;
  @Test void closureAndIntentAreAtomicAndReplayIsStableAcrossLaterCycleAndQueueRestart() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var peer = f.round(cycle, Audience.REVIEWER);
    f.save(cycle, peer, null, "PRIVATE-DRAFT", false, f.fixtures.scope("restricted-person", "REVIEWER"));
    f.save(cycle, peer, null, "RESTRICTED-TEXT", true, f.fixtures.scope("restricted-person", "REVIEWER"));
    var reject = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (p, m, args) -> m.getName().equals("compareAndSetFlow") ? false : m.invoke(f.store, args));
    assertThrows(KlabIllegalStateException.class, () -> f.fixtures.manager(reject).reviewCycle(f.flowId,
        new Command(f.store.getFlow(f.flowId).getRevision(), Operation.CLOSE_CYCLE, null, cycle.id(), null, null, null, null, false), f.editor));
    assertFalse(f.store.getFlow(f.flowId).getMetadata().containsKey(ReviewWorkflowArchives.LEDGER_KEY));
    f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
    var raw = f.store.getFlow(f.flowId); String encoded = raw.getMetadata().get(ReviewWorkflowArchives.LEDGER_KEY).toString();
    assertFalse(encoded.contains("PRIVATE")); assertFalse(encoded.contains("RESTRICTED")); assertFalse(encoded.contains("restricted-person"));
    long frozen = raw.getRevision(); f.open();
    assertEquals(encoded, f.store.getFlow(f.flowId).getMetadata().get(ReviewWorkflowArchives.LEDGER_KEY));
    for (int restart = 0; restart < 2; restart++) try (var box = new ReviewArchiveOutbox(directory, 10)) {
      assertEquals(restart == 0 ? 1 : 0, ReviewWorkflowArchives.replayCommitted(f.store, box));
      assertEquals(frozen, box.items().getFirst().intent().closedRevision());
    }
    assertFalse(Utils.Json.asString(f.manager.getFlow(f.flowId, f.editor)).contains(ReviewWorkflowArchives.LEDGER_KEY));
    assertFalse(Utils.Json.asString(f.manager.getFlow(f.flowId, f.community("reader", true, false))).contains(ReviewWorkflowArchives.LEDGER_KEY));
  }
  @Test void archiveLinksDoNotAdmitCommunityToPeerOrForeignDraftsEvenAfterClosure() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var peer = f.round(cycle, Audience.REVIEWER);
    var reviewer = f.fixtures.scope("reviewer", "REVIEWER");
    f.save(cycle, peer, null, "RESTRICTED-TEXT", true, reviewer);
    f.save(cycle, peer, null, "PRIVATE-DRAFT", false, reviewer);
    f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
    var citizen = f.community("reader", true, false);
    assertThrows(KlabResourceAccessException.class, () -> f.manager.getReviewCycles(f.flowId, citizen));
    assertThrows(KlabResourceAccessException.class, () -> f.manager.getReviewArtifact(f.flowId, cycle.id(), cycle.candidate().proposal().attachmentId(), citizen));
    String editorView = Utils.Json.asString(f.manager.getReviewCycles(f.flowId, f.editor));
    assertTrue(editorView.contains("RESTRICTED-TEXT")); assertFalse(editorView.contains("PRIVATE-DRAFT"));
    assertThrows(KlabIllegalArgumentException.class, () -> ReviewWorkflowNotifications.requirePublicMetadata(Map.of(
        "nested", Map.of(ReviewWorkflowArchives.LEDGER_KEY, "forged"))));
  }
}
