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

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

/**
 * Opaque interface for a coordinate reference system.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Projection {

	public static final String DEFAULT_METERS_PROJECTION = "EPSG:3005";
	public static final String DEFAULT_PROJECTION_CODE = "EPSG:4326";
	public static final String LATLON_PROJECTION_CODE = "EPSG:4326";

	/**
	 * Unique identifier of projection, enough to rebuild it at another endpoint.
	 *
	 * @return a {@link java.lang.String} object.
	 */
	String getCode();

	/**
	 * Check if the values projected according to this projection express meters.
	 * 
	 * @return true if a meter projection
	 */
	boolean isMeters();

	/**
	 * If true, the projection uses the first coordinate for the S->N direction and
	 * the second for the W->E. This applies to the default lat-lon projection unless
	 * it was forced into sanity.
	 * 
	 * @return true if projection is silly
	 */
	boolean flipsCoordinates();

	/**
	 * Units 
	 * @return
	 */
	String getUnits();
	
	public static Projection of(String string) {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KlabIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
		}
		return configuration.getSpatialProjection(string);
	}
	
	
	public static Projection getLatLon() {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KlabIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
		}
		return configuration.getLatLonSpatialProjection();
	}

	public static Projection getDefault() {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KlabIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
		}
		return configuration.getDefaultSpatialProjection();
	}


}
