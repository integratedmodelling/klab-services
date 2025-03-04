package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.knowledge.Observable
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.utils.Utils
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

// translates
//  set to [elevation - slope/slope.max]
class TestExpression extends ExpressionBase {

    Observable elevationObservable;
    @Lazy
    ObservationWrapper elevationObs = { new ObservationWrapper(scope.getObservation(elevationObservable)) }()

    List<Storage.DoubleBuffer> elevationBuffers;
    List<Storage.DoubleBuffer> slopeBuffers;
    List<Storage.DoubleBuffer> selfBuffers;

    /**
     * Knows that elevation, slope are qualities and exist. This is for a naïve parallelization honoring
     * any @split and/or @fillcurve annotation and is meant for scalars only. Split strategy MUST be
     * coordinated across all observations.
     *
     * @param self
     * @param elevation
     * @param scope
     */
    TestExpression(ServiceContextScope scope, Observation self, Observable elevationObservable, Observable slopeObservable,
                   List<Storage.DoubleBuffer> elevationBuffers, List<Storage.DoubleBuffer> slopeBuffers,
                   List<Storage.DoubleBuffer> selfBuffers) {
        super(scope, self)
        this.selfBuffers = selfBuffers
        this.elevationBuffers = elevationBuffers
        this.slopeBuffers = slopeBuffers
    }

    @Override
    Object run() {

        def bufferSets = [selfBuffers, elevationBuffers, slopeBuffers]
        return Utils.Java.distributeComputation(
                bufferSets,
                { bufferArray ->
                    while (bufferArray[0].hasNext()) {
                        def elevation = bufferArray[1].get()
                        def slope = bufferArray[2].get()
                        bufferArray[0].add((double) ((elevation - elevationObs.max) / slope))
                    }
                })

    }
}
