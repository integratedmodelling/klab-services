package org.integratedmodelling.klab.services.resources.workflow;

import java.util.List;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;

/** Read-only validation seam. Implementations must not apply ontologies or publish PRs.
 * Results must describe these exact bytes and imported worldview context. An acceptance requires
 * import context, schema, parser, adaptation and Reasoner PASS; scientific judgment is recorded separately by the manager.
 */
@FunctionalInterface
public interface ProposalCandidateValidator {
  List<ProposalReview.Check> validate(ProposalReview.Candidate candidate, byte[] proposal, byte[] ontology);

  ProposalCandidateValidator UNAVAILABLE = (candidate, proposal, ontology) -> List.of(
      new ProposalReview.Check(ProposalReview.CheckKind.IMPORT_CONTEXT, ProposalReview.CheckStatus.BLOCKED,
          List.of("No current import context validator configured")),
      new ProposalReview.Check(ProposalReview.CheckKind.DOCUMENT_SCHEMA, ProposalReview.CheckStatus.BLOCKED,
          List.of("No proposal schema validator configured")),
      new ProposalReview.Check(ProposalReview.CheckKind.PARSER, ProposalReview.CheckStatus.NOT_RUN, List.of()),
      new ProposalReview.Check(ProposalReview.CheckKind.REASONER, ProposalReview.CheckStatus.NOT_RUN, List.of()));
}
