package org.integratedmodelling.klab.api.knowledge.observation.impl;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Configuration;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class ProcessImpl extends DirectObservationImpl implements Configuration {
    public ProcessImpl() {
    }

    public ProcessImpl(Observable observable, String id, ContextScope scope) {
        super(observable, id, scope);
    }
}
