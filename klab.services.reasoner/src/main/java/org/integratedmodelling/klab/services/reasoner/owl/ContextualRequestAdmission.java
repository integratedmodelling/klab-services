package org.integratedmodelling.klab.services.reasoner.owl;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;

/** Request permission only: never a computational mapping, observed member or coverage proof. */
public final class ContextualRequestAdmission {
  private ContextualRequestAdmission() {}

  public static boolean admits(OWL owl, Concept request, Concept context) {
    // Other activities retain their existing applicability/emergence/occurrence contracts.
    if (context == null || !(request.is(SemanticType.QUALITY) || request.is(SemanticType.PROCESS)))
      return true;
    var clauses = new OWLSemanticClauseSupport(owl);
    var bounds = clauses.bearerConstraints(request);
    if (bounds.isEmpty()
        || clauses.clauseCompatibility(request, SemanticRole.INHERENT, context)
            == OWLSemanticClauseSupport.Compatibility.COMPATIBLE) return true;

    // One named native bearer must prove the complete native bearer conjunction. Distinct
    // declarations cannot silently coalesce identities. Only named container/native parents are traversed;
    // no transitive containment, geometry, generic min-zero entailment or runtime lookup is used.
    for (var declaration : ObservationalContainmentSupport.declarationsForContainer(owl, context)) {
      var restriction = (OWLObjectMinCardinality) declaration.getSuperClass();
      var declaredMember = restriction.getFiller().asOWLClass();
      if (!ObservationalContainmentSupport.supportedPair(
          owl.getConceptFor(declaration.getSubClass().asOWLClass()), owl.getConceptFor(declaredMember))) continue;
      for (var bound : bounds) {
        if (bound.filler().isAnonymous()) continue;
        var nativeBearer = owl.getConceptFor(bound.filler().asOWLClass());
        if (nativeBearer == null || clauses.clauseCompatibility(request, SemanticRole.INHERENT, nativeBearer)
            != OWLSemanticClauseSupport.Compatibility.COMPATIBLE) continue;
        var visited = new java.util.HashSet<org.semanticweb.owlapi.model.OWLClass>();
        var parents = new java.util.ArrayDeque<Concept>();
        parents.add(nativeBearer);
        while (!parents.isEmpty()) {
          var current = parents.removeFirst();
          var named = owl.getOWLClass(current);
          if (!visited.add(named)) continue;
          if (named.equals(declaredMember)) return true;
          parents.addAll(owl.getParents(current));
        }
      }
    }
    return false;
  }
}
