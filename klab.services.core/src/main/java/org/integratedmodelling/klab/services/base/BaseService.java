package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;

public abstract class BaseService implements KlabService {

    private static final long serialVersionUID = 1646569587945609013L;

    protected ServiceScope scope;

    protected BaseService(ServiceScope scope) {
        this.scope = scope;
    }

    @Override
    public ServiceScope scope() {
        return scope;
    }

    public abstract void initializeService();

}
