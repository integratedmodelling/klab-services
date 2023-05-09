package org.integratedmodelling.klab.api.services;

import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

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
	
	/**
	 * 
	 * @param dataflow
	 * @param scope
	 * @return
	 */
	Future<Observation> run(Dataflow<?> dataflow, ContextScope scope); 

}
