package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowCompiler {

    public Dataflow<Observation> compile(ResolutionGraph resolutionGraph, ContextScope scope) {

        return Dataflow.empty(Observation.class);
    }

}
