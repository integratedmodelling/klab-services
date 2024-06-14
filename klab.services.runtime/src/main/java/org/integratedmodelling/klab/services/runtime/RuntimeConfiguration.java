package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * FIXME this is a straight copy of the Reasoner config
 */
public class RuntimeConfiguration {

    private List<String> allowedGroups = new ArrayList<>();
    private String url = null;
    private String serviceId;

    public List<String> getAllowedGroups() {
        return allowedGroups;
    }

    public void setAllowedGroups(List<String> allowedGroups) {
        this.allowedGroups = allowedGroups;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

}
