package org.integratedmodelling.klab.api.knowledge.observation.scale;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.Authority;
import org.integratedmodelling.klab.api.knowledge.Concept;

/**
 * Enumerated extent, which details a coverage over a conceptual space including
 * the semantic closure of a stated concept.
 * 
 * @author Ferd
 *
 */
public interface EnumeratedExtent extends Extent<EnumeratedExtent> {

	/**
	 * 
	 * @return
	 */
	Authority getAuthority();

	/**
	 * 
	 * @return
	 */
	Concept getBaseIdentity();

	/**
	 * Return all the concepts that make up the extent of this domain.
	 * 
	 * @return
	 */
	Collection<Concept> getExtension();
}
