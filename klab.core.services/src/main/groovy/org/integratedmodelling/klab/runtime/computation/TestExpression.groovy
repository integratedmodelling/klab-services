package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Data
import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.runtime.storage.DoubleBuffer
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

// model shit observing elevation, slope
//  set to [elevation - slope/slope.max]
class Expression20349 extends ExpressionBase {

    Observation _elevationObs;
    Observation _slopeObs;

    /**
     * Knows that elevation, slope are qualities and exist
     *
     * Calls to known states are translated to _x.data.get(_offset); others to _xObs wrapper
     *
     * @param self
     * @param elevation
     * @param scope
     */
    Expression20349(ServiceContextScope scope, Observation self, Observation elevation, Observation slope) {
        super(scope, self)
    }

    def code(long offset) {
        // this is the expression set

        return elevation.data().get(_offset) - slope.data().get(_offset)/slopeObs.max() // <- NON facile diocan
    }

    @Override
    Object run() {
        // groups of scalars using the set fillcurve
        return false
    }

}
