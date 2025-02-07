package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.services.Reasoner
import org.integratedmodelling.klab.api.services.Resolver
import org.integratedmodelling.klab.api.services.ResourcesService
import org.integratedmodelling.klab.api.services.RuntimeService
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

/**
 * Base class for all generated scalar computations
 */
abstract class ExpressionBase extends Script implements ScalarComputation, MathOps, ObservationOps, KlabOps {

    Observation observation;

    // wrapped observations, geometry/scale fields and services
    /* FIELDS */

    // constructor takes all the observations used by the code
    ExpressionBase(ServiceContextScope scope, Observation observation /* ADDITIONAL OBSERVATIONS */) {
        this.scope = scope
        this.observation = observation
    }

    // run method uses the fill curve(s) to assign scalar values and includes any non-scalar code
    /* RUN METHOD */
}
