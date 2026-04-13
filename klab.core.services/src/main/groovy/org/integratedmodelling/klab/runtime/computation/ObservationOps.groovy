package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.observation.Observation

/**
 * Operations and wrappers for observations
 */
trait ObservationOps {

    static class ObservationWrapper {

        @Delegate
        Observation delegate;
        Scheduler.Event state;

        ObservationWrapper(Observation observation, Scheduler.Event state) {
            delegate = observation;
            this.state = state
        }

        // TODO all these pieces (check out 0.11)
        double getMax() {
            // TODO must use the combined histogram @ state, filling in at the first call
            return 1.0;
        }

        double getMin() {
            // TODO
            return 1.0;
        }

    }

}