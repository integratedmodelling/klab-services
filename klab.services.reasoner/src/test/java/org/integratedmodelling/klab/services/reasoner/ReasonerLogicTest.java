package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class ReasonerLogicTest {

  @Test
  void unionRequiresEveryOperandToBeSubsumed() {
    Concept animal = concept("Animal");
    Concept mammal = concept("Mammal");
    Concept bird = concept("Bird");
    Concept union = logical("MammalOrBird", SemanticType.UNION);
    Map<Concept, Collection<Concept>> parents =
        Map.of(mammal, List.of(animal), bird, List.of(animal));

    assertTrue(
        ReasonerService.logicalSubsumption(
            union,
            animal,
            ignored -> List.of(mammal, bird),
            (candidate, target) ->
                SemanticMatcher.shortestAssertedDistance(
                        candidate,
                        target,
                        c -> parents.getOrDefault(c.asConcept(), List.of()))
                    >= 0));
    assertFalse(
        ReasonerService.logicalSubsumption(
            union,
            mammal,
            ignored -> List.of(mammal, bird),
            (candidate, target) -> candidate.equals(target)));
  }

  @Test
  void intersectionIsSubsumedByEachOperand() {
    Concept mammal = concept("Mammal");
    Concept aquatic = concept("Aquatic");
    Concept intersection = logical("AquaticMammal", SemanticType.INTERSECTION);

    assertTrue(
        ReasonerService.logicalSubsumption(
            intersection,
            mammal,
            ignored -> List.of(mammal, aquatic),
            Object::equals));
  }

  private static Concept logical(String name, SemanticType type) {
    Concept ret = concept(name);
    ret.getType().add(type);
    return ret;
  }

  private static Concept concept(String name) {
    var ret = new ConceptImpl();
    ret.setUrn("test:" + name);
    ret.setNamespace("test");
    ret.setName(name);
    ret.setReferenceName(name.toLowerCase());
    return ret;
  }
}
