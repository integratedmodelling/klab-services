package org.integratedmodelling.klab.runtime.scale.space;

import java.util.Collection;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.locationtech.jts.geom.Geometry;

public class ShapeImpl extends SpaceImpl implements Shape {

	private static final long serialVersionUID = 5154895981013940462L;

	private Geometry geometry;

	transient private Geometry standardizedGeometry;
	
	@Override
	public ShapeImpl at(Object... locators) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Metadata getMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape.Type getGeometryType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getArea(Unit unit) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Shape transform(Projection projection) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape intersection(Shape other) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape union(Shape other) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape getBoundingExtent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Shape> getHoles() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape buffer(double bdistance) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape difference(Shape shape) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Shape getCentroid() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double[] getCenter(boolean standardized) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean contains(double[] coordinates) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double getComplexity() {
		// TODO Auto-generated method stub
		return 0;
	}

	public Geometry standardizedGeometry() {
		return this.geometry;
	}

}
