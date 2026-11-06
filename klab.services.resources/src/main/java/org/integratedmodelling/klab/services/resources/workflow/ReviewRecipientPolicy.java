package org.integratedmodelling.klab.services.resources.workflow;

import java.time.*;
import java.util.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox;

/** Trusted directory boundary. Never derive verification or consent from mutable user metadata,
 * cached scopes, the presence of an email address, workflow roles or authentication alone. */
public final class ReviewRecipientPolicy implements ReviewNotificationOutbox.RecipientPolicy {
  public enum Consent { GRANTED, DECLINED, UNKNOWN }
  public record Account(UserScope currentScope, String verifiedMailbox, boolean mailboxVerified,
      Consent consent, Instant verifiedAt) {}
  /** Empty means known revoked/missing. Outages or unavailable consent/verification must throw.
   * Implementations must resolve the exact hashed principal ID from an authoritative current source. */
  @FunctionalInterface public interface Directory { Optional<Account> lookup(String principalId); }
  private final WorkflowStore store;
  private final Directory directory;
  private final Clock clock;
  public ReviewRecipientPolicy(WorkflowStore store, Directory directory, Clock clock) {
    this.store = Objects.requireNonNull(store); this.directory = Objects.requireNonNull(directory);
    this.clock = Objects.requireNonNull(clock);
  }
  @Override public Optional<String> authorizedMailbox(ReviewNotificationOutbox.Event event, String identity) {
    if (!event.recipients().contains(identity)) return Optional.empty();
    var account = Objects.requireNonNull(directory.lookup(identity));
    if (account.isEmpty()) return Optional.empty();
    var current = account.get(); var now = clock.instant();
    if (current.verifiedAt() == null || current.verifiedAt().isAfter(now)
        || current.verifiedAt().isBefore(now.minusSeconds(60))) throw new IllegalStateException("DIRECTORY_SNAPSHOT_STALE");
    if (current.consent() == null || current.consent() == Consent.UNKNOWN) throw new IllegalStateException("CONSENT_UNAVAILABLE");
    if (!current.mailboxVerified() || current.consent() != Consent.GRANTED || current.currentScope() == null)
      return Optional.empty();
    var actor = WorkflowParticipant.from(current.currentScope());
    if (!actor.isAuthenticated() || !identity.equals(ReviewWorkflowNotifications.recipientId(actor.getIdentity()))) return Optional.empty();
    var flow = store.getFlow(event.flowId());
    if (flow == null) return Optional.empty();
    var workflow = store.getWorkflow(flow.getWorkflowId(), flow.getWorkflowVersion());
    return workflow != null && ReviewWorkflowNotifications.admitted(flow, workflow, event, actor)
        ? Optional.ofNullable(current.verifiedMailbox()) : Optional.empty();
  }
}
