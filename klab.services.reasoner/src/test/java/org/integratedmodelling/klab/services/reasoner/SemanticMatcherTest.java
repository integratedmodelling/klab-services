package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class SemanticMatcherTest {

  @Test
  void assertedDistanceUsesShortestPathAndTerminatesOnCycles() {
    var graph = new FakeOperations();
    Concept root = concept("Root");
    Concept left = concept("Left");
    Concept right = concept("Right");
    Concept leaf = concept("Leaf");
    graph.parents.put(left, List.of(root));
    graph.parents.put(right, List.of(root));
    graph.parents.put(leaf, List.of(left, right));

    var matcher = new SemanticMatcher(graph);
    assertEquals(0, matcher.assertedDistance(leaf, leaf));
    assertEquals(1, matcher.assertedDistance(leaf, left));
    assertEquals(2, matcher.assertedDistance(leaf, root));
    assertEquals(-1, matcher.assertedDistance(root, leaf));
    assertEquals(-1, matcher.assertedDistance(left, right));

    graph.parents.put(root, List.of(leaf));
    graph.revision++;
    assertEquals(3, matcher.assertedDistance(left, right));
  }

  @Test
  void semanticDistanceIsDirectionalAndMonotonic() {
    var graph = new FakeOperations();
    Concept root = predicate("Root");
    Concept child = predicate("Child");
    Concept grandchild = predicate("Grandchild");
    Concept sibling = predicate("Sibling");
    graph.parents.put(child, List.of(root));
    graph.parents.put(grandchild, List.of(child));
    graph.parents.put(sibling, List.of(root));

    var matcher = new SemanticMatcher(graph);
    assertEquals(0, matcher.semanticDistance(root, root));
    int direct = matcher.semanticDistance(root, child);
    int indirect = matcher.semanticDistance(root, grandchild);
    assertTrue(direct > 0);
    assertTrue(indirect > direct);
    assertTrue(matcher.semanticDistance(child, root) < 0);
    assertTrue(matcher.semanticDistance(child, sibling) < 0);
  }

  @Test
  void contextualDistanceDoesNotReuseAnotherContextsResult() {
    var graph = new FakeOperations();
    Concept target = predicate("Target");
    Concept candidate = predicate("Candidate");
    Concept expectedContext = concept("ExpectedContext");
    Concept wrongContext = concept("WrongContext");
    graph.parents.put(candidate, List.of(target));
    graph.inherent.put(candidate, expectedContext);

    var matcher = new SemanticMatcher(graph);
    assertTrue(matcher.semanticDistance(target, candidate, expectedContext) >= 0);
    assertTrue(matcher.semanticDistance(target, candidate, wrongContext) < 0);
    assertTrue(matcher.semanticDistance(target, candidate) < 0);
  }

  @Test
  void inheritedInherencyAcceptsOnlyCandidateSpecializations() {
    var graph = new FakeOperations();
    Concept target = predicate("Target");
    Concept candidate = predicate("Candidate");
    Concept broadInherent = concept("BroadInherent");
    Concept narrowInherent = concept("NarrowInherent");
    graph.parents.put(candidate, List.of(target));
    graph.parents.put(narrowInherent, List.of(broadInherent));
    graph.inherent.put(target, broadInherent);
    graph.inherent.put(candidate, narrowInherent);

    var matcher = new SemanticMatcher(graph);
    assertTrue(matcher.semanticDistance(target, candidate) >= 0);
    assertTrue(matcher.semanticDistance(candidate, target) < 0);
  }

  private static Concept concept(String name) {
    var ret = new ConceptImpl();
    ret.setUrn("test:" + name);
    ret.setNamespace("test");
    ret.setName(name);
    ret.setReferenceName(name.toLowerCase());
    return ret;
  }

  private static Concept predicate(String name) {
    Concept ret = concept(name);
    ret.getType().add(SemanticType.PREDICATE);
    return ret;
  }

  private static final class FakeOperations implements SemanticMatcher.Operations {
    private final Map<Concept, Collection<Concept>> parents = new HashMap<>();
    private final Map<Concept, Concept> inherent = new HashMap<>();
    private long revision;

    @Override
    public Collection<Concept> traits(Semantics concept) {
      return List.of();
    }

    @Override
    public boolean hasTrait(Semantics concept, Concept trait) {
      return false;
    }

    @Override
    public Collection<Concept> roles(Semantics concept) {
      return List.of();
    }

    @Override
    public boolean hasRole(Semantics concept, Concept role) {
      return false;
    }

    @Override
    public Concept directInherent(Semantics concept) {
      return null;
    }

    @Override
    public Concept inherent(Semantics concept) {
      return inherent.get(concept.asConcept());
    }

    @Override
    public Concept goal(Semantics concept) {
      return null;
    }

    @Override
    public Concept cooccurrent(Semantics concept) {
      return null;
    }

    @Override
    public Concept causant(Semantics concept) {
      return null;
    }

    @Override
    public Concept caused(Semantics concept) {
      return null;
    }

    @Override
    public Concept adjacent(Semantics concept) {
      return null;
    }

    @Override
    public Concept compresent(Semantics concept) {
      return null;
    }

    @Override
    public Concept relativeTo(Semantics concept) {
      return null;
    }

    @Override
    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
      return Pair.of(concept.asConcept(), List.of());
    }

    @Override
    public Concept withoutModifiers(Concept concept, Scope scope) {
      return concept;
    }

    @Override
    public boolean is(Semantics concept, Semantics other) {
      return SemanticMatcher.shortestAssertedDistance(concept, other, this::parents) >= 0;
    }

    @Override
    public Collection<Concept> parents(Semantics concept) {
      return parents.getOrDefault(concept.asConcept(), List.of());
    }

    @Override
    public Scope serviceScope() {
      return null;
    }

    @Override
    public long knowledgeRevision() {
      return revision;
    }
  }
}
