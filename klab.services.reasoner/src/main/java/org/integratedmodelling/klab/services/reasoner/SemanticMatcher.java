package org.integratedmodelling.klab.services.reasoner;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;

/**
 * Computes semantic distance between concepts, with configurable caching. Clients should also
 * provide similar caching to minimize network traffic.
 */
public class SemanticMatcher {

  interface Operations {
    Collection<Concept> traits(Semantics concept);

    boolean hasTrait(Semantics concept, Concept trait);

    Collection<Concept> roles(Semantics concept);

    boolean hasRole(Semantics concept, Concept role);

    Concept directInherent(Semantics concept);

    Concept inherent(Semantics concept);

    Concept goal(Semantics concept);

    Concept cooccurrent(Semantics concept);

    Concept causant(Semantics concept);

    Concept caused(Semantics concept);

    Concept adjacent(Semantics concept);

    Concept compresent(Semantics concept);

    Concept relativeTo(Semantics concept);

    Pair<Concept, List<SemanticType>> splitOperators(Semantics concept);

    Concept withoutModifiers(Concept concept, Scope scope);

    boolean is(Semantics concept, Semantics other);

    Collection<Concept> parents(Semantics concept);

    Scope serviceScope();

    long knowledgeRevision();
  }

  private static final class ServiceOperations implements Operations {
    private final ReasonerService service;

    private ServiceOperations(ReasonerService service) {
      this.service = service;
    }

    public Collection<Concept> traits(Semantics concept) {
      return service.traits(concept);
    }

    public boolean hasTrait(Semantics concept, Concept trait) {
      return service.hasTrait(concept, trait);
    }

    public Collection<Concept> roles(Semantics concept) {
      return service.roles(concept);
    }

    public boolean hasRole(Semantics concept, Concept role) {
      return service.hasRole(concept, role);
    }

    public Concept directInherent(Semantics concept) {
      return service.directInherent(concept);
    }

    public Concept inherent(Semantics concept) {
      return service.inherent(concept);
    }

    public Concept goal(Semantics concept) {
      return service.goal(concept);
    }

    public Concept cooccurrent(Semantics concept) {
      return service.cooccurrent(concept);
    }

    public Concept causant(Semantics concept) {
      return service.causant(concept);
    }

    public Concept caused(Semantics concept) {
      return service.caused(concept);
    }

    public Concept adjacent(Semantics concept) {
      return service.adjacent(concept);
    }

    public Concept compresent(Semantics concept) {
      return service.compresent(concept);
    }

    public Concept relativeTo(Semantics concept) {
      return service.relativeTo(concept);
    }

    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
      return service.splitOperators(concept);
    }

    public Concept withoutModifiers(Concept concept, Scope scope) {
      return SemanticsBuilder.create(concept, service, scope)
          .without(SemanticRole.modifiers())
          .buildConcept();
    }

    public boolean is(Semantics concept, Semantics other) {
      return service.is(concept, other);
    }

    public Collection<Concept> parents(Semantics concept) {
      return service.parents(concept);
    }

    public Scope serviceScope() {
      return service.serviceScope();
    }

    public long knowledgeRevision() {
      return service.knowledgeRevision();
    }
  }

  private record DistanceKey(long revision, Concept target, Concept other, Concept context) {}

  private record HierarchyKey(long revision, Semantics from, Semantics to) {}

  private final Operations operations;

  /**
   * Cache for non-contextual matching with inherency=true and no abstract predicates incarnation
   */
  private final Cache<DistanceKey, Integer> distanceCache =
      Caffeine.newBuilder().maximumSize(10_000).recordStats().build();

  private final Cache<HierarchyKey, Integer> assertedDistanceCache =
      Caffeine.newBuilder().maximumSize(20_000).recordStats().build();

  public SemanticMatcher(ReasonerService reasonerService) {
    this(new ServiceOperations(reasonerService));
  }

  SemanticMatcher(Operations operations) {
    this.operations = operations;
  }

  public int semanticDistance(Semantics target, Semantics other) {
    return semanticDistance(target, other, null);
  }

  public int semanticDistance(Semantics target, Semantics other, Semantics context) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(other, "other");
    Concept targetConcept = target.asConcept();
    Concept otherConcept = other.asConcept();
    Concept contextConcept = context == null ? null : context.asConcept();
    var key =
        new DistanceKey(
            operations.knowledgeRevision(), targetConcept, otherConcept, contextConcept);
    return distanceCache.get(
        key,
        ignored ->
            semanticDistance(
                targetConcept,
                otherConcept,
                contextConcept,
                true,
                null,
                operations.serviceScope()));
  }

  /**
   * The workhorse of semantic distance computation can also consider any predicates that were
   * abstract in the lineage of the passed concept (i.e. the concept is the result of a query with
   * the abstract predicates, which has been contextualized to incarnate them into the passed
   * correspondence with concrete counterparts). In that case, and only in that case, the distance
   * between a concrete candidate and one that contains its predicates in the abstract form can be
   * positive, i.e. a concept with abstract predicates can resolve one with concrete subclasses as
   * long as the lineage contains its resolution.
   *
   * <p>Remains public to address special situations when we have abstract resolutions or special
   * needs about inherency.
   *
   * @param to
   * @param context
   * @param compareInherency
   * @param resolvedAbstractPredicates
   * @return
   */
  public int semanticDistance(
      Concept from,
      Concept to,
      Concept context,
      boolean compareInherency,
      Map<Concept, Concept> resolvedAbstractPredicates,
      Scope scope) {

    if (from.getUrn().equals(to.getUrn()) && context == null) {
      return 0;
    }

    int distance = 0;

    int mainDistance =
        coreDistance(from, to, context, compareInherency, resolvedAbstractPredicates, scope);
    distance += mainDistance * 50;
    if (distance < 0) {
      return distance;
    }

    // should have all the same traits - additional traits are allowed only
    // in contextual types
    Set<Concept> acceptedTraits = new HashSet<>();
    for (Concept t : operations.traits(from)) {
      if (t.isAbstract()
          && resolvedAbstractPredicates != null
          && resolvedAbstractPredicates.containsKey(t)) {
        int predicateDistance = assertedDistance(resolvedAbstractPredicates.get(t), t);
        if (predicateDistance < 0) {
          return -50;
        }
        distance += predicateDistance;
        acceptedTraits.add(resolvedAbstractPredicates.get(t));
      } else {
        boolean ok = operations.hasTrait(to, t);
        if (!ok) {
          return -50;
        }
      }
    }

    for (Concept t : operations.traits(to)) {
      if (!acceptedTraits.contains(t) && !operations.hasTrait(from, t)) {
        return -50;
      }
    }

    // same with roles.
    Set<Concept> acceptedRoles = new HashSet<>();
    for (Concept t : operations.roles(from)) {
      if (t.isAbstract()
          && resolvedAbstractPredicates != null
          && resolvedAbstractPredicates.containsKey(t)) {
        int predicateDistance = assertedDistance(resolvedAbstractPredicates.get(t), t);
        if (predicateDistance < 0) {
          return -50;
        }
        distance += predicateDistance;
        acceptedRoles.add(resolvedAbstractPredicates.get(t));
      } else {
        boolean ok = operations.hasRole(to, t);
        if (!ok) {
          return -50;
        }
      }
    }

    for (Concept t : operations.roles(to)) {
      if (!acceptedRoles.contains(t) && !operations.hasRole(from, t)) {
        return -50;
      }
    }

    int component;

    if (compareInherency) {
      /*
       * any EXPLICIT inherency must be the same in both.
       */
      Concept ourExplicitInherent = operations.directInherent(from);
      Concept itsExplicitInherent = operations.directInherent(to);

      if (ourExplicitInherent != null || itsExplicitInherent != null) {
        if (ourExplicitInherent != null && itsExplicitInherent != null) {
          component = distance(ourExplicitInherent, itsExplicitInherent, true);

          if (component < 0) {
            return normalizedIncompatibility(component);
          }
          distance += component;
        } else {
          return -50;
        }
      }

      /*
       * inherency must be same (theirs is ours) unless our inherent type is abstract
       */
      Concept ourInherent = operations.inherent(from);
      Concept itsInherent = operations.inherent(to);

      if (ourInherent != null || itsInherent != null) {

        if (ourInherent != null && ourInherent.isAbstract()) {
          component = distance(ourInherent, itsInherent, false);
        } else if (ourInherent == null && context != null) {
          /*
           * Situations like: does XXX resolve YYY of ZZZ when ZZZ is the context.
           */
          component = distance(context, itsInherent, false);
        } else {
          component = distance(ourInherent, itsInherent, false);
        }

        if (component < 0) {
          return normalizedIncompatibility(component);
        }
        distance += component;
      }
    }

    component = distance(operations.goal(from), operations.goal(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.cooccurrent(from), operations.cooccurrent(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.causant(from), operations.causant(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.caused(from), operations.caused(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.adjacent(from), operations.adjacent(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.compresent(from), operations.compresent(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    component = distance(operations.relativeTo(from), operations.relativeTo(to), false);
    if (component < 0) {
      return normalizedIncompatibility(component);
    }
    distance += component;

    return distance;
  }

  private static int normalizedIncompatibility(int component) {
    return -Math.max(10, Math.abs(component) / 10);
  }

  /**
   * Get the distance between the core described observables after factoring out all operators and
   * ensuring they are the same. If not the same, the concepts are incompatible and the distance is
   * negative.
   *
   * @param to
   * @return
   */
  public int coreDistance(
      Concept from,
      Concept to,
      Concept context,
      boolean compareInherency,
      Map<Concept, Concept> resolvedAbstractPredicates,
      Scope scope) {

    if (from == to || from.equals(to)) {
      return 0;
    }

    Pair<Concept, List<SemanticType>> c1ops = operations.splitOperators(from);
    Pair<Concept, List<SemanticType>> c2ops = operations.splitOperators(to);

    if (!c1ops.getSecond().equals(c2ops.getSecond())) {
      return -50;
    }

    if (!c1ops.getSecond().isEmpty()) {
      /*
       * if operators were extracted, the distance must take into account traits and
       * the like for the concepts they describe, so call the main method again, which
       * will call this and perform the core check below.
       */
      return semanticDistance(
          c1ops.getFirst(),
          c2ops.getFirst(),
          context,
          compareInherency,
          resolvedAbstractPredicates,
          scope);
    }

    var core1 = operations.withoutModifiers(c1ops.getFirst(), scope);
    var core2 = operations.withoutModifiers(c2ops.getFirst(), scope);

    /*
     * FIXME this must check: have operator ? (operator == operator && coreObs ==
     * coreObs) : coreObs == coreObs;
     */

    if (core1 == null || core2 == null) {
      return -100;
    }

    if (!from.is(SemanticType.PREDICATE) && !core1.equals(core2)) {
      /*
       * in order to resolve an observation, the core observables must be equal;
       * subsumption is not OK (lidar elevation does not resolve elevation as it
       * creates different observations; same for different observation techniques -
       * easy strategy to annotate techs that make measurements incompatible = use a
       * subclass instead of a related trait).
       *
       * Predicates are unique in being able to resolve a more specific predicate.
       */
      return -50;
    }

    /**
     * Previously returning the distance, which does not work unless the core observables are the
     * same (differentiated by predicates only) - which for example makes identities under 'type of'
     * be compatible no matter the identity.
     */
    int hierarchyDistance = assertedDistance(to, from);
    return hierarchyDistance;
  }

  private int distance(Concept from, Concept to, boolean acceptAbsent) {

    int ret = 0;
    if (from == null && to != null) {
      ret = acceptAbsent ? 50 : -50;
    } else if (from != null && to == null) {
      ret = -50;
    } else if (from != null) {
      ret = operations.is(to, from) ? assertedDistance(to, from) : -100;
      if (ret >= 0) {
        for (Concept t : operations.traits(from)) {
          boolean ok = operations.hasTrait(to, t);
          if (!ok) {
            return -50;
          }
        }
        for (Concept t : operations.traits(to)) {
          if (!operations.hasTrait(from, t)) {
            ret += 10;
          }
        }
      }
    }

    return Math.min(ret, 100);
  }

  public int assertedDistance(Semantics from, Semantics to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    var key = new HierarchyKey(operations.knowledgeRevision(), from, to);
    return assertedDistanceCache.get(
        key, ignored -> shortestAssertedDistance(from, to, operations::parents));
  }

  static int shortestAssertedDistance(
      Semantics from, Semantics to, Function<Semantics, Collection<Concept>> parentProvider) {
    if (from.equals(to)) {
      return 0;
    }
    Set<Concept> visited = new HashSet<>();
    ArrayDeque<Pair<Concept, Integer>> queue = new ArrayDeque<>();
    visited.add(from.asConcept());
    queue.add(Pair.of(from.asConcept(), 0));
    while (!queue.isEmpty()) {
      Pair<Concept, Integer> current = queue.removeFirst();
      for (Concept parent : parentProvider.apply(current.getFirst())) {
        if (parent.equals(to.asConcept())) {
          return current.getSecond() + 1;
        }
        if (visited.add(parent)) {
          queue.addLast(Pair.of(parent, current.getSecond() + 1));
        }
      }
    }
    return -1;
  }

  public void resetCaches() {
    distanceCache.invalidateAll();
    assertedDistanceCache.invalidateAll();
  }
}
