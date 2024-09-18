package org.integratedmodelling.klab.api.services.impl;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.ArrayList;
import java.util.List;

public class ServiceStatusImpl implements KlabService.ServiceStatus {

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
    private boolean available = false;
    private boolean busy = false;
    private boolean consistent = true;
    private boolean operational = false;
    private ServiceScope.Locality locality = ServiceScope.Locality.EMBEDDED;
    private KlabService.Type serviceType;
    private String serviceId;

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

    @Override
    public KlabService.Type getServiceType() {
        return serviceType;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    @Override
    public ServiceScope.Locality getLocality() {
        return locality;
    }

    public void setLocality(ServiceScope.Locality locality) {
        this.locality = locality;
    }

    public void setServiceType(KlabService.Type serviceType) {
        this.serviceType = serviceType;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public boolean isConsistent() {
        return consistent;
    }

    @Override
    public boolean isOperational() {
        return operational;
    }

    public void setOperational(boolean operational) {
        this.operational = operational;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    @Override
    public String toString() {
        return "ServiceStatusImpl{" +
                "memoryAvailableBytes=" + memoryAvailableBytes +
                ", memoryUsedBytes=" + memoryUsedBytes +
                ", serviceType=" + serviceType +
                ", serviceId='" + serviceId + '\'' +
                ", uptimeMs=" + uptimeMs +
                ", bootTimeMs=" + bootTimeMs +
                ", available=" + available +
                ", busy=" + busy +
                '}';
    }
}
