package org.integratedmodelling.klab.services.reasoner;

import org.integratedmodelling.klab.api.services.Reasoner;

public class ReasonerCapabilities implements Reasoner.Capabilities {

    private String localName;
    private String serviceName;

    /**
     * 
     */
    private static final long serialVersionUID = -1430274296197650332L;

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

}
