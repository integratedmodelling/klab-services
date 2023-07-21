package org.integratedmodelling.klab.services.runtime;

import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.base.BaseService;

public class RuntimeService extends BaseService implements org.integratedmodelling.klab.api.services.RuntimeService,
		org.integratedmodelling.klab.api.services.RuntimeService.Admin {

	private static final long serialVersionUID = -3119521647259754846L;

	public RuntimeService(Authentication testAuthentication) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void initializeService(Scope scope) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public String getUrl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLocalName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceScope scope() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean shutdown() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Capabilities capabilities() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Future<Observation> run(Dataflow<?> dataflow, ContextScope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Create and return a Contextualizer or Verb from a library, validating the
	 * arguments at the same time. The service may override any method through
	 * configuration and plug-ins.
	 * 
	 * @param <T>
	 * @param call
	 * @param resultClass
	 * @return
	 */
	@Override
	public <T> T getLibraryMethod(ServiceCall call, Class<T> resultClass) {
		return null;
	}

	@Override
	public Map<String, String> getExceptionTestcases(boolean deleteExisting) {
		// TODO Auto-generated method stub
		return null;
	}


}
