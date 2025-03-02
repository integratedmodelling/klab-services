package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Data
import org.integratedmodelling.klab.api.knowledge.Observable
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.utils.Utils
import org.integratedmodelling.klab.runtime.storage.DoubleStorage
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

import java.nio.DoubleBuffer

// model shit observing elevation, slope
//  set to [elevation - slope/slope.max]
class Expression20349 extends ExpressionBase {

    Observable elevationObservable;
    @Lazy
    Observation elevationObs = { scope.getObservation(elevationObservable) }()

    List<DoubleBuffer> elevationBuffers;
    List<DoubleBuffer> slopeBuffers;
    List<DoubleBuffer> selfBuffers;

    /**
     * Knows that elevation, slope are qualities and exist. This is for a naïve parallelization honoring
     * any @split and/or @fillcurve annotation and is meant for scalars only.
     *
     * @param self
     * @param elevation
     * @param scope
     */
    Expression20349(ServiceContextScope scope, Observation self, Observable elevationObservable, Observable slopeObservable,
                    List<DoubleBuffer> selfBuffers, List<DoubleBuffer> elevationBuffers, List<DoubleBuffer> slopeBuffers) {
        super(scope, self)
        this.elevationBuffers = elevationBuffers
        this.selfBuffers = selfBuffers
        this.slopeBuffers = slopeBuffers
    }

    @Override
    Object run() {

        def bufferSets = [selfBuffers, elevationBuffers, slopeBuffers]
        return Utils.Java.distributeComputation(
                bufferSets,
                bufferArray -> {
                    while (selfBuf.hasNext()) {
                        /* LOCAL VARS FROM OTHER QUALITIES */
                        def elevation = bufferArray[1].get()
                        def slope = bufferArray[2].get()
                        bufferArray[0].add((double) ((elevation - _elevationObs.max) / slope))
                    }
                })

    }
}
