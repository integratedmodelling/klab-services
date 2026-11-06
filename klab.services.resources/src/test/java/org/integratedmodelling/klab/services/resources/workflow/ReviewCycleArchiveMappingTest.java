package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.ReviewCycleArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewCycleArchiveMappingTest {
  @TempDir Path project;
  @Test void closedSubmittedHistoryMapsExactlyAndRetriesAfterLaterCycles() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open();
    var peer = f.round(cycle, Audience.REVIEWER); var community = f.round(cycle, Audience.COMMUNITY);
    var reviewer = f.fixtures.scope("reviewer", "REVIEWER"); var citizen = f.community("citizen", true, false);
    f.save(cycle, peer, null, "private draft must never be archived", false, reviewer);
    f.save(cycle, peer, null, "submitted peer feedback", true, reviewer);
    f.save(cycle, community, null, "submitted community feedback", true, citizen);
    f.manager.reviewCycle(f.flowId, new Command(f.store.getFlow(f.flowId).getRevision(), Operation.SAVE_CONTRIBUTION,
        null, cycle.id(), peer.id(), null, null, "alternative", true, null, null,
        new ProposalInput(ReviewContributionProposalTest.document("r2"), "alternative ontology")), reviewer);
    var closed = f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor).cycles().getFirst();
    assertEquals(f.store.getFlow(f.flowId).getRevision(), closed.closedFlowRevision());
    var snapshot = ReviewCycleArchiveMapping.snapshot(f.store.getFlow(f.flowId), cycle.id(), "test-project", f.store::getWorkflowAttachment);
    assertEquals(closed.closedFlowRevision(), snapshot.revision()); assertEquals(closed.closedAt(), snapshot.closedAt());
    assertEquals(6, snapshot.entries().size());
    var all = snapshot.entries().stream().map(e -> new String(e.content(), StandardCharsets.UTF_8)).reduce("", String::concat);
    assertFalse(all.contains("private draft must never"));
    assertTrue(all.contains("submitted peer feedback")); assertTrue(all.contains("submitted community feedback"));
    assertTrue(all.contains("alternative ontology"));
    assertEquals(2, snapshot.entries().stream().filter(e -> e.contributor().equals("reviewer")).count());
    var identity = new PersonIdent("Archive test", "archive@example.invalid");
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      Files.writeString(project.resolve("seed.txt"), "fixture"); git.add().addFilepattern("seed.txt").call();
      git.commit().setAuthor(identity).setCommitter(identity).setMessage("Fixture").call();
      var receipt = ReviewCycleArchive.archive(project, snapshot, identity);
      assertEquals(ReviewCycleArchive.Status.COMMITTED, receipt.status());
      f.open();
      var retry = ReviewCycleArchiveMapping.snapshot(f.store.getFlow(f.flowId), cycle.id(), "test-project", f.store::getWorkflowAttachment);
      var second = ReviewCycleArchive.archive(project, retry, identity);
      assertEquals(ReviewCycleArchive.Status.ALREADY_COMMITTED, second.status());
      assertEquals(receipt.commitId(), second.commitId()); assertEquals(receipt.manifestSha256(), second.manifestSha256());
    }
  }
  @Test void openCycleAndTamperedArtifactsCannotBecomeArchiveInput() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open();
    assertThrows(KlabIllegalStateException.class, () -> ReviewCycleArchiveMapping.snapshot(
        f.store.getFlow(f.flowId), cycle.id(), "test-project", f.store::getWorkflowAttachment));
    f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
    assertThrows(KlabIllegalStateException.class, () -> ReviewCycleArchiveMapping.snapshot(
        f.store.getFlow(f.flowId), cycle.id(), "test-project", id -> "tampered".getBytes(StandardCharsets.UTF_8)));
  }
}
