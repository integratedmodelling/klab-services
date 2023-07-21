package org.integratedmodelling.klab.services.base;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;

public abstract class BaseService implements KlabService {

	private static final long serialVersionUID = 1646569587945609013L;

	public abstract void initializeService(Scope scope);
	
}
