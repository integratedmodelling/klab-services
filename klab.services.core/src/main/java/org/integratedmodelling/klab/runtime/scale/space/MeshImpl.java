package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Mesh;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.locationtech.jts.geom.Geometry;

import java.util.Collection;


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
	public MeshImpl at(Locator locator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Shape> shapes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encode(String language) {
		ServiceCall ret = super.encodeCall();
		// TODO have ShapeImpl return a service call with a protected method, then use that and add arguments
		return ret.encode(language);
	}}
