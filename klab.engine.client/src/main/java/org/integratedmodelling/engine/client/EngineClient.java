package org.integratedmodelling.engine.client;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.engine.AbstractAuthenticatedEngine;

public class EngineClient extends AbstractAuthenticatedEngine {

    @Override
    protected UserScope authenticate() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isOnline() {
        return false;
    }
}
