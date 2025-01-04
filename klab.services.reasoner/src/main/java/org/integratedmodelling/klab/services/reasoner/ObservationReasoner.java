package org.integratedmodelling.klab.services.reasoner;

import com.google.common.collect.Sets;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;

import java.util.*;

/**
 * Specialized functions to infer observation strategies. Kept separately for clarity as this is a
 * crucial k.LAB component, although they are part of the reasoner services.
 */
public class ObservationReasoner {

  private static Set<String> defaultVariables = Set.of("this", "context");

  private ReasonerService reasoner;
  private List<KimObservationStrategy> observationStrategies = new ArrayList<>();

  private static class QuickSemanticFilter {

    public Set<SemanticType> semanticTypesWhitelist = EnumSet.noneOf(SemanticType.class);
    public Set<SemanticType> semanticTypesBlacklist = EnumSet.noneOf(SemanticType.class);
    // any predefined variables used in patterns
    public Set<String> fixedVariablesUsed = new HashSet<>();
    public Set<String> customVariablesUsed = new HashSet<>();
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
      Observation observation, ContextScope scope) {

    var observable = observation.getObservable();
    List<ObservationStrategy> ret = new ArrayList<>();

    for (var strategy : observationStrategies) {

      QuickSemanticFilter filter = quickFilters.get(strategy.getUrn());

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

        var os = new ObservationStrategyImpl();

        os.setDocumentation(strategy.getDescription()); // TODO compile template
        os.setUrn(strategy.getUrn());

        for (var operation : strategy.getOperations()) {

          var op = new ObservationStrategyImpl.OperationImpl();
          op.setType(operation.getType());

          if (operation.getObservable() != null) {
            op.setObservable(
                operation.getObservable().getPatternVariables().isEmpty()
                    ? reasoner.declareObservable(operation.getObservable())
                    : reasoner.declareObservable(operation.getObservable(), patternVariableValues));
          }
          for (var function : operation.getFunctions()) {
            op.getContextualizables().add(new ContextualizableImpl(function));
          }
          os.getOperations().add(op);
        }
        ret.add(os);
      }
    }

    return ret;
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
    return languageService.execute(function, scope, Object.class);
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
}
