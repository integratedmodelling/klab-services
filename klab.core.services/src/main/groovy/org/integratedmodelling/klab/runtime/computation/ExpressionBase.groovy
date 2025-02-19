package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

/**
 * Base class for all generated scalar computations
 */
abstract class ExpressionBase extends Script implements MathOps, ObservationOps, KlabOps {

    Observation _selfObs;

    // wrapped observations, geometry/scale fields and services
    /* FIELDS */

    // constructor takes all the observations used by the code
    ExpressionBase(ServiceContextScope scope, Observation observation /* ADDITIONAL OBSERVATIONS */) {
        this.scope = scope
        this._selfObs = observation
    }

    // run method uses the fill curve(s) to assign scalar values and includes any non-scalar code
    /* RUN METHOD */
}
