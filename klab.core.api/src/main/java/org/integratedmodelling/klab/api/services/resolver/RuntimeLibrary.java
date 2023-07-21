package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * Collects all the calls needed to implement a k.LAB core runtime and returns
 * the contextualizables that implement them. The receiving end should be
 * prepared for function calls at least.
 * 
 * @author Ferd
 *
 */
public interface RuntimeLibrary {

	Contextualizable getUrnContextualizer(Urn urn, Scope scope);
}
