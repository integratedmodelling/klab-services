package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox.*;
import org.junit.jupiter.api.Test;

class ReviewRecipientPolicyTest {
  final Instant now = Instant.parse("2026-11-06T18:00:00Z");
  @Test void freshVerifiedConsentingExactAccountIsRequiredAndRevocationIsRechecked() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
    var alice = f.fixtures.scope("alice@example.invalid", "REVIEWER");
    String id = ReviewWorkflowNotifications.recipientId("alice@example.invalid");
    var event = new Event("fixture", f.flowId, "round/" + round.id(), Kind.ROUND_OPENED, List.of(id));
    var result = new AtomicReference<>(new ReviewRecipientPolicy.Account(alice, "verified@example.invalid", true,
        ReviewRecipientPolicy.Consent.GRANTED, now));
    var policy = new ReviewRecipientPolicy(f.store, requested -> { assertEquals(id, requested); return Optional.ofNullable(result.get()); }, Clock.fixed(now, ZoneOffset.UTC));
    assertEquals(Optional.of("verified@example.invalid"), policy.authorizedMailbox(event, id));
    result.set(new ReviewRecipientPolicy.Account(f.community("alice@example.invalid", true, false), "verified@example.invalid", true,
        ReviewRecipientPolicy.Consent.GRANTED, now));
    assertTrue(policy.authorizedMailbox(event, id).isEmpty());
    result.set(new ReviewRecipientPolicy.Account(f.fixtures.scope("someone-else", "REVIEWER"), "wrong@example.invalid", true,
        ReviewRecipientPolicy.Consent.GRANTED, now));
    assertTrue(policy.authorizedMailbox(event, id).isEmpty());
    result.set(null); assertTrue(policy.authorizedMailbox(event, id).isEmpty());
  }
  @Test void missingConsentStaleDirectoryAndOutagesNeverBecomeAssumedPermission() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.COMMUNITY);
    String id = ReviewWorkflowNotifications.recipientId("editor");
    var event = new Event("fixture", f.flowId, "round/" + round.id(), Kind.ROUND_OPENED, List.of(id));
    for (var consent : ReviewRecipientPolicy.Consent.values()) {
      var policy = new ReviewRecipientPolicy(f.store, key -> Optional.of(new ReviewRecipientPolicy.Account(f.editor,
          "verified@example.invalid", true, consent, now)), Clock.fixed(now, ZoneOffset.UTC));
      if (consent == ReviewRecipientPolicy.Consent.UNKNOWN) assertThrows(IllegalStateException.class, () -> policy.authorizedMailbox(event, id));
      else assertEquals(consent == ReviewRecipientPolicy.Consent.GRANTED, policy.authorizedMailbox(event, id).isPresent());
    }
    var unverified = new ReviewRecipientPolicy(f.store, key -> Optional.of(new ReviewRecipientPolicy.Account(f.editor,
        "unverified@example.invalid", false, ReviewRecipientPolicy.Consent.GRANTED, now)), Clock.fixed(now, ZoneOffset.UTC));
    assertTrue(unverified.authorizedMailbox(event, id).isEmpty());
    for (var timestamp : List.of(now.minusSeconds(61), now.plusSeconds(1))) {
      var stale = new ReviewRecipientPolicy(f.store, key -> Optional.of(new ReviewRecipientPolicy.Account(f.editor,
          "verified@example.invalid", true, ReviewRecipientPolicy.Consent.GRANTED, timestamp)), Clock.fixed(now, ZoneOffset.UTC));
      assertThrows(IllegalStateException.class, () -> stale.authorizedMailbox(event, id));
    }
    var unavailable = new ReviewRecipientPolicy(f.store, key -> { throw new IllegalStateException("unavailable"); }, Clock.fixed(now, ZoneOffset.UTC));
    assertThrows(IllegalStateException.class, () -> unavailable.authorizedMailbox(event, id));
    assertTrue(unavailable.authorizedMailbox(event, "not-a-frozen-recipient").isEmpty());
  }
}
