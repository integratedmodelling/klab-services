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
package org.integratedmodelling.klab.api.knowledge.observation.scale;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.Scope;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The Interface Scale.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Scale extends Geometry, Topology<Scale> {

    /**
     * We deal with space and time in all natural systems, so we expose these to ease API use.
     *
     * @return the space, or null
     */
    Space getSpace();

    /**
     * We deal with space and time in all natural systems, so we expose these to ease API use.
     *
     * @return the time, or null
     */
    Time getTime();

    /**
     * True if we have time and the time topology determines more than a single state. It's also in IObservation, but
     * it's convenient to duplicate it here too.
     *
     * @return true if distributed in time
     */
    boolean isTemporallyDistributed();

    /**
     * True if we have space and the space topology determines more than a single state. It's also in IObservation, but
     * it's convenient to duplicate it here too.
     *
     * @return true if distributed in space
     */
    boolean isSpatiallyDistributed();

    /**
     * Total number of extents available in this Scale. Note that in principle there may be more extents than just space
     * and/or time, although this is not supported at the moment. Read the non-existing documentation.
     *
     * @return the number of extents for this topology
     */
    int getExtentCount();

    /**
     * Return the list of extents, ordered by contextualization priority (time, if present, will always be first).
     *
     * @return the extents
     */
    List<Extent<?>> getExtents();

    /**
     * Return true only if he scale has > 0 extents and any of them is empty, so that the coverage of any other scale
     * can only be 0.
     *
     * @return true if scale cannot be the context for any observation.
     */
    boolean isEmpty();

//	/**
//	 * Merge in another scale (possibly limited to specified extents) to return a
//	 * new scale that best represents the common traits in both, seeing the passed
//	 * scale as a constraint. Used to build the "common" scale of a dataflow before
//	 * contextualization, where the passed scale is that of the desired context and
//	 * this is the scale of a model or computation used in it.
//	 * 
//	 * In detail, for each extent of the outgoing scale:
//	 * <ul>
//	 * <li>If this does not have an extent that the passed scale has, the result
//	 * should adopt it as is.</li>
//	 * <li>If this has an extent that the passed scale does not have, the result
//	 * should <em>not</em> contain it.</li>
//	 * <li>Boundaries should be inherited from the passed scale. The result can
//	 * shrink but it cannot grow beyond the common boundaries.</li>
//	 * <li>If this is distributed in the common extents, the result should stay
//	 * distributed. If the incoming scale is distributed and this is not, the result
//	 * should become distributed. If both are distributed, choices of representation
//	 * may need to be made: the result's representation should be the incoming one
//	 * as much as possible, to prevent costly mediations.</li>
//	 * <li>If the result is distributed, the resolution should be our resolution if
//	 * this was distributed, or the incoming resolution if not.</li>
//	 * </ul>
//	 * 
//	 * @param scale      the scale to merge in
//	 * @param dimensions the dimension on which to perform the merge; if no
//	 *                   dimensions are passed, merge all dimensions
//	 */
//	public Scale mergeContext(Scale scale, Dimension.Type... dimensions);

    /**
     * {@inheritDoc}
     * <p>
     * Return a new scale merging all extents from the passed parameter. The extents of the merged in scale are
     * authoritative in terms of extent; granularity is negotiated as defined by each extent individually.
     * <p>
     * Extents in common are merged according to how the merge is implemented; any extents that are in one scale and not
     * the other are left in the returned scale as they are.
     * <p>
     * Must not modify the original scales.
     */
    Geometry merge(Scale other, LogicalConnector how);

    /**
     * Return the scale at the beginning of time, or the scale itself if there is no time at all.
     */
    Scale initialization();

    /**
     * Return the scale after the end of contextualization. This scale is not produced by the scale iterator, and is
     * used during scheduling.
     *
     * @return
     */
    Scale termination();

    /**
     * Get the extent of the specified type, or null.
     *
     * @param extentType
     * @return
     */
    Extent<?> extent(Dimension.Type extentType);

    /**
     * Return a new scale with the passed dimension. This can be used to substitute an extent of the same type or to add
     * an extent that wasn't there. This method creates a new scale and the extents must be copied to the new one.
     *
     * @param extent
     * @return
     */
    Scale with(Extent<?> extent);

    /**
     * <p>Return a new scale without the passed dimension. This method must not copy the extents: it's meant as a view
     * over the same scale, not a new scale. If an independent scale is wanted, call without() then call copy() on
     * it.</p>
     *
     * <p>The result can be used in an outer for() loop when a particular dimension must be
     * iterated over but the core implementation handles others:</p>
     *
     * <pre>
     * for (Scale otherDims : scale.without(Geometry.Dimension.Type.SPACE) {
     *
     * 		... code that sets the context from dimensions other than space
     *
     * 		for (Scale space : otherDims) {
     * 			... code that goes over space only* 		}
     * }
     * </pre>
     *
     * @param dimension
     * @return
     */
    Scale without(Geometry.Dimension.Type dimension);

    /**
     * The at method mandatorily returns a scale.
     */
    @Override
    Scale at(Locator dimension);

    /**
     * Return the same scale but with multiplicity 1 and all extents collapsed to their containing extent.
     *
     * @param dimensions select the dimensions to collapse. Pass none to collapse everything.
     * @return
     */
    Scale collapse(Dimension.Type... dimensions);

    /**
     * Produce another scale with the passed extent, merging it with any existing one if it's present.
     *
     * @param extent
     * @return
     */
    Scale mergeExtent(Extent<?> extent);

    public static Scale create(String geometrySpecifications) {
        return create(Geometry.create(geometrySpecifications));
    }

    public static Scale create(Collection<Extent<?>> extents) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
        }
        return configuration.createScaleFromExtents(extents);
    }

    public static Scale create(Extent<?>... extents) {
        return extents == null ? create(Geometry.EMPTY) : create(Arrays.asList(extents));
    }

    public static Scale create(Geometry geometry) {
        return geometry == null ? null : create(geometry, null);
    }

    /**
     * Passing the scope enables resolution of grid or resource URNs
     *
     * @param geometry
     * @param scope
     * @return
     */
    public static Scale create(Geometry geometry, Scope scope) {
        if (geometry instanceof Scale) {
            return (Scale) geometry;
        }
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
        }
        return configuration.promoteGeometryToScale(geometry, scope);
    }

    public static Scale empty() {
        return create(Geometry.EMPTY);
    }

}
