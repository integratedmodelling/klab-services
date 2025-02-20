package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Data
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.runtime.storage.DoubleStorage
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

// model shit observing elevation, slope
//  set to [elevation - slope/slope.max]
class Expression20349 extends ExpressionBase {

    Observation _elevationObs;
    Observation _slopeObs;

    /**
     * Knows that elevation, slope are qualities and exist. This is for a naïve parallelization honoring
     * any @split and/or @fillcurve annotation and is meant for scalars only.
     *
     * @param self
     * @param elevation
     * @param scope
     */
    Expression20349(ServiceContextScope scope, Observation self, Observation elevation, Observation slope) {
        super(scope, self)
    }

    @Override
    Object run() {

        /* BUFFERS FOR ALL QUALITIES */
        def storage = scope.digitalTwin.stateStorage;
        def elevationBuf = storage.getOrCreateStorage(_elevationObs, DoubleStorage.class).buffer(/* FILL CURVE */ Data.SpaceFillingCurve.D2_XY)
        def slopeBuf = storage.getOrCreateStorage(_slopeObs, DoubleStorage.class).buffer(/* FILL CURVE */ Data.SpaceFillingCurve.D2_XY)
        def selfBuf = storage.getOrCreateStorage(_selfObs, DoubleStorage.class).buffer(/* FILL CURVE */ Data.SpaceFillingCurve.D2_XY)

        /* MAIN LOOP */
        while (selfBuf.hasNext()) {
            /* LOCAL VARS FROM OTHER QUALITIES */
            def elevation = elevationBuf.next()
            def slope = slopeBuf.next()
            selfBuf.add(/* EXPR CODE */(elevation - _elevationObs.max)/slope)
        }

        // OR something like this with all buffers, which may be split according to model configuration (annotations)
        GParsPool.withPool {
            buffers.eachParallel { email ->
                def wait = (long) new Random().nextDouble() * 1000
                println "in closure"
                this.sleep wait
                sendEmail(email)
            }
        }
    }

}
