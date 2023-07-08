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

import java.util.Collection;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Opaque, minimal interface for a 2D geometry pertaining to a physical
 * continuant or occurrent.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Shape extends Referenced, Space {

	/**
	 * The Enum Type.
	 */
	public enum Type {
		EMPTY, POINT, LINESTRING, POLYGON, MULTIPOINT, MULTILINESTRING, MULTIPOLYGON
	}

	/**
	 * Geometry type
	 *
	 * @return the type
	 */
	Type getGeometryType();

	/**
	 * Return a suitable measure of area. Unit must be areal.
	 *
	 * @param unit a {@link org.integratedmodelling.klab.api.data.mediation.Unit}
	 *             object.
	 * @return area in passed unit
	 */
	double getArea(Unit unit);

	/**
	 * Shapes may be empty or inconsistent.
	 *
	 * @return true if not really a shape
	 */
	boolean isEmpty();

	/**
	 * Return the shape transformed to the passed projection.
	 *
	 * @param projection a
	 *                   {@link org.integratedmodelling.klab.api.Projection.scale.space.IProjection}
	 *                   object.
	 * @return the transformed shape
	 * @throws org.integratedmodelling.klab.KValidationException.KlabValidationException
	 */
	Shape transform(Projection projection);

	/**
	 * The shape's bounding box
	 *
	 * @return the referenced envelope
	 */
	Envelope getEnvelope();

	/**
	 * Create a new shape by intersecting this with the passed other.
	 *
	 * @param other a
	 *              {@link org.integratedmodelling.klab.api.Shape.scale.space.IShape}
	 *              object.
	 * @return the intersection
	 */
	Shape intersection(Shape other);

	/**
	 * Create a new shape by uniting this with the passed other.
	 *
	 * @param other a
	 *              {@link org.integratedmodelling.klab.api.Shape.scale.space.IShape}
	 *              object.
	 * @return the union
	 */
	Shape union(Shape other);

	/**
	 * Get the boundary for a polygon, the shape itself for points and lines.
	 * 
	 * @return the boundary
	 */
	Shape getBoundingExtent();

	/**
	 * Get the interior holes for a polygon, the empty collection for points and
	 * lines.
	 * 
	 * @return the holes
	 */
	Collection<Shape> getHoles();

	/**
	 * Buffer by passed distance in native projection units.
	 * 
	 * @param bdistance
	 * @return a new shape
	 */
	Shape buffer(double bdistance);

	/**
	 * Subtract the passed shape from this shape.
	 * 
	 * @param shape
	 * @return
	 */
	Shape difference(Shape shape);

	/**
	 * Get the centroid as a new IShape.
	 * 
	 * @return
	 */
	Shape getCentroid();

	/**
	 * Get the center x,y coordinates in projection units or in standardized units.
	 * 
	 * @return
	 */
	double[] getCenter(boolean standardized);

	/**
	 * True if the passed coordinate is on or in the shape.
	 * 
	 * @param coordinates
	 * @return
	 */
	boolean contains(double[] coordinates);

	/**
	 * Only for statistics. Some metric of complexity, which should take into
	 * account the number of coordinates, inner rings etc.
	 * 
	 * @return
	 */
	double getComplexity();

	public static Shape create(String textSpecification, Projection projection) {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KIllegalStateException("k.LAB environment not configured to promote a geometry to a scale");
		}
		return configuration.createShapeFromTextSpecification(textSpecification, projection);
	}
}
