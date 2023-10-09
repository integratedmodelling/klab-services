package org.integratedmodelling.klab.services.reasoner;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Specialized functions to infer observation strategies. Kept separately for clarity as this is a crucial k.LAB
 * component, although they are part of the reasoner services.
 */
public class ObservationReasoner {
    private Reasoner reasoner;
    private Collection<ObservationStrategyPattern> observationStrategyPatterns = new ArrayList<>();

    public ObservationReasoner(ReasonerService reasonerService) {
        this.reasoner = reasonerService;
    }


    public List<ObservationStrategy> inferStrategies(Observable observable, ContextScope scope) {

        List<ObservationStrategy> ret = new ArrayList<>();

        /*
         * If observable is abstract due to abstract traits, strategy is to find a model
         * for each of the traits, then defer the resolution of a concretized observable
         * into an OR-joined meta-observable,which will use a merger model with all the
         * independent observables as dependencies.
         */

        var generics = observable.getGenericComponents();
        var traits = observable.is(SemanticType.QUALITY) ? reasoner.attributes(observable) :
                reasoner.traits(observable);

        if (generics.isEmpty() && !observable.isAbstract()) {
            ret.addAll(getDirectConcreteStrategies(observable));
        }

        if (!observable.getValueOperators().isEmpty()) {
            Observable withoutOperators = observable.builder(scope).withoutValueOperators().buildObservable();
            return addValueOperatorStrategies(inferStrategies(withoutOperators, scope), observable.getValueOperators());
        }

//        var traitStrategies = getTraitConcreteStrategies(ret, observable, traits);
//
//        if (generics == null) {
//            ret.addAll(traitStrategies);
//        } else {
//            ret.addAll(getGenericConcreteStrategies(ret, observable, generics));
//        }
//
//        ret = insertSpecializedDeferralStrategies(ret, observable, scope);

        // TODO sort by rank

        return ret;

    }

    private List<ObservationStrategy> insertSpecializedDeferralStrategies(List<ObservationStrategy> ret,
                                                                          Observable observable, ContextScope scope) {
        // TODO
        return ret;
    }

    private List<ObservationStrategy> addValueOperatorStrategies(List<ObservationStrategy> ret,
                                                                 List<Pair<ValueOperator, Literal>> observable) {
        // TODO
        return ret;
    }

    private List<ObservationStrategy> getTraitConcreteStrategies(List<ObservationStrategy> strategies,
                                                                 Observable observable, Collection<Concept> traits) {
        List<ObservationStrategy> ret = new ArrayList<>();
        return ret;
    }

    private List<ObservationStrategy> getGenericConcreteStrategies(List<ObservationStrategy> strategies,
                                                                   Observable observable,
                                                                   Collection<Concept> generics) {
        List<ObservationStrategy> ret = new ArrayList<>();
        return ret;
    }

    /**
     * Direct strategies have rank 0
     */
    private Collection<? extends ObservationStrategy> getDirectConcreteStrategies(Observable observable) {

        List<ObservationStrategy> ret = new ArrayList<>();

        /*
         * first course of action for concrete observables is always direct observation (finding a model and
         * contextualizing it)
         */
        var builder = ObservationStrategy.builder(observable).withOperation(ObservationStrategy.Operation.OBSERVE
                , observable);

        // resolve the instances if instantiating
        if (observable.getDescriptionType() == DescriptionType.INSTANTIATION) {
            builder.withOperation(ObservationStrategy.Operation.RESOLVE,
                    observable.as(DescriptionType.ACKNOWLEDGEMENT));
        }

        ret.add(builder.build());

        return ret;
    }

//    /*
//     * these should be obtained from the classpath. Plug-ins may extend them.
//     */
//    List<ObservationStrategyPattern> strategies = new ArrayList<>();
//        for(
//    ObservationStrategyPattern pattern :this.observationStrategyPatterns)
//
//    {
//        if (pattern.matches(observable, scope)) {
//            strategies.add(pattern);
//        }
//    }
//
//        if(!strategies.isEmpty())
//
//    {
//        strategies.sort(new Comparator<>() {
//
//            @Override
//            public int compare(ObservationStrategyPattern o1, ObservationStrategyPattern o2) {
//                return Integer.compare(o1.getCost(observable, scope), o2.getCost(observable, scope));
//            }
//        });
//        for (ObservationStrategyPattern strategy : strategies) {
//            ret.add(strategy.getStrategy(observable, scope));
//        }
//    }
//
//        return ret;
//}
}
