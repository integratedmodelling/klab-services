package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The syntactic peer of a k.LAB namespace.
 * 
 * @author ferdinando.villa
 *
 */
public interface KimOntology extends KimDocument<KimConceptStatement> {

	/**
	 * Return all the namespaces that this should not be mixed with during
	 * resolution or scenario setting.
	 *
	 * @return IDs of namespaces we do not agree with
	 */
	Collection<String> getDisjointNamespaces();


	/**
	 * Imports of external OWL ontologies
	 * 
	 * @return
	 */
	List<PairImpl<String, String>> getOwlImports();

	/**
	 * Import of vocabularies from resources, as resource URN -> list of
	 * vocabularies from that resource
	 * 
	 * @return
	 */
	List<PairImpl<String, List<String>>> getVocabularyImports();

	/**
	 * The domain concept, if stated.
	 * 
	 * @return
	 */
	KimConcept getDomain();

}
