package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.BoxingValueResolver;
import org.integratedmodelling.klab.api.services.runtime.extension.ConceptValueResolver;
import org.integratedmodelling.klab.api.services.runtime.extension.DoubleValueResolver;
import org.integratedmodelling.klab.api.services.runtime.extension.IntValueResolver;
import org.ojalgo.concurrent.Parallelism;

import java.util.function.BiConsumer;

/**
 * <p>Holder of atomic "executors" that implement one or more contextualizers (sequential scalar ones are merged
 * into chains to avoid storage of intermediate products unless requested). In case of a scalar chain, the executor
 * function is optimized to avoid boxing when transferring data and storing them.</p>
 *
 * <p>Has two separate and equivalent call sets, one with debugging and one without, to avoid constant checking in
 * time-critical contextualizers. The debugging scalar chain executor creates additional observations to hold
 * intermediate values.</p>
 *
 * <p>The usage is through the implemented BiConsumer that takes the localized observations and scope. The body of the
 * function is supplied based on the contextualizer(s) and the logic is in the {@link DigitalTwin} that creates the
 * executor.</p>
 */
abstract class Executor implements BiConsumer<Observation, ContextScope> {

    enum Type {
        VALUE_RESOLVER, BOXING_VALUE_RESOLVER,
        OBSERVATION_RESOLVER, OBSERVATION_INSTANTIATOR, OBSERVATION_CHARACTERIZER, OBSERVABLE_CLASSIFIER,
        OBJECT_CLASSIFIER
    }

    Type type;
    Parallelism parallelism;

    public Executor(Type type, Parallelism parallelism) {
        this.type = type;
        this.parallelism = parallelism;
    }

    public Executor(Type type, Parallelism parallelism, IntValueResolver iresolver) {
        this(type, parallelism);
        // TODO
    }

    public Executor(Type type, Parallelism parallelism, DoubleValueResolver iresolver) {
        this(type, parallelism);
        // TODO
    }

    public Executor(Type type, Parallelism parallelism, ConceptValueResolver iresolver) {
        this(type, parallelism);
        // TODO
    }

    public Executor(Type type, Parallelism parallelism, BoxingValueResolver iresolver) {
        this(type, parallelism);
        // TODO
    }


    /**
     * Execute a distributed contextualization chain using non-boxing optimizations if all the functors are non-boxing,
     * or without if one or more are boxing contextualizers. This should be done by compiling an ad-hoc Groovy/Java
     * class.
     *
     * @param targetState
     * @param scope
     */
    protected void executeChain(State targetState, ContextScope scope) {
        // TODO find the strategy based on types of value resolvers and parallelism, then run it
        System.out.println("PUTUS CAESAR");
    }

    public Executor chain(IntValueResolver iresolver) {
        // TODO
        return this;
    }

    public Executor chain(DoubleValueResolver iresolver) {
        // TODO
        return this;
    }

    public Executor chain(ConceptValueResolver iresolver) {
        // TODO
        return this;
    }

    public Executor chain(BoxingValueResolver iresolver) {
        // TODO
        return this;
    }
}
