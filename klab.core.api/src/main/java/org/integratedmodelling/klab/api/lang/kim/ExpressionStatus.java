package org.integratedmodelling.klab.api.lang.kim;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/** Abstract head status and transitive subjective status, evaluated without mutating syntax. */
public final class ExpressionStatus {
  private ExpressionStatus() {}

  public static Set<SemanticType> evaluate(KimConcept syntax) {
    return evaluate(syntax, ExpressionStatus::declarationTypes);
  }

  /** The leaf lookup may supply resolved declaration status instead of syntax descriptors. */
  public static Set<SemanticType> evaluate(
      KimConcept syntax, Function<KimConcept, Set<SemanticType>> leafTypes) {
    return evaluate(syntax, leafTypes, null);
  }

  /** A semantic backend may evaluate the whole qualified head, preserving identity scope. */
  public static Set<SemanticType> evaluate(
      KimConcept syntax, Function<KimConcept, Set<SemanticType>> leafTypes,
      java.util.function.Predicate<KimConcept> abstractHead) {
    var result = abstractStatus(syntax, leafTypes, abstractHead);
    if (isSubjective(syntax, leafTypes, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()))) {
      result.add(SemanticType.SUBJECTIVE);
    }
    return result;
  }

  private static Set<SemanticType> abstractStatus(
      KimConcept syntax, Function<KimConcept, Set<SemanticType>> leafTypes,
      java.util.function.Predicate<KimConcept> abstractHead) {
    var result = EnumSet.noneOf(SemanticType.class);
    if (syntax.getSemanticModifier() == null) {
      if (abstractHead != null) {
        if (abstractHead.test(syntax)) result.add(SemanticType.ABSTRACT);
      } else {
        result.addAll(syntax.getObservable() == null
            ? leafTypes.apply(syntax) : abstractStatus(syntax.getObservable(), leafTypes, null));
      }
    }
    for (var attribute : syntax.getTraits()) {
      if (attribute.is(SemanticType.ATTRIBUTE)) {
        result.addAll(abstractStatus(attribute, leafTypes, abstractHead));
      }
    }
    result.retainAll(EnumSet.of(SemanticType.ABSTRACT));
    return result;
  }

  public static boolean isSubjective(Object value) {
    return isSubjective(value, ExpressionStatus::declarationTypes);
  }

  /** Declaration flags exclude subjectivity cached from a removed expression component. */
  public static Set<SemanticType> declarationTypes(KimConcept concept) {
    var result = EnumSet.noneOf(SemanticType.class);
    result.addAll(concept.getType());
    if (concept instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl impl
        && impl.getDeclaredSubjective() != null) {
      result.remove(SemanticType.SUBJECTIVE);
      if (impl.getDeclaredSubjective()) result.add(SemanticType.SUBJECTIVE);
    }
    return result;
  }

  /** Resolve declaration flags inside description values as well as semantic expressions. */
  public static boolean isSubjective(Object value, Function<KimConcept, Set<SemanticType>> leafTypes) {
    return isSubjective(value, leafTypes,
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
  }

  private static boolean isSubjective(Object value,
      Function<KimConcept, Set<SemanticType>> leafTypes, Set<Object> visited) {
    if (value == null || !visited.add(value)) return false;
    if (value instanceof KimConcept c) {
      if (c.getObservable() == null && leafTypes.apply(c).contains(SemanticType.SUBJECTIVE)) return true;
      return isSubjective(c.getObservable(), leafTypes, visited)
          || isSubjective(c.getTraits(), leafTypes, visited)
          || isSubjective(c.getRoles(), leafTypes, visited)
          || isSubjective(c.getAbout(), leafTypes, visited)
          || isSubjective(c.getOperands(), leafTypes, visited)
          || isSubjective(c.getModifiers(), leafTypes, visited)
          || isSubjective(c.getComparisonConcept(), leafTypes, visited)
          || isSubjective(c.getParent(), leafTypes, visited)
          || isSubjective(c.getValueOperators(), leafTypes, visited);
    }
    if (value instanceof KimObservable o) {
      return isSubjective(o.getSemantics(), leafTypes, visited)
          || isSubjective(o.getValue(), leafTypes, visited)
          || isSubjective(o.getDefaultValue(), leafTypes, visited)
          || isSubjective(o.getValueOperators(), leafTypes, visited);
    }
    if (value instanceof org.integratedmodelling.klab.api.knowledge.Observable o)
      return o.is(SemanticType.SUBJECTIVE)
          || isSubjective(o.getValue(), leafTypes, visited)
          || isSubjective(o.getDefaultValue(), leafTypes, visited);
    if (value instanceof org.integratedmodelling.klab.api.knowledge.Semantics s)
      return s.is(SemanticType.SUBJECTIVE);
    if (value instanceof org.integratedmodelling.klab.api.collections.Pair<?, ?> p)
      return isSubjective(p.getFirst(), leafTypes, visited) || isSubjective(p.getSecond(), leafTypes, visited);
    if (value instanceof java.util.Map<?, ?> map)
      return isSubjective(map.keySet(), leafTypes, visited) || isSubjective(map.values(), leafTypes, visited);
    if (value instanceof Iterable<?> items) {
      for (var item : items) if (isSubjective(item, leafTypes, visited)) return true;
    }
    if (value instanceof Object[] items) {
      for (var item : items) if (isSubjective(item, leafTypes, visited)) return true;
    }
    return false;
  }
}
