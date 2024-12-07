package org.integratedmodelling.klab.services.configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * FIXME this is a straight copy of the Reasoner config
 */
public class RuntimeConfiguration {

    private List<String> allowedGroups = new ArrayList<>();
    private String url = null;
    private String serviceId;
    private URI brokerURI;

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

    /**
     * If no broker URL is present, the service will install a local QPid broker for
     * internal connections on port 5672.
     *
     * This should be something like "amqp://userName:password@hostName:portNumber/virtualHost"
     * to pass to a connectionfactory.
     *
     * @return
     */
    public URI getBrokerURI() {
        return brokerURI;
    }

    public void setBrokerURI(URI brokerURI) {
        this.brokerURI = brokerURI;
    }
}
