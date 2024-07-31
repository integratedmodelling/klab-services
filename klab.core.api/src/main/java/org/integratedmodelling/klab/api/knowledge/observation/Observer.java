package org.integratedmodelling.klab.api.knowledge.observation;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * The Observer is a Subject with agent semantics that has an observation scale in addition to its own.
 */
public interface Observer extends DirectObservation {

    /**
     * The observation scale, i.e. the "view" of the geometry from the observer's perspective.
     *
     * @return
     */
    Scale getObservationScale();
}
