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
