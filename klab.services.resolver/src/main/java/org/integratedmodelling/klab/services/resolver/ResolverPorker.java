package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

/**
 * Obviously a placeholder for the resolver 2.0
 */
public class ResolverPorker {

    /**
     * Entry point
     *
     * @param observation
     * @param scope
     * @return
     */
    public ResolutionGraph resolve(Observation observation, ContextScope scope) {
        var ret = new ResolutionGraph(observation);
        resolve(observation, scope, ret);
        return ret;
    }

    private Coverage resolve(Observation observation, ContextScope scope, ResolutionGraph graph) {
        return null;
    }

    private Coverage resolve(ObservationStrategy observationStrategy, ContextScope scope,
                             ResolutionGraph graph) {
        return null;
    }

    private Coverage resolve(Model model, ContextScope scope, ResolutionGraph graph) {
        return null;
    }

}
