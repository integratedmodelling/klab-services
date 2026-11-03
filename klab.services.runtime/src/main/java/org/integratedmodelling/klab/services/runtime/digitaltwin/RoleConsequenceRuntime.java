package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Server-owned integration boundary: rule/cycle authority, guarded commits and normal resolution. */
public interface RoleConsequenceRuntime {
  interface Evidence {}
  record Rule(String id,Concept role,ConsequenceRecipients.Selector selector,KimConcept recipient,Evidence evidence) {
    public Rule { Objects.requireNonNull(id);Objects.requireNonNull(role);Objects.requireNonNull(selector); }
    public Rule(String id,Concept role,ConsequenceRecipients.Selector selector,KimConcept recipient) {
      this(id,role,selector,recipient,null);
    }
  }
  record Rules(ConsequenceRecipients.Status status,List<Rule> values,String reason) {
    public Rules {
      Objects.requireNonNull(status);values=List.copyOf(values);Objects.requireNonNull(reason);
      if(status!=ConsequenceRecipients.Status.COMPLETE&&!values.isEmpty())
        throw new IllegalArgumentException("Unavailable rule discovery cannot expose partial rules");
    }
  }
  enum Resolution { NOT_REQUESTED, RESOLVED, NO_MODEL, UNRESOLVED, FAILED }
  record Effect(Consequence.Outcome outcome,List<Long> observations,String reason,Resolution resolution) {
    public Effect {Objects.requireNonNull(outcome);observations=List.copyOf(observations);Objects.requireNonNull(reason);Objects.requireNonNull(resolution);}
    public Effect(Consequence.Outcome outcome,List<Long> observations,String reason) {
      this(outcome,observations,reason,Resolution.NOT_REQUESTED);
    }
  }
  /** An exact newly committed role attribution, not all roles found in a context scan. */
  record Arrival(long sourceId,long recipientId,Concept role,String ruleId,boolean characterize) {
    public Arrival { if(recipientId<=0)throw new IllegalArgumentException("Role arrival requires a durable recipient");Objects.requireNonNull(role); }
  }
  record Arrivals(ConsequenceRecipients.Status status,List<Arrival> values,String reason) {
    public Arrivals {
      Objects.requireNonNull(status);values=List.copyOf(values);Objects.requireNonNull(reason);
      if(status!=ConsequenceRecipients.Status.COMPLETE&&!values.isEmpty())
        throw new IllegalArgumentException("Incomplete role evidence cannot expose partial arrivals");
    }
  }

  /** COMPLETE means source rules enumerated under the bound cycle; applicability is checked at apply. */
  Rules conferrals(Observation source,ServiceContextScope scope) throws Exception;
  ConsequenceRecipients.Selection resolveEach(KimConcept recipient,ServiceContextScope scope) throws Exception;
  /** Source-bound ordinary discovery; production retains successful complete-selection evidence. */
  default ConsequenceRecipients.Selection resolveRecipients(Observation source,Rule rule,ServiceContextScope scope)throws Exception {
    return resolveEach(rule.recipient(),scope);
  }
  /** Apply the whole recipient batch with semantic authority and native read guards; never characterize here. */
  Effect attribute(Consequence.Cause cause,Observation source,Rule rule,List<Observation> recipients,
      ServiceContextScope scope) throws Exception;
  default Effect attributeSelected(Consequence.Cause cause,Observation source,Rule rule,ConsequenceRecipients.Selection recipients,
      ServiceContextScope scope)throws Exception {return attribute(cause,source,rule,recipients.recipients(),scope);}
  /** Extract CLASSIFIED audit rows from this commit, retaining role/source/recipient identity. */
  Arrivals arrivals(KnowledgeGraph.Commit commit,ServiceContextScope scope) throws Exception;
  /** Changed relationship/configuration implications; configuration detection/resolution order is preserved. */
  Effect sourceImplications(Consequence.Cause cause,KnowledgeGraph.Commit commit,
      ServiceContextScope scope) throws Exception;
  /** Resolve applicable IMPLIES targets using normal optional submission and bound semantics. */
  Effect implications(Consequence.Cause cause,Arrival role,ServiceContextScope scope) throws Exception;
  /** Characterize role in the actual singular recipient; missing models never undo attribution. */
  Effect characterize(Consequence.Cause cause,Arrival role,ServiceContextScope scope) throws Exception;
}
