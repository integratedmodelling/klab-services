package org.integratedmodelling.klab.services.reasoner;

import com.google.common.collect.Sets;
import java.util.*;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Specialized functions to infer observation strategies. Kept separately for clarity as this is a
 * crucial k.LAB component, although they are part of the reasoner services.
 */
public class ObservationReasoner {

  private static Set<String> defaultVariables = Set.of("this", "context");

  private ReasonerService reasoner;
  private List<KimObservationStrategy> observationStrategies = new ArrayList<>();

  private class QuickSemanticFilter {

    public Set<SemanticType> semanticTypesWhitelist = EnumSet.noneOf(SemanticType.class);
    public Set<SemanticType> semanticTypesBlacklist = EnumSet.noneOf(SemanticType.class);
    // any predefined variables used in patterns
    public Set<String> fixedVariablesUsed = new HashSet<>();
    public Set<String> customVariablesUsed = new HashSet<>();
    public List<List<KimObservationStrategy.Filter.SemanticPattern>> typePatterns =
        new ArrayList<>();
    public boolean collectiveConstraints;
    public boolean collectiveOnly;
    public boolean nonCollectiveOnly;

    /**
     * Quick match to quickly weed out the non-matching classes and minimize the need for inference
     * and pattern instantiation.
     *
     * @param observable
     * @param scope
     * @return
     */
    public boolean match(Observable observable, ContextScope scope) {
      if (!semanticTypesWhitelist.isEmpty()) {
        if (Sets.intersection(observable.getSemantics().getType(), semanticTypesWhitelist)
            .isEmpty()) {
          return false;
        }
      }
      if (!semanticTypesBlacklist.isEmpty()) {
        if (!Sets.intersection(observable.getSemantics().getType(), semanticTypesBlacklist)
            .isEmpty()) {
          return false;
        }
      }
      if (collectiveConstraints) {
        if ((collectiveOnly && !observable.getSemantics().isCollective())
            || (nonCollectiveOnly && observable.getSemantics().isCollective())) {
          return false;
        }
      }

      boolean patternMatch = typePatterns.isEmpty();
      for (var pattern : typePatterns) {
        if (matchesPattern(observable.getSemantics(), pattern)) {
          patternMatch = true;
          break;
        }
      }

      return patternMatch;
    }

    private boolean matchesPattern(
        Concept semantics, List<KimObservationStrategy.Filter.SemanticPattern> semanticPattern) {
      for (var rule : semanticPattern) {
        switch (rule) {
          case QUALITY -> {
            if (!semantics.is(SemanticType.QUALITY)) {
              return false;
            }
          }
          case TYPE -> {
            if (!semantics.is(SemanticType.CLASS)) {
              return false;
            }
          }
          case MEASUREMENT -> {
            if (!semantics.is(SemanticType.INTENSIVE) && !semantics.is(SemanticType.EXTENSIVE)) {
              return false;
            }
          }
          case QUANTITY -> {
            if (!semantics.is(SemanticType.QUANTIFIABLE)) {
              return false;
            }
          }
          case PRIORITY -> {
            if (!semantics.is(SemanticType.PRIORITY)) {
              return false;
            }
          }
          case PRESENCE -> {
            if (!semantics.is(SemanticType.PRESENCE)) {
              return false;
            }
          }
          case PREDICATE -> {
            if (reasoner.directTraits(semantics).isEmpty()) {
              return false;
            }
          }
          case ROLE -> {
            if (reasoner.directRoles(semantics).isEmpty()) {
              return false;
            }
          }
          case ATTRIBUTE -> {
            if (reasoner.directAttributes(semantics).isEmpty()) {
              return false;
            }
          }
          case IDENTITY -> {
            if (reasoner.directIdentities(semantics).isEmpty()) {
              return false;
            }
          }
          case AGENT -> {
            if (!semantics.is(SemanticType.AGENT)) {
              return false;
            }
          }
          case RELATIONSHIP -> {
            if (!semantics.is(SemanticType.RELATIONSHIP)) {
              return false;
            }
          }
          case SUBJECT -> {
            if (!semantics.is(SemanticType.SUBJECT)) {
              return false;
            }
          }
          case PROCESS -> {
            if (!semantics.is(SemanticType.PROCESS)) {
              return false;
            }
          }
          case EVENT -> {
            if (!semantics.is(SemanticType.EVENT)) {
              return false;
            }
          }
          case CONFIGURATION -> {
            if (!semantics.is(SemanticType.CONFIGURATION)) {
              return false;
            }
          }
          case INSTANTIATION -> {
            if (semantics.getDescriptionType() != DescriptionType.INSTANTIATION) {
              return false;
            }
          }
          case DETECTION -> {
            if (semantics.getDescriptionType() != DescriptionType.DETECTION) {
              return false;
            }
          }
          case SIMULATION -> {
            if (semantics.getDescriptionType() != DescriptionType.SIMULATION) {
              return false;
            }
          }
          case MEASURE -> {
            if (semantics.getDescriptionType() != DescriptionType.MEASURE) {
              return false;
            }
          }
          case QUANTIFICATION -> {
            if (semantics.getDescriptionType() != DescriptionType.QUANTIFICATION) {
              return false;
            }
          }
          case VALUATION -> {
            if (semantics.getDescriptionType() != DescriptionType.VALUATION) {
              return false;
            }
          }
          case CATEGORIZATION -> {
            if (semantics.getDescriptionType() != DescriptionType.CATEGORIZATION) {
              return false;
            }
          }
          case VERIFICATION -> {
            if (semantics.getDescriptionType() != DescriptionType.VERIFICATION) {
              return false;
            }
          }
          case CLASSIFICATION -> {
            if (semantics.getDescriptionType() != DescriptionType.CLASSIFICATION) {
              return false;
            }
          }
          case CHARACTERIZATION -> {
            if (semantics.getDescriptionType() != DescriptionType.CHARACTERIZATION) {
              return false;
            }
          }
          case TRANSFORMATION -> {
            if (semantics.getDescriptionType() != DescriptionType.TRANSFORMATION) {
              return false;
            }
          }
          case ACKNOWLEDGEMENT -> {
            if (semantics.getDescriptionType() != DescriptionType.ACKNOWLEDGEMENT) {
              return false;
            }
          }
          case CONNECTION -> {
            if (semantics.getDescriptionType() != DescriptionType.CONNECTION) {
              return false;
            }
          }
        }
      }
      return true;
    }
  }

  /**
   * We precompute the non-contextual applicable info for each strategy to quickly weed out those
   * that are certain to not apply.
   */
  private Map<String, QuickSemanticFilter> quickFilters = new HashMap<>();

  public ObservationReasoner(ReasonerService reasonerService) {
    this.reasoner = reasonerService;
    // ensure the core functor library is read. Plugins may add more.
    reasonerService
        .getComponentRegistry()
        .loadExtensions("org.integratedmodelling.klab.services.reasoner.functors");
  }

  /**
   * Compile and return a list of matching, contextualized observation strategies that match the
   * observable and scope, in order of rank and cost, for the resolver to resolve.
   *
   * @param observation
   * @param scope
   * @return
   */
  public List<ObservationStrategy> computeMatchingStrategies(
      Observation observation, ContextScope scope, boolean isResolution) {

    var observable = observation.getObservable();
    List<ObservationStrategy> ret = new ArrayList<>();

    for (var strategy : observationStrategies) {

      if (isResolution && strategy.getType() != KimObservationStrategy.Type.OBSERVATION) {
        continue;
      }

      var filter = quickFilters.get(strategy.getUrn());

      if (filter.fixedVariablesUsed.contains("context") && scope.getContextObservation() == null) {
        continue;
      }

      if (filter.match(observable, scope)) {

        Map<String, Object> patternVariableValues = new HashMap<>();
        for (var variable : filter.fixedVariablesUsed) {
          patternVariableValues.put(
              variable,
              switch (variable) {
                case "this" -> observable;
                case "context" -> scope.getContextObservation().getObservable();
                default ->
                    throw new KlabUnimplementedException("predefined pattern variable " + variable);
              });
        }

        for (var variable : strategy.getMacroVariables().keySet()) {
          var functor = strategy.getMacroVariables().get(variable);
          if (functor.getLiteral() != null) {
            patternVariableValues.put(variable, Utils.Data.asString(functor.getLiteral()));
          } else if (functor.getMatch() != null) {
            // can't happen for now, parser won't accept. Should be a pattern to be useful.
          } else if (!functor.getFunctions().isEmpty()) {
            for (var function : functor.getFunctions()) {
              var value =
                  matchFunction(function, observable, scope, Object.class, patternVariableValues);
              String[] varNames = variable.split(",");
              if (value instanceof Collection<?> collection) {
                // must be string with same amount of return values
                if (varNames.length != collection.size()) {
                  scope.error("wrong number of return values from " + function);
                }
                int i = 0;
                for (var o : collection) {
                  patternVariableValues.put(varNames[i++], o);
                }
              } else {
                // set pattern var
                if (varNames.length != 1) {
                  scope.error("wrong number of return values from " + function);
                }
                patternVariableValues.put(variable, value);
              }
            }
          }
        }

        /*
         * A null match to the required macro variables means no match
         */
        if (!strategy.getMacroVariables().isEmpty() && patternVariableValues.containsValue(null)) {
          continue;
        }

        // at least a matching filter is necessary
        boolean match = false;
        for (var filterList : strategy.getFilters()) {
          for (var matching : filterList) {
            if (matchFilter(matching, observation, scope, patternVariableValues)) {
              match = true;
              break;
            }
          }
          if (match) {
            break;
          }
        }

        if (!match) {
          continue;
        }

        /*
          if we get here, the strategy definition is a match: compile the observation strategy
          operations for the observable and scope
        */
        ret.add(contextualizeStrategy(observation, strategy, patternVariableValues, scope));
      }
    }

    return ret;
  }

  private ObservationStrategy contextualizeStrategy(
      Observation observation,
      KimObservationStrategy strategy,
      Map<String, Object> patternVariableValues,
      ContextScope scope) {

    var os = new ObservationStrategyImpl();
    os.setDocumentation(strategy.getDescription()); // TODO compile template
    os.setUrn(strategy.getUrn());

    if (observation.getContextualizationData() != null
        && !observation.getContextualizationData().validate(observation, scope)) {
      var op = new ObservationStrategyImpl.OperationImpl();
      op.setType(KimObservationStrategy.Operation.Type.APPLY);
      op.getContextualizables()
          .add(new ContextualizableImpl(observation.getContextualizationData()));
      os.getOperations().add(op);
    }
    for (var operation : strategy.getOperations()) {

      var op = new ObservationStrategyImpl.OperationImpl();
      op.setType(operation.getType());
      op.setId(operation.getLocalId());
      op.setTransformationTarget(operation.getTransformationTarget());

      if (operation.getObservable() != null) {
        op.setObservable(
            operation.getObservable().getPatternVariables().isEmpty()
                ? reasoner.declareObservable(operation.getObservable())
                : reasoner.declareObservable(operation.getObservable(), patternVariableValues));
      }
      for (var function : operation.getFunctions()) {
        op.getContextualizables().add(new ContextualizableImpl(function));
      }
      for (var deferred : operation.getDeferredStrategies()) {
        op.getContextualizables()
            .add(
                new ContextualizableImpl(
                    ServiceCallImpl.create(
                        RuntimeService.CoreFunctor.DEFER_RESOLUTION.getServiceCallName(),
                        "strategy",
                        contextualizeStrategy(
                            observation, deferred, patternVariableValues, scope))));
      }
      os.getOperations().add(op);
    }
    return os;
  }

  private Object matchFunction(
      ServiceCall function,
      Semantics observable,
      ContextScope scope,
      Class<Object> objectClass,
      Map<String, Object> patternVariableValues) {

    var languageService = ServiceConfiguration.INSTANCE.getService(Language.class);

    // complete arguments if empty or using previously instantiated variables
    if (function.getParameters().isEmpty()) {
      function = function.withUnnamedParameters(observable);
    } else
      for (var key : function.getParameters().keySet()) {
        // substitute parameters and set them as unnamed
        function =
            function.withUnnamedParameters(
                patternVariableValues.getOrDefault(key.substring(1), key));
      }
    return languageService.execute(function, scope, Object.class, scope, observable);
  }

  private boolean matchFilter(
      KimObservationStrategy.Filter filter,
      Observation observation,
      ContextScope scope,
      Map<String, Object> patternVariableValues) {

    boolean ret = true;
    if (filter.getMatch() != null) {
      var semantics =
          filter.getMatch().isPattern()
              ? reasoner.declareConcept(filter.getMatch(), patternVariableValues)
              : reasoner.declareConcept(filter.getMatch());
      ret = semantics != null && reasoner.match(observation.getObservable(), semantics);
    } else if (!filter.getTypePattern().isEmpty()) {
      for (var pattern : filter.getTypePattern()) {
        if (ret) {
          switch (pattern) {
            case QUALITY -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.QUALITY)) {
                ret = false;
              }
            }
            case TYPE -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.CLASS)) {
                ret = false;
              }
            }
            case MEASUREMENT -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.INTENSIVE)
                  && !observation.getObservable().getSemantics().is(SemanticType.EXTENSIVE)) {
                ret = false;
              }
            }
            case QUANTITY -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.QUANTIFIABLE)) {
                ret = false;
              }
            }
            case PRIORITY -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.PRIORITY)) {
                ret = false;
              }
            }
            case PRESENCE -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.PRESENCE)) {
                ret = false;
              }
            }
            case PREDICATE -> {
              if (reasoner.directTraits(observation.getObservable().getSemantics()).isEmpty()) {
                ret = false;
              }
            }
            case ROLE -> {
              if (reasoner.directRoles(observation.getObservable().getSemantics()).isEmpty()) {
                ret = false;
              }
            }
            case ATTRIBUTE -> {
              if (reasoner.directAttributes(observation.getObservable().getSemantics()).isEmpty()) {
                ret = false;
              }
            }
            case IDENTITY -> {
              if (reasoner.directIdentities(observation.getObservable().getSemantics()).isEmpty()) {
                ret = false;
              }
            }
            case AGENT -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.AGENT)) {
                ret = false;
              }
            }
            case RELATIONSHIP -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.RELATIONSHIP)) {
                ret = false;
              }
            }
            case SUBJECT -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.SUBJECT)) {
                ret = false;
              }
            }
            case PROCESS -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.PROCESS)) {
                ret = false;
              }
            }
            case EVENT -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.EVENT)) {
                ret = false;
              }
            }
            case CONFIGURATION -> {
              if (!observation.getObservable().getSemantics().is(SemanticType.CONFIGURATION)) {
                ret = false;
              }
            }
              //          default -> {
              //            throw new IllegalArgumentException("Unknown type pattern: " + pattern);
              //          }
          }
        }
      }
    }
    if (ret && !filter.getFunctions().isEmpty()) {
      for (var function : filter.getFunctions()) {
        var value =
            matchFunction(
                function, observation.getObservable(), scope, Object.class, patternVariableValues);
        ret = value instanceof Boolean bool && bool;
      }
    }
    return filter.isNegated() != ret;
  }

  /**
   * An integer from 0 to 100, used to rank strategies <em>in context</em> among groups of
   * strategies with the same rank. Only called on strategies that match the observable.
   *
   * @return
   */
  public int getCost(ObservationStrategy strategy, Observable observable, ContextScope scope) {
    return 0;
  }

  /**
   * Release the named namespace, i.e. remove all strategies it contains.
   *
   * @param strategyNamespace
   */
  public void releaseNamespace(String strategyNamespace) {
    var filtered =
        observationStrategies.stream()
            .filter(o -> !o.getNamespace().equals(strategyNamespace))
            .toList();
    observationStrategies.clear();
    observationStrategies.addAll(filtered);
  }

  /** Add a new strategy or substitute the existing version of the same. */
  public void registerStrategy(KimObservationStrategy observationStrategy) {
    observationStrategies.add(observationStrategy);
    quickFilters.put(observationStrategy.getUrn(), computeInfo(observationStrategy));
  }

  public void initializeStrategies() {
    observationStrategies.sort(
        new Comparator<KimObservationStrategy>() {
          @Override
          public int compare(KimObservationStrategy o1, KimObservationStrategy o2) {
            return Integer.compare(o1.getRank(), o2.getRank());
          }
        });
  }

  private QuickSemanticFilter computeInfo(KimObservationStrategy observationStrategy) {

    Set<String> variables = new HashSet<>();
    QuickSemanticFilter ret = new QuickSemanticFilter();

    int nCollective = 0;
    int nNoncollective = 0;

    for (var filter : observationStrategy.getFilters()) {
      for (var match : filter) {
        // TODO negation is much more complicated
        if (match.getMatch() != null) {
          if (match.isNegated()) {
            ret.semanticTypesBlacklist.add(
                SemanticType.fundamentalType(match.getMatch().getType()));
          } else {
            ret.semanticTypesWhitelist.add(
                SemanticType.fundamentalType(match.getMatch().getType()));
          }
          if (match.getMatch().isCollective()) {
            nCollective++;
          } else {
            nNoncollective++;
          }
          variables.addAll(match.getMatch().getPatternVariables());
        } else if (!match.getTypePattern().isEmpty()) {
          ret.typePatterns.add(match.getTypePattern());
        }
      }
    }

    for (var operation : observationStrategy.getOperations()) {
      if (operation.getObservable() != null) {
        variables.addAll(operation.getObservable().getPatternVariables());
      }
    }

    if ((nCollective == 0 && nNoncollective > 0) || (nCollective > 0 && nNoncollective == 0)) {
      ret.collectiveConstraints = true;
      ret.collectiveOnly = nCollective > 0;
      ret.nonCollectiveOnly = nNoncollective > 0;
    }

    ret.fixedVariablesUsed.addAll(variables);
    ret.fixedVariablesUsed.retainAll(defaultVariables);
    ret.customVariablesUsed.addAll(variables);
    ret.customVariablesUsed.removeAll(defaultVariables);

    return ret;
  }

  public ObservationStrategy computeIdentificationStrategy(
      Observable observable, ContextScope scope) {
    // bit of a stretch, but no harm done
    var observation =
        DigitalTwin.createObservation(
            scope, observable, Geometry.UNIVERSAL, "dummy", Urn.of("urn:dummy"));
    var strategies = computeMatchingStrategies(observation, scope, false);
    return strategies.isEmpty() ? null : strategies.getFirst();
  }
}
