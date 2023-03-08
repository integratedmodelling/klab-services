package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

public interface Runtime extends KlabService {
	
	/**
	 * All services publish capabilities and have a call to obtain them.
	 * 
	 * @author Ferd
	 *
	 */
	interface Capabilities extends Serializable {

	}

	Capabilities getCapabilities();
	

}
