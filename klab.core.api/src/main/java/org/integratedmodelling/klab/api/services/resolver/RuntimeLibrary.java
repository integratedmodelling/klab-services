package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.Contextualizable;

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
