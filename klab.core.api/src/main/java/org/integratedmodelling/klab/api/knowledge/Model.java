package org.integratedmodelling.klab.api.knowledge;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Contextualizable;

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
	 * The kind of description this model represents. Instantiators return
	 * {@link DescriptionType#INSTANTIATION}.
	 * 
	 * @return
	 */
	DescriptionType getDescriptionType();

	/**
	 * All the observables contextualized by the model, including the "root" one
	 * that defines the model semantics.
	 * 
	 * @return
	 */
	List<Observable> getObservables();

	/**
	 * All the observables needed by the model before contextualization. If the list
	 * has size 0, the model is resolved, i.e. it can be computed without
	 * referencing any other observation.
	 * 
	 * @return
	 */
	List<Observable> getDependencies();

	/**
	 * Models may have a coverage, either explicitly set in the model definition or
	 * in the namespace, or inherited by their resources. Models with universal
	 * coverage should return an empty scale. FIXME there should be a "universal"
	 * scale that isn't empty.
	 * 
	 * @return
	 */
	Scale getCoverage();

	/**
	 * The sequence of contextualizables (resources, function calls, expressions
	 * etc.) that composes the computable part of the model.
	 * 
	 * @return
	 */
	List<Contextualizable> getComputation();

}
