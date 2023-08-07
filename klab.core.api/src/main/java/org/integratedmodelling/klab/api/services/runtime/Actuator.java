/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serializable;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Plan;

/**
 * Actuators are first-class nodes in dataflows. In other workflow systems (e.g.
 * Ptolemy), an actuator corresponds to a composite actor dedicated to an
 * artifact and representing a k.IM model (explicit or derived by the k.LAB AI).
 * The "atomic" actors are contextualizers, represented by k.LAB
 * {@link org.integratedmodelling.ServiceCall.api.IServiceCall}s that serve as
 * entry points into the runtime, and derived from {@link Contextualizable} in
 * k.LAB models.
 * <p>
 * Some actuators may be references, corresponding to "input ports" in other
 * workflow systems. In a k.LAB computation, references are always resolved and
 * the implementing which case the original actuator will always be serialized
 * before any references to it.
 * <p>
 * In a dataflow, the ID returned by {@link #getId()} becomes the base for the
 * ID of the observation(s) that it handles. If the observation is of a
 * dependent the ID may be the same or derived through a known transformation;
 * otherwise instances of an instantiator will use it as a base for the ID. The
 * ID is not unique: the ID of a reference actuator must be the same of its
 * referenced one. Observations must be able to trace their way back to the
 * actuator and the other way around, without actually storing the objects, so
 * that the dataflow can grow incrementally and provenance can be inspected.
 *
 * 
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Actuator extends Plan, Serializable {

	/**
	 * All actuators have a name that corresponds 1-to-1 to the semantics it was
	 * created to resolve (observable reference name). The only case for duplication
	 * of the same name is when a direct observation is made as instantiation (with
	 * type <code>object</code>) and later, inside the instantiating actuator, as
	 * resolution (with type <code>void</code>). References may provide a different
	 * name (alias) for the same actuator.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * The local name and "user-level" name for the actuator, correspondent to the
	 * simple name associated with the semantics or to its renaming through 'named'
	 * when used as a dependency.
	 *
	 * @return the alias or null
	 */
	String getAlias();

	// /**
	// * Return the name with which the passed observable is known within this
	// * actuator, or null if the observable is not referenced in it.
	// *
	// * @param observable
	// * @return
	// */
	// String getAlias(Observable observable);

	/**
	 * Each actuator reports the artifact type of the observation it produces. Pure
	 * resolvers (e.g. the resolver for an object) are de facto void, but report the
	 * special RESOLVE type; VOID actuators resolve views or predicates. Actuators
	 * whose type defines an occurrent are not run at initialization but are just
	 * called upon to schedule temporal actions.
	 * 
	 * @return
	 */
	Artifact.Type getType();

	/**
	 * Although actuators belong to the runtime and use no reasoning, they have an
	 * observable, which in case of pure non-semantic computations will be a
	 * non-semantic observable.
	 * 
	 * @return
	 */
	Observable getObservable();

	/**
	 * Return all child actuators in order of declaration in the dataflow. This may
	 * not correspond to the order of contextualization, which is computed by the
	 * runtime, although it is expected that child actuators at the same level
	 * without mutual dependencies have a non-random order that should be honored by
	 * serial runtimes.
	 * <p>
	 * In a runtime context, the child list may contain actuators that serve as
	 * inputs, outputs, references, filters, and other dataflows from deferred
	 * resolutions.
	 * 
	 * @return all the internal actuators in order of declaration.
	 */
	public List<Actuator> getChildren();

	/**
	 * Return the list of all computations in this actuator, or an empty list. If
	 * the actuator is a reference, the list should be empty: any mediations are
	 * part of the referencing actuator's computations.
	 *
	 * @return all computations. Never null, possibly empty.
	 */
	List<ServiceCall> getComputation();

	/**
	 * If true, this actuator represents a named input that will need to be
	 * connected to an artifact from the computation context.
	 * 
	 * @return
	 */
	boolean isInput();

	/**
	 * If true, this actuator represents an exported output that will need to be
	 * connected to an artifact from the computation context.
	 * 
	 * @return
	 */
	boolean isOutput();

	/**
	 * If true, this actuator is a reference to another which has been defined
	 * before it in order of computation, and has produced its observation by the
	 * time this actuator is called into a contextualization. It only serves as a
	 * placeholder with a possibly different alias to define the local identifier of
	 * the original observation. Reference actuators are otherwise empty, with no
	 * children and no computation.
	 * 
	 * @return
	 */
	boolean isReference();

	/**
	 * The actuator's geometry is a merge of the native coverage of all models
	 * downstream of the actuator. Dealing with different coverages within a model
	 * is the responsibility of the runtime. A null or empty coverage returned here
	 * means universal coverage, as no actuators are output by a resolution that
	 * does not succeed.
	 * 
	 * @return the merged coverage of all models in or below this actuator.
	 */
	Geometry getCoverage();

	/**
	 * Each actuator can bring with itself arbitrary data that can be referenced by
	 * ID in the computations downstream. This includes tables such as
	 * classifications and lookup tables defined in models and needed for
	 * computation.
	 * 
	 * @return
	 */
	Parameters<String> getData();

	/**
	 * The observer on behalf of whom the observations are made. If null, the
	 * "objective" point of view of the current session user is meant.
	 * 
	 * @return
	 */
	String getObserver();

}
