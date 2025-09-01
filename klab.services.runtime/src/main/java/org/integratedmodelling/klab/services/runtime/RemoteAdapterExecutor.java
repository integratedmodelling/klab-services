package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;

public class RemoteAdapterExecutor implements CompiledDataflow.ContextualExecutor {

    @Override
    public boolean execute(Scheduler.Event context) {
        return false;
    }
}
