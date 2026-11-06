package org.integratedmodelling.klab.services.base;

import static org.junit.jupiter.api.Assertions.*;
import static org.integratedmodelling.klab.services.base.ReviewNotificationOutbox.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewNotificationOutboxTest {
  @TempDir Path directory;
  final MutableClock clock = new MutableClock();
  final RecipientPolicy allowed = (event, recipient) -> Optional.of(recipient + "@example.invalid");
  ReviewNotificationOutbox open() throws IOException {
    return new ReviewNotificationOutbox(directory, clock, 100, 3, Duration.ofMinutes(1));
  }
  Event event(String id, String... recipients) {
    return new Event(id, "flow-1", "peer-stage", Kind.CONTRIBUTION_SUBMITTED, List.of(recipients));
  }
  void advance() { clock.now = clock.now.plusSeconds(61); }

  @Test void enqueueDoesNotSendAndSurvivesRestartWithRecipientDeduplication() throws Exception {
    try (var box = open()) {
      assertTrue(box.enqueueCommitted(event("e1", "editor", "editor", "other")));
      assertFalse(box.enqueueCommitted(event("e1", "other", "editor")));
      assertEquals(2, box.deliveries().size());
      assertTrue(box.deliveries().stream().allMatch(d -> d.status() == Status.PENDING));
    }
    var sent = new AtomicInteger();
    try (var box = open()) {
      assertFalse(box.enqueueCommitted(event("e1", "editor", "other")));
      assertEquals(1, box.dispatch(1, allowed, (to, subject, body) -> { sent.incrementAndGet(); return Result.ACCEPTED; }));
    }
    try (var box = open()) {
      box.dispatch(10, allowed, (to, subject, body) -> { sent.incrementAndGet(); return Result.ACCEPTED; });
      box.dispatch(10, allowed, (to, subject, body) -> fail("Already accepted"));
      assertEquals(2, sent.get());
    }
  }

  @Test void replayRepairsCrashAfterWorkflowCommitBeforeEnqueue() throws Exception {
    // The immutable committed source exists even if its post-commit wake-up never ran.
    var committedSource = List.of(event("transaction-1", "editor"), event("transaction-2", "editor"));
    try (var box = open()) { box.enqueueCommitted(committedSource.getFirst()); }
    try (var box = open()) {
      for (var event : committedSource) box.enqueueCommitted(event);
      assertEquals(2, box.deliveries().size());
      assertEquals(2, box.dispatch(10, allowed, (to, subject, body) -> Result.ACCEPTED));
    }
  }

  @Test void intentsPersistInSameHistoryMetadataAndReplayAfterPostCommitFailure() throws Exception {
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("existing", "preserved");
    ReviewNotificationIntents.stage(metadata, event("tx1:contribution", "editor"));
    ReviewNotificationIntents.stage(metadata, event("tx1:contribution", "editor"));
    assertEquals("preserved", metadata.get("existing"));
    // Simulate the JSON-compatible metadata snapshot committed with the workflow aggregate.
    var committed = Map.copyOf(metadata);
    assertEquals(List.of(event("tx1:contribution", "editor")), ReviewNotificationIntents.read(committed));
    try (var box = open()) {
      for (var intent : ReviewNotificationIntents.read(committed)) box.enqueueCommitted(intent);
      for (var intent : ReviewNotificationIntents.read(committed)) box.enqueueCommitted(intent);
      assertEquals(1, box.deliveries().size());
    }
    assertThrows(IllegalArgumentException.class, () -> ReviewNotificationIntents.stage(metadata,
        event("tx1:contribution", "community")));
    assertThrows(IllegalArgumentException.class, () -> ReviewNotificationIntents.read(
        Map.of(ReviewNotificationIntents.METADATA_KEY, List.of("invalid"))));
  }

  @Test void identityCollisionCannotWidenAudienceOrReplaceFact() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      assertThrows(IllegalArgumentException.class, () -> box.enqueueCommitted(event("e1", "community")));
      assertThrows(IllegalArgumentException.class, () -> box.enqueueCommitted(
          new Event("e1", "flow-1", "community-stage", Kind.ROUND_OPENED, List.of("editor"))));
      assertEquals(1, box.deliveries().size());
    }
  }

  @Test void disabledIsObservableRateLimitedAndDoesNotConsumeRetryBudget() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      for (int i = 0; i < 5; i++) {
        assertEquals(1, box.dispatch(1, allowed, (to, subject, body) -> Result.DISABLED));
        assertEquals(Status.DISABLED, box.deliveries().getFirst().status());
        assertEquals(0, box.deliveries().getFirst().attempts());
        assertEquals(0, box.dispatch(1, allowed, (to, subject, body) -> fail("Rate limit")));
        advance();
      }
      box.dispatch(1, allowed, (to, subject, body) -> Result.ACCEPTED);
      assertEquals(Status.ACCEPTED, box.deliveries().getFirst().status());
    }
  }

  @Test void knownNonDeliveryRetriesAreBoundedAndPersisted() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      box.dispatch(1, allowed, (to, subject, body) -> Result.RETRYABLE_FAILURE);
    }
    advance();
    try (var box = open()) {
      box.dispatch(1, allowed, (to, subject, body) -> Result.RETRYABLE_FAILURE);
      advance();
      box.dispatch(1, allowed, (to, subject, body) -> Result.RETRYABLE_FAILURE);
      advance();
      assertEquals(0, box.dispatch(1, allowed, (to, subject, body) -> fail("Retry budget exhausted")));
      assertEquals(Status.FAILED, box.deliveries().getFirst().status());
      assertEquals(3, box.deliveries().getFirst().attempts());
    }
  }

  @Test void exceptionIsUncertainNeverAutomaticallyRetriedOrLeaked() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      box.dispatch(1, allowed, (to, subject, body) -> { throw new RuntimeException("smtp password SECRET"); });
      assertEquals(Status.UNCERTAIN, box.deliveries().getFirst().status());
    }
    advance();
    try (var box = open()) {
      assertEquals(0, box.dispatch(1, allowed, (to, subject, body) -> fail("Uncertain requires reconciliation")));
      assertFalse(box.deliveries().toString().contains("SECRET"));
      box.resolveUncertain("e1", "editor", Resolution.CONFIRMED_ACCEPTED);
      assertEquals(Status.ACCEPTED, box.deliveries().getFirst().status());
    }
    assertFalse(new String(Files.readAllBytes(directory.resolve("review-notifications.bin")),
        java.nio.charset.StandardCharsets.ISO_8859_1).contains("SECRET"));
  }

  @Test void crashDuringSendRecoversAsUncertainWithoutDuplicate() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      assertThrows(SimulatedCrash.class, () -> box.dispatch(1, allowed, (to, subject, body) -> {
        throw new SimulatedCrash(); // Send may already have happened; skip normal result checkpoint.
      }));
    }
    advance();
    try (var box = open()) {
      assertEquals(Status.UNCERTAIN, box.deliveries().getFirst().status());
      assertEquals("CRASH_DURING_DELIVERY", box.deliveries().getFirst().reason());
      assertEquals(0, box.dispatch(1, allowed, (to, subject, body) -> fail("Do not duplicate")));
      box.resolveUncertain("e1", "editor", Resolution.RETRY_WITH_DUPLICATE_RISK);
      advance();
      assertEquals(1, box.dispatch(1, allowed, (to, subject, body) -> Result.ACCEPTED));
    }
  }

  @Test void currentAuthorizationIsRequiredAndNoContentOrAudienceLeaks() throws Exception {
    var sends = new ArrayList<String>();
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "revoked", "editor"));
      box.dispatch(10, (event, recipient) -> recipient.equals("editor")
          ? Optional.of("editor@example.invalid") : Optional.empty(), (to, subject, body) -> {
        sends.add(to);
        assertEquals("Ontology review update", subject);
        assertFalse(body.contains("peer-stage"));
        assertFalse(body.contains("flow-1"));
        assertFalse(body.contains("revoked"));
        return Result.ACCEPTED;
      });
      assertEquals(List.of("editor@example.invalid"), sends);
      assertEquals(Status.SUPPRESSED, box.deliveries().stream()
          .filter(d -> d.recipientIdentity().equals("revoked")).findFirst().orElseThrow().status());
    }
    var bytes = new String(Files.readAllBytes(directory.resolve("review-notifications.bin")),
        java.nio.charset.StandardCharsets.ISO_8859_1);
    assertFalse(bytes.contains("@example.invalid"));
  }

  @Test void recipientLookupFailsClosedAndMalformedMailboxesCannotExpandRecipients() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      box.dispatch(1, (event, recipient) -> { throw new IllegalStateException("private identity info"); },
          (to, subject, body) -> fail("No authorized recipient"));
      assertEquals("RECIPIENT_LOOKUP_FAILED", box.deliveries().getFirst().reason());
      advance();
      box.dispatch(1, (event, recipient) -> Optional.of("one@example.invalid,two@example.invalid"),
          (to, subject, body) -> fail("Multiple recipients forbidden"));
      assertEquals(Status.SUPPRESSED, box.deliveries().getFirst().status());
    }
  }

  @Test void capacityDoesNotPruneDeduplicationHistoryAndCanBeRaisedOnReopen() throws Exception {
    try (var box = new ReviewNotificationOutbox(directory, clock, 1, 3, Duration.ofMinutes(1))) {
      box.enqueueCommitted(event("e1", "editor"));
      assertThrows(IOException.class, () -> box.enqueueCommitted(event("e2", "editor")));
      assertFalse(box.enqueueCommitted(event("e1", "editor")));
    }
    try (var box = open()) { assertTrue(box.enqueueCommitted(event("e2", "editor"))); }
  }

  @Test void exclusiveOwnershipAndConcurrentDispatchPreventDuplicateSends() throws Exception {
    try (var box = open()) {
      assertThrows(IOException.class, this::open);
      box.enqueueCommitted(event("e1", "editor"));
      var sent = new AtomicInteger();
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var tasks = new ArrayList<Future<?>>();
        for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> {
          try { box.dispatch(1, allowed, (to, subject, body) -> { sent.incrementAndGet(); return Result.ACCEPTED; }); }
          catch (IOException e) { throw new RuntimeException(e); }
        }));
        for (var task : tasks) task.get();
      }
      assertEquals(1, sent.get());
    }
  }

  @Test void corruptStoreFailsClosedAndReleasesOwnership() throws Exception {
    Files.write(directory.resolve("review-notifications.bin"), new byte[] {1, 2, 3});
    assertThrows(IOException.class, this::open);
    assertThrows(IOException.class, this::open);
  }

  @Test void replacementFailurePreventsSendAndPoisonsInstance() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      Files.createDirectory(directory.resolve("review-notifications.tmp"));
      assertThrows(IOException.class, () -> box.dispatch(1, allowed, (to, subject, body) -> fail("Must persist before SMTP")));
      assertThrows(IllegalStateException.class, box::deliveries);
    }
  }

  @Test void identifiersRejectContentAndHeaderInjection() {
    assertThrows(IllegalArgumentException.class, () -> event("e1\r\nBcc:someone", "editor"));
    assertThrows(IllegalArgumentException.class, () -> event("e1", "real@example.invalid"));
    assertThrows(IllegalArgumentException.class, () -> event("Full confidential proposal text", "editor"));
  }

  @Test void boundedBatchesDoNotStarveNewRecipientsBehindDisabledDelivery() throws Exception {
    try (var box = open()) {
      box.enqueueCommitted(event("e1", "editor"));
      box.dispatch(1, allowed, (to, subject, body) -> Result.DISABLED);
      box.enqueueCommitted(event("e2", "other"));
      advance();
      box.dispatch(1, allowed, (to, subject, body) -> {
        assertEquals("other@example.invalid", to);
        return Result.ACCEPTED;
      });
    }
  }

  @Test void emailAdapterDistinguishesDisabledAcceptanceAndUncertainWithoutRealSmtp() {
    // A fake service-owned EmailManager: the overridden send cannot create an SMTP connection.
    var settings = (org.integratedmodelling.klab.api.configuration.Settings) java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[] {org.integratedmodelling.klab.api.configuration.Settings.class},
        (proxy, method, args) -> { throw new AssertionError("No settings read expected"); });
    class FakeEmail extends EmailManager {
      int outcome;
      FakeEmail() { super(settings); }
      @Override public boolean send(String recipient, String subject, String text) {
        if (outcome == 2) throw new IllegalStateException("SMTP acknowledgement lost");
        return outcome == 1;
      }
    }
    var email = new FakeEmail();
    var transport = new ReviewEmailTransport(email);
    assertEquals(Result.DISABLED, transport.send("editor@example.invalid", "subject", "body"));
    email.outcome = 1;
    assertEquals(Result.ACCEPTED, transport.send("editor@example.invalid", "subject", "body"));
    email.outcome = 2;
    assertEquals(Result.UNCERTAIN, transport.send("editor@example.invalid", "subject", "body"));
  }

  static class SimulatedCrash extends Error {}
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-06T12:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
}
