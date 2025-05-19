package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.geometry.Geometry
import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.services.Reasoner
import org.integratedmodelling.klab.api.services.Resolver
import org.integratedmodelling.klab.api.services.ResourcesService
import org.integratedmodelling.klab.api.services.RuntimeService

/**
 * Base class for all generated scalar computations. The generated classes extending this will be created based on the
 * code.templates.ScalarBufferFiller template.
 */
abstract class ExpressionBase extends GroovyObjectSupport implements MathOps, ObservationOps {

    @Lazy
    def reasoner = { scope.getService(Reasoner.class) }()
    @Lazy
    def resourcesService = { scope.getService(ResourcesService.class) }()
    @Lazy
    def resourcesServices = { scope.getServices(ResourcesService.class) }()
    @Lazy
    def resolver = { scope.getService(Resolver.class) }()
    @Lazy
    def runtime = { scope.getService(RuntimeService.class) }()
    @Lazy
    def runtimes = { scope.getServices(RuntimeService.class) }()

    ContextScope scope
    Observation __self
    Observation selfObs = {new ObservationWrapper(__self)}()

    // constructor takes all the observations used by the code
    ExpressionBase(ContextScope scope, Observation observation) {
        this.scope = scope
        this.__self = observation
    }

    abstract boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope);

}
