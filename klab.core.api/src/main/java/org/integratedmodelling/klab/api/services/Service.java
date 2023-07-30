package org.integratedmodelling.klab.api.services;

/**
 * Basic service type. Those that are not {@link KlabService}s are created explicitly or injected
 * and accessed through configuration. Only needed for the API.
 * 
 * @author Ferd
 *
 */
public interface Service {

    /**
     * The identifier of the service type in the k.LAB ecosystem. This is the same for all services
     * of the same type and the derived interfaces provide a default implementation.
     * 
     * @return
     */
    String getServiceName();
}
