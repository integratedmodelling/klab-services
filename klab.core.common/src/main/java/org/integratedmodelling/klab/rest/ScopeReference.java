package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Scope data returned by {@link org.integratedmodelling.klab.api.ServicesAPI.SCOPE#CREATE} requests when
 * successful.
 * <p>
 * If the passed channelURL isn't null, the scope is allowed to establish duplex communication using the
 * methods based on the URL protocol.
 */
public class ScopeReference {

    private String scopeId;
    private URL channelUrl;
    private Parameters<String> scopeData = Parameters.create();
    private List<Notification> notifications = new ArrayList<>();

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public URL getChannelUrl() {
        return channelUrl;
    }

    public void setChannelUrl(URL channelUrl) {
        this.channelUrl = channelUrl;
    }

    public Parameters<String> getScopeData() {
        return scopeData;
    }

    public void setScopeData(Parameters<String> scopeData) {
        this.scopeData = scopeData;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
