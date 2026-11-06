package org.integratedmodelling.klab.services.resources.workflow;

import java.io.IOException;
import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.integratedmodelling.klab.services.base.ReviewNotificationIntents;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox.*;

/** Service-private replay ledger, published in the same conditional write as review facts.
 * No worker, directory lookup, transport or mailbox configuration is activated by this adapter. */
final class ReviewWorkflowNotifications {
  // Nitrite treats dots in dynamic Metadata keys as document paths. Keep this key flat and
  // serialize the nested intent wrappers as a string, preserving their own dotted reserved key.
  static final String LEDGER_KEY = "klab:review:notificationLedger:v1";
  private static final int MAX_EVENTS = 8192;
  private ReviewWorkflowNotifications() {}

  static void stage(Flow flow, Command command) {
    if (command.operation() == Operation.SAVE_CONTRIBUTION && !command.submit()) return;
    Kind kind = switch (command.operation()) {
      case OPEN_ROUND -> Kind.ROUND_OPENED;
      case SAVE_CONTRIBUTION -> Kind.CONTRIBUTION_SUBMITTED;
      case CLOSE_ROUND -> Kind.ROUND_CLOSED;
      case CLOSE_CYCLE -> Kind.CYCLE_CLOSED;
      default -> null;
    };
    if (kind == null) return; // Opening a candidate is not proof of editorial integration.
    var cycle = flow.getReviewCycles().stream().filter(c -> Objects.equals(c.id(), command.cycleId()))
        .findFirst().orElseThrow();
    var recipients = new TreeSet<String>();
    recipients.add(flow.getOwner());
    String coordinate;
    if (kind == Kind.CYCLE_CLOSED) {
      coordinate = "cycle/" + cycle.id();
      cycle.rounds().forEach(r -> addSubmitted(recipients, r));
    } else {
      var round = command.operation() == Operation.OPEN_ROUND ? cycle.rounds().getLast()
          : cycle.rounds().stream().filter(r -> Objects.equals(r.id(), command.roundId())).findFirst().orElseThrow();
      coordinate = "round/" + round.id();
      if (kind == Kind.ROUND_CLOSED) addSubmitted(recipients, round);
      if (kind == Kind.ROUND_OPENED) flow.getReviewCycles().forEach(c -> c.rounds().stream()
          .filter(r -> r.audience() == round.audience()).forEach(r -> addSubmitted(recipients, r)));
      // Submissions notify the flow editor only: no directory exists to prove additional
      // assigned editors' roles. Draft authors are never inferred as phase subscribers.
    }
    var ledger = ledger(flow);
    var identities = recipients.stream().map(ReviewWorkflowNotifications::recipientId).sorted().toList();
    int batches = (identities.size() + 255) / 256;
    if (ledger.size() + batches > MAX_EVENTS)
      throw new KlabIllegalStateException("Review notice ledger capacity reached");
    for (int batch = 0; batch < batches; batch++) {
      var event = new Event(flow.getId() + ":" + (flow.getRevision() + 1) + ":" + kind + ":" + batch,
          flow.getId(), coordinate, kind, identities.subList(batch * 256, Math.min(identities.size(), (batch + 1) * 256)));
      var metadata = new LinkedHashMap<String, Object>();
      ReviewNotificationIntents.stage(metadata, event);
      if (ledger.putIfAbsent(event.id(), metadata) != null)
        throw new KlabIllegalStateException("Review notification event identity collision");
    }
    String encoded = org.integratedmodelling.common.utils.Utils.Json.asString(ledger);
    if (encoded.length() > 8 * 1024 * 1024) throw new KlabIllegalStateException("Review notice ledger byte budget reached");
    flow.getMetadata().put(LEDGER_KEY, encoded);
  }

  private static void addSubmitted(Set<String> recipients, Round round) {
    round.contributions().stream().filter(Contribution::submitted).map(Contribution::owner).forEach(recipients::add);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ledger(Flow flow) {
    var value = flow.getMetadata().get(LEDGER_KEY);
    if (value == null) return new LinkedHashMap<>();
    if (!(value instanceof String encoded) || encoded.length() > 8 * 1024 * 1024)
      throw new KlabIllegalStateException("Invalid server notification ledger");
    var map = org.integratedmodelling.common.utils.Utils.Json.parseObject(encoded, Map.class);
    if (map == null || map.size() > MAX_EVENTS) throw new KlabIllegalStateException("Invalid server notification ledger");
    return new LinkedHashMap<>((Map<String, Object>) map);
  }

  /** Call only on an independent internal worker. Full replay repairs a lost commit wake-up;
   * outbox failures propagate to worker health, never roll back an already committed review. */
  @SuppressWarnings("unchecked")
  static int replayCommitted(WorkflowStore store, ReviewNotificationOutbox outbox) throws IOException {
    int added = 0;
    for (var flow : store.listFlows()) for (var value : ledger(flow).values()) {
      if (!(value instanceof Map<?, ?> metadata)) throw new KlabIllegalStateException("Invalid notice ledger entry");
      for (var event : ReviewNotificationIntents.read((Map<String, Object>) metadata)) {
        if (!Objects.equals(flow.getId(), event.flowId())) throw new KlabIllegalStateException("Cross-flow notice intent");
        if (outbox.enqueueCommitted(event)) added++;
      }
    }
    return added;
  }

  /** Delivery policy's authorization component; a trusted directory must separately resolve the
   * exact authenticated account and verified mailbox. A positive role grant never adds recipients. */
  static boolean admitted(Flow flow, Workflow workflow, Event event, WorkflowParticipant recipient) {
    if (!Objects.equals(flow.getId(), event.flowId()) || !recipient.isAuthenticated()
        || !event.recipients().contains(recipientId(recipient.getIdentity()))) return false;
    boolean editor = ReviewCycleProtocol.editor(flow, workflow, recipient);
    for (var cycle : flow.getReviewCycles()) {
      if (event.stageId().equals("cycle/" + cycle.id()))
        return event.kind() == Kind.CYCLE_CLOSED && cycle.closedAt() != null && (editor
            || cycle.rounds().stream().anyMatch(r -> ReviewCycleProtocol.admitted(workflow, recipient, r.audience())
                && r.contributions().stream().anyMatch(c -> c.submitted() && Objects.equals(c.owner(), recipient.getIdentity()))));
      for (var round : cycle.rounds()) if (event.stageId().equals("round/" + round.id())) {
        if (event.kind() != Kind.ROUND_OPENED && event.kind() != Kind.ROUND_CLOSED && event.kind() != Kind.CONTRIBUTION_SUBMITTED) return false;
        if (event.kind() == Kind.ROUND_CLOSED && round.closedAt() == null) return false;
        return editor || event.kind() != Kind.CONTRIBUTION_SUBMITTED && ReviewCycleProtocol.admitted(workflow, recipient, round.audience());
      }
    }
    return false;
  }

  /** Existing account names may contain Unicode or be email-shaped. Never place those names in
   * delivery metadata. A future trusted directory adapter must match this ID to the exact account. */
  static String recipientId(String identity) {
    if (identity == null || identity.isBlank()) throw new KlabIllegalStateException("Missing notification identity");
    return "principal/" + org.integratedmodelling.common.review.ProposalCandidateBinding.digest(
        identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static void requirePublicMetadata(Object value) {
    requirePublicMetadata(value, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
  }
  private static void requirePublicMetadata(Object value, Set<Object> path, int depth) {
    if (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) return;
    if (depth > 128 || !path.add(value)) throw new KlabIllegalArgumentException("Metadata must be bounded and acyclic");
    try {
      if (value instanceof Map<?, ?> map) for (var entry : map.entrySet()) {
        if (entry.getKey() instanceof String key && reserved(key))
          throw new KlabIllegalArgumentException("Review operational metadata is reserved for the service");
        requirePublicMetadata(entry.getValue(), path, depth + 1);
      } else if (value instanceof Collection<?> list) for (var item : list) requirePublicMetadata(item, path, depth + 1);
    } finally { path.remove(value); }
  }

  /** Strip recipient identities even from owner/admin/public-flow projections. */
  static void strip(Flow copy) {
    stripMap(copy.getMetadata());
    copy.getStates().values().forEach(s -> stripMap(s.getMetadata()));
    copy.getHistory().forEach(t -> stripMap(t.getMetadata()));
  }
  private static void stripMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      map.keySet().removeIf(key -> key instanceof String text && reserved(text));
      map.values().forEach(ReviewWorkflowNotifications::stripMap);
    } else if (value instanceof Collection<?> list) list.forEach(ReviewWorkflowNotifications::stripMap);
  }
  private static boolean reserved(String key) {
    return key.startsWith("klab.review.notification") || key.startsWith("klab:review:notification")
        || key.startsWith("klab.review.archive") || key.startsWith("klab:review:archive");
  }
}
