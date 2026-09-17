package org.integratedmodelling.klab.indexing;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticClauseRestriction;

/** Shared source for proposal bounds and their explanation. Services may supply all OWL restrictions. */
public class SemanticClauseSupport {
  protected final Reasoner reasoner;
  public SemanticClauseSupport(Reasoner reasoner) { this.reasoner = reasoner; }
  public List<SemanticClauseRestriction> clauses(Concept concept) {
    var result = new ArrayList<SemanticClauseRestriction>();
    add(result, SemanticRole.INHERENT, reasoner.directInherent(concept), reasoner.inherent(concept));
    add(result, SemanticRole.GOAL, reasoner.directGoal(concept), reasoner.goal(concept));
    add(result, SemanticRole.COOCCURRENT, reasoner.directCooccurrent(concept), reasoner.cooccurrent(concept));
    add(result, SemanticRole.CAUSANT, reasoner.directCausant(concept), reasoner.causant(concept));
    add(result, SemanticRole.CAUSED, reasoner.directCaused(concept), reasoner.caused(concept));
    add(result, SemanticRole.ADJACENT, reasoner.directAdjacent(concept), reasoner.adjacent(concept));
    add(result, SemanticRole.COMPRESENT, reasoner.directCompresent(concept), reasoner.compresent(concept));
    add(result, SemanticRole.RELATIONSHIP_SOURCE, null, reasoner.relationshipSource(concept));
    add(result, SemanticRole.RELATIONSHIP_TARGET, null, reasoner.relationshipTarget(concept));
    return result;
  }
  private void add(List<SemanticClauseRestriction> result, SemanticRole role, Concept direct, Concept effective) {
    if (direct != null) result.add(new SemanticClauseRestriction(role, direct, false));
    if (effective != null && (direct == null || !Objects.equals(direct.getUrn(), effective.getUrn())))
      result.add(new SemanticClauseRestriction(role, effective, true));
  }
  public boolean hasBounds(Concept owner, SemanticRole role) {
    return clauses(owner).stream().anyMatch(c -> c.getRole() == role);
  }
  public boolean accepts(Concept owner, SemanticRole role, Concept operand) {
    return clauses(owner).stream().filter(c -> c.getRole() == role).allMatch(c -> {
      Concept bound = c.getFiller();
      Concept candidate = operand.isCollective() ? operand.singular() : operand;
      bound = bound.isCollective() ? bound.singular() : bound;
      return Objects.equals(candidate.getUrn(), bound.getUrn()) || reasoner.is(candidate, bound);
    });
  }
}
