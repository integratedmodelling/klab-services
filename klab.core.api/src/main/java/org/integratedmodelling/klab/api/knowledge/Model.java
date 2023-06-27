package org.integratedmodelling.klab.api.knowledge;

import java.util.List;

public interface Model extends Knowledge {

	String getNamespace();

	/**
	 * One of CONCEPT, TEXT, NUMBER, BOOLEAN or VOID if inactive because of error or
	 * offline resources
	 * 
	 * @return
	 */
	Artifact.Type getType();

	/**
	 * All the observables contextualized by the model, including the "root" one that
	 * defines the model semantics.
	 * 
	 * @return
	 */
	List<Observable> getObservables();

	/**
	 * All the observables needed by the model before contextualization.
	 * 
	 * @return
	 */
	List<Observable> getDependencies();

}
