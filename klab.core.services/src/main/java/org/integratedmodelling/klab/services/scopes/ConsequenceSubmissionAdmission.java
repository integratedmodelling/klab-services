package org.integratedmodelling.klab.services.scopes;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;

/** Server-owned evidence binder for one optional submission; never transported as authority. */
@FunctionalInterface
public interface ConsequenceSubmissionAdmission {
  void bind(DigitalTwin.Transaction transaction, ServiceContextScope execution);

  /** Exact original directive check before any runtime-owned consequence operation. */
  default void requireTarget(org.integratedmodelling.klab.api.knowledge.Observable requested,
      ServiceContextScope execution) {
    throw new IllegalStateException("Consequence target authority is unavailable");
  }

  /** Native source/result guard prepared outside locks; no transported value implements it. */
  @FunctionalInterface
  interface ExistingResultGuard {
    void validate(org.integratedmodelling.klab.api.data.KnowledgeGraph.Transaction transaction);
  }

  /** Existing answers need causal validation even when no perception write is necessary. */
  default ExistingResultGuard prepareExisting(
      org.integratedmodelling.klab.api.knowledge.observation.Observation result,
      ServiceContextScope execution) {
    throw new IllegalStateException("Existing-result causal admission is unavailable");
  }

  /** Actual positive root receipt; a reused asset's older metadata is not this request's commit. */
  default void committed(long commitId) {}

  /** Exact source/rule accounting after bind; absent attached accounting stays UNKNOWN. */
  default org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding semanticEvidence(){return null;}
}
