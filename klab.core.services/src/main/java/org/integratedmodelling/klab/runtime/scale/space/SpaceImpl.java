package org.integratedmodelling.klab.runtime.scale.space;

import java.util.Collection;
import java.util.Iterator;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;
import org.locationtech.jts.geom.GeometryFactory;

public abstract class SpaceImpl extends ExtentImpl<Space> implements Space {

	private static final long serialVersionUID = 1L;
	
	static GeometryFactory gFactory = new GeometryFactory();

	public SpaceImpl() {
	    super(Dimension.Type.SPACE);
	}
	
//	Envelope envelope;

//	@Override
//	public Space at(Object... locators) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public int getRank() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public Space collapsed() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public double getDimensionSize() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	public Unit getDimensionUnit() {
//		return null;
//	}
//
//	@Override
//	public Space getExtent(long stateIndex) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public <T extends Locator> T as(Class<T> cls) {
//		// TODO Auto-generated method stub
//		return null;
//	}

//	@Override
//	public boolean contains(Space o) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public boolean overlaps(Space o) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public boolean intersects(Space o) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public Iterator<Space> iterator() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Shape getGeometricShape() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Envelope getEnvelope() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Projection getProjection() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public double getStandardizedVolume() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedArea() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedWidth() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double[] getStandardizedCentroid() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public double getStandardizedHeight() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedDepth() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedLength() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedDistance(Space extent) {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getStandardizedDimension(Locator locator) {
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public boolean isEmpty() {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public Extent<?> merge(Extent<?> other, LogicalConnector how) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public boolean matches(Collection<Constraint> constraints) {
//		// TODO Auto-generated method stub
//		return false;
//	}

	public static Space create(Dimension dimension) {

		if (dimension.isRegular()) {

			if (dimension.getParameters().containsKey(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION)) {

			} else if (dimension.getParameters().containsKey(GeometryImpl.PARAMETER_SPACE_SHAPE)) {

			}
		} else if (dimension.size() > 1) {

		} else {
			
		}
		return null;
	}

}
