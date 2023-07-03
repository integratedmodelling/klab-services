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
package org.integratedmodelling.klab.api.knowledge.observation.scale;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

/**
 * A {@code IExtent} is a semantically aware {@link Dimension geometry
 * dimension} that represents an observation of the topology it describes.
 * {@code IExtent}s make up the dimensions of the semantically aware
 * {@link org.integratedmodelling.klab.api.data.Geometry} represented by
 * {@link org.integratedmodelling.klab.api.Scale.scale.IScale}.
 *
 * In a {@code IExtent}, the {{@link #size()} will never return
 * {IGeometry#UNDEFINED} and the shape returned by {{@link #shape()} will never
 * contain undefined values.
 *
 * {@code IExtent}s can be used as {@link Locator locators} to address the value
 * space of observations.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Extent<T extends TopologicallyComparable<T>> extends Locator, Topology<T>, Geometry.Dimension {

	/**
	 * Locate the extent and return another with the original located extent and
	 * offsets set in. Can be passed another extent (e.g. a point to locate a cell
	 * in a grid space), one or more integer locators, a time period, or anything
	 * that can be understood by the extent.
	 * 
	 * @param locator
	 * @return the extent, or null if location is impossible.
	 */
	T at(Object... locators);

	/**
	 * Each extent must be able to return a worldview-dependent integer scale rank,
	 * usable to constrain model retrieval to specific scales. In spatial extents
	 * this corresponds to something like a "zoom level". The worldview establishes
	 * the scale for the ranking; otherwise, no assumptions are made on the value
	 * except that higher values correspond to smaller extents.
	 *
	 * The worldview defines this using numeric restrictions on the data property
	 * used to annotate scale constraints and establishes the range and granularity
	 * for the ranking.
	 *
	 * @return an integer summarizing the extent's size within the range covered by
	 *         the worldview
	 */
	int getRank();

	/**
	 * Collapse the multiplicity and return the extent that represents the full
	 * extent of our topology in one single state. This extent may not be of the
	 * same class.
	 *
	 * @return a new extent with size() == 1.
	 */
	T collapse();

	/**
	 * Return the simplest boundary that can be compared to another coming from an
	 * extent of the same type. This should be a "bounding box" that ignores
	 * internal structure and shape and behaves with optimal efficiency when merged
	 * with others.
	 * 
	 * @return the boundary.
	 */
	T getBoundingExtent();

	/**
	 * Return the dimensional coverage in the passed unit, which must be compatible
	 * or a {@link KIllegalArgumentException} should be thrown.
	 * 
	 * @param unit
	 * @return
	 */
	double getDimensionSize(Unit unit);

	/**
	 * Return the standardized (SI) dimension of the extent at the passed locator
	 * along with the unit it's in.
	 * 
	 * @return
	 */
	Pair<Double, Unit> getStandardizedDimension(Locator locator);


	/** {@inheritDoc} */
	@Override
	T merge(T other, LogicalConnector how);

	/**
	 * Return the n-th state of the ordered topology as a new extent with one state.
	 * 
	 * @param stateIndex must be between 0 and {@link #size()}, exclusive.
	 * @return a new extent with getValueCount() == 1, or this if it is 1-sized and
	 *         0 is passed.
	 */
	T getExtent(long stateIndex);

}
