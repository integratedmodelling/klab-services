package org.integratedmodelling.klab.api.services;

public interface RuntimeService extends KlabService {
	
    default String getServiceName() {
        return "klab.runtime.service";
    }
    
	/**
	 * All services publish capabilities and have a call to obtain them.
	 * 
	 * @author Ferd
	 *
	 */
	interface Capabilities extends ServiceCapabilities {
	    
	}

	Capabilities getCapabilities();
	

}
