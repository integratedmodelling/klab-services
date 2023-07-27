package org.integratedmodelling.klab.api.services;

public interface Service {

    /**
     * The identifier of the service type in the k.LAB ecosystem. This is the same for all services
     * of the same type and the derived interfaces provide a default implementation.
     * 
     * @return
     */
    String getServiceName();
}
