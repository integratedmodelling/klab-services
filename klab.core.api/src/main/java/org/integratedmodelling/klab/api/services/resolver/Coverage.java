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
package org.integratedmodelling.klab.api.services.resolver;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent.Constraint;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

/**
 * A {@code Coverage} is a scale that only uses collapsed extents
 * ({@link #size()} == 1}) and keeps information about how much of its extents
 * is being covered by any other extents merged into it, so that successive
 * merges can be compared for contribution. Coverage is initialized at 0 or 1
 * and can be modified only by merging in conformant scales, which either
 * subtract (if merged with {@link LogicalConnector#INTERSECTION} mode) or add (
 * {@link LogicalConnector#UNION}) to the covered proportion of extent.
 * <p>
 * In addition to the percent covered, a coverage may also include scale
 * constraints that restrict the type of scales an asset can be used in. This
 * includes specific extents, resolutions, or ranges thereof for each of the
 * extents, or the statement that a specific extent must be present without
 * further specification.
 * <p>
 * A {@code Coverage} redefines the
 * {@link Scale#merge(TopologicallyComparable, LogicalConnector)} method to only
 * perform a union when the resulting coverage adds enough coverage. The
 * {@link #getGain()} can be called on the result to check if the merge produced
 * any significant increment or decrement in coverage.
 * <p>
 * Due to the collapsing of extents and the reinterpretation of the meaning of
 * merging, it is essential that a coverage is not used as a regular scale, with
 * which it is compatible otherwise. Also note that
 * {@link LogicalConnector#EXCLUSION} merging is unimplemented at the moment.
 * <p>
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Coverage extends Scale {
    
	/**
	 * Return the proportion of total coverage as a double 0-1. It is the product of
	 * the coverages for all the extents.
	 *
	 * @return the proportional coverage
	 */
	double getCoverage();

	/**
	 * Return the proportion of total coverage for one extent as a double 0-1.
	 *
	 * @param dimension a Dimension.Type object.
	 * @return the proportional coverage covered in the passed extent.
	 */
	double getCoverage(Dimension.Type dimension);

	/**
	 * {@inheritDoc}
	 *
	 * Reimplements {@link Scale#merge(TopologicallyComparable, LogicalConnector)}
	 * to return a coverage and implement {@code ICoverage}-specific behavior. Note
	 * that this breaks the {@link Scale} contract by returning a coverage where the
	 * underlying scale is <strong>unmodified</strong>, but only its coverage
	 * information has potentially changed.
	 * <p>
	 * If the coverage is a union, it will return the unaltered receiver
	 * {@code this}) unless the <strong>additional</strong> coverage resulting from
	 * the union is higher than the proportion returned by {@link #isRelevant()}.
	 * The proportion of coverage that has changed should be checked after this is
	 * called using {@link #getGain()} to see if anything has changed.
	 * <p>
	 * Must not modify the original scales.
	 */
	@Override
	Coverage merge(Scale coverage, LogicalConnector how);

	/**
	 * Like {@link #merge(TopologicallyComparable, LogicalConnector)} but only
	 * merges extents, without building merged representations such as grids or
	 * other tessellations.
	 * 
	 * @param coverage
	 * @param how
	 * @return
	 */
	Coverage mergeExtents(Coverage coverage, LogicalConnector how);

	/**
	 * Check that the passed scale matches any constraints built into the coverage.
	 * 
	 * @param geometry
	 * @return
	 */
	boolean checkConstraints(Scale geometry);

	/**
	 * Return any constraints specified for this coverage, or an empty collection.
	 * 
	 * @return
	 */
	Collection<Constraint> getConstraints();

	/**
	 * True if the coverage is less than the global setting defining a usable
	 * coverage (default 1%).
	 *
	 * @return true if coverage is below accepted defaults.
	 */
	boolean isEmpty();

	/**
	 * True if the coverage is at least as much as the minimum required coverage of
	 * a context (95% by default). Note that setting this to 1.0 may trigger lots of
	 * resolutions to resolve minute portions of the context.
	 *
	 * @return true if coverage is enough to declare an observation consistent.
	 */
	boolean isComplete();

	/**
	 * true if the coverage is relevant enough for a model to be accepted by the
	 * resolver (default smallest extent intersection covers 25% of scale).
	 *
	 * @return true if coverage is enough to keep
	 */
	boolean isRelevant();

	/**
	 * Proportion of coverage gained or lost during the merge operation that
	 * generated this coverage, if any.
	 *
	 * @return The coverage gained or lost (negative if lost). Always [-1, 1]. If
	 *         zero, the coverage was not created by a merge.
	 */
	double getGain();

	/**
	 * Create a coverage that spans a scale and has an initial coverage percentage.
	 * 
	 * @param geometry
	 * @param coverage
	 * @return
	 */
	static Coverage create(Scale geometry, double coverage) {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KIllegalStateException("k.LAB environment not configured to promote a scale to a coverage");
		}
		return configuration.promoteScaleToCoverage(geometry, coverage);

	}

    public static Coverage empty() {
        return create(Scale.create(Geometry.EMPTY), 0);
    }
    

}
