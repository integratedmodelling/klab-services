package org.integratedmodelling.klab.api.knowledge;

import java.util.List;

import org.integratedmodelling.klab.api.geometry.Geometry;

public interface Model extends Knowledge {

	/**
	 * Models are in namespaces, which are relevant to organization and scoping.
	 * 
	 * @return
	 */
	String getNamespace();

	/**
	 * One of CONCEPT, TEXT, NUMBER, BOOLEAN or VOID if inactive because of error or
	 * offline resources.
	 * 
	 * @return
	 */
	Artifact.Type getType();

	/**
	 * All the observables contextualized by the model, including the "root" one
	 * that defines the model semantics.
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

	/**
	 * Models may have a coverage, either explicitly set in the model definition or
	 * in the namespace, or inherited by their resources. Models with universal
	 * coverage should return an empty geometry. FIXME maybe there should be a
	 * "universal" geometry that isn't empty.
	 * 
	 * @return
	 */
	Geometry getCoverage();

}
