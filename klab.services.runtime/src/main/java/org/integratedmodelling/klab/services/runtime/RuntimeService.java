package org.integratedmodelling.klab.services.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.runtime.tasks.ObservationTask;

public class RuntimeService extends BaseService implements org.integratedmodelling.klab.api.services.RuntimeService,
		org.integratedmodelling.klab.api.services.RuntimeService.Admin {

	public RuntimeService(Authentication testAuthentication, ServiceScope scope) {
		// TODO Auto-generated constructor stub
	    super(scope);
	}

	@Override
	public void initializeService() {
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
		return new ObservationTask(dataflow, scope, true);
	}

	@Override
	public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
		Map<String, String> ret = new HashMap<>();
		return ret;
	}


}
