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
package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;

/**
 * A {@code DataArtifact} is an {@link Artifact} that is typed, owns storage and admits {@link Locator}s as indices for
 * getting and setting POD values in it. The storage must be conformant with the {@link Geometry#size() size} and
 * dimensions of the linked {@link Geometry geometry}.
 * <p>
 * According to the context of computation, the size of a data artifact may differ from {@link Geometry#size()}. For
 * example, a non-dynamic state in a dynamic context (where time advances but the observable cannot be inferred to
 * change in the context) may only receive updates in case of event-related modifications. In such cases the state may
 * only contain the time dimensions where change has happened.
 * <p>
 *
 * TODO unused at the moment. Revise and incorporate the logic into the modern API.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface DataArtifact extends Artifact {

    enum ValuePresentation {

        /**
         * Artifact value is a single value of the specified type.
         */
        VALUE,

        /**
         * Artifact value is presented as a discrete, empirical probability distribution assigning different
         * probabilities to each value, with probabilities summing up to 1.
         */
        PROBABILITY_DISTRIBUTION,

        /**
         * Artifact value is presented as a table, i.e. a multi-entry map with a value column and one or more keys to be
         * matched outside the context.
         */
        TABLE
    }

    /**
     * Get the POD object pointed to by the locator. If the locator implies mediation, this should be supported. If the
     * locator is incompatible with the geometry, throw an exception.
     *
     * @param index a locator for the state. If the locator implies mediation, propagation or aggregation should be
     *              done.
     * @return value at index
     * @throws java.lang.IllegalArgumentException if the locator is not compatible with the artifact's geometry.
     */
    Object get(Locator index);

    /**
     * Get the POD object pointed to by the locator. If the locator implies mediation, this should be supported. If the
     * locator is incompatible with the geometry, throw an exception.
     *
     * @param index a locator for the state. If the locator implies mediation, propagation or aggregation should be
     *              done.
     * @param cls   the class of the result we want
     * @param <T>   a T object.
     * @return value at index
     * @throws java.lang.IllegalArgumentException if the locator is not compatible with the artifact's geometry.
     */
    <T> T get(Locator index, Class<T> cls);

    /**
     * Total number of values. Must be compatible with the size of the dimensions of the underlying geometry.
     *
     * @return total count of states
     */
    long size();

    /**
     * If the individual values can be matched to an interpretive key, return it here.
     *
     * @return the data key, or null.
     */
    DataKey getDataKey();

    /**
     * Return a value aggregated over the passed geometry and converted to the passed type if necessary and possible.
     *
     * @param geometry
     * @param cls
     * @return the aggregated value
     * @throws IllegalArgumentException if the type can't fit the data or the geometry is not covered by the original
     *                                  geometry.
     */
    <T> T aggregate(Locator geometry, Class<? extends T> cls);

}
