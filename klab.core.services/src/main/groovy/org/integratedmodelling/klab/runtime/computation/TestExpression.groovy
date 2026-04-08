import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.runtime.computation.ExpressionBase

import java.util.function.LongConsumer

/** This one is generated (and cleaned up) */
class ScalarComputation_8xnlsc1yk extends ExpressionBase {

    Observation __elevation
    Observation __slope

    ScalarComputation_8xnlsc1yk(ContextScope scope, Observation self, Observation elevation, Observation slope) {
        super(scope, self)
        this.__elevation = elevation
        this.__slope = slope
    }

    @Override
    boolean run(Map<String, Storage.Scanner> scanners, Scheduler.Event event, ContextScope scope) {

        try {
            def elevationObs = new ObservationWrapper(__elevation, event)
            def selfBuffer = (Storage.DoubleScanner) scanners.get("self")
            def elevationBuffer = (Storage.DoubleScanner) scanners.get("elevation")
            def slopeBuffer = (Storage.DoubleScanner) scanners.get("slope")

            selfBuffer.forEachRemaining((LongConsumer) { n ->
                def elevation = elevationBuffer.get()
                def slope = slopeBuffer.get()
                selfBuffer.add(
                        /* START COMPILED SCALAR CODE */
                        (elevationObs.max - elevation) / slope
                        /* END COMPILED SCALAR CODE */)
            })
        } catch (Throwable t) {
            __self.getNotifications().add(Notification.error(t.getMessage(), t))
            return false;
        }

        return true;
    }
}

