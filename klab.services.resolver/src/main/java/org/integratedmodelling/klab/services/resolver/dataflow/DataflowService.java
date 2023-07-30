package org.integratedmodelling.klab.services.resolver.dataflow;

import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraph;

/**
 * This can be seen as a sub-service if needed.
 * 
 * @author Ferd
 *
 */
public class DataflowService {

	/**
	 * Compile the resolution strategy into a dataflow. The scale of
	 * contextualization is no longer relevant: the dataflow's coverage will reflect
	 * the coverage of the actuators.
	 * 
	 * @param resolution
	 * @return
	 */
	public Dataflow<?> compile(ResolutionGraph.Resolution resolution) {
	    DataflowImpl ret = new DataflowImpl();
	    // TODO 
	    return ret;
	}
}
