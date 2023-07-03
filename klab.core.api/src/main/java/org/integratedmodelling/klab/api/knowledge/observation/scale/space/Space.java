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
package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;

/**
 * The Interface ISpace.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Space extends Extent<Space>, Spatial {

	/** Constant <code>MIN_SCALE_RANK=0</code> */
	int MIN_SCALE_RANK = 0;
	/** Constant <code>MAX_SCALE_RANK=21</code> */
	int MAX_SCALE_RANK = 21;


	/**
	 * Volume in standard SI units (square meters), NaN if < 3D.
	 * 
	 * @return
	 */
	double getStandardizedVolume();

	/**
	 * Area in standard SI units (square meters), NaN if < 2D.
	 * 
	 * @return
	 */
	double getStandardizedArea();

	/**
	 * Width in standard SI units (meters), NaN if < 2D.
	 * 
	 * @return
	 */
	double getStandardizedWidth();

	/**
	 * Centroid in whatever standard coordinates the implementation uses.
	 * 
	 * @return
	 */
	double[] getStandardizedCentroid();

	/**
	 * Height in standard SI units (meters), NaN if 1D.
	 * 
	 * @return
	 */
	double getStandardizedHeight();

	/**
	 * Depth in standard SI units (meters), NaN if 2D.
	 * 
	 * @return
	 */
	double getStandardizedDepth();

	/**
	 * Length in standard SI units (meters), NaN if 0D. Same as
	 * {@link #getStandardizedWidth()} in 2D shapes.
	 * 
	 * @return
	 */
	double getStandardizedLength();

	/**
	 * Edge-to-edge distance in standard SI units (meters).
	 * 
	 * @param shape
	 * @return the distance
	 */
	double getStandardizedDistance(Space extent);

}
