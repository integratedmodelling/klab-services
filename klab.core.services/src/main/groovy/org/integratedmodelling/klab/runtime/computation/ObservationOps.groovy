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
            throw new UnsupportedOperationException(
                    "Observation aggregate property 'max' is not implemented for scalar expressions")
        }

        double getMin() {
            throw new UnsupportedOperationException(
                    "Observation aggregate property 'min' is not implemented for scalar expressions")
        }

    }

}
