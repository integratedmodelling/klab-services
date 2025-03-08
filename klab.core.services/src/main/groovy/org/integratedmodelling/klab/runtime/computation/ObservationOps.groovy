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

        // TODO all these pieces (check out 0.11)
        double max() {
            // TODO
            return 1.0;
        }

        double min() {
            // TODO
            return 1.0;
        }

    }

}