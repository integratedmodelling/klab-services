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
      view = manager.reviewCycle(flowId, new Command(view.revision(), Operation.SAVE_CONTRIBUTION,
          null, cycleId, round, null, null, "Alternative proposal", true, null, null,
          new ProposalInput(ReviewContributionProposalTest.document("r2"), "ontology test.ontology;")), reviewer);
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
      var alternative = view.cycles().getFirst().rounds().getFirst().contributions().getLast().proposal();
      assertEquals(ReviewContributionProposalTest.document("r2"), alternative.document());
      assertEquals("ontology test.ontology;", alternative.ontologySource());
      assertArrayEquals(proposal, manager.getReviewArtifact(flowId, cycleId, closed.candidate().proposal().attachmentId(), reviewer));
    } finally { store.shutdown(); }
  }

  @Test void productionStoreConditionallyUpdatesOneOfTwoDetachedSnapshots() throws Exception {
    var fixtures = new ProposalReviewProtocolTest(); var editor = fixtures.scope("editor", "EDITOR");
    var store = open();
    try {
      var manager = fixtures.manager(store);
      var flow = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, fixtures.initialization(true), editor);
      long expected = flow.getRevision();
      var first = org.integratedmodelling.common.utils.Utils.Json.parseObject(org.integratedmodelling.common.utils.Utils.Json.asString(store.getFlow(flow.getId())), org.integratedmodelling.klab.api.services.resources.workflow.Flow.class);
      var second = org.integratedmodelling.common.utils.Utils.Json.parseObject(org.integratedmodelling.common.utils.Utils.Json.asString(first), org.integratedmodelling.klab.api.services.resources.workflow.Flow.class);
      first.setRevision(expected + 1); second.setRevision(expected + 1);
      first.getMetadata().put("writer", "first"); second.getMetadata().put("writer", "second");
      var gate = new java.util.concurrent.CountDownLatch(1);
      try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        var a = executor.submit(() -> { gate.await(); return store.compareAndSetFlow(first, expected); });
        var b = executor.submit(() -> { gate.await(); return store.compareAndSetFlow(second, expected); });
        gate.countDown();
        assertEquals(1, (a.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0));
      }
      assertEquals(expected + 1, store.getFlow(flow.getId()).getRevision());
      assertFalse(store.compareAndSetFlow(first, expected));
    } finally { store.shutdown(); }
  }

  @Test void anotherProcessCannotOwnTheDatabaseUntilTheCurrentOwnerCloses() throws Exception {
    var store = open(); var database = directory.resolve("services/resources/data/resources.db");
    try {
      assertTrue(java.nio.file.Files.exists(database));
      assertEquals(23, probe(database, "while-owned.log"));
      assertTrue(java.nio.file.Files.readString(directory.resolve("while-owned.log")).toLowerCase().contains("lock"));
    } finally { store.shutdown(); }
    assertEquals(0, probe(database, "after-close.log"));
  }

  private int probe(Path database, String log) throws Exception {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    // Reactor test classpaths exceed Windows' process command-line limit. Java argfiles also
    // keep paths containing spaces intact without invoking a shell.
    var arguments = directory.resolve(log + ".args");
    java.nio.file.Files.writeString(arguments, java.util.stream.Stream.of(
        "-Duser.home=" + System.getProperty("user.home"), "-cp",
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
        LockProbe.class.getName(), database.toString())
        .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        .collect(java.util.stream.Collectors.joining(System.lineSeparator())));
    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
        "@" + arguments)
        .redirectErrorStream(true).redirectOutput(directory.resolve(log).toFile()).start();
    if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
      process.destroyForcibly(); fail("Isolated database lock probe timed out");
    }
    return process.exitValue();
  }

  public static class LockProbe {
    public static void main(String[] args) {
      try (var database = org.dizitart.no2.Nitrite.builder().loadModule(
          org.dizitart.no2.rocksdb.RocksDBModule.withConfig().filePath(args[0]).build()).openOrCreate()) {
        System.out.println("OPENED");
      } catch (Throwable failure) {
        failure.printStackTrace(System.out); System.exit(23);
      }
    }
  }
}
