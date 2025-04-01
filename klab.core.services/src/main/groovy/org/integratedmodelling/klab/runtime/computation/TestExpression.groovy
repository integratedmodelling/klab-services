package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.geometry.Geometry
import org.integratedmodelling.klab.api.knowledge.Observable
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.utils.Utils
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

// translates
//  set to [elevation - slope/slope.max]
class TestExpression extends ExpressionBase {

    Observation __elevation;
    Observation __slope;
    Observation __self;
    @Lazy
    ObservationWrapper elevationObs = { new ObservationWrapper(__elevation) }()
    @Lazy
    ObservationWrapper slopeObs = { new ObservationWrapper(__slope) }()
    @Lazy
    ObservationWrapper selfObs = { new ObservationWrapper(__self) }()

    /**
     * Knows that elevation, slope are qualities and exist. This is for a naïve parallelization honoring
     * any @split and/or @fillcurve annotation and is meant for scalars only. Split strategy MUST be
     * coordinated across all observations.
     *
     * @param self
     * @param elevation
     * @param scope
     */
    TestExpression(ServiceContextScope scope, Observation self, Observation elevation, Observation slope) {
        super(scope, self)
        this.__self = self
        this.__elevation = elevation
        this.__slope = slope
    }

    @Override
    boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope) {

        /* TODO need to build the buffers here based on the geometry */
        def selfBuffers = scope.getDigitalTwin().getStorageManager().getStorage(__self).buffers(geometry)
        def elevationBuffers = scope.getDigitalTwin().getStorageManager().getStorage(__elevation).buffers(geometry)
        def slopeBuffers = scope.getDigitalTwin().getStorageManager().getStorage(__slope).buffers(geometry)

        def bufferSets = Utils.Collections.transpose(selfBuffers, elevationBuffers, slopeBuffers)

        return Utils.Java.distributeComputation( // template - this allows Spark templates to be different if the buffer is a spark thing
                bufferSets,
                { bufferArray ->
                    var scannerArray = bufferArray.stream().map({ b->b.scan()}).toArray();
                    while (scannerArray[0].hasNext()) { // template ends here
                        // TODO THESE use the proper non-boxed type
                        double elevation = scannerArray[1].get()
                        double slope = scannerArray[2].get()
                        // THIS
                        double self = ((elevation - elevationObs.max) / slope)
                        // TODO any other transformations
                        // TODO add() for any other targets
                        scannerArray[0].add(self) // template
                    }
                })
    }
}
