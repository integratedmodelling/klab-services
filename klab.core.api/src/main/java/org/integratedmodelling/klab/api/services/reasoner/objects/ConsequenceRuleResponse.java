package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticContentFingerprint;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;

/** Complete source candidates remain distinct from applicability, graph evidence and execution. */
public record ConsequenceRuleResponse(Status status, Epoch epoch, List<Entry> rules,
    SemanticDependencyWitness witness, String reason) {
  public enum Status { COMPLETE, UNAVAILABLE, STALE }
  public record Epoch(String serviceId,long knowledgeRevision,String semanticFingerprint,
      String semanticSessionId) {
    public boolean valid() {
      return serviceId!=null&&!serviceId.isBlank()&&knowledgeRevision>=0
          &&SemanticContentFingerprint.valid(semanticFingerprint)
          &&semanticSessionId!=null&&!semanticSessionId.isBlank();
    }
  }
  public record Entry(String ruleId,String declarationUrn,Concept owner,KimConsequenceRule rule,
      String sourceOntology,String sourceVersion,String sourceHash) {
    public Entry {
      Objects.requireNonNull(ruleId);Objects.requireNonNull(declarationUrn);
      Objects.requireNonNull(owner);Objects.requireNonNull(rule);
      Objects.requireNonNull(sourceOntology);Objects.requireNonNull(sourceVersion);Objects.requireNonNull(sourceHash);
    }
  }
  public ConsequenceRuleResponse {
    Objects.requireNonNull(status);
    rules=rules==null?List.of():List.copyOf(rules);
    if(status!=Status.COMPLETE&&!rules.isEmpty())
      throw new IllegalArgumentException("Unavailable/stale discovery cannot expose partial rules");
    if(status==Status.COMPLETE&&(epoch==null||!epoch.valid()))
      throw new IllegalArgumentException("Complete discovery requires a valid semantic epoch");
  }
}
