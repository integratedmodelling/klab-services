package org.integratedmodelling.klab.indexing;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticClauseRestriction;

/**
 * Shared source for proposal bounds and their explanation. Services may supply all OWL
 * restrictions.
 */
public class SemanticClauseSupport {
  protected final Reasoner reasoner;

  public SemanticClauseSupport(Reasoner reasoner) {
    this.reasoner = reasoner;
  }

  /** Check the domain in which a predicate or dependent may be used. */
  public boolean applicableTo(Concept concept, Concept target) {
    var alternatives = reasoner.applicableObservables(concept);
    if (alternatives == null || alternatives.isEmpty()) return true;
    var candidate = target.isCollective() ? target.singular() : target;
    return alternatives.stream()
        .anyMatch(
            bound -> {
              var type = bound.isCollective() ? bound.singular() : bound;
              return Objects.equals(candidate.getUrn(), type.getUrn())
                  || reasoner.is(candidate, type);
            });
  }

  /** Whether predicates can jointly qualify one operand. */
  public boolean predicatesCompatible(Collection<Concept> predicates) {
    if (predicates.size() < 2) return true;
    var intersection =
        reasoner.resolveConcept(
            String.join(
                " and ", predicates.stream().map(value -> "(" + value.getUrn() + ")").toList()));
    return intersection != null
        && !intersection.is(SemanticType.NOTHING)
        && reasoner.satisfiable(intersection);
  }

  public List<SemanticClauseRestriction> clauses(Concept concept) {
    var result = new ArrayList<SemanticClauseRestriction>();
    add(
        result,
        SemanticRole.INHERENT,
        reasoner.directInherent(concept),
        reasoner.inherent(concept));
    add(result, SemanticRole.GOAL, reasoner.directGoal(concept), reasoner.goal(concept));
    add(
        result,
        SemanticRole.COOCCURRENT,
        reasoner.directCooccurrent(concept),
        reasoner.cooccurrent(concept));
    add(result, SemanticRole.CAUSANT, reasoner.directCausant(concept), reasoner.causant(concept));
    add(result, SemanticRole.CAUSED, reasoner.directCaused(concept), reasoner.caused(concept));
    add(
        result,
        SemanticRole.ADJACENT,
        reasoner.directAdjacent(concept),
        reasoner.adjacent(concept));
    add(
        result,
        SemanticRole.COMPRESENT,
        reasoner.directCompresent(concept),
        reasoner.compresent(concept));
    add(result, SemanticRole.RELATIONSHIP_SOURCE, null, reasoner.relationshipSource(concept));
    add(result, SemanticRole.RELATIONSHIP_TARGET, null, reasoner.relationshipTarget(concept));
    return result;
  }

  private void add(
      List<SemanticClauseRestriction> result,
      SemanticRole role,
      Concept direct,
      Concept effective) {
    if (direct != null) result.add(new SemanticClauseRestriction(role, direct, false));
    if (effective != null
        && (direct == null || !Objects.equals(direct.getUrn(), effective.getUrn())))
      result.add(new SemanticClauseRestriction(role, effective, true));
  }

  public boolean hasBounds(Concept owner, SemanticRole role) {
    return clauses(owner).stream().anyMatch(c -> c.getRole() == role)
        || role == SemanticRole.INHERENT
            && SemanticType.isDependent(owner.getType())
            && !reasoner.applicableObservables(owner).isEmpty();
  }

  public boolean accepts(Concept owner, SemanticRole role, Concept operand) {
    if (role == SemanticRole.INHERENT
        && SemanticType.isDependent(owner.getType())
        && !applicableTo(owner, operand)) return false;
    return clauses(owner).stream()
        .filter(c -> c.getRole() == role)
        .allMatch(
            c -> {
              Concept bound = c.getFiller();
              Concept candidate = operand.isCollective() ? operand.singular() : operand;
              bound = bound.isCollective() ? bound.singular() : bound;
              return Objects.equals(candidate.getUrn(), bound.getUrn())
                  || reasoner.is(candidate, bound);
            });
  }
}
