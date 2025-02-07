//package org.integratedmodelling.klab.runtime.computation
//
//import org.integratedmodelling.klab.api.data.Data
//import org.integratedmodelling.klab.api.data.Storage
//import org.integratedmodelling.klab.api.knowledge.observation.Observation
//import org.integratedmodelling.klab.runtime.storage.DoubleBuffer
//
//// model shit observing elevation, slope
////  set to [elevation - slope/slope.max]
//class Expression20349 extends ExpressionBase {
//
//    @Lazy
//    DoubleBuffer self = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()
//    @Lazy
//    DoubleBuffer elevation = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()
//    @Lazy
//    DoubleBuffer slope = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()
//
//    Data.FillCurve fillCurve;
//
//    /**
//     * Knows that elevation, slope are qualities and exist
//     *
//     * Calls to known states are translated to _x.data.get(_offset); others to _xObs wrapper
//     *
//     * @param self
//     * @param elevation
//     * @param scope
//     */
////    Expression20349(Observation self, Observation elevation, Observation slope, Observation scope, int offset) {
////        this.observationSelf = self;
//////        this._slopeObs = new State(slope)
////
////
////    }
//
//    def code(long offset) {
//        // this is the expression set
//
//        return elevation.data().get(_offset) - slope.data().get(_offset)/slopeObs.max() // <- NON facile diocan
//    }
//
//    // TODO this goes upstairs
//
//    @Override
//    Object run() {
//        map(code)
//    }
//
//    @Override
//    boolean run(Storage<?> storage) {
//        return false
//    }
//}
