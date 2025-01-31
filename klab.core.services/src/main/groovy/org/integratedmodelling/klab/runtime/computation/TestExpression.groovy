package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Data
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.runtime.storage.DoubleBuffer

class Expression20349 extends ExpressionBase {

    @Lazy
    DoubleBuffer self = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()
    @Lazy
    DoubleBuffer elevation = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()
    @Lazy
    DoubleBuffer slope = { scope.digitalTwin.stateStorage.getExistingStorage(observation).buffer(3203, Data.FillCurve.D2_XY, 0) }()

    Expression20349(Observation self, Observation elevation, Observation scope) {
        this.observationSelf = self;

    }

    def code() {
        // this is the expression set
    }

    // TODO this goes upstairs

    @Override
    Object run() {
        map(code)
    }
}
