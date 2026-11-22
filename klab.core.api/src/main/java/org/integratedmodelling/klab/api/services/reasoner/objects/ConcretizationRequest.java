package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Semantics;

/** Infer substitutions for a concept or observable using the supplied concrete concepts. */
public record ConcretizationRequest(Semantics pattern, List<Concept> concreteConcepts) {
  public ConcretizationRequest {
    if (pattern == null || concreteConcepts == null || concreteConcepts.stream().anyMatch(c -> c == null))
      throw new IllegalArgumentException("A pattern and a list of concrete concepts are required");
    concreteConcepts = List.copyOf(concreteConcepts);
  }
}
