package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time

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
            def time = state.time.getTimeType() == Time.Type.INITIALIZATION ? 0 : state.time.getStart().milliseconds
            delegate.getHistograms().get(time)?.max
        }

        double getMin() {
            def time = state.time.getTimeType() == Time.Type.INITIALIZATION ? 0 : state.time.getStart().milliseconds
            delegate.getHistograms().get(time)?.min
        }

    }

}
