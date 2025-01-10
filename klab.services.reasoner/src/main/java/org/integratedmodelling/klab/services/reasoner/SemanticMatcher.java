package org.integratedmodelling.klab.services.reasoner;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.ResourcesService;

/**
 * Computes semantic distance between concepts, with configurable caching. Clients should also
 * provide similar caching to minimize network traffic.
 *
 * TODO integrate within ReasonerService and remove the corresponding code from there.
 */
public class SemanticMatcher {

  private final ReasonerService reasonerService;
  private final ResourcesService resourcesService;

  /**
   * Cache for non-contextual matching with inherency=true and no abstract predicates incarnation
   */
  private final LoadingCache<Pair<Semantics, Semantics>, Integer> binaryMatchCache =
      CacheBuilder.newBuilder()
          .concurrencyLevel(20)
          .maximumSize(400) // TODO configure
          .build(
              new CacheLoader<>() {
                @Override
                public Integer load(Pair<Semantics, Semantics> key) throws Exception {
                  return computeSemanticDistance(key.getFirst(), key.getSecond());
                }
              });

  /**
   * Cache for contextual matching with inherency=true and no abstract predicates incarnation
   */
  private final LoadingCache<Triple<Semantics, Semantics, Semantics>, Integer> ternaryMatchCache =
      CacheBuilder.newBuilder()
          .concurrencyLevel(20)
          .maximumSize(400) // TODO configure
          .build(
              new CacheLoader<>() {
                @Override
                public Integer load(Triple<Semantics, Semantics, Semantics> key) throws Exception {
                  return computeSemanticDistance(key.getFirst(), key.getSecond());
                }
              });

  private Integer computeSemanticDistance(Semantics first, Semantics second) {
    return semanticDistance(first, second);
  }

  public SemanticMatcher(ReasonerService reasonerService, ResourcesService resourcesService) {
    this.reasonerService = reasonerService;
    this.resourcesService = resourcesService;
  }

  // TODO use cache except in special cases
  public int semanticDistance(Semantics target, Semantics other) {
    return semanticDistance(target.asConcept(), other.asConcept(), null, true, null);
  }

  // TODO use cache except in special cases
  public int semanticDistance(Semantics target, Semantics other, Semantics context) {
    return semanticDistance(
        target.asConcept(),
        other.asConcept(),
        context == null ? null : context.asConcept(),
        true,
        null);
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
      Map<Concept, Concept> resolvedAbstractPredicates) {

    int distance = 0;

    // String resolving = this.getDefinition();
    // String resolved = concept.getDefinition();
    // System.out.println("Does " + resolving + " resolve " + resolved + "?");

    int mainDistance =
        coreDistance(from, to, context, compareInherency, resolvedAbstractPredicates);
    distance += mainDistance * 50;
    if (distance < 0) {
      return distance;
    }

    // should have all the same traits - additional traits are allowed only
    // in contextual types
    Set<Concept> acceptedTraits = new HashSet<>();
    for (Concept t : reasonerService.traits(from)) {
      if (t.isAbstract()
          && resolvedAbstractPredicates != null
          && resolvedAbstractPredicates.containsKey(t)) {
        distance += assertedDistance(resolvedAbstractPredicates.get(t), t);
        acceptedTraits.add(resolvedAbstractPredicates.get(t));
      } else {
        boolean ok = reasonerService.hasTrait(to, t);
        if (!ok) {
          return -50;
        }
      }
    }

    for (Concept t : reasonerService.traits(to)) {
      if (!acceptedTraits.contains(t) && !reasonerService.hasTrait(from, t)) {
        return -50;
      }
    }

    // same with roles.
    Set<Concept> acceptedRoles = new HashSet<>();
    for (Concept t : reasonerService.roles(from)) {
      if (t.isAbstract()
          && resolvedAbstractPredicates != null
          && resolvedAbstractPredicates.containsKey(t)) {
        distance += assertedDistance(resolvedAbstractPredicates.get(t), t);
        acceptedRoles.add(resolvedAbstractPredicates.get(t));
      } else {
        boolean ok = reasonerService.hasRole(to, t);
        if (!ok) {
          return -50;
        }
      }
    }

    for (Concept t : reasonerService.roles(to)) {
      if (!acceptedRoles.contains(t) && !reasonerService.hasRole(from, t)) {
        return -50;
      }
    }

    int component;

    if (compareInherency) {
      /*
       * any EXPLICIT inherency must be the same in both.
       */
      Concept ourExplicitInherent = reasonerService.directInherent(from);
      Concept itsExplicitInherent = reasonerService.directInherent(to);

      if (ourExplicitInherent != null || itsExplicitInherent != null) {
        if (ourExplicitInherent != null && itsExplicitInherent != null) {
          component = distance(ourExplicitInherent, itsExplicitInherent, true);

          if (component < 0) {
            double d = ((double) component / 10.0);
            return -1 * (int) (d > 10 ? d : 10);
          }
          distance += component;
        } else {
          return -50;
        }
      }

      /*
       * inherency must be same (theirs is ours) unless our inherent type is abstract
       */
      Concept ourInherent = reasonerService.inherent(from);
      Concept itsInherent = reasonerService.inherent(to);

      if (ourInherent != null || itsInherent != null) {

        if (ourInherent != null && ourInherent.isAbstract()) {
          component = distance(ourInherent, itsInherent, false);
        } else if (ourInherent == null && itsInherent != null && context != null) {
          /*
           * Situations like: does XXX resolve YYY of ZZZ when ZZZ is the context.
           */
          component = distance(context, itsInherent, false);
        } else {
          component = distance(itsInherent, ourInherent, false);
        }

        if (component < 0) {
          double d = ((double) component / 10.0);
          return -1 * (int) (d > 10 ? d : 10);
        }
        distance += component;
      }
    }

    component = distance(reasonerService.goal(from), reasonerService.goal(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.cooccurrent(from), reasonerService.cooccurrent(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.causant(from), reasonerService.causant(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.caused(from), reasonerService.caused(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.adjacent(from), reasonerService.adjacent(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.compresent(from), reasonerService.compresent(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    component = distance(reasonerService.relativeTo(from), reasonerService.relativeTo(to), false);
    if (component < 0) {
      double d = ((double) component / 10.0);
      return -1 * (int) (d > 10 ? d : 10);
    }
    distance += component;

    return distance;
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
      Map<Concept, Concept> resolvedAbstractPredicates) {

    if (from == to || from.equals(to)) {
      return 0;
    }

    Pair<Concept, List<SemanticType>> c1ops = reasonerService.splitOperators(from);
    Pair<Concept, List<SemanticType>> c2ops = reasonerService.splitOperators(to);

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
          resolvedAbstractPredicates);
    }

    Concept core1 = reasonerService.coreObservable(c1ops.getFirst());
    Concept core2 = reasonerService.coreObservable(c2ops.getFirst());

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
    return core1.equals(core2)
        ? assertedDistance(from, to)
        : (assertedDistance(from, to) == 0 ? 0 : -1);
  }

  private int distance(Concept from, Concept to, boolean acceptAbsent) {

    int ret = 0;
    if (from == null && to != null) {
      ret = acceptAbsent ? 50 : -50;
    } else if (from != null && to == null) {
      ret = -50;
    } else if (from != null && to != null) {
      ret = reasonerService.is(to, from) ? assertedDistance(to, from) : -100;
      if (ret >= 0) {
        for (Concept t : reasonerService.traits(from)) {
          boolean ok = reasonerService.hasTrait(to, t);
          if (!ok) {
            return -50;
          }
        }
        for (Concept t : reasonerService.traits(to)) {
          if (!reasonerService.hasTrait(from, t)) {
            ret += 10;
          }
        }
      }
    }

    return ret > 100 ? 100 : ret;
  }

  public int assertedDistance(Semantics from, Semantics to) {

    if (from == to || from.equals(to)) {
      return 0;
    }
    int ret = 1;
    while (true) {
      Collection<Concept> parents = reasonerService.parents(from);
      if (parents.isEmpty()) {
        break;
      }
      if (parents.contains(to)) {
        return ret;
      }
      for (Concept parent : parents) {
        int d = assertedDistance(from, parent);
        if (d >= 0) {
          return ret + d;
        }
      }
      ret++;
    }
    return -1;
  }
}
