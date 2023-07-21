package org.integratedmodelling.klab.api;

import java.util.Collection;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;

/**
 * Holds global configurations and functions that allow generic interfaces to
 * expose constructor methods that produce implementation classes that depend on
 * complex dependencies. Implements a poor-man injection pattern that needs to
 * be configured in a static block, as done in <code>klab.services.core</code>.
 * This permits complex classes like Scale or Projection to have generic
 * builders declared in the API package.
 * 
 * @author Ferd
 *
 */
public enum Klab {

	INSTANCE;

	public interface Configuration {

		Observable promoteConceptToObservable(Concept concept);

		Observable.Builder getObservableBuilder(Concept observable);

		Observable.Builder getObservableBuilder(Observable observable);

		Scale promoteGeometryToScale(Geometry geometry);

		Projection getDefaultSpatialProjection();

		Projection getLatLonSpatialProjection();

		Scale createScaleFromExtents(Collection<Extent<?>> extents);

		Shape createShapeFromTextSpecification(String shapeText, Projection projection);

	}

	private Configuration configuration;

	/**
	 * Call this in the static block of the core package configuration to ensure
	 * that the constructors know how to do their job.
	 * 
	 * @param configuration
	 */
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

}
