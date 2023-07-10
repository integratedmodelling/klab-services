package org.integratedmodelling.klab.api.services.runtime.extension;

import java.util.List;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;

public interface Instantiator<T extends DirectObservation> extends Contextualizer {

	List<T> resolve(Observable semantics, ContextScope scope);
}
