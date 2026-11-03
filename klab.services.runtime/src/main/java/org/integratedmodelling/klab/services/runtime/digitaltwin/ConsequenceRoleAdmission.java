package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Server-only admission seam owned by a bound consequence cycle. No wire request or causal
 * header implements this interface. The engine supplies source-rule, semantic witness and graph
 * evidence authority; a transported claim alone is never sufficient to stage an attribution.
 * Both semantic methods execute outside the native graph monitor.
 */
public interface ConsequenceRoleAdmission {
  record Prepared(long sourceId,long recipientId,String ruleId,Concept role,
      Observable before,Observable after) {}
  /** Validate the entire prepared batch against this cycle's rule and applicability evidence. */
  void validate(List<Prepared> prepared,ContextScope execution);
  /** Revalidate the bound authority before root commit admission, not against an unrelated cycle. */
  void admitCommit();
  /** Detached source rule, epoch and witness provenance to persist with the scientific activity. */
  Map<String,Object> provenance();
  /** Exact inherited execution and applicability accounting; absent accounting is never authority. */
  default org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding semanticEvidence(){return null;}
}
