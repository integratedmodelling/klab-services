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
      String phase = "selection";
      try {
        var variables = select(strategy, observation.getObservable(), scope);
        phase = "setup";
        if (variables != null && setup(strategy, variables, scope)) {
          phase = "lowering";
          result.add(lower(strategy, variables, scope));
        }
      } catch (IllegalArgumentException | UnsupportedOperationException e) {
        scope.warn(
            "Strategy "
                + strategy.getNamespace()
                + ":"
                + strategy.getUrn()
                + " is unavailable during " + phase + ": "
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
      if (matches && checks(alternative.getGuards(), variables, scope, strategy.getUrn())) return variables;
      if (!matches) scope.debug("Observation strategy pattern did not match: " + strategy.getUrn()
          + "; observable=" + observable.getUrn() + "; types=" + observable.getSemantics().getType());
    }
    return null;
  }

  private boolean checks(
      List<StrategyCall> calls, Map<String, Object> variables, ContextScope scope, String strategyUrn) {
    for (var call : calls) {
      if (!Boolean.TRUE.equals(evaluate(call, variables, scope))) {
        scope.debug("Observation strategy " + strategyUrn + " guard rejected: " + call.getFunction()
            + "; observable=" + variables.get("this") + "; context=" + variables.get("context"));
        return false;
      }
    }
    return true;
  }

  private boolean setup(
      KimObservationStrategy strategy, Map<String, Object> variables, ContextScope scope) {
    for (var setup : strategy.getSetup()) {
      if (setup instanceof EnsureSetup ensure) {
        if (!checks(ensure.getChecks(), variables, scope, strategy.getUrn())) return false;
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
      if (call.getFunction().equals("inherent") || call.getFunction().equals("transform.applicable")) {
        if (args.size() != 2 || !(args.get(0) instanceof Semantics predicate)
            || !(args.get(1) instanceof Semantics base))
          throw new IllegalArgumentException("Expected predicate and base arguments to " + call.getFunction());
        if (call.getFunction().equals("transform.applicable"))
          return predicate.is(SemanticType.PREDICATE) && !predicate.isAbstract()
              && base.is(SemanticType.QUALITY) && !base.isAbstract();
        return new ObservableBuildStrategy(predicate.asConcept(), scope).of(base.asConcept()).buildObservable();
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
        case "predicate.concrete" -> semantics.is(SemanticType.PREDICATE) && !semantics.isAbstract();
        case "predicates.split_first" -> {
          var predicate = reasoner.directTraits(semantics).stream()
              .sorted(Comparator.comparing(Concept::getUrn)).findFirst()
              .orElseThrow(() -> new IllegalArgumentException("No direct predicate to split"));
          var original = Observable.promote(semantics);
          var remainder = original.builder(scope).without(predicate).buildObservable();
          if (remainder == null || Objects.equals(remainder.getUrn(), original.getUrn()))
            throw new IllegalArgumentException("Predicate split did not reduce the observable");
          yield List.of(predicate, remainder);
        }
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
      if (yielded) throw new IllegalArgumentException("Steps after terminal result");
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
        String graphName = producer.getName();
        if (graphName == null) {
          // The source result is anonymous. Keep a collision-free operational ID so
          // the Resolver's named-plan protocol and JSON representation stay unchanged.
          graphName = "__result";
          while (names.contains(graphName)) graphName += "_";
          yielded = true;
        } else if (graphName.isBlank() || !names.add(graphName)) {
          throw new IllegalArgumentException("Empty or duplicate graph name");
        }
        operation.setId(graphName);
        last = graphName;
        result.getOperations().add(operation);
      } else if (step instanceof GraphMerge merge) {
        // The first executable composition is a quality plus a transformer model.
        // Lower its explicit two-graph contract into the existing transformation linkage.
        if (merge.getOperator() != CompositionKind.TRANSFORM || merge.getName() != null
            || !merge.getOptions().isEmpty() || result.getOperations().size() != 2)
          throw new UnsupportedOperationException("Only terminal two-producer transform merges are executable");
        var base = result.getOperations().get(0);
        var transformer = result.getOperations().get(1);
        var output = target(merge.getTarget(), variables, scope);
        if (base.getType() != ObservationStrategy.Operation.Type.RESOLVE
            || transformer.getType() != ObservationStrategy.Operation.Type.OBSERVE
            || !base.getId().equals(merge.getLeft().getName())
            || !transformer.getId().equals(merge.getRight().getName())
            || !transformer.getInputs().isEmpty()
            || !base.getObservable().is(SemanticType.QUALITY)
            || transformer.getObservable().getContextualization() != Contextualization.TRANSFORMATION
            || !Objects.equals(reasoner.directInherent(transformer.getObservable()), base.getObservable().asConcept())
            || !Objects.equals(output.getUrn(), ((Observable) variables.get("this")).getUrn()))
          throw new IllegalArgumentException("Invalid transform graph pairing or output");
        boolean reconstructsOutput = reasoner.directTraits(output).stream().anyMatch(predicate -> {
          var remainder = output.builder(scope).without(predicate).buildObservable();
          var expectedTransformer = new ObservableBuildStrategy(predicate, scope)
              .of(base.getObservable().asConcept()).buildObservable();
          return remainder != null && expectedTransformer != null
              && Objects.equals(remainder.getUrn(), base.getObservable().getUrn())
              && Objects.equals(expectedTransformer.getUrn(), transformer.getObservable().getUrn());
        });
        if (!reconstructsOutput)
          throw new IllegalArgumentException("Transform graphs do not reconstruct the yielded observable");
        ((ObservationStrategyImpl.OperationImpl) transformer).setTransformationTarget(base.getId());
        consumed.add(base.getId());
        consumed.add(transformer.getId());
        yielded = true;
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
