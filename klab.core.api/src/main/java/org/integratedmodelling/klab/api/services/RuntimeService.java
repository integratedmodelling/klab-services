package org.integratedmodelling.klab.api.services;

public interface RuntimeService extends KlabService {
	
    default String getServiceName() {
        return "klab.runtime.service";
    }
    
    public static final int DEFAULT_PORT = 8094;

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
