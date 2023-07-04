package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Mesh;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;

/**
 * Represents a tessellation of the space which can be contiguous (e.g.
 * triangulation or irregular contiguous shapes) or discontinuous. Includes the
 * single tessellation (which can be turned into a Shape using
 * {@link #as(Class)} without cost). The special case of a square or rectangular
 * grid is handled independently in {@link TileImpl}.
 * 
 * @author Ferd
 *
 */
public class MeshImpl extends SpaceImpl implements Mesh {

	private static final long serialVersionUID = -7906419063910020731L;

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
