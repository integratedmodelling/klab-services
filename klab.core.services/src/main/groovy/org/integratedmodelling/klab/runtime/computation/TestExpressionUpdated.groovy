package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.services.runtime.Notification
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

import java.util.function.LongConsumer

// translates
//  set to [elevation - slope/slope.max]
class TestExpressionUpdated extends ExpressionBase {

    Observation __elevation;
    Observation __slope;
    Observation __self;

    /**
     * Knows that elevation, slope are qualities and exist. This is for a naïve parallelization honoring
     * any @split and/or @fillcurve annotation and is meant for scalars only. Split strategy MUST be
     * coordinated across all observations.
     *
     * @param self
     * @param elevation
     * @param scope
     */
    TestExpressionUpdated(ServiceContextScope scope, Observation self, Observation elevation, Observation slope) {
        super(scope, self)
        this.__self = self
        this.__elevation = elevation
        this.__slope = slope
    }

    @Override
    boolean run(Map<String, Storage.Scanner> scanners, Scheduler.Event event, ContextScope scope) {

        try {

            // make any wrappers (xxxObs), including those for extents (as functions taking the buffer offset if
            // needed
            def elevationObs = new ObservationWrapper(__elevation, event)

            // Add any "static" objects
            def elevationBuffer = (Storage.DoubleScanner) scanners.get("elevation")
            def slopeBuffer = (Storage.DoubleScanner) scanners.get("slope")
            def selfBuffer = (Storage.DoubleScanner) scanners.get("self")

            selfBuffer.forEachRemaining((LongConsumer) { n ->
                // extract any point values
                def elevation = elevationBuffer.get()
                def slope = slopeBuffer.get()
                selfBuffer.add((
                        /* START COMPILED CODE */
                        (elevation - elevationObs.max) / slope)
                        /* END COMPILED CODE */)
            })

        } catch (Throwable t) {
            __self.getNotifications().add(Notification.error(t.getMessage(), t));
            return false;
        }

        return true;
    }
}
