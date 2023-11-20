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
    protected String localName = "Embedded";

    protected List<BiConsumer<Scope, Message>> eventListeners = new ArrayList<>();

    protected BaseService(ServiceScope scope, String localName, BiConsumer<Scope, Message>...eventListeners) {
        this.scope = scope;
        this.localName = localName;
        if (eventListeners != null) {
            Arrays.stream(eventListeners).map(e -> this.eventListeners.add(e));
        }
    }

    public String localName() {
        return localName;
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
