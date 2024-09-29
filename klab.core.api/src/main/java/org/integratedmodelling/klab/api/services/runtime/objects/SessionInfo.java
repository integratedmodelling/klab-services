package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.scope.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about a session, returned by the runtime on request.
 */
public class SessionInfo {

    private String name;
    private String id;
    private String username;
    private String behavior;
    private long creationTime;
    private long idleTimeMs;
    private Scope.Expiration expiration;
    private List<ContextInfo> contexts = new ArrayList<>();

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public List<ContextInfo> getContexts() {
        return contexts;
    }

    public void setContexts(List<ContextInfo> contexts) {
        this.contexts = contexts;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
