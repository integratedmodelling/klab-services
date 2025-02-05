package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.knowledge.observation.Observation
import org.integratedmodelling.klab.api.services.Reasoner
import org.integratedmodelling.klab.api.services.Resolver
import org.integratedmodelling.klab.api.services.ResourcesService
import org.integratedmodelling.klab.api.services.RuntimeService
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

/**
 * Base class for all generated scalar computations
 */
abstract class ExpressionBase extends Script implements ScalarComputation, MathOps, ObservationOps {

    ServiceContextScope scope;
    Observation observation;

    @Lazy Reasoner reasoner = { scope.getService(Reasoner.class) }()
    @Lazy ResourcesService resources = { scope.getService(ResourcesService.class) }()
    @Lazy Resolver resolver = { scope.getService(Resolver.class) }()
    @Lazy RuntimeService runtime = { scope.getService(RuntimeService.class) }()

}
