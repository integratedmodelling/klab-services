package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

public interface Runtime extends KlabFederatedService {
	
    default String getServiceName() {
        return "klab.runtime.service";
    }
    
	/**
	 * All services publish capabilities and have a call to obtain them.
	 * 
	 * @author Ferd
	 *
	 */
	interface Capabilities extends FederatedServiceCapabilities {
	    
	}

	Capabilities getCapabilities();
	

}
