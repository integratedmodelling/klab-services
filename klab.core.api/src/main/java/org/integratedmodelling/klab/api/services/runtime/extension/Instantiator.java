package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.List;

public interface Instantiator<T extends Observation> extends Contextualizer {

	List<T> resolve(Observable semantics, ServiceCall call, ContextScope scope);
}
