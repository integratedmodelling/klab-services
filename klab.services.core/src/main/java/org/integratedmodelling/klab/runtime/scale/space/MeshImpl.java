package org.integratedmodelling.klab.runtime.scale.space;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Mesh;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.locationtech.jts.geom.Geometry;


public class MeshImpl extends ShapeImpl implements Mesh {

	private static final long serialVersionUID = -7906419063910020731L;
	private Collection<Shape> features;

	public MeshImpl(Geometry geometry, Projection projection, Collection<Shape> features) {
		super(geometry, projection);
		this.features = features;
	}
	
	@Override
	public long size() {
		return features.size();
	}

	@Override
	public MeshImpl at(Object... locators) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Shape> shapes() {
		// TODO Auto-generated method stub
		return null;
	}

}
