package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Service;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public abstract class BaseService implements KlabService {

    public static class ServiceStatusImpl implements ServiceStatus {

        private int healthPercentage = -1;
        private int loadPercentage = -1;
        private long memoryAvailableBytes = Runtime.getRuntime().totalMemory();
        private long memoryUsedBytes = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory();
        private int connectedSessionCount = -1;
        private int knownSessionCount = -1;
        private long uptimeMs = -1;
        private long bootTimeMs = -1;
        private List<Notification> advisories = new ArrayList<>();
        private Metadata metadata = Metadata.create();

        @Override
        public int getHealthPercentage() {
            return this.healthPercentage;
        }

        @Override
        public int getLoadPercentage() {
            return this.loadPercentage;
        }

        @Override
        public long getMemoryAvailableBytes() {
            return this.memoryAvailableBytes;
        }

        @Override
        public long getMemoryUsedBytes() {
            return this.memoryUsedBytes;
        }

        @Override
        public int getConnectedSessionCount() {
            return this.connectedSessionCount;
        }

        @Override
        public int getKnownSessionCount() {
            return this.knownSessionCount;
        }

        @Override
        public long getUptimeMs() {
            return this.uptimeMs;
        }

        @Override
        public long getBootTimeMs() {
            return this.bootTimeMs;
        }

        @Override
        public List<Notification> getAdvisories() {
            return this.advisories;
        }

        @Override
        public Metadata getMetadata() {
            return this.metadata;
        }

        public void setHealthPercentage(int healthPercentage) {
            this.healthPercentage = healthPercentage;
        }

        public void setLoadPercentage(int loadPercentage) {
            this.loadPercentage = loadPercentage;
        }

        public void setMemoryAvailableBytes(long memoryAvailableBytes) {
            this.memoryAvailableBytes = memoryAvailableBytes;
        }

        public void setMemoryUsedBytes(long memoryUsedBytes) {
            this.memoryUsedBytes = memoryUsedBytes;
        }

        public void setConnectedSessionCount(int connectedSessionCount) {
            this.connectedSessionCount = connectedSessionCount;
        }

        public void setKnownSessionCount(int knownSessionCount) {
            this.knownSessionCount = knownSessionCount;
        }

        public void setUptimeMs(long uptimeMs) {
            this.uptimeMs = uptimeMs;
        }

        public void setBootTimeMs(long bootTimeMs) {
            this.bootTimeMs = bootTimeMs;
        }

        public void setAdvisories(List<Notification> advisories) {
            this.advisories = advisories;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
    }

    private static final long serialVersionUID = 1646569587945609013L;

    protected ServiceScope scope;
    protected String localName = "Embedded";

    protected List<BiConsumer<Scope, Message>> eventListeners = new ArrayList<>();

    protected BaseService(ServiceScope scope, String localName,
                          BiConsumer<Scope, Message>... eventListeners) {
        this.scope = scope;
        this.localName = localName;
        if (eventListeners != null) {
            Arrays.stream(eventListeners).map(e -> this.eventListeners.add(e));
        }
    }

    /**
     * Override this to fill in the known parameters, i.e. everything except free/total memory.
     *
     * @return
     */
    public ServiceStatus status() {
        return new ServiceStatusImpl();
    }


    public String getLocalName() {
        // TODO Auto-generated method stub
        return localName;
    }

    @Override
    public ServiceScope scope() {
        return scope;
    }

    public abstract void initializeService();

    protected void notify(Scope scope, Object... objects) {
        if (!eventListeners.isEmpty()) {
            for (var listener : eventListeners) {
                listener.accept(scope, Message.create(scope, objects));
            }
        }
    }
}
