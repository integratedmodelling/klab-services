package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.geometry.Geometry
import org.integratedmodelling.klab.api.knowledge.Observable
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.utils.Utils
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

// translates
//  set to [elevation - slope/slope.max]
class TestExpression extends ExpressionBase {

    Observation __elevation;
    Observation __slope;
    Observation __self;
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
    TestExpression(ServiceContextScope scope, Observation self, Observation elevationObservable, Observation slopeObservable) {
        super(scope, self)
        this.selfBuffers = selfBuffers // THESE
        this.elevationBuffers = elevationBuffers
        this.slopeBuffers = slopeBuffers
    }

    @Override
    boolean run(Geometry geometry) {

        /* TODO need to build the buffers here based on the geometry */
        def selfBuffers = scope.getDigitalTwin().getStorageManager().getStorage(_self).buffers(geometry)
        def elevationBuffers = scope.getDigitalTwin().getStorageManager().getStorage(__elevation).buffers(geometry)
        def slopeBuffers = scope.getDigitalTwin().getStorageManager().getStorage(__slope).buffers(geometry)
        def bufferSets = Utils.Collections.transpose(/* these */ selfBuffers, elevationBuffers, slopeBuffers)

        return Utils.Java.distributeComputation( // template - this allows Spark templates to be different if the buffer is a spark thing
                bufferSets,
                { bufferArray ->
                    while (bufferArray[0].hasNext()) { // template ends here
                        // TODO THESE use the proper non-boxed type
                        double elevation = bufferArray[1].get()
                        double slope = bufferArray[2].get()
                        // THIS
                        double value = ((elevation - elevationObs.max) / slope)
                        // TODO any other transformations
                        // TODO add() for any other targets
                        bufferArray[0].add(value) // template
                    }
                })
    }
}
