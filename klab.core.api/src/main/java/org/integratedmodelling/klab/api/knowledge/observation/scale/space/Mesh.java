package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

/**
 * Represents a shape containing an irregular tessellation of the space, which
 * can be contiguous (e.g. triangulation or irregular contiguous shapes) or
 * discontinuous. Includes the single tessellation (which resolves to a Shape).
 * 
 * @author Ferd
 *
 */
public interface Mesh extends Shape {

	Iterable<Shape> shapes();

}
