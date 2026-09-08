package org.integratedmodelling.klab.services.reasoner;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Structural matching with branch-local captures. An unsupported field never becomes a wildcard. */
final class ObservationPatternMatcher {
  private final Reasoner reasoner;
  private final Function<KimObservable, Observable> declare;

  ObservationPatternMatcher(Reasoner reasoner, Function<KimObservable, Observable> declare) {
    this.reasoner = reasoner;
    this.declare = declare;
  }

  boolean match(PatternExpression pattern, Object value, Map<String, Object> captures) {
    var trial = new LinkedHashMap<>(captures);
    if (!test(pattern, value, trial)) return false;
    captures.clear();
    captures.putAll(trial);
    return true;
  }

  private boolean test(PatternExpression pattern, Object value, Map<String, Object> captures) {
    if (pattern instanceof AnyPattern) return value != null;
    if (pattern instanceof AbsentPattern) return value == null || value instanceof Collection<?> c && c.isEmpty();
    if (pattern instanceof PresencePattern p) return value != null
        && (!(value instanceof Collection<?> c) || !c.isEmpty()) && match(p.getOperand(), value, captures);
    if (pattern instanceof CapturePattern p) {
      if (!match(p.getOperand(), value, captures)) return false;
      String name = ObservationReasoner.name(p.getName());
      if (name.equals("this") || name.equals("context")) throw new IllegalArgumentException("Reserved capture " + name);
      if (captures.containsKey(name)) throw new IllegalArgumentException("Duplicate capture " + name + "; use same");
      captures.put(name, value);
      return true;
    }
    if (pattern instanceof SamePattern p) {
      String name = ObservationReasoner.name(p.getName());
      if (!captures.containsKey(name)) throw new IllegalArgumentException("Unbound capture " + name);
      return Objects.equals(captures.get(name), value);
    }
    if (pattern instanceof NotPattern p) return !match(p.getOperand(), value, new LinkedHashMap<>(captures));
    if (pattern instanceof BooleanPattern p) {
      for (var operand : p.getOperands()) {
        boolean matched = match(operand, value, captures);
        if (p.getMode() == PatternBooleanMode.ALL && !matched) return false;
        if (p.getMode() == PatternBooleanMode.EITHER && matched) return true;
      }
      return p.getMode() == PatternBooleanMode.ALL;
    }
    if (pattern instanceof ScalarPattern p) return Objects.equals(scalar(p.getValue()), value);
    if (pattern instanceof CollectionPattern p) {
      if (!(value instanceof Collection<?> collection)) return false;
      for (var element : collection) {
        boolean matched = match(p.getElement(), element, captures);
        if (p.getQuantifier() == CollectionQuantifier.CONTAINS && matched) return true;
        if (p.getQuantifier() == CollectionQuantifier.EVERY && !matched) return false;
      }
      return p.getQuantifier() == CollectionQuantifier.EVERY;
    }
    if (pattern instanceof ExactCollectionPattern p) {
      if (!(value instanceof Collection<?> c) || c.size() != p.getElements().size()) return false;
      return unordered(p.getElements(), new ArrayList<>(c), 0, captures, null);
    }
    if (!(value instanceof Semantics semantics)) return false;
    if (pattern instanceof SemanticPatternTest p) {
      var expected = declare.apply(p.getObservable().getObservable());
      return p.getRelation() == SemanticMatchRelation.IS ? reasoner.is(semantics, expected)
          : Objects.equals(semantics.getUrn(), expected.getUrn());
    }
    if (pattern instanceof NodePattern p) {
      for (var field : p.getFields()) if (!field(field, semantics, captures)) return false;
      return true;
    }
    if (pattern instanceof LogicalPattern p) {
      var type = p.getConnector() == LogicalPatternConnector.OR ? SemanticType.UNION : SemanticType.INTERSECTION;
      if (!semantics.is(type)) return false;
      List<Object> operands = new ArrayList<>(reasoner.operands(semantics));
      operands.sort(Comparator.comparing(o -> ((Semantics) o).getUrn()));
      if (p.getRemainder() == null ? operands.size() != p.getOperands().size()
          : operands.size() <= p.getOperands().size()) return false;
      if (p.getOrdering() == OperandOrdering.UNORDERED)
        return unordered(p.getOperands(), operands, 0, captures, p);
      for (int i = 0; i < p.getOperands().size(); i++)
        if (!match(p.getOperands().get(i), operands.get(i), captures)) return false;
      return remainder(p, operands.subList(p.getOperands().size(), operands.size()), captures);
    }
    throw new UnsupportedOperationException("Pattern " + pattern.getClass().getSimpleName());
  }

  private boolean unordered(List<PatternExpression> patterns, List<Object> remaining, int index,
      Map<String, Object> captures, LogicalPattern logical) {
    if (index == patterns.size()) return logical == null ? remaining.isEmpty() : remainder(logical, remaining, captures);
    for (int i = 0; i < remaining.size(); i++) {
      var trial = new LinkedHashMap<>(captures);
      if (!match(patterns.get(index), remaining.get(i), trial)) continue;
      var rest = new ArrayList<>(remaining);
      rest.remove(i);
      if (unordered(patterns, rest, index + 1, trial, logical)) {
        captures.clear(); captures.putAll(trial); return true;
      }
    }
    return false;
  }

  private boolean remainder(LogicalPattern pattern, List<Object> values, Map<String, Object> captures) {
    if (pattern.getRemainder() == null) return values.isEmpty();
    String name = ObservationReasoner.name(pattern.getRemainder().getName());
    if (captures.containsKey(name)) throw new IllegalArgumentException("Duplicate/reserved remainder " + name);
    if (values.isEmpty()) return false;
    Object value = values.size() == 1 ? values.getFirst() : reasoner.resolveConcept(values.stream()
        .map(o -> "(" + ((Semantics) o).getUrn() + ")")
        .collect(Collectors.joining(pattern.getConnector() == LogicalPatternConnector.OR ? " or " : " and ")));
    if (value == null) return false;
    captures.put(name, value);
    return true;
  }

  private boolean field(PatternField field, Semantics semantics, Map<String, Object> captures) {
    if (field instanceof KindPatternField f) return f.getValue().getKinds().stream()
        .anyMatch(kind -> semantics.is(SemanticType.valueOf(kind.name())));
    if (field instanceof ActivityPatternField f) return f.getActivity() == (semantics instanceof Observable o
        ? o.getContextualization() : semantics.asConcept().getDescriptionType());
    if (field instanceof FlagPatternField f) {
      boolean value = switch (f.getFlag()) {
        case COLLECTIVE -> semantics.asConcept().isCollective();
        case ABSTRACT -> semantics.isAbstract();
        case NEGATED -> throw new UnsupportedOperationException("Negation requires a syntax projection");
      };
      return Objects.equals(f.getValue(), value);
    }
    if (field instanceof ChildPatternField f) {
      Object child = switch (f.getChild()) {
        case HEAD -> reasoner.coreObservable(semantics);
        case PREDICATES -> reasoner.directTraits(semantics);
        case ROLES -> reasoner.directRoles(semantics);
        case INHERENT -> reasoner.directInherent(semantics);
        default -> throw new UnsupportedOperationException("Pattern child " + f.getChild());
      };
      return match(f.getValue(), child, captures);
    }
    throw new UnsupportedOperationException("Pattern field " + field.getClass().getSimpleName());
  }

  static Object scalar(StrategyScalar scalar) {
    if (scalar.getBooleanValue() != null) return scalar.getBooleanValue();
    if (scalar.getNumber() != null) return scalar.getNumber();
    return scalar.getText();
  }
}
