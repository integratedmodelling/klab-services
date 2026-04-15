import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.runtime.computation.ExpressionBase

/**
 * Scalar buffer-based processing with "local" parallelism. Default for the local engines and OK for distributed
 * computation without involving clustered engines with Spark or other infrastructure.
 */
class ScalarComputation_826mikwj8 extends ExpressionBase {


    Observation __elevation

    Observation __slope


    ScalarComputation_826mikwj8(ContextScope scope, Observation self, Observation elevation, Observation slope) {
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

            var n = selfBuffer.size()
            for (long i = 0; i < n; i++) {
                def elevation = elevationBuffer.get()
                def slope = slopeBuffer.get()
                selfBuffer.add(
                        /* START COMPILED SCALAR CODE */
                        (elevationObs.max - elevation) / slope
                /* END COMPILED SCALAR CODE */
                )
            }
        } catch (Throwable t) {
            __self.getNotifications().add(Notification.error(t.getMessage(), t))
            return false;
        }

        return true;
    }
}

