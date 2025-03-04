package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.knowledge.observation.Observation

/**
 * Operations and wrappers for observations
 */
trait ObservationOps {

    static class ObservationWrapper {

        @Delegate
        Observation delegate;

        ObservationWrapper(Observation observation) {
            delegate = observation;
        }

    }

}