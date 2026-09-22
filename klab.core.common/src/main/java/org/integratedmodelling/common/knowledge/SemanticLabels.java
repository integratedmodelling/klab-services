package org.integratedmodelling.common.knowledge;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.atteo.evo.inflector.English;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/** Presentation labels only: never use these as OWL identities or reference identifiers. */
public final class SemanticLabels {
  private SemanticLabels() {}
  private static final Pattern TOKEN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.]*:[a-zA-Z0-9_]+");
  private static final Pattern CLAUSE = Pattern.compile(
      " (?=of |in |to |from |for |with |within |during |caused by |causing |adjacent to |linking |over |greater than |less than |equal to |at least |at most )");

  public static String words(String name) {
    if (name == null || name.isBlank()) return "Concept";
    return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').strip();
  }

  private static String sentence(String label) {
    return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  private static String lower(String label) {
    if (label.length() > 1 && Character.isUpperCase(label.charAt(1))) return label;
    return label.isEmpty() ? label : Character.toLowerCase(label.charAt(0)) + label.substring(1);
  }

  /** Inflect the head noun, leaving clauses such as "of a region" unchanged. */
  public static String plural(String label) {
    var clause = CLAUSE.matcher(label);
    int end = clause.find() ? clause.start() : label.length();
    String head = label.substring(0, end);
    int last = head.lastIndexOf(' ') + 1;
    return head.substring(0, last) + English.plural(head.substring(last)) + label.substring(end);
  }

  /** Compatibility fallback for older concept snapshots without display metadata. */
  public static String expression(String urn) {
    if (urn == null || urn.isBlank()) return "Concept";
    String text = urn.strip();
    if (text.startsWith("each ")) return plural(expression(text.substring(5)));
    var matcher = TOKEN.matcher(text);
    var result = new StringBuilder();
    while (matcher.find()) {
      String token = matcher.group();
      String word = token.startsWith("k.derived:") ? "concept"
          : lower(words(token.substring(token.indexOf(':') + 1)));
      matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(word));
    }
    matcher.appendTail(result);
    return sentence(result.toString());
  }

  public static String singularLabel(Concept concept) {
    for (var key : List.of(Metadata.DISPLAY_LABEL, Metadata.DC_LABEL, Metadata.RDFS_LABEL)) {
      if (concept.getMetadata().get(key) instanceof String label && !label.isBlank()) {
        if (Metadata.DISPLAY_LABEL.equals(key) || Metadata.DC_LABEL.equals(key)) return label;
        // Older unary factories sometimes persisted their generated class ID as rdfs:label.
        if (!label.matches(".*(?:K_DERIVED|[A-Z][A-Z0-9_]*_[0-9]{9}).*"))
          return label.contains(" ") ? label : sentence(words(label));
      }
    }
    if ("k.derived".equals(concept.getNamespace()) || concept.getName() == null
        || concept.getName().matches("[A-Z][A-Z0-9_]*_[0-9]{9}"))
      return expression(concept.getUrn() != null && concept.getUrn().startsWith("each ")
          ? concept.getUrn().substring(5) : concept.getUrn());
    return sentence(words(concept.getName()));
  }

  public static String label(Concept concept) {
    String singular = singularLabel(concept);
    return concept.isCollective() ? plural(singular) : singular;
  }

  public static String unary(UnarySemanticOperator operator, String operand, String comparison) {
    String prefix = switch (operator) {
      case COUNT -> "Number of";
      case RATE -> "Rate of change of";
      case CHANGED -> "Change in";
      default -> sentence(operator.declaration[0]);
    };
    String label = prefix + " " + lower(operator == UnarySemanticOperator.COUNT ? plural(operand) : operand);
    if (comparison != null) label += " " + (operator.declaration.length > 1 ? operator.declaration[1] : "over")
        + " " + lower(comparison);
    return label;
  }

  /** Resolve only atomic labels; generated class IDs never participate in a composed label. */
  public static String describe(KimConcept syntax, Function<String, Concept> resolve, boolean collective) {
    if (syntax == null) return null;
    String base;
    if (syntax.getName() != null) {
      Concept concept = resolve.apply(syntax.getName());
      base = concept == null ? expression(syntax.getName()) : singularLabel(concept);
    } else base = describe(syntax.getObservable(), resolve, true);
    if (base == null) base = "Concept";
    if (syntax.getSemanticModifier() != null)
      base = unary(syntax.getSemanticModifier(), base, describe(syntax.getComparisonConcept(), resolve, true));
    var predicates = new ArrayList<String>();
    for (var trait : syntax.getTraits()) predicates.add(describe(trait, resolve, true));
    for (var role : syntax.getRoles()) predicates.add(describe(role, resolve, true));
    if (!predicates.isEmpty()) base = String.join(" ", predicates) + " " + lower(base);
    if (syntax.isNegated() && syntax.getSemanticModifier() != UnarySemanticOperator.NOT) base = "Not " + lower(base);
    for (var modifier : syntax.getModifiers())
      base += " " + modifier.getFirst().kimDeclaration + " " + lower(describe(modifier.getSecond(), resolve, true));
    for (var operator : syntax.getValueOperators())
      base += " " + valueOperator(operator.getFirst()) + (operator.getSecond() == null ? "" : " " + operand(operator.getSecond(), resolve));
    for (var operand : syntax.getOperands())
      base += (syntax.getExpressionType() == KimConcept.Expression.UNION ? " or " : " and ") + lower(describe(operand, resolve, true));
    return sentence(collective && syntax.isCollective() ? plural(base) : base);
  }

  public static String valueOperator(ValueOperator operator) {
    return switch (operator) {
      case GREATER -> "greater than";
      case LESS -> "less than";
      case GREATEREQUAL -> "at least";
      case LESSEQUAL -> "at most";
      case IS, SAMEAS -> "equal to";
      default -> operator.declaration;
    };
  }

  private static String operand(Object value, Function<String, Concept> resolve) {
    if (value instanceof KimConcept concept) return lower(describe(concept, resolve, true));
    if (value instanceof Semantics semantics) return lower(semantics.displayLabel());
    return String.valueOf(value);
  }
}
