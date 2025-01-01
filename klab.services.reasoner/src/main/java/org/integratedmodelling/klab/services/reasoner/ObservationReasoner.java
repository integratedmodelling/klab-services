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

  private Reasoner reasoner;
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
        if ((collectiveOnly && !observable.isCollective())
            || (nonCollectiveOnly && observable.isCollective())) {
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
        .loadExtensions("org.integratedmodelling.klab.services" + ".reasoner.functors");
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

  //    public void loadWorldview(Worldview worldview) {
  //        for (var strategyDocument : worldview.getObservationStrategies()) {
  //            for (var strategy : strategyDocument.getStatements()) {
  //                observationStrategies.add(new ObservationStrategyImpl(strategy, reasoner));
  //            }
  //        }
  //
  //        this.observationStrategies.sort(Comparator.comparingInt(ObservationStrategy::rank));
  //    }

  //
  //    public List<ObservationStrategy> inferStrategies(Observable observable, ContextScope scope)
  // {
  //
  //        List<ObservationStrategy> ret = new ArrayList<>();
  //
  //        /*
  //         * If observable is abstract due to abstract traits, strategy is to find a model
  //         * for each of the traits, then defer the resolution of a concretized observable
  //         * into an OR-joined meta-observable,which will use a merger model with all the
  //         * independent observables as dependencies.
  //         */
  //        var generics = observable.getGenericComponents();
  //        var resources = reasoner.serviceScope().getService(ResourcesService.class);
  //        var traits = observable.is(SemanticType.QUALITY)
  //                     ? reasoner.directAttributes(observable)
  //                     : reasoner.directTraits(observable);
  //
  //        /*
  //        TODO with traits, we should switch off the direct resolution if the unmodified
  // observation is
  //         available for the naked observable, and switch directly to trait resolution
  //         */
  //
  //        /**
  //         * FIXME check if the "one strategy at a time" technique works in all situations
  //         */
  ////        int rank = 0;
  ////        if (generics.isEmpty() && !observable.isAbstract()) {
  ////            ret.addAll(getDirectConcreteStrategies(observable, scope, rank++));
  ////        }
  ////
  ////        // TODO deferred strategies for unary operators that have built-in dereifiers
  ////        //  defer to the argument(s), add distance computation
  ////        ObservationStrategyObsolete opDeferred = null;
  ////        if (observable.is(SemanticType.DISTANCE)) {
  ////            opDeferred = ObservationStrategyObsolete.builder(Observable.promote(reasoner
  // .describedType(observable)))
  ////                    .withCost(rank++)
  ////                    .withOperation(ObservationStrategyObsolete.Operation.APPLY, (ServiceCall)
  // null)
  ////                    .build();
  ////        } else if (observable.is(SemanticType.NUMEROSITY)) {
  ////            opDeferred = ObservationStrategyObsolete.builder(Observable.promote(reasoner
  // .describedType(observable)))
  ////                    .withCost(rank++)
  ////                    .withOperation(ObservationStrategyObsolete.Operation.APPLY, (ServiceCall)
  // null)
  ////                    .build();
  ////        } else if (observable.is(SemanticType.PRESENCE)) {
  ////            opDeferred = ObservationStrategyObsolete.builder(Observable.promote(reasoner
  // .describedType(observable)))
  ////                    .withCost(rank++)
  ////                    .withOperation(ObservationStrategyObsolete.Operation.APPLY, (ServiceCall)
  // null)
  ////                    .build();
  ////        } else if (observable.is(SemanticType.PERCENTAGE) ||
  // observable.is(SemanticType.PROPORTION)) {
  //////            opDeferred =
  // ObservationStrategy.builder(Observable.promote(reasoner.describedType
  // (observable)))
  //////                    .withCost(rank++)
  //////                    .withOperation(ObservationStrategy.Operation.APPLY, (ServiceCall) null)
  //////                    .build();
  ////        } else if (observable.is(SemanticType.RATIO)) {
  //////            opDeferred =
  // ObservationStrategy.builder(Observable.promote(reasoner.describedType
  // (observable)))
  //////                    .withCost(rank++)
  //////                    .withOperation(ObservationStrategy.Operation.APPLY, (ServiceCall) null)
  //////                    .build();
  ////        }
  ////
  ////        if (opDeferred != null) {
  ////            ret.add(ObservationStrategyObsolete.builder(observable).withStrategy
  // (ObservationStrategyObsolete.Operation.RESOLVE, opDeferred).withCost(rank).build());
  ////        }
  ////
  ////        if (!traits.isEmpty()) {
  ////            ret.addAll(getTraitConcreteStrategies(observable, traits, scope, rank++));
  ////        }
  ////
  ////        if (observable.is(SemanticType.QUALITY) && reasoner.directInherent(observable) !=
  // null) {
  ////            ret.addAll(getInherencyStrategies(observable, scope, rank++));
  ////        }
  ////
  ////        if (!observable.getValueOperators().isEmpty()) {
  ////            Observable withoutOperators =
  // observable.builder(scope).withoutValueOperators().build();
  ////            return addValueOperatorStrategies(inferStrategies(withoutOperators, scope),
  ////                    observable.getValueOperators(), rank);
  ////        }
  //
  //
  ////        var traitStrategies = getTraitConcreteStrategies(ret, observable, traits);
  ////
  ////        if (generics == null) {
  ////            ret.addAll(traitStrategies);
  ////        } else {
  ////            ret.addAll(getGenericConcreteStrategies(ret, observable, generics));
  ////        }
  ////
  ////        ret = insertSpecializedDeferralStrategies(ret, observable, scope);
  //
  //        // TODO sort by rank
  //
  //        return ret;
  //
  //    }
  //
  //    private List<ObservationStrategyObsolete> insertSpecializedDeferralStrategies
  //    (List<ObservationStrategyObsolete> ret,
  //                                                                                  Observable
  // observable,
  //                                                                                  ContextScope
  // scope,
  //                                                                                  int rank) {
  //        // TODO
  //        return ret;
  //    }
  //
  //    private List<ObservationStrategyObsolete> addValueOperatorStrategies
  //    (List<ObservationStrategyObsolete> ret,
  //
  // List<Pair<ValueOperator,
  //                                                                         Object>> observable,
  // int
  //                                                                         rank) {
  //        // TODO add new strategies to the previous one; increment their rank by 1
  //        return ret;
  //    }
  //
  //    /**
  //     * Inherency-based strategies are for qualities distributed to inherent contexts through
  //     <code>of</code>,
  //     * resolved by deferring the inherent objects with their inherent qualities and inserting an
  //     aggregating
  //     * core function for the main observable.
  //     *
  //     * @param observable
  //     * @param scope
  //     * @param rank
  //     * @return
  //     */
  //    private List<ObservationStrategyObsolete> getInherencyStrategies(Observable observable,
  //    ContextScope scope,
  //                                                                     int rank) {
  //        // TODO
  //        return Collections.emptyList();
  //    }
  //
  //    /**
  //     * Indirect resolution of concrete traits in qualities and instances
  //     * <p>
  //     * For qualities: TODO
  //     * <p>
  //     * For instances: solution for (e.g.) landcover:Urban infrastructure:City should be
  //     *
  //     * <pre>
  //     * DEFER infrastructure:City [instantiation]
  //     *      RESOLVE landcover:LandCoverType of infrastructure:City [classification]
  //     *      APPLY filter(trait=landcover:Urban, artifact=infrastructure:City) // -> builds the
  //     filtered view
  //     * </pre>
  //     * <p>
  //     * The solution for >1 traits, e.g. im:Big landcover:Urban infrastructure:City, simply
  // resolves
  //     the first
  //     * trait and leaves the other in the deferred observation:
  //     * <pre>
  //     * DEFER landcover:Urban infrastructure:City [instantiation]
  //     *      RESOLVE im:SizeRelated of landcover:Urban infrastructure:City [classification]
  //     *      APPLY klab.core.filter.objects(trait=im:Big, artifact=landcover:Urban
  // infrastructure:City)
  //     * </pre>
  //     * <p>
  //     * as the recursion implicit in DEFER takes care of the strategy for landcover:Urban
  //     *
  //     * @param observable
  //     * @param traits
  //     * @param scope
  //     * @param rank
  //     * @return
  //     */
  //    private List<ObservationStrategyObsolete> getTraitConcreteStrategies(Observable observable,
  //                                                                         Collection<Concept>
  // traits,
  //                                                                         Scope scope,
  //                                                                         int rank) {
  //        List<ObservationStrategyObsolete> ret = new ArrayList<>();
  //        Concept toResolve = traits.iterator().next();
  //
  //        var nakedObservable = observable.builder(scope).without(toResolve).build();
  //        var builder = ObservationStrategyObsolete.builder(observable).withCost(rank);
  //
  //        // TODO this is the strategy for instances, not for qualities
  //
  //        var deferred = ObservationStrategyObsolete.builder(nakedObservable).withCost(rank);
  //        var baseTrait = reasoner.baseParentTrait(toResolve);
  //        if (baseTrait == null) {
  //            throw new KlabInternalErrorException("no base trait for " + toResolve);
  //        }
  //        deferred
  //                .withOperation(ObservationStrategyObsolete.Operation.OBSERVE,
  //
  // Observable.promote(baseTrait).builder(scope).of(nakedObservable.getSemantics
  //                        ()).build());
  //
  //        if (observable.is(SemanticType.QUALITY)) {
  //
  //            // TODO probably not necessary, the model seems generic enough
  //
  //            // The resolve above has produced a quality of x observation, we must resolve the
  // quality
  //            // selectively
  //            // where that quality is our target
  //            // TODO defer to concrete dependencies using CONCRETIZE which creates the concrete
  // deps and
  //            //  applies
  //            //  an implicit WHERE to their resolution; then APPLY an aggregator for the main
  //            //  observation. NO - CONCRETIZE is for generic quality observables. Generic
  // countable
  //            observables
  //            //  remain one dependency, which triggers classification and then resolution of the
  //            individual
  //            //  classes on
  //            //  filtered groups.
  ////            deferred.withOperation(ObservationStrategy.Operation.CONCRETIZE, )
  //
  //        } else {
  //            deferred
  //                    // filter the instances to set the ones with the trait in context
  //                    .withOperation(ObservationStrategyObsolete.Operation.APPLY,
  //                            // FIXME this must be the FILTER call to filter instances with
  // toSolve as
  //                            //  arguments
  //                            (ServiceCall) null)
  //                    // Explain the instantiated classification, deferring the resolution of the
  //                    attributed
  //                    // trait within the instances
  //                    .withStrategy(ObservationStrategyObsolete.Operation.RESOLVE,
  //                            ObservationStrategyObsolete.builder(
  //                                            Observable.promote(toResolve).builder(scope)
  //                                                    .of(nakedObservable.getSemantics())
  //                                                    .optional(true).build())
  //                                    .withCost(rank)
  //                                    .build());
  //        }
  //
  //        builder.withStrategy(ObservationStrategyObsolete.Operation.RESOLVE, deferred.build());
  //
  //        ret.add(builder.build());
  //
  //        return ret;
  //    }
  //
  //    private List<ObservationStrategyObsolete> getGenericConcreteStrategies
  //    (List<ObservationStrategyObsolete> strategies,
  //                                                                           Observable
  // observable,
  //                                                                           Collection<Concept>
  //                                                                           generics, int rank) {
  //        List<ObservationStrategyObsolete> ret = new ArrayList<>();
  //        return ret;
  //    }
  //
  ////    /**
  ////     * Direct strategies have rank 0
  ////     */
  ////    private Collection<? extends ObservationStrategy> getDirectConcreteStrategies(Observable
  // observable,
  ////
  // Scope
  // scope, int rank) {
  ////
  ////        List<ObservationStrategy> ret = new ArrayList<>();
  ////
  ////        /*
  ////         * first course of action for concrete observables is always direct observation
  // (finding a
  // model and
  ////         * contextualizing it)
  ////         */
  ////        var builder =
  ////                ObservationStrategyObsolete.builder(observable)
  ////                        .withCost(rank);
  ////
  ////        /**
  ////         * If we are resolving a relationship, we need the targets of the relationship first
  // of all
  ////         */
  ////        if (observable.is(SemanticType.RELATIONSHIP)) {
  ////            for (var target : reasoner.relationshipTargets(observable)) {
  ////                builder.withOperation(ObservationStrategyObsolete.Operation.OBSERVE,
  // Observable
  // .promote(target));
  ////            }
  ////        }
  ////
  ////        // main target
  ////        builder.withOperation(ObservationStrategyObsolete.Operation.OBSERVE, observable);
  ////
  ////        // defer resolution of the instances
  ////        if (observable.getDescriptionType() == DescriptionType.INSTANTIATION) {
  ////            builder.withStrategy(ObservationStrategyObsolete.Operation.RESOLVE,
  ////                    ObservationStrategyObsolete.builder(observable.builder(scope).as
  // (DescriptionType.ACKNOWLEDGEMENT)
  ////                                    .optional(true).build())
  ////                            .withCost(rank)
  ////                            .build());
  ////        }
  ////
  ////        ret.add(builder.build());
  ////
  ////        return ret;
  ////    }
  //
  ////    /*
  ////     * these should be obtained from the classpath. Plug-ins may extend them.
  ////     */
  ////    List<ObservationStrategyPattern> strategies = new ArrayList<>();
  ////        for(
  ////    ObservationStrategyPattern pattern :this.observationStrategyPatterns)
  ////
  ////    {
  ////        if (pattern.matches(observable, scope)) {
  ////            strategies.add(pattern);
  ////        }
  ////    }
  ////
  ////        if(!strategies.isEmpty())
  ////
  ////    {
  ////        strategies.sort(new Comparator<>() {
  ////
  ////            @Override
  ////            public int compare(ObservationStrategyPattern o1, ObservationStrategyPattern o2) {
  ////                return Integer.compare(o1.getCost(observable, scope), o2.getCost(observable,
  // scope));
  ////            }
  ////        });
  ////        for (ObservationStrategyPattern strategy : strategies) {
  ////            ret.add(strategy.getStrategy(observable, scope));
  ////        }
  ////    }
  ////
  ////        return ret;
  //// }
}
