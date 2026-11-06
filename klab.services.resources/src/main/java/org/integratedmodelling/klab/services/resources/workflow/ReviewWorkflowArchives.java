package org.integratedmodelling.klab.services.resources.workflow;

import java.io.IOException;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.ReviewArchiveOutbox;
import org.integratedmodelling.klab.resources.WorkflowStore;

/** Private closure facts, persisted atomically with the closed cycle. No Git I/O under the
 * workflow transaction. Replay to the service-private queue repairs a lost postcommit wake-up. */
final class ReviewWorkflowArchives {
  static final String LEDGER_KEY = "klab:review:archiveLedger:v1";
  private ReviewWorkflowArchives() {}

  static void stage(Flow flow, Command command) {
    if (command.operation() != Operation.CLOSE_CYCLE) return;
    var cycle = flow.getReviewCycles().stream().filter(c -> Objects.equals(c.id(), command.cycleId()))
        .findFirst().orElseThrow();
    if (cycle.closedAt() == null || !Objects.equals(cycle.closedFlowRevision(), flow.getRevision() + 1))
      throw new KlabIllegalStateException("Archive intent requires this transaction's frozen closure");
    var intents = intents(flow, false);
    var intent = new ReviewArchiveOutbox.Intent(flow.getId(), cycle.id(), flow.getAssetUrn(), cycle.closedFlowRevision());
    if (intents.stream().anyMatch(i -> i.id().equals(intent.id())))
      throw new KlabIllegalStateException("Duplicate archive closure intent");
    intents.add(intent);
    if (intents.size() > 256) throw new KlabIllegalStateException("Archive closure ledger capacity reached");
    flow.getMetadata().put(LEDGER_KEY, Utils.Json.asString(intents));
  }

  @SuppressWarnings("unchecked")
  private static List<ReviewArchiveOutbox.Intent> intents(Flow flow, boolean committed) {
    var raw = flow.getMetadata().get(LEDGER_KEY);
    if (raw == null) return new ArrayList<>();
    if (!(raw instanceof String encoded) || encoded.length() > 1024 * 1024)
      throw new KlabIllegalStateException("Invalid private archive ledger");
    var result = new ArrayList<ReviewArchiveOutbox.Intent>();
    var values = Utils.Json.parseObject(encoded, List.class);
    if (values == null || values.size() > 256) throw new KlabIllegalStateException("Invalid private archive ledger");
    var ids = new HashSet<String>();
    for (var value : values) {
      var intent = Utils.Json.parseObject(Utils.Json.asString(value), ReviewArchiveOutbox.Intent.class);
      var cycle = flow.getReviewCycles().stream().filter(c -> Objects.equals(c.id(), intent.cycleId())).findFirst()
          .orElseThrow(() -> new KlabIllegalStateException("Missing archive closure"));
      if (!Objects.equals(intent.flowId(), flow.getId()) || !Objects.equals(intent.ontologyUrn(), flow.getAssetUrn())
          || cycle.closedAt() == null || !Objects.equals(cycle.closedFlowRevision(), intent.closedRevision())
          || committed && intent.closedRevision() > flow.getRevision() || !ids.add(intent.id()))
        throw new KlabIllegalStateException("Archive intent is not bound to the persisted closed cycle");
      result.add(intent);
    }
    return result;
  }

  static int replayCommitted(WorkflowStore store, ReviewArchiveOutbox outbox) throws IOException {
    int added = 0;
    for (var flow : store.listFlows()) for (var intent : intents(flow, true))
      if (outbox.enqueueCommitted(intent)) added++;
    return added;
  }

  static ReviewArchiveOutbox.Intent committedIntent(Flow flow, String cycleId) {
    return intents(flow, true).stream().filter(i -> i.cycleId().equals(cycleId)).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("No persisted archive closure intent"));
  }
}
