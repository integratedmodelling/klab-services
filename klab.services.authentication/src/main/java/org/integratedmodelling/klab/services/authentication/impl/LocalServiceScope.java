package org.integratedmodelling.klab.services.authentication.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.ServiceIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.*;
import java.util.function.BiConsumer;

public abstract class LocalServiceScope extends Monitor implements ServiceScope {

    KlabService service;
    Status status;

    class LocalService implements ServiceIdentity {

        Date boot = new Date();
        KlabService service;
        Scope delegate;

        public LocalService(KlabService service) {
            this.service = service;
        }

        @Override
        public boolean stop() {
            return false;
        }

        @Override
        public Channel getMonitor() {
            return LocalServiceScope.this;
        }

        @Override
        public Parameters<String> getState() {
            return getData();
        }

        @Override
        public Type getIdentityType() {
            return Type.SERVICE;
        }

        @Override
        public String getId() {
            return service.getLocalName();
        }

        @Override
        public Identity getParentIdentity() {
            return null;
        }

        @Override
        public boolean is(Type type) {
            return type == Type.SERVICE;
        }

        @Override
        public <T extends Identity> T getParentIdentity(Class<T> type) {
            return null;
        }

        @Override
        public String getName() {
            return service.getServiceName();
        }

        @Override
        public Date getBootTime() {
            return boot;
        }

        @Override
        public Collection<String> getUrls() {
            return Collections.singleton(service.getUrl());
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public Parameters<String> getData() {
            return LocalServiceScope.this.data;
        }

    }

    public LocalServiceScope(Class<? extends KlabService> serviceClass,
                             BiConsumer<Scope, Message>... listeners) {
        super(listeners);
        this.serviceClass = serviceClass;
    }

    private Parameters<String> data = Parameters.create();
    private Identity serviceIdentity;
    private Class<? extends KlabService> serviceClass;

    @Override
    public Parameters<String> getData() {
        return data;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }

    @Override
    public boolean isDedicated() {
        return true;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
        // TODO if a service resolver is available to the service, that should be used.
        return Collections.singleton(getService(serviceClass));
    }

    @Override
    public Identity getIdentity() {
        if (this.serviceIdentity != null) {
            this.serviceIdentity = new LocalService(getService(this.serviceClass));
        }
        return this.serviceIdentity;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

}
