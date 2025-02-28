package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.services.Reasoner
import org.integratedmodelling.klab.api.services.Resolver
import org.integratedmodelling.klab.api.services.ResourcesService
import org.integratedmodelling.klab.api.services.RuntimeService

/**
 * Base class for all generated scalar computations
 */
abstract class ExpressionBase extends Script implements MathOps, ObservationOps, KlabOps {

    @Lazy Reasoner reasoner = { scope.getService(Reasoner.class) }()
    @Lazy ResourcesService resourcesService = { scope.getService(ResourcesService.class) }()
    @Lazy Collection<ResourcesService> resourcesServices = { scope.getServices(ResourcesService.class) }()
    @Lazy Resolver resolver = { scope.getService(Resolver.class) }()
    @Lazy RuntimeService runtime = { scope.getService(RuntimeService.class) }()
    @Lazy Collection<RuntimeService> runtimes = { scope.getServices(RuntimeService.class) }()

    Observation self;

    // wrapped observations, geometry/scale fields and services
    /* FIELDS */

    // constructor takes all the observations used by the code
    ExpressionBase(ContextScope scope, Observation observation /* ADDITIONAL OBSERVATIONS */) {
        this.scope = scope
        this.self = observation
    }

    // run method uses the fill curve(s) to assign scalar values and includes any non-scalar code
    /* RUN METHOD */
}
