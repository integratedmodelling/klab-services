package org.integratedmodelling.klab.services.reasoner;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.objects.IdentificationStrategyImpl;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;

/** Select and instantiate portable observation plans. Matching never executes a resolution. */
public class ObservationReasoner {

  private final Reasoner reasoner;
  private final Function<KimObservable, Observable> declare;
  private final ObservationPatternMatcher matcher;
  private final List<KimObservationStrategy> strategies = new CopyOnWriteArrayList<>();

  public ObservationReasoner(ReasonerService reasoner) {
    this(reasoner, reasoner::declareObservable);
    reasoner
        .getComponentRegistry()
        .loadExtensions("org.integratedmodelling.klab.services.reasoner.functors");
  }

  public ObservationReasoner(Reasoner reasoner, Function<KimObservable, Observable> declare) {
    this.reasoner = reasoner;
    this.declare = declare;
    this.matcher = new ObservationPatternMatcher(reasoner, declare);
  }

  public List<ObservationStrategy> computeMatchingStrategies(
      Observation observation, ContextScope scope, boolean isResolution) {
    List<ObservationStrategy> result = new ArrayList<>();
    for (var strategy : strategies) {
      if (strategy.getType()
          != (isResolution
              ? KimObservationStrategy.Type.OBSERVATION
              : KimObservationStrategy.Type.IDENTIFICATION)) continue;
      try {
        var variables = select(strategy, observation.getObservable(), scope);
        if (variables != null && setup(strategy, variables, scope)) {
          result.add(lower(strategy, variables, scope));
        }
      } catch (IllegalArgumentException | UnsupportedOperationException e) {
        scope.warn(
            "Strategy "
                + strategy.getNamespace()
                + ":"
                + strategy.getUrn()
                + " is unavailable: "
                + e.getMessage());
      }
    }
    return result;
  }

  private Map<String, Object> select(
      KimObservationStrategy strategy, Observable observable, ContextScope scope) {
    if (strategy.getModelVersion() != 2 || strategy.getSelection() == null)
      throw new IllegalArgumentException("Expected a version-2 selection");
    for (var alternative : strategy.getSelection().getAlternatives()) {
      Map<String, Object> variables = new LinkedHashMap<>();
      variables.put("this", observable);
      variables.put(
          "context",
          scope.getContextObservation() == null
              ? null
              : scope.getContextObservation().getObservable());
      boolean matches;
      if (alternative.getPattern() != null) {
        var pattern = alternative.getPattern();
        var subject =
            pattern.getSubject() == null
                ? observable
                : evaluate(pattern.getSubject(), variables, scope);
        matches = matcher.match(pattern.getExpression(), subject, variables);
      } else if (alternative.getObservable() != null) {
        matches = reasoner.match(observable, declare.apply(alternative.getObservable()));
      } else if (alternative.getMatcher() != null) {
        matches =
            Boolean.TRUE.equals(evaluate(alternative.getMatcher().getCall(), variables, scope));
      } else throw new IllegalArgumentException("Empty match alternative");
      if (matches && checks(alternative.getGuards(), variables, scope)) return variables;
    }
    return null;
  }

  private boolean checks(
      List<StrategyCall> calls, Map<String, Object> variables, ContextScope scope) {
    for (var call : calls) if (!Boolean.TRUE.equals(evaluate(call, variables, scope))) return false;
    return true;
  }

  private boolean setup(
      KimObservationStrategy strategy, Map<String, Object> variables, ContextScope scope) {
    for (var setup : strategy.getSetup()) {
      if (setup instanceof EnsureSetup ensure) {
        if (!checks(ensure.getChecks(), variables, scope)) return false;
      } else if (setup instanceof LetSetup let) {
        for (var binding : let.getBindings()) {
          Object value = evaluate(binding.getValue(), variables, scope);
          List<?> values =
              binding.getNames().size() == 1
                  ? Collections.singletonList(value)
                  : value instanceof List<?> tuple ? tuple : List.of();
          if (values.size() != binding.getNames().size())
            throw new IllegalArgumentException("Tuple arity mismatch");
          for (int i = 0; i < values.size(); i++) {
            String name = name(binding.getNames().get(i));
            if (variables.containsKey(name))
              throw new IllegalArgumentException("Duplicate/reserved variable $" + name);
            variables.put(name, values.get(i));
          }
        }
      } else throw new UnsupportedOperationException("Setup " + setup.getClass().getSimpleName());
    }
    return true;
  }

  static String name(String spelling) {
    if (spelling == null || spelling.isBlank())
      throw new IllegalArgumentException("Missing variable name");
    return spelling.startsWith("$") ? spelling.substring(1) : spelling;
  }

  private Object evaluate(
      StrategyExpression expression, Map<String, Object> variables, ContextScope scope) {
    if (expression instanceof StrategyVariable variable) {
      String name = name(variable.getSpelling());
      if (!variables.containsKey(name))
        throw new IllegalArgumentException("Unbound variable $" + name);
      return variables.get(name);
    }
    if (expression instanceof ClosedObservable closed) return declare.apply(closed.getObservable());
    if (expression instanceof StrategyScalar scalar)
      return ObservationPatternMatcher.scalar(scalar);
    if (expression instanceof StrategyCall call) {
      List<Object> args = new ArrayList<>();
      for (var argument : call.getArguments()) {
        if (argument.getName() != null)
          throw new UnsupportedOperationException("Named functor arguments");
        args.add(evaluate(argument.getValue(), variables, scope));
      }
      if (call.getFunction().equals("context.exists")) {
        if (!args.isEmpty())
          throw new IllegalArgumentException("context.exists takes no arguments");
        return scope.getContextObservation() != null;
      }
      if (args.isEmpty()) args.add(variables.get("this"));
      if (args.size() != 1 || !(args.getFirst() instanceof Semantics semantics))
        throw new IllegalArgumentException(
            "Expected one semantic argument to " + call.getFunction());
      return switch (call.getFunction()) {
        case "request.fully_specified" ->
            !semantics.isAbstract()
                && !semantics.isGeneric()
                && !semantics.is(SemanticType.NOTHING)
                && (!(semantics instanceof Observable o) || o.getGenericComponents().isEmpty());
        case "type.concrete" -> !semantics.isAbstract();
        case "type.abstract" -> semantics.isAbstract();
        case "type.collective" -> semantics.asConcept().isCollective();
        case "relationship.source", "type.relationship.source" ->
            reasoner.relationshipSource(semantics);
        case "relationship.target", "type.relationship.target" ->
            reasoner.relationshipTarget(semantics);
        case "collective", "type.arity.collective" ->
            semantics instanceof Observable o
                ? o.builder(scope).collective(true).buildObservable()
                : reasoner.resolveObservable(semantics.asConcept().collective().getUrn());
        default -> throw new UnsupportedOperationException("Functor " + call.getFunction());
      };
    }
    throw new IllegalArgumentException("Missing or unknown expression");
  }

  private Observable target(
      StrategyTarget target, Map<String, Object> variables, ContextScope scope) {
    Object value =
        target.getObservable() == null
            ? evaluate(target.getExpression(), variables, scope)
            : declare.apply(target.getObservable());
    if (value instanceof Observable observable) return observable;
    if (value instanceof Concept concept) {
      var observable = reasoner.resolveObservable(concept.getUrn());
      if (observable != null) return observable;
    }
    throw new IllegalArgumentException("Producer target is not an observable");
  }

  /** Initial execution subset: producers with explicit prerequisite inputs, and a final yield. */
  private ObservationStrategy lower(
      KimObservationStrategy strategy, Map<String, Object> variables, ContextScope scope) {

    var result = new ObservationStrategyImpl();
    result.setUrn(strategy.getUrn());
    result.setNamespace(strategy.getNamespace());
    result.setRank(strategy.getRank());
    result.setDocumentation(strategy.getDescription());
    result.setMetadata(strategy.getMetadata());
    result.setServiceId(strategy.getServiceId());
    result.setAnnotations(strategy.getAnnotations());
    Set<String> names = new HashSet<>();
    Set<String> consumed = new HashSet<>();
    String last = null;
    boolean yielded = false;
    if (strategy.getPlan() == null) throw new IllegalArgumentException("Missing plan");
    for (var step : strategy.getPlan().getSteps()) {
      if (yielded) throw new IllegalArgumentException("Steps after yield");
      if (step instanceof GraphProducer producer) {
        if (producer.getContext() != null || producer.getFallback() != null)
          throw new UnsupportedOperationException("Explicit context or fallback");
        var operation = new ObservationStrategyImpl.OperationImpl();
        if (!result.getOperations().isEmpty()
            && result.getOperations().getLast().getType()
                == ObservationStrategy.Operation.Type.OBSERVE)
          throw new UnsupportedOperationException("Intermediate observe producers");
        operation.setType(ObservationStrategy.Operation.Type.valueOf(producer.getMode().name()));
        operation.setObservable(target(producer.getTarget(), variables, scope));
        operation.setId(producer.getName());
        for (var input : producer.getInputs()) {
          String graph = input.getGraph().getName();
          if (!names.contains(graph))
            throw new IllegalArgumentException("Unknown/forward graph " + graph);
          if (operation.getInputs().putIfAbsent(input.getPort(), graph) != null)
            throw new IllegalArgumentException("Duplicate input port " + input.getPort());
          consumed.add(graph);
        }
        if (operation.getType() == ObservationStrategy.Operation.Type.RESOLVE
            && !operation.getInputs().isEmpty())
          throw new UnsupportedOperationException("Inputs on recursive resolve");
        if (producer.getName() == null || !names.add(producer.getName()))
          throw new IllegalArgumentException("Missing or duplicate graph name");
        last = producer.getName();
        result.getOperations().add(operation);
      } else if (step instanceof PlanYield yield) {
        if (!Objects.equals(last, yield.getGraph().getName()))
          throw new UnsupportedOperationException("Yield must select the final producer");
        yielded = true;
      } else
        throw new UnsupportedOperationException("Plan step " + step.getClass().getSimpleName());
    }
    if (last == null) throw new IllegalArgumentException("Empty plan");
    consumed.add(last);
    if (!consumed.containsAll(names))
      throw new IllegalArgumentException("Unconsumed graphs require explicit composition");
    return result;
  }

  public int getCost(ObservationStrategy strategy, Observable observable, ContextScope scope) {
    return 0;
  }

  public void releaseNamespace(String namespace) {
    strategies.removeIf(s -> Objects.equals(s.getNamespace(), namespace));
  }

  public void registerStrategy(KimObservationStrategy strategy) {
    strategies.removeIf(
        s ->
            Objects.equals(s.getNamespace(), strategy.getNamespace())
                && Objects.equals(s.getUrn(), strategy.getUrn()));
    strategies.add(strategy);
  }

  public void initializeStrategies() {
    strategies.sort(Comparator.comparingInt(KimObservationStrategy::getRank));
  }

  public IdentificationStrategy computeIdentificationStrategy(
      Observable observable, ContextScope scope) {
    var observation =
        scope
            .observation(observable)
            .geometry(Geometry.UNIVERSAL)
            .identity("dummy", "name")
            .register();
    var matches = computeMatchingStrategies(observation, scope, false);
    return matches.isEmpty() ? null : new IdentificationStrategyImpl(matches.getFirst());
  }
}
