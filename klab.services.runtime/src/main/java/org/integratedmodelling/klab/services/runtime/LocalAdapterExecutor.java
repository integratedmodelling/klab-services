package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;

public class LocalAdapterExecutor implements CompiledDataflow.ContextualExecutor {

    @Override
    public boolean execute(Scheduler.Event context) {
        return false;
    }
}
