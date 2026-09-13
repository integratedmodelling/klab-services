package org.integratedmodelling.klab.api.lang.kim;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/** Status of an expression's head and direct attributes, excluding clause fillers. */
public final class ExpressionStatus {
  private ExpressionStatus() {}

  public static Set<SemanticType> evaluate(KimConcept syntax) {
    return evaluate(syntax, KimConcept::getType);
  }

  /** The leaf lookup may supply resolved declaration status instead of syntax descriptors. */
  public static Set<SemanticType> evaluate(
      KimConcept syntax, Function<KimConcept, Set<SemanticType>> leafTypes) {
    var result = EnumSet.noneOf(SemanticType.class);
    if (syntax.getSemanticModifier() == null) {
      result.addAll(syntax.getObservable() == null
          ? leafTypes.apply(syntax) : evaluate(syntax.getObservable(), leafTypes));
    }
    for (var attribute : syntax.getTraits()) {
      if (attribute.is(SemanticType.ATTRIBUTE)) {
        result.addAll(evaluate(attribute, leafTypes));
      }
    }
    result.retainAll(EnumSet.of(SemanticType.ABSTRACT, SemanticType.SUBJECTIVE));
    return result;
  }
}
