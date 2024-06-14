package org.integratedmodelling.klab.components;

import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.pf4j.update.DefaultUpdateRepository;

public class LocalComponentRepository extends DefaultUpdateRepository {

	public LocalComponentRepository(String servicePath)  {
		super("local", ServiceConfiguration.INSTANCE.getLocalComponentRepositoryURL(servicePath));
	}

}
