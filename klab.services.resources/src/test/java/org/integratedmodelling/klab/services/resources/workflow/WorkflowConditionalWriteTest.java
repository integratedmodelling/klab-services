package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;

class WorkflowConditionalWriteTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
  @Test void closingAndSubmittingFromIndependentManagersPreserveOnlyTheWinningRevision() throws Exception {
    for (boolean closeWins : new boolean[]{true, false}) {
      var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
      var reviewer = f.fixtures.scope("alice", "REVIEWER"); long revision = f.store.getFlow(f.flowId).getRevision();
      var barrier = new CyclicBarrier(2); var armed = new AtomicBoolean(true); var committed = new CountDownLatch(1);
      var proxy = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class}, (p, method, args) -> {
        if (method.getName().equals("compareAndSetFlow")) {
          var replacement = (org.integratedmodelling.klab.api.services.resources.workflow.Flow) args[0];
          boolean closing = replacement.getReviewCycles().getFirst().closedAt() != null;
          if (closing != closeWins) assertTrue(committed.await(10, TimeUnit.SECONDS));
          var result = method.invoke(f.store, args);
          if (closing == closeWins) committed.countDown(); return result;
        }
        var result = method.invoke(f.store, args);
        if (method.getName().equals("getFlow") && armed.get()) barrier.await(10, TimeUnit.SECONDS);
        return result;
      });
      var closer = f.fixtures.manager(proxy); var contributor = f.fixtures.manager(proxy);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var close = executor.submit(() -> {
          try { closer.reviewCycle(f.flowId, new Command(revision, Operation.CLOSE_CYCLE, null, cycle.id(), null, null, null, null, false), f.editor); return true; }
          catch (KlabIllegalStateException conflict) { return false; }
        });
        var save = executor.submit(() -> {
          try { contributor.reviewCycle(f.flowId, new Command(revision, Operation.SAVE_CONTRIBUTION, null, cycle.id(), round.id(), null, null, "racing submission", true), reviewer); return true; }
          catch (KlabIllegalStateException conflict) { return false; }
        });
        assertEquals(closeWins, close.get(20, TimeUnit.SECONDS)); assertEquals(!closeWins, save.get(20, TimeUnit.SECONDS));
      } finally { armed.set(false); }
      var current = f.store.getFlow(f.flowId); assertEquals(revision + 1, current.getRevision());
      assertEquals(closeWins ? 0 : 1, current.getReviewCycles().getFirst().rounds().getFirst().contributions().size());
      if (!closeWins) f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
      var closed = f.store.getFlow(f.flowId).getReviewCycles().getFirst(); assertNotNull(closed.closedAt());
      assertThrows(KlabIllegalStateException.class, () -> f.save(cycle, round, null, "late", true, reviewer));
      assertEquals(closed, f.store.getFlow(f.flowId).getReviewCycles().getFirst());
      try (var box = new org.integratedmodelling.klab.services.base.ReviewNotificationOutbox(
          directory.resolve(closeWins ? "close-wins" : "submit-wins"), java.time.Clock.systemUTC(), 20, 3, java.time.Duration.ofMinutes(1))) {
        assertEquals(closeWins ? 2 : 3, ReviewWorkflowNotifications.replayCommitted(f.store, box));
        assertEquals(0, ReviewWorkflowNotifications.replayCommitted(f.store, box));
      }
    }
  }
  @Test void independentManagersWritingTheSameSnapshotHaveOneWinner() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); var cycle = fixture.open(); var round = fixture.round(cycle, Audience.REVIEWER);
    long revision = fixture.store.getFlow(fixture.flowId).getRevision();
    var barrier = new CyclicBarrier(2); var armed = new AtomicBoolean(true);
    var store = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (proxy, method, args) -> {
          var result = method.invoke(fixture.store, args);
          if (method.getName().equals("getFlow") && armed.get()) barrier.await(10, TimeUnit.SECONDS);
          return result;
        });
    var first = fixture.fixtures.manager(store); var second = fixture.fixtures.manager(store);
    var command = new Command(revision, Operation.SAVE_CONTRIBUTION, null, cycle.id(), round.id(), null, null, "parallel", true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> { try { first.reviewCycle(fixture.flowId, command, fixture.fixtures.scope("alice", "REVIEWER")); return true; } catch (KlabIllegalStateException conflict) { return false; } });
      var b = executor.submit(() -> { try { second.reviewCycle(fixture.flowId, command, fixture.fixtures.scope("bob", "REVIEWER")); return true; } catch (KlabIllegalStateException conflict) { return false; } });
      assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
    } finally { armed.set(false); }
    assertEquals(revision + 1, fixture.store.getFlow(fixture.flowId).getRevision());
    assertEquals(1, fixture.manager.getReviewCycles(fixture.flowId, fixture.editor).cycles().getFirst().rounds().getFirst().contributions().size());
  }

  @Test void anUncertainSuccessfulAttachmentWriteRetainsItsCommittedPayload() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); var once = new AtomicBoolean(true);
    var store = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (proxy, method, args) -> {
          var result = method.invoke(fixture.store, args);
          if (method.getName().equals("compareAndSetFlow") && Boolean.TRUE.equals(result) && once.getAndSet(false))
            throw new KlabIllegalStateException("test: response lost after commit");
          return result;
        });
    var manager = fixture.fixtures.manager(store); long revision = fixture.store.getFlow(fixture.flowId).getRevision();
    var upload = fixture.fixtures.upload("supporting-material", "text/plain", "keep committed payload");
    assertThrows(KlabIllegalStateException.class, () -> manager.addAttachment(fixture.flowId, fixture.source, upload, fixture.editor));
    var flow = fixture.store.getFlow(fixture.flowId); assertEquals(revision + 1, flow.getRevision());
    var descriptor = flow.getStates().get(fixture.source).getAttachments().stream().filter(a -> a.getMediaType().equals("text/plain")).findFirst().orElseThrow();
    assertArrayEquals(upload.getContent(), fixture.store.getWorkflowAttachment(descriptor.getId()));
  }

  @Test void aRejectedConditionalAttachmentWriteCleansOnlyItsNewPayload() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); int count = ((WorkflowManagerAuthorizationTest.MemoryStore) fixture.store).attachments.size();
    var store = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (proxy, method, args) -> method.getName().equals("compareAndSetFlow") ? false : method.invoke(fixture.store, args));
    var manager = fixture.fixtures.manager(store); long revision = fixture.store.getFlow(fixture.flowId).getRevision();
    assertThrows(KlabIllegalStateException.class, () -> manager.addAttachment(fixture.flowId, fixture.source,
        fixture.fixtures.upload("supporting-material", "text/plain", "rejected"), fixture.editor));
    assertEquals(count, ((WorkflowManagerAuthorizationTest.MemoryStore) fixture.store).attachments.size());
    assertEquals(revision, fixture.store.getFlow(fixture.flowId).getRevision());
  }
}
