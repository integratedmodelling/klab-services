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

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * The interface Observation, which is the semantic equivalent of an Artifact and represents an observable in
 * the observation graph of a k.LAB context. Once created in a k.LAB session, it can be made reactive by
 * supplementing it with a behavior, which will create an agent accessible through the context scope focused
 * on the observation. Models may bind instantiated observations to actor files that will provide behaviors
 * for their instances (or a subset thereof). Once made reactive, they can interact with each other and the
 * system.
 * <p>
 * TODO we could just use Observation (abstract) + DirectObservation (rename to Substantial) and State, then
 *  everything else is taken care of by the semantics (folder == getObservable().isCollective()), the DT
 *  and its graph.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Observation extends Knowledge, Artifact, Resolvable, RuntimeAsset {

    static final long UNASSIGNED_ID = -1;

    default RuntimeAsset.Type classify() {
        return RuntimeAsset.Type.OBSERVATION;
    }

    /**
     * The ID of an observation is a positive long for efficiency. Paths such as 3.44.234 identify observation
     * hierarchies to reconstruct scopes. An ID of -1L means that the observation is unassigned to the
     * observation graph.
     *
     * @return
     */
    long getId();

    /**
     * A name should never be null, although only substantials have the name as a defining feature. Names do
     * not need to be unique or conform to any syntax rule.
     *
     * @return
     */
    String getName();

    /**
     * Return the observable.
     *
     * @return the observation's observable
     */
    Observable getObservable();

    /**
     * The observer that/who made the observation. May be a simple identity (like the user in the main scope)
     * or a DirectObservation from (this or another) scope, which also implements Identity. Never null.
     *
     * @return
     */
    Identity getObserver();

    /**
     * True if the observation has been resolved. This will be false until the resolution task with the same
     * ID has finished. Dependent observation that are unresolved make the context inconsistent. Substantials
     * remain usable in an unresolved state.
     *
     * @return
     */
    boolean isResolved();

    /**
     * Return a view of this observation restricted to the passed locator, which is applied to the scale to
     * obtain a new scale, used as a filter to obtain the view. The result should be able to handle both
     * conformant scaling (e.g. fix one dimension) and non-conformant (i.e. one state maps to multiple ones
     * with irregular extent coverage) in both reading and writing.
     *
     * @param locator
     * @return a rescaled view of this observation
     * @throws IllegalArgumentException if the locator is unsuitable for the observation
     */
    Observation at(Locator locator);

    static Observation EMPTY_OBSERVATION = new ObservationImpl() {

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

    public static Observation empty() {
        return EMPTY_OBSERVATION;
    }

//    Geometry getObserverGeometry();

    Object getValue();
}
