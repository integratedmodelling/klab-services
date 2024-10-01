/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of
 * the Affero
 * GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory
 *  of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author
 * tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategyObsolete;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.io.Serializable;
import java.util.List;
import java.util.Queue;

/**
 * Actuators are the computable dataflow elements; each of them creates and computes an observation. Within an
 * actuator, the individual runtime operations are represented by k.LAB {@link ServiceCall}s that serve as
 * entry points into the runtime, and derived from {@link Contextualizable} in k.LAB models. Before computing
 * an actuator, the runtime must ensure that all the components serving the needed service calls are
 * available, authorized and loaded, interacting appropriately with the {@link ResourcesService}s in the
 * scope. An actuator with no computation is just responsible for creating its observation, and only
 * non-dependent observables ("substantials", generating {@link DirectObservation}s) have the ability to just
 * "acknowledge" the observation without providing a computation strategy.
 * <p>
 * Actuators may be references, corresponding to "input ports" in other workflow systems and used only to
 * establish the chain of computation, and they must be computed before they are referenced. In a k.LAB
 * dataflow, references are always resolved and in their k.DL serialization the original actuator will always
 * be serialized before any references to it is inserted. Within an actuator that creates instances, the
 * "resolve" actuators are chosen to "explain" each instance, and they may appear in multiple implementations
 * linked to specific portions of the covered scale.
 * <p>
 * In a dataflow, the ID returned by {@link #getId()} becomes the base for the ID of the observation(s) that
 * it handles. If the observation is of a dependent the ID may be the same or derived through a known
 * transformation; otherwise instances of an instantiator will use it as a base for the ID, adding a
 * progressive counter. The ID is not unique as the ID of a reference actuator must be the same of its
 * referenced one, but it uniquely identifies an observable and its observation. Observations must be able to
 * trace their way back to the actuator and the other way around, without actually storing the objects, to
 * allow the dataflow to grow incrementally with successive queries and a coherent provenance graph to be
 * maintained.
 *
 * @author ferdinando.villa
 */
public interface Actuator extends Serializable, RuntimeAsset {

    /**
     * Name of the service call that encodes deferred resolution when that must be included in the
     * computation.
     */
    static final String DEFERRED_STRATEGY_CALL = "klab.internal.deferred";

    /**
     * The ID of the actuator must be the same as that of the observation it handles.
     *
     * @return the observation's ID.
     */
    long getId();

    /**
     * All actuators have a name that corresponds 1-to-1 to the semantics it was created to resolve
     * (observable reference name). The only case for duplication of the same name is when a direct
     * observation is made as instantiation (with type
     * <code>object</code>) and later, inside the instantiating actuator, as resolution
     * (with type <code>void</code>). References may provide a different name (alias) for the same actuator.
     *
     * @return the name
     */
    String getName();

    /**
     * The local name and "user-level" name for the actuator, correspondent to the simple name associated with
     * the semantics or to its renaming through 'named' when used as a dependency.
     *
     * @return the alias or null
     */
    String getAlias();

    /**
     * Each actuator reports the artifact type of the observation it produces. Pure resolvers (e.g. the
     * resolver for an object) are de facto void, but report the special RESOLVE type; VOID actuators resolve
     * views or predicates. Actuators whose type defines an occurrent are not run at initialization but are
     * just called upon to schedule temporal actions.
     *
     * @return
     */
    Artifact.Type getType();

    /**
     * If the actuator is deferred, the observable is not resolved but must be resolved within the parent
     * actuator (the deferring actuator) once the latter has produced observations. If the deferring actuator
     * is for a concrete direct observable, the deferred observable will be resolved within each instance; if
     * it's for an abstract observable, the deferred observable will be substituted with the results of
     * resolving each concrete observable resulting from its resolution, potentially applying its computations
     * to merge the results.
     *
     * @return
     */
    boolean isDeferred();

    /**
     * Although actuators belong to the runtime and use no reasoning, they have an observable, which in case
     * of pure non-semantic computations will be a non-semantic observable.
     *
     * @return
     */
    Observable getObservable();

    /**
     * Return all child actuators in order of declaration in the dataflow. This may not correspond to the
     * order of contextualization, which is computed by the runtime, although it is expected that child
     * actuators at the same level without mutual dependencies have a non-random order that should be honored
     * by serial runtimes.
     * <p>
     * In a runtime context, the child list may contain actuators that serve as inputs, outputs, references,
     * filters, and other dataflows from deferred resolutions.
     *
     * @return all the internal actuators in order of declaration.
     */
    public List<Actuator> getChildren();

    /**
     * Return the list of all computations in this actuator, or an empty list. If the actuator is a reference,
     * the list should be empty: any mediations are part of the referencing actuator's computations.
     * <p>
     * An internal service call named {@link #DEFERRED_STRATEGY_CALL} should be used to specify deferred
     * observation strategies, with the {@link ObservationStrategyObsolete} communicated by the resolver as
     * default {@link ServiceCall#DEFAULT_PARAMETER_NAME} parameter. The runtime will resolve the strategy
     * once the context observation has been made and insert the resulting dataflow's actuators as children of
     * the current one.
     *
     * @return all computations. Never null, possibly empty.
     */
    List<ServiceCall> getComputation();

    /**
     * If true, this actuator represents a named input that will need to be connected to an artifact from the
     * computation context.
     *
     * @return
     */
    boolean isInput();

    /**
     * If true, this actuator represents an exported output that will need to be connected to an artifact from
     * the computation context.
     *
     * @return
     */
    boolean isOutput();

    /**
     * If true, this actuator is a reference to another which has been defined before it in order of
     * computation, and has produced its observation by the time this actuator is called into a
     * contextualization. It only serves as a placeholder with a possibly different alias to define the local
     * identifier of the original observation. Reference actuators are otherwise empty, with no children and
     * no computation.
     *
     * @return
     */
    boolean isReference();

    /**
     * The actuator's geometry is a merge of the native coverage of all models downstream of the actuator.
     * Dealing with different coverages within a model is the responsibility of the runtime. A null or empty
     * coverage returned here means universal coverage, as no actuators are output by a resolution that does
     * not succeed.
     *
     * @return the merged coverage of all models in or below this actuator.
     */
    Geometry getCoverage();

    /**
     * Each actuator can bring with itself arbitrary data that can be referenced by ID in the computations
     * downstream. This includes tables such as classifications and lookup tables defined in models and needed
     * for computation.
     *
     * @return
     */
    Parameters<String> getData();

    /**
     * The observer on behalf of whom the observations are made. If null, the "objective" point of view of the
     * current session user is meant.
     *
     * @return
     */
    String getObserver();

    /**
     * An actuator in a dataflow that is still being resolved may have deferrals, which are specific
     * observables that must be resolved in the context of the actuator's output observation, and are passed
     * to the resolver after making the observation and setting it as the context of resolution. The resulting
     * dataflow is appended to the actuator after resolution.
     * <p>
     * A dataflow that has deferrals anywhere in the chain cannot be serialized as it is not a valid
     * algorithm.
     * </p>
     *
     * @return
     */
    Queue<ObservationStrategy> getDeferrals();
}
