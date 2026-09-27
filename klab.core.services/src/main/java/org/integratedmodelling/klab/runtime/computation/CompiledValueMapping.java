package org.integratedmodelling.klab.runtime.computation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimDate;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Immutable, thread-safe executable form of a transported classification or lookup table. */
public final class CompiledValueMapping {

  public static final String CACHE_SIZE_PROPERTY = "klab.runtime.valueMapping.cache.maxSize";
  public static final long DEFAULT_CACHE_SIZE = 16_384;
  private static final int NO_MATCH = -1;

  public record Statistics(long hits, long misses, long evictions, long size) {}

  @FunctionalInterface
  private interface Matcher {
    boolean matches(Object value, ContextScope scope);
  }

  @FunctionalInterface
  private interface Result {
    Object value(Object[] inputs, ContextScope scope);
  }

  private record Rule(Matcher[] matchers, Result result) {}

  private record InputKey(List<Object> values) {
    static InputKey of(Object[] values) {
      var normalized = new ArrayList<Object>(values.length);
      for (var value : values) normalized.add(normalize(value));
      return new InputKey(List.copyOf(normalized));
    }

    private static Object normalize(Object value) {
      if (value == null) return Missing.INSTANCE;
      if (value instanceof Number number)
        return new NumericKey(Double.doubleToLongBits(number.doubleValue()));
      if (value instanceof Concept concept) return new ConceptKey(concept.getUrn());
      return value;
    }
  }

  private record NumericKey(long bits) {}

  private record ConceptKey(String urn) {}

  private enum Missing {
    INSTANCE
  }

  private final List<String> inputNames;
  private final List<Rule> rules;
  private final Cache<InputKey, Integer> matches;
  private final Object noData;

  private CompiledValueMapping(
      List<String> inputNames, List<Rule> rules, Object noData, long cacheSize) {
    this.inputNames = List.copyOf(inputNames);
    this.rules = List.copyOf(rules);
    this.noData = noData;
    this.matches =
        cacheSize <= 0 ? null : Caffeine.newBuilder().maximumSize(cacheSize).recordStats().build();
  }

  public static CompiledValueMapping compile(KimClassification classification, ContextScope scope) {
    return compile(classification, scope, configuredCacheSize());
  }

  static CompiledValueMapping compile(
      KimClassification classification, ContextScope scope, long cacheSize) {
    Objects.requireNonNull(classification, "classification");
    var compiler = new Compiler(scope, List.of("self"));
    var rules = new ArrayList<Rule>();
    for (var pair : classification.getClassifiers()) {
      rules.add(
          new Rule(
              new Matcher[] {compiler.matcher(pair.getSecond())},
              compiler.resultForConcept(pair.getFirst())));
    }
    return new CompiledValueMapping(List.of("self"), rules, null, cacheSize);
  }

  public static CompiledValueMapping compile(KimLookupTable lookup, ContextScope scope) {
    return compile(lookup, scope, configuredCacheSize());
  }

  /** Compile an annotation-derived codelist for one {@code according to} authority. */
  public static CompiledValueMapping compile(
      Codelist codelist, String authorityId, ContextScope scope) {
    Objects.requireNonNull(codelist, "codelist");
    if (authorityId == null || authorityId.isBlank())
      throw new KlabIllegalArgumentException("A codelist authority is required");
    var compiler = new Compiler(scope, List.of("self"));
    var rules = new ArrayList<Rule>();
    for (var code : codelist.codes(authorityId)) {
      var expected = integralLong(code);
      if (expected == null)
        throw new KlabIllegalArgumentException(
            "Codelist " + codelist.getUrn() + " contains a non-integral code: " + code);
      var value = codelist.value(authorityId, code);
      if (!(value instanceof KimConcept concept))
        throw new KlabIllegalArgumentException(
            "Codelist " + codelist.getUrn() + " must map codes to KimConcept values");
      rules.add(
          new Rule(
              new Matcher[] {(input, context) -> Objects.equals(expected, integralLong(input))},
              compiler.resultForConcept(concept)));
    }
    if (rules.isEmpty())
      throw new KlabIllegalArgumentException(
          "Codelist " + codelist.getUrn() + " has no codes for authority " + authorityId);
    return new CompiledValueMapping(List.of("self"), rules, null, configuredCacheSize());
  }

  static CompiledValueMapping compile(KimLookupTable lookup, ContextScope scope, long cacheSize) {
    Objects.requireNonNull(lookup, "lookup");
    var names = new ArrayList<String>();
    var columns = new ArrayList<Integer>();
    int rowDimension = -1;
    int columnDimension = -1;
    for (int i = 0; i < lookup.getArguments().size(); i++) {
      var argument = lookup.getArguments().get(i);
      if ("?".equals(argument.id) || "*".equals(argument.id)) continue;
      if (argument.id == null || argument.id.isBlank()) {
        throw new KlabIllegalArgumentException(
            "Abstract-predicate lookup arguments must be resolved before Runtime compilation");
      }
      if (argument.dimension == KimLookupTable.Argument.Dimension.ROW) rowDimension = names.size();
      if (argument.dimension == KimLookupTable.Argument.Dimension.COLUMN)
        columnDimension = names.size();
      names.add(argument.id);
      columns.add(i);
    }
    var compiler = new Compiler(scope, names);
    var rules = new ArrayList<Rule>();
    if (lookup.isTwoWay()) {
      if (names.size() != 2 || rowDimension < 0 || columnDimension < 0) {
        throw new KlabIllegalArgumentException(
            "A two-way lookup requires row and column input arguments");
      }
      for (int row = 0; row < lookup.getTable().getRowCount(); row++) {
        for (int column = 0; column < lookup.getTable().getColumnCount(); column++) {
          var matchers = new Matcher[names.size()];
          matchers[rowDimension] = compiler.matcher(lookup.getRowClassifiers().get(row));
          matchers[columnDimension] = compiler.matcher(lookup.getColumnClassifiers().get(column));
          rules.add(new Rule(matchers, compiler.result(lookup.getTable().row(row)[column])));
        }
      }
    } else {
      for (var row : lookup.getTable().rows()) {
        var matchers = new Matcher[names.size()];
        for (int i = 0; i < columns.size(); i++) {
          matchers[i] = compiler.matcher(row[columns.get(i)]);
        }
        rules.add(new Rule(matchers, compiler.result(row[lookup.getLookupColumnIndex()])));
      }
    }
    Object noData =
        lookup.getLookupType() == org.integratedmodelling.klab.api.knowledge.Artifact.Type.NUMBER
            ? Double.NaN
            : lookup.getLookupType()
                    == org.integratedmodelling.klab.api.knowledge.Artifact.Type.BOOLEAN
                ? Boolean.FALSE
                : null;
    return new CompiledValueMapping(names, rules, noData, cacheSize);
  }

  public List<String> inputNames() {
    return inputNames;
  }

  /** First-match-wins lookup. The cache stores the selected rule, never the result value. */
  public Object lookup(Object[] inputs, ContextScope scope) {
    if (inputs == null || inputs.length != inputNames.size()) {
      throw new KlabIllegalArgumentException(
          "Value mapping expected " + inputNames.size() + " inputs");
    }
    var key = InputKey.of(inputs);
    Integer index = matches == null ? null : matches.getIfPresent(key);
    if (index == null) {
      index = match(inputs, scope);
      if (matches != null) matches.put(key, index);
    }
    return index == NO_MATCH ? noData : rules.get(index).result().value(inputs, scope);
  }

  public Statistics statistics() {
    if (matches == null) return new Statistics(0, 0, 0, 0);
    matches.cleanUp();
    var stats = matches.stats();
    return new Statistics(
        stats.hitCount(), stats.missCount(), stats.evictionCount(), matches.estimatedSize());
  }

  private int match(Object[] inputs, ContextScope scope) {
    for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
      var rule = rules.get(ruleIndex);
      boolean matched = true;
      for (int input = 0; input < inputs.length; input++) {
        if (!rule.matchers()[input].matches(inputs[input], scope)) {
          matched = false;
          break;
        }
      }
      if (matched) return ruleIndex;
    }
    return NO_MATCH;
  }

  private static long configuredCacheSize() {
    return Math.max(0, Long.getLong(CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE));
  }

  private static final class Compiler {
    private final ContextScope scope;
    private final Reasoner reasoner;
    private final List<String> inputNames;

    private Compiler(ContextScope scope, List<String> inputNames) {
      this.scope = scope;
      this.reasoner = scope == null ? null : scope.getService(Reasoner.class);
      this.inputNames = inputNames;
    }

    private Matcher matcher(KimClassifier classifier) {
      Matcher matcher;
      if (classifier.isCatchAnything()) matcher = (value, context) -> true;
      else if (classifier.isCatchAll()) matcher = (value, context) -> !isMissing(value);
      else if (classifier.isNullMatch()) matcher = (value, context) -> isMissing(value);
      else if (classifier.getNumberMatch() != null) {
        double expected = classifier.getNumberMatch();
        matcher =
            (value, context) ->
                value instanceof Number number
                    && Double.compare(expected, number.doubleValue()) == 0;
      } else if (classifier.getBooleanMatch() != null) {
        boolean expected = classifier.getBooleanMatch();
        matcher = (value, context) -> value instanceof Boolean bool && expected == bool;
      } else if (classifier.getIntervalMatch() != null) {
        var range = classifier.getIntervalMatch();
        matcher =
            (value, context) ->
                value instanceof Number number
                    && Double.isFinite(number.doubleValue())
                    && range.contains(number.doubleValue());
      } else if (classifier.getStringMatch() != null) {
        String expected = classifier.getStringMatch();
        matcher = (value, context) -> value != null && expected.equals(value.toString());
      } else if (classifier.getConceptMatch() != null) {
        var expected = resolve(classifier.getConceptMatch());
        matcher =
            (value, context) ->
                value instanceof Concept concept
                    && expected != null
                    && reasoner(context).is(concept, expected);
      } else if (classifier.getClassifierMatches() != null
          && !classifier.getClassifierMatches().isEmpty()) {
        var alternatives = classifier.getClassifierMatches().stream().map(this::matcher).toList();
        matcher =
            (value, context) ->
                alternatives.stream().anyMatch(alternative -> alternative.matches(value, context));
      } else if (classifier.getConceptMatches() != null
          && !classifier.getConceptMatches().isEmpty()) {
        var alternatives = classifier.getConceptMatches().stream().map(this::resolve).toList();
        matcher =
            (value, context) ->
                value instanceof Concept concept
                    && alternatives.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(expected -> reasoner(context).is(concept, expected));
      } else if (classifier.getQuantityMatch() != null) {
        double expected = classifier.getQuantityMatch().getValue().doubleValue();
        matcher =
            (value, context) ->
                value instanceof Number number
                    && Double.compare(expected, number.doubleValue()) == 0;
      } else if (classifier.getDateMatch() != null) {
        long expected = milliseconds(classifier.getDateMatch());
        matcher = (value, context) -> temporalMilliseconds(value) == expected;
      } else if (classifier.getExpressionMatch() != null) {
        Expression expression =
            new GroovyProcessor()
                .analyze(
                    classifier.getExpressionMatch(),
                    scope,
                    List.of(),
                    List.of(),
                    Expression.CompilerOption.IgnoreContext)
                .compile();
        matcher = (value, context) -> Boolean.TRUE.equals(expression.eval(context, "self", value));
      } else {
        throw new KlabIllegalArgumentException("Unsupported empty classifier");
      }
      return classifier.isNegated()
          ? (value, context) -> !matcher.matches(value, context)
          : matcher;
    }

    private Result resultForConcept(KimConcept concept) {
      Concept resolved = resolve(concept);
      return (inputs, context) -> resolved;
    }

    private Result result(KimClassifier classifier) {
      if (classifier.getExpressionMatch() != null) {
        Expression expression =
            new GroovyProcessor()
                .analyze(
                    classifier.getExpressionMatch(),
                    scope,
                    List.of(),
                    List.of(),
                    Expression.CompilerOption.IgnoreContext)
                .compile();
        return (inputs, context) -> {
          var parameters = new java.util.LinkedHashMap<String, Object>();
          parameters.put("self", inputs.length == 0 ? null : inputs[0]);
          for (int i = 0; i < inputNames.size(); i++) parameters.put(inputNames.get(i), inputs[i]);
          return expression.eval(context, parameters);
        };
      }
      Object value = literal(classifier);
      return (inputs, context) -> value;
    }

    private Object literal(KimClassifier classifier) {
      if (classifier.isNullMatch()) return null;
      if (classifier.getNumberMatch() != null) return classifier.getNumberMatch();
      if (classifier.getBooleanMatch() != null) return classifier.getBooleanMatch();
      if (classifier.getStringMatch() != null) return classifier.getStringMatch();
      if (classifier.getConceptMatch() != null) return resolve(classifier.getConceptMatch());
      if (classifier.getQuantityMatch() != null) return classifier.getQuantityMatch().getValue();
      if (classifier.getDateMatch() != null) return milliseconds(classifier.getDateMatch());
      throw new KlabIllegalArgumentException("Classifier cannot be used as a result value");
    }

    private Concept resolve(KimConcept concept) {
      if (concept == null) return null;
      if (reasoner == null)
        throw new KlabIllegalArgumentException(
            "Concept mappings require a Runtime Reasoner service");
      return reasoner.resolveConcept(concept.getUrn());
    }

    private Reasoner reasoner(ContextScope context) {
      Reasoner ret = reasoner != null ? reasoner : context.getService(Reasoner.class);
      if (ret == null)
        throw new KlabIllegalArgumentException("Concept matching requires a Reasoner service");
      return ret;
    }
  }

  private static boolean isMissing(Object value) {
    return value == null || (value instanceof Number number && Double.isNaN(number.doubleValue()));
  }

  private static Long integralLong(Object value) {
    if (!(value instanceof Number number)) return null;
    try {
      if (number instanceof java.math.BigInteger integer) return integer.longValueExact();
      if (number instanceof java.math.BigDecimal decimal) return decimal.longValueExact();
    } catch (ArithmeticException e) {
      return null;
    }
    if (number instanceof Byte
        || number instanceof Short
        || number instanceof Integer
        || number instanceof Long) return number.longValue();
    double candidate = number.doubleValue();
    if (!Double.isFinite(candidate)
        || Math.rint(candidate) != candidate
        || candidate >= 0x1.0p63
        || candidate < -0x1.0p63) return null;
    return (long) candidate;
  }

  private static long milliseconds(KimDate date) {
    return LocalDateTime.of(
            date.getYear(),
            Math.max(1, date.getMonth()),
            Math.max(1, date.getDay()),
            date.getHour(),
            date.getMin(),
            date.getSec(),
            date.getMs() * 1_000_000)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  private static long temporalMilliseconds(Object value) {
    if (value instanceof Instant instant) return instant.toEpochMilli();
    if (value instanceof java.util.Date date) return date.getTime();
    if (value instanceof Number number) return number.longValue();
    if (value instanceof KimDate date) return milliseconds(date);
    return Long.MIN_VALUE;
  }
}
