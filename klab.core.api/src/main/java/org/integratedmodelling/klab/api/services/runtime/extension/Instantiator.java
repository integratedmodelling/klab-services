package org.integratedmodelling.klab.api.services.runtime.extension;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

public interface Instantiator<T extends DirectObservation> extends Contextualizer {

	List<T> resolve(Observable semantics, ServiceCall call, ContextScope scope);
}
