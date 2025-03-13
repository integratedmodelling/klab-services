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
        double getMax() {
            // TODO
            return 1.0;
        }

        double getMin() {
            // TODO
            return 1.0;
        }

    }

}