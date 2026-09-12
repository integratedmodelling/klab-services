package org.integratedmodelling.klab.api.lang;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/** Collect syntax annotations before semantic canonicalization loses declaration order. */
public final class AnnotationCollector {
  private AnnotationCollector() {}

  /** Later contributors replace earlier annotations with the same name. */
  @SafeVarargs
  public static List<Annotation> merge(Collection<Annotation>... contributors) {
    Map<String, Annotation> result = new LinkedHashMap<>();
    for (var contributor : contributors) {
      if (contributor != null) {
        for (var annotation : contributor) {
          result.put(annotation.getName(), new AnnotationImpl(annotation));
        }
      }
    }
    return new ArrayList<>(result.values());
  }

  public static List<Annotation> collect(
      KimConcept syntax, Function<String, Concept> concepts) {
    return collect(syntax, concepts, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static List<Annotation> collect(
      KimConcept syntax, Function<String, Concept> concepts, Set<KimConcept> visiting) {
    if (syntax == null || !visiting.add(syntax)) return List.of();
    List<Annotation> result = new ArrayList<>();
    if (syntax.getObservable() != null) {
      result = collect(syntax.getObservable(), concepts, visiting);
    } else if (syntax.getName() != null) {
      var concept = concepts.apply(syntax.getName());
      if (concept != null) result = merge(concept.getAnnotations());
    }
    result = merge(result, syntax.getAnnotations());
    // Include concepts in semantic clauses and logical/operator operands as well.
    var components = new ArrayList<>(syntax.getOperands());
    for (var modifier : syntax.getModifiers()) components.add(modifier.getSecond());
    if (syntax.getComparisonConcept() != null) components.add(syntax.getComparisonConcept());
    components.sort(Comparator.comparingInt(KimConcept::getOffsetInDocument));
    for (var component : components.reversed()) {
      result = merge(result, collect(component, concepts, visiting));
    }
    // Source offsets retain interleaved trait/role order across the two syntactic lists.
    var predicates = new ArrayList<>(syntax.getTraits());
    predicates.addAll(syntax.getRoles());
    predicates.sort(Comparator.comparingInt(KimConcept::getOffsetInDocument));
    for (var predicate : predicates.reversed()) {
      result = merge(result, collect(predicate, concepts, visiting));
    }
    visiting.remove(syntax);
    return result;
  }
}
