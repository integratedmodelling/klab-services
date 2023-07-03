package org.integratedmodelling.klab.runtime.scale.space;

/**
 * Represents a tessellation of the space which can be contiguous (e.g.
 * triangulation or irregular contiguous shapes) or discontinuous. Includes the
 * single tessellation (which can be turned into a Shape using
 * {@link #as(Class)} without cost). The special case of a square or rectangular
 * grid is handled independently in {@link Grid}.
 * 
 * @author Ferd
 *
 */
public class Mesh extends SpaceImpl {

	private static final long serialVersionUID = -7906419063910020731L;

	@Override
	public Mesh at(Object... locators) {
		// TODO Auto-generated method stub
		return null;
	}

}
