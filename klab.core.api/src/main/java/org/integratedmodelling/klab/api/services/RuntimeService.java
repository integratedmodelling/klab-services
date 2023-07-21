package org.integratedmodelling.klab.api.services;

import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
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

	Capabilities capabilities();

	/**
	 * 
	 * @param dataflow
	 * @param scope
	 * @return
	 */
	Future<Observation> run(Dataflow<?> dataflow, ContextScope scope);

	/**
	 * Create and return a Contextualizer or Verb implementation from a known
	 * library, validating the arguments at the same time. The service may override
	 * any method through configuration and plug-ins.
	 * 
	 * @param <T>
	 * @param call
	 * @param resultClass
	 * @return
	 */
	<T> T getLibraryMethod(ServiceCall call, Class<T> resultClass);

	interface Admin {

		/**
		 * If runtime exceptions have caused the building of test cases, retrieve them
		 * as a map of case class->source code, with the option of deleting them after
		 * responding.
		 * 
		 * @param deleteExisting
		 * @return
		 */
		Map<String, String> getExceptionTestcases(boolean deleteExisting);
	}

}
