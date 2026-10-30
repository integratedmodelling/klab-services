package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class SemanticMatcherCollectiveHierarchyTest {
  private ConceptImpl subject(String name) {
    var concept = new ConceptImpl(); concept.setUrn("w:" + name); concept.setNamespace("w");
    concept.setName(name); concept.setReferenceName(name.toLowerCase());
    concept.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE, SemanticType.COUNTABLE));
    return concept;
  }

  @Test void collectiveTraversalPreservesShortestHierarchyWithoutChangingSingularScores() {
    var location = subject("Location"); var region = subject("Region"); var basin = subject("Basin");
    Map<Concept, List<Concept>> parents = Map.of(region, List.of(location), basin, List.of(region));
    // OWL hierarchy parents are canonical singular classes even when queried through a collective view.
    java.util.function.Function<org.integratedmodelling.klab.api.knowledge.Semantics, java.util.Collection<Concept>>
        canonicalParents = c -> parents.getOrDefault(c.asConcept().singular(), List.of());
    assertEquals(2, SemanticMatcher.shortestAssertedDistance(basin, location, canonicalParents));
    assertEquals(2, SemanticMatcher.shortestAssertedDistance(basin.collective(), location.collective(), canonicalParents));
    assertEquals(1, SemanticMatcher.shortestAssertedDistance(region.collective(), location.collective(), canonicalParents));
    assertEquals(-1, SemanticMatcher.shortestAssertedDistance(location.collective(), region.collective(), canonicalParents));
    assertEquals(-1, SemanticMatcher.shortestAssertedDistance(region.collective(), location, canonicalParents));
    assertEquals(-1, SemanticMatcher.shortestAssertedDistance(region, location.collective(), canonicalParents));
  }
}
