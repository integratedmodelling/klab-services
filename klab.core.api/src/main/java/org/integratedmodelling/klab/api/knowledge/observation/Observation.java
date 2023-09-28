/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found
 * see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.knowledge.observation;

import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;

/**
 * The interface Observation, which is the semantic equivalent of an Artifact and once created in a k.LAB session, can
 * be made reactive by supplementing it with a behavior. Models may bind instantiated observations to actor files that
 * will provide behaviors for their instances (or a subset thereof). Once made reactive, they can interact with each
 * other and the system.
 * <p>
 * FIXME the API needs to lose a lot of weight
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Observation extends Knowledge, Artifact {

    /**
     * Return the observable.
     *
     * @return the observation's observable
     */
    Observable getObservable();

    /**
     * The observer that/who made the observation. May be a simple identity (like the user in the main scope) or a
     * DirectObservation from (this or another) scope, which also implements Identity. Never null.
     *
     * @return
     */
    Identity getObserver();

    /**
     * Return the scale where this is contextualized. It may differ from the scale of the context although the latter
     * should always contain the scale of all observations and observers in it.
     *
     * @return the observation's scale
     */
    @Override
    Scale getGeometry();

    /**
     * Return a view of this observation restricted to the passed locator, which is applied to the scale to obtain a new
     * scale, used as a filter to obtain the view. The result should be able to handle both conformant scaling (e.g. fix
     * one dimension) and non-conformant (i.e. one state maps to multiple ones with irregular extent coverage) in both
     * reading and writing.
     *
     * @param locator
     * @return a rescaled view of this observation
     * @throws IllegalArgumentException if the locator is unsuitable for the observation
     */
    Observation at(Locator locator);

//	/**
//	 * Observation may have been made in the context of another direct observation.
//	 * This will always return non-null in indirect observations, and may return
//	 * null in direct ones when they represent the "root" context.
//	 *
//	 * @return the context for the observation, if any.
//	 */
//	DirectObservation getContext();

//	/**
//	 * True if our scale has an observation of space with more than one state value.
//	 *
//	 * @return true if distributed in space
//	 */
//	boolean isSpatiallyDistributed();
//
//	/**
//	 * True if our scale has an observation of time with more than one state value.
//	 *
//	 * @return true if distributed in time.
//	 */
//	boolean isTemporallyDistributed();
//
//	/**
//	 * True if our scale has any implementation of time.
//	 *
//	 * @return if time is known
//	 */
//	boolean isTemporal();
//
//	/**
//	 * True if our scale has any implementation of space.
//	 *
//	 * @return if space is known
//	 */
//	boolean isSpatial();

    /**
     * Return the spatial extent, or null.
     *
     * @return the observation of space
     */
    Space getSpace();

//	/**
//	 * Return true if this observation has changes that happened after
//	 * initialization. Note that it is not guaranteed that a dynamic observation
//	 * knows it's dynamic before changes are reported, so observations may start
//	 * static and become dynamic later.
//	 *
//	 * @return
//	 */
//	boolean isDynamic();

//	/**
//	 * Time of creation according to context time, not to be confused with the
//	 * system creation time returned by {@link #getTimestamp()}.
//	 *
//	 * @return the time of creation
//	 */
//	long getCreationTime();

//	/**
//	 * Time of "exit", i.e. end of life of the observation according to context
//	 * time. If the context has no time or the object is current, this is -1L.
//	 *
//	 * @return the time of exit
//	 */
//
//	long getExitTime();

    static Observation EMPTY_OBSERVATION = new ObservationImpl() {

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

    public static Observation empty() {
        return EMPTY_OBSERVATION;
    }
}
