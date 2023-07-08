package org.integratedmodelling.klab.api.knowledge.observation.scale;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.Concept;

/**
 * Any extent may be implemented as an enumerated extension, which details a
 * coverage over a conceptual space including the semantic closure of a stated
 * concept or 1+ identities from an authority. These extents can normally only
 * be merged with other enumerated extents with the same domain, unless the
 * runtime has a mediation strategy that can retrieve the physical
 * representation of the same extent.
 * <p>
 * A conceptual extent is essentially an enumerated extension with the extent
 * semantics.
 * 
 * @author Ferd
 *
 */
public interface EnumeratedExtension<T extends Extent<T>> {

	/**
	 * 
	 * @return
	 */
	String getDomainAuthority();

	/**
	 * 
	 * @return
	 */
	Concept getDomainIdentity();

	/**
	 * Return all the concepts that make up the extent of this domain.
	 * 
	 * @return
	 */
	Collection<Concept> getExtension();

	/**
	 * If this returns a valid extent, the return value can be used for merging
	 * instead of the extension and domain, and the extent is compatible with other
	 * non-enumerated ones.
	 * 
	 * @return
	 */
	Extent<T> getPhysicalExtent();
}
