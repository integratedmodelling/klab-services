package org.integratedmodelling.klab.services.resources.workflow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.resources.ReviewCycleArchive;

/** Maps frozen submitted review history, never the current editing stage or private drafts.
 * This is deliberately not a public operation: callers must authorize the actual host project,
 * establish its readership policy and persist an archive outbox/receipt before scheduling Git. */
final class ReviewCycleArchiveMapping {
  private ReviewCycleArchiveMapping() {}

  static ReviewCycleArchive.ClosedCycle snapshot(Flow flow, String cycleId, String hostProject,
      Function<String, byte[]> payload) {
    var cycle = flow.getReviewCycles().stream().filter(c -> Objects.equals(c.id(), cycleId)).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Unknown review cycle"));
    if (cycle.closedAt() == null || cycle.closedFlowRevision() == null
        || cycle.closedFlowRevision() > flow.getRevision())
      throw new KlabIllegalStateException("A persisted closed cycle with its exact closure revision is required");
    var candidate = cycle.candidate();
    var entries = new ArrayList<ReviewCycleArchive.Entry>();
    addArtifact(entries, flow, candidate.proposal(), "candidate-proposal", candidate.proposalId(),
        candidate.revisionId(), "application/vnd.klab.proposal+yaml", payload);
    if (candidate.ontology() != null) addArtifact(entries, flow, candidate.ontology(), "candidate-ontology",
        candidate.proposalId(), candidate.revisionId(), "application/vnd.klab.ontology", payload);
    var rounds = new ArrayList<Object>();
    for (var round : cycle.rounds()) {
      if (round.closedAt() == null) throw new KlabIllegalStateException("Closed cycle contains an open round");
      var submitted = round.contributions().stream().filter(ReviewCycles.Contribution::submitted).toList();
      // Stable field ordering matters when retrying the same export after another JVM starts.
      var roundHistory = new LinkedHashMap<String, Object>();
      roundHistory.put("id", round.id()); roundHistory.put("audience", round.audience());
      roundHistory.put("openedBy", round.openedBy()); roundHistory.put("openedAt", round.openedAt());
      roundHistory.put("closedAt", round.closedAt());
      roundHistory.put("submittedContributions", submitted.stream().map(ReviewCycles.Contribution::id).toList());
      rounds.add(roundHistory);
      for (var contribution : submitted) {
        entries.add(new ReviewCycleArchive.Entry("contribution:" + contribution.id(),
            round.audience() == ReviewCycles.Audience.REVIEWER ? "reviewer-contribution" : "community-contribution",
            contribution.owner(), contribution.proposal() == null ? candidate.proposalId() : contribution.proposal().proposalId(),
            1, candidate.revisionId(), "application/json", json(contribution)));
      }
    }
    var history = new LinkedHashMap<String, Object>();
    history.put("schema", "klab-closed-review-history/1"); history.put("flow", flow.getId());
    history.put("cycle", cycle.id()); history.put("sourceState", cycle.sourceStateId());
    history.put("sourceFlowRevision", cycle.sourceFlowRevision()); history.put("candidate", candidate);
    history.put("openedBy", cycle.openedBy()); history.put("openedAt", cycle.openedAt());
    history.put("closedAt", cycle.closedAt()); history.put("closedFlowRevision", cycle.closedFlowRevision());
    history.put("rounds", rounds);
    entries.add(new ReviewCycleArchive.Entry("cycle:" + cycle.id(), "closed-cycle-history", cycle.openedBy(),
        candidate.proposalId(), 1, candidate.revisionId(), "application/json", json(history)));
    return new ReviewCycleArchive.ClosedCycle(hostProject, flow.getAssetUrn(), flow.getId(), cycle.id(),
        cycle.closedFlowRevision(), cycle.closedAt(), candidate.revisionId(), List.copyOf(entries));
  }

  private static void addArtifact(List<ReviewCycleArchive.Entry> entries, Flow flow,
      ProposalReview.Artifact artifact, String kind, String proposal, String revision, String media,
      Function<String, byte[]> payload) {
    byte[] bytes = payload.apply(artifact.attachmentId());
    ReviewCycleProtocol.artifact(flow, artifact, media, id -> bytes);
    var descriptor = flow.getStates().values().stream().flatMap(s -> s.getAttachments().stream())
        .filter(a -> Objects.equals(a.getId(), artifact.attachmentId())).findFirst().orElseThrow();
    entries.add(new ReviewCycleArchive.Entry("attachment:" + artifact.attachmentId(), kind,
        descriptor.getCreatedBy(), proposal, 1, revision, media, bytes));
  }
  private static byte[] json(Object value) { return Utils.Json.asString(value).getBytes(StandardCharsets.UTF_8); }
}
