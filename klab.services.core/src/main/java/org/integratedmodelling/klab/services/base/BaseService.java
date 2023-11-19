package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public abstract class BaseService implements KlabService {

    private static final long serialVersionUID = 1646569587945609013L;

    protected ServiceScope scope;

    protected List<BiConsumer<Scope, Message>> eventListeners = new ArrayList<>();

    protected BaseService(ServiceScope scope, BiConsumer<Scope, Message>...eventListeners) {
        this.scope = scope;
        if (eventListeners != null) {
            Arrays.stream(eventListeners).map(e -> this.eventListeners.add(e));
        }
    }

    @Override
    public ServiceScope scope() {
        return scope;
    }

    public abstract void initializeService();

    protected void notify(Scope scope, Object... objects) {
        if (!eventListeners.isEmpty()) {
            for(var listener : eventListeners) {
                listener.accept(scope,Message.create(scope, objects));
            }
        }
    }
}
