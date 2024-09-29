package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.scope.Scope;

public class ContextInfo {

    private String name;
    private String id;
    private String behavior;
    private long creationTime;
    private long idleTimeMs;
    private Scope.Expiration expiration;
    private long creditsSoFar;
    private long observations;
    private long size;

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getCreditsSoFar() {
        return creditsSoFar;
    }

    public void setCreditsSoFar(long creditsSoFar) {
        this.creditsSoFar = creditsSoFar;
    }

    public Scope.Expiration getExpiration() {
        return expiration;
    }

    public void setExpiration(Scope.Expiration expiration) {
        this.expiration = expiration;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getIdleTimeMs() {
        return idleTimeMs;
    }

    public void setIdleTimeMs(long idleTimeMs) {
        this.idleTimeMs = idleTimeMs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getObservations() {
        return observations;
    }

    public void setObservations(long observations) {
        this.observations = observations;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
