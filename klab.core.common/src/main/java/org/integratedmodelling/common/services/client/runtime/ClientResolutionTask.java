package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionTask;

import java.util.concurrent.CompletableFuture;

public class ClientResolutionTask extends CompletableFuture<Observation> implements ResolutionTask {
    @Override
    public long getId() {
        return 0;
    }
}
