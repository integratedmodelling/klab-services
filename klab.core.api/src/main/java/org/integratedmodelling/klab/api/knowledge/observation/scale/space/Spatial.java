package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

/**
 * Anything that is spatial has a shape.
 * 
 * @author ferdinando.villa
 *
 */
public interface Spatial {
	
	/**
	 * Get the shape.
	 * 
	 * @return
	 */
	Shape getGeometricShape();
	
	/**
	 * Get the envelope, providing boundaries.
	 *
	 * @return the referenced envelope
	 */
	Envelope getEnvelope();

	/**
	 * Projection. Just repeats same in envelope and shape. It's not legal to have
	 * different projections in different elements of a spatial extent.
	 *
	 * @return coordinate reference system
	 */
	Projection getProjection();
}
